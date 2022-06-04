package org.grobid.core.engines;

import org.apache.commons.io.FileUtils;
import org.grobid.core.GrobidModels;
import org.grobid.core.analyzers.DataseerAnalyzer;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.data.BibDataSet;
import org.grobid.core.data.Dataset;
import org.grobid.core.data.Dataset.DatasetType;
import org.grobid.core.data.DatasetComponent;
import org.grobid.core.document.Document;
import org.grobid.core.document.DocumentPiece;
import org.grobid.core.document.DocumentSource;
import org.grobid.core.document.TEIFormatter;
import org.grobid.core.document.xml.XmlBuilderUtils;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.engines.label.SegmentationLabels;
import org.grobid.core.engines.label.TaggingLabel;
import org.grobid.core.engines.label.TaggingLabels;
import org.grobid.core.engines.label.DatasetTaggingLabels;
import org.grobid.core.engines.tagging.GrobidCRFEngine;
import org.grobid.core.engines.DatasetParser;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.features.FeaturesVectorDataseer;
import org.grobid.core.features.FeatureFactory;
import org.grobid.core.layout.BoundingBox;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.LayoutTokenization;
import org.grobid.core.lexicon.DataseerLexicon;
import org.grobid.core.lexicon.Lexicon;
import org.grobid.core.tokenization.TaggingTokenCluster;
import org.grobid.core.tokenization.TaggingTokenClusteror;
import org.grobid.core.utilities.*;
import org.grobid.core.utilities.counters.CntManager;
import org.grobid.core.utilities.counters.impl.CntManagerFactory;
import org.grobid.core.lexicon.FastMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.xml.sax.InputSource;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import nu.xom.Attribute;
import nu.xom.Element;
import nu.xom.Node;
import nu.xom.Text;

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.io.*;

import static org.apache.commons.lang3.StringUtils.*;
import static org.grobid.core.document.xml.XmlBuilderUtils.teiElement;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Identification of the dataset names, implicit dataset expressions and data acquisition device names in text.
 *
 * @author Patrice
 */
