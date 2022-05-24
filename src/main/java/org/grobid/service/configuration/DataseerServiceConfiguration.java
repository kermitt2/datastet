package org.grobid.service.configuration;

import io.dropwizard.Configuration;
import org.grobid.core.utilities.DataseerConfiguration;

public class DataseerServiceConfiguration extends Configuration {

    private String grobidHome;
    private DataseerConfiguration dataseerConfiguration;

    public String getGrobidHome() {
        return grobidHome;
    }

    public void setGrobidHome(String grobidHome) {
        this.grobidHome = grobidHome;
    }

    public DataseerConfiguration getDataseerConfiguration() {
        return this.dataseerConfiguration;
    }

    public void setDataseerConfiguration(DataseerConfiguration conf) {
        this.dataseerConfiguration = conf;
    }
}
