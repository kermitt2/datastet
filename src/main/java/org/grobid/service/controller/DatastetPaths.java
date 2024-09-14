package org.grobid.service.controller;

/**
 * This interface only contains the path extensions for accessing the dataseer module service.
 *
 * @author Patrice
 *
 */
public interface DatastetPaths {
    /**
     * path extension for dataseer service.
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
     * path extension for classifying a textual sentence input.
     */
    public static final String PATH_DATASEER_SENTENCE = "annotateDataseerSentence";
    public static final String PATH_OLD_DATASEER_SENTENCE = "processDataseerSentence";

    public static final String PATH_DATASEER_SENTENCES = "annotateDataseerSentences";
    public static final String PATH_OLD_DATASEER_SENTENCES = "processDataseerSentences";

    /**
     * path extension for processing a TEI file 
     * (for instance produced by GROBID or Pub2TEI).
     */
    public static final String PATH_DATASEER_TEI = "processDataseerTEI";

    /**
     * path extension for processing a JATS file.
     */
    public static final String PATH_DATASEER_JATS = "processDataseerJATS";

    /**
     * path extension for processing a PDF file, which will include its conversion 
     * into TEI via GROBID.
     */
    public static final String PATH_DATASEER_PDF = "processDataseerPDF";

    /**
     * path extension for getting the json datatype resource file 
     */
    public static final String PATH_DATATYPE_JSON = "jsonDataTypes";

    /**
     * path extension to re-sync the json datatype resource file with the DokuWiki
     */
    public static final String PATH_RESYNC_DATATYPE_JSON = "resyncJsonDataTypes";

}
