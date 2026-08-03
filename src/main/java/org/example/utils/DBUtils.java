package org.example.utils;

import org.example.compare.TableMetadata;
import org.hibernate.Session;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class DBUtils {

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

    public static List<Object[]> fetchDataBatch(Session session, String tableName, List<String> selectColumns, String keyColumn, long offset, int limit) {
        Connection connection = session.connection();
        List<Object[]> batch = new ArrayList<>();
        String sql = "SELECT " + String.join(", ", selectColumns)
                + " FROM " + tableName
                + " ORDER BY " + keyColumn + " ASC"
                + " LIMIT ? OFFSET ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
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