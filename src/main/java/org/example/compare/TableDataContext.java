package org.example.compare;

import org.example.utils.DBUtils;
import org.hibernate.Session;
import java.util.ArrayList;
import java.util.List;

public class TableDataContext {
    private final Session session;
    private final String tableName;
    private final String keyColumn;
    private final List<String> selectColumns;
    private final int batchSize;
    
    private final List<Object[]> currentBatch = new ArrayList<>();
    private int cursor = 0;
    private long index = 0;
    private boolean hasMore = true;

    public TableDataContext(Session session, String tableName, String keyColumn, List<String> compareColumns, int batchSize) {
        this.session = session;
        this.tableName = tableName;
        this.keyColumn = keyColumn.toUpperCase();
        this.selectColumns = new ArrayList<>();
        this.selectColumns.add(this.keyColumn);
        for (String col : compareColumns) {
            this.selectColumns.add(col.toUpperCase());
        }
        this.batchSize = batchSize;
    }

    public Object[] peek() {
        if (cursor >= currentBatch.size() && hasMore) {
            fetchNextBatch();
        }
        if (cursor < currentBatch.size()) {
            return currentBatch.get(cursor);
        }
        return null;
    }

    public void poll() {
        Object[] row = peek();
        if (row != null) {
            cursor++;
        }
    }

    private void fetchNextBatch() {
        List<Object[]> batch = DBUtils.fetchDataBatch(
            session, tableName, selectColumns, keyColumn, index, batchSize
        );
        currentBatch.clear();
        currentBatch.addAll(batch);

        cursor = 0;
        index += currentBatch.size();
        if (currentBatch.size() < batchSize) {
            hasMore = false;
        }
    }
}
