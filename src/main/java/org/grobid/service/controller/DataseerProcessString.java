package org.grobid.service.controller;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.grobid.core.engines.DataseerClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.List;
import java.util.NoSuchElementException;

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
