package com.company.bds.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.callbacks.Callback;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rewrites every {@code $ref} of an {@link OpenAPI} document in place, on the live object graph (never through a JSON
 * round trip — see {@link OpenApiSchemaNames}). Walks every place a {@link Schema} can appear: components, path
 * parameters, request bodies, responses, headers and callbacks, following nested schemas ({@code properties},
 * {@code items}, {@code additionalProperties}, {@code allOf}/{@code oneOf}/{@code anyOf}/{@code not}).
 */
final class SchemaRefRewriter {
    private static final String REF_PREFIX = "#/components/schemas/";

    private SchemaRefRewriter() {}

    static void rewrite(OpenAPI openApi, Map<String, String> renames) {
        Set<Schema<?>> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        if (openApi.getComponents() != null) {
            forEach(openApi.getComponents().getSchemas(), schema -> schema(schema, renames, visited));
            forEach(openApi.getComponents().getResponses(), response -> response(response, renames, visited));
            forEach(openApi.getComponents().getRequestBodies(), body -> requestBody(body, renames, visited));
            forEach(openApi.getComponents().getParameters(), parameter -> parameter(parameter, renames, visited));
            forEach(openApi.getComponents().getHeaders(), header -> header(header, renames, visited));
        }
        if (openApi.getPaths() != null) {
            openApi.getPaths().values().forEach(item -> pathItem(item, renames, visited));
        }
    }

    private static void pathItem(PathItem item, Map<String, String> renames, Set<Schema<?>> visited) {
        item.readOperations().forEach(operation -> operation(operation, renames, visited));
    }

    private static void operation(Operation operation, Map<String, String> renames, Set<Schema<?>> visited) {
        if (operation.getParameters() != null) operation.getParameters().forEach(p -> parameter(p, renames, visited));
        requestBody(operation.getRequestBody(), renames, visited);
        forEachResponses(operation.getResponses(), renames, visited);
        forEach(operation.getCallbacks(), callback -> callback(callback, renames, visited));
    }

    private static void callback(Callback callback, Map<String, String> renames, Set<Schema<?>> visited) {
        if (callback == null) return;
        callback.values().forEach(item -> pathItem(item, renames, visited));
    }

    private static void forEachResponses(ApiResponses responses, Map<String, String> renames, Set<Schema<?>> visited) {
        if (responses == null) return;
        responses.values().forEach(response -> response(response, renames, visited));
    }

    private static void response(ApiResponse response, Map<String, String> renames, Set<Schema<?>> visited) {
        if (response == null) return;
        content(response.getContent(), renames, visited);
        forEach(response.getHeaders(), header -> header(header, renames, visited));
    }

    private static void header(Header header, Map<String, String> renames, Set<Schema<?>> visited) {
        if (header == null) return;
        schema(header.getSchema(), renames, visited);
        content(header.getContent(), renames, visited);
    }

    private static void requestBody(RequestBody body, Map<String, String> renames, Set<Schema<?>> visited) {
        if (body == null) return;
        content(body.getContent(), renames, visited);
    }

    private static void parameter(Parameter parameter, Map<String, String> renames, Set<Schema<?>> visited) {
        if (parameter == null) return;
        schema(parameter.getSchema(), renames, visited);
        content(parameter.getContent(), renames, visited);
    }

    private static void content(Content content, Map<String, String> renames, Set<Schema<?>> visited) {
        if (content == null) return;
        for (MediaType mediaType : content.values()) {
            schema(mediaType.getSchema(), renames, visited);
            forEach(mediaType.getEncoding(), encoding -> forEach(encoding.getHeaders(), h -> header(h, renames, visited)));
        }
    }

    @SuppressWarnings("unchecked")
    private static void schema(Schema<?> schema, Map<String, String> renames, Set<Schema<?>> visited) {
        if (schema == null || !visited.add(schema)) return;
        if (schema.get$ref() != null) schema.set$ref(rewriteRef(schema.get$ref(), renames));
        schema(schema.getItems(), renames, visited);
        if (schema.getAdditionalProperties() instanceof Schema<?> additional) schema(additional, renames, visited);
        forEach(schema.getProperties(), value -> schema((Schema<?>) value, renames, visited));
        forEachList(schema.getAllOf(), renames, visited);
        forEachList(schema.getOneOf(), renames, visited);
        forEachList(schema.getAnyOf(), renames, visited);
        schema(schema.getNot(), renames, visited);
    }

    private static void forEachList(List<Schema> list, Map<String, String> renames, Set<Schema<?>> visited) {
        if (list != null) list.forEach(s -> schema(s, renames, visited));
    }

    private static <V> void forEach(Map<String, V> map, java.util.function.Consumer<V> action) {
        if (map != null) map.values().forEach(action);
    }

    private static String rewriteRef(String ref, Map<String, String> renames) {
        if (!ref.startsWith(REF_PREFIX)) return ref;
        String name = ref.substring(REF_PREFIX.length());
        return REF_PREFIX + renames.getOrDefault(name, name);
    }
}
