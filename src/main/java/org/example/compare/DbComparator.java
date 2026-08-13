package org.example.compare;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.model.TableConfig;
import org.example.utils.DBUtils;
import org.example.utils.ProcessUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.*;
import java.util.concurrent.*;

public class DbComparator {
    private static final Logger logger = LogManager.getLogger(DbComparator.class);

    public static List<String> compare(Session session1, Session session2, List<TableConfig> tableConfigs, int mode, int batchSize) {
        List<String> reportLines = new ArrayList<>();

        if (mode == 1) {
            List<TableCompareResult> results = new ArrayList<>();
            List<String> detailedMismatches = new ArrayList<>();

            for (TableConfig config : tableConfigs) {
                logger.info("Checking and validating table configuration: {}", config.getTableName());

                TableMetadata meta1 = TableMetadata.getMetadata(session1, config.getTableName());
                TableMetadata meta2 = TableMetadata.getMetadata(session2, config.getTableName());

                boolean hasError = !meta1.isExists();

                if (!meta2.isExists()) {
                    hasError = true;
                }
                if (hasError) {
                    logger.error("Table {} does not exist in one of the environments. Skipping comparison for this table.", config.getTableName());
                    reportLines.add(createMissingTableReport(config.getTableName(), meta1, meta2));
                    continue;
                }

                String keyCol = config.getKeyColumn().toUpperCase();
                if (!meta1.getColumns().contains(keyCol)) {
                    logger.error("Key column {} does not exist in table {} in CRBT16M. Skipping comparison for this table.", keyCol, config.getTableName());
                    hasError = true;
                }
                if (!meta2.getColumns().contains(keyCol)) {
                    logger.error("Key column {} does not exist in table {} in CRBT21M. Skipping comparison for this table.", keyCol, config.getTableName());
                    hasError = true;
                }

                boolean isKey1Valid = meta1.getPrimaryKeys().contains(keyCol) || meta1.getUniqueKeys().contains(keyCol);
                boolean isKey2Valid = meta2.getPrimaryKeys().contains(keyCol) || meta2.getUniqueKeys().contains(keyCol);

                if (!isKey1Valid) {
                    logger.error("Key column {} is not a primary or unique key in table {} in CRBT16M. Skipping comparison for this table.", keyCol, config.getTableName());
                    hasError = true;
                }
                if (!isKey2Valid) {
                    logger.error("Key column {} is not a primary or unique key in table {} in CRBT21M. Skipping comparison for this table.", keyCol, config.getTableName());
                    hasError = true;
                }

                for (String ignoreCol : config.getIgnoreColumns()) {
                    String icUpper = ignoreCol.toUpperCase();
                    if (icUpper.equals(keyCol)) {
                        logger.error("Ignore column {} is the same as the key column {} in table {}. Skipping comparison for this table.", ignoreCol, keyCol, config.getTableName());
                        hasError = true;
                    }
                    if (!meta1.getColumns().contains(icUpper)) {
                        logger.error("Ignore column {} does not exist in table {} in CRBT16M. Skipping comparison for this table.", ignoreCol, config.getTableName());
                        hasError = true;
                    }
                    if (!meta2.getColumns().contains(icUpper)) {
                        logger.error("Ignore column {} does not exist in table {} in CRBT21M. Skipping comparison for this table.", ignoreCol, config.getTableName());
                        hasError = true;
                    }
                }

                if (hasError) {
                    logger.error("Table configuration for {} violates validation. Skipping comparison for this table.", config.getTableName());
                    continue;
                }

                logger.info("Starting detailed data comparison for table: {}", config.getTableName());
                TableCompareResult tblResult = compareTableData(session1, session2, config, meta1, meta2, batchSize);
                results.add(tblResult);
            }

            for (TableCompareResult r : results) {
                reportLines.add(String.format("TABLE = %s, TOTAL = %d, MATCH = %d, NOT_MATCH = %d", r.getTableName(), r.getTotal(), r.getMatch(), r.getNotMatch()));
            }

            if (!reportLines.isEmpty()) {
                reportLines.add("");
            }

            for (TableCompareResult r : results) {
                boolean hasDetails = !r.getMissingColumnsInCrbt16M().isEmpty() || !r.getMissingColumnsInCrbt21M().isEmpty() || !r.getDiffDetails().isEmpty();
                if (!hasDetails) {
                    continue;
                }
                if (detailedMismatches.isEmpty()) {
                    detailedMismatches.add("COMPARE:");
                }
                detailedMismatches.add("TABLE = " + r.getTableName());
                if (!r.getMissingColumnsInCrbt16M().isEmpty()) {
                    detailedMismatches.add("Missing column in CRBT16M: " + String.join(", ", r.getMissingColumnsInCrbt16M()));
                }
                if (!r.getMissingColumnsInCrbt21M().isEmpty()) {
                    detailedMismatches.add("Missing column in CRBT21M: " + String.join(", ", r.getMissingColumnsInCrbt21M()));
                }
                for (String detail : r.getDiffDetails()) {
                    detailedMismatches.add(detail.contains(" key = ") ? detail : "    " + detail);
                }
                detailedMismatches.add("");
            }
            reportLines.addAll(detailedMismatches);

        } else if (mode == 2) {
            for (TableConfig config : tableConfigs) {
                logger.info("Checking and validating table configuration: {}", config.getTableName());

                TableMetadata meta1 = TableMetadata.getMetadata(session1, config.getTableName());
                TableMetadata meta2 = TableMetadata.getMetadata(session2, config.getTableName());

                boolean hasError = !meta1.isExists();
                if (!meta2.isExists()) {
                    hasError = true;
                }
                if (hasError) {
                    logger.error("Table {} does not exist in one of the environments. Skipping count for this table.", config.getTableName());
                    reportLines.add(createMissingTableReport(config.getTableName(), meta1, meta2));
                    continue;
                }

                logger.info("Counting records for table: {}", config.getTableName());
                long count1 = DBUtils.getRecordCount(session1, meta1.getActualTableName());
                long count2 = DBUtils.getRecordCount(session2, meta2.getActualTableName());
                reportLines.add(String.format("TABLE = %s, CRBT16M = %d, CRBT21M = %d", config.getTableName(), count1, count2));
            }
        }
        return reportLines;
    }

