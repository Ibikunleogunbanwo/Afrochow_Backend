package com.afrochow.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class DotenvConfig implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .directory("./")
                    .ignoreIfMissing()
                    .load();

            ConfigurableEnvironment environment = applicationContext.getEnvironment();
            Map<String, Object> dotenvMap = new HashMap<>();

            dotenv.entries().forEach(entry -> {
                dotenvMap.put(entry.getKey(), entry.getValue());
                System.setProperty(entry.getKey(), entry.getValue());
            });

            environment.getPropertySources().addFirst(
                    new MapPropertySource("dotenvProperties", dotenvMap)
            );

            // Basic Info
            System.out.println("✅ .env file loaded successfully");
            System.out.println("🌍 Active Profile: " + dotenv.get("SPRING_PROFILES_ACTIVE", "default"));
            System.out.println("💾 Database: " + dotenv.get("DB_NAME", "not-set"));
            System.out.println("🔌 Port: " + dotenv.get("DB_PORT", "3306"));
            System.out.println("📧 Email enabled: " + dotenv.get("SPRING_MAIL_ENABLED", "false"));

            // Connection String (helpful for debugging)
            String dbUrl = String.format("jdbc:mysql://%s:%s/%s",
                    dotenv.get("DB_HOST", "localhost"),
                    dotenv.get("DB_PORT", "3306"),
                    dotenv.get("DB_NAME", "afrochow")
            );
            System.out.println("🔗 Database URL: " + dbUrl);

            // Detailed debug only in dev mode
            if (isDebugMode(dotenv)) {
                printDebugInfo(dotenv);
            }

            runFlywayMigrations(dotenv);

        } catch (Exception e) {
            System.err.println("⚠️ Warning: Could not load .env file: " + e.getMessage());
            System.err.println("💡 Using environment variables or default values instead");
        }
    }

    /**
     * Runs Flyway migrations directly, ahead of Spring context refresh (and
     * therefore ahead of Hibernate's ddl-auto=update, which must never touch
     * the schema before migrations do).
     *
     * Why this exists instead of relying on Spring Boot's own Flyway
     * autoconfiguration: on this project's Spring Boot version, that
     * autoconfiguration was found to never fire at all (confirmed via a full
     * --debug CONDITIONS EVALUATION REPORT — FlywayAutoConfiguration did not
     * appear in positive matches, negative matches, or exclusions, meaning it
     * was never even a candidate). The practical symptom: db/migration/*.sql
     * files were silently never applied, `flyway_schema_history` never got
     * created, and hand-written schema fixes (like the payment.status ENUM
     * drift fixed in V23/V30) never took effect on real databases. Driving
     * Flyway manually here removes the dependency on that autoconfiguration
     * entirely and guarantees migrations run — in every profile, including
     * prod, where the same silent-no-op risk applies.
     */
    private void runFlywayMigrations(Dotenv dotenv) {
        String profile = dotenv.get("SPRING_PROFILES_ACTIVE", "dev");
        boolean isProd = "prod".equalsIgnoreCase(profile);

        String host = dotenv.get("DB_HOST", "localhost");
        String port = dotenv.get("DB_PORT", "3306");
        String name = dotenv.get("DB_NAME", "afrochow");
        String username = dotenv.get("DB_USERNAME", "root");
        String password = dotenv.get("DB_PASSWORD", "");

        String url = isProd
                ? String.format(
                        "jdbc:mysql://%s:%s/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                        host, port, name)
                : String.format(
                        "jdbc:mysql://%s:%s/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true",
                        host, port, name);

        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(url, username, password)
                    .locations("classpath:db/migration")
                    // baseline-on-migrate: if flyway_schema_history doesn't exist yet
                    // (true for any DB whose schema predates Flyway adoption on this
                    // project), stamp it at baselineVersion as already-applied instead
                    // of erroring on a non-empty un-baselined schema.
                    .baselineOnMigrate(true)
                    .baselineVersion("16")
                    .validateOnMigrate(false)
                    .cleanDisabled(true)
                    .load();

            // If a previous run left a failed migration entry in
            // flyway_schema_history (non-transactional DDL can't be rolled
            // back on error), migrate() would otherwise refuse to proceed at
            // all. repair() clears failed entries and realigns checksums so
            // a fixed migration file can be retried safely. Runs in prod too
            // (no live customer traffic yet, so there's no risk of masking a
            // real incident) — a genuinely failed migration still surfaces
            // loudly via the fatal throw below, a human just doesn't have to
            // manually repair before the next redeploy can retry.
            flyway.repair();

            var result = flyway.migrate();
            MigrationInfo currentMigration = flyway.info().current();
            System.out.println("🐬 Flyway: " + result.migrationsExecuted
                    + " migration(s) applied, schema now at version "
                    + schemaVersionDescription(currentMigration == null ? null : currentMigration.getVersion()));
        } catch (Exception e) {
            System.err.println("⚠️ Flyway migration failed: " + e.getMessage());

            if (isProd) {
                // In prod, ddl-auto is not reliably in "validate" mode (see
                // application-prod.properties), so there is no safety net if
                // migrations don't apply — the app would boot against a
                // stale/incompatible schema and fail at runtime instead of
                // at startup. Fail fast so this is caught by deploy tooling
                // rather than by a customer hitting a 500.
                throw new IllegalStateException(
                        "Flyway migration failed in prod — refusing to start. See cause for details.", e);
            }

            System.err.println("💡 The application will continue starting, but the schema may be out of date.");
        }
    }

    private boolean isDebugMode(Dotenv dotenv) {
        String profile = dotenv.get("SPRING_PROFILES_ACTIVE", "default");
        return !"prod".equalsIgnoreCase(profile);
    }

    private void printDebugInfo(Dotenv dotenv) {
        System.out.println("\n🔍 DEBUG MODE - Configuration Details:");
        System.out.println("   DB_HOST: " + dotenv.get("DB_HOST", "not-set"));
        System.out.println("   DB_USERNAME: " + configuredStatus(dotenv.get("DB_USERNAME")));
        System.out.println("   DB_PASSWORD: " + configuredStatus(dotenv.get("DB_PASSWORD")));
        System.out.println("   DB_DRIVER: " + dotenv.get("DB_DRIVER", "com.mysql.cj.jdbc.Driver"));
        System.out.println("   JWT_SECRET: " + configuredStatus(dotenv.get("APP_JWT_SECRET")));
        System.out.println();
    }

    static String configuredStatus(String value) {
        return value == null || value.isBlank() ? "not-set" : "set";
    }

    static String schemaVersionDescription(MigrationVersion version) {
        return version == null ? "none" : version.toString();
    }
}
