package com.afrochow.content.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** A tab/section of FAQs. {@code href} optionally links to the related dashboard page. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FaqCategory(String id, String label, String href, List<FaqEntry> faqs) {
}
