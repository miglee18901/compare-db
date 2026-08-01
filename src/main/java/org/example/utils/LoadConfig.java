package org.example.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class LoadConfig {
    private static final Logger logger = LogManager.getLogger(LoadConfig.class);

    private int mode = 1;
    private int batchSize = 1000;
    private String pathStatsFile = ".";
    private boolean loadedSuccessfully = false;

    public LoadConfig() {
        load();
    }

    private void load() {
        Properties props = new Properties();
        File configFile = new File("config.properties");
        if (!configFile.exists()) {
            logger.error("config.properties file not found in the working directory!");
            return;
        }

        try (FileInputStream fis = new FileInputStream(configFile)) {
            props.load(fis);
            loadedSuccessfully = true;
        } catch (IOException e) {
            logger.error("Error reading config.properties file: ", e);
            return;
        }

        // Read Mode (defaults to 1)
        try {
            mode = Integer.parseInt(props.getProperty("MODE", "1").trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid MODE configuration value. Using default MODE = 1");
        }

        // Read Batch Size (defaults to 1000)
        try {
            batchSize = Integer.parseInt(props.getProperty("BATCH_SIZE", "1000").trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid BATCH_SIZE configuration value. Using default BATCH_SIZE = 1000");
        }

        // Read output path for results (defaults to ".")
        String pathVal = props.getProperty("PATH_STATISTICS_FILE");
        if (pathVal != null) {
            pathStatsFile = pathVal.trim();
        }
    }

    public int getMode() {
        return mode;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public String getPathStatsFile() {
        return pathStatsFile;
    }

    public boolean isLoadedSuccessfully() {
        return loadedSuccessfully;
    }
}
