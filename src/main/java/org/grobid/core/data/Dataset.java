package org.grobid.core.data;

import org.grobid.core.engines.label.TaggingLabel;
import org.grobid.core.utilities.TextUtilities;
import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.lexicon.DataseerLexicon;
import org.grobid.core.layout.BoundingBox;
import org.grobid.core.layout.LayoutToken;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.io.JsonStringEncoder;

import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Representation of a mention of a dataset, name or implicit expression, or data device.  
 *
 */
public class Dataset extends KnowledgeEntity {   
    private static final Logger logger = LoggerFactory.getLogger(Dataset.class);
    
    // Orign of the component definition
    public enum DatasetType {
        DATASET_NAME  ("dataset-name"),
        DATASET    ("dataset"),
        DATA_DEVICE ("data-device");
        
        private String name;

        private DatasetType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    };

    protected DatasetComponent datasetName = null;
    protected DatasetComponent dataset = null;
    protected DatasetComponent dataDevice = null;
    protected DatasetComponent url = null;
    protected DatasetComponent publisher = null;

    // one or several bibliographical references attached to the dataset entity
    private List<BiblioComponent> bibRefs = null;

    // type of dataset object
    protected DatasetType type = null;

    // surface form of the component as it appears in the source document
    protected String rawForm = null;
    
    // list of layout tokens corresponding to the component mention in the source document
    //protected List<LayoutToken> tokens = null;
    
    // normalized form of the component
    protected String normalizedForm = null;
    
    // relative offset positions in context, if defined and expressed as (Java) character offset
    //protected OffsetPosition offsets = null;
    
    // confidence score of the component in context, if defined
    protected double conf = 0.8;
    
    // optional bounding box in the source document
    //protected List<BoundingBox> boundingBoxes = null;
    
    // language
    protected String lang = null;

    // tagging label of the LayoutToken cluster corresponding to the component
    //protected TaggingLabel label = null;

    // a status flag indicating that the component was filtered 
    protected boolean filtered = false;

    // features of the mention context relatively to the referenced dataset: 
    // 1) dataset usage by the research work disclosed in the document: used
    // 2) dataset creation of the research work disclosed in the document (creation, extension, etc.): contribution
    // 3) dataset is shared
    private Boolean used = null;
    private Double usedScore = null;
    private Boolean created = null;
    private Double createdScore = null;
    private Boolean shared = null;
    private Double sharedScore = null;

    // characteristics of the mention context relatively to the referenced dataset for the single local mention
    private DatasetContextAttributes mentionContextAttributes = null;

    // characteristics of the mention contexts relatively to the referenced dataset considering all mentions in a document
    private DatasetContextAttributes documentContextAttributes = null;

    // the text context where the entity takes place - typically a snippet with the 
    // sentence including the mention
    private String context = null;
    
    // offset of the context with respect of the paragraph 
    private int paragraphContextOffset = -1;

    // offset of the context with rspect to the complete content
    private int globalContextOffset = -1;

    // full paragraph context where the entity takes place, this is an optional field
    // relevant for certain scenarios only
    private String paragraph = null;

    public Dataset(DatasetType type) {
        this.type = type;
    }

    public Dataset(DatasetType type, String rawForm) {
        this.type = type;
        this.rawForm = rawForm;
        this.normalizedForm = normalizeRawForm(rawForm);
    }

    public DatasetType getType() {
        return this.type;
    }

    public void setType(DatasetType type) {
        this.type = type;
    }

    public String getRawForm() {
        return rawForm;
    }
    
    public void setRawForm(String raw) {
        this.rawForm = raw;
        this.normalizedForm = normalizeRawForm(raw);
    }

    public String getNormalizedForm() {
        return normalizedForm;
    }
    
    public void setNormalizedForm(String normalized) {
        this.normalizedForm = normalizeRawForm(normalized);
    }

    public OffsetPosition getOffsets() {
        if (datasetName != null)
            return datasetName.getOffsets();
        else if (dataset != null)
            return dataset.getOffsets();
        return null;
    }
    
    public int getOffsetStart() {
        if (datasetName != null)
            return datasetName.getOffsetStart();
        else if (dataset != null)
            return dataset.getOffsetStart();
        return -1;
    }

    public int getOffsetEnd() {
        if (datasetName != null)
            return datasetName.getOffsetEnd();
        else if (dataset != null)
            return dataset.getOffsetEnd();
        return -1;
    }
    
    public double getConf() {
        return this.conf;
    }
    
