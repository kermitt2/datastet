package org.grobid.core.lexicon;

import org.grobid.core.analyzers.DataseerAnalyzer;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.exceptions.GrobidResourceException;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.Pair;
import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.utilities.LayoutTokensUtil;
import org.grobid.core.utilities.Utilities;
import org.grobid.core.lexicon.FastMatcher;
import org.grobid.core.layout.LayoutToken;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Class for managing the lexical resources for dataseer
 *
 * @author Patrice
 */
public class DataseerLexicon {

    private static Logger LOGGER = LoggerFactory.getLogger(DataseerLexicon.class);

    private Set<String> Datasetocabulary = null;
    private FastMatcher DatasetPattern = null;

    private Map<String, Double> termIDF = null;

    // the list of P31 and P279 values of the Wikidata dataset entities
    private List<String> propertyValues = null;

    private List<String> englishStopwords = null;

    private Set<String> doiPrefixes = null;
    private Set<String> urlDomains = null;

    private static volatile DataseerLexicon instance;

    // to use the url pattern in grobid-core after merging branch update_header
    static public final Pattern urlPattern = Pattern
        .compile("(?i)(https?|ftp)\\s?:\\s?//\\s?[-A-Z0-9+&@#/%=~_:.]*[-A-Z0-9+&@#/%=~_]");

    public static synchronized DataseerLexicon getInstance() {
        if (instance == null)
            instance = new DataseerLexicon();

        return instance;
    }

