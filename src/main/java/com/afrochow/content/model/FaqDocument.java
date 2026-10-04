package com.afrochow.content.model;

import java.util.List;

/**
 * Help-centre FAQs for one audience (customer, vendor or admin).
 * {@code sourcePath} is the frontend page that renders this document, so
 * answers generated from it can link back to the source.
 */
public record FaqDocument(
        String audience,
        String title,
        String description,
        String sourcePath,
        List<FaqCategory> categories
) {
}
