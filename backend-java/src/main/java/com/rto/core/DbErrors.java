package com.rto.core;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns MySQL errors into business-level ApiExceptions; raw SQL never reaches clients. */
public final class DbErrors {
    private DbErrors() {}

    private static final Pattern KEY = Pattern.compile("for key '(?:[\\w.]+\\.)?(\\w+)'");
    private static final Pattern CHECK = Pattern.compile("constraint '(\\w+)'");

    public static SQLException rootSql(Throwable t) {
        SQLException found = null;
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SQLException s) found = s;
            if (c.getCause() == c) break;
        }
        return found;
    }

    /** @return the mapped exception, or null if the throwable is not a recognised database error. */
    public static ApiException translate(Throwable t) {
        SQLException sql = rootSql(t);
        if (sql == null) return null;
        int errno = sql.getErrorCode();
        String msg = String.valueOf(sql.getMessage());
        switch (errno) {
            case 1062 -> {
                Matcher m = KEY.matcher(msg);
                String key = m.find() ? m.group(1) : "";
                return ApiException.conflict("DUPLICATE",
                        DbMessages.UNIQUE.getOrDefault(key, "A record with the same unique value already exists"));
            }
            case 3819, 4025 -> {
                Matcher m = CHECK.matcher(msg);
                String key = m.find() ? m.group(1) : "";
                String code = key.equals("chk_slot_capacity") ? "SLOT_FULL" : "CHECK_VIOLATION";
                return ApiException.conflict(code, DbMessages.CHECK.getOrDefault(key, "A data integrity rule was violated"));
            }
            case 1452 -> { return ApiException.badRequest("INVALID_REFERENCE", "A referenced record does not exist"); }
            case 1451 -> {
                return ApiException.conflict("IN_USE",
                        "This record is referenced by other records and cannot be changed or removed");
            }
            case 1213, 1205 -> {
                return ApiException.conflict("RETRY", "The system is busy with a competing update; please retry");
            }
            case 1048, 1364 -> { return ApiException.badRequest("MISSING_VALUE", "A required value is missing"); }
            case 1264, 1265, 1366, 1406 -> {
                return ApiException.badRequest("INVALID_VALUE", "A value is out of range or has the wrong format");
            }
            default -> { return null; }
        }
    }
}
