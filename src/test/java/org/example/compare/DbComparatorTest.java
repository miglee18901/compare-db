package org.example.compare;

import org.example.model.TableConfig;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DbComparatorTest {

    private SessionFactory sf16M;
    private SessionFactory sf21M;
    private Session session16M;
    private Session session21M;

    private SessionFactory createH2SessionFactory(String dbName) {
        Configuration cfg = new Configuration();
        cfg.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
        cfg.setProperty("hibernate.connection.url", "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        cfg.setProperty("hibernate.connection.username", "sa");
        cfg.setProperty("hibernate.connection.password", "");
        cfg.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        cfg.setProperty("hibernate.hbm2ddl.auto", "update");
        cfg.setProperty("hibernate.show_sql", "false");
        return cfg.buildSessionFactory();
    }

    @BeforeEach
    public void setUp() throws Exception {
        sf16M = createH2SessionFactory("crbt16m");
        sf21M = createH2SessionFactory("crbt21m");

        session16M = sf16M.openSession();
        session21M = sf21M.openSession();

        // Create tables and schema in both environments (drop if exists to support multiple test runs)
        try (Statement sm = session16M.connection().createStatement()) {
                sm.execute("DROP TABLE IF EXISTS SUBS_INFO");
                sm.execute("DROP TABLE IF EXISTS PROD_SPEC");
                sm.execute("CREATE TABLE SUBS_INFO (" +
                           "  MSISDN VARCHAR(50) PRIMARY KEY," +
                           "  TONE_CODE VARCHAR(50)," +
                           "  SINGER VARCHAR(50)," +
                           "  IGNORECOLUMN1 VARCHAR(50)," +
                           "  IGNORECOLUMN2 VARCHAR(50)" +
                           ")");
                sm.execute("CREATE TABLE PROD_SPEC (" +
                           "  ID INT PRIMARY KEY," +
                           "  PROD_SPEC_ID VARCHAR(50) UNIQUE," +
                           "  NAME VARCHAR(50)" +
                           ")");
        }

        try (Statement sm = session21M.connection().createStatement()) {
                sm.execute("DROP TABLE IF EXISTS SUBS_INFO");
                sm.execute("DROP TABLE IF EXISTS PROD_SPEC");
                sm.execute("CREATE TABLE SUBS_INFO (" +
                           "  MSISDN VARCHAR(50) PRIMARY KEY," +
                           "  TONE_CODE VARCHAR(50)," +
                           "  SINGER VARCHAR(50)," +
                           "  IGNORECOLUMN1 VARCHAR(50)," +
                           "  IGNORECOLUMN2 VARCHAR(50)" +
                           ")");
                sm.execute("CREATE TABLE PROD_SPEC (" +
                           "  ID INT PRIMARY KEY," +
                           "  PROD_SPEC_ID VARCHAR(50) UNIQUE," +
                           "  NAME VARCHAR(50)" +
                           ")");
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (session16M != null) session16M.close();
        if (session21M != null) session21M.close();
        if (sf16M != null) sf16M.close();
        if (sf21M != null) sf21M.close();
    }

    @Test
    public void testTableConfigParsing() {
        TableConfig c1 = TableConfig.parse("SUBS_INFO|MSISDN|ignoreColumn1,ignoreColumn2");
        assertNotNull(c1);
        assertEquals("SUBS_INFO", c1.getTableName());
        assertEquals("MSISDN", c1.getKeyColumn());
        assertEquals(2, c1.getIgnoreColumns().size());
        assertTrue(c1.getIgnoreColumns().contains("ignoreColumn1"));
        assertTrue(c1.getIgnoreColumns().contains("ignoreColumn2"));

        TableConfig c2 = TableConfig.parse("PROD_SPEC|PROD_SPEC_ID|");
        assertNotNull(c2);
        assertEquals("PROD_SPEC", c2.getTableName());
        assertEquals("PROD_SPEC_ID", c2.getKeyColumn());
        assertTrue(c2.getIgnoreColumns().isEmpty());

        // Skip blank/comment lines
        assertNull(TableConfig.parse(""));
        assertNull(TableConfig.parse("  "));
        assertNull(TableConfig.parse("# COMMENT CONTEXT"));

        // Invalid forms should raise exception
        assertThrows(IllegalArgumentException.class, () -> TableConfig.parse("INVALID_LINE"));
        assertThrows(IllegalArgumentException.class, () -> TableConfig.parse("|KEY|"));
        assertThrows(IllegalArgumentException.class, () -> TableConfig.parse("TAB||"));
    }

    @Test
    public void testMode1CompareBasic() throws Exception {
        // Populating environment 1 (CRBT16M)
        try (Statement sm = session16M.connection().createStatement()) {
                // Key 1: Perfect match
                sm.execute("INSERT INTO SUBS_INFO VALUES('1', 'T1', 'S1', 'I1', 'I2')");
                // Key 2: Mismatch in SINGER field
                sm.execute("INSERT INTO SUBS_INFO VALUES('2', 'T2', 'S2_old', 'I1', 'I2')");
                // Key 3: Exists only in CRBT16M
                sm.execute("INSERT INTO SUBS_INFO VALUES('3', 'T3', 'S3', 'I1', 'I2')");
        }

        // Populating environment 2 (CRBT21M)
        try (Statement sm = session21M.connection().createStatement()) {
                // Key 1: Perfect match (ignore columns have different values, which should be ignored!)
                sm.execute("INSERT INTO SUBS_INFO VALUES('1', 'T1', 'S1', 'I1_new', 'I2_new')");
                // Key 2: Mismatch in SINGER field
                sm.execute("INSERT INTO SUBS_INFO VALUES('2', 'T2', 'S2_new', 'I1', 'I2')");
                // Key 4: Exists only in CRBT21M
                sm.execute("INSERT INTO SUBS_INFO VALUES('4', 'T4', 'S4', 'I1', 'I2')");
        }

        TableConfig config = TableConfig.parse("SUBS_INFO|MSISDN|ignoreColumn1,ignoreColumn2");
        List<String> report = DbComparator.compare(session16M, session21M, Arrays.asList(config), 1, 1000);

        assertNotNull(report);
        // Find the summary line
        String summaryLine = report.stream().filter(l -> l.contains("TABLE = SUBS_INFO")).findFirst().orElse(null);
        assertNotNull(summaryLine);
        
        // Assert sums: keys 1, 2, 3, 4 are compared -> total = 4
        // Key 1: matches (since ignore columns values differences are not compared)
        // Key 2: mismatch (SINGER)
        // Key 3: missing in CRBT21M (mismatch)
        // Key 4: missing in CRBT16M (mismatch)
        // TOTAL = 4, MATCH = 1, NOT_MATCH = 3
        assertTrue(summaryLine.contains("TOTAL = 4"));
        assertTrue(summaryLine.contains("MATCH = 1"));
        assertTrue(summaryLine.contains("NOT_MATCH = 3"));

        // Verify details presence
        assertTrue(report.contains("MSISDN key = '2'"));
        assertTrue(report.contains("SINGER (CRBT16M) = 'S2_old', SINGER (CRBT21M) = 'S2_new'"));
        assertTrue(report.contains("MSISDN key = 3, CRBT21M = <does not exist>"));
        assertTrue(report.contains("MSISDN key = 4, CRBT16M = <does not exist>"));
    }

    @Test
    public void testMode1UniqueKeyLogic() throws Exception {
        // For PROD_SPEC table, the comparison key is PROD_SPEC_ID (which is a unique key).
        // Since it's a unique key (unikey), we should strip the primary key ('ID') from comparison.
        try (Statement sm = session16M.connection().createStatement()) {
                // Key P1: Match. But ID primary key is different (1 vs 2). Since ID is PK, it must be ignored!
                sm.execute("INSERT INTO PROD_SPEC VALUES(1, 'P1', 'Name1')");
                // Key P2: Mismatch in NAME
                sm.execute("INSERT INTO PROD_SPEC VALUES(3, 'P2', 'Name2_old')");
        }

        try (Statement sm = session21M.connection().createStatement()) {
                // Key P1: Match (with different ID)
                sm.execute("INSERT INTO PROD_SPEC VALUES(2, 'P1', 'Name1')");
                // Key P2: Mismatch in NAME
                sm.execute("INSERT INTO PROD_SPEC VALUES(4, 'P2', 'Name2_new')");
        }

        TableConfig config = TableConfig.parse("PROD_SPEC|PROD_SPEC_ID|");
        List<String> report = DbComparator.compare(session16M, session21M, Arrays.asList(config), 1, 1000);

        assertNotNull(report);
        String summaryLine = report.stream().filter(l -> l.contains("TABLE = PROD_SPEC")).findFirst().orElse(null);
        assertNotNull(summaryLine);

        // Verification:
        // P1 should be matching: ID pk differences are ignored!
        // P2 should be mismatching: NAME field differs
        // Total = 2, Match = 1, Not Match = 1
        assertTrue(summaryLine.contains("TOTAL = 2"));
        assertTrue(summaryLine.contains("MATCH = 1"));
        assertTrue(summaryLine.contains("NOT_MATCH = 1"));
    }

    @Test
    public void testMode2Comparison() throws Exception {
        try (Statement sm = session16M.connection().createStatement()) {
                sm.execute("INSERT INTO SUBS_INFO VALUES('1', 'T1', 'S1', 'I1', 'I2')");
                sm.execute("INSERT INTO SUBS_INFO VALUES('2', 'T2', 'S2', 'I1', 'I2')");
        }

        try (Statement sm = session21M.connection().createStatement()) {
                sm.execute("INSERT INTO SUBS_INFO VALUES('1', 'T1', 'S1', 'I1', 'I2')");
                sm.execute("INSERT INTO SUBS_INFO VALUES('2', 'T2', 'S2', 'I1', 'I2')");
                sm.execute("INSERT INTO SUBS_INFO VALUES('3', 'T3', 'S3', 'I1', 'I2')");
        }

        TableConfig config = TableConfig.parse("SUBS_INFO|MSISDN|");
        List<String> report = DbComparator.compare(session16M, session21M, Arrays.asList(config), 2, 1000);

        assertNotNull(report);
        assertTrue(report.contains("TABLE = SUBS_INFO, CRBT16M = 2, CRBT21M = 3"));
    }

    @Test
    public void testValidationErrors() {
        // Table not exist
        TableConfig invalidTable = TableConfig.parse("NON_EXISTENT|ID|");
        List<String> report1 = DbComparator.compare(session16M, session21M, Arrays.asList(invalidTable), 1, 1000);
        assertEquals(Arrays.asList("TABLE = NON_EXISTENT, CRBT16M = <does not exist>, CRBT21M = <does not exist>"), report1);

        // Key column not in table
        TableConfig invalidKey = TableConfig.parse("SUBS_INFO|NOT_EXISTENT|");
        List<String> report2 = DbComparator.compare(session16M, session21M, Arrays.asList(invalidKey), 1, 1000);
        assertTrue(report2.isEmpty());

        // Key column is in table but not PK or UK (SINGER is normal column)
        TableConfig invalidKeyRole = TableConfig.parse("SUBS_INFO|SINGER|");
        List<String> report3 = DbComparator.compare(session16M, session21M, Arrays.asList(invalidKeyRole), 1, 1000);
        assertTrue(report3.isEmpty());

        // Ignore column mismatch (same as key)
        TableConfig keyIgnoreOverlap = TableConfig.parse("SUBS_INFO|MSISDN|MSISDN");
        List<String> report4 = DbComparator.compare(session16M, session21M, Arrays.asList(keyIgnoreOverlap), 1, 1000);
        assertTrue(report4.isEmpty());

        // Ignore column not in table
        TableConfig ignoreNotExist = TableConfig.parse("SUBS_INFO|MSISDN|UNKNOWN_FIELD");
        List<String> report5 = DbComparator.compare(session16M, session21M, Arrays.asList(ignoreNotExist), 1, 1000);
        assertTrue(report5.isEmpty());
    }
    @Test
    public void testMissingTableIsReportedInBothModes() throws Exception {
        try (Statement sm = session16M.connection().createStatement()) {
            sm.execute("CREATE TABLE ONLY_IN_CRBT16M (ID INT PRIMARY KEY)");
        }

        TableConfig config = TableConfig.parse("ONLY_IN_CRBT16M|ID|");
        String expected = "TABLE = ONLY_IN_CRBT16M, CRBT21M = <does not exist>";

        assertEquals(Arrays.asList(expected), DbComparator.compare(session16M, session21M, Arrays.asList(config), 1, 1000));
        assertEquals(Arrays.asList(expected), DbComparator.compare(session16M, session21M, Arrays.asList(config), 2, 1000));
    }
}