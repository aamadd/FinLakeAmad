package com.amad.finlake;

import com.amad.finlake.common.SqlIdentifierValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlIdentifierValidatorTest {

    @Test
    void acceptsValidIdentifiers() {
        assertEquals("amad_transactions", SqlIdentifierValidator.requireValid("amad_transactions", "table"));
        assertEquals("dbo", SqlIdentifierValidator.requireValid("dbo", "schema"));
    }

    @Test
    void rejectsInjectionAndEmptyIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> SqlIdentifierValidator.requireValid("users; DROP TABLE x", "table"));
        assertThrows(IllegalArgumentException.class, () -> SqlIdentifierValidator.requireValid("", "table"));
        assertThrows(IllegalArgumentException.class, () -> SqlIdentifierValidator.requireValid(null, "schema"));
    }
}
