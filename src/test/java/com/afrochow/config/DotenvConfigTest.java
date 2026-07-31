package com.afrochow.config;

import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DotenvConfigTest {

    @Test
    void configuredStatusDoesNotExposeSecretValue() {
        String secret = "super-sensitive-secret";

        String status = DotenvConfig.configuredStatus(secret);

        assertThat(status).isEqualTo("set");
        assertThat(status).doesNotContain(secret);
        assertThat(status).doesNotContain("super");
        assertThat(status).doesNotContain("cret");
    }

    @Test
    void configuredStatusReportsMissingValuesWithoutMaskingFragments() {
        assertThat(DotenvConfig.configuredStatus(null)).isEqualTo("not-set");
        assertThat(DotenvConfig.configuredStatus("")).isEqualTo("not-set");
        assertThat(DotenvConfig.configuredStatus("   ")).isEqualTo("not-set");
    }

    @Test
    void schemaVersionDescriptionUsesCurrentAppliedMigrationVersion() {
        assertThat(DotenvConfig.schemaVersionDescription(MigrationVersion.fromVersion("46"))).isEqualTo("46");
    }

    @Test
    void schemaVersionDescriptionHandlesEmptySchemaHistory() {
        assertThat(DotenvConfig.schemaVersionDescription(null)).isEqualTo("none");
    }
}
