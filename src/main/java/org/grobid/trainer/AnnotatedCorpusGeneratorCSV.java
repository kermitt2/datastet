package org.grobid.trainer;

import org.grobid.core.analyzers.DataseerAnalyzer;
import org.grobid.core.engines.DataseerClassifier;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.utilities.ArticleUtilities;
import org.grobid.core.utilities.ArticleUtilities.Source;

import org.grobid.core.analyzers.GrobidAnalyzer;
import org.grobid.core.data.BibDataSet;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.document.Document;
import org.grobid.core.document.DocumentPiece;
import org.grobid.core.document.DocumentSource;
import org.grobid.core.document.xml.XmlBuilderUtils;
import org.grobid.core.engines.Engine;
import org.grobid.core.engines.FullTextParser;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.engines.label.SegmentationLabels;
import org.grobid.core.engines.label.TaggingLabel;
import org.grobid.core.engines.label.TaggingLabels;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.lang.Language;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.LayoutTokenization;
import org.grobid.core.lexicon.FastMatcher;
import org.grobid.core.utilities.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.io.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.NumberFormat;

import org.apache.commons.io.*;
import org.apache.commons.csv.*;
import org.apache.commons.lang3.tuple.Pair;

import java.net.URI;
import java.net.URLEncoder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.*;
import java.util.function.*;

import nu.xom.*;
import static org.grobid.core.document.xml.XmlBuilderUtils.teiElement;
import org.apache.commons.lang3.StringUtils;

/*import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.*;
import org.w3c.dom.*;*/

import java.io.*;

import com.fasterxml.jackson.core.io.JsonStringEncoder;

/**
 * This class aims at converting annotations in .csv format from the original 
 * dataseer dataset into annotated XML files (at document level) usable for training 
 * text mining tools and readable by humans. 
 *
 * We need in particular to re-align the content of the original document which
 * has been annotated (e.g. a PMC article) with the "quotes" and strings available
 * in the .csv stuff. This is not always straightforward because: 
 * 
 * - the strings in the csv files has been cut and paste directly from the PDF 
 *   document, which is more noisy than what we can get from GROBID PDF parsing 
 *   pipeline,
 * - some annotations refers to  unlocated information present in the document and 
 *   we need some global document analysis to try to related the annotations with 
 *   the right document content.
 *
 * Example command line:
 * ./gradlew annotated_corpus_generator_csv -Pfull=/mnt/data/resources/plos/0/tei/ 
 * -Ppdf=/mnt/data/resources/plos/pdf/ -Pcsv=resources/dataset/dataseer/csv/ -Pxml=resources/dataset/dataseer/corpus/
 *
 * @author Patrice
 */
public class AnnotatedCorpusGeneratorCSV {

    private static final Logger logger = LoggerFactory.getLogger(AnnotatedCorpusGeneratorCSV.class);

    private ArticleUtilities articleUtilities = new ArticleUtilities();

