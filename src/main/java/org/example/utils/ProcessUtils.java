package org.example.utils;

public class ProcessUtils {

    public static int compareKeys(Object k1, Object k2) {
        if (k1 == null && k2 == null) return 0;
        if (k1 == null) return -1;
        if (k2 == null) return 1;

        String s1 = k1.toString().trim();
        String s2 = k2.toString().trim();

        // Try numeric comparison first to align with database numeric sort order
        try {
            double d1 = Double.parseDouble(s1);
            double d2 = Double.parseDouble(s2);
            return Double.compare(d1, d2);
        } catch (NumberFormatException e) {
            // Fallback to lexicographical comparison
            return s1.compareTo(s2);
        }
    }

    public static boolean valuesEqual(Object v1, Object v2) {
        return java.util.Objects.equals(v1, v2);
    }

    public static String formatValue(Object val) {
        if (val == null) {
            return "null";
        }
        return "'" + val.toString().replace("'", "''") + "'";
    }
}
