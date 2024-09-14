package org.grobid.core.data;

public class DataseerResults {
    Double bestScore;
    Double hasDatasetScore;
    String bestType;

    public DataseerResults(Double bestScore, Double hasDatasetScore, String bestType) {
        this.bestScore = bestScore;
        this.hasDatasetScore = hasDatasetScore;
        this.bestType = bestType;
    }

    public Double getBestScore() {
        return bestScore;
    }

    public void setBestScore(Double bestScore) {
        this.bestScore = bestScore;
    }

    public Double getHasDatasetScore() {
        return hasDatasetScore;
    }

    public void setHasDatasetScore(Double hasDatasetScore) {
        this.hasDatasetScore = hasDatasetScore;
    }

    public String getBestType() {
        return bestType;
    }

    public void setBestType(String bestType) {
        this.bestType = bestType;
    }
}
