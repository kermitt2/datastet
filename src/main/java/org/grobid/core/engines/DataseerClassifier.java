package org.grobid.core.engines;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.grobid.core.document.Document;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.jni.DeLFTClassifierModel;
import org.grobid.core.utilities.*;
import org.grobid.core.utilities.GrobidConfig.ModelParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Dataset identification.
 *
 * @author Patrice
 */
public class DataseerClassifier {
    private static final Logger logger = LoggerFactory.getLogger(DataseerClassifier.class);

    private static volatile DataseerClassifier instance;

    // components for sentence segmentation
    //private SentenceDetectorME detector = null;
    //private static String openNLPModelFile = "resources/openNLP/en-sent.bin";

    private static Engine engine = null; 

    private static List<String> textualElements = Arrays.asList("p", "figDesc");
    //private static List<String> textualElements = Arrays.asList("p");

    // map of classification models (binay, first-level, etc.)
    private Map<String,DeLFTClassifierModel> models = null;

    private DeLFTClassifierModel classifierBinary = null;
    private DeLFTClassifierModel classifierFirstLevel = null;
    private DeLFTClassifierModel classifierReuse = null;

    private DatastetConfiguration datastetConfiguration = null;

    public static DataseerClassifier getInstance() {
        if (instance == null) {
            getNewInstance();
        }
        return instance;
    }

    /**
     * Create a new instance.
     */
    private static synchronized void getNewInstance() {
        instance = new DataseerClassifier();
    }

    private DataseerClassifier() {
        try {
            this.datastetConfiguration = null;
            try {
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

                File configFile = new File("resources/config/config.yml").getAbsoluteFile();
                datastetConfiguration = mapper.readValue(configFile, DatastetConfiguration.class);
            } catch(Exception e) {
                logger.error("The config file does not appear valid, see resources/config/config.yml", e);
            }

            // grobid
            engine = GrobidFactory.getInstance().createEngine();

            // Datatype classifier via DeLFT
            for(ModelParameters parameter : datastetConfiguration.getModels()) {
                if (parameter.name.equals("dataseer-binary")) {
                    this.classifierBinary = new DeLFTClassifierModel("dataseer-binary", parameter.delft.architecture);
                } else if (parameter.name.equals("dataseer-first")) {
                    this.classifierFirstLevel = new DeLFTClassifierModel("dataseer-first", parameter.delft.architecture);
                } else if (parameter.name.equals("dataseer-reuse")) {
                    this.classifierReuse = new DeLFTClassifierModel("dataseer-reuse", parameter.delft.architecture);
                }
            }

        } catch (Exception e) {
            throw new GrobidException("Cannot initialise DataSeer classifier ", e);
        }
    }

    public DatastetConfiguration getDatastetConfiguration() {
        return this.datastetConfiguration;
    }

    /**
     * Classify a simple piece of text
     * @return JSON string
     */
    public String classify(String text) throws Exception {
        if (StringUtils.isEmpty(text))
            return null;
        List<String> texts = new ArrayList<>();
        texts.add(text);
        return classify(texts);
    }

    /**
     * Classify a simple piece of text whether it refers to some dataset or not
     * @return JSON string
     */
    public String classifyBinary(String text) throws Exception {
        if (StringUtils.isEmpty(text))
            return null;
        List<String> texts = new ArrayList<String>();
        texts.add(text);
        return classifyBinary(texts);
    }

    /**
     * Classify a simple piece of text for the data type of a referenced dataset
     * @return JSON string
     */
    public String classifyFirstLevel(String text) throws Exception {
        if (StringUtils.isEmpty(text))
            return null;
        List<String> texts = new ArrayList<String>();
        texts.add(text);
        return classifyFirstLevel(texts);
    }

