package org.grobid.core.data;

import org.grobid.core.engines.label.TaggingLabel;
import org.grobid.core.utilities.TextUtilities;
import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.lexicon.DatastetLexicon;
import org.grobid.core.layout.BoundingBox;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.data.Dataset.DatasetType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Representation of a mention of a component corresponding to a dataset description.
 *
 */
public class DatasetComponent extends KnowledgeEntity implements Comparable<DatasetComponent> {   
    private static final Logger logger = LoggerFactory.getLogger(DatasetComponent.class);

    // surface form of the component as it appears in the source document
    protected String rawForm = null;
    
    // list of layout tokens corresponding to the component mention in the source document
    protected List<LayoutToken> tokens = null;
    
    // normalized form of the component
    protected String normalizedForm = null;
    
    // relative offset positions in context, if defined and expressed as (Java) character offset
    protected OffsetPosition offsets = null;
    
    // confidence score of the component in context, if defined
    protected double conf = 0.8;
    
    // optional bounding box in the source document
    protected List<BoundingBox> boundingBoxes = null;
    
    // language
    protected String lang = null;

    // tagging label of the LayoutToken cluster corresponding to the component
    protected TaggingLabel label = null;

    // a status flag indicating that the component was filtered 
    protected boolean filtered = false;

    // type of dataset object
    protected DatasetType type = null;

    // in case we have a URL/URI value, this is the string target location
    protected String destination = null;

    // predicted data type related to the component
    protected double bestDataTypeScore = 0.0;
    protected String bestDataType = null;
    protected double hasDatasetScore = 0.0;

    public DatasetComponent() {
        this.offsets = new OffsetPosition();
    }
    
    public DatasetComponent(String rawForm) {
        this.rawForm = rawForm;
        this.normalizedForm = normalizeRawForm(rawForm);
        this.offsets = new OffsetPosition();
    }

    public DatasetComponent(DatasetType type, String rawForm) {
        this.rawForm = rawForm;
        this.normalizedForm = normalizeRawForm(rawForm);
        this.offsets = new OffsetPosition();
        this.type = type;
    }

    /**
     * This is a deep copy of a component, excluding layout tokens, offset and bounding boxes information.
     * The usage is for propagation of the component information to entities in other position.
     */
    public DatasetComponent(DatasetComponent ent) {
        this.rawForm = ent.rawForm;
        this.normalizedForm = ent.normalizedForm;
        this.conf = ent.conf;
        this.lang = ent.lang;
        this.label = ent.label;
        this.filtered = ent.filtered;
        this.type = ent.type;
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
        return offsets;
    }
    
    public void setOffsets(OffsetPosition offsets) {
        this.offsets = offsets;
    }
    
    public void setOffsetStart(int start) {
        offsets.start = start;
    }

    public int getOffsetStart() {
        return offsets.start;
    }

    public void setOffsetEnd(int end) {
        offsets.end = end;
    }

    public int getOffsetEnd() {
        return offsets.end;
    }
    
    public double getConf() {
        return this.conf;
    }
    
    public void setConf(double conf) {
        this.conf = conf;
    }
    
    public List<BoundingBox> getBoundingBoxes() {
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
    }

    public String getLang() {
        return this.lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public void normalize() {
        // TBD is necessary
    }

    public boolean isFiltered() {
        return filtered;
    }

    public void setFiltered(boolean filtered) {
        this.filtered = filtered;
    } 
    
    public DatasetType getType() {
        return this.type;
    }

    public void setType(DatasetType type) {
        this.type = type;
    }

    public String getBestDataType() {
        return this.bestDataType;
    }

    public void setBestDataType(String dataType) {
        this.bestDataType= dataType;
    }

    public double getBestDataTypeScore() {
        return this.bestDataTypeScore;
    }

    public void setBestDataTypeScore(double score) {
        this.bestDataTypeScore = score;
    }

    public double getHasDatasetScore() {
        return this.hasDatasetScore;
    }

    public void setHasDatasetScore(double score) {
        this.hasDatasetScore = score;
    }

    public String getDestination() {
        return this.destination;
    }

    public void setDestination(String destination) {
        this.destination= destination;
    }

    protected String bestType = null;

    @Override
    public boolean equals(Object object) {
        boolean result = false;
        if ( (object != null) && object instanceof DatasetComponent) {
            int start = ((DatasetComponent)object).getOffsetStart();
            int end = ((DatasetComponent)object).getOffsetEnd();
            if ( (start == offsets.start) && (end == offsets.end) ) {
                result = true;
            }
        }
        return result;
    }

    @Override
    public int compareTo(DatasetComponent theEntity) {
        int start = theEntity.getOffsetStart();
        int end = theEntity.getOffsetEnd();
        
        if (offsets.start != start) 
            return offsets.start - start;
        else 
            return offsets.end - end;
    }
    
    public String toJson() {
        ObjectMapper mapper = new ObjectMapper();
        
        StringBuffer buffer = new StringBuffer();
        buffer.append("{ ");
        try {
            buffer.append("\"rawForm\" : " + mapper.writeValueAsString(rawForm));
        } catch (JsonProcessingException e) {
            buffer.append("\"rawForm\" : \"" + "JsonProcessingException" + "\"");
        }
        if (normalizedForm != null) {
            try {
                buffer.append(", \"normalizedForm\" : " + mapper.writeValueAsString(normalizedForm));
            } catch (JsonProcessingException e) {
                logger.warn("could not serialize in JSON the normalized form: " + normalizedForm);
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

        if (offsets != null) {
            buffer.append(", \"offsetStart\" : " + offsets.start);
            buffer.append(", \"offsetEnd\" : " + offsets.end);  
        }
        
        if (bestDataType != null) { 
            buffer.append(", \"bestDataType\": \"" + bestDataType + "\"");
            buffer.append(", \"bestTypeScore\": " + TextUtilities.formatFourDecimals(bestDataTypeScore));
            buffer.append(", \"hasDataset\": " + hasDatasetScore);
        }

        //buffer.append(", \"conf\" : \"" + conf + "\"");
        
        if ( (boundingBoxes != null) && (boundingBoxes.size() > 0) ) {
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
        }
        
        buffer.append(" }");
        return buffer.toString();
    }
    
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        if (rawForm != null) {
            buffer.append(rawForm + "\t");
        }
        if (normalizedForm != null) {
            buffer.append(normalizedForm + "\t");
        }
        //if (type != null) {
        //  buffer.append(type + "\t"); 
        //}
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
    }

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
    
}
