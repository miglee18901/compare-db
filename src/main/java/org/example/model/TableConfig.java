package org.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TableConfig {
    private final String tableName;
    private final String keyColumn;
    private final List<String> ignoreColumns;
    private final String rawLine;

    public TableConfig(String tableName, String keyColumn, List<String> ignoreColumns, String rawLine) {
        this.tableName = tableName;
        this.keyColumn = keyColumn;
        this.ignoreColumns = ignoreColumns != null ? ignoreColumns : Collections.emptyList();
        this.rawLine = rawLine;
    }

    public String getTableName() {
        return tableName;
    }

    public String getKeyColumn() {
        return keyColumn;
    }

    public List<String> getIgnoreColumns() {
        return ignoreColumns;
    }

    public String getRawLine() {
        return rawLine;
    }

    public static TableConfig parse(String line) {
        if (line == null) return null;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }

        String[] parts = trimmed.split("\\|", -1);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Table configuration line is malformed (needs at least table name and key column): " + line);
        }

        String tableName = parts[0].trim();
        String keyColumn = parts[1].trim();
        
        List<String> ignoreColumns = new ArrayList<>();
        if (parts.length > 2 && !parts[2].trim().isEmpty()) {
            String[] cols = parts[2].trim().split(",");
            for (String col : cols) {
                if (!col.trim().isEmpty()) {
                    ignoreColumns.add(col.trim());
                }
            }
        }

        if (tableName.isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be empty: " + line);
        }
        if (keyColumn.isEmpty()) {
            throw new IllegalArgumentException("Comparison key cannot be empty: " + line);
        }

        return new TableConfig(tableName, keyColumn, ignoreColumns, trimmed);
    }
}