    public void setConf(double conf) {
        this.conf = conf;
    }
    
    /*public List<BoundingBox> getBoundingBoxes() {
        return boundingBoxes;
    }

    public void setBoundingBoxes(List<BoundingBox> boundingBoxes) {
        this.boundingBoxes = boundingBoxes;
    }
    
    public List<LayoutToken> getTokens() {
        return this.tokens;
    }
    
    public void setTokens(List<LayoutToken> tokens) {
        this.tokens = tokens;
    }
    
    public TaggingLabel getLabel() {
        return label;
    }

    public void setLabel(TaggingLabel label) {
        this.label = label;
    }*/

    public String getLang() {
        return this.lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public boolean isFiltered() {
        return filtered;
    }

    public void setFiltered(boolean filtered) {
        this.filtered = filtered;
    } 

    public void setContext(String context) {
        this.context = context;
    }

    public String getContext() {
        return this.context;
    }

    public void setParagraphContextOffset(int paragraphContextOffset) {
        this.paragraphContextOffset = paragraphContextOffset;
    }

    public int getParagraphContextOffset() {
        return this.paragraphContextOffset;
    }

    public void setGlobalContextOffset(int globalContextOffset) {
        this.globalContextOffset = globalContextOffset;
    }

    public int getGlobalContextOffset() {
        return this.globalContextOffset;
    }

    public void setParagraph(String paragraph) {
        this.paragraph = paragraph;
    }

    public String getParagraph() {
        return this.paragraph;
    }
    
    public List<BiblioComponent> getBibRefs() {
        return this.bibRefs;
    }

    public void setBibRefs(List<BiblioComponent> bibRefs) {
        this.bibRefs = bibRefs;
    }

    public void addBibRef(BiblioComponent bibRef) {
        if (bibRefs == null) {
            bibRefs = new ArrayList<BiblioComponent>();
        }
        bibRefs.add(bibRef);
    }

    public DatasetComponent getDatasetName() {
        return this.datasetName;
    }

    public void setDatasetName(DatasetComponent datasetName) {
        this.datasetName = datasetName;
    }

    public DatasetComponent getDataset() {
        return this.dataset;
    }

    public void setDataset(DatasetComponent dataset) {
        this.dataset = dataset;
    }

    public DatasetComponent getDataDevice() {
        return this.dataDevice;
    }

    public void setDataDevice(DatasetComponent dataDevice) {
        this.dataDevice = dataDevice;
    }

    public DatasetComponent getUrl() {
        return this.url;
    }

    public void setUrl(DatasetComponent url) {
        this.url = url;
    }

    public DatasetComponent getPublisher() {
        return this.publisher;
    }

    public void setPublisher(DatasetComponent publisher) {
        this.publisher = publisher;
    }

    /*
    @Override
    public boolean equals(Object object) {
        boolean result = false;
        if ( (object != null) && object instanceof Dataset) {
            int start = ((Dataset)object).getOffsetStart();
            int end = ((Dataset)object).getOffsetEnd();
            if ( (start == offsets.start) && (end == offsets.end) ) {
                result = true;
            }
        }
        return result;
    }

    @Override
    public int compareTo(Dataset theEntity) {
        int start = theEntity.getOffsetStart();
        int end = theEntity.getOffsetEnd();
        
        if (offsets.start != start) 
            return offsets.start - start;
        else 
            return offsets.end - end;
    }*/
    
    public String toJson() {
        ObjectMapper mapper = new ObjectMapper();
        JsonStringEncoder encoder = JsonStringEncoder.getInstance();
        byte[] encoded = null;
        String output;

        StringBuffer buffer = new StringBuffer();
        buffer.append("{ ");

        encoded = encoder.quoteAsUTF8(rawForm);
        output = new String(encoded);
        try {
            buffer.append("\"rawForm\" : " + mapper.writeValueAsString(output));
        } catch (JsonProcessingException e) {
            logger.warn("could not serialize in JSON the normalized form: " + type.getName());
        }

        if (type != null) {
            try {
                buffer.append(", \"type\" : " + mapper.writeValueAsString(type.getName()));
            } catch (JsonProcessingException e) {
                logger.warn("could not serialize in JSON the normalized form: " + type.getName());
            }
        }

        if (datasetName != null) {
            buffer.append(", \"dataset-name\":" + datasetName.toJson());
        }

        if (dataset != null) {
            buffer.append(", \"dataset\":" + dataset.toJson());
        }

        if (dataDevice != null) {
            buffer.append(", \"data-device\":" + dataDevice.toJson());
        }

        if (url != null) {
            buffer.append(", \"url\":" + url.toJson());
        }

        if (publisher != null) {
            buffer.append(", \"publisher\":" + url.toJson());
        }

        if (normalizedForm != null) {
            encoded = encoder.quoteAsUTF8(normalizedForm);
            output = new String(encoded);
            try{
                buffer.append(", \"normalizedForm\" : " + mapper.writeValueAsString(output));
            } catch (JsonProcessingException e) {
                logger.warn("could not serialize in JSON the normalized form: " + type.getName());
            }
        }

        // knowledge information
        if (wikidataId != null) {
            buffer.append(", \"wikidataId\": \"" + wikidataId + "\"");
        }
        if (wikipediaExternalRef != -1) {
            buffer.append(", \"wikipediaExternalRef\": " + wikipediaExternalRef);
        }

        if (lang != null) {
            buffer.append(", \"lang\": \"" + lang + "\"");
        }
        if (disambiguationScore != null) {
            buffer.append(", \"confidence\": " + TextUtilities.formatFourDecimals(disambiguationScore.doubleValue()));
        }

        /*if (offsets != null) {
            buffer.append(", \"offsetStart\" : " + offsets.start);
            buffer.append(", \"offsetEnd\" : " + offsets.end);  
        }*/

        if (context != null && context.length()>0) {
            encoded = encoder.quoteAsUTF8(context.replace("\n", " ").replace("  ", " "));
            output = new String(encoded);
            try {
                buffer.append(", \"context\" : " + mapper.writeValueAsString(output));
            } catch (JsonProcessingException e) {
                logger.warn("could not serialize in JSON the normalized form: " + type.getName());
            }
            /*try {
                buffer.append(", \"context\" : \"" + mapper.writeValueAsString(context.replace("\n", " ").replace("  ", " ")) + "\"");
            } catch (JsonProcessingException e) {
                logger.warn("could not serialize in JSON the context: " + context);
            }*/
        }

        if (paragraph != null && paragraph.length()>0) {
            if (paragraphContextOffset != -1) {
                buffer.append(", \"contextOffset\": " + paragraphContextOffset);
            }

            encoded = encoder.quoteAsUTF8(paragraph.replace("\n", " ").replace("  ", " "));
            output = new String(encoded);
            try{
                buffer.append(", \"paragraph\": \"" + mapper.writeValueAsString(output) + "\"");
            } catch (JsonProcessingException e) {
                logger.warn("could not serialize in JSON the normalized form: " + type.getName());
            }
            /*try {
                buffer.append(", \"paragraph\": \"" + mapper.writeValueAsString(paragraph.replace("\n", " ").replace("  ", " ")) + "\"");
            } catch (JsonProcessingException e) {
                logger.warn("could not serialize in JSON the paragraph context: " + paragraph);
            }*/
        }

        //buffer.append(", \"conf\" : \"" + conf + "\"");
        
        /*if ( (boundingBoxes != null) && (boundingBoxes.size() > 0) ) {
            buffer.append(", \"boundingBoxes\" : [");
            boolean first = true;
            for (BoundingBox box : boundingBoxes) {
                if (first)
                    first = false;
                else
                    buffer.append(",");
                buffer.append("{").append(box.toJson()).append("}");
            }
            buffer.append("] ");
        }*/
        
        if (bibRefs != null) {
            buffer.append(", \"references\": ["); 
            boolean first = true;
            for(BiblioComponent bibRef : bibRefs) {
                if (bibRef.getBiblio() == null)
                    continue;
                if (!first)
                    buffer.append(", ");
                else
                    first = false;
                buffer.append(bibRef.toJson());
            }

            buffer.append(" ] ");
        }

        buffer.append(" }");
        return buffer.toString();
    }
    
    /*public String toString() {
        StringBuffer buffer = new StringBuffer();
        if (rawForm != null) {
            buffer.append(rawForm + "\t");
        }
        if (normalizedForm != null) {
            buffer.append(normalizedForm + "\t");
        }
        if (type != null) {
            buffer.append(type + "\t"); 
        }
        //if (entityId != null)
        //  buffer.append(entityId + "\t"); 

        if (offsets != null) {
            buffer.append(offsets.toString() + "\t");
        }

        if ( (boundingBoxes != null) && (boundingBoxes.size()>0) ) {
            for(BoundingBox box : boundingBoxes) {
                buffer.append(box.toString() + "\t");
            }
        }

        return buffer.toString();
    }*/

    /**
     * This is a string normalization process adapted to the dataset 
     * attribute strings
     */
    private static String normalizeRawForm(String raw) {
        if (raw == null)
            return null;
        String result = raw.replace("\n", " ");
        result = result.replaceAll("( )+", " ");
        result = TextUtilities.cleanField(result, false);
        return result;
    }
    
    public void mergeDocumentContextAttributes(DatasetContextAttributes attributes) {
        if (this.documentContextAttributes == null)
            this.documentContextAttributes = attributes;

        if (this.documentContextAttributes.getUsed() == null || !this.documentContextAttributes.getUsed()) {
            this.documentContextAttributes.setUsed(attributes.getUsed());
        }

        if (this.documentContextAttributes.getUsedScore() != null) {
            if (attributes.getUsedScore() > this.documentContextAttributes.getUsedScore()) 
                this.documentContextAttributes.setUsedScore(attributes.getUsedScore());
        } else
            this.documentContextAttributes.setUsedScore(attributes.getUsedScore());

        if (this.documentContextAttributes.getCreated() == null || !this.documentContextAttributes.getCreated()) {
            this.documentContextAttributes.setCreated(attributes.getCreated());
        }

        if (this.documentContextAttributes.getCreatedScore() != null) {
            if (attributes.getCreatedScore() > this.documentContextAttributes.getCreatedScore()) 
                this.documentContextAttributes.setCreatedScore(attributes.getCreatedScore());
        } else
            this.documentContextAttributes.setCreatedScore(attributes.getCreatedScore());

        if (this.documentContextAttributes.getShared() == null || !this.documentContextAttributes.getShared()) {
            this.documentContextAttributes.setShared(attributes.getShared());
        }

        if (this.documentContextAttributes.getSharedScore() != null) {
            if (attributes.getSharedScore() > this.documentContextAttributes.getSharedScore()) 
                this.documentContextAttributes.setSharedScore(attributes.getSharedScore());
        } else
            this.documentContextAttributes.setSharedScore(attributes.getSharedScore());
    }

    /**
     * Assuming that dataset names are identical, this method merges the attributes
     * of the two entities.    
     */
    public static void merge(Dataset entity1, Dataset entity2) {

        if (entity1.getPublisher() == null)
            entity1.setPublisher(entity2.getPublisher());
        else if (entity2.getPublisher() == null)
            entity2.setPublisher(entity1.getPublisher());

        if (entity1.getUrl() == null)
            entity1.setUrl(entity2.getUrl());
        else if (entity2.getUrl() == null)
            entity2.setUrl(entity1.getUrl());

        if (entity1.getBibRefs() == null)
            entity1.setBibRefs(entity2.getBibRefs());
        else if (entity2.getBibRefs() == null)
            entity2.setBibRefs(entity1.getBibRefs());
    }

    /**
     * Assuming that dataset names are identical, this method merges the attributes
     * of the two entities with a copy of the added attribute component.    
     */
    public static void mergeWithCopy(Dataset entity1, Dataset entity2) {

        if (entity1.getPublisher() == null && entity2.getPublisher() != null)
            entity1.setPublisher(new DatasetComponent(entity2.getPublisher()));
        else if (entity2.getPublisher() == null && entity1.getPublisher() != null)
            entity2.setPublisher(new DatasetComponent(entity1.getPublisher()));

        if (entity1.getUrl() == null && entity2.getUrl() != null)
            entity1.setUrl(new DatasetComponent(entity2.getUrl()));
        else if (entity2.getUrl() == null && entity1.getUrl() != null)
            entity2.setUrl(new DatasetComponent(entity1.getUrl()));

        if (entity1.getBibRefs() == null && entity2.getBibRefs() != null) {
            List<BiblioComponent> newBibRefs = new ArrayList<>();
            for(BiblioComponent bibComponent : entity2.getBibRefs()) {
                newBibRefs.add(new BiblioComponent(bibComponent));
            }
            if (newBibRefs.size() > 0)
                entity1.setBibRefs(newBibRefs);
        }
        else if (entity2.getBibRefs() == null && entity1.getBibRefs() != null) {
            List<BiblioComponent> newBibRefs = new ArrayList<>();
            for(BiblioComponent bibComponent : entity1.getBibRefs()) {
                newBibRefs.add(new BiblioComponent(bibComponent));
            }
            if (newBibRefs.size() > 0)
                entity2.setBibRefs(newBibRefs);
        }
    }

 }
