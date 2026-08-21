package org.example.utils;

import java.math.BigDecimal;

public class ProcessUtils {

    public static int compareKeys(Object k1, Object k2) {
        if (k1 == null && k2 == null) return 0;
        if (k1 == null) return -1;
        if (k2 == null) return 1;

        if (k1 instanceof Number && k2 instanceof Number) {
            return new BigDecimal(k1.toString()).compareTo(new BigDecimal(k2.toString()));
        }
        return k1.toString().trim().compareTo(k2.toString().trim());
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
