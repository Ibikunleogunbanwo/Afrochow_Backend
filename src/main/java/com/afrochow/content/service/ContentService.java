package com.afrochow.content.service;

import com.afrochow.common.exceptions.ResourceNotFoundException;
import com.afrochow.content.model.FaqDocument;
import com.afrochow.content.model.PolicyDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Single source of truth for static help-centre and policy content.
 *
 * <p>Documents live as JSON under {@code classpath:content/} and are loaded once at
 * startup — a malformed file fails the boot rather than serving a broken page. The
 * frontend renders these documents via {@code ContentController}, and the same
 * documents are what the assistant's retrieval index is built from, so the website
 * and the AI never drift apart.
 */
@Slf4j
@Service
public class ContentService {

    public static final String ADMIN_AUDIENCE = "admin";

    private static final String FAQ_PATTERN = "classpath:content/faqs/*.json";
    private static final String POLICY_PATTERN = "classpath:content/policies/*.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    private Map<String, FaqDocument> faqsByAudience = Map.of();
    private Map<String, PolicyDocument> policiesBySlug = Map.of();

    @PostConstruct
    void load() {
        faqsByAudience = loadAll(FAQ_PATTERN, FaqDocument.class, FaqDocument::audience);
        policiesBySlug = loadAll(POLICY_PATTERN, PolicyDocument.class, PolicyDocument::slug);
        log.info("Loaded static content: FAQs for {}, policies {}",
                faqsByAudience.keySet(), policiesBySlug.keySet());
    }

    public FaqDocument getFaqs(String audience) {
        FaqDocument doc = faqsByAudience.get(audience);
        if (doc == null) {
            throw new ResourceNotFoundException("FAQ document", "audience", audience);
        }
        return doc;
    }

    public PolicyDocument getPolicy(String slug) {
        PolicyDocument doc = policiesBySlug.get(slug);
        if (doc == null) {
            throw new ResourceNotFoundException("Policy", "slug", slug);
        }
        return doc;
    }

    /** Every FAQ document, including admin-only content. Intended for the RAG ingestion job. */
    public Collection<FaqDocument> getAllFaqs() {
        return faqsByAudience.values();
    }

    /** Every policy document. Intended for the RAG ingestion job. */
    public Collection<PolicyDocument> getAllPolicies() {
        return policiesBySlug.values();
    }

    private <T> Map<String, T> loadAll(String pattern, Class<T> type, Function<T, String> key) {
        Map<String, T> result = new LinkedHashMap<>();
        try {
            for (Resource resource : resolver.getResources(pattern)) {
                try (InputStream in = resource.getInputStream()) {
                    T doc = objectMapper.readValue(in, type);
                    String id = key.apply(doc);
                    if (id == null || id.isBlank()) {
                        throw new IllegalStateException("Content file " + resource.getFilename() + " has no id");
                    }
                    if (result.putIfAbsent(id, doc) != null) {
                        throw new IllegalStateException("Duplicate content id '" + id + "' in " + resource.getFilename());
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load content from " + pattern, e);
        }
        return Map.copyOf(result);
    }
}
