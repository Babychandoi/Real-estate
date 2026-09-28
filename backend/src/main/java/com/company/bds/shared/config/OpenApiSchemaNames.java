package com.company.bds.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Unique, stable schema names (audit F22.4). Springdoc names a schema after the simple class name, so two DTOs called
 * e.g. {@code TeamMember} or {@code Freshness} in different modules silently became one schema and the generated
 * frontend types were wrong. With {@code springdoc.use-fqn=true} every schema is first named by its fully qualified
 * class; this customizer then shortens each name back to the simple name when that is unique, otherwise to
 * {@code <Module><Outer?><Simple>} (e.g. {@code LeadTeamMember}), and rewrites every {@code $ref} — in the live model,
 * never through a JSON round trip (deserialising a {@code Schema} back from generic JSON drops which subtype it was,
 * e.g. turning an enum query parameter's {@code type: string} into the base class's default {@code type: object}).
 * The result depends only on the set of classes, never on scan order.
 */
public final class OpenApiSchemaNames implements OpenApiCustomizer {
    private static final String ROOT = "com.company.bds.";

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) return;
        Map<String, String> renames = renames(List.copyOf(openApi.getComponents().getSchemas().keySet()));
        if (renames.values().stream().distinct().count() != renames.size()) {
            throw new IllegalStateException("OpenAPI schema names are not unique after shortening: " + renames);
        }
        SchemaRefRewriter.rewrite(openApi, renames);
        Map<String, io.swagger.v3.oas.models.media.Schema> schemas = new TreeMap<>();
        openApi.getComponents().getSchemas().forEach((name, schema) -> schemas.put(renames.getOrDefault(name, name), schema));
        openApi.getComponents().setSchemas(new java.util.LinkedHashMap<>(schemas));
    }

    static Map<String, String> renames(List<String> qualifiedNames) {
        Map<String, List<String>> bySimple = qualifiedNames.stream().collect(Collectors.groupingBy(OpenApiSchemaNames::simple));
        Map<String, String> candidates = new HashMap<>();
        for (String name : qualifiedNames) {
            String simple = simple(name);
            if (bySimple.get(simple).size() == 1) {
                candidates.put(name, simple);
                continue;
            }
            String qualified = module(name) + simple;
            long sameInModule = bySimple.get(simple).stream().filter(other -> (module(other) + simple).equals(qualified)).count();
            candidates.put(name, sameInModule == 1 ? qualified : module(name) + outer(name) + simple);
        }
        // Springdoc's own name for a generic wrapper (e.g. a paged result of a type also used bare) can still collide
        // with the plain type's candidate after the steps above; break the tie deterministically by declaration order
        // rather than fail the build over an edge case neither reader nor writer of the schema needs to disambiguate by
        // hand.
        Map<String, String> renames = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (String name : qualifiedNames.stream().sorted().toList()) {
            String candidate = candidates.get(name);
            int seen = counts.merge(candidate, 1, Integer::sum);
            renames.put(name, seen == 1 ? candidate : candidate + seen);
        }
        return renames;
    }

    /** {@code com.company.bds.lead.api.request.LeadCommandRequests$TeamMember} → {@code TeamMember}. */
    private static String simple(String name) {
        String last = name.substring(name.lastIndexOf('.') + 1);
        return last.substring(last.lastIndexOf('$') + 1);
    }

    private static String module(String name) {
        if (!name.startsWith(ROOT)) return "";
        String rest = name.substring(ROOT.length());
        String module = rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : "";
        return module.isEmpty() ? "" : module.substring(0, 1).toUpperCase(Locale.ROOT) + module.substring(1);
    }

    private static String outer(String name) {
        String last = name.substring(name.lastIndexOf('.') + 1);
        int nested = last.lastIndexOf('$');
        if (nested < 0) return "";
        String outer = last.substring(0, nested);
        return outer.substring(outer.lastIndexOf('$') + 1);
    }
}
