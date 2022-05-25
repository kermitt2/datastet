package org.grobid.service.controller;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.grobid.core.engines.DataseerClassifier;
import org.grobid.core.engines.DatasetParser;
import org.grobid.core.data.Dataset;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.DataseerUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.List;
import java.util.NoSuchElementException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.io.JsonStringEncoder;

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
            long end = System.currentTimeMillis();

            // building JSON response
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append(DataseerUtilities.applicationDetails(GrobidProperties.getVersion()));
            float runtime = ((float)(end-start)/1000);
            json.append(", \"runtime\": "+ runtime);

            byte[] encoded = encoder.quoteAsUTF8(text);
            String output = new String(encoded);

            json.append(", \"text\": \"" + output + "\"");
            json.append(", \"entities\": [");

            boolean startList = true;
            for(Dataset dataset : result) {
                if (startList)
                    startList = false;
                else 
                    json.append(", ");
                json.append(dataset.toJson());
            }
            json.append("]}");

            ObjectMapper mapper = new ObjectMapper();
            Object jsonObject = mapper.readValue(json.toString(), Object.class);
            String retValString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);

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
