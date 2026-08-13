package org.example.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.compare.TableMetadata;
import org.hibernate.Session;
import org.owasp.encoder.Encode;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DBUtils {

    private static final Logger logger = LogManager.getLogger(DBUtils.class);

    public static long getRecordCount(Session session, String tableName) {
        Connection connection = session.connection();
        String sql = "SELECT COUNT(*) FROM " + tableName;

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getLong(1);
            }
            return 0L;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to count records for table: " + tableName, e);
        }
    }

    public static List<Object[]> fetchDataBatch(Session session, String environment, String tableName, List<String> selectColumns, String keyColumn, long offset, int limit) {
        logger.debug("Fetching data batch from environment: {}, table: {}, offset: {}, limit: {}", environment, tableName, offset, limit);
        Connection connection = session.connection();
        List<Object[]> batch = new ArrayList<>();
        String safeTable = quoteIdentifier(connection, tableName);
        String safeKeyColumn = quoteIdentifier(connection, keyColumn);
        List<String> safeColumns = new ArrayList<>();
        for (String column : selectColumns) {
            safeColumns.add(quoteIdentifier(connection, column));
        }
        String safeColumnList = String.join(", ", safeColumns);
        String sql = "SELECT " + safeColumnList + " FROM " + safeTable + " ORDER BY " + safeKeyColumn + " ASC LIMIT ? OFFSET ?";

        logger.debug("Executing SQL in environment {}: {}", environment, sql);
        try (PreparedStatement statement = connection.prepareStatement(Encode.forHtml(sql))) {
            statement.setInt(1, limit);
            statement.setLong(2, offset);

            try (ResultSet resultSet = statement.executeQuery()) {
                int columnCount = selectColumns.size();
                while (resultSet.next()) {
                    String[] row = new String[columnCount];
                    for (int i = 0; i < columnCount; i++) {
                        row[i] = resultSet.getString(i + 1);
                    }
                    batch.add(row);
                }
            }
            return batch;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read data from table: " + tableName, e);
        }
    }

    private static String validateIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid SQL identifier");
        }
        return identifier;
    }

    private static String quoteIdentifier(Connection connection, String identifier) {
        String safe = validateIdentifier(identifier);
        try {
            String quote = connection.getMetaData().getIdentifierQuoteString();
            if (quote == null || quote.trim().isEmpty()) {
                return safe;
            }
            return quote + safe + quote;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to quote identifier: " + identifier, e);
        }
    }

    public static TableMetadata getTableMetadata(Session session, String inputTableName) {
        TableMetadata metadata = new TableMetadata();

        try {
            Connection connection = session.connection();
            DatabaseMetaData databaseMetadata = connection.getMetaData();

            String schema = connection.getCatalog();
            if (schema == null || schema.isEmpty()) {
                schema = connection.getSchema();
            }

            String inputUpper = inputTableName.trim().toUpperCase();
            findTable(databaseMetadata, schema, null, inputUpper, metadata);
            if (!metadata.isExists()) {
                findTable(databaseMetadata, null, schema, inputUpper, metadata);
            }

            if (metadata.isExists()) {
                readColumns(databaseMetadata, schema, null, metadata);
                if (metadata.getColumns().isEmpty()) {
                    readColumns(databaseMetadata, null, schema, metadata);
                }

                readPrimaryKeys(databaseMetadata, schema, null, metadata);
                if (metadata.getPrimaryKeys().isEmpty()) {
                    readPrimaryKeys(databaseMetadata, null, schema, metadata);
                }

                readUniqueKeys(databaseMetadata, schema, null, metadata);
                if (metadata.getUniqueKeys().isEmpty()) {
                    readUniqueKeys(databaseMetadata, null, schema, metadata);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read metadata for table: " + inputTableName, e);
        }

        return metadata;
    }

    private static void findTable(DatabaseMetaData metadata, String catalog, String schema, String inputUpper, TableMetadata tableMetadata) throws Exception {
        try (ResultSet resultSet = metadata.getTables(catalog, schema, null, new String[]{"TABLE", "VIEW"})) {
            while (resultSet.next()) {
                String tableName = resultSet.getString("TABLE_NAME");
                if (tableName != null && tableName.toUpperCase().equals(inputUpper)) {
                    tableMetadata.setExists(true);
                    tableMetadata.setActualTableName(tableName);
                    return;
                }
            }
        }
    }

    private static void readColumns(DatabaseMetaData metadata, String catalog, String schema, TableMetadata tableMetadata) throws Exception {
        try (ResultSet resultSet = metadata.getColumns(catalog, schema, tableMetadata.getActualTableName(), null)) {
            while (resultSet.next()) {
                tableMetadata.getColumns().add(resultSet.getString("COLUMN_NAME").toUpperCase());
            }
        }
    }

    private static void readPrimaryKeys(DatabaseMetaData metadata, String catalog, String schema, TableMetadata tableMetadata) throws Exception {
        try (ResultSet resultSet = metadata.getPrimaryKeys(catalog, schema, tableMetadata.getActualTableName())) {
            while (resultSet.next()) {
                tableMetadata.getPrimaryKeys().add(resultSet.getString("COLUMN_NAME").toUpperCase());
            }
        }
    }

    private static void readUniqueKeys(DatabaseMetaData metadata, String catalog, String schema, TableMetadata tableMetadata) {
        try (ResultSet resultSet = metadata.getIndexInfo(catalog, schema, tableMetadata.getActualTableName(), true, false)) {
            while (resultSet.next()) {
                String columnName = resultSet.getString("COLUMN_NAME");
                if (columnName != null) {
                    tableMetadata.getUniqueKeys().add(columnName.toUpperCase());
                }
            }
        } catch (Exception ignored) {
        }
    }
}