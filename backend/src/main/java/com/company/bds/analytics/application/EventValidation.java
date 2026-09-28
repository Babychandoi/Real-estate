package com.company.bds.analytics.application;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Catalog checks shared by web ingestion and server recording. */
final class EventValidation {
    private EventValidation() {}

    /** Problems of a properties object against the event definition; empty when valid. */
    static List<String> propertyProblems(EventCatalog.EventDefinition definition, JsonNode properties) {
        List<String> problems = new ArrayList<>();
        if (properties == null || properties.isNull()) properties = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        if (!properties.isObject()) {
            problems.add("properties must be an object");
            return problems;
        }
        for (Iterator<Map.Entry<String, JsonNode>> it = properties.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> field = it.next();
            EventCatalog.PropertyRule rule = definition.properties().get(field.getKey());
            if (rule == null) {
                problems.add("property '" + field.getKey() + "' is not allowed for " + definition.name());
            } else if (field.getValue().isNull()) {
                if (!rule.nullable()) problems.add("property '" + field.getKey() + "' must not be null");
            } else if (!rule.accepts().test(field.getValue())) {
                problems.add("property '" + field.getKey() + "' must be " + rule.expectation());
            }
        }
        for (EventCatalog.PropertyRule rule : definition.properties().values()) {
            if (rule.required() && !properties.has(rule.name())) problems.add("property '" + rule.name() + "' is required");
        }
        return problems;
    }
}
