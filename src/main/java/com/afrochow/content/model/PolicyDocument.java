package com.afrochow.content.model;

import java.util.List;

/** A legal/policy page such as the privacy policy, terms of service or cookie policy. */
public record PolicyDocument(
        String slug,
        String title,
        String description,
        String lastUpdated,
        String sourcePath,
        String intro,
        List<PolicySection> sections
) {
}
