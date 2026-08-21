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

    public static TableConfig parse(String line, int mode) {
        if (line == null) return null;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }

        String[] parts = trimmed.split("\\|", -1);
        if (parts.length > 3) {
            throw new IllegalArgumentException("Table configuration line has too many parts: " + line);
        }

        String tableName = parts.length > 0 ? parts[0].trim() : "";
        String keyColumn = parts.length > 1 ? parts[1].trim() : "";

        if ((mode == 1 || mode == 2) && tableName.isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be empty: " + line);
        }
        if (mode == 1 && keyColumn.isEmpty()) {
            throw new IllegalArgumentException("Comparison key cannot be empty: " + line);
        }

        List<String> ignoreColumns = new ArrayList<>();
        String ignoreColsPart = parts.length > 2 ? parts[2].trim() : "";
        if (!ignoreColsPart.isEmpty()) {
            String[] cols = ignoreColsPart.split(",");
            for (String col : cols) {
                if (!col.trim().isEmpty()) {
                    ignoreColumns.add(col.trim());
                }
            }
        }

        return new TableConfig(tableName, keyColumn, ignoreColumns, trimmed);
    }
}