    /**
     * Classify an array of texts
     * @return JSON string
     */
    public String classify(List<String> texts) throws Exception {

        if (CollectionUtils.isEmpty(texts))
            return null;
        logger.info("classify: " + texts.size() + " sentence(s)");
        ObjectMapper mapper = new ObjectMapper();

        String the_json = classifierBinary.classify(texts);
        // first pass to select texts to be cascaded to next level
        List<String> cascaded_texts = new ArrayList<>();
        JsonNode root = null;
        if (the_json != null && the_json.length() > 0) {
            root = mapper.readTree(the_json);
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
                        if (probDataset > probNoDataset) {
                            JsonNode textNode = classificationNode.findPath("text");
                            cascaded_texts.add(textNode.asText());
                        } 

                        // rename "dataset" attribute to avoid confusion with "Dataset" type of the taxonomy
                        ((ObjectNode)classificationNode).put("has_dataset", probDataset);
                        ((ObjectNode)classificationNode).remove("dataset");
                    }
                }
            }
        }
        //System.out.println("cascaded classify: " + cascaded_texts.size() + " sentences");
        String cascaded_json = null;
        JsonNode rootCascaded = null;
        if (cascaded_texts.size() > 0) {
            cascaded_json = classifierFirstLevel.classify(cascaded_texts);
            if (cascaded_json != null && cascaded_json.length() > 0)
                rootCascaded = mapper.readTree(cascaded_json);
        }

        if (rootCascaded == null) {
            return this.shadowModelName(the_json);
        }

        // application of the reuse model on the positive texts
        String cascaded_reuse_json = null;
        JsonNode rootReuseCascaded = null;
        if (cascaded_texts.size() > 0) {
            cascaded_reuse_json = classifierReuse.classify(cascaded_texts);
            if (cascaded_reuse_json != null && cascaded_reuse_json.length() > 0)
                rootReuseCascaded = mapper.readTree(cascaded_reuse_json);
        }

        StringBuilder builder = new StringBuilder();
        builder.append("{\n\t\"model\": \"dataseer\",\n\t\"software\": \"DeLFT\",\n\t\"date\": \"" + 
            DatastetUtilities.getISO8601Date() + "\",\n\t\"classifications\": [");

        boolean first = true;
        // second pass to inject additional results
        if (root != null && rootCascaded != null && rootReuseCascaded != null) {
            JsonNode classificationsNode = root.findPath("classifications");
            JsonNode classificationsCascadedNode = rootCascaded.findPath("classifications");
            JsonNode classificationsReuseCascadedNode = rootReuseCascaded.findPath("classifications");
            if ((classificationsNode != null) && (!classificationsNode.isMissingNode()) && 
                (classificationsCascadedNode != null) && (!classificationsCascadedNode.isMissingNode()) &&
                (classificationsReuseCascadedNode != null) && (!classificationsReuseCascadedNode.isMissingNode())) {
                Iterator<JsonNode> ite = classificationsNode.elements();
                Iterator<JsonNode> iteCascaded = classificationsCascadedNode.elements();
                Iterator<JsonNode> iteReuseCascaded = classificationsReuseCascadedNode.elements();
                while (ite.hasNext()) {
                    JsonNode classificationNode = ite.next();
                    JsonNode datasetNode = classificationNode.findPath("has_dataset");
                    JsonNode noDatasetNode = classificationNode.findPath("no_dataset");

                    if ((datasetNode != null) && (!datasetNode.isMissingNode()) &&
                        (noDatasetNode != null) && (!noDatasetNode.isMissingNode()) ) {
                        double probDataset = datasetNode.asDouble();
                        double probNoDataset = noDatasetNode.asDouble();

                        //System.out.println(probDataset + " " + probNoDataset);
                        if (probDataset > probNoDataset) {
                            JsonNode textNode = classificationNode.findPath("text");
                            if (iteCascaded.hasNext()) {
                                JsonNode classificationCascadedNode = iteCascaded.next();
                                // inject dataset/no_dataset probabilities as extra-information relevant for post--processing
                                ((ObjectNode)classificationCascadedNode).put("has_dataset", probDataset);
                                ((ObjectNode)classificationCascadedNode).put("no_dataset", probNoDataset);

                                if (iteReuseCascaded.hasNext()) {
                                    JsonNode classificationReuseCascadedNode = iteReuseCascaded.next();
                                    JsonNode reuseNode = classificationReuseCascadedNode.findPath("reuse");
                                    JsonNode noReuseNode = classificationReuseCascadedNode.findPath("not_reuse");

                                    if ((reuseNode != null) && (!reuseNode.isMissingNode()) &&
                                        (noReuseNode != null) && (!noReuseNode.isMissingNode()) ) {
                                        double probReuse = reuseNode.asDouble();
                                        double probNoReuse = noReuseNode.asDouble();

                                        if (probReuse > probNoReuse) {
                                            ((ObjectNode)classificationCascadedNode).put("reuse", true);
                                        } else {
                                            ((ObjectNode)classificationCascadedNode).put("reuse", false);
                                        }
                                    }
                                }

                                if (first)
                                    first = false;
                                else
                                    builder.append(",");
                                builder.append("\n\t\t");
                                builder.append(this.prettyPrintJsonNode(classificationCascadedNode, mapper));
                            }
                        } else {
                            if (first)
                                first = false;
                            else
                                builder.append(",");
                            builder.append("\n\t\t");
                            builder.append(this.prettyPrintJsonNode(classificationNode, mapper));
                        }
                    }
                }
            }
        }
        builder.append("\n\t]\n}");

        if (the_json != null) {
            // replace the model explitely used by a more general "dataseer"
            // final beautifier
            String finalJson = builder.toString();
            return prettyPrintJsonString(finalJson, mapper);
        }
        else
            return null;
    }

    /**
     * Classify a simple piece of text whether it refers to some dataset or not
     * @return JSON string
     */
    public String classifyBinary(List<String> texts) throws Exception {
        if (texts == null || texts.size() == 0)
            return null;
        logger.info("classify: " + texts.size() + " sentence(s)");
        ObjectMapper mapper = new ObjectMapper();

        String the_json = classifierBinary.classify(texts);
        // first pass to select texts to be cascaded to next level
        //List<String> cascaded_texts = new ArrayList<String>();
        JsonNode root = null;
        if (the_json != null && the_json.length() > 0) {
            root = mapper.readTree(the_json);
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
                        if (probDataset > probNoDataset) {
                            JsonNode textNode = classificationNode.findPath("text");
                            //cascaded_texts.add(textNode.asText());
                        } 

                        // rename "dataset" attribute to avoid confusion with "Dataset" type of the taxonomy
                        ((ObjectNode)classificationNode).put("has_dataset", probDataset);
                        ((ObjectNode)classificationNode).remove("dataset");
                    }
                }
            }
        }

        return this.shadowModelName(the_json);
    }

    /**
     * Classify a simple piece of text for the data type of a referenced dataset
     * @return JSON string
     */
    public String classifyFirstLevel(List<String> texts) throws Exception {
        if (texts == null || texts.size() == 0)
            return null;
        logger.info("classify: " + texts.size() + " sentence(s)");
        ObjectMapper mapper = new ObjectMapper();

        JsonNode rootCascaded = null;
        String cascaded_json = classifierFirstLevel.classify(texts);
        if (cascaded_json != null && cascaded_json.length() > 0)
            rootCascaded = mapper.readTree(cascaded_json);

        String finalJson = this.shadowModelName(cascaded_json);;
        return prettyPrintJsonString(finalJson, mapper);
    }

    private String shadowModelName(String the_json) {
        if (the_json == null || the_json.trim().length() == 0)
            return the_json;
        the_json = the_json.replace("\"model\": \"dataseer-binary\",", "\"model\": \"dataseer\",");
        return the_json.replace("\"model\": \"dataseer-first\",", "\"model\": \"dataseer\",");
    } 

    public String prettyPrintJsonNode(JsonNode jsonNode, ObjectMapper mapper) {
        if (jsonNode == null || jsonNode.isMissingNode())
            return null;
        try {
            Object json = mapper.readValue(jsonNode.toString(), Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String prettyPrintJsonString(String json, ObjectMapper mapper) {
        try {
            Object root = mapper.readValue(json, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Enrich a TEI document with Dataseer information
     * @return enriched TEI string
     */
    public String processTEIString(String xmlString, boolean segmentSentences) throws Exception {
        String tei = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();           
            org.w3c.dom.Document document = builder.parse(new InputSource(new StringReader(xmlString)));
            //document.getDocumentElement().normalize();
            tei = processTEIDocument(document, segmentSentences);
        } catch(ParserConfigurationException | IOException e) {
            e.printStackTrace();
        }
        return tei;
    }
    

    /**
     * Enrich a TEI document with Dataseer information
     * @return enriched TEI string
     */
    public String processTEI(String filePath, boolean segmentSentences, boolean avoidDomParserBug) throws Exception {
        String tei = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            tei = FileUtils.readFileToString(new File(filePath), UTF_8);
            if (avoidDomParserBug)
                tei = avoidDomParserAttributeBug(tei);

            org.w3c.dom.Document document = builder.parse(new InputSource(new StringReader(tei)));
            //document.getDocumentElement().normalize();
            tei = processTEIDocument(document, segmentSentences);
            if (avoidDomParserBug)
                tei = restoreDomParserAttributeBug(tei); 

        } catch(ParserConfigurationException | IOException e) {
            e.printStackTrace();
        }
        return tei;
    }

    /**
     * Enrich a TEI document with Dataseer information
     * @return enriched TEI string
     */
    public String processTEIDocument(org.w3c.dom.Document document, boolean segmentSentences) throws Exception {
        String tei = null;
        Element root = document.getDocumentElement();
        if (segmentSentences)
            segment(document, root);
        // augment sentences with dataseer classification information
        enrich(document, root);
        tei = serialize(document, null);
        return tei;
    }

    /**
     * Process a JATS document and enrich with Dataseer information as a TEI document.
     * Transformation of the JATS/NLM document is realised thanks to Pub2TEI 
     * (https://github.com/kermitt2/pub2tei) 
     * 
     * @return enriched TEI string
     */
    public String processJATS(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists())
            return null;
        String fileName = file.getName();
        String tei = null;
        String newFilePath = null;
        try {
            File tmpFile = GrobidProperties.getInstance().getTempPath();
            newFilePath = ArticleUtilities.applyPub2TEI(filePath, 
                tmpFile.getPath() + "/" + fileName.replace(".xml", ".tei.xml"), 
                this.datastetConfiguration.getPub2TEIPath());
            //System.out.println(newFilePath);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            tei = FileUtils.readFileToString(new File(newFilePath), UTF_8);
            //if (avoidDomParserBug)
            //    tei = avoidDomParserAttributeBug(tei);

            org.w3c.dom.Document document = builder.parse(new InputSource(new StringReader(tei)));
            //document.getDocumentElement().normalize();
            tei = processTEIDocument(document, true);
            //if (avoidDomParserBug)
            //    tei = restoreDomParserAttributeBug(tei); 

        } catch(ParserConfigurationException | IOException e) {
            e.printStackTrace();
        } finally {
            if (newFilePath != null) {
                File newFile = new File(newFilePath);
                IOUtilities.removeTempFile(newFile);
            }
        }
        return tei;
    }

    private void segment(org.w3c.dom.Document doc, Node node) {
        final NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node n = children.item(i);
            if ( (n.getNodeType() == Node.ELEMENT_NODE) && 
                 (textualElements.contains(n.getNodeName())) ) {
                // text content
                //String text = n.getTextContent();
                StringBuffer textBuffer = new StringBuffer();
                NodeList childNodes = n.getChildNodes();
                for(int y=0; y<childNodes.getLength(); y++) {
                    textBuffer.append(serialize(doc, childNodes.item(y)));
                    textBuffer.append(" ");
                }
                String text = textBuffer.toString();
                //String theSentences[] = detector.sentDetect(text);
                List<OffsetPosition> theSentenceBoundaries = SentenceUtilities.getInstance().runSentenceDetection(text);

                // we're making a first pass to ensure that there is no element broken by the segmentation
                List<String> sentences = new ArrayList<String>();
                List<String> toConcatenate = new ArrayList<String>();
                for(OffsetPosition sentPos : theSentenceBoundaries) {
                    //System.out.println("new chunk: " + sent);
                    String sent = text.substring(sentPos.start, sentPos.end);
                    String newSent = sent;
                    if (toConcatenate.size() != 0) {
                        StringBuffer conc = new StringBuffer();
                        for(String concat : toConcatenate) {
                            conc.append(concat);
                            conc.append(" ");
                        }
                        newSent = conc.toString() + sent;
                    }
                    String fullSent = "<s xmlns=\"http://www.tei-c.org/ns/1.0\">" + newSent + "</s>";
                    boolean fail = false;
                    try {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        factory.setNamespaceAware(true);
                        org.w3c.dom.Document d = factory.newDocumentBuilder().parse(new InputSource(new StringReader(fullSent)));                
                    } catch(Exception e) {
                        fail = true;
                    }
                    if (fail)
                        toConcatenate.add(sent);
                    else {
                        sentences.add(fullSent);
                        toConcatenate = new ArrayList<String>();
                    }
                }

                List<Node> newNodes = new ArrayList<Node>();
                for(String sent : sentences) {
                    //System.out.println("-----------------");
                    sent = sent.replace("\n", " ");
                    sent = sent.replaceAll("( )+", " ");
                
                    //Element sentenceElement = doc.createElement("s");                        
                    //sentenceElement.setTextContent(sent);
                    //newNodes.add(sentenceElement);

                    //System.out.println(sent);  

                    try {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        factory.setNamespaceAware(true);
                        org.w3c.dom.Document d = factory.newDocumentBuilder().parse(new InputSource(new StringReader(sent)));
                        //d.getDocumentElement().normalize();
                        Node newNode = doc.importNode(d.getDocumentElement(), true);
                        newNodes.add(newNode);
                        //System.out.println(serialize(doc, newNode));
                    } catch(Exception e) {

                    }
                }

                // remove old nodes 
                while (n.hasChildNodes())
                    n.removeChild(n.getFirstChild());

                // and add new ones

                // if we have a figDesc, we need to inject div/p nodes for dataseer-ml support
                if (n.getNodeName().equals("figDesc")) {
                    Element theDiv = doc.createElementNS("http://www.tei-c.org/ns/1.0", "div");
                    Element theP = doc.createElementNS("http://www.tei-c.org/ns/1.0", "p");
                    for(Node theNode : newNodes) 
                        theP.appendChild(theNode);
                    theDiv.appendChild(theP);
                    n.appendChild(theDiv);
                } else {
                    for(Node theNode : newNodes) 
                        n.appendChild(theNode);
                }

            } else if (n.getNodeType() == Node.ELEMENT_NODE) {
                segment(doc, (Element) n);
            }
        }
    }

    private void enrich(org.w3c.dom.Document doc, Node node) {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, JsonNode> mapSentenceJsonResult = new TreeMap<String, JsonNode>();

        // build the list of sections
        List<Boolean> relevantSections = null;
        List<String> segments = new ArrayList<String>();
        List<String> sectionTypes = new ArrayList<String>();
        List<Integer> nbDatasets =new ArrayList<Integer>();
        List<String> datasetTypes = new ArrayList<String>();

        // map dataset id to its data type and data subtype
        Map<String, Pair<String,String>> datasetMap = new TreeMap<>();

        // map a dataInstance id to its dataset id
        Map<String, String> dataInstanceMap = new TreeMap<>();

        // map a dataInstance id to its obtained confidence score in context
        Map<String, Double> dataInstanceScoreMap = new TreeMap<>();

        // map a dataInstance id to its reuse information in context
        Map<String, Boolean> dataInstanceReuseMap = new TreeMap<>();

        // as a preprocess, we put an identifier to every sentences (if they don't have already one)
        NodeList sentenceList = doc.getElementsByTagName("s");
        for (int i = 0; i < sentenceList.getLength(); i++) {
            Element sentenceElement = (Element) sentenceList.item(i);
            if (!sentenceElement.hasAttribute("xml:id"))
                sentenceElement.setAttribute("xml:id", "sentence-"+i);
        }

        NodeList sectionList = doc.getElementsByTagName("div");
        for (int i = 0; i < sectionList.getLength(); i++) {
            Element sectionElement = (Element) sectionList.item(i);

            // check that the father is not <abstract> and not <figDesc>
            Node fatherNode = sectionElement.getParentNode();
            if (fatherNode != null) {
                if ("abstract".equals(fatherNode.getNodeName()) || "figDesc".equals(fatherNode.getNodeName()))
                    continue;
            }

            // head element (unique, but not mandatory)
            Element headElement = this.getFirstDirectChild(sectionElement, "head");
            if (headElement != null) {
                String localTextContent = headElement.getTextContent();
                if (localTextContent == null || localTextContent.length() == 0) 
                    continue;

                segments.add(localTextContent);
                sectionTypes.add("head");
                nbDatasets.add(0);
                datasetTypes.add("no_dataset");
            }

            // the <p> elements under <div> only, and ignoring <abstract>
            for(Node child = sectionElement.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element && "p".equals(child.getNodeName())) {
                    Element childElement = (Element)child;
                    String localTextContent = childElement.getTextContent();
                    if (localTextContent == null || localTextContent.length() == 0) 
                        continue;
                    segments.add(localTextContent);
                    sectionTypes.add("p");

                    // get the sentences elements
                    List<String> localSentences = new ArrayList<String>();
                    for(Node subchild = childElement.getFirstChild(); subchild != null; subchild = subchild.getNextSibling()) {
                        if (subchild instanceof Element && "s".equals(subchild.getNodeName())) {
                            Element subchildElement = (Element)subchild;
                            localSentences.add(subchildElement.getTextContent());
                        }
                    }
                    int nbLocalDatasets = 0;
                    String mainDataset = null;
                    try {
                        String localJson = this.classify(localSentences);
                        //System.out.println(localJson);

                        List<Boolean> datasetSentences = new ArrayList<Boolean>();
                        if (localJson != null && localJson.length() > 0) {
                            JsonNode root = mapper.readTree(localJson);
                            JsonNode classificationsNode = root.findPath("classifications");
                            if ((classificationsNode != null) && (!classificationsNode.isMissingNode())) {
                                Iterator<JsonNode> ite = classificationsNode.elements();
                                while (ite.hasNext()) {
                                    JsonNode classificationNode = ite.next();
                                    JsonNode datasetNode = classificationNode.findPath("has_dataset");
                                    JsonNode noDatasetNode = classificationNode.findPath("no_dataset");
                                    JsonNode textNode = classificationNode.findPath("text");

                                    Boolean localResult = Boolean.valueOf(false);
                                    if ((datasetNode != null) && (!datasetNode.isMissingNode()) &&
                                        (noDatasetNode != null) && (!noDatasetNode.isMissingNode()) ) {
                                        double probDataset = datasetNode.asDouble();
                                        double probNoDataset = noDatasetNode.asDouble();

                                        // we consider enrichment only in the case a dataset is more likely
                                        if (probDataset > probNoDataset && probDataset > 0.9)
                                            nbLocalDatasets++;

                                        // save results
                                        mapSentenceJsonResult.put(textNode.asText(), classificationNode);
                                    }
                                }
                            }
                        }
                    } catch(Exception e) {
                        e.printStackTrace();
                    }

                    nbDatasets.add(nbLocalDatasets);
                    datasetTypes.add("no_dataset");
                }
            }
        }

        relevantSections = DataseerParser.getInstance().processingText(segments, sectionTypes, nbDatasets, datasetTypes);

        sectionList = doc.getElementsByTagName("div");
        int dataSetId = 1;
        int relevantSectionIndex = 0;
        for (int i = 0; i < sectionList.getLength(); i++) {
            Element sectionElement = (Element) sectionList.item(i);
            boolean relevantSection = false;

            // check that the father is not <abstract>
            Node fatherNode = sectionElement.getParentNode();
            if (fatherNode != null) {
                if ("abstract".equals(fatherNode.getNodeName()) || "figDesc".equals(fatherNode.getNodeName()))
                    continue;
            }

            // head element (unique, but not mandatory)
            Element headElement = this.getFirstDirectChild(sectionElement, "head");
            if (headElement != null) {
                String localTextContent = headElement.getTextContent();
                if (localTextContent == null || localTextContent.length() == 0) 
                    continue;
                relevantSection = relevantSections.get(relevantSectionIndex);
                relevantSectionIndex++;
            }

            // the <p> elements 
            for(Node child = sectionElement.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element && "p".equals(child.getNodeName())) {
                    Element childElement = (Element)child;
                    String localTextContent = childElement.getTextContent();
                    if (localTextContent == null || localTextContent.length() == 0) 
                        continue;
                    boolean localRelevantSection = relevantSections.get(relevantSectionIndex);
                    if (localRelevantSection)
                        relevantSection = true;
                    relevantSectionIndex++;
                }
            }

            // do we consider this section?
            if (!relevantSection)
                continue;

            // if we consider this section, we get back the classification of the sentences present in it and
            // update the <div> level accordingly
            for(Node child = sectionElement.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element && "p".equals(child.getNodeName())) {
                    Element childElement = (Element)child;
                    // get the sentences elements
                    for(Node subchild = childElement.getFirstChild(); subchild != null; subchild = subchild.getNextSibling()) {
                        if (subchild instanceof Element && "s".equals(subchild.getNodeName())) {
                            Element subchildElement = (Element)subchild;
                            
                            String localSentence = subchildElement.getTextContent();
                            JsonNode classificationNode = mapSentenceJsonResult.get(localSentence);

                            if (classificationNode != null && (!classificationNode.isMissingNode())) {
                                JsonNode datasetNode = classificationNode.findPath("has_dataset");
                                JsonNode noDatasetNode = classificationNode.findPath("no_dataset");

                                if ((datasetNode != null) && (!datasetNode.isMissingNode()) &&
                                    (noDatasetNode != null) && (!noDatasetNode.isMissingNode()) ) {
                                    double probDataset = datasetNode.asDouble();
                                    double probNoDataset = noDatasetNode.asDouble();

                                    // we consider enrichment only in the case a dataset is more likely
                                    if (probDataset > probNoDataset && probDataset > 0.9) {
                                        // we get the best dataset type Prediction
                                        Pair<String, Double> bestDataTypeWithProb = this.getBestDataType(classificationNode);
                                        boolean isReuse = this.getReuseInfo(classificationNode);
                                        if (bestDataTypeWithProb != null) {
                                            // annotation will look like this: <s id="dataset-1" type="Generic data">
                                            // or if existing dataset: corresp=\"#dataset- + dataSetId\"
                                            Element sentenceElement = subchildElement;

                                            /*sentenceElement.setAttribute("id","dataset-" + dataSetId);
                                            sentenceElement.setAttribute("type", bestDataTypeWithProb.getLeft());
                                            sentenceElement.setAttribute("cert", bestDataTypeWithProb.getRight().toString());
                                            if (isReuse) {
                                                sentenceElement.setAttribute("reuse", "true");
                                            } else {
                                                sentenceElement.setAttribute("reuse", "false");
                                            }*/

                                            sentenceElement.setAttribute("corresp","#dataInstance-"+dataSetId);

                                            // update dataset information  maps
                                            datasetMap.put("dataset-"+dataSetId, Pair.of(bestDataTypeWithProb.getLeft(), null));
                                            dataInstanceMap.put("dataInstance-"+dataSetId, "dataset-"+dataSetId);
                                            dataInstanceScoreMap.put("dataInstance-"+dataSetId, bestDataTypeWithProb.getRight());
                                            dataInstanceReuseMap.put("dataInstance-"+dataSetId, Boolean.valueOf(isReuse));
                                            dataSetId++;

                                            // we also need to add a dataseer subtype attribute to the parent <div>
                                            Node currentNode = sentenceElement;
                                            while(currentNode != null) {
                                                currentNode = currentNode.getParentNode();
                                                if (currentNode != null && 
                                                    currentNode instanceof Element &&
                                                    !(currentNode.getParentNode() instanceof Document) && 
                                                    ((Element)currentNode).getTagName().equals("div")) {
                                                    ((Element)currentNode).setAttribute("subtype", "dataseer");
                                                    currentNode = null;
                                                }

                                                if (currentNode != null && (currentNode.getParentNode() instanceof Document))
                                                    currentNode = null;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // get <encodingDesc> path - normally it is always there - or create it 
        NodeList encodingDescList = doc.getElementsByTagName("encodingDesc");
        Element encodingDescElement = null;
        if (encodingDescList.getLength() == 0) {
            NodeList teiHeaderList = doc.getElementsByTagName("teiHeader");
            Node teiHeaderNode = null;
            if (teiHeaderList.getLength() == 0) {
                // this should never be the case !
                // just to be sure, we inject a dummy header node to complete the process
                teiHeaderNode = doc.createElementNS("http://www.tei-c.org/ns/1.0", "teiHeader");
                doc.getDocumentElement().appendChild(teiHeaderNode);
            } else {
                teiHeaderNode = teiHeaderList.item(0);
            }

            encodingDescElement = doc.createElementNS("http://www.tei-c.org/ns/1.0", "encodingDesc");
            teiHeaderNode.appendChild(encodingDescElement);
        } else {
            encodingDescElement = (Element) encodingDescList.item(0);
        }

        // inject the dataset list under <encodingDesc>
        if (!datasetMap.isEmpty()) {
            Element listElement = doc.createElementNS("http://www.tei-c.org/ns/1.0", "list");
            listElement.setAttribute("type", "dataset");

            for (Map.Entry<String, Pair<String,String>> entry : datasetMap.entrySet()) {
                Element datasetNode = doc.createElementNS("http://www.tei-c.org/ns/1.0", "dataset");
                datasetNode.setAttribute("xml:id", entry.getKey());

                Pair<String,String> theDataTypes = entry.getValue();

                if (theDataTypes.getLeft() != null) {
                    datasetNode.setAttribute("type", theDataTypes.getLeft());
                }

                if (theDataTypes.getRight() != null) {
                    datasetNode.setAttribute("subtype", theDataTypes.getRight());
                }

                listElement.appendChild(datasetNode);
            }

            encodingDescElement.appendChild(listElement);
        }

        if (!dataInstanceMap.isEmpty()) {
            // inject the dataInstance list under <encodingDesc>
            Element listElement2 = doc.createElementNS("http://www.tei-c.org/ns/1.0", "list");
            listElement2.setAttribute("type", "dataInstance");

            for (Map.Entry<String, String> entry : dataInstanceMap.entrySet()) {
                Element dataInstanceNode = doc.createElementNS("http://www.tei-c.org/ns/1.0", "dataInstance");
                
                dataInstanceNode.setAttribute("xml:id", entry.getKey());
                dataInstanceNode.setAttribute("corresp", "#"+entry.getValue());

                Boolean reuse = dataInstanceReuseMap.get(entry.getKey());
                if (reuse != null) {
                    dataInstanceNode.setAttribute("reuse", reuse.toString());
                }

                Double cert = dataInstanceScoreMap.get(entry.getKey());
                if (cert != null) {
                    dataInstanceNode.setAttribute("cert", cert.toString());
                }

                listElement2.appendChild(dataInstanceNode);
            }

            encodingDescElement.appendChild(listElement2);
        }
    }

    private static Element getFirstDirectChild(Element parent, String name) {
        for(Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && name.equals(child.getNodeName())) 
                return (Element) child;
        }
        return null;
    }

    private static String getUpperHeaderSection(Element element) {
        String header = null;
        Node currentNode = element;
        while(currentNode != null) {
            currentNode = currentNode.getParentNode();
            if (currentNode != null && 
                currentNode instanceof Element &&
                !(currentNode.getParentNode() instanceof Document) && 
                ((Element)currentNode).getTagName().equals("div")) {
                Element headElement = getFirstDirectChild((Element)currentNode, "head");
                if (headElement != null) {
                    header = headElement.getTextContent();   
                    currentNode = null;
                }
            }

            if (currentNode != null && (currentNode.getParentNode() instanceof Document))
                currentNode = null;
        }
        return header;
    }

    public static String serialize(org.w3c.dom.Document doc, Node node) {
        DOMSource domSource = null;
        String xml = null;
        try {
            if (node == null) {
                domSource = new DOMSource(doc);
            } else {
                domSource = new DOMSource(node);
            }
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            if (node != null)
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.transform(domSource, result);
            xml = writer.toString();
        } catch(TransformerException ex) {
            ex.printStackTrace();
        }
        return xml;
    }

    public String serializeLs(org.w3c.dom.Document doc)    {
        DOMImplementationLS domImplementation = (DOMImplementationLS) doc.getImplementation();
        LSSerializer lsSerializer = domImplementation.createLSSerializer();
        return lsSerializer.writeToString(doc);   
    }

    private Pair<String, Double> getBestDataType(JsonNode classificationsNode) {
        Iterator<Map.Entry<String,JsonNode>> ite = classificationsNode.fields();
        String bestDataType = null;
        double bestProb = 0.0;
        while (ite.hasNext()) {
            Map.Entry<String, JsonNode> entry = ite.next(); 
            String className = entry.getKey();
            if (className.equals("has_dataset") || className.equals("no_dataset") || className.equals("reuse"))
                continue;

            JsonNode valNode = entry.getValue();
            double prob = valNode.asDouble();

            if (prob > bestProb) {
                bestProb = prob;
                bestDataType = className;
            }
        }
        return Pair.of(bestDataType, Double.valueOf(bestProb));
    }

    private boolean getReuseInfo(JsonNode classificationsNode) {
        boolean isReused = false;
        Iterator<Map.Entry<String,JsonNode>> ite = classificationsNode.fields();
        while (ite.hasNext()) {
            Map.Entry<String, JsonNode> entry = ite.next(); 
            String className = entry.getKey();
            if (className.equals("reuse")) {
                JsonNode valNode = entry.getValue();
                isReused = valNode.asBoolean();
                break;
            }
        }
        return isReused;
    }


    /**
     *  XML is always full of bad surprises. The following document:
     * <?xml version="1.0" encoding="UTF-8"?>
     * <a>
     * <p>
     * <c toto="http://creativecommons.org/licenses/by/4.0/">Creative Commons Attribution License</c>
     * </p>
     * </a>
     * results in [Fatal Error] :1:94: Element type "ref" must be followed by either attribute specifications, ">" or "/>".
     * or [Fatal Error] :1:70: The element type "c" must be terminated by the matching end-tag "</c>". 
     * It appears that removing the dots in the attribute value avoid the parsing error (it doesn't make sense of course, 
     * but ok...).
     * So we temporary replace the dot in the attribute values of <ref> by dummy &#x02ADB;, and restore them afterwards.
     */
    public String avoidDomParserAttributeBug(String xml) {
        //System.out.println(xml);
        String newXml = xml.replaceAll("(<ref .*)\\.(.*>)", "$1&#x02ADB;$2");
        newXml = newXml.replaceAll("(<formula .*)\\.(.*>)", "$1&#x02ADB;$2");
        while(!newXml.equals(xml)) {
            xml = newXml;
            newXml = xml.replaceAll("(<ref .*)\\.(.*>)", "$1&#x02ADB;$2");
            newXml = newXml.replaceAll("(<formula .*)\\.(.*>)", "$1&#x02ADB;$2");
        }
        xml = newXml;
        //System.out.println(xml);
        return xml;
    }

    public String restoreDomParserAttributeBug(String xml) {
        xml = xml.replace("&#x02ADB;", ".");
        return xml;
    }

    /**
     * Convert a PDF into TEI and enrich the TEI document with Dataseer information
     * @return enriched TEI string
     */
    public String processPDF(String filePath) throws Exception {
        // convert PDF into structured TEI thanks to GROBID

        List<String> coordinates = new ArrayList<>();
        coordinates.add("s");
        coordinates.add("head");
        // TBD: review arguments, no need for images, annotations, outline
        GrobidAnalysisConfig config = new GrobidAnalysisConfig.GrobidAnalysisConfigBuilder()
            .consolidateHeader(1)
            .consolidateCitations(0)
            .withSentenceSegmentation(true)
            .generateTeiCoordinates(coordinates)
            .build();
        String tei = engine.fullTextToTEI(new File(filePath), config);
        return processTEIString(tei, false);
    }

}
