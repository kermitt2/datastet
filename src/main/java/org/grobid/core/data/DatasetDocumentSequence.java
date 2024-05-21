package org.grobid.core.data;

import org.apache.commons.lang3.tuple.Triple;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.utilities.OffsetPosition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class represent a block in the document, which contains a sentence text,
 * sentence Layouttoken list and their id
 */

public class DatasetDocumentSequence {
    private String text;
    private List<LayoutToken> tokens;

    // The sentence identifier is loaded here, we need them so that each mention can be easily located in the
    // document's TEI
    private String id;

    private boolean relevantSectionsNamedDatasets = false;
    private boolean relevantSectionsImplicitDatasets = false;

    // The references callout are loaded here, so that we can recover the position in the text
    // we need target, text value, and position (character related)
    Map<String, Triple<OffsetPosition, String, String>> references = new HashMap<>();

    public DatasetDocumentSequence(List<LayoutToken> layoutTokens) {
        this.tokens = layoutTokens;
    }

    public DatasetDocumentSequence(DatasetDocumentSequence block) {
        this(block.getText(), block.getTokens(), block.getId());
    }

    public DatasetDocumentSequence(String text, List<LayoutToken> tokens, String id) {
        this(text, tokens);
        this.id = id;
    }

    public DatasetDocumentSequence(String text, List<LayoutToken> tokens) {
        this.text = text;
        this.tokens = tokens;
    }

    public DatasetDocumentSequence(String text, String id) {
        this.text = text;
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<LayoutToken> getTokens() {
        return tokens;
    }

    public void setTokens(List<LayoutToken> tokens) {
        this.tokens = tokens;
    }

    public boolean isRelevantSectionsNamedDatasets() {
        return relevantSectionsNamedDatasets;
    }

    public void setRelevantSectionsNamedDatasets(boolean relevantSectionsNamedDatasets) {
        this.relevantSectionsNamedDatasets = relevantSectionsNamedDatasets;
    }

    public boolean isRelevantSectionsImplicitDatasets() {
        return relevantSectionsImplicitDatasets;
    }

    public void setRelevantSectionsImplicitDatasets(boolean relevantSectionsImplicitDatasets) {
        this.relevantSectionsImplicitDatasets = relevantSectionsImplicitDatasets;
    }

    public Map<String, Triple<OffsetPosition, String, String>> getReferences() {
        return references;
    }

    public void setReferences(Map<String, Triple<OffsetPosition, String, String>> references) {
        this.references = references;
    }
}
