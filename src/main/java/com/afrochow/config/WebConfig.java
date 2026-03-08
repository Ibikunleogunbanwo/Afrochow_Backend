package com.afrochow.config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebConfig.class);

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Resolve to absolute path
        Path absoluteUploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        String uploadUri = absoluteUploadPath.toUri().toString();

        logger.info("=== Image Resource Handler Configuration ===");
        logger.info("Upload directory (from config): {}", uploadDir);
        logger.info("Absolute upload path: {}", absoluteUploadPath);
        logger.info("Resource URI: {}", uploadUri);
        logger.info("Mapping: /images/** -> {}", uploadUri);

        registry.addResourceHandler("/images/**")
                .addResourceLocations(uploadUri);
    }
}
