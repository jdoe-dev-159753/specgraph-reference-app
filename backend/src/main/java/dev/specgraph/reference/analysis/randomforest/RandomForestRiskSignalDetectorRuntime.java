package dev.specgraph.reference.analysis.randomforest;

import dev.specgraph.reference.analysis.RiskSignalDetectorPort;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Loads the one immutable, provenance-pinned Random Forest model distributed with the application. */
public final class RandomForestRiskSignalDetectorRuntime {
    static final String MODEL_RESOURCE =
            "dev/specgraph/reference/analysis/randomforest/synthetic-review-random-forest-v1.pb";
    static final String MANIFEST_RESOURCE =
            "dev/specgraph/reference/analysis/randomforest/synthetic-review-random-forest-v1.properties";
    static final String EXPECTED_MANIFEST_SHA256 =
            "1289ccacf839ec75e56a324450356b8c718e037ab279d20fccdda821e71bda74";
    private static final int MAX_MODEL_BYTES = 16 * 1024 * 1024;
    private static final int MAX_MANIFEST_BYTES = 16 * 1024;
    private static final String EXPECTED_MODEL_VERSION = "synthetic-review-random-forest-v1";
    private static final String EXPECTED_LIBRARY_VERSION = "tribuo-4.3.2";

    private final ClassLoader classLoader;

    public RandomForestRiskSignalDetectorRuntime() {
        this(RandomForestRiskSignalDetectorRuntime.class.getClassLoader());
    }

    RandomForestRiskSignalDetectorRuntime(ClassLoader classLoader) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    /** Performs all resource I/O and validation now; callers control laziness by deferring this call. */
    public RiskSignalDetectorPort load() {
        byte[] manifestBytes = readRequiredResource(MANIFEST_RESOURCE, MAX_MANIFEST_BYTES);
        if (!sha256(manifestBytes).equals(EXPECTED_MANIFEST_SHA256)) {
            throw new IllegalStateException("packaged random-forest manifest does not match its trust anchor");
        }
        RandomForestModelManifest manifest;
        try {
            manifest = RandomForestModelManifest.fromCanonicalProperties(manifestBytes);
            validatePinnedProvenance(manifest);
            byte[] modelBytes = readRequiredResource(MODEL_RESOURCE, MAX_MODEL_BYTES);
            return new RandomForestRiskSignalDetectorAdapter(modelBytes, manifest);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("packaged random-forest resources failed validation", exception);
        }
    }

    /** Limits packaged resources to the model and library revisions reviewed with this runtime. */
    private static void validatePinnedProvenance(RandomForestModelManifest manifest) {
        if (!EXPECTED_MODEL_VERSION.equals(manifest.modelVersion())) {
            throw new IllegalArgumentException("unexpected packaged random-forest model version");
        }
        if (!EXPECTED_LIBRARY_VERSION.equals(manifest.libraryVersion())) {
            throw new IllegalArgumentException("unexpected packaged random-forest library version");
        }
    }

    /** Reads one mandatory resource under a caller-specific upper bound and never substitutes it. */
    private byte[] readRequiredResource(String name, int maximumBytes) {
        try (InputStream input = classLoader.getResourceAsStream(name)) {
            if (input == null) {
                throw new IllegalStateException("required packaged random-forest resource is missing: " + name);
            }
            byte[] bytes = input.readNBytes(maximumBytes + 1);
            if (bytes.length == 0 || bytes.length > maximumBytes) {
                throw new IllegalStateException("packaged random-forest resource has an invalid size: " + name);
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("could not read packaged random-forest resource: " + name, exception);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
