package org.grobid.service.controller;

/**
 * This interface only contains the path extensions for accessing the datastet module service.
 *
 * @author Patrice
 */
public interface DatastetPaths {
    /**
     * path extension for datastet service.
     */
    public static final String PATH_DATASEER = "/";

    /**
     * path extension for is alive request.
     */
    public static final String PATH_IS_ALIVE = "isalive";

    /**
     * path extension for processing a textual sentence input for dataset mentions.
     */
    public static final String PATH_DATASET_SENTENCE = "annotateDatasetSentence";

    /**
     * path extension for annotating a PDF file with the dataset-relevant mentions
     */
    public static final String PATH_DATASET_PDF = "annotateDatasetPDF";

    public static final String PATH_DATASET_TEI = "processDatasetTEI";

    public static final String PATH_DATASET_JATS = "processDatasetJATS";

    /**
     * path extension for getting the json datatype resource file
     */
    public static final String PATH_DATATYPE_JSON = "jsonDataTypes";

    /**
     * path extension to re-sync the json datatype resource file with the DokuWiki
     */
    public static final String PATH_RESYNC_DATATYPE_JSON = "resyncJsonDataTypes";

    /**
     * path extension returning the running version + git revision of the service.
     */
    public static final String PATH_VERSION = "version";

}
