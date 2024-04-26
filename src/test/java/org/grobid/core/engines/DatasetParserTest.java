package org.grobid.core.engines;

import junit.framework.TestCase;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class DatasetParserTest extends TestCase {

    @Test
    public void testGetXPathWithoutNamespaces() {
        String output = DatasetParser.getXPathWithoutNamespaces("//abstract/p/s");

        assertThat(output, is("//*[local-name() = 'abstract']/*[local-name() = 'p']/*[local-name() = 's']"));
    }

}