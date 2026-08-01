package org.example.compare;

import org.example.utils.DBUtils;
import org.hibernate.Session;
import java.util.LinkedHashSet;
import java.util.Set;

public class TableMetadata {

    private boolean exists;
    private String actualTableName;
    private final Set<String> columns = new LinkedHashSet<>();
    private final Set<String> primaryKeys = new LinkedHashSet<>();
    private final Set<String> uniqueKeys = new LinkedHashSet<>();

    public boolean isExists() {
        return exists;
    }

    public void setExists(boolean exists) {
        this.exists = exists;
    }

    public String getActualTableName() {
        return actualTableName;
    }

    public void setActualTableName(String actualTableName) {
        this.actualTableName = actualTableName;
    }

    public Set<String> getColumns() {
        return columns;
    }

    public Set<String> getPrimaryKeys() {
        return primaryKeys;
    }

    public Set<String> getUniqueKeys() {
        return uniqueKeys;
    }

    public static TableMetadata getMetadata(Session session, String inputTableName) {
        return DBUtils.getTableMetadata(session, inputTableName);
    }
}
