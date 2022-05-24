package org.grobid.service.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class DataseerRestProcessGeneric {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataseerRestProcessGeneric.class);

    /**
     * Returns a string containing true, if the service is alive.
     *
     * @return returns a response object containing the string true if service
     * is alive.
     */
    public static Response isAlive() {
        Response response = null;
        try {
            LOGGER.debug("Called isAlive()...");

            String retVal = null;
            try {
                retVal = Boolean.valueOf(true).toString();
            } catch (Exception e) {
                LOGGER.error("dataseer-ml service is not alive. ", e);
                retVal = Boolean.valueOf(false).toString();
            }
            response = Response.status(Status.OK).entity(retVal).build();
        } catch (Exception e) {
            LOGGER.error("Exception occurred while check if the service is alive. " + e);
            response = Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return response;
    }
    
}
