package org.grobid.service.controller;

import org.glassfish.jersey.media.multipart.FormDataParam;
import org.grobid.core.lexicon.DatastetLexicon;
import org.grobid.core.main.GrobidHomeFinder;
import org.grobid.core.main.LibraryLoader;
import org.grobid.core.utilities.DatastetConfiguration;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.GrobidConfig.ModelParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.io.File;
import java.util.Arrays;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.grobid.service.configuration.DatastetServiceConfiguration;

/**
 * RESTful service for GROBID dataseer extension.
 *
 * @author Patrice
 */
@Singleton
@Path(DataseerPaths.PATH_DATASEER)
public class DataseerController implements DataseerPaths {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataseerController.class);

    private static final String TEXT = "text";
    private static final String XML = "xml";
    private static final String TEI = "tei";
    private static final String PDF = "pdf";
    private static final String INPUT = "input";
    private static final String JSON = "json";

    private DatastetConfiguration configuration;

    @Inject
    public DataseerController(DatastetServiceConfiguration serviceConfiguration) {
        this.configuration = serviceConfiguration.getDatastetConfiguration();
    }

    @GET
    @Path(PATH_IS_ALIVE)
    @Produces(MediaType.TEXT_PLAIN)
    public Response isAlive() {
        return DataseerRestProcessGeneric.isAlive();
    }

    @Path(PATH_DATASEER_SENTENCE)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @POST
    public Response processText_post(@FormParam(TEXT) String text) {
        LOGGER.info(text);
        return DataseerProcessString.processSentence(text);
    }

    @Path(PATH_DATASEER_SENTENCE)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @GET
    public Response processText_get(@QueryParam(TEXT) String text) {
        LOGGER.info(text);
        return DataseerProcessString.processSentence(text);
    }

    @Path(PATH_DATASET_SENTENCE)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @POST
    public Response processDatasetText_post(@FormParam(TEXT) String text) {
        LOGGER.info(text);
        return DataseerProcessString.processDatsetSentence(text);
    }
    
    @Path(PATH_DATASET_SENTENCE)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @GET
    public Response processDatasetText_get(@QueryParam(TEXT) String text) {
        LOGGER.info(text);
        return DataseerProcessString.processDatsetSentence(text);
    }

    @Path(PATH_DATASEER_PDF)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_XML)
    @POST
    public Response processPDF(@FormDataParam(INPUT) InputStream inputStream) {
        return DataseerProcessFile.processPDF(inputStream);
    }

    @Path(PATH_DATASET_PDF)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_XML)
    @POST
    public Response processDatasetPDF(@FormDataParam(INPUT) InputStream inputStream) {
        return DataseerProcessFile.processDatasetPDF(inputStream);
    }

    @Path(PATH_DATASEER_TEI)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_XML)
    @POST
    public Response processTEI(@FormDataParam(INPUT) InputStream inputStream) {
        return DataseerProcessFile.processTEI(inputStream);
    }

    @Path(PATH_DATASEER_JATS)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_XML)
    @POST
    public Response processJATS(@FormDataParam(INPUT) InputStream inputStream) {
        return DataseerProcessFile.processJATS(inputStream);
    }

    @Path(PATH_DATATYPE_JSON)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @GET
    public Response getJsonDataTypes() {
        return DataseerDataTypeService.getInstance().getJsonDataTypes();
    }

    @Path(PATH_RESYNC_DATATYPE_JSON)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @GET
    public Response getResyncJsonDataTypes() {
        return DataseerDataTypeService.getInstance().getResyncJsonDataTypes();
    }
}
