package org.grobid.service.exceptions;

import org.grobid.core.exceptions.GrobidException;
import javax.ws.rs.core.Response;

public class DataseerServiceException extends GrobidException {

    private Response.Status responseCode;

    public DataseerServiceException(Response.Status responseCode) {
        super();
        this.responseCode = responseCode;
    }

    public DataseerServiceException(String msg, Response.Status responseCode) {
        super(msg);
        this.responseCode = responseCode;
    }

    public DataseerServiceException(String msg, Throwable cause, Response.Status responseCode) {
        super(msg, cause);
        this.responseCode = responseCode;
    }

    public Response.Status getResponseCode() {
        return responseCode;
    }
}
