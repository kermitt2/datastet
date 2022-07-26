package org.grobid.core.lexicon;

import org.apache.commons.io.IOUtils;
import org.grobid.core.analyzers.DataseerAnalyzer;
import org.grobid.core.data.Dataset;
import org.grobid.core.document.Document;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.DataseerConfiguration;
import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.main.GrobidHomeFinder;
import org.grobid.core.utilities.GrobidConfig.ModelParameters;
import org.grobid.core.main.LibraryLoader;
import org.grobid.core.utilities.Pair;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * @author Patrice
 */
public class DatasetLexiconTest {
    private static DataseerLexicon dataseerLexicon;

    @BeforeClass
    public static void setUpClass() throws Exception {
        DataseerConfiguration dataseerConfiguration = null;
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            dataseerConfiguration = mapper.readValue(new File("resources/config/dataseer-ml.yml").getAbsoluteFile(), DataseerConfiguration.class);

            String pGrobidHome = dataseerConfiguration.getGrobidHome();

            GrobidHomeFinder grobidHomeFinder = new GrobidHomeFinder(Arrays.asList(pGrobidHome));
            GrobidProperties.getInstance(grobidHomeFinder);
    
            System.out.println(">>>>>>>> GROBID_HOME="+GrobidProperties.get_GROBID_HOME_PATH());

            if (dataseerConfiguration != null && dataseerConfiguration.getModel() != null) {
                for (ModelParameters model : dataseerConfiguration.getModels())
                    GrobidProperties.getInstance().addModel(model);
            }
            //LibraryLoader.load();

            dataseerLexicon = DataseerLexicon.getInstance();

        } catch (final Exception exp) {
            System.err.println("GROBID dataset initialisation failed: " + exp);
            exp.printStackTrace();
        }
    }

    @Test
    public void testDatasetDOISuccess() throws Exception {
        String testStringZenodo = "10.5281/zenodo.5769577";
        String testStringDryad = "https://doi.org/10.5061/DRYAD.0SN63/7";
        String testStringFigshare = "https://doi.org/10.6084/m9.ﬁgshare.10275182";
        
        boolean zenodoCheck = DataseerLexicon.getInstance().isDatasetDOI(testStringZenodo);
        boolean dryadCheck = DataseerLexicon.getInstance().isDatasetDOI(testStringDryad);
        boolean figshareCheck = DataseerLexicon.getInstance().isDatasetDOI(testStringFigshare);

        assertThat(zenodoCheck, is(true));
        assertThat(dryadCheck, is(true));
        assertThat(figshareCheck, is(true));
    }

    @Test
    public void testDatasetDOIFail() throws Exception {
        String testStringFirst = "https://doi.org/10.1038/s41523-019-0142-6";
        String testStringSecond = "https://doi.org/10.1371/journal.pone.0263302";
        String testStringThird = "https://doi.org/10.1186/s13064-019-0127-z";
        
        boolean firstCheck = DataseerLexicon.getInstance().isDatasetDOI(testStringFirst);
        boolean secondCheck = DataseerLexicon.getInstance().isDatasetDOI(testStringSecond);
        boolean thirdCheck = DataseerLexicon.getInstance().isDatasetDOI(testStringThird);

        assertThat(firstCheck, is(false));
        assertThat(secondCheck, is(false));
        assertThat(thirdCheck, is(false));
    }

    @Test
    public void testDatasetUrlSuccess() throws Exception {
        String testStringId = "https://identifiers.org/ega.dataset:EGAD00010000210";
        String testStringGithub = "https://github.com/leonfodoulian/SARS_CoV_2_anosmia";
        String testStringOsf = "https://osf.io/5r72u";

        boolean idCheck = DataseerLexicon.getInstance().isDatasetURL(testStringId);
        boolean githubCheck = DataseerLexicon.getInstance().isDatasetURL(testStringGithub);
        boolean osfCheck = DataseerLexicon.getInstance().isDatasetURL(testStringOsf);

        assertThat(idCheck, is(true));
        assertThat(githubCheck, is(true));
        assertThat(osfCheck, is(true));
    }

    @Test
    public void testDatasetUrlFail() throws Exception {
        String testStringFirst = "https://google.com";
        String testStringSecond = "https://nlp.johnsnowlabs.com/api/com/johnsnowlabs/nlp/annotators/LemmatizerModel.html";
        String testStringThird = "https://stackoverflow.com/questions/11976393/get-github-username-by-id";

        boolean firstCheck = DataseerLexicon.getInstance().isDatasetURL(testStringFirst);
        boolean secondCheck = DataseerLexicon.getInstance().isDatasetURL(testStringSecond);
        boolean thirdCheck = DataseerLexicon.getInstance().isDatasetURL(testStringThird);

        assertThat(firstCheck, is(false));
        assertThat(secondCheck, is(false));
        assertThat(thirdCheck, is(false));
    }

}