public class DatasetParser extends AbstractParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatasetParser.class);

    private static volatile DatasetParser instance;

    private DataseerLexicon dataseerLexicon = null;
    private EngineParsers parsers;
    private DataseerConfiguration dataseerConfiguration;
    private DataseerClassifier dataseerClassifier;

    public static DatasetParser getInstance(DataseerConfiguration configuration) {
        if (instance == null) {
            getNewInstance(configuration);
        }
        return instance;
    }

    /**
     * Create a new instance.
     */
    private static synchronized void getNewInstance(DataseerConfiguration configuration) {
        instance = new DatasetParser(configuration);
    }

    private DatasetParser(DataseerConfiguration configuration) {
        super(DatasetModels.DATASET, CntManagerFactory.getCntManager(), 
            GrobidCRFEngine.valueOf(configuration.getModel("datasets").engine.toUpperCase()),
            configuration.getModel("datasets").delft.architecture);

        dataseerLexicon = DataseerLexicon.getInstance();
        parsers = new EngineParsers();
        dataseerConfiguration = configuration;
    }

    /**
     * Sequence labelling of a list of layout tokens for identifying dataset names.
     * Input corresponds to a list of sentences, each sentence being itself a list of Layout tokens.
     *  
     * @param tokensList the list of LayoutTokens sequences to be labeled
     * 
     * @return list of identified Dataset objects. 
     */
    public List<List<Dataset>> processing(List<List<LayoutToken>> tokensList) {

        List<List<Dataset>> results = new ArrayList<>();
        if (tokensList == null || tokensList.size() == 0) {
            return results;
        }

        StringBuilder input = new StringBuilder();
        //List<String> inputs = new ArrayList<>();
        List<List<LayoutToken>> newTokensList = new ArrayList<>();
        int total = 0;
        for (List<LayoutToken> tokens : tokensList) {
            // to be sure it's done, retokenize according to the DataseerAnalyzer
            tokens = DataseerAnalyzer.getInstance().retokenizeLayoutTokens(tokens);
            newTokensList.add(tokens);

            // create basic input without features
            for(LayoutToken token : tokens) {
                if (token.getText().trim().length() == 0) {
                    //System.out.println("skipped: " + token.getText());
                    continue;
                }
                input.append(token.getText());
                input.append("\n");
            }

            //inputs.add(input.toString());
            input.append("\n\n");
            total++;
        }

        System.out.println("total size: " + total);

        tokensList = newTokensList;

        String allRes = null;
        try {
            allRes = label(input.toString());
        } catch (Exception e) {
            LOGGER.error("An exception occured while labeling a sequence.", e);
            throw new GrobidException(
                    "An exception occured while labeling a sequence.", e);
        }

        if (allRes == null || allRes.length() == 0)
            return results;

        String[] resBlocks = allRes.split("\n\n");

        System.out.println("resBlocks: " + resBlocks.length);

        int i = 0;
        for (List<LayoutToken> tokens : tokensList) {
            if (tokens == null || tokens.size() == 0) {
                results.add(null);
            } else {
                String text = LayoutTokensUtil.toText(tokens);
                List<DatasetComponent> localDatasetcomponents = resultExtractionLayoutTokens(resBlocks[i], tokens, text);
                List<Dataset> localDatasets = groupByEntities(localDatasetcomponents, tokens, text);
                results.add(localDatasets);
            }
            i++;
        }

        return results;
    }

    private List<DatasetComponent> resultExtractionLayoutTokens(String result, List<LayoutToken> tokenizations, String text) {
        List<DatasetComponent> datasetComponents = new ArrayList<>();
        
        //String text = LayoutTokensUtil.toText(tokenizations);

        TaggingTokenClusteror clusteror = new TaggingTokenClusteror(DatasetModels.DATASET, result, tokenizations);
        List<TaggingTokenCluster> clusters = clusteror.cluster();

        int pos = 0; // position in term of characters for creating the offsets
        DatasetComponent dataset = null;

        for (TaggingTokenCluster cluster : clusters) {
            if (cluster == null) {
                continue;
            }
            TaggingLabel clusterLabel = cluster.getTaggingLabel();
            Engine.getCntManager().i(clusterLabel);
            
            String clusterText = LayoutTokensUtil.toText(cluster.concatTokens());
            List<LayoutToken> theTokens = cluster.concatTokens();

            if ((pos < text.length()-1) && (text.charAt(pos) == ' '))
                pos += 1;
            if ((pos < text.length()-1) && (text.charAt(pos) == '\n'))
                pos += 1;
            
            int endPos = pos;
            boolean start = true;
            for (LayoutToken token : theTokens) {
                if (token.getText() != null) {
                    if (start && token.getText().equals(" ")) {
                        pos++;
                        endPos++;
                        continue;
                    }
                    if (start)
                        start = false;
                    endPos += token.getText().length();
                }
            }

            if ((endPos > 0) && (text.length() >= endPos) && (text.charAt(endPos-1) == '\n'))
                endPos--;
            if ((endPos > 0) && (text.length() >= endPos) && (text.charAt(endPos-1) == ' '))
                endPos--;

            if (clusterLabel.equals(DatasetTaggingLabels.DATASET_NAME)) {
                dataset = new DatasetComponent(DatasetType.DATASET_NAME, text.substring(pos, endPos));
            } else if (clusterLabel.equals(DatasetTaggingLabels.DATASET)) {
                dataset = new DatasetComponent(DatasetType.DATASET, text.substring(pos, endPos));
            } else if (clusterLabel.equals(DatasetTaggingLabels.DATA_DEVICE)) {
                dataset = new DatasetComponent(DatasetType.DATA_DEVICE, text.substring(pos, endPos));
            } 

            if (dataset != null) {
                dataset.setOffsetStart(pos);
                dataset.setOffsetEnd(endPos);

                dataset.setLabel(clusterLabel);
                dataset.setTokens(theTokens);

                List<BoundingBox> boundingBoxes = BoundingBoxCalculator.calculate(cluster.concatTokens());
                dataset.setBoundingBoxes(boundingBoxes);

                datasetComponents.add(dataset);

                dataset = null;
            }

            pos = endPos;
        }

        return datasetComponents;
    }

    private List<Dataset> groupByEntities(List<DatasetComponent> components, List<LayoutToken> tokens, String text) {
        List<Dataset> localDatasets = new ArrayList<>();
        for(DatasetComponent localComponent : components) {
            Dataset localDataset = null; 
            if (localComponent.getType() == DatasetType.DATASET_NAME) {
                localDataset = new Dataset(DatasetType.DATASET_NAME, localComponent.getRawForm());
                localDataset.setDatasetName(localComponent);
            } else if (localComponent.getType() == DatasetType.DATASET) {
                localDataset = new Dataset(DatasetType.DATASET, localComponent.getRawForm());
                localDataset.setDataset(localComponent);
            } else if (localComponent.getType() == DatasetType.DATA_DEVICE) {
                for(Dataset knownDataset : localDatasets) {
                    if (knownDataset != null && knownDataset.getDataset() != null) {
                        knownDataset.setDataDevice(localComponent);
                    }
                }
            }
            if (localDataset != null) {
                localDataset.setContext(text);
                localDatasets.add(localDataset);
            }
        } 

        return localDatasets;
    }

    /**
     * Sequence labelling of a string for identifying dataset names. 
     *
     * @param tokens the list of LayoutTokens to be labeled
     * 
     * @return list of identified Dataset objects. 
     */
    public List<Dataset> processingString(String input) {
        List<List<LayoutToken>> tokensList = new ArrayList<>();
        input = UnicodeUtil.normaliseText(input);
        tokensList.add(analyzer.tokenizeWithLayoutToken(input));
        List<List<Dataset>> result = processing(tokensList);
        if (result != null && result.size()>0)
            return result.get(0);
        else 
            return new ArrayList<Dataset>();
    }

    public List<List<Dataset>> processingStrings(List<String> inputs) {
        List<List<LayoutToken>> tokensList = new ArrayList<>();
        for(String input : inputs) {
            input = UnicodeUtil.normaliseText(input);
            tokensList.add(analyzer.tokenizeWithLayoutToken(input));
        }
        return processing(tokensList);
    }

    public Pair<List<List<Dataset>>,Document> processPDF(File file, 
                                                        boolean disambiguate) throws IOException {
        List<List<Dataset>> entities = new ArrayList<>();
        Document doc = null;
        try {
            GrobidAnalysisConfig config =
                        GrobidAnalysisConfig.builder()
                                .consolidateHeader(0)
                                .consolidateCitations(0)
                                .build();

            DocumentSource documentSource = 
                DocumentSource.fromPdf(file, config.getStartPage(), config.getEndPage());
            doc = parsers.getSegmentationParser().processing(documentSource, config);

            // process bibliographical reference section first
            List<BibDataSet> resCitations = parsers.getCitationParser().
                processingReferenceSection(doc, parsers.getReferenceSegmenterParser(), config.getConsolidateCitations());

            doc.setBibDataSets(resCitations);

            // here we process the relevant textual content of the document

            // for refining the process based on structures, we need to filter
            // segment of interest (e.g. header, body, annex) and possibly apply 
            // the corresponding model to further filter by structure types 

            List<List<LayoutToken>> selectedLayoutTokenSequences = new ArrayList<>();
            List<Boolean> relevantSections = new ArrayList<>();

            // from the header, we are interested in title, abstract and keywords
            SortedSet<DocumentPiece> documentParts = doc.getDocumentPart(SegmentationLabels.HEADER);
            BiblioItem resHeader = null;
            if (documentParts != null) {
                Pair<String,List<LayoutToken>> headerFeatured = parsers.getHeaderParser().getSectionHeaderFeatured(doc, documentParts);
                String header = headerFeatured.getLeft();
                List<LayoutToken> tokenizationHeader = doc.getTokenizationParts(documentParts, doc.getTokenizations());
                String labeledResult = null;
                if ((header != null) && (header.trim().length() > 0)) {
                    labeledResult = parsers.getHeaderParser().label(header);
                    resHeader = new BiblioItem();
                    resHeader.generalResultMapping(labeledResult, tokenizationHeader);

                    // title
                    List<LayoutToken> titleTokens = resHeader.getLayoutTokens(TaggingLabels.HEADER_TITLE);
                    if (titleTokens != null) {
                        selectedLayoutTokenSequences.add(titleTokens);
                        relevantSections.add(false);
                    } 

                    // abstract
                    List<LayoutToken> abstractTokens = resHeader.getLayoutTokens(TaggingLabels.HEADER_ABSTRACT);
                    if (abstractTokens != null) {
                        selectedLayoutTokenSequences.add(abstractTokens);
                        relevantSections.add(false);
                    } 

                    // keywords
                    List<LayoutToken> keywordTokens = resHeader.getLayoutTokens(TaggingLabels.HEADER_KEYWORD);
                    if (keywordTokens != null) {
                        selectedLayoutTokenSequences.add(keywordTokens);
                        relevantSections.add(false);
                    }
                }
            }

            // process selected structures in the body,
            documentParts = doc.getDocumentPart(SegmentationLabels.BODY);
            List<TaggingTokenCluster> bodyClusters = null;
            if (documentParts != null) {
                // full text processing
                Pair<String, LayoutTokenization> featSeg = parsers.getFullTextParser().getBodyTextFeatured(doc, documentParts);
                if (featSeg != null) {
                    // if featSeg is null, it usually means that no body segment is found in the
                    // document segmentation
                    String bodytext = featSeg.getLeft();

                    LayoutTokenization tokenizationBody = featSeg.getRight();
                    String rese = null;
                    if ( (bodytext != null) && (bodytext.trim().length() > 0) ) {               
                        rese = parsers.getFullTextParser().label(bodytext);
                    } else {
                        LOGGER.debug("Fulltext model: The input to the sequence labelling processing is empty");
                    }

                    TaggingTokenClusteror clusteror = new TaggingTokenClusteror(GrobidModels.FULLTEXT, rese, 
                        tokenizationBody.getTokenization(), true);
                    bodyClusters = clusteror.cluster();
                    List<LayoutToken> curParagraphTokens = null;
                    TaggingLabel lastClusterLabel = null;
                    for (TaggingTokenCluster cluster : bodyClusters) {
                        if (cluster == null) {
                            continue;
                        }

                        TaggingLabel clusterLabel = cluster.getTaggingLabel();
                        String clusterText = LayoutTokensUtil.toText(cluster.concatTokens());

                        List<LayoutToken> localTokenization = cluster.concatTokens();
                        if ((localTokenization == null) || (localTokenization.size() == 0))
                            continue;
                        
                        if (TEIFormatter.MARKER_LABELS.contains(clusterLabel)) {
                            if (curParagraphTokens == null)
                                curParagraphTokens = new ArrayList<>();
                            curParagraphTokens.addAll(localTokenization);
                        } else if (clusterLabel.equals(TaggingLabels.PARAGRAPH) || clusterLabel.equals(TaggingLabels.ITEM)) {
                            //|| clusterLabel.equals(TaggingLabels.SECTION) {
                            if (lastClusterLabel == null || curParagraphTokens == null  || isNewParagraph(lastClusterLabel)) { 
                                if (curParagraphTokens != null) {
                                    selectedLayoutTokenSequences.add(curParagraphTokens);
                                    relevantSections.add(true);
                                }
                                curParagraphTokens = new ArrayList<>();
                            }
                            curParagraphTokens.addAll(localTokenization);

                            //selectedLayoutTokenSequences.add(localTokenization);
                        } else if (clusterLabel.equals(TaggingLabels.TABLE)) {
                            //processLayoutTokenSequenceTableFigure(localTokenization, entities);
                        } else if (clusterLabel.equals(TaggingLabels.FIGURE)) {
                            //processLayoutTokenSequenceTableFigure(localTokenization, entities);
                        } else if (clusterLabel.equals(TaggingLabels.SECTION)) {
                            //currentSection = clusterText;
                        }

                        lastClusterLabel = clusterLabel;
                    }
                    // last paragraph
                    if (curParagraphTokens != null) {
                        selectedLayoutTokenSequences.add(curParagraphTokens);
                        relevantSections.add(true);
                    }
                }
            }

            // we don't process references (although reference titles could be relevant)
            // acknowledgement? 

            // we can process annexes, except those referring to author information
            documentParts = doc.getDocumentPart(SegmentationLabels.ANNEX);
            if (documentParts != null) {
                //processDocumentPart(documentParts, doc, entities);

                List<LayoutToken> annexTokens = doc.getTokenizationParts(documentParts, doc.getTokenizations());
                if (annexTokens != null) {
                    selectedLayoutTokenSequences.add(annexTokens);
                    if (this.checkAuthorAnnex(annexTokens))
                        relevantSections.add(true);
                    else 
                        relevantSections.add(false);
                }
            }

            // footnotes are also relevant
            documentParts = doc.getDocumentPart(SegmentationLabels.FOOTNOTE);
            if (documentParts != null) {
                //processDocumentPart(documentParts, doc, components);

                List<LayoutToken> footnoteTokens = doc.getTokenizationParts(documentParts, doc.getTokenizations());
                if (footnoteTokens != null) {
                    selectedLayoutTokenSequences.add(footnoteTokens);
                    relevantSections.add(false);
                }
            }

            // segment zone into sentences
            List<List<LayoutToken>> allLayoutTokens = new ArrayList<>();
            List<String> allSentences = new ArrayList<>();
            int zoneIndex = 0;
            Map<Integer,Integer> mapSentencesToZones = new HashMap<>();
            for(List<LayoutToken> layoutTokens : selectedLayoutTokenSequences) {
                layoutTokens = DataseerAnalyzer.getInstance().retokenizeLayoutTokens(layoutTokens);

                if ( (layoutTokens == null) || (layoutTokens.size() == 0) ) {
                    allLayoutTokens.add(null);
                    continue;
                }

                // positions for lexical match
                List<OffsetPosition> urlPositions = Lexicon.getInstance().tokenPositionsUrlPattern(layoutTokens);
                
                // segment into sentences
                String localText = LayoutTokensUtil.toText(layoutTokens);
                List<OffsetPosition> sentencePositions = 
                    SentenceUtilities.getInstance().runSentenceDetection(localText, urlPositions, layoutTokens, null);
                if (sentencePositions == null) {
                    sentencePositions = new ArrayList<>();
                    sentencePositions.add(new OffsetPosition(0, localText.length()));
                }

                for(OffsetPosition sentencePosition : sentencePositions) {
                    int startPos = sentencePosition.start;
                    int endPos = sentencePosition.end;

                    List<LayoutToken> sentenceTokens = new ArrayList<>();
                    int pos = 0;
                    for(LayoutToken token : layoutTokens) {
                        if (startPos <= pos && (pos+token.getText().length()) <= endPos) {
                            sentenceTokens.add(token);
                        } else if (endPos < (pos+token.getText().length())) {
                            break;
                        }
                        pos += token.getText().length();
                    }

                    allLayoutTokens.add(sentenceTokens);
                    allSentences.add(localText.substring(startPos, endPos));
                    mapSentencesToZones.put(allSentences.size()-1, zoneIndex);
                }

                zoneIndex++;
            }

            System.out.println("allLayoutTokens size: " + allLayoutTokens.size());
            System.out.println("allSentences size: " + allSentences.size());

            // pre-process labeling of every sentences in batch
            processLayoutTokenSequences(allLayoutTokens, entities, disambiguate);

            System.out.println("entities size: " + entities.size());
            System.out.println("mapSentencesToZones size: " + mapSentencesToZones.size());
            System.out.println("relevantSections size: " + relevantSections.size());

            // pre-process classification of every sentences in batch
            if (this.dataseerClassifier == null)
                dataseerClassifier = DataseerClassifier.getInstance();

            List<Double> bestScores = new ArrayList<>();
            List<String> bestTypes = new ArrayList<>();
            List<Double> hasDatasetScores = new ArrayList<>();
            try {
                String jsonClassification = dataseerClassifier.classify(allSentences);
                //System.out.println(jsonClassification);

                //List<Boolean> hasDatasets = new ArrayList<>();
                ObjectMapper mapper = new ObjectMapper();
                try {
                    //System.out.println(jsonClassification);
                    JsonNode root = mapper.readTree(jsonClassification);
                    JsonNode classificationsNode = root.findPath("classifications");
                    if ((classificationsNode != null) && (!classificationsNode.isMissingNode())) {
                        Iterator<JsonNode> ite = classificationsNode.elements();
                        while(ite.hasNext()) {
                            JsonNode classificationNode = ite.next();

                            double bestScore = 0.0;
                            String bestType = null;
                            Iterator<String> iterator = classificationNode.fieldNames();
                            Map<String, Double> scoresPerDatatypes = new TreeMap<>();
                            while(iterator.hasNext()) {
                                String field = iterator.next();

                                if (field.equals("has_dataset")) {
                                    JsonNode hasDatasetNode = classificationNode.findPath("has_dataset"); 
                                    if ((hasDatasetNode != null) && (!hasDatasetNode.isMissingNode())) {
                                        hasDatasetScores.add(hasDatasetNode.doubleValue());
                                    }
                                } else {
                                    scoresPerDatatypes.put(field, classificationNode.get(field).doubleValue());
                                }
                            }
                            
                            for (Map.Entry<String, Double> entry : scoresPerDatatypes.entrySet()) {
                                if (entry.getValue() > bestScore) {
                                    bestScore = entry.getValue();
                                    bestType = entry.getKey();
                                }
                            }

                            bestTypes.add(bestType);
                            bestScores.add(bestScore);
                        }
                    }
                } catch(JsonProcessingException e) {
                    LOGGER.error("Parsing of dataseer classifier JSON result failed", e);
                } catch(Exception e) {
                    LOGGER.error("Error when applying dataseer sentence classifier", e);
                }

            } catch(Exception e) {
                e.printStackTrace();
            }

            System.out.println("bestTypes size: " + bestTypes.size());
            System.out.println("bestScores size: " + bestScores.size());
            System.out.println("hasDatasetScores size: " + hasDatasetScores.size());

            int i = 0;
            for(List<Dataset> localDatasets : entities) {
                for(Dataset localDataset : localDatasets) {
                    if (localDataset.getType() == DatasetType.DATASET && (bestTypes.get(i) != null) && localDataset.getDataset() != null) {
                        localDataset.getDataset().setBestDataType(bestTypes.get(i));
                        localDataset.getDataset().setBestDataTypeScore(bestScores.get(i));
                        localDataset.getDataset().setHasDatasetScore(hasDatasetScores.get(i));
                    }
                }
                i++;
            }

            // selection of relevant data sections
            //List<Boolean> relevantSections = DataseerParser.getInstance().processingText(segments, sectionTypes, nbDatasets, datasetTypes);

            // filter implicit datasets based on selected relevant data section
            List<List<Dataset>> filteredEntities = new ArrayList<>();
            int index = 0;
            for(List<Dataset> localDatasets : entities) {
                List<Dataset> filteredLocalEntities = new ArrayList<>();

                int currentZone = mapSentencesToZones.get(index);

                //System.out.println("sentence index: " + index);
                //System.out.println("currentZone: " + mapSentencesToZones.get(index));
                //System.out.println("relevantSections: " + relevantSections.get(currentZone));

                if (!relevantSections.get(currentZone)) {
                    index++;
                    continue;
                }

                for (Dataset localDataset : localDatasets) {
                    if (localDataset.getType() == DatasetType.DATASET && !relevantSections.get(currentZone)) {
                        continue;
                    } else if (localDataset.getType() == DatasetType.DATASET &&
                        localDataset.getDataset() != null && 
                        localDataset.getDataset().getHasDatasetScore() < 0.5) {
                        continue;
                    } else {
                        //localDataset.setContext(localText);
                        filteredLocalEntities.add(localDataset);
                    }
                }

                filteredEntities.add(filteredLocalEntities);
                index++;
            }
            entities = filteredEntities;

            System.out.println(entities.size() + " mentions of interest");

        } catch (Exception e) {
            e.printStackTrace();
            throw new GrobidException("Cannot process pdf file: " + file.getPath());
        }

        return Pair.of(entities, doc);
    }

    /**
     * Process with the dataset model a set of arbitrary sequence of LayoutTokenization
     */ 
    private List<List<Dataset>> processLayoutTokenSequences(List<List<LayoutToken>> layoutTokenList, 
                                                  List<List<Dataset>> entities, 
                                                  boolean disambiguate) {
        List<List<Dataset>> results = processing(layoutTokenList);
        entities.addAll(results);
        return entities;
    }

    /**
     * Process with the dataset model a set of arbitrary sequence of LayoutTokenization
     */ 
    private List<List<Dataset>> processLayoutTokenSequences2(List<List<LayoutToken>> layoutTokenList, 
                                                  List<List<Dataset>> entities, 
                                                  boolean disambiguate) {
        if (this.dataseerClassifier == null)
            dataseerClassifier = DataseerClassifier.getInstance();
        List<List<LayoutToken>> allLayoutTokens = new ArrayList<>();
        List<String> allSentences = new ArrayList<>();
        for(List<LayoutToken> layoutTokens : layoutTokenList) {
            layoutTokens = DataseerAnalyzer.getInstance().retokenizeLayoutTokens(layoutTokens);

            if ( (layoutTokens == null) || (layoutTokens.size() == 0) ) {
                allLayoutTokens.add(null);
                continue;
            }

            // positions for lexical match
            List<OffsetPosition> urlPositions = Lexicon.getInstance().tokenPositionsUrlPattern(layoutTokens);
            
            // segment into sentences
            String localText = LayoutTokensUtil.toText(layoutTokens);
            List<OffsetPosition> sentencePositions = SentenceUtilities.getInstance().runSentenceDetection(localText, urlPositions, layoutTokens, null);
            if (sentencePositions == null) {
                sentencePositions = new ArrayList<>();
                sentencePositions.add(new OffsetPosition(0, localText.length()));
            }

            //int posIndex = 0;
            for(OffsetPosition sentencePosition : sentencePositions) {
                int startPos = sentencePosition.start;
                int endPos = sentencePosition.end;

                List<LayoutToken> sentenceTokens = new ArrayList<>();
                int pos = 0;
                for(LayoutToken token : layoutTokens) {
                    if (startPos <= pos && (pos+token.getText().length()) <= endPos) {
                        sentenceTokens.add(token);
                    } else if (endPos < (pos+token.getText().length())) {
                        break;
                    }
                    pos += token.getText().length();
                }

                allLayoutTokens.add(sentenceTokens);
                allSentences.add(localText.substring(startPos, endPos));
            }
        }

        List<List<Dataset>> results = processing(allLayoutTokens);
        
        List<Boolean> hasDatasets = new ArrayList<>();

        ObjectMapper mapper = new ObjectMapper();
        try {
            String jsonClassification = dataseerClassifier.classifyBinary(allSentences);
            //System.out.println(jsonClassification);
            JsonNode root = mapper.readTree(jsonClassification);
            JsonNode classificationsNode = root.findPath("classifications");
            if ((classificationsNode != null) && (!classificationsNode.isMissingNode())) {
                Iterator<JsonNode> ite = classificationsNode.elements();
                while (ite.hasNext()) {
                    JsonNode classificationNode = ite.next();
                    JsonNode datasetNode = classificationNode.findPath("dataset");
                    JsonNode noDatasetNode = classificationNode.findPath("no_dataset");

                    if ((datasetNode != null) && (!datasetNode.isMissingNode()) &&
                        (noDatasetNode != null) && (!noDatasetNode.isMissingNode()) ) {
                        double probDataset = datasetNode.asDouble();
                        double probNoDataset = noDatasetNode.asDouble();

                        //System.out.println(probDataset + " " + probNoDataset);
                        if (probDataset > probNoDataset) 
                            hasDatasets.add(true);
                        else 
                            hasDatasets.add(false);
                    }
                }
            }
        } catch(JsonProcessingException e) {
            LOGGER.error("Parsing of dataseer classifier JSON result failed", e);
        } catch(Exception e) {
            LOGGER.error("Error when applying dataseer sentence classifier", e);
        }

        System.out.println("hasDatasets size: " + hasDatasets.size());
        System.out.println("allLayoutTokens size: " + allLayoutTokens.size());

        for (int i=0; i<allLayoutTokens.size(); i++) {
            List<LayoutToken> layoutTokens = allLayoutTokens.get(i);
            boolean hasDataset = hasDatasets.get(i);

            if ( (layoutTokens == null) || (layoutTokens.size() == 0) )
                continue;

            List<Dataset> localEntities = results.get(i);
            if (localEntities == null || localEntities.size() == 0) 
                continue;

            // text of the selected segment
            //String text = LayoutTokensUtil.toText(layoutTokens);            

            // note using dehyphenized text looks nicer, but break entity-level offsets
            // we would need to re-align offsets in a post-processing if we go with 
            // dehyphenized text in the context
            String localText = LayoutTokensUtil.normalizeDehyphenizeText(layoutTokens);

            //System.out.println(hasDataset + " " + localEntities.size() + " mentions / " + localText);

            List<Dataset> filteredLocalEntities = new ArrayList<>();
            if (!hasDataset) {
                for (Dataset localDataset : localEntities) {
                    if (localDataset.getType() == DatasetType.DATASET) {
                        continue;
                    } else {
                        localDataset.setContext(localText);
                        filteredLocalEntities.add(localDataset);
                    }
                }
            } else {
                for (Dataset localDataset : localEntities) {
                    localDataset.setContext(localText);
                }
                filteredLocalEntities = localEntities;
            }

            // disambiguation
            /*if (disambiguate) {
                localEntities = disambiguator.disambiguate(localEntities, layoutTokens);

                // apply existing filtering
                List<Integer> indexToBeFiltered = new ArrayList<>();
                int k = 0;
                for(Dataset entity : localEntities) {
                    if (entity.isFiltered()) {
                        indexToBeFiltered.add(new Integer(k));
                    }
                    k++;
                }

                if (indexToBeFiltered.size() > 0) {
                    for(int j=indexToBeFiltered.size()-1; j>= 0; j--) {
                        localEntities.remove(indexToBeFiltered.get(j).intValue());
                    }
                }
            }*/

            // enrich datasets with predicted data types
            /*List<String> datasetContexts = new ArrayList<>();
            for (Dataset localDataset : localEntities) {
                if (localDataset.getType() == DatasetType.DATASET) {
                    String context = localDataset.getContext();
                    datasetContexts.add(context);
                }
            }

            String jsonClassification = dataseerClassifier.classifyBinary(datasetContexts);

            for(Dataset localDataset : localEntities) {
                if (localDataset.getType() == DatasetType.DATASET) {
                    String context = localDataset.getContext();


                }
            }

            //addContext(localEntities, text, layoutTokens, true);
            entities.add(filteredLocalEntities);*/
        }

        return entities;
    }

    public static boolean isNewParagraph(TaggingLabel lastClusterLabel) {
        return (!TEIFormatter.MARKER_LABELS.contains(lastClusterLabel) && lastClusterLabel != TaggingLabels.FIGURE
                && lastClusterLabel != TaggingLabels.TABLE);
    }

    public static boolean checkAuthorAnnex(List<LayoutToken> annexTokens) {
        for(int i=0; i<annexTokens.size() && i<10; i++) {
            String localText = annexTokens.get(i).getText();
            if (localText != null && localText.toLowerCase().startsWith("author"))
                return true;
        }
        return false;
    }

}
