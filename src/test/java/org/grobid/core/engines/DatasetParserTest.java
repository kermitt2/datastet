package org.grobid.core.engines;

import junit.framework.TestCase;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.grobid.core.GrobidModels;
import org.grobid.core.data.BibDataSet;
import org.grobid.core.data.Dataset;
import org.grobid.core.main.GrobidHomeFinder;
import org.grobid.core.utilities.GrobidConfig;
import org.grobid.core.utilities.GrobidProperties;
import org.junit.Before;
import org.junit.Test;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Predicates.notNull;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class DatasetParserTest extends TestCase {
    private DatasetParser target;

    @Before
    public void setUp() throws Exception {
        GrobidProperties.getInstance(new GrobidHomeFinder(Arrays.asList("../../grobid/grobid-home/")));
        GrobidConfig.ModelParameters modelParameters = new GrobidConfig.ModelParameters();
        modelParameters.name = "bao";
        GrobidProperties.addModel(modelParameters);
        target = new DatasetParser(GrobidModels.DUMMY);
    }


    @Test
    public void testGetXPathWithoutNamespaces() {
        String output = DatasetParser.getXPathWithoutNamespaces("//abstract/p/s");

        assertThat(output, is("//*[local-name() = 'abstract']/*[local-name() = 'p']/*[local-name() = 's']"));
    }

    @Test
    public void testProcessTEIDocument() throws Exception {
        String text = IOUtils.toString(Objects.requireNonNull(this.getClass().getResourceAsStream("erl_18_11_114012.tei.xml")), StandardCharsets.UTF_8);

        Pair<List<List<Dataset>>, List<BibDataSet>> listListPair = target.processTEIDocument(text, true, false, false);

        assertThat(listListPair, is(notNull()));

    }
}