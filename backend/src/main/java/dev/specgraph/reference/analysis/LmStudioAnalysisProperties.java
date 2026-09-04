package dev.specgraph.reference.analysis;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("specgraph.analysis.local")
record LmStudioAnalysisProperties(String baseUrl, String model, String apiKey, Duration timeout) {
    LmStudioAnalysisProperties {
        baseUrl = normalize(baseUrl);
        model = normalize(model);
        apiKey = apiKey == null ? "" : apiKey.trim();
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
    }

    String validatedBaseUrl() {
        if (baseUrl == null) {
            throw new IllegalStateException("SPECGRAPH_LOCAL_BASE_URL is required when backend=local");
        }
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("SPECGRAPH_LOCAL_BASE_URL must be a valid absolute HTTP(S) URL", exception);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https"))
                || uri.getHost() == null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalStateException("SPECGRAPH_LOCAL_BASE_URL must be a valid absolute HTTP(S) URL");
        }
        if (!isNetworkLocal(uri.getHost())) {
            throw new IllegalStateException(
                    "SPECGRAPH_LOCAL_BASE_URL must target a loopback or private-network LM Studio host");
        }
        return baseUrl;
    }

    String validatedModel() {
        if (model == null) {
            throw new IllegalStateException("SPECGRAPH_LOCAL_MODEL is required when backend=local");
        }
        return model;
    }

    Duration validatedTimeout() {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException("SPECGRAPH_LOCAL_TIMEOUT must be positive");
        }
        return timeout;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isNetworkLocal(String host) {
        String value = host.toLowerCase(Locale.ROOT);
        if (value.equals("localhost") || value.endsWith(".local")) {
            return true;
        }
        if (value.contains(":")) {
            return value.equals("::1")
                    || value.startsWith("fc")
                    || value.startsWith("fd")
                    || value.matches("fe[89ab].*");
        }
        if (!value.matches("(?:\\d{1,3}\\.){3}\\d{1,3}")) {
            return false;
        }
        int[] octets = java.util.Arrays.stream(value.split("\\."))
                .mapToInt(Integer::parseInt)
                .toArray();
        if (java.util.Arrays.stream(octets).anyMatch(octet -> octet > 255)) {
            return false;
        }
        return octets[0] == 10
                || octets[0] == 127
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168)
                || (octets[0] == 169 && octets[1] == 254);
    }
}
