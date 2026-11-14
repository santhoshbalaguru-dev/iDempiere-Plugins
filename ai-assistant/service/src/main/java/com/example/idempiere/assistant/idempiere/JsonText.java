package com.example.idempiere.assistant.idempiere;

import com.fasterxml.jackson.databind.JsonNode;

/** Helpers for reading iDempiere REST records, where foreign keys come back as objects. */
public final class JsonText {

    private JsonText() {}

    /** Text of a field; for reference fields returns the human-readable identifier. */
    public static String of(JsonNode record, String field) {
        JsonNode n = record.path(field);
        if (n.isMissingNode() || n.isNull()) {
            return null;
        }
        if (n.isObject()) {
            return n.path("identifier").asText(null);
        }
        return n.asText();
    }
}
