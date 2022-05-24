package org.grobid.core.engines;

import org.apache.commons.io.IOUtils;
import org.grobid.core.document.Document;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.DataseerConfiguration;
import org.grobid.core.main.GrobidHomeFinder;
import org.grobid.core.utilities.GrobidConfig.ModelParameters;
import org.grobid.core.main.LibraryLoader;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

import org.apache.commons.lang3.tuple.Pair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertNotNull;

/**
 * @author Patrice
 */
public class DataseerClassifierTest {
    private static DataseerConfiguration configuration;

    @BeforeClass
    public static void setUpClass() throws Exception {
        DataseerConfiguration dataseerConfiguration = null;
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

            File yamlFile = new File("resources/config/dataseer-ml.yml");
            yamlFile = new File(yamlFile.getAbsolutePath());
            dataseerConfiguration = mapper.readValue(yamlFile, DataseerConfiguration.class);

            String pGrobidHome = dataseerConfiguration.getGrobidHome();

            GrobidHomeFinder grobidHomeFinder = new GrobidHomeFinder(Arrays.asList(pGrobidHome));
            GrobidProperties.getInstance(grobidHomeFinder);
    
            System.out.println(">>>>>>>> GROBID_HOME="+GrobidProperties.get_GROBID_HOME_PATH());

            if (dataseerConfiguration != null && dataseerConfiguration.getModels() != null) {
                for (ModelParameters model : dataseerConfiguration.getModels())
                    GrobidProperties.getInstance().addModel(model);
            }
            LibraryLoader.load();

        } catch (final Exception exp) {
            System.err.println("dataseer-ml initialisation failed: " + exp);
            exp.printStackTrace();
        }

        configuration = dataseerConfiguration;
    }

    @Before
    public void getTestResourcePath() {
        GrobidProperties.getInstance();
    }

    @Test
    public void testDataseerBinaryClassifierText() throws Exception {
        String text = IOUtils.toString(this.getClass().getResourceAsStream("/texts.txt"), StandardCharsets.UTF_8.toString());
        String[] textPieces = text.split("\n");
        List<String> texts = new ArrayList<>();
        for (int i=0; i<textPieces.length; i++) {
            text = textPieces[i].replace("\\t", " ").replaceAll("( )+", " ");
            System.out.println(text);
            texts.add(text);
        }
        String json = DataseerClassifier.getInstance().classify(texts);
        System.out.println(json);
    }

}