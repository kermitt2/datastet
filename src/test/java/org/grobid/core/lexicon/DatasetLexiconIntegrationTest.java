package org.grobid.core.lexicon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.grobid.core.main.GrobidHomeFinder;
import org.grobid.core.utilities.DatastetConfiguration;
import org.grobid.core.utilities.GrobidConfig.ModelParameters;
import org.grobid.core.utilities.GrobidProperties;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author Patrice
 */
public class DatasetLexiconIntegrationTest {
    private static DatastetLexicon target;

    @BeforeClass
    public static void setUpClass() throws Exception {
        DatastetConfiguration dataseerConfiguration = null;
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            dataseerConfiguration = mapper.readValue(new File("resources/config/config.yml").getAbsoluteFile(), DatastetConfiguration.class);

            String pGrobidHome = dataseerConfiguration.getGrobidHome();

            GrobidHomeFinder grobidHomeFinder = new GrobidHomeFinder(Arrays.asList(pGrobidHome));
            GrobidProperties.getInstance(grobidHomeFinder);

            System.out.println(">>>>>>>> GROBID_HOME=" + GrobidProperties.get_GROBID_HOME_PATH());

            if (dataseerConfiguration != null && dataseerConfiguration.getModel() != null) {
                for (ModelParameters model : dataseerConfiguration.getModels())
                    GrobidProperties.getInstance().addModel(model);
            }
            //LibraryLoader.load();
            target = target.getInstance();
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

        boolean zenodoCheck = target.getInstance().isDatasetDOI(testStringZenodo);
        boolean dryadCheck = target.getInstance().isDatasetDOI(testStringDryad);
        boolean figshareCheck = target.getInstance().isDatasetDOI(testStringFigshare);

        assertThat(zenodoCheck, is(true));
        assertThat(dryadCheck, is(true));
        assertThat(figshareCheck, is(true));
    }

    @Test
    public void testDatasetDOIFail() throws Exception {
        String testStringFirst = "https://doi.org/10.1038/s41523-019-0142-6";
        String testStringSecond = "https://doi.org/10.1371/journal.pone.0263302";
        String testStringThird = "https://doi.org/10.1186/s13064-019-0127-z";

        boolean firstCheck = target.isDatasetDOI(testStringFirst);
        boolean secondCheck = target.isDatasetDOI(testStringSecond);
        boolean thirdCheck = target.isDatasetDOI(testStringThird);

        assertThat(firstCheck, is(false));
        assertThat(secondCheck, is(false));
        assertThat(thirdCheck, is(false));
    }

    @Test
    public void testDatasetUrlSuccess() throws Exception {
        String testStringId = "https://identifiers.org/ega.dataset:EGAD00010000210";
        String testStringGithub = "https://github.com/leonfodoulian/SARS_CoV_2_anosmia";
        String testStringOsf = "https://osf.io/5r72u";

        boolean idCheck = target.isDatasetURL(testStringId);
        boolean githubCheck = target.isDatasetURL(testStringGithub);
        boolean osfCheck = target.isDatasetURL(testStringOsf);

        assertThat(idCheck, is(true));
        assertThat(githubCheck, is(true));
        assertThat(osfCheck, is(true));
    }

    @Test
    public void testDatasetUrlFail() throws Exception {
        String testStringFirst = "https://google.com";
        String testStringSecond = "https://nlp.johnsnowlabs.com/api/com/johnsnowlabs/nlp/annotators/LemmatizerModel.html";
        String testStringThird = "https://stackoverflow.com/questions/11976393/get-github-username-by-id";

        boolean firstCheck = target.isDatasetURL(testStringFirst);
        boolean secondCheck = target.isDatasetURL(testStringSecond);
        boolean thirdCheck = target.isDatasetURL(testStringThird);

        assertThat(firstCheck, is(false));
        assertThat(secondCheck, is(false));
        assertThat(thirdCheck, is(false));
    }

    @Test
    public void testLeadingStopwords() throws Exception {
        String testStringFirst = "and the dataset TOTO";
        String testStringSecond = "and the dataset TOTO of";

        String firstCheck = target.removeLeadingEnglishStopwords(testStringFirst);
        String secondCheck = target.removeLeadingEnglishStopwords(testStringSecond);

        assertThat(firstCheck, is("dataset TOTO"));
        assertThat(secondCheck, is("dataset TOTO of"));
    }

    @Test
    public void testFilterMatches() throws Exception {
        String testStringFirst = " 1/2";
        String testStringSecond = "(";
        String testStringThird = "https://stackoverflow.com";

        boolean firstCheck = testStringFirst.matches("[0-9\\(\\)/\\[\\]\\,\\.\\:\\-\\+\\; ]+");
        boolean secondCheck = testStringSecond.matches("[0-9\\(\\)/\\[\\]\\,\\.\\:\\-\\+\\; ]+");
        boolean thirdCheck = testStringThird.matches("[0-9\\(\\)/\\[\\]\\,\\.\\:\\-\\+\\; ]+");

        assertThat(firstCheck, is(true));
        assertThat(secondCheck, is(true));
        assertThat(thirdCheck, is(false));
    }

}