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
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.features.FeaturesVectorDataseer;
import org.grobid.core.features.FeatureFactory;
import org.grobid.core.layout.BoundingBox;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.LayoutTokenization;
import org.grobid.core.lexicon.DataseerLexicon;
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
        System.out.println(DatasetModels.DATASET);
        System.out.println(GrobidCRFEngine.valueOf(configuration.getModel("datasets").engine.toUpperCase()));
        System.out.println(configuration.getModel("datasets").delft.architecture);
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
        List<List<LayoutToken>> newTokensList = new ArrayList<>();
        for (List<LayoutToken> tokens : tokensList) {
            // retokenize according to the DataseerAnalyzer
            tokens = DataseerAnalyzer.getInstance().retokenizeLayoutTokens(tokens);
            newTokensList.add(tokens);

            // create basic input without features
            
            for(LayoutToken token : tokens) {
                if (token.getText().trim().length() == 0)
                    continue;
                input.append(token.getText());
                input.append("\n");
            }

            input.append("\n\n");
        }

        tokensList= newTokensList;

        String allRes = null;
        try {
            allRes = label(input.toString());
        } catch (Exception e) {
            LOGGER.error("An exception occured while labeling a citation.", e);
            throw new GrobidException(
                    "An exception occured while labeling a citation.", e);
        }

        if (allRes == null || allRes.length() == 0)
            return null;
        String[] resBlocks = allRes.split("\n\n");
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
        Dataset dataset = null;

        String text = LayoutTokensUtil.toText(tokenizations);

        TaggingTokenClusteror clusteror = new TaggingTokenClusteror(DatasetModels.DATASET, result, tokenizations);
        List<TaggingTokenCluster> clusters = clusteror.cluster();

        int pos = 0; // position in term of characters for creating the offsets

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
                dataset = new Dataset(DatasetType.DATASET_NAME, clusterText);
            } else if (clusterLabel.equals(DatasetTaggingLabels.DATASET)) {
                dataset = new Dataset(DatasetType.DATASET_EXPRESSION, clusterText);
            } else if (clusterLabel.equals(DatasetTaggingLabels.DATA_DEVICE)) {
                dataset = new Dataset(DatasetType.DATA_DEVICE, clusterText);
            }

            if (dataset != null) {
                datasets.add(dataset);
                dataset = null;

                dataset.setOffsetStart(pos);
                dataset.setOffsetEnd(endPos);

                dataset.setLabel(clusterLabel);
                dataset.setTokens(theTokens);

                List<BoundingBox> boundingBoxes = BoundingBoxCalculator.calculate(cluster.concatTokens());
                dataset.setBoundingBoxes(boundingBoxes);
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
}