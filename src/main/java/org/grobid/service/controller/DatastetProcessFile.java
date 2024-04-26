package org.grobid.service.controller;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.grobid.core.data.BibDataSet;
import org.grobid.core.data.Dataset;
import org.grobid.core.document.Document;
import org.grobid.core.engines.DataseerClassifier;
import org.grobid.core.engines.DatasetParser;
import org.grobid.core.layout.Page;
import org.grobid.core.utilities.ArticleUtilities;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.IOUtilities;
import org.grobid.service.exceptions.DatastetServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.NoSuchElementException;

/**
 *
 * @author Patrice
 */
@Singleton
public class DatastetProcessFile {

    /**
     * The class Logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(DatastetProcessFile.class);

    @Inject
    public DatastetProcessFile() {
    }

    /**
     * Uploads a TEI document, identify dataset introductory section, segment and classify sentences.
     *
     * @param inputStream the data of origin TEI document
     * @return a response object which contains an enriched TEI representation of the document
     */
    public static Response processTEI(final InputStream inputStream, boolean segmentSentences) {
        LOGGER.debug(methodLogIn());
        String retVal = null;
        Response response = null;
        File originFile = null;
        DataseerClassifier classifier = DataseerClassifier.getInstance();
        try {
            originFile = ArticleUtilities.writeInputFile(inputStream, ".tei.xml");
            if (originFile == null) {
                LOGGER.error("The input file cannot be written.");
                throw new DatastetServiceException(
                    "The input file cannot be written. ", Status.INTERNAL_SERVER_ERROR);
            } 

            // starts conversion process
            retVal = classifier.processTEI(originFile.getAbsolutePath(), segmentSentences, false);

            if (!isResultOK(retVal)) {
                response = Response.status(Response.Status.NO_CONTENT).build();
            } else {
                response = Response.status(Response.Status.OK)
                    .entity(retVal)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML + "; charset=UTF-8")
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT")
                    .build();
            }
        } catch (Exception exp) {
            LOGGER.error("An unexpected exception occurs. ", exp);
            response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(exp.getMessage()).build();
        } finally {
            if (originFile != null)
                IOUtilities.removeTempFile(originFile);
        }

