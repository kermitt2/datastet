package org.grobid.core.engines;

import org.apache.commons.io.FileUtils;
import org.grobid.core.GrobidModels;
import org.grobid.core.analyzers.DataseerAnalyzer;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.data.BibDataSet;
import org.grobid.core.data.Dataset;
import org.grobid.core.data.Dataset.DatasetType;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

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
        System.out.println("to process: " + tokensList.size());

        List<List<Dataset>> results = new ArrayList<>();
        if (tokensList == null || tokensList.size() == 0) {
            return results;
        }

        StringBuilder input = new StringBuilder();
        //List<String> inputs = new ArrayList<>();
        List<List<LayoutToken>> newTokensList = new ArrayList<>();
        for (List<LayoutToken> tokens : tokensList) {
            // to be sure it's done, retokenize according to the DataseerAnalyzer
            tokens = DataseerAnalyzer.getInstance().retokenizeLayoutTokens(tokens);
            newTokensList.add(tokens);

            // create basic input without features
            
            for(LayoutToken token : tokens) {
                if (token.getText().trim().length() == 0)
                    continue;
                input.append(token.getText());
                input.append("\n");
            }

            //inputs.add(input.toString());
            input.append("\n\n");
        }

        //System.out.println("inputs size: " + inputs.size());

        tokensList = newTokensList;

        String allRes = null;
        try {
            allRes = label(input.toString());
        } catch (Exception e) {
            LOGGER.error("An exception occured while labeling a sequence.", e);
            throw new GrobidException(
                    "An exception occured while labeling a sequence.", e);
        }

        //System.out.println(allRes);

        if (allRes == null || allRes.length() == 0)
            return results;

        //System.out.println(allRes);

        String[] resBlocks = allRes.split("\n\n");

        System.out.println("resBlocks: " + resBlocks.length);

        int i = 0;
        for (List<LayoutToken> tokens : tokensList) {
            if (tokens == null || tokens.size() == 0) {
                results.add(null);
            } else {
                List<Dataset> localDatasets = resultExtractionLayoutTokens(resBlocks[i], tokens);
                results.add(localDatasets);
            }
            i++;
        }

        return results;
    }

    private List<Dataset> resultExtractionLayoutTokens(String result, List<LayoutToken> tokenizations) {
        List<Dataset> datasets = new ArrayList<>();
        
        String text = LayoutTokensUtil.toText(tokenizations);

        TaggingTokenClusteror clusteror = new TaggingTokenClusteror(DatasetModels.DATASET, result, tokenizations);
        List<TaggingTokenCluster> clusters = clusteror.cluster();

        int pos = 0; // position in term of characters for creating the offsets
        Dataset dataset = null;

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
                dataset = new Dataset(DatasetType.DATASET_NAME, text.substring(pos, endPos));
            } else if (clusterLabel.equals(DatasetTaggingLabels.DATASET)) {
                dataset = new Dataset(DatasetType.DATASET, text.substring(pos, endPos));
            } else if (clusterLabel.equals(DatasetTaggingLabels.DATA_DEVICE)) {
                dataset = new Dataset(DatasetType.DATA_DEVICE, text.substring(pos, endPos));
            } 

            if (dataset != null) {
                dataset.setOffsetStart(pos);
                dataset.setOffsetEnd(endPos);
                
                dataset.setLabel(clusterLabel);
                dataset.setTokens(theTokens);
                
                List<BoundingBox> boundingBoxes = BoundingBoxCalculator.calculate(cluster.concatTokens());
                dataset.setBoundingBoxes(boundingBoxes);
                
                datasets.add(dataset);
                
                dataset = null;
            }

            pos = endPos;
        }

        return datasets;
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
                                                        boolean disambiguate, 
                                                        boolean addParagraphContext) throws IOException {
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
                    } 

                    // abstract
                    List<LayoutToken> abstractTokens = resHeader.getLayoutTokens(TaggingLabels.HEADER_ABSTRACT);
                    if (abstractTokens != null) {
                        selectedLayoutTokenSequences.add(abstractTokens);
                    } 

                    // keywords
                    List<LayoutToken> keywordTokens = resHeader.getLayoutTokens(TaggingLabels.HEADER_KEYWORD);
                    if (keywordTokens != null) {
                        selectedLayoutTokenSequences.add(keywordTokens);
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
                                if (curParagraphTokens != null)
                                    selectedLayoutTokenSequences.add(curParagraphTokens);
                                curParagraphTokens = new ArrayList<>();
                            }
                            curParagraphTokens.addAll(localTokenization);

                            //selectedLayoutTokenSequences.add(localTokenization);
                        } else if (clusterLabel.equals(TaggingLabels.TABLE)) {
                            //processLayoutTokenSequenceTableFigure(localTokenization, entities);
                        } else if (clusterLabel.equals(TaggingLabels.FIGURE)) {
                            //processLayoutTokenSequenceTableFigure(localTokenization, entities);
                        }

                        lastClusterLabel = clusterLabel;
                    }
                    // last paragraph
                    if (curParagraphTokens != null)
                        selectedLayoutTokenSequences.add(curParagraphTokens);
                }
            }

            // we don't process references (although reference titles could be relevant)
            // acknowledgement? 

            // we can process annexes
            documentParts = doc.getDocumentPart(SegmentationLabels.ANNEX);
            if (documentParts != null) {
                //processDocumentPart(documentParts, doc, entities);

                List<LayoutToken> annexTokens = doc.getTokenizationParts(documentParts, doc.getTokenizations());
                if (annexTokens != null) {
                    selectedLayoutTokenSequences.add(annexTokens);
                } 
            }

            // footnotes are also relevant
            documentParts = doc.getDocumentPart(SegmentationLabels.FOOTNOTE);
            if (documentParts != null) {
                //processDocumentPart(documentParts, doc, components);

                List<LayoutToken> footnoteTokens = doc.getTokenizationParts(documentParts, doc.getTokenizations());
                if (footnoteTokens != null) {
                    selectedLayoutTokenSequences.add(footnoteTokens);
                } 
            }

            // actual processing of the selected sequences which have been delayed to be processed in groups and
            // take advantage of deep learning batch
            processLayoutTokenSequenceMultiple(selectedLayoutTokenSequences, entities, disambiguate, addParagraphContext);

            System.out.println(entities.size() + " mentions of interest");

        } catch (Exception e) {
            e.printStackTrace();
            throw new GrobidException("Cannot process pdf file: " + file.getPath());
        }

        return Pair.of(entities, doc);
    }

    /**
     * Process with the dataset model a single arbitrary sequence of LayoutToken objects
     */ 
    private List<List<Dataset>> processLayoutTokenSequenceMultiple(List<List<LayoutToken>> layoutTokenList, 
                                                            List<List<Dataset>> entities,
                                                            boolean disambiguate, 
                                                            boolean addParagraphContext) {
        List<LayoutTokenization> layoutTokenizations = new ArrayList<LayoutTokenization>();
        for(List<LayoutToken> layoutTokens : layoutTokenList)
            layoutTokenizations.add(new LayoutTokenization(layoutTokens));
        return processLayoutTokenSequences(layoutTokenizations, entities, disambiguate, addParagraphContext);
    }

    /**
     * Process with the dataset model a set of arbitrary sequence of LayoutTokenization
     */ 
    private List<List<Dataset>> processLayoutTokenSequences(List<LayoutTokenization> layoutTokenizations, 
                                                  List<List<Dataset>> entities, 
                                                  boolean disambiguate,
                                                  boolean addParagraphContext) {
        List<List<LayoutToken>> allLayoutTokens = new ArrayList<>();
        for(LayoutTokenization layoutTokenization : layoutTokenizations) {
            List<LayoutToken> layoutTokens = layoutTokenization.getTokenization();
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
                for(LayoutToken token : layoutTokens) {
                    if (startPos <= token.getOffset() && (token.getOffset()+token.getText().length()) <= endPos) {
                        sentenceTokens.add(token);
                    } else if (endPos < (token.getOffset()+token.getText().length())) {
                        break;
                    }
                }
                allLayoutTokens.add(sentenceTokens);
            }
        }

        List<List<Dataset>> results = processing(allLayoutTokens);

        System.out.println("results: " + results.size());
        System.out.println("allLayoutTokens: " + allLayoutTokens.size());

        for (int i=0; i<allLayoutTokens.size(); i++) {
            List<LayoutToken> layoutTokens = allLayoutTokens.get(i);
            if ( (layoutTokens == null) || (layoutTokens.size() == 0) )
                continue;

            List<Dataset> localEntities = results.get(i);
            if (localEntities == null || localEntities.size() == 0) 
                continue;

            // text of the selected segment
            String text = LayoutTokensUtil.toText(layoutTokens);            

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
            
            // note using dehyphenized text looks nicer, but break entity-level offsets
            // we would need to re-align offsets in a post-processing if we go with 
            // dehyphenized text in the context
            //text = LayoutTokensUtil.normalizeDehyphenizeText(layoutTokens);
            
            //addContext(localEntities, text, layoutTokens, true, addParagraphContext);
            entities.add(localEntities);
        }

        return entities;
    }

    public static boolean isNewParagraph(TaggingLabel lastClusterLabel) {
        return (!TEIFormatter.MARKER_LABELS.contains(lastClusterLabel) && lastClusterLabel != TaggingLabels.FIGURE
                && lastClusterLabel != TaggingLabels.TABLE);
    }

}