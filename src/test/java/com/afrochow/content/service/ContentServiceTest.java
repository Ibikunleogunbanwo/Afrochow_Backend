package com.afrochow.content.service;

import com.afrochow.common.exceptions.ResourceNotFoundException;
import com.afrochow.content.model.ContentBlock;
import com.afrochow.content.model.FaqDocument;
import com.afrochow.content.model.PolicyDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Loads the real JSON files under src/main/resources/content, so a malformed or
 * incomplete content edit fails here instead of at application startup.
 */
class ContentServiceTest {

    private static final Set<String> BLOCK_TYPES = Set.of("paragraph", "list", "table", "contact");

    private ContentService contentService;

    @BeforeEach
    void setUp() {
        contentService = new ContentService();
        contentService.load();
    }

    @Test
    void loadsAllAudiencesAndPolicies() {
        assertThat(contentService.getAllFaqs()).extracting(FaqDocument::audience)
                .containsExactlyInAnyOrder("customer", "vendor", "admin");
        assertThat(contentService.getAllPolicies()).extracting(PolicyDocument::slug)
                .containsExactlyInAnyOrder("privacy", "terms", "cookies");
    }

    @Test
    void everyFaqDocumentIsComplete() {
        for (FaqDocument doc : contentService.getAllFaqs()) {
            assertThat(doc.title()).as(doc.audience() + " title").isNotBlank();
            assertThat(doc.sourcePath()).as(doc.audience() + " sourcePath").startsWith("/");
            assertThat(doc.categories()).as(doc.audience() + " categories").isNotEmpty();
            doc.categories().forEach(category -> {
                assertThat(category.id()).isNotBlank();
                assertThat(category.label()).isNotBlank();
                assertThat(category.faqs()).as(category.id() + " faqs").isNotEmpty();
                category.faqs().forEach(faq -> {
                    assertThat(faq.question()).isNotBlank();
                    assertThat(faq.answer()).as(faq.question()).isNotBlank();
                });
            });
        }
    }

    @Test
    void everyPolicyBlockMatchesItsType() {
        for (PolicyDocument doc : contentService.getAllPolicies()) {
            assertThat(doc.title()).isNotBlank();
            assertThat(doc.lastUpdated()).isNotBlank();
            assertThat(doc.intro()).isNotBlank();
            assertThat(doc.sections()).isNotEmpty();
            doc.sections().forEach(section -> {
                assertThat(section.id()).isNotBlank();
                assertThat(section.heading()).isNotBlank();
                assertThat(section.blocks()).as(section.id()).isNotEmpty();
                section.blocks().forEach(block -> assertValidBlock(doc.slug() + "/" + section.id(), block));
            });
        }
    }

    @Test
    void faqCountsMatchTheOriginalPages() {
        assertThat(countFaqs("customer")).isEqualTo(21);
        assertThat(countFaqs("vendor")).isEqualTo(24);
        assertThat(countFaqs("admin")).isEqualTo(18);
    }

    @Test
    void unknownIdsThrowNotFound() {
        assertThatThrownBy(() -> contentService.getFaqs("nobody")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> contentService.getPolicy("nothing")).isInstanceOf(ResourceNotFoundException.class);
    }

    private int countFaqs(String audience) {
        return contentService.getFaqs(audience).categories().stream().mapToInt(c -> c.faqs().size()).sum();
    }

    private void assertValidBlock(String where, ContentBlock block) {
        assertThat(block.type()).as(where).isIn(BLOCK_TYPES);
        switch (block.type()) {
            case "paragraph" -> assertThat(block.text()).as(where).isNotBlank();
            case "list" -> assertThat(block.items()).as(where).isNotEmpty();
            case "table" -> {
                assertThat(block.columns()).as(where).isNotEmpty();
                block.rows().forEach(row -> assertThat(row).as(where).hasSameSizeAs(block.columns()));
            }
            case "contact" -> assertThat(block.email()).as(where).contains("@");
            default -> throw new AssertionError("unreachable");
        }
    }
}
