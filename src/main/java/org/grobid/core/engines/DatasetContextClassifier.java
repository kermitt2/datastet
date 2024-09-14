package org.grobid.core.engines;

import java.util.*;

import org.apache.commons.io.FileUtils;
import org.grobid.core.GrobidModels;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.LayoutTokenization;
import org.grobid.core.utilities.*;
import org.grobid.core.jni.PythonEnvironmentConfig;
import org.grobid.core.jni.DeLFTClassifierModel;
import org.grobid.core.utilities.GrobidConfig.ModelParameters;
import org.grobid.core.utilities.TextUtilities;
import org.grobid.core.data.Dataset;
import org.grobid.core.data.DatasetContextAttributes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.io.*;

import static org.apache.commons.lang3.ArrayUtils.isEmpty;

/**
 * Use a Deep Learning multiclass and multilabel classifier to characterize the context of a recognized dataset mention. 
 * This classifier predicts if the dataset introduced by a dataset mention in a sentence is likely:
 * - used or not by the described work (class used)
 * - a contribution of the described work (class contribution)
 * - shared (class shared)
 *
 * The prediction uses the sentence where the mention appears (sentence is context here).
 * Then given n mentions of the same dataset in a document, we have n predictions and we can derived from this
 * the nature of the dataset mention at document level. 
 */
