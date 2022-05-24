package org.grobid.core.utilities;

import org.grobid.core.utilities.GrobidConfig.ModelParameters;
import java.util.*;

public class DataseerConfiguration {

    public String corpusPath;
    public String templatePath;
    public String grobidHome;
    public String tmpPath;
    public String pub2teiPath;
    public String gluttonHost;
    public String gluttonPort;

    //models (sequence labeling and text classifiers)
    public List<ModelParameters> models;

    public String getCorpusPath() {
        return this.corpusPath;
    }

    public void setCorpusPath(String corpusPath) {
        this.corpusPath = corpusPath;
    }

    public String getTemplatePath() {
        return this.templatePath;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }

    public String getTmpPath() {
        return this.tmpPath;
    }

    public void setTmpPath(String tmpPath) {
        this.tmpPath = tmpPath;
    }

    public String getPub2TEIPath() {
        return this.pub2teiPath;
    }

    public void setPub2teiPath(String pub2teiPath) {
        this.pub2teiPath = pub2teiPath;
    }

    public String getGrobidHome() {
        return this.grobidHome;
    }

    public void setGrobidHome(String grobidHome) {
        this.grobidHome = grobidHome;
    }

    public List<ModelParameters> getModels() {
        return models;
    }

    public ModelParameters getModel() {
        // by default return the software mention sequence labeling model
        return getModel("dataseer");
    }

    public ModelParameters getModel(String modelName) {
        for(ModelParameters parameters : models) {
            if (parameters.name.equals(modelName)) {
                return parameters;
            }
        }
        return null;
    }

    public void setModels(List<ModelParameters> models) {
        this.models = models;
    }

    public String getGluttonHost() {
        return this.gluttonHost;
    }

    public void setGluttonHost(String host) {
        this.gluttonHost = host;
    }

    public String getGluttonPort() {
        return this.gluttonPort;
    }

    public void setGluttonPort(String port) {
        this.gluttonPort = port;
    }
}
 