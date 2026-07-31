package com.afrochow.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaRuntimeVersionGuardTest {

    @Test
    void acceptsJava21Runtime() {
        assertThatCode(() -> JavaRuntimeVersionGuard.validateJavaFeatureVersion(21))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNewerRuntimeThanCompiledTarget() {
        assertThatThrownBy(() -> JavaRuntimeVersionGuard.validateJavaFeatureVersion(25))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported Java runtime version 25")
                .hasMessageContaining("must run on Java 21");
    }
}