public class DatasetContextClassifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatasetContextClassifier.class);

    // we can use either one single multi-label (over 3 classes) classifier or 3 binary classifiers

    // multi-class/multi-label classifier
    private DeLFTClassifierModel classifier = null;

    // binary classifiers
    private DeLFTClassifierModel classifierBinaryUsed = null;
    private DeLFTClassifierModel classifierBinaryCreated = null;
    private DeLFTClassifierModel classifierBinaryShared = null;

    private Boolean useBinary; 

    private DatastetConfiguration datastetConfiguration;
    private JsonParser parser;

    private static volatile DatasetContextClassifier instance;

    public static DatasetContextClassifier getInstance(DatastetConfiguration configuration) {
        if (instance == null) {
            getNewInstance(configuration);
        }
        return instance;
    }

    public enum MODEL_TYPE {
        used("used"),
        created("created"),
        shared("shared"),
        all("all");

        private final String text;

        MODEL_TYPE(final String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /**
     * Create a new instance.
     */
    private static synchronized void getNewInstance(DatastetConfiguration configuration) {
        instance = new DatasetContextClassifier(configuration);
    }

    private DatasetContextClassifier(DatastetConfiguration configuration) {
        ModelParameters parameter = configuration.getModel("context");

        ModelParameters parameterUsed = configuration.getModel("context_used");
        ModelParameters parameterCreated = configuration.getModel("context_creation");
        ModelParameters parameterShared = configuration.getModel("context_shared");

        this.useBinary = configuration.getUseBinaryContextClassifiers();
        if (this.useBinary == null)
            this.useBinary = true;

        if (this.useBinary) {
            this.classifierBinaryUsed = new DeLFTClassifierModel("context_used", parameterUsed.delft.architecture);
            this.classifierBinaryCreated = new DeLFTClassifierModel("context_creation", parameterCreated.delft.architecture);
            this.classifierBinaryShared = new DeLFTClassifierModel("context_shared", parameterShared.delft.architecture);
        } else {
            this.classifier = new DeLFTClassifierModel("context", parameter.delft.architecture);
        }
    }

    /**
     * Classify a simple piece of text
     * @return list of predicted labels/scores pairs
     */
    public String classify(String text, MODEL_TYPE type) throws Exception {
        if (StringUtils.isEmpty(text))
            return null;
        List<String> texts = new ArrayList<String>();
        texts.add(text);
        return classify(texts, type);
    }

    /**
     * Classify an array of texts
     * @return list of predicted labels/scores pairs for each text
     */
    public String classify(List<String> texts, MODEL_TYPE type) throws Exception {
        if (texts == null || texts.size() == 0)
            return null;

        LOGGER.info("classify: " + texts.size() + " sentence(s) for type " + type.toString());

        String the_json = null;

        if (type == MODEL_TYPE.used)
            the_json = this.classifierBinaryUsed.classify(texts);
        else if (type == MODEL_TYPE.created)
            the_json = this.classifierBinaryCreated.classify(texts);
        else if (type == MODEL_TYPE.shared)
            the_json = this.classifierBinaryShared.classify(texts);
        else
            the_json = this.classifier.classify(texts);
        //System.out.println(the_json);

        return the_json;
    }

    /**
     * Process the contexts of a set of entities identified in a document. Each context is
     * classified and a global decision is realized at document-level using all the mentioned 
     * contexts corresponding to the same dataset.  
     * 
     * This method uses one multi-class, multi-label classifier.
     * 
     **/
    public List<List<Dataset>> classifyDocumentContexts(List<List<Dataset>> entities) {

        if (this.useBinary)
            return classifyDocumentContextsBinary(entities);

        List<String> contexts = new ArrayList<>();

        for(List<Dataset> datasets : entities) {
            for(Dataset entity : datasets) {
                if (StringUtils.isNotBlank(entity.getContext())) {
                    String localContext = TextUtilities.dehyphenize(entity.getContext());
                    localContext = localContext.replace("\n", " ");
                    localContext = localContext.replaceAll("( )+", " ");
                    contexts.add(localContext);
                } else {
                    // dummy place holder
                    contexts.add("");
                }
            }
        }

        String results = null;
        try {
            results = classify(contexts, MODEL_TYPE.all);
        } catch(Exception e) {
            LOGGER.error("fail to classify document's set of contexts", e);
            return entities;
        }

        if (results == null) 
            return entities;

        // set resulting context classes to entity mentions
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(results);

            int entityRank =0;
            String lang = null;
            JsonNode classificationsNode = root.findPath("classifications");
            if ((classificationsNode != null) && (!classificationsNode.isMissingNode())) {
                Iterator<JsonNode> ite = classificationsNode.elements();
                while (ite.hasNext()) {
                    JsonNode classificationNode = ite.next();

                    JsonNode usedNode = classificationNode.findPath("used");
                    JsonNode createdNode = classificationNode.findPath("creation");
                    JsonNode sharedNode = classificationNode.findPath("shared");
                    JsonNode textNode = classificationNode.findPath("text");

                    double scoreUsed = 0.0;
                    if ((usedNode != null) && (!usedNode.isMissingNode())) {
                        scoreUsed = usedNode.doubleValue();
                    }

                    double scoreCreated = 0.0;
                    if ((createdNode != null) && (!createdNode.isMissingNode())) {
                        scoreCreated = createdNode.doubleValue();
                    }

                    double scoreShared = 0.0;
                    if ((sharedNode != null) && (!sharedNode.isMissingNode())) {
                        scoreShared = sharedNode.doubleValue();
                    }

                    String textValue = null;
                    if ((textNode != null) && (!textNode.isMissingNode())) {
                        textValue = textNode.textValue();
                    }
                    
                    DatasetContextAttributes contextAttributes = new DatasetContextAttributes();
                    contextAttributes.setUsedScore(scoreUsed);
                    contextAttributes.setCreatedScore(scoreCreated);
                    contextAttributes.setSharedScore(scoreShared);

                    if (scoreUsed>0.5) 
                        contextAttributes.setUsed(true);
                    else 
                        contextAttributes.setUsed(false);

                    if (scoreCreated > 0.5) 
                        contextAttributes.setCreated(true);
                    else 
                        contextAttributes.setCreated(false);

                    if (scoreShared > 0.5) 
                        contextAttributes.setShared(true);
                    else 
                        contextAttributes.setShared(false);
                    
                    //Dataset entity = entities.get(entityRank);
                    Dataset entity = getEntityByGlobalRank(entities, entityRank);
                    if (entity != null)
                        entity.setMentionContextAttributes(contextAttributes);

                    entityRank++;
                }
            }
        } catch(JsonProcessingException e) {
            LOGGER.error("failed to parse JSON context classification result", e);
        }

        // in a second pass, we share all predictions for mentions of the same dataset name in 
        // different places and apply a consistency propagation
        return documentPropagation(entities);
    }

    private static Dataset getEntityByGlobalRank(List<List<Dataset>> entities, int rank) {
        int currentRank = 0;
        for(List<Dataset> datasets : entities) {
            int localSize = datasets.size();
            if (currentRank + localSize > rank) {
                return datasets.get(rank - currentRank);
            } else {
                currentRank += localSize;
            }
        }
        return null;
    }

    /**
     * Process the contexts of a set of entities identified in a document. Each context is
     * classified and a global decision is realized at document-level using all the mentioned 
     * contexts corresponding to the same dataset.  
     * 
     * This method uses binary classifiers.
     * 
     **/
    public List<List<Dataset>> classifyDocumentContextsBinary(List<List<Dataset>> entities) {
        List<String> contexts = new ArrayList<>();
        for(List<Dataset> datasets : entities) {
            for(Dataset entity : datasets) {
                if (entity.getContext() != null && entity.getContext().length()>0) {
                    String localContext = TextUtilities.dehyphenize(entity.getContext());
                    localContext = localContext.replace("\n", " ");
                    localContext = localContext.replaceAll("( )+", " ");
                    contexts.add(localContext);
                } else {
                    // dummy place holder
                    contexts.add("");
                }
            }
        }

        String resultsUsed = null;
        String resultsCreated = null;
        String resultsShared = null;
        try {
            resultsUsed = classify(contexts, MODEL_TYPE.used);
            resultsCreated = classify(contexts, MODEL_TYPE.created);
            resultsShared = classify(contexts, MODEL_TYPE.shared);
        } catch(Exception e) {
            LOGGER.error("fail to classify document's set of contexts", e);
            return entities;
        }

        if (resultsUsed == null && resultsCreated == null && resultsShared == null) 
            return entities;

        List<String> results = new ArrayList<>();
        results.add(resultsUsed);
        results.add(resultsCreated);
        results.add(resultsShared);

        // set resulting context classes to entity mentions
        for(int i=0; i<results.size(); i++) {
            if (results.get(i) == null) 
                continue;
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(results.get(i));

                int entityRank =0;
                String lang = null;
                JsonNode classificationsNode = root.findPath("classifications");
                if ((classificationsNode != null) && (!classificationsNode.isMissingNode())) {
                    Iterator<JsonNode> ite = classificationsNode.elements();
                    while (ite.hasNext()) {
                        JsonNode classificationNode = ite.next();
                        //Dataset entity = entities.get(entityRank);
                        Dataset entity = getEntityByGlobalRank(entities, entityRank);

                        if (entity == null) {
                            entityRank++;
                            continue;
                        }

                        DatasetContextAttributes contextAttributes = entity.getMentionContextAttributes();
                        if (contextAttributes == null)
                            contextAttributes = new DatasetContextAttributes();
                        
                        if (i==0) {
                            JsonNode usedNode = classificationNode.findPath("used");
                            JsonNode notUsedNode = classificationNode.findPath("not_used");

                            double scoreUsed = 0.0;
                            if ((usedNode != null) && (!usedNode.isMissingNode())) {
                                scoreUsed = usedNode.doubleValue();
                            }
                            double scoreNotUsed = 0.0;
                            if ((notUsedNode != null) && (!notUsedNode.isMissingNode())) {
                                scoreNotUsed = notUsedNode.doubleValue();
                            }

                            if (scoreUsed > scoreNotUsed)
                                contextAttributes.setUsedScore(scoreUsed);
                            else 
                                contextAttributes.setUsedScore(1-scoreNotUsed);

                            if (scoreUsed>0.5 && scoreUsed > scoreNotUsed) 
                                contextAttributes.setUsed(true);
                            else 
                                contextAttributes.setUsed(false);
                        } else if (i == 1) {
                            JsonNode createdNode = classificationNode.findPath("creation");
                            JsonNode notCreatedNode = classificationNode.findPath("not_creation");

                            double scoreCreated = 0.0;
                            if ((createdNode != null) && (!createdNode.isMissingNode())) {
                                scoreCreated = createdNode.doubleValue();
                            }

                            double scoreNotCreated = 0.0;
                            if ((notCreatedNode != null) && (!notCreatedNode.isMissingNode())) {
                                scoreNotCreated = notCreatedNode.doubleValue();
                            }

                            if (scoreCreated > scoreNotCreated)
                                contextAttributes.setCreatedScore(scoreCreated);
                            else
                                contextAttributes.setCreatedScore(1 - scoreNotCreated);

                            if (scoreCreated > 0.5 && scoreCreated > scoreNotCreated) 
                                contextAttributes.setCreated(true);
                            else 
                                contextAttributes.setCreated(false);
                        } else {
                            JsonNode sharedNode = classificationNode.findPath("shared");
                            JsonNode notSharedNode = classificationNode.findPath("not_shared");

                            double scoreShared = 0.0;
                            if ((sharedNode != null) && (!sharedNode.isMissingNode())) {
                                scoreShared = sharedNode.doubleValue();
                            }

                            double scoreNotShared = 0.0;
                            if ((notSharedNode != null) && (!notSharedNode.isMissingNode())) {
                                scoreNotShared = notSharedNode.doubleValue();
                            }

                            if (scoreShared > scoreNotShared)
                                contextAttributes.setSharedScore(scoreShared);
                            else
                                contextAttributes.setSharedScore(1 - scoreNotShared);

                            if (scoreShared > 0.5 && scoreShared > scoreNotShared) 
                                contextAttributes.setShared(true);
                            else 
                                contextAttributes.setShared(false);
                        }

                        JsonNode textNode = classificationNode.findPath("text");
                        String textValue = null;
                        if ((textNode != null) && (!textNode.isMissingNode())) {
                            textValue = textNode.textValue();
                        }
                        
                        entity.setMentionContextAttributes(contextAttributes);
                        entityRank++;
                    }
                }
            } catch(JsonProcessingException e) {
                LOGGER.error("failed to parse JSON context classification result", e);
            }
        }

        // in a second pass, we share all predictions for mentions of the same dataset name in 
        // different places and apply a consistency propagation
        return documentPropagation(entities);
    }

    private List<List<Dataset>> documentPropagation(List<List<Dataset>> entities) {
        Map<String, List<Dataset>> entityMap = new TreeMap<>();
        for(List<Dataset> datasets : entities) {
            for(Dataset entity : datasets) {
                if (entity.getDatasetName() == null)
                    continue;

                if (entity.getDatasetName().getRawForm() == null || entity.getDatasetName().getRawForm().length() == 0)
                    continue;

                String datasetNameRaw = entity.getDatasetName().getRawForm();
                List<Dataset> localList = entityMap.get(datasetNameRaw);
                if (localList == null) {
                    localList = new ArrayList<>();
                } 
                localList.add(entity);
                entityMap.put(datasetNameRaw, localList);

                String datasetNameNormalized = entity.getDatasetName().getNormalizedForm();
                if (datasetNameNormalized != null && !datasetNameRaw.equals(datasetNameNormalized)) {
                    localList = entityMap.get(datasetNameNormalized);
                    if (localList == null) {
                        localList = new ArrayList<>();
                    } 
                    localList.add(entity);
                    entityMap.put(datasetNameNormalized, localList);
                }
            }
        }

        for (Map.Entry<String, List<Dataset>> entry : entityMap.entrySet()) {

            int is_used = 0;
            double best_used = 0.0;        
            int is_created = 0;
            double best_created = 0.0;
            int is_shared = 0;
            double best_shared = 0.0;
            for(Dataset entity : entry.getValue()) {
                DatasetContextAttributes localContextAttributes = entity.getMentionContextAttributes();
                if (localContextAttributes.getUsed()) 
                    is_used++;
                if (localContextAttributes.getUsedScore() > best_used)
                    best_used = localContextAttributes.getUsedScore();

                if (localContextAttributes.getCreated()) 
                    is_created++;
                if (localContextAttributes.getCreatedScore() > best_created)
                    best_created = localContextAttributes.getCreatedScore();

                if (localContextAttributes.getShared()) 
                    is_shared++;
                if (localContextAttributes.getSharedScore() > best_shared)
                    best_shared = localContextAttributes.getSharedScore();
            }

            DatasetContextAttributes globalContextAttributes = new DatasetContextAttributes();
            globalContextAttributes.init();
            if (is_used > 0)
                globalContextAttributes.setUsed(true);
            if (best_used > 0.0)
                globalContextAttributes.setUsedScore(best_used);

            if (is_created > 0) 
                globalContextAttributes.setCreated(true);
            if (best_created > 0.0)
                globalContextAttributes.setCreatedScore(best_created);

            if (is_shared > 0)
                globalContextAttributes.setShared(true);
            if (best_shared > 0.0)
                globalContextAttributes.setSharedScore(best_shared);

            for(Dataset entity : entry.getValue()) {
                entity.mergeDocumentContextAttributes(globalContextAttributes);
            }
        }

        return entities;
    }

}