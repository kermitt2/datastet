package org.grobid.core.engines;

import nu.xom.Attribute;
import nu.xom.Element;
import org.apache.commons.io.FileUtils;
import org.grobid.core.GrobidModels;
import org.grobid.core.data.DatasetComponent;
import org.grobid.core.data.Dataset;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.document.Document;
import org.grobid.core.document.DocumentPiece;
import org.grobid.core.document.DocumentSource;
import org.grobid.core.document.xml.XmlBuilderUtils;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.engines.label.DatasetTaggingLabels;
import org.grobid.core.engines.label.SegmentationLabels;
import org.grobid.core.engines.label.TaggingLabel;
import org.grobid.core.engines.label.TaggingLabels;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.features.FeaturesVectorDataseer;
import org.grobid.core.layout.BoundingBox;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.LayoutTokenization;
import org.grobid.core.lexicon.DatastetLexicon;
import org.grobid.core.utilities.DatastetConfiguration;
import org.grobid.core.utilities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.io.*;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.commons.lang3.tuple.Pair;

import static org.apache.commons.lang3.StringUtils.*;
import static org.grobid.core.document.xml.XmlBuilderUtils.teiElement;

/**
 * Dataset entity disambiguator. Once dataset mentions are recognized and grouped
 * into an entity (dataset name with recognized attributes), we use entity-fishing
 * service to disambiguate the dataset against Wikidata, as well as the attribute 
 * values (currently only creator). The main goal is to filter out false positives.
 *
 * @author Patrice
 */
