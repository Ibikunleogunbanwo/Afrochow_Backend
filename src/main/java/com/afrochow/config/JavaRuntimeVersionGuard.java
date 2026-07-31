package com.afrochow.config;

public final class JavaRuntimeVersionGuard {

    static final int REQUIRED_JAVA_FEATURE_VERSION = 21;

    private JavaRuntimeVersionGuard() {
    }

    public static void validateCurrentRuntime() {
        validateJavaFeatureVersion(Runtime.version().feature());
    }

    static void validateJavaFeatureVersion(int actualFeatureVersion) {
        if (actualFeatureVersion != REQUIRED_JAVA_FEATURE_VERSION) {
            throw new IllegalStateException(
                    "Unsupported Java runtime version " + actualFeatureVersion
                            + ". Afrochow must run on Java " + REQUIRED_JAVA_FEATURE_VERSION
                            + " to match the compiled and tested target.");
        }
    }
}
