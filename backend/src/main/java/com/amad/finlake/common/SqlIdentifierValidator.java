package com.amad.finlake.common;

import java.util.regex.Pattern;

/** Validates SQL identifiers before quoting to prevent SQL injection. */
public final class SqlIdentifierValidator {
    private static final Pattern SAFE_IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,127}$");

    private SqlIdentifierValidator() {}

    public static String requireValid(String identifier, String label) {
        if (identifier == null || !SAFE_IDENT.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Invalid " + label + " identifier");
        }
        return identifier;
    }
}
