package org.grobid.core.utilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.grobid.service.configuration.DatastetServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

/**
 * Some convenient methods for retrieving the original PDF files from the annotated set.
 */
public class ArticleUtilities {

    private static final Logger logger = LoggerFactory.getLogger(ArticleUtilities.class);

    // Shared Jackson mapper: ObjectMapper is thread-safe after configuration
    // and retains compiled bean descriptors, so creating one per request is
    // wasteful under load. See https://github.com/FasterXML/jackson-docs/wiki/Presentation:-Jackson-Performance
    private static final ObjectMapper JSON = new ObjectMapper();

    private DatastetServiceConfiguration configuration;

    private static String halURL = "https://hal.archives-ouvertes.fr";
    private static String pmcURL = "http://www.ncbi.nlm.nih.gov/pmc/articles";
    private static String arxivURL = "https://arxiv.org/pdf";

    public int totalDOIFail = 0;
    public int totalFail = 0;

    public enum Source {
        HAL, PMC, ARXIV, DOI;
    }

    public ArticleUtilities(DatastetServiceConfiguration datastetServiceConfiguration) {
        this.configuration = datastetServiceConfiguration;
    }

    /**
     * Get the PDF file from an article ID.
     * If the source is not present, we try to guess it from the identifier itself.
     * <p>
     * Return null if the identification fails.
     */
    public File getPDFDoc(String identifier, Source source) {
        try {
            if (source == null) {
                source = guessDomain(identifier);
            }

            if (source == null) {
                totalFail++;
                logger.info("Cannot identify download url for " + identifier);
                return null;
            }

            String urll = null;
            switch (source) {
                case HAL:
                    urll = halURL + File.separator + identifier + "/document";
                    break;
                case PMC:
                    urll = pmcURL + File.separator + identifier + "/pdf";
                    break;
                case ARXIV:
                    String localNumber = identifier.replace("arXiv:", "");
                    urll = arxivURL + File.separator + localNumber + ".pdf";
                    break;
                case DOI:
                    // hard case to find the right PDF, we use the Unpaywall API to get the best Open Access PDF url
                    // handle boring URL encoding
                    try {
                        identifier = urlDecode(identifier);
                        urll = getUnpaywallOAUrl(identifier);
                        if (urll == null || urll.equals("null")) {
                            urll = getGluttonOAUrl(identifier);
                        }
                        if (urll == null || urll.equals("null")) {
                            totalDOIFail++;
                            logger.warn("No Open Access PDF found via Unpaywall for DOI: " + identifier);
                            System.out.println("No Open Access PDF found via Unpaywall for DOI: " + identifier);
                            urll = null;
                        }
                    } catch (UnsupportedEncodingException e) {
                        logger.warn("Invalid DOI identifier encoding: " + identifier, e);
                        System.out.println("Invalid DOI: " + identifier);
                    } catch (Exception e) {
                        logger.warn("No Open Access PDF found for DOI: " + identifier, e);
                        System.out.println("No Open Access PDF found via Unpaywall for DOI: " + identifier);
                    }
            }

            if (urll == null) {
                totalFail++;
                logger.info("Cannot identify download url for " + identifier);
                return null;
            }

            File file = uploadFile(urll, this.configuration.getDatastetConfiguration().getTmpPath(),
                    KeyGen.getKey() + ".pdf");
            return file;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public File getPDFDoc(String identifier) {
        return getPDFDoc(identifier, null);
    }

    private static String urlDecode(String value) throws Exception {
        return URLDecoder.decode(value, StandardCharsets.UTF_8.toString());
    }

    private Source guessDomain(String identifier) {
        identifier = identifier.replace("%2F", "/");
        if (identifier.startsWith("PMC")) {
            return Source.PMC;
        } else if (identifier.startsWith("hal-")) {
            return Source.HAL;
        } else if (identifier.startsWith("10.") ||
                identifier.startsWith("https://doi.org/10.") ||
                identifier.startsWith("http://dx.doi.org/10.")) {
            return Source.DOI;
        } else {
            Matcher arXivMatcher = TextUtilities.arXivPattern.matcher(identifier);
            if (arXivMatcher.find()) {
                return Source.ARXIV;
            }
        }
        return null;
    }

    private String getUnpaywallOAUrl(String doi) throws Exception {
        doi = doi.trim();
        doi = doi.replace(" ", "");

        String queryUrl = "https://api.unpaywall.org/v2/" + doi + "?email=patrice.lopez@science-miner.com";
        logger.debug("GET {}", queryUrl);
        String json = httpGetAsString(queryUrl);

        JsonNode jsonNode = JSON.readTree(json);
        JsonNode bestOALocation = jsonNode.path("best_oa_location");
        if (!bestOALocation.isMissingNode()) {
            JsonNode urlForPdfNode = bestOALocation.path("url_for_pdf");
            if (!urlForPdfNode.isMissingNode()) {
                return urlForPdfNode.asText();
            }
        }
        return null;
    }

    private String getGluttonOAUrl(String doi) throws Exception {
        String host = this.configuration.getDatastetConfiguration().getGluttonHost();
        String port = this.configuration.getDatastetConfiguration().getGluttonPort();
        String queryUrl = "http://" + host;
        if (port != null)
            queryUrl += ":" + port;
        queryUrl += "/service/oa?doi=" + doi;
        logger.debug("GET {}", queryUrl);
        String json = httpGetAsString(queryUrl);

        JsonNode jsonNode = JSON.readTree(json);
        JsonNode urlForPdfNode = jsonNode.path("oaLink");
        if (!urlForPdfNode.isMissingNode()) {
            return urlForPdfNode.asText();
        }
        return null;
    }

    /**
     * Shared single-use HTTP-GET helper. Every resource (client, response,
     * reader) is closed in a try-with-resources chain so a thrown exception
     * cannot leak the connection pool or the response entity stream.
     */
    private static String httpGetAsString(String url) throws IOException {
        HttpGet request = new HttpGet(url);
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(request);
             BufferedReader rd = new BufferedReader(
                     new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8))) {
            logger.debug("Response Code : {}", response.getStatusLine().getStatusCode());
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        }
    }

