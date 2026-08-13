package org.example.main;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.example.compare.DbComparator;
import org.example.model.TableConfig;
import org.example.utils.DbHelper;
import org.example.utils.LoadConfig;
import org.hibernate.SessionFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Start {
    private static final Logger logger = LogManager.getLogger(Start.class);

    private static final String PATH_LOG4J2 = "../etc/log4j2.xml";
    private static final String PATH_CONFIG = "../etc/config.properties";
    private static final String PATH_TABLE_LIST = "../etc/tableList.txt";
    private static final String PATH_CFG_16M = "../etc/hibernate_mysql_crbt16m.cfg.xml";
    private static final String PATH_CFG_21M = "../etc/hibernate_mysql_crbt21m.cfg.xml";

    public static void main(String[] args) {
        logger.info("Starting Compare DB application...");

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        File log4j = new File(PATH_LOG4J2);
        ctx.setConfigLocation(log4j.toURI());

        LoadConfig config = new LoadConfig(PATH_CONFIG);
        if (!config.isLoadedSuccessfully()) {
            return;
        }

        int mode = config.getMode();
        int batchSize = config.getBatchSize();
        int workerThreads = config.getWorkerThreads();

        File outputDir = new File(config.getPathStatsFile());
        if (!outputDir.exists()) {
            boolean created = outputDir.mkdirs();
            if (created) {
                logger.debug("Created result directory: {}", outputDir.getAbsolutePath());
            }
        }
        String absoluteOutputPath = Paths.get(config.getPathStatsFile()).toAbsolutePath().normalize().toString();
        File absoluteOutputDir = new File(absoluteOutputPath);

        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String resultFileName = "result_" + timestamp + ".txt";
        File resultFile = new File(absoluteOutputDir, resultFileName);

        List<TableConfig> tableConfigs = new ArrayList<>();
        File tableListFile = new File(PATH_TABLE_LIST);
        if (!tableListFile.exists()) {
            logger.error("Configuration file not found: {}", tableListFile.getAbsolutePath());
            return;
        }
        String absoluteTableListPath = Paths.get(PATH_TABLE_LIST).toAbsolutePath().normalize().toString();
        File absoluteTableListFile = new File(absoluteTableListPath);
        try (BufferedReader br = Files.newBufferedReader(absoluteTableListFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                try {
                    TableConfig tblConfig = TableConfig.parse(line);
                    if (tblConfig != null) {
                        tableConfigs.add(tblConfig);
                    }
                } catch (IllegalArgumentException e) {
                    logger.error("Skipped malformed line in {}: {}", tableListFile.getPath(), e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.error("Error reading {}: ", tableListFile.getPath(), e);
            return;
        }

        if (tableConfigs.isEmpty()) {
            logger.error("Table list in {} is empty or contains no valid tables.", tableListFile.getPath());
            return;
        }

        File cfgFile16M = new File(PATH_CFG_16M);
        File cfgFile21M = new File(PATH_CFG_21M);

        SessionFactory sf16M = null;
        SessionFactory sf21M = null;

        try {
            logger.debug("Connecting to database CRBT16M...");
            sf16M = DbHelper.buildSessionFactory(cfgFile16M, null);

            logger.debug("Connecting to database CRBT21M...");
            sf21M = DbHelper.buildSessionFactory(cfgFile21M, null);

            logger.info("Starting parallel comparison (Mode = {}, workers = {})...", mode, workerThreads);
            List<String> report = DbComparator.compareInParallel(sf16M, sf21M, tableConfigs, mode, batchSize, workerThreads);

            if (!report.isEmpty()) {
                Files.write(resultFile.toPath(), report, StandardCharsets.UTF_8);
                logger.info("Database validation results written successfully.");
                logger.info("Result file path: {}", resultFile.getAbsolutePath());
            }

        } catch (Exception e) {
            logger.error("An error occurred during comparison: ", e);
            try {
                List<String> errorReport = new ArrayList<>();
                errorReport.add("Comparison process failed due to system error:");
                errorReport.add(e.toString());
                Files.write(resultFile.toPath(), errorReport, StandardCharsets.UTF_8);
                logger.info("Error report written to: {}", resultFile.getAbsolutePath());
            } catch (IOException ex) {
                logger.error("Unable to write error report file: ", ex);
            }
        } finally {
            if (sf16M != null) {
                try {
                    sf16M.close();
                } catch (Exception e) {
                    logger.error("Error closing SessionFactory CRBT16M", e);
                }
            }
            if (sf21M != null) {
                try {
                    sf21M.close();
                } catch (Exception e) {
                    logger.error("Error closing SessionFactory CRBT21M", e);
                }
            }
            logger.info("All connection resources released. Program execution completed.");
        }
    }
}