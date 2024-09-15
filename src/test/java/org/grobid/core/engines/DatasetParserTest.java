package org.grobid.core.engines;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.io.IOUtils;
import org.grobid.core.data.Dataset;
import org.grobid.core.main.GrobidHomeFinder;
import org.grobid.core.main.LibraryLoader;
import org.grobid.core.utilities.DatastetConfiguration;
import org.grobid.core.utilities.GrobidConfig.ModelParameters;
import org.grobid.core.utilities.GrobidProperties;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Patrice
 */
@Ignore
public class DatasetParserTest {
    private static DatastetConfiguration configuration;

    @BeforeClass
    public static void setUpClass() throws Exception {
        DatastetConfiguration dataseerConfiguration = null;
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

            File yamlFile = new File("resources/config/dataseer-ml.yml").getAbsoluteFile();
            yamlFile = new File(yamlFile.getAbsolutePath());
            dataseerConfiguration = mapper.readValue(yamlFile, DatastetConfiguration.class);

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
    public void testDatasetParserText() throws Exception {
        String text = IOUtils.toString(this.getClass().getResourceAsStream("/texts.txt"), StandardCharsets.UTF_8.toString());
        String[] textPieces = text.split("\n");
        List<String> texts = new ArrayList<>();
        for (int i=0; i<textPieces.length; i++) {
            text = textPieces[i].replace("\\t", " ").replaceAll("( )+", " ");
            //System.out.println(text);
            texts.add(text);
        }

        List<List<Dataset>> results = DatasetParser.getInstance(configuration).processingStrings(texts, false);
        StringBuilder json = new StringBuilder();

        int i = 0;
        for(List<Dataset> result : results) {
            for(Dataset dataset : result) {
                json.append(dataset.toJson());
                json.append("\n");
            }
        }
        System.out.println(json);
    }

}