package org.grobid.service;

import com.google.common.collect.ImmutableList;
import org.grobid.core.main.GrobidHomeFinder;
import org.grobid.core.main.LibraryLoader;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.engines.tagging.GrobidCRFEngine;
import org.grobid.core.lexicon.DataseerLexicon;
import org.grobid.service.configuration.DataseerServiceConfiguration;
import org.grobid.core.utilities.DataseerConfiguration;
import org.grobid.core.utilities.GrobidConfig.ModelParameters;

import java.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GrobidEngineInitialiser {
    private static final Logger LOGGER = LoggerFactory.getLogger(org.grobid.service.GrobidEngineInitialiser.class);

    @Inject
    public GrobidEngineInitialiser(DataseerServiceConfiguration configuration) {
        LOGGER.info("Initialising Grobid");
        GrobidHomeFinder grobidHomeFinder = new GrobidHomeFinder(ImmutableList.of(configuration.getGrobidHome()));
        GrobidProperties.getInstance(grobidHomeFinder);
        DataseerLexicon.getInstance();

        DataseerConfiguration dataseerConfiguration = null;
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            dataseerConfiguration = mapper.readValue(new File("resources/config/dataseer-ml.yml"), DataseerConfiguration.class);
        } catch(Exception e) {
            LOGGER.error("The config file does not appear valid, see resources/config/dataseer-ml.yml", e);
            dataseerConfiguration = null;
        }

        configuration.setDataseerConfiguration(dataseerConfiguration);

        if (dataseerConfiguration != null && dataseerConfiguration.getModels() != null) {
            for (ModelParameters model : dataseerConfiguration.getModels())
                GrobidProperties.getInstance().addModel(model);
        }
        LibraryLoader.load();
    }
}
