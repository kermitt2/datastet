package org.grobid.service;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Binder;
import com.google.inject.Provides;
import com.hubspot.dropwizard.guicier.DropwizardAwareModule;
import org.grobid.service.configuration.DatastetServiceConfiguration;
import org.grobid.service.controller.DatastetController;
import org.grobid.service.controller.HealthCheck;
import org.grobid.service.controller.DatastetProcessFile;
import org.grobid.service.controller.DatastetProcessString;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;


public class DatastetServiceModule extends DropwizardAwareModule<DatastetServiceConfiguration> {

    @Override
    public void configure(Binder binder) {
        // Generic modules
        binder.bind(GrobidEngineInitialiser.class);
        binder.bind(HealthCheck.class);

        // Core components
        binder.bind(DatastetProcessFile.class);
        binder.bind(DatastetProcessString.class);

        // REST
        binder.bind(DatastetController.class);
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