    private DataseerLexicon() {
        Lexicon.getInstance();
        // init the lexicon
        LOGGER.info("Init dataseer lexicon");

        // term idf
        File file = new File("resources/lexicon/idf.label.en.txt.gz");
        file = new File(file.getAbsolutePath());
        if (!file.exists()) {
            throw new GrobidResourceException("Cannot initialize software dictionary, because file '" + 
                file.getAbsolutePath() + "' does not exists.");
        }
        if (!file.canRead()) {
            throw new GrobidResourceException("Cannot initialize software dictionary, because cannot read file '" + 
                file.getAbsolutePath() + "'.");
        }

        BufferedReader dis = null;
        // read the idf file
        try {
            termIDF = new TreeMap<String, Double>();

            dis = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file)), "UTF-8"));
            //dis = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String l = null;
            while ((l = dis.readLine()) != null) {
                if (l.length() == 0) continue;

                String[] pieces = l.split("\t");
                if (pieces.length != 2) {
                    LOGGER.warn("Invalid term/idf line format: " + l);
                    continue;
                }

                String term = pieces[0];
                String idfString = pieces[1];
                double idf = 0.0;
                try {
                    idf = Double.parseDouble(idfString);
                } catch(Exception e) {
                    LOGGER.warn("Invalid idf format: " + idfString);
                    continue;
                }

                termIDF.put(term, new Double(idf));
            }
        } catch (FileNotFoundException e) {
            throw new GrobidException("SoftwareLexicon file not found.", e);
        } catch (IOException e) {
            throw new GrobidException("Cannot read SoftwareLexicon file.", e);
        } finally {
            try {
                if (dis != null)
                    dis.close();
            } catch(Exception e) {
                throw new GrobidResourceException("Cannot close IO stream.", e);
            }
        }

        // read the datacite DOI prefixes
        file = new File("resources/lexicon/doiPrefixes.txt");
        file = new File(file.getAbsolutePath());
        if (!file.exists()) {
            throw new GrobidResourceException("Cannot initialize DatasetLexicon DOI prefix file, because file '" + 
                file.getAbsolutePath() + "' does not exists.");
        }
        if (!file.canRead()) {
            throw new GrobidResourceException("Cannot initialize DatasetLexicon DOI prefix file, because cannot read file '" + 
                file.getAbsolutePath() + "'.");
        }

        dis = null;
        try {
            doiPrefixes = new HashSet<>();

            dis = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String l = null;
            while ((l = dis.readLine()) != null) {
                l = l.trim();
                if (l.length() == 0) 
                    continue;
                doiPrefixes.add(l);
            }
        } catch (FileNotFoundException e) {
            throw new GrobidException("DatasetLexicon DOI prefix file not found.", e);
        } catch (IOException e) {
            throw new GrobidException("Cannot read DatasetLexicon DOI prefix file.", e);
        } finally {
            try {
                if (dis != null)
                    dis.close();
            } catch(Exception e) {
                throw new GrobidResourceException("DatasetLexicon DOI prefix file: cannot close IO stream.", e);
            }
        }

        // read the data source url domains 
        file = new File("resources/lexicon/domains.txt");
        file = new File(file.getAbsolutePath());
        if (!file.exists()) {
            throw new GrobidResourceException("Cannot initialize DatasetLexicon url domain file, because file '" + 
                file.getAbsolutePath() + "' does not exists.");
        }
        if (!file.canRead()) {
            throw new GrobidResourceException("Cannot initialize DatasetLexicon url domain file, because cannot read file '" + 
                file.getAbsolutePath() + "'.");
        }
        dis = null;
        try {
            urlDomains = new HashSet<>();

            dis = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String l = null;
            while ((l = dis.readLine()) != null) {
                l = l.trim();
                if (l.length() == 0) 
                    continue;
                urlDomains.add(l);
            }
        } catch (FileNotFoundException e) {
            throw new GrobidException("DatasetLexicon url domain file not found.", e);
        } catch (IOException e) {
            throw new GrobidException("Cannot read DatasetLexicon url domain file.", e);
        } finally {
            try {
                if (dis != null)
                    dis.close();
            } catch(Exception e) {
                throw new GrobidResourceException("DatasetLexicon url domain file: cannot close IO stream.", e);
            }
        }

    }

    // to use the same method in grobid-core Utilities.java after merging branch update_header
    public static List<OffsetPosition> convertStringOffsetToTokenOffset(
        List<OffsetPosition> stringPosition, List<LayoutToken> tokens) {
        List<OffsetPosition> result = new ArrayList<OffsetPosition>();
        int indexText = 0;
        int indexToken = 0;
        OffsetPosition currentPosition = null;
        LayoutToken token = null;
        for(OffsetPosition pos : stringPosition) {
            while(indexToken < tokens.size()) {

                token = tokens.get(indexToken);
                if (token.getText() == null) {
                    indexToken++;
                    continue;
                }
                
                if (indexText >= pos.start) {
                    // we have a start
                    currentPosition = new OffsetPosition(indexToken, indexToken);
                    // we need an end
                    boolean found = false;
                    while(indexToken < tokens.size()) {
                        token = tokens.get(indexToken);

                        if (token.getText() == null) {
                            indexToken++;
                            continue;
                        }

                        if (indexText+token.getText().length() >= pos.end) {
                            // we have an end
                            currentPosition.end = indexToken;
                            result.add(currentPosition);
                            found = true;
                            break;
                        }
                        indexToken++;
                        indexText += token.getText().length();
                    }
                    if (found) {
                        indexToken++;
                        indexText += token.getText().length();
                        break;
                    } else {
                        currentPosition.end = indexToken-1;
                        result.add(currentPosition);
                    }
                }
                indexToken++;
                indexText += token.getText().length();
            }
        }
        return result;
    }

    public List<OffsetPosition> tokenPositionsUrlVectorLabeled(List<Pair<String, String>> pairs) {
        List<LayoutToken> tokens = new ArrayList<LayoutToken>();
        for(Pair<String, String> thePair : pairs) {
            tokens.add(new LayoutToken(thePair.getA()));
        }
        String text = LayoutTokensUtil.toText(tokens);
        List<OffsetPosition> textResult = new ArrayList<OffsetPosition>();
        Matcher urlMatcher = urlPattern.matcher(text);
        while (urlMatcher.find()) {  
            //System.out.println(urlMatcher.start() + " / " + urlMatcher.end() + " / " + text.substring(urlMatcher.start(), urlMatcher.end()));                 
            textResult.add(new OffsetPosition(urlMatcher.start(), urlMatcher.end()));
        }
        return convertStringOffsetToTokenOffset(textResult, tokens);
    }

    public double getTermIDF(String term) {
        Double idf = termIDF.get(term);
        if (idf != null)
            return idf.doubleValue();
        else 
            return 0.0;
    }

    /*public boolean inSoftwarePropertyValues(String value) {
        return propertyValues.contains(value.toLowerCase());
    }

    public boolean inSoftwareCategories(String value) {
        return wikipediaCategories.contains(value.toLowerCase());
    }  */ 

    public boolean isEnglishStopword(String value) {
        if (this.englishStopwords == null || value == null)
            return false;
        if (value.length() == 1) 
            value = value.toLowerCase();
        return this.englishStopwords.contains(value);
    }

    /**
     * Return a boolean value indicating if an URL or DOI is data DOI (referenced by datacite)
     * or a known dataset URL.
     * 
     * To determine this, we use a list of DOI prefix collected from a datacite dump and a list
     * of known domains of data repository.
     */
    public boolean isDatasetURLorDOI(String url) {
        if (url == null || url.length() == 0)
            return false;
        return (isDatasetURL(url) || isDatasetDOI(url));
    }

    /**
     * Return a boolean value indicating if an URL data source as a known dataset URL.
     * 
     * To determine this, we use a list of known domains of data repository.
     */
    public boolean isDatasetURL(String url) {
        if (url == null || url.length() == 0)
            return false;

        // strip protocol prefix
        if (url.startsWith("https://"))
            url = url.substring(8);
        if (url.startsWith("http://"))
            url = url.substring(7);
        if (url.startsWith("www."))
            url = url.substring(4);

        // strip url path
        int ind = url.indexOf("/");
        if (ind != -1) 
            url = url.substring(0, ind);

System.out.println(" -----------------> check URL string with: " + url);

        if (urlDomains != null && urlDomains.contains(url))
            return true;
        return false;
    }


    /**
     * Return a boolean value indicating if a DOI is data DOI (referenced by datacite).
     * 
     * To determine this, we use a list of DOI prefix collected from a datacite dump.
     */
    public boolean isDatasetDOI(String doi) {
        if (doi == null || doi.length() == 0)
            return false;

        // strip protocol prefix
        doi = doi.replace("https://doi.org/", "");
        doi = doi.replace("http://doi.org/", "");

        // strip url path
        int ind = doi.indexOf("/");
        if (ind != -1) 
            doi = doi.substring(0, ind);

System.out.println(" -----------------> check DOI string with: " + doi);

        if (doiPrefixes != null && doiPrefixes.contains(doi))
            return true;
        return false;
    }
}
