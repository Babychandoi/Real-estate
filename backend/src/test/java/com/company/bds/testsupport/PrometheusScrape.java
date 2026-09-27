package com.company.bds.testsupport;

import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Parses {@code /actuator/prometheus} (text exposition format) so tests can evaluate the proposed alert expressions on the
 * exact series Prometheus would scrape. Requires {@code @AutoConfigureObservability} on the test class.
 */
public final class PrometheusScrape {
    private static final Pattern SAMPLE = Pattern.compile("^([a-zA-Z_:][a-zA-Z0-9_:]*)(\\{([^}]*)})?\\s+(\\S+).*$");
    private static final Pattern LABEL = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)=\"((?:[^\"\\\\]|\\\\.)*)\"");

    public record Sample(String name, Map<String, String> labels, double value) {}

    private final List<Sample> samples;

    private PrometheusScrape(List<Sample> samples) {
        this.samples = samples;
    }

    public static PrometheusScrape of(MockMvc mockMvc) throws Exception {
        String body = mockMvc.perform(get("/actuator/prometheus")).andReturn().getResponse().getContentAsString();
        List<Sample> samples = new ArrayList<>();
        for (String line : body.split("\n")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            Matcher matcher = SAMPLE.matcher(line.trim());
            if (!matcher.matches()) continue;
            Map<String, String> labels = new LinkedHashMap<>();
            if (matcher.group(3) != null) {
                Matcher label = LABEL.matcher(matcher.group(3));
                while (label.find()) labels.put(label.group(1), label.group(2));
            }
            samples.add(new Sample(matcher.group(1), labels, parse(matcher.group(4))));
        }
        return new PrometheusScrape(samples);
    }

    /** Value of the series with this name whose labels include the given ones (e.g. {@code queue=email}). */
    public OptionalDouble value(String name, String... labelPairs) {
        Map<String, String> wanted = new LinkedHashMap<>();
        for (int i = 0; i + 1 < labelPairs.length; i += 2) wanted.put(labelPairs[i], labelPairs[i + 1]);
        return samples.stream()
                .filter(sample -> sample.name().equals(name))
                .filter(sample -> wanted.entrySet().stream().allMatch(entry -> entry.getValue().equals(sample.labels().get(entry.getKey()))))
                .mapToDouble(Sample::value)
                .findFirst();
    }

    /** {@code sum(name)} over all series of the metric (0 when absent, like Prometheus' {@code sum} over nothing plus absent()). */
    public double sum(String name) {
        return samples.stream().filter(sample -> sample.name().equals(name)).mapToDouble(Sample::value).sum();
    }

    private static double parse(String value) {
        return switch (value) {
            case "NaN" -> Double.NaN;
            case "+Inf" -> Double.POSITIVE_INFINITY;
            case "-Inf" -> Double.NEGATIVE_INFINITY;
            default -> Double.parseDouble(value);
        };
    }
}
