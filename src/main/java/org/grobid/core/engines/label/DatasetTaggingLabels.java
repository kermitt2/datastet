package org.grobid.core.engines.label;

import org.grobid.core.engines.DatasetModels;

public class DatasetTaggingLabels extends TaggingLabels {
    private DatasetTaggingLabels() {
        super();
    }

    private static final String DATASET_LABEL = "dataset";
    private static final String DATASET_NAME_LABEL = "dataset_name";
    private static final String DATA_DEVICE_LABEL = "data_device";
    private static final String DATASET_URL_LABEL = "url";

    public static final TaggingLabel DATASET = new TaggingLabelImpl(DatasetModels.DATASET, DATASET_LABEL);
    public static final TaggingLabel DATASET_NAME = new TaggingLabelImpl(DatasetModels.DATASET, DATASET_NAME_LABEL);
    public static final TaggingLabel DATA_DEVICE = new TaggingLabelImpl(DatasetModels.DATASET, DATA_DEVICE_LABEL);
    public static final TaggingLabel DATASET_URL = new TaggingLabelImpl(DatasetModels.DATASET, DATASET_URL_LABEL);

    static {
        register(DATASET);
        register(DATASET_NAME);
        register(DATA_DEVICE);
        register(DATASET_URL);
    }
}
