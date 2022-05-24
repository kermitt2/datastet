package org.grobid.core.lexicon;

import org.grobid.core.analyzers.DataseerAnalyzer;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.exceptions.GrobidResourceException;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.utilities.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class for managing the lexical resources for dataseer
 *
 * @author Patrice
 */
public class DataseerLexicon {

    // Dataset base types
    public enum DataType {
        UNKNOWN("UNKNOWN"),
        GENERIC("GENERIC");

        private String name;

        private DataType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private static Logger LOGGER = LoggerFactory.getLogger(DataseerLexicon.class);

    private static volatile DataseerLexicon instance;

    public static synchronized DataseerLexicon getInstance() {
        if (instance == null)
            instance = new DataseerLexicon();

        return instance;
    }

    private DataseerLexicon() {
        Lexicon.getInstance();
        // init the lexicon
        LOGGER.info("Init dataseer lexicon");
    }
}