    public static List<String> compareInParallel(SessionFactory sessionFactory16M, SessionFactory sessionFactory21M, List<TableConfig> tableConfigs, int mode, int batchSize, int workerThreads) {
        int poolSize = Math.min(workerThreads, tableConfigs.size());
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        logger.debug("Starting parallel comparison with {} worker threads for {} tables.", poolSize, tableConfigs.size());
        try {
            List<Future<List<String>>> futures = new ArrayList<>();
            for (TableConfig config : tableConfigs) {
                futures.add(executor.submit(() -> compareTable(sessionFactory16M, sessionFactory21M, config, mode, batchSize)));
            }

            List<String> reportLines = new ArrayList<>();
            for (Future<List<String>> future : futures) {
                reportLines.addAll(future.get());
            }
            return reportLines;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Comparison was interrupted.", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Unable to compare table.", e.getCause());
        } finally {
            executor.shutdown();
        }
    }

    private static List<String> compareTable(SessionFactory sessionFactory16M, SessionFactory sessionFactory21M, TableConfig config, int mode, int batchSize) {
        logger.info("Starting comparison task for table {}", config.getTableName());
        Session session16M = null;
        Session session21M = null;
        try {
            session16M = sessionFactory16M.openSession();
            session21M = sessionFactory21M.openSession();
            List<String> tableReport = compare(session16M, session21M, Collections.singletonList(config), mode, batchSize);
            if (tableReport.isEmpty()) {
                return tableReport;
            }
            List<String> formattedReport = new ArrayList<>();
            formattedReport.add("-------------------------------------------------------------------------------------");
            formattedReport.addAll(tableReport);
            return formattedReport;
        } finally {
            closeSession(session16M, "CRBT16M", config.getTableName());
            closeSession(session21M, "CRBT21M", config.getTableName());
            logger.info("Finished comparison task for table {}", config.getTableName());
        }
    }

    private static void closeSession(Session session, String environment, String tableName) {
        if (session == null) {
            return;
        }
        try {
            session.close();
        } catch (Exception e) {
            logger.error("Unable to close {} session for table {}", environment, tableName, e);
        }
    }

    private static String createMissingTableReport(String tableName, TableMetadata meta1, TableMetadata meta2) {
        List<String> reportParts = new ArrayList<>();
        reportParts.add("TABLE = " + tableName);
        if (!meta1.isExists()) {
            reportParts.add("CRBT16M = <does not exist>");
        }
        if (!meta2.isExists()) {
            reportParts.add("CRBT21M = <does not exist>");
        }
        return String.join(", ", reportParts);
    }

    private static TableCompareResult compareTableData(Session session1, Session session2, TableConfig config, TableMetadata meta1, TableMetadata meta2, int batchSize) {
        TableCompareResult result = new TableCompareResult(config.getTableName());
        String keyCol = config.getKeyColumn().toUpperCase();

        Set<String> commonCols = new LinkedHashSet<>(meta1.getColumns());
        commonCols.retainAll(meta2.getColumns());

        Set<String> ignoreCols = new LinkedHashSet<>();
        for (String ic : config.getIgnoreColumns()) {
            ignoreCols.add(ic.toUpperCase());
        }

        Set<String> excludeCols = new LinkedHashSet<>();
        excludeCols.add(keyCol);
        excludeCols.addAll(ignoreCols);

        boolean keyIsPk1 = meta1.getPrimaryKeys().contains(keyCol);
        boolean keyIsUk1 = meta1.getUniqueKeys().contains(keyCol);
        boolean keyIsPk2 = meta2.getPrimaryKeys().contains(keyCol);
        boolean keyIsUk2 = meta2.getUniqueKeys().contains(keyCol);

        if ((keyIsUk1 && !keyIsPk1) || (keyIsUk2 && !keyIsPk2)) {
            excludeCols.addAll(meta1.getPrimaryKeys());
            excludeCols.addAll(meta2.getPrimaryKeys());
        }

        List<String> compareCols = new ArrayList<>();
        for (String col : commonCols) {
            if (!excludeCols.contains(col)) {
                compareCols.add(col);
            }
        }

        boolean hasColumnMismatch = false;
        for (String col : meta1.getColumns()) {
            if (!meta2.getColumns().contains(col) && !excludeCols.contains(col)) {
                hasColumnMismatch = true;
                result.addMissingColumnInCrbt21M(col);
            }
        }
        for (String col : meta2.getColumns()) {
            if (!meta1.getColumns().contains(col) && !excludeCols.contains(col)) {
                hasColumnMismatch = true;
                result.addMissingColumnInCrbt16M(col);
            }
        }

        TableDataContext ctx1 = new TableDataContext(session1, "CRBT16M", meta1.getActualTableName(), keyCol, compareCols, batchSize);
        TableDataContext ctx2 = new TableDataContext(session2, "CRBT21M", meta2.getActualTableName(), keyCol, compareCols, batchSize);

        while (true) {
            Object[] row1 = ctx1.peek();
            Object[] row2 = ctx2.peek();

            if (row1 == null && row2 == null) {
                break;
            }

            if (row1 != null && row2 != null) {
                Object k1 = row1[0];
                Object k2 = row2[0];
                int cmp = ProcessUtils.compareKeys(k1, k2);

                if (cmp == 0) {
                    ctx1.poll();
                    ctx2.poll();

                    boolean match = !hasColumnMismatch;
                    List<String> diffLogs = new ArrayList<>();
                    for (int i = 0; i < compareCols.size(); i++) {
                        Object val1 = row1[i + 1];
                        Object val2 = row2[i + 1];
                        if (!ProcessUtils.valuesEqual(val1, val2)) {
                            match = false;
                            String colName = compareCols.get(i);
                            diffLogs.add(String.format("%s (CRBT16M) = %s, %s (CRBT21M) = %s", colName, ProcessUtils.formatValue(val1), colName, ProcessUtils.formatValue(val2)));
                        }
                    }

                    if (match) {
                        result.incrementMatch();
                    } else {
                        result.incrementNotMatch();
                        if (!diffLogs.isEmpty()) {
                            result.addDiffDetail(String.format("%s key = %s:", keyCol, k1));
                            for (String log : diffLogs) {
                                result.addDiffDetail(log);
                            }
                        }
                    }
                } else if (cmp < 0) {
                    ctx1.poll();
                    result.incrementNotMatch();
                    result.addDiffDetail(String.format("%s key = %s: CRBT21M = <does not exist>", keyCol, k1));
                } else {
                    ctx2.poll();
                    result.incrementNotMatch();
                    result.addDiffDetail(String.format("%s key = %s: CRBT16M = <does not exist>", keyCol, k2));
                }
            } else if (row1 != null) {
                Object k1 = row1[0];
                ctx1.poll();
                result.incrementNotMatch();
                result.addDiffDetail(String.format("%s key = %s: CRBT21M = <does not exist>", keyCol, k1));
            } else {
                Object k2 = row2[0];
                ctx2.poll();
                result.incrementNotMatch();
                result.addDiffDetail(String.format("%s key = %s: CRBT16M = <does not exist>", keyCol, k2));
            }
        }

        return result;
    }
}
