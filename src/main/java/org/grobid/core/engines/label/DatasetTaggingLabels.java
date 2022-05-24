package org.grobid.core.engines.label;

import org.grobid.core.engines.QuantitiesModels;

public class DatasetTaggingLabels extends TaggingLabels {
    private DatasetTaggingLabels() {
        super();
    }

    private static final String DATASET_LABEL = "<dataset>";
    private static final String DATASET_NAME_LABEL = "<dataset_name>";
    private static final String DATA_DEVICE_LABEL = "<data_device>";

    public static final TaggingLabel DATASET = new TaggingLabelImpl(DatasetTaggingLabels.QUANTITIES, DATASET_LABEL);
    public static final TaggingLabel DATASET_NAME = new TaggingLabelImpl(DatasetTaggingLabels.QUANTITIES, DATASET_NAME_LABEL);
    public static final TaggingLabel DATA_DEVICE = new TaggingLabelImpl(DatasetTaggingLabels.QUANTITIES, DATA_DEVICE_LABEL);

    static {
        register(DATASET);
        register(DATASET_NAME);
        register(DATA_DEVICE);
    }
}
