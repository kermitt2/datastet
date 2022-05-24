package org.grobid.core.features;

import org.grobid.core.layout.LayoutToken;
import org.grobid.core.utilities.TextUtilities;

import java.util.*;

/**
 * Class for features used for dataseer segment selections
 *
 * @author Patrice Lopez
 */
public class FeaturesVectorDataseer {
    public List<LayoutToken> tokens = null; // not a feature, reference value
    
    public String string = null; // first lexical feature
    public String secondString = null; // second lexical feature
    public String thirdString = null; // second lexical feature
    public String label = null; // label if known
    
    public String sectionType = null; // header or paragraph or list
    public boolean has_dataset; // if the segment has been predicted as having a dataset by the classifier
    public int nbDataset = 0; // number of predicted datasets in the segment, discretised
    public String datasetType = null; // most frequent dataset present in the segment

    //public String capitalisation = null; // one of INITCAP, ALLCAPS, NOCAPS
    public String digit;  // one of ALLDIGIT, CONTAINDIGIT, NODIGIT
    //public boolean singleChar = false;
    //public String punctType = null; // one of NOPUNCT, OPENBRACKET, ENDBRACKET, DOT, COMMA, HYPHEN, QUOTE, PUNCT (default)
    public int relativeDocumentPosition = -1; // discretized 
    
    //public String punctuationProfile = null; // the punctuations of the current line of the token
    
    public int segmentLength = 0; // discretized 
    public int characterDensity = 0; // discretized 

    public boolean materialsAndMethodPattern = false; // true if match a generic "materials and method" header

    public String printVector() {
        if (string == null) return null;
        if (string.length() == 0) return null;
        StringBuffer res = new StringBuffer();

        // token string (0)
        res.append(string);
        
        // second token string (1)
        if (secondString != null)
            res.append(" " + secondString);
        else
            res.append(" " + string);

        // third token string (2)
        if (thirdString != null) 
            res.append(" " + thirdString);
        else
            res.append(" " + string);
        
        // lowercase string (3)
        res.append(" " + string.toLowerCase());

        // prefix (4)
        /*res.append(" " + TextUtilities.prefix(string, 1));
        res.append(" " + TextUtilities.prefix(string, 2));
        res.append(" " + TextUtilities.prefix(string, 3));
        res.append(" " + TextUtilities.prefix(string, 4));*/

        if (sectionType != null)
            res.append(" " + sectionType);
        else
            res.append(" p");

        if (has_dataset)
            res.append(" 1");
        else
            res.append(" 0");

        res.append(" " + nbDataset);

        if (datasetType != null)
            res.append(" " + datasetType);
        else
            res.append(" no_dataset");

        // capitalisation (1)
        /*if (digit.equals("ALLDIGIT"))
            res.append(" NOCAPS");
        else
            res.append(" " + capitalisation);*/

        // digit information (1)
        //res.append(" " + digit);

        // character information (1)
        /*if (singleChar)
            res.append(" 1");
        else
            res.append(" 0");*/

        // punctuation information (1)
        /*if (punctType != null)
            res.append(" " + punctType); // in case the token is a punctuation (NO otherwise)*/

        // relative document position (1)
        res.append(" " + relativeDocumentPosition);
        
        // punctuation profile
        /*if ( (punctuationProfile == null) || (punctuationProfile.length() == 0) ) {
            // string profile
            res.append(" no");
            // number of punctuation symbols in the line
            res.append(" 0");
        }
        else {
            // string profile
            res.append(" " + punctuationProfile);
            // number of punctuation symbols in the line
            res.append(" "+punctuationProfile.length());
        }*/

        // current segment length on a predefined scale and relative to the longest segment
        res.append(" " + segmentLength);

        // materials and methods pattern
        if (materialsAndMethodPattern)
            res.append(" 1");
        else
            res.append(" 0");

        res.append("\n");

        return res.toString();
    }

}