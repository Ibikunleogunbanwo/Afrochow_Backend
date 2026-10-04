package com.afrochow.content.controller;

import com.afrochow.common.exceptions.ResourceNotFoundException;
import com.afrochow.content.model.FaqCategory;
import com.afrochow.content.model.FaqDocument;
import com.afrochow.content.model.FaqEntry;
import com.afrochow.content.model.PolicyDocument;
import com.afrochow.content.service.ContentService;
import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for ContentController. Security rules (/public/** open,
 * /admin/** admin-only) live in SecurityConfig and are not exercised by this slice.
 */
@ControllerSliceTest(ContentController.class)
class ContentControllerTest extends AbstractControllerTest {

    @MockitoBean private ContentService contentService;

    private FaqDocument faqs(String audience) {
        return new FaqDocument(audience, "Help & Support", "desc", "/help", List.of(
                new FaqCategory("ordering", "Placing Orders", null,
                        List.of(new FaqEntry("How do I place an order?", "Browse restaurants...")))));
    }

    @Test
    void getPublicFaqs_returnsDocumentWithCacheHeader() throws Exception {
        when(contentService.getFaqs("customer")).thenReturn(faqs("customer"));

        mockMvc.perform(get("/public/content/faqs/customer"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=300, public"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.audience").value("customer"))
                .andExpect(jsonPath("$.data.categories[0].faqs[0].question").value("How do I place an order?"))
                .andExpect(jsonPath("$.data.categories[0].href").doesNotExist());
    }

    @Test
    void getPublicFaqs_adminAudience_isNotServedPublicly() throws Exception {
        mockMvc.perform(get("/public/content/faqs/admin"))
                .andExpect(status().isNotFound());

        verify(contentService, never()).getFaqs("admin");
    }

    @Test
    void getPublicFaqs_unknownAudience_returns404() throws Exception {
        when(contentService.getFaqs("nobody"))
                .thenThrow(new ResourceNotFoundException("FAQ document", "audience", "nobody"));

        mockMvc.perform(get("/public/content/faqs/nobody"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getAdminFaqs_returnsAdminDocument() throws Exception {
        when(contentService.getFaqs("admin")).thenReturn(faqs("admin"));

        mockMvc.perform(get("/admin/content/faqs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.audience").value("admin"));
    }

    @Test
    void getPolicy_returnsDocument() throws Exception {
        when(contentService.getPolicy("privacy")).thenReturn(
                new PolicyDocument("privacy", "Privacy Policy", "desc", "March 2025", "/privacy", "intro", List.of()));

        mockMvc.perform(get("/public/content/policies/privacy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Privacy Policy"))
                .andExpect(jsonPath("$.data.lastUpdated").value("March 2025"));
    }
}