public class DatasetDisambiguator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatasetDisambiguator.class);

    private static volatile DatasetDisambiguator instance;

    private static String nerd_host = null;
    private static String nerd_port = null;

    private static boolean serverStatus = false;

    public static DatasetDisambiguator getInstance(DatastetConfiguration configuration) {
        if (instance == null) {
            getNewInstance(configuration);
        }
        return instance;
    }

    /**
     * Create a new instance.
     */
    private static synchronized void getNewInstance(DatastetConfiguration configuration) {
        instance = new DatasetDisambiguator(configuration);
    }

    private DatasetDisambiguator(DatastetConfiguration configuration) {
        try {
            nerd_host = configuration.getEntityFishingHost();
            nerd_port = configuration.getEntityFishingPort();
            serverStatus = checkIfAlive();
            if (serverStatus == true)
                ensureCustomizationReady();
        } catch(Exception e) {
            LOGGER.error("Cannot read properties for disambiguation service", e);
        }
    }

    private static int CONTEXT_WINDOW = 50;

    /**
     * Check if the disambiguation service is available using its isalive status service
     */
    public boolean checkIfAlive() {
        boolean result = false;
        try {
            URL url = null;
            if ( (nerd_port != null) && (nerd_port.length() > 0) )
                if (nerd_port.equals("443"))
                    url = new URL("https://" + nerd_host + "/service/isalive");
                else
                    url = new URL("http://" + nerd_host + ":" + nerd_port + "/service/isalive");
            else
                url = new URL("http://" + nerd_host + "/service/isalive");

            LOGGER.debug("Calling: " + url.toString());
//System.out.println("Calling: " + url.toString());
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpGet get = new HttpGet(url.toString());

            CloseableHttpResponse response = null;
            Scanner in = null;
            try {
                response = httpClient.execute(get);
//System.out.println(response.getStatusLine());
                int code = response.getStatusLine().getStatusCode();
                if (code != 200) {
                    LOGGER.error("Failed isalive service for disambiguation service entity-fishing, HTTP error code : " + code);
                    return false;
                } else {
                    result = true;
                }
            } finally {
                if (in != null)
                    in.close();
                if (response != null)
                    response.close();
            }
        } catch (MalformedURLException e) {
            LOGGER.error("disambiguation service not available: MalformedURLException");
        } catch (HttpHostConnectException e) {
            LOGGER.error("cannot connect to the disambiguation service");
        } catch(Exception e) {
            LOGGER.error("disambiguation service not available", e);
        }

        return result;
    }

    /**
     * Check if the dataset customisation is ready on the entity-fishing server, if not load it 
     */
    public void ensureCustomizationReady() {
        boolean result = false;
        URL url = null;
        CloseableHttpResponse response = null;
        try {
            if ( (nerd_port != null) && (nerd_port.length() > 0) )
                if (nerd_port.equals("443"))
                    url = new URL("https://" + nerd_host + "/service/customisation/dataset");
                else
                    url = new URL("http://" + nerd_host + ":" + nerd_port + "/service/customisation/dataset");
            else
                url = new URL("http://" + nerd_host + "/service/customisation/dataset");

            LOGGER.debug("Calling: " + url.toString());
//System.out.println("Calling: " + url.toString());
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpGet get = new HttpGet(url.toString());
            Scanner in = null;
            try {
                response = httpClient.execute(get);
//System.out.println(response.getStatusLine());
                int code = response.getStatusLine().getStatusCode();
                if (code != 200) {
                    LOGGER.info("Failed customization lookup service: HTTP error code : " + code + " - the customization will be loaded");
                } else {
                    result = true;
                }
            } finally {
                if (in != null)
                    in.close();
                if (response != null)
                    response.close();
            }
        } catch (MalformedURLException e) {
            LOGGER.error("disambiguation service not available: MalformedURLException");
        } catch (HttpHostConnectException e) {
            LOGGER.error("cannot connect to the disambiguation service");
        } catch(Exception e) {
            LOGGER.error("disambiguation service not available", e);
        }

        if (!result && url != null) {
            LOGGER.info("Dataset customisation not present on server, loading it...");
            try {
                if ( (nerd_port != null) && (nerd_port.length() > 0) )
                    if (nerd_port.equals("443"))
                        url = new URL("https://" + nerd_host + "/service/customisations");
                    else
                       url = new URL("http://" + nerd_host + ":" + nerd_port + "/service/customisations");
                else
                    url = new URL("http://" + nerd_host + "/service/customisations");

                LOGGER.debug("Calling: " + url.toString());
//System.out.println("Calling: " + url.toString());
                // load the dataset customisation
                File cutomisationFile = new File("resources/config/customisation-dataset.json");
                cutomisationFile = new File(cutomisationFile.getAbsolutePath());

                String json = FileUtils.readFileToString(cutomisationFile, "UTF-8");

                CloseableHttpClient httpClient = HttpClients.createDefault();
                HttpPost post = new HttpPost(url.toString());

                //StringBody stringValue = new StringBody(json, ContentType.MULTIPART_FORM_DATA);
                //StringBody stringName = new StringBody("dataset", ContentType.MULTIPART_FORM_DATA);
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
                builder.addTextBody("value", json);
                builder.addTextBody("name", "dataset");
                //builder.addPart("value", stringValue);
                //builder.addPart("name", stringName);
                HttpEntity entity = builder.build();
                try {
                    post.setEntity(entity);
                    response = httpClient.execute(post);
//System.out.println(response.getStatusLine());

                    int code = response.getStatusLine().getStatusCode();
                    if (code != 200) {
                        LOGGER.error("Failed loading dataset customisation: HTTP error code : " + code);
                    } else {
                        LOGGER.info("Dataset customisation loaded");
                    }
                } finally {
                    if (response != null)
                        response.close();
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Disambiguate against Wikidata a list of raw entities extracted from text 
     * represented as a list of tokens. The tokens will be used as disambiguisation 
     * context, as well the other local raw datasets. 
     * 
     * @return list of disambiguated dataset entities
     */
    public List<Dataset> disambiguate(List<Dataset> entities, List<LayoutToken> tokens) {
        if ( (entities == null) || (entities.size() == 0) ) 
            return entities;
        String json = null;
        try {
            json = runNerd(entities, tokens, "en");
        } catch(RuntimeException e) {
            LOGGER.error("Call to entity-fishing failed.", e);
        }
        if (json == null)
            return entities;

        List<Dataset> filteredEntities = new ArrayList<Dataset>();

//System.out.println(json);
        int segmentStartOffset = 0;
        if (tokens != null && tokens.size()>0)
            segmentStartOffset = tokens.get(0).getOffset();

        // build a map for the existing entities in order to catch them easily
        // based on their positions
        Map<Integer, DatasetComponent> entityPositions = new TreeMap<Integer, DatasetComponent>();
        for(Dataset entity : entities) {
            DatasetComponent datasetName = entity.getDatasetName();
            DatasetComponent dataset = entity.getDataset();
            DatasetComponent dataDevice = entity.getDataDevice();

            if (datasetName != null)
                entityPositions.put(Integer.valueOf(datasetName.getOffsetStart()), datasetName);
            if (dataset != null)
                entityPositions.put(Integer.valueOf(dataset.getOffsetStart()), dataset);
            if (dataDevice != null)
                entityPositions.put(Integer.valueOf(dataDevice.getOffsetStart()), dataDevice);
        }

        // merge entity disambiguation with actual extracted mentions
        JsonNode root = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            root = mapper.readTree(json);

            // given that we have potentially a wikipedia identifier, we need the language
            // to be able to solve it in the right wikipedia version
            String lang = null;
            JsonNode languageNode = root.findPath("language");
            if ((languageNode != null) && (!languageNode.isMissingNode())) {
                JsonNode langNode = languageNode.findPath("lang");
                if ((langNode != null) && (!langNode.isMissingNode())) {
                    lang = langNode.textValue();
                }
            }
            
            JsonNode entitiesNode = root.findPath("entities");
            if ((entitiesNode != null) && (!entitiesNode.isMissingNode())) {
                // we have an array of entity
                Iterator<JsonNode> ite = entitiesNode.elements();
                while (ite.hasNext()) {
                    JsonNode entityNode = ite.next();
                    JsonNode startNode = entityNode.findPath("offsetStart");
                    int startOff = -1;
                    //int endOff = -1;
                    if ((startNode != null) && (!startNode.isMissingNode())) {
                        startOff = startNode.intValue();
                    }
                    /*JsonNode endNode = entityNode.findPath("offsetEnd");
                    if ((endNode != null) && (!endNode.isMissingNode())) {
                        endOff = endNode.intValue();
                    }*/
                    double score = -1;
                    JsonNode scoreNode = entityNode.findPath("confidence_score");
                    if ((scoreNode != null) && (!scoreNode.isMissingNode())) {
                        score = scoreNode.doubleValue();
                    }
                    int wikipediaId = -1;
                    JsonNode wikipediaNode = entityNode.findPath("wikipediaExternalRef");
                    if ((wikipediaNode != null) && (!wikipediaNode.isMissingNode())) {
                        wikipediaId = wikipediaNode.intValue();
                    }
                    String wikidataId = null;
                    JsonNode wikidataNode = entityNode.findPath("wikidataId");
                    if ((wikidataNode != null) && (!wikidataNode.isMissingNode())) {
                        wikidataId = wikidataNode.textValue();
                    }

                    // domains, e.g. "domains" : [ "Biology", "Engineering" ]
                    
                    // statements
                    Map<String, List<String>> statements = new TreeMap<String,List<String>>();
                    JsonNode statementsNode = entityNode.findPath("statements");
                    if ((statementsNode != null) && (!statementsNode.isMissingNode())) {
                        if (statementsNode.isArray()) {
                            for (JsonNode statement : statementsNode) {
                                JsonNode propertyIdNode = statement.findPath("propertyId");
                                JsonNode valueNode = statement.findPath("value");
                                if ( (propertyIdNode != null) && (!propertyIdNode.isMissingNode()) &&
                                     (valueNode != null) && (!valueNode.isMissingNode()) ) {
                                    List<String> localValues = statements.get(propertyIdNode.textValue());
                                    if (localValues == null)
                                        localValues = new ArrayList<String>();
                                    localValues.add(valueNode.textValue());

                                    statements.put(propertyIdNode.textValue(), localValues);
                                }
                            }
                        }
                    }

                    // statements can be used to filter obvious non-dataset entities which are
                    // mere disambiguation errors

                    // NOTE: this needs to be properly configured in future versions, we normally start
                    // from toBeFiltered = true and identify valid dataset "senses" like for software
                    // for the moment nothing is filtered out based on disambiguation

                    // check if value of P31 (instance of) are observed dataset values (to be build in future version)
                    boolean toBeFiltered = false;
                    /*if ( (statements != null) && (statements.get("P31") != null) ) {
                        List<String> p31 = statements.get("P31");
                        for(String p31Value : p31) {
                            if (DatastetLexicon.getInstance().inDatasetPropertyValues(p31Value)) {
                                toBeFiltered = false;
                                break;
                            }
                        }
                    }*/

                    // check if any of the P279 (subclass of) values are compatible with dataset entities, 
                    // as collected in existing wikidata dataset entities (to be build in future version)
                    /*if ( toBeFiltered && (statements != null) && (statements.get("P279") != null) ) {
                        List<String> p279 = statements.get("P279");
                        for(String p279Value : p279) {
                            if (DatasetLexicon.getInstance().inDatasetPropertyValues(p279Value)) {
                                toBeFiltered = false;
                                break;
                            }
                        }
                    }*/

                    // occurence of any of these properties in the statements mean a dataset (to be refined)
                    // P5874: re3data repository ID, P5195: Wikidata Dataset Imports page, P2666: Datahub page,
                    // P6526: data.gouv.fr dataset ID, P2702: dataset distribution
                    if ( toBeFiltered && (statements != null) && (statements.get("P5874)") != null || statements.get("P5195") != null
                        || statements.get("P2666") != null || statements.get("P6526") != null || statements.get("P2702") != null) ) {
                        toBeFiltered = false;
                    }
                    
                    // completely hacky for the moment and to be reviewed
                    if ( toBeFiltered && (statements != null) && (statements.get("P856") != null) ) {
                        List<String> p856 = statements.get("P856");
                        for(String p856Value : p856) {
                            // these are official web page values, we allow main data sharing sites as possible dataset web page
                            // keyterms (.edu, .org ?)
                            if (p856Value.indexOf("datacite") != -1 || p856Value.indexOf("zenodo") != -1 || p856Value.indexOf("dryad") != -1 || 
                                p856Value.indexOf("figshare") != -1 || p856Value.indexOf("pangaea") != -1 || 
                                p856Value.indexOf("osf") != -1 || p856Value.indexOf(" kaggle") != -1 ||
                                p856Value.indexOf("Mendeley") != -1 || p856Value.indexOf("github") != -1) {
                                toBeFiltered = false;
                                break;
                            }
                        }
                    }

                    // here things to consider in next version(s)
                    // categories: https://en.wikipedia.org/wiki/Category:Datasets and sub-categories
                    // statement value: P486 (MeSH descriptor ID) = D064886

                    // if we have absolutely no statement, we don't filter
                    if ( toBeFiltered && (statements == null || statements.size() == 0 || statementsNode.isMissingNode()) ) {
                        toBeFiltered = false;
                    }

//System.out.println(""+startOff + " / " + (startOff+segmentStartOffset));
                    DatasetComponent component = entityPositions.get(startOff+segmentStartOffset);
                    if (component != null) {
                        // merging
                        if (wikidataId != null)
                            component.setWikidataId(wikidataId);
                        if (wikipediaId != -1)
                            component.setWikipediaExternalRef(wikipediaId);
                        if (score != -1)
                            component.setDisambiguationScore(score);
                        if (lang != null)
                            component.setLang(lang);

                        if (toBeFiltered) {
                            component.setFiltered(true);
//System.out.println("filtered entity: " + wikidataId);
                            //continue;
                        }
                    }
                }
            }

            // propagate filtering status
            for(Dataset entity : entities) {
                DatasetComponent datasetName = entity.getDatasetName();
                if (datasetName != null && datasetName.isFiltered()) {
                    entity.setFiltered(true);
                }
                DatasetComponent dataset = entity.getDataset();
                if (dataset != null && dataset.isFiltered()) {
                    entity.setFiltered(true);
                }
                DatasetComponent dataDevice = entity.getDataDevice();
                if (dataDevice != null && dataDevice.isFiltered()) {
                    entity.setFiltered(true);
                }
            }

            // we could also retrieve the "global_categories" and use that for filtering out some non-dataset senses
            // e.g. [{"weight" : 0.16666666666666666, "source" : "wikipedia-en", "category" : "Bioinformatics", "page_id" : 726312}, ...

        } catch (Exception e) {
            LOGGER.error("Invalid JSON answer from the NERD", e);
            e.printStackTrace();
        }

        return entities;
    }

    private static String RESOURCEPATH = "disambiguate";

    /**
     * Call entity fishing disambiguation service on server.
     *
     * To be Moved in a Worker !
     *
     * @return the resulting disambiguated context in JSON or null
     */
    public String runNerd(List<Dataset> entities, List<LayoutToken> subtokens, String lang) throws RuntimeException {
        if (!serverStatus)
            return null;

        StringBuffer output = new StringBuffer();
        try {
            URL url = null;
            if ( (nerd_port != null) && (nerd_port.length() > 0) )
                if (nerd_port.equals("443"))
                    url = new URL("https://" + nerd_host + "/service/" + RESOURCEPATH);
                else
                    url = new URL("http://" + nerd_host + ":" + nerd_port + "/service/" + RESOURCEPATH);
            else
                url = new URL("http://" + nerd_host + "/service/" + RESOURCEPATH);
//System.out.println("calling... " + url.toString());
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpPost post = new HttpPost(url.toString());
            //post.addHeader("Content-Type", "application/json");
            //post.addHeader("Accept", "application/json");

            // we create the query structure
            // context as an JSON array of strings
            JsonStringEncoder encoder = JsonStringEncoder.getInstance();
            StringBuffer buffer = new StringBuffer();
            buffer.append("{\"language\":{\"lang\":\"" + lang + "\"}");
            //buffer.append(",\"nbest\": 0");
            // we ask for French and German language correspondences in the result
            //buffer.append(", \"resultLanguages\":[ \"de\", \"fr\"]");
            buffer.append(", \"text\": \"");
            int startSegmentOffset = -1;
            for(LayoutToken token : subtokens) {
                String tokenText = token.getText();
                if (startSegmentOffset == -1)
                    startSegmentOffset = token.getOffset();
                if (tokenText.equals("\n")) 
                    tokenText = " ";
                byte[] encodedText = encoder.quoteAsUTF8(tokenText);
                String outputEncodedText = new String(encodedText);
                buffer.append(outputEncodedText);
            }
            if (startSegmentOffset == -1)
                startSegmentOffset = 0;

            // no mention, it means only the mentions given in the query will be dismabiguated!
            buffer.append("\", \"mentions\": []");

            buffer.append(", \"entities\": [");
            boolean first = true;
            List<DatasetComponent> components = new ArrayList<>();
            for(Dataset entity : entities) {
                // get the dataset components interesting to disambiguate
                DatasetComponent datasetName = entity.getDatasetName();
                DatasetComponent dataset = entity.getDataset();
                DatasetComponent dataDevice = entity.getDataDevice();

                if (datasetName != null)
                    components.add(datasetName);
                if (dataset != null)
                    components.add(dataset);
                if (dataDevice != null)
                    components.add(dataDevice);
            }

            for(DatasetComponent component: components) {
                if (first) {
                    first = false;
                } else {
                    buffer.append(", ");
                }

                byte[] encodedText = encoder.quoteAsUTF8(component.getRawForm() );
                String outputEncodedText = new String(encodedText);

                buffer.append("{\"rawName\": \"" + outputEncodedText + "\", \"offsetStart\": " + (component.getOffsetStart() - startSegmentOffset)+ 
                    ", \"offsetEnd\": " + (component.getOffsetEnd() - startSegmentOffset));
                //buffer.append(", \"type\": \"");
                buffer.append(" }");
            }

            buffer.append("], \"full\": true, \"customisation\": \"dataset\", \"minSelectorScore\": 0.2 }");
            //buffer.append("] }");
            LOGGER.debug(buffer.toString());
//System.out.println(buffer.toString());

            //params.add(new BasicNameValuePair("query", buffer.toString()));

            StringBody stringBody = new StringBody(buffer.toString(), ContentType.MULTIPART_FORM_DATA);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            builder.addPart("query", stringBody);
            HttpEntity entity = builder.build();

            CloseableHttpResponse response = null;
            Scanner in = null;
            try {
                //post.setEntity(new UrlEncodedFormEntity(params));
                post.setEntity(entity);
                response = httpClient.execute(post);
                // System.out.println(response.getStatusLine());

                int code = response.getStatusLine().getStatusCode();
                if (code != 200) {
                    LOGGER.error("Failed annotating text segment: HTTP error code : " + code);
                    return null;
                }

                HttpEntity entityResp = response.getEntity();
                in = new Scanner(entityResp.getContent());
                while (in.hasNext()) {
                    output.append(in.next());
                    output.append(" ");
                }
                EntityUtils.consume(entityResp);
            } finally {
                if (in != null)
                    in.close();
                if (response != null)
                    response.close();
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return output.toString().trim();
    }

}
