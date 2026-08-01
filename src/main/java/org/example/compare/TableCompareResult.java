package org.example.compare;

import java.util.ArrayList;
import java.util.List;

public class TableCompareResult {
    private final String tableName;
    private long total = 0;
    private long match = 0;
    private long notMatch = 0;
    private final List<String> diffDetails = new ArrayList<>();
    private final List<String> missingColumnsInCrbt16M = new ArrayList<>();
    private final List<String> missingColumnsInCrbt21M = new ArrayList<>();

    public TableCompareResult(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }

    public long getTotal() {
        return total;
    }

    public long getMatch() {
        return match;
    }

    public long getNotMatch() {
        return notMatch;
    }

    public List<String> getDiffDetails() {
        return diffDetails;
    }

    public void incrementMatch() {
        match++;
        total++;
    }

    public void incrementNotMatch() {
        notMatch++;
        total++;
    }

    public void addDiffDetail(String detail) {
        diffDetails.add(detail);
    }

    public List<String> getMissingColumnsInCrbt16M() {
        return missingColumnsInCrbt16M;
    }

    public List<String> getMissingColumnsInCrbt21M() {
        return missingColumnsInCrbt21M;
    }

    public void addMissingColumnInCrbt16M(String columnName) {
        missingColumnsInCrbt16M.add(columnName);
    }

    public void addMissingColumnInCrbt21M(String columnName) {
        missingColumnsInCrbt21M.add(columnName);
    }
}