    /**
     * Start the conversion/fusion process for generating MUC-style annotated XML documents
     * from PDF, parsed by GROBID core, and dataseer dataset  
     */
    public void processXML(String documentPath, String csvPath, String xmlPath) throws IOException {

        Map<String, AnnotatedDocument> documents = new HashMap<String, AnnotatedDocument>();
        Map<String, DataseerAnnotation> annotations = new HashMap<String, DataseerAnnotation>();

        importCSVFiles(csvPath, documents, annotations);

        System.out.println("\n" + annotations.size() + " total annotations");
        System.out.println(documents.size() + " total annotated documents");  
        if (!documentPath.endsWith("/"))
            documentPath += "/";

        DataseerClassifier dataseer = DataseerClassifier.getInstance();

        // some counters
        int totalAnnotations = 0;
        int m = 0;
        for (Map.Entry<String, AnnotatedDocument> entry : documents.entrySet()) {
            if (m > 100) {
                break;
            }
            m++;
            AnnotatedDocument doc = entry.getValue();
            String doi = doc.getDoi();
            if (doi.indexOf("10.1371/") == -1)
                continue;
            if (doc.getAnnotations() != null) {
                totalAnnotations += doc.getAnnotations().size();
            }
        }
        int totalUnmatchedAnnotations = 0;
        int totalMatchedAnnotations = 0;
        int allMatchedDoc = 0;

        // go thought all annotated documents 
        m = 0;
        for (Map.Entry<String, AnnotatedDocument> entry : documents.entrySet()) {
            if (m > 100) {
                break;
            }
            m++;
            AnnotatedDocument doc = entry.getValue();

            if ((doc.getAnnotations() == null) || (doc.getAnnotations().size() == 0))
                continue;

            String doi = doc.getDoi();

            //System.out.println(doi);

            // get file name from DOI

            // temporary: only process PLOS, e.g. plos/0/journal.pbio.0020190.xml
            if (doi.indexOf("10.1371/") == -1)
                continue;

            int ind1 = doi.indexOf("journal");
            String fileName = doi.substring(ind1);
            int ind2 = fileName.lastIndexOf(".");
            String plosPath = documentPath + fileName + ".xml"; 

            System.out.println(plosPath);
            // get the XML full text if available, otherwise PDF
            File f = new File(plosPath);
            if (!f.exists()) {
                System.out.println("TEI XML of the PLOS article does not exist: " + plosPath);
                System.out.println("moving to next article...");
                continue;
            }

            try {
                // segment into sentence
                String segmentedTEI = dataseer.processTEI(plosPath, true, true);
                //segmentedTEI = avoidDomParserAttributeBug(segmentedTEI);

                Builder parser = new Builder();
                InputStream inputStream = new ByteArrayInputStream(segmentedTEI.getBytes(UTF_8));
                nu.xom.Document document = parser.build(inputStream);

                // the list of annotations which have been matched. We keep track of their indices
                List<Integer> solvedAnnotations = new ArrayList<Integer>();

                // let's iterate through the segmented sentences
                nu.xom.Element root = document.getRootElement();
                List<nu.xom.Element> nodeList = getElementsByTagName(root, "s");

                for (int i = 0; i < nodeList.size(); i++) {
                    nu.xom.Element node = nodeList.get(i);
                    StringBuffer textValue = new StringBuffer();
                    for (int j = 0; j < node.getChildCount(); j++) {
                        Node subnode = node.getChild(j);
                        if (subnode instanceof Text) {
                            textValue.append(subnode.getValue());
                        }
                    }
                    String localSentence = textValue.toString();

                    // match sentence and inject attributes to sentence tags
                    boolean hasMatched = false;
                    int k = 0;
                    for(DataseerAnnotation annotation : doc.getAnnotations()) {
                        if (!solvedAnnotations.contains(k)) {
                            String sentence = annotation.getContext();
                            if (localSentence.equals(sentence)) {
                                totalMatchedAnnotations++;
                                System.out.println("matched sentence!");
                                solvedAnnotations.add(new Integer(k));
                                // add annotation attributes to the DOM sentence

                                break;
                            }
                        }
                        k++; 
                    }
                }

                if (solvedAnnotations.size() == doc.getAnnotations().size()) {
                    System.out.println("all annotations: " + doc.getAnnotations().size() + " matched");
                    allMatchedDoc++;
                }

                if (solvedAnnotations.size() < doc.getAnnotations().size()) {
                    System.out.println((doc.getAnnotations().size() - solvedAnnotations.size()) + " unmatched annotations");
                    totalUnmatchedAnnotations += doc.getAnnotations().size() - solvedAnnotations.size();
                }
            } catch (ParsingException e) {
                e.printStackTrace();
            } catch(IOException e) {
                e.printStackTrace();
            } catch(Exception e) {
                e.printStackTrace();
            }

        }

        System.out.println("Total matched annotations: " + totalMatchedAnnotations);
        System.out.println(totalUnmatchedAnnotations + " total unmatched annotations, out of " + totalAnnotations);

        System.out.println("Total documents fully matched: " + allMatchedDoc);
    }


    private static class StreamGobbler implements Runnable {
        private InputStream inputStream;
        private Consumer<String> consumer;
     
