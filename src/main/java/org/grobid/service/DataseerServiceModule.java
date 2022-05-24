package org.grobid.service;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Binder;
import com.google.inject.Provides;
import com.hubspot.dropwizard.guicier.DropwizardAwareModule;
import org.grobid.service.configuration.DataseerServiceConfiguration;
import org.grobid.service.controller.DataseerController;
import org.grobid.service.controller.HealthCheck;
import org.grobid.service.controller.DataseerProcessFile;
import org.grobid.service.controller.DataseerProcessString;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;


public class DataseerServiceModule extends DropwizardAwareModule<DataseerServiceConfiguration> {

    @Override
    public void configure(Binder binder) {
        // Generic modules
        binder.bind(GrobidEngineInitialiser.class);
        binder.bind(HealthCheck.class);

        // Core components
        binder.bind(DataseerProcessFile.class);
        binder.bind(DataseerProcessString.class);

        // REST
        binder.bind(DataseerController.class);
    }

    @Provides
    protected ObjectMapper getObjectMapper() {
        return getEnvironment().getObjectMapper();
    }

    @Provides
    protected MetricRegistry provideMetricRegistry() {
        return getMetricRegistry();
    }

    //for unit tests
    protected MetricRegistry getMetricRegistry() {
        return getEnvironment().metrics();
    }

    @Provides
    Client provideClient() {
        return ClientBuilder.newClient();
    }

}