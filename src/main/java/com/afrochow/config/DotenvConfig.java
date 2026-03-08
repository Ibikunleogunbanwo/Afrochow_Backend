package com.afrochow.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DotenvConfig implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "DB_PASSWORD", "PROD_DB_PASSWORD", "APP_JWT_SECRET",
            "APP_JWT_ENCRYPTION_KEY", "SPRING_MAIL_PASSWORD",
            "DEV_GMAIL_PASSWORD", "PROD_SENDGRID_PASSWORD"
    );

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
            System.out.println("🌍 Active Profile: " + dotenv.get("SPRING_PROFILE", "dev"));
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

        } catch (Exception e) {
            System.err.println("⚠️ Warning: Could not load .env file: " + e.getMessage());
            System.err.println("💡 Using environment variables or default values instead");
        }
    }

    private boolean isDebugMode(Dotenv dotenv) {
        String profile = dotenv.get("SPRING_PROFILE", "dev");
        return "dev".equalsIgnoreCase(profile);
    }

    private void printDebugInfo(Dotenv dotenv) {
        System.out.println("\n🔍 DEBUG MODE - Configuration Details:");
        System.out.println("   DB_HOST: " + dotenv.get("DB_HOST", "not-set"));
        System.out.println("   DB_USERNAME: " + dotenv.get("DB_USERNAME", "not-set"));
        System.out.println("   DB_PASSWORD: " + maskValue(dotenv.get("DB_PASSWORD")));
        System.out.println("   DB_DRIVER: " + dotenv.get("DB_DRIVER", "com.mysql.cj.jdbc.Driver"));
        System.out.println("   JWT_SECRET: " + maskValue(dotenv.get("APP_JWT_SECRET")));
        System.out.println();
    }

    private String maskValue(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }
}