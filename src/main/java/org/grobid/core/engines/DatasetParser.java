package org.grobid.core.engines;

import org.apache.commons.io.FileUtils;
import org.grobid.core.GrobidModels;
import org.grobid.core.analyzers.DataseerAnalyzer;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.data.BibDataSet;
import org.grobid.core.document.Document;
import org.grobid.core.document.DocumentPiece;
import org.grobid.core.document.DocumentSource;
import org.grobid.core.document.TEIFormatter;
import org.grobid.core.document.xml.XmlBuilderUtils;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.engines.label.SegmentationLabels;
import org.grobid.core.engines.label.TaggingLabel;
import org.grobid.core.engines.label.TaggingLabels;
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
    private static final Logger logger = LoggerFactory.getLogger(DatasetParser.class);

    private static volatile DatasetParser instance;

    private DataseerLexicon softwareLexicon = null;
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
        super(GrobidModels.DATASET, CntManagerFactory.getCntManager(), 
            GrobidCRFEngine.valueOf(configuration.getModel().engine.toUpperCase()),
            configuration.getModel().delft.architecture);

        dataseerLexicon = SoftwareLexicon.getInstance();
        parsers = new EngineParsers();
        dataseerConfiguration = configuration;
    }

    /**
     * Sequence labelling of a list of layout tokens for identifying dataset names. 
     *
     * @param tokens the list of LayoutTokens to be labeled
     * 
     * @return list of identified Dataset objects. 
     */
    public List<Dataset> processing(List<LayoutToken> tokens) {
        List<Dataset> result = new ArrayList<>();
        if (tokens == null || tokens.size() == 0) {
            return result;
        }

        // retokenize according to the DataseerAnalyzer
        List<LayoutToken> tokens = retokenizeLayoutTokens(tokens);

        // create basic input without features
        StringBuilder input = new StringBuilder();


        String allRes = null;
        try {
            allRes = label(featuredInput.toString());
        } catch (Exception e) {
            LOGGER.error("An exception occured while labeling a citation.", e);
            throw new GrobidException(
                    "An exception occured while labeling a citation.", e);
        }

        if (allRes == null || allRes.length() == 0)
            return null;
        String[] resBlocks = allRes.split("\n\n");
        for (List<LayoutToken> tokens : tokenList) {
            if (CollectionUtils.isEmpty(localTokens))
                results.add(null);
            else {
                List<Dataset> localDatasets = resultExtractionLayoutTokens(res, true, localTokens);
                
                
                
                
                
            }

        }



    }

    /**
     * Sequence labelling of a string for identifying dataset names. 
     *
     * @param tokens the list of LayoutTokens to be labeled
     * 
     * @return list of identified Dataset objects. 
     */
     */ 
    public BiblioItem processingString(String input) {
        input = UnicodeUtil.normaliseText(input);
        List<LayoutToken> tokens = analyzer.tokenizeWithLayoutToken(input);
        return processing(tokens);
    }
}