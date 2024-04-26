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
@Path(DatastetPaths.PATH_DATASEER)
public class DatastetController implements DatastetPaths {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatastetController.class);

    private static final String TEXT = "text";
    private static final String XML = "xml";
    private static final String TEI = "tei";
    private static final String PDF = "pdf";
    private static final String INPUT = "input";
    private static final String JSON = "json";
    private static final String ADD_PARAGRAPH_CONTEXT = "addParagraphContext";
    private static final String SEGMENT_SENTENCES = "segmentSentences";

    private DatastetConfiguration configuration;

    @Inject
    public DatastetController(DatastetServiceConfiguration serviceConfiguration) {
        this.configuration = serviceConfiguration.getDatastetConfiguration();
    }

    @GET
    @Path(PATH_IS_ALIVE)
    @Produces(MediaType.TEXT_PLAIN)
    public Response isAlive() {
        return DatastetRestProcessGeneric.isAlive();
    }

    @Path(PATH_DATASEER_SENTENCE)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @POST
    public Response processText_post(@FormParam(TEXT) String text) {
        LOGGER.info(text);
        return DatastetProcessString.processSentence(text);
    }

    @Path(PATH_DATASEER_SENTENCE)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @GET
    public Response processText_get(@QueryParam(TEXT) String text) {
        LOGGER.info(text);
        return DatastetProcessString.processSentence(text);
    }

    @Path(PATH_DATASET_SENTENCE)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @POST
    public Response processDatasetText_post(@FormParam(TEXT) String text) {
        LOGGER.info(text);
        return DatastetProcessString.processDatsetSentence(text);
    }

    @Path(PATH_DATASET_SENTENCE)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @GET
    public Response processDatasetText_get(@QueryParam(TEXT) String text) {
        LOGGER.info(text);
        return DatastetProcessString.processDatsetSentence(text);
    }

    @Path(PATH_DATASEER_PDF)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_XML)
    @POST
    public Response processPDF(@FormDataParam(INPUT) InputStream inputStream) {
        return DatastetProcessFile.processPDF(inputStream);
    }

    @Path(PATH_DATASET_PDF)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_XML)
    @POST
    public Response processDatasetPDF(@FormDataParam(INPUT) InputStream inputStream,
                                      @DefaultValue("0") @FormDataParam(ADD_PARAGRAPH_CONTEXT) String addParagraphContext) {
        boolean addParagraphContextBoolean = DatastetServiceUtils.validateBooleanRawParam(addParagraphContext);
        return DatastetProcessFile.processDatasetPDF(inputStream, addParagraphContextBoolean);
    }

    @Path(PATH_DATASET_TEI)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @POST
    public Response processDatasetTEI(
            @FormDataParam(INPUT) InputStream inputStream,
            @DefaultValue("0") @FormDataParam(SEGMENT_SENTENCES) String segmentSentences,
            @DefaultValue("0") @FormDataParam(ADD_PARAGRAPH_CONTEXT) String addParagraphContext
    ) {
        boolean addParagraphContextBoolean = DatastetServiceUtils.validateBooleanRawParam(addParagraphContext);
        boolean segmentSentencesBoolean = DatastetServiceUtils.validateBooleanRawParam(segmentSentences);
        return DatastetProcessFile.processDatasetTEI(inputStream, segmentSentencesBoolean, addParagraphContextBoolean);
    }

    @Path(PATH_DATASEER_TEI)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_XML)
    @POST
    public Response processTEI(
            @FormDataParam(INPUT) InputStream inputStream,
            @FormDataParam("segmentSentences") String segmentSentences) {
        boolean segmentSentencesBoolean = DatastetServiceUtils.validateBooleanRawParam(segmentSentences);
        return DatastetProcessFile.processTEI(inputStream, segmentSentencesBoolean);
    }

    @Path(PATH_DATASEER_JATS)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_XML)
    @POST
    public Response processJATS(@FormDataParam(INPUT) InputStream inputStream) {
        return DatastetProcessFile.processJATS(inputStream);
    }

    @Path(PATH_DATATYPE_JSON)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @GET
    public Response getJsonDataTypes() {
        return DatastetDataTypeService.getInstance().getJsonDataTypes();
    }

    @Path(PATH_RESYNC_DATATYPE_JSON)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @GET
    public Response getResyncJsonDataTypes() {
        return DatastetDataTypeService.getInstance().getResyncJsonDataTypes();
    }
}
