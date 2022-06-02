package org.grobid.service.controller;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.grobid.core.engines.DataseerClassifier;
import org.grobid.core.engines.DatasetParser;
import org.grobid.core.data.Dataset;
import org.grobid.core.data.Dataset.DatasetType;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.DataseerUtilities;
import org.grobid.core.utilities.TextUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.io.*;

/**
 * 
 * @author Patrice
 * 
 */
@Singleton
public class DataseerProcessString {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataseerProcessString.class);

    @Inject
    public DataseerProcessString() {
    }

    /**
     * Determine if a provided sentence introduces a dataset and classify the type of the dataset.
     * 
     * @param text
     *            raw sentence string
     * @return a json response object containing the information related to possible dataset
     */
    public static Response processSentence(String text) {
        LOGGER.debug(methodLogIn());
        Response response = null;
        StringBuilder retVal = new StringBuilder();
        DataseerClassifier classifier = DataseerClassifier.getInstance();
        try {
            LOGGER.debug(">> set raw sentence text for stateless service'...");
            
            text = text.replaceAll("\\n", " ").replaceAll("\\t", " ");
            long start = System.currentTimeMillis();
            String retValString = classifier.classify(text);
            long end = System.currentTimeMillis();

            // TBD: update json with runtime and software/version 

            if (!isResultOK(retValString)) {
                response = Response.status(Status.NO_CONTENT).build();
            } else {
                response = Response.status(Status.OK).entity(retValString).type(MediaType.TEXT_PLAIN).build();
            }
        } catch (NoSuchElementException nseExp) {
            LOGGER.error("Could not get an instance of DataseerClassifier. Sending service unavailable.");
            response = Response.status(Status.SERVICE_UNAVAILABLE).build();
        } catch (Exception e) {
            LOGGER.error("An unexpected exception occurs. ", e);
            response = Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } 
        LOGGER.debug(methodLogOut());
        return response;
    }

    /**
     * Label dataset names, implicit datasets and data acquisition devices in a sentence.
     * 
     * @param text 
     *          raw sentence string
     * @return a json response object containing the labeling information related to possible 
     *          dataset mentions
     */
    public static Response processDatsetSentence(String text) {
        LOGGER.debug(methodLogIn());
        Response response = null;
        StringBuilder retVal = new StringBuilder();
        DataseerClassifier classifier = DataseerClassifier.getInstance();
        DatasetParser parser = DatasetParser.getInstance(classifier.getDataseerConfiguration());
        JsonStringEncoder encoder = JsonStringEncoder.getInstance();
        try {
            LOGGER.debug(">> set raw sentence text for stateless service'...");
            
            text = text.replaceAll("\\n", " ").replaceAll("\\t", " ").replace("  ", " ");
            long start = System.currentTimeMillis();
            List<Dataset> result = parser.processingString(text);

            // building JSON response
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append(DataseerUtilities.applicationDetails(classifier.getDataseerConfiguration().getVersion()));

            byte[] encoded = encoder.quoteAsUTF8(text);
            String output = new String(encoded);

            //json.append(", \"text\": \"" + output + "\"");
            json.append(", \"mentions\": [");

            ObjectMapper mapper = new ObjectMapper();

            String classifierJson = classifier.classify(text);

            JsonNode rootNode = mapper.readTree(classifierJson);

            // get best type
            double bestScore = 0.0;
            String bestType = null;
            double hasDatasetScore = 0.0;

            JsonNode classificationsNode = rootNode.findPath("classifications"); 
            if ((classificationsNode != null) && (!classificationsNode.isMissingNode())) {

                if (classificationsNode.isArray()) {
                    ArrayNode classificationsArray = (ArrayNode)classificationsNode;
                    JsonNode classificationNode = classificationsArray.get(0);

                    Iterator<String> iterator = classificationNode.fieldNames();
                    Map<String, Double> scoresPerDatatypes = new TreeMap<>();
                    while (iterator.hasNext()) {
                        String field = iterator.next();

                        if (field.equals("has_dataset")) {
                            JsonNode hasDatasetNode = rootNode.findPath("has_dataset"); 
                            if ((hasDatasetNode != null) && (!hasDatasetNode.isMissingNode())) {
                                hasDatasetScore = hasDatasetNode.doubleValue();
                                
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
                }
            }

            boolean startList = true;
            for(Dataset dataset : result) {
                if (startList)
                    startList = false;
                else 
                    json.append(", ");

                if (dataset.getType() == DatasetType.DATASET && (bestType != null) && dataset.getDataset() != null) {
                    dataset.getDataset().setBestDataType(bestType);
                    dataset.getDataset().setBestDataTypeScore(bestScore);
                    dataset.getDataset().setHasDatasetScore(hasDatasetScore);
                }

                json.append(dataset.toJson());
            }
            json.append("]");

            long end = System.currentTimeMillis();
            float runtime = ((float)(end-start)/1000);
            json.append(", \"runtime\": "+ runtime);
            json.append("}");

            System.out.println(json.toString());

            Object finalJsonObject = mapper.readValue(json.toString(), Object.class);
            String retValString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(finalJsonObject);

            if (!isResultOK(retValString)) {
                response = Response.status(Status.NO_CONTENT).build();
            } else {
                response = Response.status(Status.OK).entity(retValString).type(MediaType.TEXT_PLAIN).build();
            }
        } catch (NoSuchElementException nseExp) {
            LOGGER.error("Could not get an instance of DatasetParser. Sending service unavailable.");
            response = Response.status(Status.SERVICE_UNAVAILABLE).build();
        } catch (Exception e) {
            LOGGER.error("An unexpected exception occurs. ", e);
            response = Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } 
        LOGGER.debug(methodLogOut());
        return response;
    }


    /**
     * @return
     */
    public static String methodLogIn() {
        return ">> " + DataseerProcessString.class.getName() + "." + Thread.currentThread().getStackTrace()[1].getMethodName();
    }

    /**
     * @return
     */
    public static String methodLogOut() {
        return "<< " + DataseerProcessString.class.getName() + "." + Thread.currentThread().getStackTrace()[1].getMethodName();
    }

    /**
     * Check whether the result is null or empty.
     */
    public static boolean isResultOK(String result) {
        return StringUtils.isBlank(result) ? false : true;
    }

}
