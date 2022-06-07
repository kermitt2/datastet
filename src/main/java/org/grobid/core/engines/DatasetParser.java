package org.grobid.core.engines;

import org.apache.commons.io.FileUtils;
import org.grobid.core.GrobidModels;
import org.grobid.core.analyzers.DataseerAnalyzer;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.data.BibDataSet;
import org.grobid.core.data.Dataset;
import org.grobid.core.data.Dataset.DatasetType;
import org.grobid.core.data.DatasetComponent;
import org.grobid.core.data.BiblioComponent;
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
        int maxTokens = 0;
        for (List<LayoutToken> tokens : tokensList) {
            // to be sure it's done, retokenize according to the DataseerAnalyzer
            tokens = DataseerAnalyzer.getInstance().retokenizeLayoutTokens(tokens);
            newTokensList.add(tokens);

            // create basic input without features
            int nbTokens = 0;
            for(LayoutToken token : tokens) {
                if (token.getText().trim().length() == 0) {
                    //System.out.println("skipped: " + token.getText());
                    continue;
                }
                input.append(token.getText());
                input.append("\n");
                nbTokens++;
            }

            if (nbTokens > maxTokens)
                maxTokens = nbTokens;
            
            //inputs.add(input.toString());
            input.append("\n\n");
            total++;
        }

        System.out.println("total size: " + total);
        System.out.println("max token sequence: " + maxTokens);

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
            List<Integer> sentenceOffsetStarts = new ArrayList<>();
            int zoneIndex = 0;
            int accumulatedOffset = 0;
            Map<Integer,Integer> mapSentencesToZones = new HashMap<>();
            for(List<LayoutToken> layoutTokens : selectedLayoutTokenSequences) {
                layoutTokens = DataseerAnalyzer.getInstance().retokenizeLayoutTokens(layoutTokens);

                if ( (layoutTokens == null) || (layoutTokens.size() == 0) ) {
                    //allLayoutTokens.add(null);
                    //allSentences.add(null);
                    List<LayoutToken> dummyLayoutTokens = new ArrayList<>();
                    dummyLayoutTokens. add(new LayoutToken("dummy"));
                    allLayoutTokens.add(dummyLayoutTokens);
                    //System.out.println("dummy sentence at " + (allSentences.size()));
                    allSentences.add("dummy");
                    continue;
                }

                accumulatedOffset = layoutTokens.get(0).getOffset();

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

                    sentenceOffsetStarts.add(accumulatedOffset+startPos);

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
            processLayoutTokenSequences(allLayoutTokens, entities, sentenceOffsetStarts, disambiguate);

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
                if (localDatasets == null || localDatasets.size() == 0) {
                    continue;
                }
                for(Dataset localDataset : localDatasets) {
                    if (localDataset == null)
                        continue;

                    if (localDataset.getType() == DatasetType.DATASET && (bestTypes.get(i) != null) && localDataset.getDataset() != null) {
                        localDataset.getDataset().setBestDataType(bestTypes.get(i));
                        localDataset.getDataset().setBestDataTypeScore(bestScores.get(i));
                        localDataset.getDataset().setHasDatasetScore(hasDatasetScores.get(i));
                    }
                }
                i++;
            }

            // we prepare a matcher for all the identified dataset mention forms 
            FastMatcher termPattern = prepareTermPattern(entities);
            // we prepare the frequencies for each dataset name in the whole document
            Map<String, Integer> frequencies = prepareFrequencies(entities, doc.getTokenizations());
            // we prepare a map for mapping a dataset name with its positions of annotation in the document and its IDF
            Map<String, Double> termProfiles = prepareTermProfiles(entities);
            List<List<OffsetPosition>> placeTaken = preparePlaceTaken(entities);

            System.out.println("entities size: " + entities.size());

            int index = 0;
            List<List<Dataset>> newEntities = new ArrayList<>();
            for (List<LayoutToken> sentenceTokens : allLayoutTokens) {
                List<Dataset> localEntities = propagateLayoutTokenSequence(sentenceTokens, 
                                                                        entities.get(index), 
                                                                        termProfiles, 
                                                                        termPattern, 
                                                                        placeTaken.get(index), 
                                                                        frequencies);
                Collections.sort(localEntities);
                newEntities.add(localEntities);
                index++;
            }
            entities = newEntities;

            // selection of relevant data sections
            //List<Boolean> relevantSections = DataseerParser.getInstance().processingText(segments, sectionTypes, nbDatasets, datasetTypes);

            // filter implicit datasets based on selected relevant data section
            List<List<Dataset>> filteredEntities = new ArrayList<>();
            index = 0;
            for(List<Dataset> localDatasets : entities) {
                List<Dataset> filteredLocalEntities = new ArrayList<>();

                if (mapSentencesToZones.get(index) == null) {
                    index++;
                    continue;
                }

                Integer currentZoneObject = mapSentencesToZones.get(index);
                if (currentZoneObject == null) {
                    index++;
                    continue;
                }  

                int currentZone = currentZoneObject.intValue();

                /*System.out.println("\nsentence index: " + index);
                System.out.println("currentZone: " + mapSentencesToZones.get(index));
                System.out.println("relevantSections: " + relevantSections.get(currentZone));*/

                if (!relevantSections.get(currentZone)) {
                    index++;
                    continue;
                }

                for (Dataset localDataset : localDatasets) {

                    if (localDataset.getType() == DatasetType.DATASET &&
                        localDataset.getDataset() != null && 
                        localDataset.getDataset().getHasDatasetScore() < 0.5) {
                        continue;
                    } 
                        
                    filteredLocalEntities.add(localDataset);
                }

                filteredEntities.add(filteredLocalEntities);
                index++;
            }
            entities = filteredEntities;

            System.out.println(entities.size() + " mentions of interest");
            
            // we attach and match bibliographical reference callout
            TEIFormatter formatter = new TEIFormatter(doc, parsers.getFullTextParser());
            // second pass, body
            if ( (bodyClusters != null) && (resCitations != null) && (resCitations.size() > 0) ) {
                List<BiblioComponent> bibRefComponents = new ArrayList<BiblioComponent>();
                for (TaggingTokenCluster cluster : bodyClusters) {
                    if (cluster == null) {
                        continue;
                    }

                    TaggingLabel clusterLabel = cluster.getTaggingLabel();

                    List<LayoutToken> localTokenization = cluster.concatTokens();
                    if ((localTokenization == null) || (localTokenization.size() == 0))
                        continue;

                    if (clusterLabel.equals(TaggingLabels.CITATION_MARKER)) {
                        List<LayoutToken> refTokens = TextUtilities.dehyphenize(localTokenization);
                        String chunkRefString = LayoutTokensUtil.toText(refTokens);

                        List<nu.xom.Node> refNodes = formatter.markReferencesTEILuceneBased(refTokens,
                                    doc.getReferenceMarkerMatcher(),
                                    true, // generate coordinates
                                    false); // do not mark unsolved callout as ref

                        if (refNodes != null) {                            
                            for (nu.xom.Node refNode : refNodes) {
                                if (refNode instanceof Element) {
                                    // get the bib ref key
                                    String refKey = ((Element)refNode).getAttributeValue("target");
                       
                                    if (refKey == null)
                                        continue;

                                    int refKeyVal = -1;
                                    if (refKey.startsWith("#b")) {
                                        refKey = refKey.substring(2, refKey.length());
                                        try {
                                            refKeyVal = Integer.parseInt(refKey);
                                        } catch(Exception e) {
                                            LOGGER.warn("Invalid ref identifier: " + refKey);
                                        }
                                    }
                                    if (refKeyVal == -1)
                                        continue;

                                    // get the bibref object
                                    BibDataSet resBib = resCitations.get(refKeyVal);
                                    if (resBib != null) {
                                        BiblioComponent biblioComponent = new BiblioComponent(resBib.getResBib(), refKeyVal);
                                        biblioComponent.setRawForm(refNode.getValue());
                                        biblioComponent.setOffsetStart(refTokens.get(0).getOffset());
                                        biblioComponent.setOffsetEnd(refTokens.get(refTokens.size()-1).getOffset() + 
                                            refTokens.get(refTokens.size()-1).getText().length());
                                        List<BoundingBox> boundingBoxes = BoundingBoxCalculator.calculate(refTokens);
                                        biblioComponent.setBoundingBoxes(boundingBoxes);
                                        bibRefComponents.add(biblioComponent);
                                    }
                                }
                            }
                        }
                    }
                }


                if (bibRefComponents.size() > 0) {
                    // attach references to dataset entities 
                    entities = attachRefBib(entities, bibRefComponents);
                }

                // consolidate the attached ref bib (we don't consolidate all bibliographical references
                // to avoid useless costly computation)
                List<BibDataSet> citationsToConsolidate = new ArrayList<BibDataSet>();
                List<Integer> consolidated = new ArrayList<Integer>();
                for(List<Dataset> datasets : entities) {
                    for(Dataset entity : datasets) {
                        if (entity.getBibRefs() != null && entity.getBibRefs().size() > 0) {
                            List<BiblioComponent> bibRefs = entity.getBibRefs();
                            for(BiblioComponent bibRef: bibRefs) {
                                Integer refKeyVal = new Integer(bibRef.getRefKey());
                                if (!consolidated.contains(refKeyVal)) {
                                    citationsToConsolidate.add(resCitations.get(refKeyVal));
                                    consolidated.add(refKeyVal);
                                }
                            }
                        }
                    }
                }

                try {
                    Consolidation consolidator = Consolidation.getInstance();
                    Map<Integer,BiblioItem> resConsolidation = consolidator.consolidate(citationsToConsolidate);
                    for(int j=0; j<citationsToConsolidate.size(); j++) {
                        BiblioItem resCitation = citationsToConsolidate.get(j).getResBib();
                        BiblioItem bibo = resConsolidation.get(j);
                        if (bibo != null) {
                            BiblioItem.correct(resCitation, bibo);
                        }
                    }
                } catch(Exception e) {
                    throw new GrobidException(
                    "An exception occured while running consolidation on bibliographical references.", e);
                }

                // propagate the bib. ref. to the entities corresponding to the same dataset name without bib. ref.
                for(List<Dataset> datasets1 : entities) {
                    for(Dataset entity1 : datasets1) {
                        if (entity1.getBibRefs() != null && entity1.getBibRefs().size() > 0) {
                            for(List<Dataset> datasets2 : entities) {
                                for(Dataset entity2 : datasets2) {
                                    if (entity2.getBibRefs() != null) {
                                        continue;
                                    }
                                    if (entity2.getDatasetName() != null && 
                                        entity2.getDatasetName().getNormalizedForm().equals(entity1.getDatasetName().getNormalizedForm())) {
                                        List<BiblioComponent> newBibRefs = new ArrayList<>();
                                        for(BiblioComponent bibComponent : entity1.getBibRefs()) {
                                            newBibRefs.add(new BiblioComponent(bibComponent));
                                        }
                                        entity2.setBibRefs(newBibRefs);
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
                                                  List<Integer> sentenceOffsetStarts,
                                                  boolean disambiguate) {
        List<List<Dataset>> results = processing(layoutTokenList);
        entities.addAll(results);

        int i = 0;
        for(List<Dataset> datasets : entities) {
            if (datasets == null) {
                i++;
                continue;
            }
            for(Dataset dataset : datasets) {
                if (dataset == null)
                    continue;
                dataset.setGlobalContextOffset(sentenceOffsetStarts.get(i));
            }
            i++;
        }

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

    /**
     * Try to attach relevant bib ref component to dataset entities
     */
    public List<List<Dataset>> attachRefBib(List<List<Dataset>> entities, List<BiblioComponent> refBibComponents) {

        // we anchor the process to the dataset names and aggregate other closest components on the right
        // if we cross a bib ref component we attach it, if a bib ref component is just after the last 
        // component of the entity group, we attach it 
        for(List<Dataset> datasets : entities) {
            for(Dataset entity : datasets) {
                if (entity.getDatasetName() == null)
                    continue;

                // positions are relative to the context if present, so they have to be shifted in this case
                // to be comparable with reference marker offsets
                int shiftOffset = 0;
                if (entity.getGlobalContextOffset() != -1) {
                    shiftOffset = entity.getGlobalContextOffset();
                }

                // find the name component
                DatasetComponent nameComponent = entity.getDatasetName();
                int pos = nameComponent.getOffsetEnd() + shiftOffset;
                
                // find end boundary
                int endPos = pos;
                /*List<SoftwareComponent> theComps = new ArrayList<>();
                DatasetComponent comp = entity.getVersion();
                if (comp != null) 
                    theComps.add(comp);
                comp = entity.getCreator();
                if (comp != null) 
                    theComps.add(comp);
                comp = entity.getSoftwareURL();
                if (comp != null) 
                    theComps.add(comp);

                for(DatasetComponent theComp : theComps) {
                    int localPos = theComp.getOffsetEnd() + shiftOffset;
                    if (localPos > endPos)
                        endPos = localPos;
                }*/

                //System.out.println(nameComponent.getRawForm() + ": " + endPos);

                // find included or just next bib ref callout
                for(BiblioComponent refBib : refBibComponents) {
                    //System.out.println(refBib.getOffsetStart() + " - " + refBib.getOffsetStart());
                    if ( (refBib.getOffsetStart() >= pos) &&
                         (refBib.getOffsetStart() <= endPos+5) ) {
                        entity.addBibRef(refBib);
                        endPos = refBib.getOffsetEnd();
                    }
                }
            }
        }
        
        return entities;
    }

    public List<List<OffsetPosition>> preparePlaceTaken(List<List<Dataset>> entities) {
        List<List<OffsetPosition>> localPositions = new ArrayList<>();
        for(List<Dataset> datasets : entities) {
            List<OffsetPosition> localSentencePositions = new ArrayList<>();
            for(Dataset entity : datasets) {
                DatasetComponent nameComponent = entity.getDatasetName();
                if (nameComponent != null) {
                    List<LayoutToken> localTokens = nameComponent.getTokens();
                    localSentencePositions.add(new OffsetPosition(localTokens.get(0).getOffset(), 
                        localTokens.get(localTokens.size()-1).getOffset() + localTokens.get(localTokens.size()-1).getText().length()-1));
                    DatasetComponent publisherComponent = entity.getPublisher();
                    if (publisherComponent != null) {
                        localTokens = publisherComponent.getTokens();
                        if (localTokens.size() > 0) {
                            localSentencePositions.add(new OffsetPosition(localTokens.get(0).getOffset(), 
                                localTokens.get(localTokens.size()-1).getOffset() + localTokens.get(localTokens.size()-1).getText().length()-1));
                        }
                    }
                }
                nameComponent = entity.getDataset();
                if (nameComponent != null) {
                    List<LayoutToken> localTokens = nameComponent.getTokens();
                    localSentencePositions.add(new OffsetPosition(localTokens.get(0).getOffset(), 
                        localTokens.get(localTokens.size()-1).getOffset() + localTokens.get(localTokens.size()-1).getText().length()-1));

                    DatasetComponent deviceComponent = entity.getDataDevice();
                    if (deviceComponent != null) {
                        localTokens = deviceComponent.getTokens();
                        if (localTokens.size() > 0) {
                            localSentencePositions.add(new OffsetPosition(localTokens.get(0).getOffset(), 
                                localTokens.get(localTokens.size()-1).getOffset() + localTokens.get(localTokens.size()-1).getText().length()-1));
                        }
                    }
                }
                DatasetComponent urlComponent = entity.getUrl();
                if (urlComponent != null) {
                    List<LayoutToken> localTokens = urlComponent.getTokens();
                    if (localTokens.size() > 0) {
                        localSentencePositions.add(new OffsetPosition(localTokens.get(0).getOffset(), 
                            localTokens.get(localTokens.size()-1).getOffset() + localTokens.get(localTokens.size()-1).getText().length()-1));
                    }
                }
            }
            localPositions.add(localSentencePositions);
        }
        return localPositions;
    }

    public Map<String, Double> prepareTermProfiles(List<List<Dataset>> entities) {
        Map<String, Double> result = new TreeMap<String, Double>();

        for(List<Dataset> datasets : entities) {
            for(Dataset entity : datasets) {
                DatasetComponent nameComponent = entity.getDatasetName();
                if (nameComponent == null)
                    continue;
                String term = nameComponent.getRawForm();
                term = term.replace("\n", " ");
                term = term.replaceAll("( )+", " ");

                Double profile = result.get(term);
                if (profile == null) {
                    profile = DataseerLexicon.getInstance().getTermIDF(term);
                    result.put(term, profile);
                }

                if (!term.equals(nameComponent.getNormalizedForm())) {
                    profile = result.get(nameComponent.getNormalizedForm());
                    if (profile == null) {
                        profile = DataseerLexicon.getInstance().getTermIDF(nameComponent.getNormalizedForm());
                        result.put(nameComponent.getNormalizedForm(), profile);
                    }
                }
            }
        }

        return result;
    } 

    public FastMatcher prepareTermPattern(List<List<Dataset>> entities) {
        FastMatcher termPattern = new FastMatcher();
        List<String> added = new ArrayList<>();
        for(List<Dataset> datasets : entities) {
            for(Dataset entity : datasets) {
                DatasetComponent nameComponent = entity.getDatasetName();
                if (nameComponent == null)
                    continue;
                String term = nameComponent.getRawForm();
                term = term.replace("\n", " ");
                term = term.replaceAll("( )+", " ");

                if (term.trim().length() == 0)
                    continue;

                // for safety, we don't propagate something that looks like a stopword with simply an Uppercase first letter
                if (FeatureFactory.getInstance().test_first_capital(term) && 
                    !FeatureFactory.getInstance().test_all_capital(term) &&
                    DataseerLexicon.getInstance().isEnglishStopword(term.toLowerCase()) ) {
                    continue;
                }

                if (!added.contains(term)) {
                    termPattern.loadTerm(term, DataseerAnalyzer.getInstance(), false);
                    added.add(term);
System.out.println("add term: " + term);
                }

                if (!term.equals(nameComponent.getNormalizedForm())) {
                    if (!added.contains(nameComponent.getNormalizedForm())) {
                        termPattern.loadTerm(nameComponent.getNormalizedForm(), DataseerAnalyzer.getInstance(), false);
                        added.add(nameComponent.getNormalizedForm());
                    }
                }
            }
        }
        return termPattern;
    }

    public Map<String, Integer> prepareFrequencies(List<List<Dataset>> entities, List<LayoutToken> tokens) {
        Map<String, Integer> frequencies = new TreeMap<String, Integer>();
        for(List<Dataset> datasets : entities) {
            for(Dataset entity : datasets) {
                DatasetComponent nameComponent = entity.getDatasetName();
                if (nameComponent == null)
                    continue;
                String term = nameComponent.getRawForm();
                if (frequencies.get(term) == null) {
                    FastMatcher localTermPattern = new FastMatcher();
                    localTermPattern.loadTerm(term, DataseerAnalyzer.getInstance());
                    List<OffsetPosition> results = localTermPattern.matchLayoutToken(tokens, true, true);
                    // ignore delimiters, but case sensitive matching
                    int freq = 0;
                    if (results != null) {  
                        freq = results.size();
                    }
                    frequencies.put(term, new Integer(freq));
                }
            }
        }
        return frequencies;
    }

    public List<Dataset> propagateLayoutTokenSequence(List<LayoutToken> layoutTokens, 
                                              List<Dataset> entities,
                                              Map<String, Double> termProfiles,
                                              FastMatcher termPattern, 
                                              List<OffsetPosition> placeTaken,
                                              Map<String, Integer> frequencies) {

        List<OffsetPosition> results = termPattern.matchLayoutToken(layoutTokens, true, true);
        // above: do not ignore delimiters and case sensitive matching

        if ( (results == null) || (results.size() == 0) ) {
            return entities;
        }

        String localText = LayoutTokensUtil.toText(layoutTokens);
        //System.out.println(results.size() + " results for: " + localText);

        for(OffsetPosition position : results) {
            // the match positions are expressed relative to the local layoutTokens index, while the offset at
            // token level are expressed relative to the complete doc positions in characters
            List<LayoutToken> matchedTokens = layoutTokens.subList(position.start, position.end+1);
            
            // we recompute matched position using local tokens (safer than using doc level offsets)
            int matchedPositionStart = 0;
            for(int i=0; i < position.start; i++) {
                LayoutToken theToken = layoutTokens.get(i);
                if (theToken.getText() == null)
                    continue;
                matchedPositionStart += theToken.getText().length();
            }

            String term = LayoutTokensUtil.toText(matchedTokens);
            OffsetPosition matchedPosition = new OffsetPosition(matchedPositionStart, matchedPositionStart+term.length());

            // this positions is expressed at document-level, to check if we have not matched something already recognized
            OffsetPosition rawMatchedPosition = new OffsetPosition(
                matchedTokens.get(0).getOffset(),
                matchedTokens.get(matchedTokens.size()-1).getOffset() + matchedTokens.get(matchedTokens.size()-1).getText().length()
            );

            int termFrequency = 1;
            if (frequencies != null && frequencies.get(term) != null)
                termFrequency = frequencies.get(term);

            // check the tf-idf of the term
            double tfidf = -1.0;
            
            // is the match already present in the entity list? 
            if (overlapsPosition(placeTaken, rawMatchedPosition)) {
                continue;
            }
            if (termProfiles.get(term) != null) {
                tfidf = termFrequency * termProfiles.get(term);
            }

            // ideally we should make a small classifier here with entity frequency, tfidf, disambiguation success and 
            // and/or log-likelyhood/dice coefficient as features - but for the time being we introduce a simple rule
            // with an experimentally defined threshold:
            if ( (tfidf <= 0) || (tfidf > 0.001) ) {
                // add new entity mention
                DatasetComponent name = new DatasetComponent();
                name.setRawForm(term);
                // these offsets are relative now to the local layoutTokens sequence
                name.setOffsetStart(matchedPosition.start);
                name.setOffsetEnd(matchedPosition.end);
                name.setLabel(DatasetTaggingLabels.DATASET_NAME);
                name.setTokens(matchedTokens);

                List<BoundingBox> boundingBoxes = BoundingBoxCalculator.calculate(matchedTokens);
                name.setBoundingBoxes(boundingBoxes);

                Dataset entity = new Dataset(DatasetType.DATASET_NAME, name.getRawForm());
                entity.setDatasetName(name);
                entity.setContext(localText);
                //entity.setType(DataseerLexicon.Dataset_Type.DATASET);
                entity.setPropagated(true);
                if (entities == null) 
                    entities = new ArrayList<>();
                entities.add(entity);
            }
        }
        return entities;
    }

    private boolean overlapsPosition(final List<OffsetPosition> list, final OffsetPosition position) {
        for (OffsetPosition pos : list) {
            if (pos.start == position.start)  
                return true;
            if (pos.end == position.end)  
                return true;
            if (position.start <= pos.start &&  pos.start <= position.end)  
                return true;
            if (pos.start <= position.start && position.start <= pos.end)  
                return true;
        } 
        return false;
    }

}
