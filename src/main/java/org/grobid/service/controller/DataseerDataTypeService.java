package org.grobid.service.controller;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.io.FileUtils;
import org.grobid.core.engines.DataseerClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.List;
import java.util.NoSuchElementException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.io.*;
import java.lang.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 
 * @author Patrice
 * 
 */
public class DataseerDataTypeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataseerDataTypeService.class);

    private static volatile DataseerDataTypeService instance;

    // we keep the datatype json in memory and instance-based for the application 
    // for faster serving the resource
    private String jsonDataTypeResource = null;

    private String defaultPath = null;

    public static DataseerDataTypeService getInstance() {
        if (instance == null) {
            getNewInstance();
        }
        return instance;
    }

    /**
     * Create a new instance.
     */
    private static synchronized void getNewInstance() {
        instance = new DataseerDataTypeService();
    }

    private DataseerDataTypeService() {
        File jsonFile = new File("resources/DataTypes.json");
        if (!jsonFile.exists())
            jsonDataTypeResource = null;
        else {
            try {  
                this.jsonDataTypeResource = FileUtils.readFileToString(jsonFile, StandardCharsets.UTF_8);
            } catch(Exception e) {
                LOGGER.warn("Data type json file cannot be read", e);
            }
        }
        // get default current path
        this.defaultPath = Paths.get(".").toAbsolutePath().normalize().toString();
    }

    public Response getJsonDataTypes() {
        if (jsonDataTypeResource == null)
            return getResyncJsonDataTypes();
        // if the json resource file is not available, we need to sync it 
        return Response.status(Status.OK).entity(jsonDataTypeResource).type(MediaType.APPLICATION_JSON).build();
    }

    public Response getResyncJsonDataTypes() {
        // external call to the python script crawling the wiki in a separate thread

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("python3", "script/converter.py", "resources/dataset/dataseer/csv/all-1.csv");
        // ensure we are using the right path to the script
        processBuilder.directory(new File(this.defaultPath));
        LOGGER.info("calling script:" + processBuilder.command());
        try {
            long start = System.currentTimeMillis();
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
                builder.append(System.getProperty("line.separator"));
            }

            int exitCode = process.waitFor();
            long end = System.currentTimeMillis();
            LOGGER.info("Exit code : " + exitCode);
            LOGGER.info("Sync with online DataSeer wiki made in " + ((end - start)/1000) + " seconds");

            if (builder.length()>0)
                jsonDataTypeResource = builder.toString();
        } catch (Exception e) {
            e.printStackTrace();
        } 

        return Response.status(Status.OK).entity(jsonDataTypeResource).type(MediaType.APPLICATION_JSON).build();
    }

    public Response getResyncThreadedJsonDataTypes() {
        // external call to the python script crawling the wiki in a separate thread

        ExecutorService pool = Executors.newSingleThreadExecutor();

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("python3", "script/converter.py", "resources/dataset/dataseer/csv/all-1.csv");
        // ensure we are using the right path to the script
        processBuilder.directory(new File(this.defaultPath));
        LOGGER.info("calling script:" + processBuilder.command());
        try {
            long theStart = System.currentTimeMillis();
            Process process = processBuilder.start();

            ProcessReadTask task = new ProcessReadTask(process.getInputStream());
            Future<List<String>> future = pool.submit(task);
            pool.shutdown();

            StringBuilder builder = new StringBuilder();
            //ExecutorCompletionService service = new ExecutorCompletionService(pool);
            while (!pool.isTerminated()) {
                Thread.sleep(1000);
            }
            
            List<String> result = future.get();
            for(String line : result) {
                builder.append(line);
                builder.append(System.getProperty("line.separator"));
            }

            long theEnd = System.currentTimeMillis();
            LOGGER.info("Sync with online DataSeer wiki made in " + ((theEnd - theStart)/1000) + " milliseconds");

            if (builder.length()>0)
                jsonDataTypeResource = builder.toString();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            pool.shutdown();
        }

        return Response.status(Status.OK).entity(jsonDataTypeResource).type(MediaType.APPLICATION_JSON).build();
    }

    private static class ProcessReadTask implements Callable<List<String>> {

        private InputStream inputStream;

        public ProcessReadTask(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public List<String> call() {
            return new BufferedReader(new InputStreamReader(inputStream))
                .lines()
                .collect(Collectors.toList());
        }
    }

}