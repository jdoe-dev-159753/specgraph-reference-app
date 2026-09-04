package dev.specgraph.reference.analysis;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
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
        return isNetworkLocal(host, InetAddress::getAllByName);
    }

    static boolean isNetworkLocal(String host, HostAddressResolver resolver) {
        String value = host.toLowerCase(Locale.ROOT);
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.contains(":")) {
            return isLocalIpv6(value);
        }
        if (!value.matches("(?:\\d{1,3}\\.){3}\\d{1,3}")) {
            return resolvesOnlyToNetworkLocalAddresses(value, resolver);
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

    private static boolean resolvesOnlyToNetworkLocalAddresses(String value, HostAddressResolver resolver) {
        try {
            InetAddress[] addresses = resolver.resolve(value);
            return addresses.length > 0
                    && java.util.Arrays.stream(addresses).allMatch(LmStudioAnalysisProperties::isLocalAddress);
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private static boolean isLocalAddress(InetAddress address) {
        if (address instanceof Inet6Address) {
            byte first = address.getAddress()[0];
            boolean uniqueLocal = (first & 0xfe) == 0xfc;
            return address.isLoopbackAddress() || address.isLinkLocalAddress() || uniqueLocal;
        }
        byte[] octets = address.getAddress();
        int first = Byte.toUnsignedInt(octets[0]);
        int second = Byte.toUnsignedInt(octets[1]);
        return first == 10
                || first == 127
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 169 && second == 254);
    }

    private static boolean isLocalIpv6(String value) {
        try {
            InetAddress address = InetAddress.getByName(value);
            return address instanceof Inet6Address && isLocalAddress(address);
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    @FunctionalInterface
    interface HostAddressResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