        public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }
     
        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
              .forEach(consumer);
        }
    }

    public void process(String documentPath, String pdfPath, String csvPath, String xmlPath) throws IOException {

        Map<String, AnnotatedDocument> documents = new HashMap<String, AnnotatedDocument>();
        Map<String, DataseerAnnotation> annotations = new HashMap<String, DataseerAnnotation>();

        importCSVFiles(csvPath, documents, annotations);

        System.out.println("\n" + annotations.size() + " total annotations");
        System.out.println(documents.size() + " total annotated documents");  
        if (!documentPath.endsWith("/"))
            documentPath += "/";

        DataseerClassifier dataseer = DataseerClassifier.getInstance();

        // some counters
        int totalAnnotations = 0;
        int m = 0;
        for (Map.Entry<String, AnnotatedDocument> entry : documents.entrySet()) {
            /*if (m > 100) {
                break;
            }
            m++;*/
            AnnotatedDocument doc = entry.getValue();
            String doi = doc.getDoi();
            if (doc.getAnnotations() != null) {
                totalAnnotations += doc.getAnnotations().size();
            }
        }
        int totalUnmatchedAnnotations = 0;
        int totalMatchedAnnotations = 0;
        int allMatchedDoc = 0;
        int allMatchedDocannotations = 0;

        ArticleUtilities articleUtilities = new ArticleUtilities();

        // training file for binary classification (dataset/no_dataset)
        Writer writerCSVBinary = new PrintWriter(new BufferedWriter(
            new FileWriter(csvPath + "all-binary.csv")));
        CSVPrinter csvPrinterBinary = new CSVPrinter(writerCSVBinary, 
            CSVFormat.DEFAULT.withHeader("doi", "text", "datatype"));

        // training file with all the data types, first level
        Writer writerCSV1 = new PrintWriter(new BufferedWriter(
            new FileWriter(csvPath + "all-1.csv")));
        CSVPrinter csvPrinter1 = new CSVPrinter(writerCSV1, 
            CSVFormat.DEFAULT.withHeader("doi", "text", "datatype", "dataSubtype", "leafDatatype"));

        // training file for new versus reuse of datasets
        Writer writerCSVReuse = new PrintWriter(new BufferedWriter(
            new FileWriter(csvPath + "all-reuse.csv")));
        CSVPrinter csvPrinterReuse = new CSVPrinter(writerCSVReuse, 
            CSVFormat.DEFAULT.withHeader("doi", "text", "datatype"));

        Writer failedPDFWriter = new PrintWriter(new BufferedWriter(
            new FileWriter(documentPath + "/failed-pdf.txt")));

        Writer unmatchedSentencesWriter = new PrintWriter(new BufferedWriter(
            new FileWriter(documentPath + "/unmatched-sentences.txt")));

        // go thought all annotated documents 
        m = 0;
        for (Map.Entry<String, AnnotatedDocument> entry : documents.entrySet()) {
            /*if (m > 20) {
                break;
            }*/
            m++;
            AnnotatedDocument doc = entry.getValue();

            if ((doc.getAnnotations() == null) || (doc.getAnnotations().size() == 0))
                continue;

            String doi = doc.getDoi();

            // this part output the text part in a csv format for classification
            String previousContext = null;
            for(DataseerAnnotation annotation : doc.getAnnotations()) {
                if (previousContext == null || 
                    (previousContext != null && !previousContext.equals(annotation.getContext()))) {

                    String localContext = annotation.getContext();
                    localContext = localContext.replace("[pagebreak]", " ");
                    localContext = localContext.replace("[page break]", " ");
                    localContext = localContext.replace("[columnbreak]", " ");
                    localContext = localContext.replace("[column break]", " ");
                    localContext = localContext.replace("\n", " ");
                    localContext = localContext.replaceAll("( )+", " ");
                    
                    csvPrinter1.printRecord(doi, localContext, annotation.getDataType(), 
                        annotation.getDataSubType(), annotation.getDataLeafType());
                    csvPrinter1.flush();

                    csvPrinterBinary.printRecord(doi, localContext, "dataset");
                    csvPrinterBinary.flush();

                    if (annotation.getExisting())
                        csvPrinterReuse.printRecord(doi, localContext, "reuse");
                    else 
                        csvPrinterReuse.printRecord(doi, localContext, "no_reuse");
                    csvPrinterReuse.flush();

                    previousContext = annotation.getContext();
                }
                //System.out.println(annotation.toString());
            }

            // check if the TEI file already exists for this PDF
            String teiPath = documentPath + "/" + URLEncoder.encode(doi, "UTF-8")+".tei.xml";
            File teiFile = new File(teiPath);

            if (!teiFile.exists()) {

                // check if the native XML file exists for this PDF
                boolean noXMLPlos = true;
                if (doi.indexOf("10.1371/") != -1) {

                    int ind1 = doi.indexOf("journal");
                    String fileName = doi.substring(ind1);
                    int ind2 = fileName.lastIndexOf(".");
                    String plosPath = documentPath + fileName + ".xml"; 

                    System.out.println(plosPath);
                    // get the XML full text if available, otherwise PDF
                    File f = new File(plosPath);
                    if (f.exists()) {
                        // transform the XML NLM file into TEI with Pub2TEI command line
                        String pub2teiPath = dataseer.getDataseerConfiguration().getPub2TEIPath();
                        System.out.println(pub2teiPath);
                        File pub2teiDir = new File(pub2teiPath);

                        if (!pub2teiDir.exists() || !pub2teiDir.isDirectory()) {
                            System.out.println("Warning: the Pub2TEI path is invalid, only PDF will be used");
                        } else {
                            try {
                                ProcessBuilder builder = new ProcessBuilder();
                                builder.directory(new File(pub2teiPath));

                                builder.command("java", "-jar", "Samples/saxon9he.jar", "-s:"+plosPath, "-xsl:Stylesheets/Publishers.xsl",
                                    "-o:"+teiPath, "-dtd:off", "-a:off", "-expand:off", "-t");
                                // java -jar Samples/saxon9he.jar -s:Samples/TestPubInput/BMJ/bmj_sample.xml -xsl:Stylesheets/Publishers.xsl -o:out.tei.xml -dtd:off -a:off -expand:off -t

                                Process process = builder.start();
                                StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream(), System.out::println);
                                Executors.newSingleThreadExecutor().submit(streamGobbler);
                                int exitCode = process.waitFor();
                                if (exitCode == 0)
                                    noXMLPlos = false;
                            } catch(Exception e) {
                                e.printStackTrace();
                            }
                        }                        
                    }
                }

                if (noXMLPlos) {
                    String pdfFilePath = pdfPath + "/" + URLEncoder.encode(doi, "UTF-8")+".pdf";

                    // do we have the PDF file around?
                    File pdfFile = new File(pdfFilePath);
                    if (!pdfFile.exists()) {
                        // get PDF file from DOI
                        File localPdfFile = articleUtilities.getPDFDoc(doi, Source.DOI);
                        if (localPdfFile != null) {
                            System.out.println(localPdfFile.getPath());
                            // save the PDF file
                            FileUtils.copyFile(localPdfFile, pdfFile);
                            // remove the downloaded PDF file
                            FileUtils.deleteQuietly(localPdfFile);
                        } else {
                            failedPDFWriter.write(doi);
                            failedPDFWriter.write("\n");
                            failedPDFWriter.flush();
                        }
                    }

                    if (pdfFile.exists()) {
                        // produce TEI with GROBID
                        GrobidAnalysisConfig config = new GrobidAnalysisConfig.GrobidAnalysisConfigBuilder()
                                                .consolidateHeader(1)
                                                .consolidateCitations(0)
                                                .withSentenceSegmentation(true)
                                                .build();
                        Engine engine = GrobidFactory.getInstance().getEngine();
                        String tei = null;
                        try {
                            tei = engine.fullTextToTEI(pdfFile, config);
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                        
                        // save TEI file
                        FileUtils.writeStringToFile(new File(teiPath), tei, UTF_8);
                    }
                }
            }

            try {
                int localUnmatchedAnnotations = 0;
                List<String> docMatchedSentences = new ArrayList<>();

                // via PDF no need to segment into sentence
                //String segmentedTEI = dataseer.processTEI(teiPath, false, false);
                //FileUtils.writeStringToFile(new File(teiPath.replace(".tei.xml", "-segmented.tei.xml")), segmentedTEI, UTF_8);

                Builder parser = new Builder();
                //InputStream inputStream = new ByteArrayInputStream(segmentedTEI.getBytes(UTF_8));
                InputStream inputStream = new FileInputStream(teiPath);

                nu.xom.Document document = parser.build(inputStream);

                // the list of annotations which have been matched. We keep track of their indices
                List<Integer> solvedAnnotations = new ArrayList<Integer>();

                // let's iterate through the segmented sentences
                nu.xom.Element root = document.getRootElement();
                List<nu.xom.Element> nodeList = getElementsByTagName(root, "s");

                List<String> dataSetIds = new ArrayList<String>();
                for (int i = 0; i < nodeList.size(); i++) {
                    nu.xom.Element node = nodeList.get(i);

                    StringBuffer textValue = new StringBuffer();
                    for (int j = 0; j < node.getChildCount(); j++) {
                        Node subnode = node.getChild(j);
                        if (subnode instanceof Text) {
                            textValue.append(subnode.getValue());
                        } else if (subnode.getChildCount() > 0 ) {
                            for (int q = 0; q < subnode.getChildCount(); q++) {
                                Node subsubnode = subnode.getChild(q);
                                if (subsubnode instanceof Text) {
                                    textValue.append(subsubnode.getValue());
                                }
                            }
                        }
                    }
                    String localSentence = textValue.toString();
                    //System.out.println(localSentence);
                    String localSentenceSimplified = localSentence.trim().replace("\n", " ").toLowerCase();
                    localSentenceSimplified = localSentenceSimplified.replace(" ", "");
                    localSentenceSimplified = simplifiedField(localSentenceSimplified);

                    // match sentence and inject attributes to sentence tags
                    boolean hasMatched = false;
                    int k = 0;
                    
                    for(DataseerAnnotation annotation : doc.getAnnotations()) {
                        if (annotation.getRawDataType() == null) {
                            k++;
                            continue;
                        }

                        String sentence = annotation.getContext().trim();
                        if (sentence.toLowerCase().equals("n/a")) {
                            k++;
                            continue;
                        }

                        if (!solvedAnnotations.contains(k)) {
                            sentence = sentence.replace("[pagebreak]", "");
                            sentence = sentence.replace("[page break]", "");
                            sentence = sentence.replace("[columnbreak]", "");
                            sentence = sentence.replace("[column break]", "");

                            String sentenceSimplified = sentence.trim().replace("\n", "").toLowerCase();
                            sentenceSimplified = sentenceSimplified.replace(" ", "");
                            sentenceSimplified = simplifiedField(sentenceSimplified);

                            if (sentenceSimplified.length() < localSentenceSimplified.length()/2) {
                                k++;
                                continue;
                            }

                            //System.out.println(sentence);
                            if (localSentenceSimplified.equals(sentenceSimplified) || 
                                localSentenceSimplified.indexOf(sentenceSimplified) != -1 ||
                                sentenceSimplified.indexOf(localSentenceSimplified) != -1 || 
                                localSentenceSimplified.indexOf(sentenceSimplified) != -1 ||
                                sentenceSimplified.indexOf(localSentenceSimplified) != -1 || 
                                docMatchedSentences.contains(sentenceSimplified)) {

                                totalMatchedAnnotations++;
                                //System.out.println("matched sentence! " + sentence);
                                solvedAnnotations.add(new Integer(k));

                                if (!docMatchedSentences.contains(sentenceSimplified)) {
                                    docMatchedSentences.add(sentenceSimplified);
                                
                                    // add annotation attributes to the DOM sentence
                                    // e.g. id="dataset-2" type="Spectrometry"
                                    // add @corresp in case the @id is already generated
                                    String dataSetId = annotation.getDatasetId();
                                    Attribute id = null;
                                    if (dataSetIds.contains(dataSetId)) {
                                        id = new Attribute("corresp", "#dataset-" + dataSetId);
                                    } else {
                                        id = new Attribute("id", "dataset-" + dataSetId);
                                        dataSetIds.add(dataSetId);
                                    }
                                    node.addAttribute(id);

                                    Attribute type = new Attribute("type", annotation.getRawDataType());
                                    node.addAttribute(type);

                                    // we also need to add a dataseer subtype attribute to the parent <div>
                                    nu.xom.ParentNode currentNode = node;
                                    while(currentNode != null) {
                                        currentNode = ((nu.xom.Element)currentNode).getParent();
                                        if (currentNode != null && 
                                            !(currentNode.getParent() instanceof nu.xom.Document) && 
                                            ((nu.xom.Element)currentNode).getLocalName().equals("div")) {
                                            Attribute subtype = new Attribute("subtype", "dataseer");
                                            ((nu.xom.Element)currentNode).addAttribute(subtype);
                                            currentNode = null;
                                        }

                                        if (currentNode != null && (currentNode.getParent() instanceof nu.xom.Document))
                                            currentNode = null;
                                    }
                                }

                                //break;
                            }
                        }
                        k++; 
                    }
                }
                
                int l = 0;
                for(DataseerAnnotation annotation : doc.getAnnotations()) {
                    if (annotation.getRawDataType() == null) {
                        l++;
                        continue;
                    }

                    String sentence = annotation.getContext().trim();
                    if (sentence.equals("n/a")) {
                        l++;
                        continue;
                    }

                    if (!solvedAnnotations.contains(l)) {
                        unmatchedSentencesWriter.write(doc.getDoi() + "\t" + annotation.getContext().replace("\n", " ").replaceAll("( )+", " ") + "\n");
                        localUnmatchedAnnotations++;
                        totalUnmatchedAnnotations++;
                    }

                    l++;
                }

                String teiOutPutPath = xmlPath + "/" + URLEncoder.encode(doi, "UTF-8")+".tei.xml";;
                //if (solvedAnnotations.size() == doc.getAnnotations().size()) {

                System.out.println("\n" + doc.getDoi());
                if (localUnmatchedAnnotations == 0) {
                    System.out.println("all annotations " + doc.getAnnotations().size() + " matched for the document");
                    allMatchedDoc++;
                    allMatchedDocannotations += solvedAnnotations.size();
                    // we write this document to the result folder
                    Writer writerTEI = new PrintWriter(new BufferedWriter(new FileWriter(teiOutPutPath)));
                    writerTEI.write(XMLUtilities.toPrettyString(document.toXML(), 4));
                    writerTEI.close();
                } else {
                    System.out.println((doc.getAnnotations().size() - localUnmatchedAnnotations) + " matched annotations for the document");
                    System.out.println(localUnmatchedAnnotations + " unmatched annotations for the document");
                }

                /*if (solvedAnnotations.size() < doc.getAnnotations().size()) {
                    System.out.println((doc.getAnnotations().size() - solvedAnnotations.size()) + " unmatched annotations");
                    //totalUnmatchedAnnotations += doc.getAnnotations().size() - solvedAnnotations.size();
                }*/

                // we want to inject additional "negative" sentences in the training data for the ML models,
                // in priority sentences from the same section as the ones introducing datasets
                // consider only documents with all annotation matched (to avoid any false negatives)
                
                //if (solvedAnnotations.size() == doc.getAnnotations().size()) {
                if (localUnmatchedAnnotations == 0) {
                    /*String teicontent = FileUtils.readFileToString(new File(teiOutPutPath), "UTF-8");
                    parser = new Builder();
                    inputStream = new ByteArrayInputStream(teicontent.getBytes(UTF_8));
                    document = parser.build(inputStream);*/

                    int nbLabeledDiv = 0;
                    int nbLabeledSentences = 0;
                    int nbNonLabeledSentences = 0;

                    List<Element> divs = getElementsByTagName(root, "div");
                    // the following is returning 0 node, even document.query("//div") returns 0 node, even adding namespaces, wth with xom?
                    //Nodes nodesList = document.query("//div[@subtype='dataseer']");

                    //System.out.println("nb div: " + divs.size());
                    
                    // check if we have a sentence in this section introducing a dataset
                    for (int i = 0; i < divs.size(); i++) {
                        //nu.xom.Node node = nodesList.get(i);
                        Element div = divs.get(i);
                        // check if we have @subtype='dataseer' (xom XPath fails, with or without namespaces, :/)
                        String attribute = div.getAttributeValue("subtype");
                        if (attribute != null && attribute.equals("dataseer")) {
                            nbLabeledDiv++;
                            //Nodes nodeTest = div.query("//s[@type and string-length(@type)>0]");
                            //Elements paragraphs = div.getChildElements("p");
                            Elements paragraphs = div.getChildElements();
                            //System.out.println("nb paragraphs: " + paragraphs.size());
                            for (int j = 0; j < paragraphs.size(); j++) {
                                Element paragraph = paragraphs.get(j); 
                                //System.out.println(paragraph.getQualifiedName());
                                if (!paragraph.getQualifiedName().equals("p"))
                                    continue;
                                Elements sentences = paragraph.getChildElements();
                                //System.out.println("nb sentences: " + sentences.size());
                                for (int k = 0; k < sentences.size(); k++) {
                                    Element sentence = sentences.get(k); 
                                    if (!sentence.getQualifiedName().equals("s"))
                                        continue;
                                    String attribute2 = sentence.getAttributeValue("type");
                                    if (attribute2 == null || attribute2.length() == 0) {
                                        nbNonLabeledSentences++;
                                        if (sentence.getValue() != null && sentence.getValue().length() > 0) {
                                            csvPrinter1.printRecord(doi, sentence.getValue(), "no_dataset", null, null);
                                            csvPrinter1.flush();

                                            csvPrinterBinary.printRecord(doi, sentence.getValue(), "no_dataset");
                                            csvPrinterBinary.flush();
                                        }
                                    } else
                                        nbLabeledSentences++;
                                }
                            }
                        }
                    }

                    /*
                    System.out.println("nb labeled div: " + nbLabeledDiv);
                    System.out.println("nb labeled sentences: " + nbLabeledSentences);
                    System.out.println("nb non-labeled sentences: " + nbNonLabeledSentences);
                    */
                }

            } catch (ParsingException e) {
                e.printStackTrace();
            } catch(IOException e) {
                e.printStackTrace();
            } catch(Exception e) {
                e.printStackTrace();
            }

        }

        csvPrinterBinary.close();
        // not sure the following is needed, but to be sure...
        writerCSVBinary.close();

        csvPrinter1.close();
        // not sure the following is needed, but to be sure...
        writerCSV1.close();

        csvPrinterReuse.close();
        // not sure the following is needed, but to be sure...
        writerCSVReuse.close();

        failedPDFWriter.close();
        unmatchedSentencesWriter.close();

        System.out.println("\n--------------------------------");
        System.out.println("\nTotal matched annotations: " + totalMatchedAnnotations + ", out of " + totalAnnotations);
        System.out.println("total unmatched annotations: " + totalUnmatchedAnnotations + ", out of " + totalAnnotations);
        System.out.println("Total documents fully matched: " + allMatchedDoc + " (covering " + allMatchedDocannotations + 
            " annotations) out of " +documents.size() + " documents");

        System.exit(0);
    }

    public static List<nu.xom.Element> getElementsByTagName(nu.xom.Element element, String tagName) {
        nu.xom.Elements children = element.getChildElements();
        List<nu.xom.Element> result = new ArrayList<>();
        for(int i=0; i<children.size(); i++) {
            nu.xom.Element child = children.get(i);
            if (tagName.equals(child.getLocalName())) {
                result.add(child);
            }
            result.addAll(getElementsByTagName(child, tagName));
        }
        return result;
    }

    private void importCSVFiles(String csvPath, Map<String, AnnotatedDocument> documents, Map<String, DataseerAnnotation> annotations) {
        // process is driven by what's available in the dataseer dataset
        File dataseerRoot = new File(csvPath);
        // if the given root is the dataseer repo root, we go down to data/ and then csv_dataset 
        // (otherwise we assume we are already under data/csv_dataset)
        // todo


        File[] refFiles = dataseerRoot.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".csv");
            }
        });

        if (refFiles == null) {
            logger.warn("We found no .csv file to process");
            return;
        }

        // this csv file gives information on the article set to which each article belongs to 
        //File dataseerArticles = new File(csvPath + File.separator + "All2000_for_Patrice_16th_June-xlsx.csv");
        //File dataseerArticles = new File(csvPath + File.separator + "All2000_for_Patrice_December_2019.csv");
        File dataseerArticles = new File(csvPath + File.separator + "All2000_Existing-vs-New_updated_data_types_Feb5_2020.csv");
        try {
            CSVParser parser = CSVParser.parse(dataseerArticles, UTF_8, CSVFormat.RFC4180);
            // schema is:
            // Journal,Article #,Manuscript ID or DOI,dataset number,Existing/New,Full MeSH data type,Section,Subsection title,Page number,
            // Column number,Data paragraph,Data Keyword,Data action word,Specialist equipment,Notes

            // e.g.
            // PLOS ONE,1.0,https://doi.org/10.1371/journal.pone.0198270,1.0,Dataset Re-use,Tabular Data,Materials and methods,
            // Architecture,6.0,1.0,"For this phase, each dataset was downloaded from curated sources and was annotated with
            // ontology terms URIs by reusing the ontology fields when provided by the original source.",Dataset,was downloaded,,

            boolean start = true;
            for (CSVRecord csvRecord : parser) {
                if (start) {
                    start = false;
                    continue;
                }
                DataseerAnnotation annotation = null;
                String attribute = null;
                String documentId = null;
                for(int i=0; i<csvRecord.size(); i++) {
                    String value = csvRecord.get(i);
                    if ( (value.trim().length() == 0) || (value.trim().equals("NA")) )
                        continue;
                    value = cleanValue(value);
                    if (i == 0) {
                        annotation = new DataseerAnnotation();
                        annotation.setCollectionID(value);
                    } else if (i == 1) {
                        documentId = value.replace(".0", "");
                        annotation.setDocumentId(documentId);
                        annotations.put(value, annotation);
                        AnnotatedDocument doc = documents.get(documentId);
                        if (doc == null) {
                            doc = new AnnotatedDocument();
                            doc.setDocumentId(documentId);
                            documents.put(documentId, doc);
                        }
                        doc.addAnnotation(annotation);
                    } else if (i == 2) {
                        AnnotatedDocument doc = documents.get(documentId);
                        if (doc.getDoi() == null) {
                            if (value.startsWith("https://doi.org/"))
                                value = value.replace("https://doi.org/", "");
                            doc.setDoi(value);
                        }
                    } else if (i == 3) {
                        annotation.setDatasetId(value.replace(".0", ""));
                    } else if (i == 4) {
                        if (value.equals("New"))
                            annotation.setExisting(false);
                        else
                            annotation.setExisting(true);
                    } else if (i == 5) {
                        annotation.setRawDataType(value);
                    } else if (i == 6) {
                        annotation.setSection(value);
                    } else if (i == 7) {
                        annotation.setSubsection(value);
                    } else if (i == 8) {
                        annotation.setPage(value.replace(".0", ""));
                    } else if (i == 10) {
                        annotation.setContext(value);
                    } else if (i == 11) {
                        annotation.setDataKeyword(value);
                    } else if (i == 12) {
                        annotation.setDataAction(value);
                    } else if (i == 13) {
                        annotation.setAcquisitionEquipment(value);
                    }
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private static String cleanValue(String value) {
        value = value.trim();
        value = value.replace("\n", " ");
        if (value.startsWith("\""))
            value = value.substring(1,value.length());
        if (value.endsWith("\""))
            value = value.substring(0,value.length()-1);
        value = value.replaceAll(" +", " ");
        return value.trim();
    }

    public static String simplifiedField(String field) { 
        if (field == null)
            return "";
        return field.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    /**
     * Command line execution.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
       
        // we are expecting four arguments, absolute path to the available fulltext NLM
        // documents, absolute path to the original PDF documents, absolute path to the csv files 
        // with the dataseer manual annotations and finally path where to put the generated 
        // training XML files 

        if (args.length != 4) {
            System.err.println("Usage: command [absolute path to the original NLM fulltexts] [absolute path to PDF fulltexts] [absolute path to the dataseer root data in csv] [output for the generated XML files]");
            System.exit(-1);
        }

        String documentNLMPath = args[0];
        File f = new File(documentNLMPath);
        if (!f.exists() || !f.isDirectory()) {
            System.err.println("path to the NLM full text directory does not exist or is invalid: " + documentNLMPath);
            System.exit(-1);
        }

        String pdfPath = args[1];
        f = new File(pdfPath);
        if (!f.exists() || !f.isDirectory()) {
            System.out.println("PDF directory path does not exist, so it will be created (downloaded PDF will be stored and reused there).");
            new File(pdfPath).mkdirs();
        }     

        String csvPath = args[2];
        f = new File(csvPath);
        if (!f.exists() || !f.isDirectory()) {
            System.err.println("path to dataseer annotated data csv directory does not exist or is invalid: " + csvPath);
            System.exit(-1);
        }   

        String xmlPath = args[3];
        f = new File(xmlPath);
        if (!f.exists() || !f.isDirectory()) {
            System.out.println("XML output directory path does not exist, so it will be created");
            new File(xmlPath).mkdirs();
        }       

        AnnotatedCorpusGeneratorCSV converter = new AnnotatedCorpusGeneratorCSV();
        try {
            converter.process(documentNLMPath, pdfPath, csvPath, xmlPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}