    private static File uploadFile(String urll, String path, String name) throws Exception {
        try {
            File pathFile = new File(path);
            if (!pathFile.exists()) {
                System.out.println("temporary path for dataseer invalid: " + path);
                return null;
            }

            System.out.println("GET: " + urll);
            URL url = new URL(urll);

            File outFile = new File(path, name);

            Downloader downloader = new Downloader();
            downloader.download(url, outFile);
            //downloader.downloadExternal(url, outFile);
            return outFile;
        } catch (Exception e) {
            throw new Exception("An exception occured while downloading " + urll, e);
        }
    }

    /**
     * Apply a Pub2TEI transformation to an XML file to produce a TEI file.
     * Input XML file must be a native XML publisher file supported by Pub2TEI.
     * Output the path to the transformed outputed file or null if the transformation failed.
     */
    public static String applyPub2TEI(String inputFilePath, String outputFilePath, String pathToPub2TEI) {
        // we use an external command line for simplification (though it would be more elegant to 
        // stay in the current VM)
        // java -jar Samples/saxon9he.jar -s:/mnt/data/resources/plos/0/ -xsl:Stylesheets/Publishers.xsl -o:/mnt/data/resources/plos/0/tei/ -dtd:off -a:off -expand:off -t

        // remove first the DTD declaration from the input nlm/jats XML because all these shitty xml mechanisms break 
        // the process at one point or another or keep looking for something over the internet 
        try {
            String xmlContent = FileUtils.readFileToString(new File(inputFilePath), "UTF-8");
            xmlContent = xmlContent.replaceAll("<!DOCTYPE((.|\n|\r)*?)\">", "");
            FileUtils.writeStringToFile(new File(inputFilePath), xmlContent, "UTF-8");
        } catch (IOException e) {
            logger.error("Fail to preprocess the XML file to be transformed", e);
        }

        ProcessBuilder processBuilder = new ProcessBuilder();
        String s = "-s:" + inputFilePath;
        File dirToPub2TEI = new File(pathToPub2TEI);

        String xsl = "-xsl:" + dirToPub2TEI.getAbsolutePath() + "/Stylesheets/Publishers.xsl";
        String o = "-o:" + outputFilePath;
        processBuilder.command("java", "-jar", dirToPub2TEI.getAbsolutePath() + "/Samples/saxon9he.jar", s, xsl, o, "-dtd:off", "-a:off", "-expand:off", "-t");
        // Merge stderr into stdout so we consume a single pipe — otherwise a
        // chatty Saxon can fill the OS stderr buffer and deadlock the child.
        processBuilder.redirectErrorStream(true);

        Process process = null;
        try {
            process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitVal = process.waitFor();
            if (exitVal == 0) {
                logger.info("XML transformation done");
            } else {
                logger.warn("XML transformation failed (exit code {}): {}", exitVal, output);
                outputFilePath = null;
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Failure running Pub2TEI transformation", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            outputFilePath = null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
        return outputFilePath;
    }

    /**
     * Write an input stream in temp directory.
     */
    public static File writeInputFile(InputStream inputStream, String extension) {
        logger.debug(">> set origin document for stateless service'...");

        File originFile = null;
        OutputStream out = null;
        try {
            originFile = IOUtilities.newTempFile("origin", extension);

            out = new FileOutputStream(originFile);

            byte buf[] = new byte[1024];
            int len;
            while ((len = inputStream.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } catch (IOException e) {
            logger.error(
                    "An internal error occurs, while writing to disk (file to write '"
                            + originFile + "').", e);
            originFile = null;
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
                inputStream.close();
            } catch (IOException e) {
                logger.error("An internal error occurs, while writing to disk (file to write '"
                        + originFile + "').", e);
                originFile = null;
            }
        }
        return originFile;
    }

}