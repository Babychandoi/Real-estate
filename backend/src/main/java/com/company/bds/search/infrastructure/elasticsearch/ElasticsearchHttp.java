package com.company.bds.search.infrastructure.elasticsearch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Minimal Elasticsearch transport (JDK HTTP client, JSON strings in and out). Every call has an explicit time budget;
 * transport failures are raised as {@link EngineUnavailableException}, HTTP status handling is left to the caller.
 */
@Component
public class ElasticsearchHttp {
    public record Response(int status, String body) {
        public boolean ok() { return status >= 200 && status < 300; }
    }

    /** Timeout, connection failure or interrupted call: the engine is unhealthy. */
    public static class EngineUnavailableException extends RuntimeException {
        public EngineUnavailableException(String message, Throwable cause) { super(message, cause); }
    }

    private final HttpClient http;
    private final String base;

    public ElasticsearchHttp(@Value("${spring.elasticsearch.uris:http://localhost:9200}") String uris) {
        this.base = uris.split(",")[0].trim().replaceAll("/+$", "");
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(800)).build();
    }

    public Response send(String method, String path, String body, Duration timeout) {
        return send(method, path, body, "application/json", timeout);
    }

    public Response send(String method, String path, String body, String contentType, Duration timeout) {
        HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(timeout)
                .header("Content-Type", contentType)
                .method(method, publisher)
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (HttpTimeoutException ex) {
            throw new EngineUnavailableException("Elasticsearch timed out after " + timeout.toMillis() + " ms", ex);
        } catch (IOException ex) {
            throw new EngineUnavailableException("Elasticsearch unreachable: " + ex.getClass().getSimpleName(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EngineUnavailableException("Interrupted while calling Elasticsearch", ex);
        }
    }
}
