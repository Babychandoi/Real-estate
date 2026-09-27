package com.company.bds.analytics.domain;

import java.util.regex.Pattern;

/**
 * Simple User-Agent heuristic for {@code is_bot}: crawlers, link previewers, monitoring, headless/automated browsers and
 * HTTP libraries. Events are still stored (flagged) so S8 can refine the rules and dashboards can exclude them.
 */
public final class BotDetector {
    private static final Pattern AUTOMATION = Pattern.compile("(?i)(bot\\b|bot/|crawl|spider|slurp|bingpreview|facebookexternalhit"
            + "|embedly|preview|headless|phantomjs|puppeteer|playwright|selenium|webdriver|lighthouse|pagespeed|gtmetrix|pingdom"
            + "|uptime|statuscake|curl/|wget/|python-requests|python-urllib|aiohttp|httpclient|okhttp|java/|go-http-client"
            + "|axios/|node-fetch|undici|libwww|scrapy|postman)");

    private BotDetector() {}

    public static boolean isBot(String userAgent) {
        if (userAgent == null || userAgent.isBlank() || userAgent.length() < 12) return true; // real browsers always send one
        return AUTOMATION.matcher(userAgent).find();
    }
}
