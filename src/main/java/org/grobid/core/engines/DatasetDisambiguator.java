package org.grobid.core.engines;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.grobid.core.data.Dataset;
import org.grobid.core.data.DatasetComponent;
import org.grobid.core.layout.LayoutToken;
import org.grobid.service.configuration.DatastetConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Dataset entity disambiguator. Once dataset mentions are recognized and grouped
 * into an entity (dataset name with recognized attributes), we use entity-fishing
 * service to disambiguate the dataset against Wikidata, as well as the attribute
 * values (currently only creator). The main goal is to filter out false positives.
 *
 * @author Patrice
 */
@Singleton
public class DatasetDisambiguator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatasetDisambiguator.class);

    private static volatile DatasetDisambiguator instance;

    private static String nerd_host = null;
    private static String nerd_port = null;

    private static boolean serverStatus = false;

    public static DatasetDisambiguator getInstance(DatastetConfiguration configuration) {
        if (instance == null) {
            synchronized (DatasetDisambiguator.class) {
                if (instance == null) {
                    instance = new DatasetDisambiguator(configuration);
                }
            }
        }
        return instance;
    }

    @Inject
    private DatasetDisambiguator(DatastetConfiguration configuration) {
        try {
            nerd_host = configuration.getEntityFishingHost();
            nerd_port = configuration.getEntityFishingPort();
            if (StringUtils.isBlank(nerd_host)) {
                LOGGER.warn("entity-fishing host not configured, dataset disambiguation will be skipped");
                serverStatus = false;
                return;
            }
            serverStatus = checkIfAlive();
            if (serverStatus) {
                ensureCustomizationReady();
            } else {
                LOGGER.warn("entity-fishing service is not reachable at " + nerd_host
                        + (StringUtils.isNotBlank(nerd_port) ? ":" + nerd_port : "")
                        + ", dataset disambiguation will be skipped");
            }
        } catch (Exception e) {
            LOGGER.warn("Cannot initialise entity-fishing disambiguation service, it will be skipped: " + e.getMessage());
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
            if (StringUtils.isNotBlank(nerd_port)) {
                if (nerd_port.equals("443")) {
                    url = new URL("https://" + nerd_host + "/service/isalive");
                } else {
                    url = new URL("http://" + nerd_host + ":" + nerd_port + "/service/isalive");
                }
            } else
                url = new URL("http://" + nerd_host + "/service/isalive");

            LOGGER.debug("Calling: " + url);

            int timeout = 5;
            RequestConfig config = RequestConfig.custom()
                    .setConnectTimeout(timeout * 100)
                    .setConnectionRequestTimeout(timeout * 100)
                    .setSocketTimeout(timeout * 100).build();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                    .setDefaultRequestConfig(config)
                    .build();) {
                HttpGet get = new HttpGet(url.toString());

                try (CloseableHttpResponse response = httpClient.execute(get)) {
                    int code = response.getStatusLine().getStatusCode();
                    if (code != 200) {
                        LOGGER.warn("entity-fishing isalive returned HTTP " + code + ", disambiguation will be skipped");
                        return false;
                    } else {
                        result = true;
                    }
                }
            }

        } catch (MalformedURLException e) {
            LOGGER.warn("entity-fishing URL is malformed, disambiguation will be skipped");
        } catch (HttpHostConnectException e) {
            LOGGER.warn("entity-fishing is not reachable, disambiguation will be skipped");
        } catch (Exception e) {
            LOGGER.warn("entity-fishing is not available (" + e.getClass().getSimpleName() + "), disambiguation will be skipped");
        }

        return result;
    }

    /**
     * Check if the dataset customisation is ready on the entity-fishing server, if not load it
     */
    public void ensureCustomizationReady() {
        boolean result = false;
        URL url = null;
        try {
            if ((nerd_port != null) && (nerd_port.length() > 0))
                if (nerd_port.equals("443"))
                    url = new URL("https://" + nerd_host + "/service/customisation/dataset");
                else
                    url = new URL("http://" + nerd_host + ":" + nerd_port + "/service/customisation/dataset");
            else
                url = new URL("http://" + nerd_host + "/service/customisation/dataset");

            LOGGER.debug("Calling: " + url.toString());
            HttpGet get = new HttpGet(url.toString());
            try (CloseableHttpClient httpClient = HttpClients.createDefault();
                 CloseableHttpResponse response = httpClient.execute(get)) {
                int code = response.getStatusLine().getStatusCode();
                if (code != 200) {
                    LOGGER.info("Failed customization lookup service: HTTP error code : " + code + " - the customization will be loaded");
                } else {
                    result = true;
                }
            }
        } catch (MalformedURLException e) {
            LOGGER.warn("entity-fishing URL is malformed, customization skipped");
        } catch (HttpHostConnectException e) {
            LOGGER.warn("entity-fishing is not reachable, customization skipped");
        } catch (Exception e) {
            LOGGER.warn("entity-fishing customization lookup failed: " + e.getMessage());
        }

        if (!result && url != null) {
            LOGGER.info("Dataset customisation not present on server, loading it...");
            try {
                if ((nerd_port != null) && (nerd_port.length() > 0))
                    if (nerd_port.equals("443"))
                        url = new URL("https://" + nerd_host + "/service/customisations");
                    else
                        url = new URL("http://" + nerd_host + ":" + nerd_port + "/service/customisations");
                else
                    url = new URL("http://" + nerd_host + "/service/customisations");

                LOGGER.debug("Calling: " + url.toString());
                // load the dataset customisation
                File cutomisationFile = new File("resources/config/customisation-dataset.json");
                cutomisationFile = new File(cutomisationFile.getAbsolutePath());

                String json = FileUtils.readFileToString(cutomisationFile, "UTF-8");

                HttpPost post = new HttpPost(url.toString());

                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
                builder.addTextBody("value", json);
                builder.addTextBody("name", "dataset");
                HttpEntity entity = builder.build();
                post.setEntity(entity);

                try (CloseableHttpClient httpClient = HttpClients.createDefault();
                     CloseableHttpResponse response = httpClient.execute(post)) {
                    int code = response.getStatusLine().getStatusCode();
                    if (code != 200) {
                        LOGGER.error("Failed loading dataset customisation: HTTP error code : " + code);
                    } else {
                        LOGGER.info("Dataset customisation loaded");
                    }
                }
            } catch (MalformedURLException e) {
                LOGGER.warn("MalformedURLException while loading dataset customisation", e);
            } catch (IOException e) {
                LOGGER.warn("I/O error while loading dataset customisation", e);
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
        if ((entities == null) || (entities.size() == 0))
            return entities;
        if (!serverStatus)
            return entities;
        String json = null;
        try {
            json = runNerd(entities, tokens, "en");
        } catch (RuntimeException e) {
            LOGGER.warn("Call to entity-fishing failed, disambiguation skipped: " + e.getMessage());
        }
        if (json == null)
            return entities;

        List<Dataset> filteredEntities = new ArrayList<Dataset>();

//System.out.println(json);
        int segmentStartOffset = 0;
        if (tokens != null && tokens.size() > 0)
            segmentStartOffset = tokens.get(0).getOffset();

        // build a map for the existing entities in order to catch them easily
        // based on their positions
        Map<Integer, DatasetComponent> entityPositions = new TreeMap<Integer, DatasetComponent>();
        for (Dataset entity : entities) {
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
                    Map<String, List<String>> statements = new TreeMap<String, List<String>>();
                    JsonNode statementsNode = entityNode.findPath("statements");
                    if ((statementsNode != null) && (!statementsNode.isMissingNode())) {
                        if (statementsNode.isArray()) {
                            for (JsonNode statement : statementsNode) {
                                JsonNode propertyIdNode = statement.findPath("propertyId");
                                JsonNode valueNode = statement.findPath("value");
                                if ((propertyIdNode != null) && (!propertyIdNode.isMissingNode()) &&
                                        (valueNode != null) && (!valueNode.isMissingNode())) {
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
                    if (toBeFiltered && (statements != null) && (statements.get("P5874)") != null || statements.get("P5195") != null
                            || statements.get("P2666") != null || statements.get("P6526") != null || statements.get("P2702") != null)) {
                        toBeFiltered = false;
                    }

                    // completely hacky for the moment and to be reviewed
                    if (toBeFiltered && (statements != null) && (statements.get("P856") != null)) {
                        List<String> p856 = statements.get("P856");
                        for (String p856Value : p856) {
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
                    if (toBeFiltered && (statements == null || statements.size() == 0 || statementsNode.isMissingNode())) {
                        toBeFiltered = false;
                    }

//System.out.println(""+startOff + " / " + (startOff+segmentStartOffset));
                    DatasetComponent component = entityPositions.get(startOff + segmentStartOffset);
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
            for (Dataset entity : entities) {
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
            LOGGER.warn("Invalid JSON answer from entity-fishing, disambiguation skipped: " + e.getMessage());
        }

        return entities;
    }

    private static String RESOURCEPATH = "disambiguate";

    /**
     * Call entity fishing disambiguation service on server.
     * <p>
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
            if ((nerd_port != null) && (nerd_port.length() > 0))
                if (nerd_port.equals("443"))
                    url = new URL("https://" + nerd_host + "/service/" + RESOURCEPATH);
                else
                    url = new URL("http://" + nerd_host + ":" + nerd_port + "/service/" + RESOURCEPATH);
            else
                url = new URL("http://" + nerd_host + "/service/" + RESOURCEPATH);
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
            for (LayoutToken token : subtokens) {
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
            for (Dataset entity : entities) {
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

            for (DatasetComponent component : components) {
                if (first) {
                    first = false;
                } else {
                    buffer.append(", ");
                }

                byte[] encodedText = encoder.quoteAsUTF8(component.getRawForm());
                String outputEncodedText = new String(encodedText);

                buffer.append("{\"rawName\": \"" + outputEncodedText + "\", \"offsetStart\": " + (component.getOffsetStart() - startSegmentOffset) +
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

            post.setEntity(entity);
            try (CloseableHttpClient httpClient = HttpClients.createDefault();
                 CloseableHttpResponse response = httpClient.execute(post)) {
                int code = response.getStatusLine().getStatusCode();
                if (code != 200) {
                    LOGGER.warn("entity-fishing annotation returned HTTP " + code + ", disambiguation skipped");
                    return null;
                }

                HttpEntity entityResp = response.getEntity();
                try (Scanner in = new Scanner(entityResp.getContent())) {
                    while (in.hasNext()) {
                        output.append(in.next());
                        output.append(" ");
                    }
                }
                EntityUtils.consume(entityResp);
            }
        } catch (MalformedURLException e) {
            LOGGER.warn("entity-fishing URL is malformed, disambiguation skipped");
        } catch (IOException e) {
            LOGGER.warn("entity-fishing request failed, disambiguation skipped: " + e.getMessage());
        }
        return output.toString().trim();
    }

}
