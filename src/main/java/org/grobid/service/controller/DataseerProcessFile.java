package org.grobid.service.controller;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.grobid.core.document.Document;
import org.grobid.core.data.Dataset;
import org.grobid.core.engines.DataseerClassifier;
import org.grobid.core.engines.Engine;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.engines.DatasetParser;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.layout.Page;
import org.grobid.core.utilities.IOUtilities;
import org.grobid.core.utilities.ArticleUtilities;
import org.grobid.core.utilities.DataseerUtilities;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.service.exceptions.DataseerServiceException;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.NoSuchElementException;

import org.apache.commons.lang3.tuple.Pair;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.io.*;

import java.security.DigestInputStream;
import java.security.MessageDigest;
import javax.xml.bind.DatatypeConverter;

import org.grobid.core.data.BibDataSet;
import org.grobid.core.data.BiblioComponent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Patrice
 */
@Singleton
public class DataseerProcessFile {

    /**
     * The class Logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(DataseerProcessFile.class);

    @Inject
    public DataseerProcessFile() {
    }

    /**
     * Uploads a TEI document, identify dataset introductory section, segment and classify sentences.
     *
     * @param inputStream the data of origin TEI document
     * @return a response object which contains an enriched TEI representation of the document
     */
    public static Response processTEI(final InputStream inputStream) {
        LOGGER.debug(methodLogIn());
        String retVal = null;
        Response response = null;
        File originFile = null;
        DataseerClassifier classifier = DataseerClassifier.getInstance();
        try {
            originFile = ArticleUtilities.writeInputFile(inputStream, ".tei.xml");
            if (originFile == null) {
                LOGGER.error("The input file cannot be written.");
                throw new DataseerServiceException(
                    "The input file cannot be written. ", Status.INTERNAL_SERVER_ERROR);
            } 

            // starts conversion process
            retVal = classifier.processTEI(originFile.getAbsolutePath(), true, false);

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
                throw new DataseerServiceException(
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
                throw new DataseerServiceException(
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
    public static Response processDatasetPDF(final InputStream inputStream) {
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
                throw new DataseerServiceException(
                    "The input file cannot be written. ", Status.INTERNAL_SERVER_ERROR);
            } 

            long start = System.currentTimeMillis();
            // starts conversion process
            Pair<List<List<Dataset>>, Document> extractedResults = 
                    parser.processPDF(originFile, disambiguate);
            
            StringBuilder json = new StringBuilder();
            json.append("{ ");
            json.append(DataseerUtilities.applicationDetails(classifier.getDatastetConfiguration().getVersion()));
            
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
                serializeReferences(json, bibDataSet, extractedResults.getLeft());
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

    public static String methodLogIn() {
        return ">> " + DataseerProcessFile.class.getName() + "." + Thread.currentThread().getStackTrace()[1].getMethodName();
    }

    public static String methodLogOut() {
        return "<< " + DataseerProcessFile.class.getName() + "." + Thread.currentThread().getStackTrace()[1].getMethodName();
    }

    /**
     * Check whether the result is null or empty.
     */
    public static boolean isResultOK(String result) {
        return StringUtils.isBlank(result) ? false : true;
    }

    /**
     * Serialize the bibliographical references present in a list of entities
     */ 
    public static void serializeReferences(StringBuilder json, 
                                           List<BibDataSet> bibDataSet, 
                                           List<List<Dataset>> entities) {
        ObjectMapper mapper = new ObjectMapper();
        List<Integer> serializedKeys = new ArrayList<Integer>();
        for(List<Dataset> datasets : entities) {
            for(Dataset entity : datasets) {
                List<BiblioComponent> bibRefs = entity.getBibRefs();
                if (bibRefs != null) {
                    for(BiblioComponent bibComponent : bibRefs) {
                        int refKey = bibComponent.getRefKey();
                        if (!serializedKeys.contains(refKey)) {
                            if (serializedKeys.size()>0)
                                json.append(", ");
                            if (bibComponent.getBiblio() != null) {
                                json.append("{ \"refKey\": " + refKey);
                                try {
                                    json.append(", \"tei\": " + mapper.writeValueAsString(bibComponent.getBiblio().toTEI(refKey)));
                                } catch (JsonProcessingException e) {
                                    LOGGER.warn("tei for biblio cannot be encoded", e);
                                }
                                json.append("}");
                            }
                            serializedKeys.add(Integer.valueOf(refKey));
                        }
                    }
                }
            }
        }
    }

}