        LOGGER.debug(methodLogOut());
        return response;
    }

    /**
     * Uploads a JATS document, identify dataset introductory section, segment and classify sentences.
     *
     * @param inputStream the data of origin JATS document
     * @return a response object which contains an enriched TEI representation of the document
     */
    public static Response processJATS(final InputStream inputStream) {
        LOGGER.debug(methodLogIn());
        String retVal = null;
        Response response = null;
        File originFile = null;
        DataseerClassifier classifier = DataseerClassifier.getInstance();
        try {
            originFile = ArticleUtilities.writeInputFile(inputStream, ".xml");
            if (originFile == null) {
                LOGGER.error("The input file cannot be written.");
                throw new DatastetServiceException(
                    "The input file cannot be written. ", Status.INTERNAL_SERVER_ERROR);
            } 

            // starts conversion process
            retVal = classifier.processJATS(originFile.getAbsolutePath());

            if (!isResultOK(retVal)) {
                response = Response.status(Response.Status.NO_CONTENT).build();
            } else {
                response = Response.status(Response.Status.OK)
                    .entity(retVal)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML + "; charset=UTF-8")
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT")
                    .build();
            }
        } catch (Exception exp) {
            LOGGER.error("An unexpected exception occurs. ", exp);
            response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(exp.getMessage()).build();
        } finally {
            if (originFile != null)
                IOUtilities.removeTempFile(originFile);
        }

        LOGGER.debug(methodLogOut());
        return response;
    }

    /**
     * Uploads a PDF document, extract and structured content with GROBID, convert it into TEI, 
     * identify dataset introductory section, segment and classify sentences.
     *
     * @param inputStream the data of origin PDF document
     * @return a response object which contains an enriched TEI representation of the document
     */
    public static Response processPDF(final InputStream inputStream) {
        LOGGER.debug(methodLogIn());
        String retVal = null;
        Response response = null;
        File originFile = null;
        DataseerClassifier classifier = DataseerClassifier.getInstance();
        try {
            originFile = IOUtilities.writeInputFile(inputStream);
            if (originFile == null) {
                LOGGER.error("The input file cannot be written.");
                throw new DatastetServiceException(
                    "The input file cannot be written. ", Status.INTERNAL_SERVER_ERROR);
            } 

            // starts conversion process
            retVal = classifier.processPDF(originFile.getAbsolutePath());

            if (!isResultOK(retVal)) {
                response = Response.status(Response.Status.NO_CONTENT).build();
            } else {
                response = Response.status(Response.Status.OK)
                    .entity(retVal)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML + "; charset=UTF-8")
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT")
                    .build();
            }
        } catch (Exception exp) {
            LOGGER.error("An unexpected exception occurs. ", exp);
            response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(exp.getMessage()).build();
        } finally {
            if (originFile != null)
                IOUtilities.removeTempFile(originFile);
        }

        LOGGER.debug(methodLogOut());
        return response;
    }

    /**
     * Uploads a PDF document, extract and structured content with GROBID, identify datasets and
     * associated information, return JSON response as layer annotations.
     *
     * @param inputStream the data of origin PDF document
     * @return a response object which contains JSON annotation enrichments
     */
    public static Response processDatasetPDF(final InputStream inputStream,
                                        boolean addParagraphContext) {
        LOGGER.debug(methodLogIn());
        String retVal = null;
        Response response = null;
        File originFile = null;
        DataseerClassifier classifier = DataseerClassifier.getInstance();
        DatasetParser parser = DatasetParser.getInstance(classifier.getDatastetConfiguration());
        JsonStringEncoder encoder = JsonStringEncoder.getInstance();

        boolean disambiguate = false;
        try {
            ObjectMapper mapper = new ObjectMapper();

            MessageDigest md = MessageDigest.getInstance("MD5");
            DigestInputStream dis = new DigestInputStream(inputStream, md); 

            originFile = IOUtilities.writeInputFile(dis);
            byte[] digest = md.digest();

            if (originFile == null) {
                LOGGER.error("The input file cannot be written.");
                throw new DatastetServiceException(
                    "The input file cannot be written. ", Status.INTERNAL_SERVER_ERROR);
            } 

            long start = System.currentTimeMillis();
            // starts conversion process
            Pair<List<List<Dataset>>, Document> extractedResults = 
                    parser.processPDF(originFile, disambiguate);
            
            StringBuilder json = new StringBuilder();
            json.append("{ ");
            json.append(DatastetServiceUtils.applicationDetails(classifier.getDatastetConfiguration().getVersion()));
            
            String md5Str = DatatypeConverter.printHexBinary(digest).toUpperCase();
            json.append(", \"md5\": \"" + md5Str + "\"");

            // page height and width
            json.append(", \"pages\":[");
            Document doc = extractedResults.getRight();
            List<Page> pages = doc.getPages();
            boolean first = true;
            for(Page page : pages) {
                if (first) 
                    first = false;
                else
                    json.append(", ");    
                json.append("{\"page_height\":" + page.getHeight());
                json.append(", \"page_width\":" + page.getWidth() + "}");
            }

            json.append("], \"mentions\":[");
            boolean startList = true;
            for(List<Dataset> results : extractedResults.getLeft()) {
                for(Dataset dataset : results) {
                    if (startList)
                        startList = false;
                    else 
                        json.append(", ");
                    json.append(dataset.toJson());
                }
            }

            json.append("], \"references\":[");

            List<BibDataSet> bibDataSet = doc.getBibDataSets();
            if (bibDataSet != null && bibDataSet.size()>0) {
                DatastetServiceUtils.serializeReferences(json, bibDataSet, extractedResults.getLeft());
            }
            json.append("]");

            long end = System.currentTimeMillis();
            float runtime = ((float)(end-start)/1000);
            json.append(", \"runtime\": "+ runtime);

            json.append("}");

            //System.out.println(json.toString());

            Object finalJsonObject = mapper.readValue(json.toString(), Object.class);
            String retValString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(finalJsonObject);

            if (!isResultOK(retValString)) {
                response = Response.status(Status.NO_CONTENT).build();
            } else {
                response = Response.status(Status.OK).entity(retValString).type(MediaType.TEXT_PLAIN).build();
            }
        } catch (Exception exp) {
            LOGGER.error("An unexpected exception occurs. ", exp);
            response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(exp.getMessage()).build();
        } finally {
            if (originFile != null)
                IOUtilities.removeTempFile(originFile);
        }

        LOGGER.debug(methodLogOut());
        return response;
    }

    /**
     * Uploads the origin XML, process it and return the extracted dataset mention objects in JSON.
     *
     * @param inputStream the data of origin XML
     * @param addParagraphContext if true, the full paragraph where an annotation takes place is added
     * @return a response object containing the JSON annotations
     */
    public static Response extractXML(final InputStream inputStream, 
                                        boolean addParagraphContext) {
        LOGGER.debug(methodLogIn()); 
        Response response = null;
        File originFile = null;
        DataseerClassifier classifier = DataseerClassifier.getInstance();
        DatasetParser parser = DatasetParser.getInstance(classifier.getDatastetConfiguration());
        JsonStringEncoder encoder = JsonStringEncoder.getInstance();

        try {
            ObjectMapper mapper = new ObjectMapper();

            MessageDigest md = MessageDigest.getInstance("MD5");
            DigestInputStream dis = new DigestInputStream(inputStream, md); 

            originFile = IOUtilities.writeInputFile(dis);
            byte[] digest = md.digest();

            if (originFile == null) {
                response = Response.status(Status.INTERNAL_SERVER_ERROR).build();
            } else {
                long start = System.currentTimeMillis();

                Pair<List<List<Dataset>>, List<BibDataSet>> extractionResult = parser.processXML(originFile, false, false, addParagraphContext);
                long end = System.currentTimeMillis();

                List<List<Dataset>> extractedEntities = null;
                if (extractionResult != null) {
                    extractedEntities = extractionResult.getLeft();
                }

                StringBuilder json = new StringBuilder();
                json.append("{ ");
                json.append(DatastetServiceUtils.applicationDetails(GrobidProperties.getVersion()));
                
                String md5Str = DatatypeConverter.printHexBinary(digest).toUpperCase();
                json.append(", \"md5\": \"" + md5Str + "\"");
                json.append(", \"mentions\":[");

                if (extractedEntities != null && extractedEntities.size()>0) {
                    boolean startList = true;
                    for(List<Dataset> results : extractedEntities) {
                        for(Dataset dataset : results) {
                            if (startList)
                                startList = false;
                            else 
                                json.append(", ");
                            json.append(dataset.toJson());
                        }
                    }
                }

                json.append("], \"references\":[");

//                if (extractionResult != null) {
//                    List<BibDataSet> bibDataSet = extractionResult.getRight();
//                    if (bibDataSet != null && bibDataSet.size()>0) {
//                        DatastetServiceUtils.serializeReferences(json, bibDataSet, extractedEntities);
//                    }
//                }

                json.append("]");

                float runtime = ((float)(end-start)/1000);
                json.append(", \"runtime\": "+ runtime);

                json.append("}");

                Object finalJsonObject = mapper.readValue(json.toString(), Object.class);
                String retValString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(finalJsonObject);

                if (!isResultOK(retValString)) {
                    response = Response.status(Status.NO_CONTENT).build();
                } else {
                    response = Response.status(Status.OK).entity(retValString).type(MediaType.TEXT_PLAIN).build();
                    /*response = Response
                            .ok()
                            .type("application/json")
                            .entity(retValString)
                            .build();*/
                }
            }

        } catch (NoSuchElementException nseExp) {
            LOGGER.error("Could not get an instance of DatastetParser. Sending service unavailable.");
            response = Response.status(Status.SERVICE_UNAVAILABLE).build();
        } catch (Exception exp) {
            LOGGER.error("An unexpected exception occurs. ", exp);
            response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(exp.getMessage()).build();
        } finally {
            IOUtilities.removeTempFile(originFile);
        }
        LOGGER.debug(methodLogOut());
        return response;
    }

    /**
     * Uploads the origin TEI XML, process it and return the extracted dataset mention objects in JSON.
     *
     * @param inputStream the data of origin TEI
     * @param segmentSentences add sentence segmentation if the TEI was not already segmented
     * @param addParagraphContext if true, the full paragraph where an annotation takes place is added
     * @return a response object containing the JSON annotations
     */
    public static Response extractTEI(final InputStream inputStream,
                                        boolean segmentSentences,
                                        boolean disambiguate, 
                                        boolean addParagraphContext) {
        LOGGER.debug(methodLogIn()); 
        Response response = null;
        File originFile = null;
        DataseerClassifier classifier = DataseerClassifier.getInstance();
        DatasetParser parser = DatasetParser.getInstance(classifier.getDatastetConfiguration());
        JsonStringEncoder encoder = JsonStringEncoder.getInstance();

        try {
            ObjectMapper mapper = new ObjectMapper();

            MessageDigest md = MessageDigest.getInstance("MD5");
            DigestInputStream dis = new DigestInputStream(inputStream, md); 

            originFile = IOUtilities.writeInputFile(dis);
            byte[] digest = md.digest();

            if (originFile == null) {
                response = Response.status(Status.INTERNAL_SERVER_ERROR).build();
            } else {
                long start = System.currentTimeMillis();
                Pair<List<List<Dataset>>, List<BibDataSet>> extractionResult = parser.processTEI(originFile, segmentSentences, disambiguate, addParagraphContext);
                long end = System.currentTimeMillis();

                List<List<Dataset>> extractedEntities = null;
                if (extractionResult != null) {
                    extractedEntities = extractionResult.getLeft();
                }

                StringBuilder json = new StringBuilder();
                json.append("{ ");
                json.append(DatastetServiceUtils.applicationDetails(GrobidProperties.getVersion()));
                
                String md5Str = DatatypeConverter.printHexBinary(digest).toUpperCase();
                json.append(", \"md5\": \"" + md5Str + "\"");
                json.append(", \"mentions\":[");
                if (extractedEntities != null && extractedEntities.size()>0) {
                    boolean startList = true;
                    for(List<Dataset> results : extractedEntities) {
                        for(Dataset dataset : results) {
                            if (startList)
                                startList = false;
                            else 
                                json.append(", ");
                            json.append(dataset.toJson());
                        }
                    }
                }
                json.append("], \"references\":[");

//                if (extractionResult != null) {
//                    List<BibDataSet> bibDataSet = extractionResult.getRight();
//                    if (bibDataSet != null && bibDataSet.size()>0) {
//                        DatastetServiceUtils.serializeReferences(json, bibDataSet, extractedEntities);
//                    }
//                }
                
                float runtime = ((float)(end-start)/1000);
                json.append(", \"runtime\": "+ runtime);

                json.append("}");

                Object finalJsonObject = mapper.readValue(json.toString(), Object.class);
                String retValString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(finalJsonObject);

                if (!isResultOK(retValString)) {
                    response = Response.status(Status.NO_CONTENT).build();
                } else {
                    response = Response.status(Status.OK).entity(retValString).type(MediaType.TEXT_PLAIN).build();
                    /*response = Response
                            .ok()
                            .type("application/json")
                            .entity(retValString)
                            .build();*/
                }
            }

        } catch (NoSuchElementException nseExp) {
            LOGGER.error("Could not get an instance of DatastetParser. Sending service unavailable.");
            response = Response.status(Status.SERVICE_UNAVAILABLE).build();
        } catch (Exception exp) {
            LOGGER.error("An unexpected exception occurs. ", exp);
            response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(exp.getMessage()).build();
        } finally {
            IOUtilities.removeTempFile(originFile);
        }
        LOGGER.debug(methodLogOut());
        return response;
    }

    public static String methodLogIn() {
        return ">> " + DatastetProcessFile.class.getName() + "." + Thread.currentThread().getStackTrace()[1].getMethodName();
    }

    public static String methodLogOut() {
        return "<< " + DatastetProcessFile.class.getName() + "." + Thread.currentThread().getStackTrace()[1].getMethodName();
    }

    private static boolean validateTrueFalseParam(String param) {
        boolean booleanOutput = false;
        if ((param != null) && (param.equals("1") || param.equalsIgnoreCase("true"))) {
            booleanOutput = true;
        }
        return booleanOutput;
    }

    /**
     * Check whether the result is null or empty.
     */
    public static boolean isResultOK(String result) {
        return StringUtils.isBlank(result) ? false : true;
    }

    public static Response processDatasetTEI(InputStream inputStream, boolean segmentSentences, boolean addParagraphContextBoolean) {
        return extractTEI(inputStream, segmentSentences, false, addParagraphContextBoolean);
    }
}
