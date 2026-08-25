package com.afrochow.auth;
import com.afrochow.auth.controller.TestController;

import com.afrochow.testsupport.AbstractControllerTest;
import com.afrochow.testsupport.ControllerSliceTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer test for TestController — a fully public diagnostics
 * controller with no dependencies, no auth, and no request body.
 */
@ControllerSliceTest(TestController.class)
class TestControllerTest extends AbstractControllerTest {

    @Test
    void hello_returns200() throws Exception {
        mockMvc.perform(get("/v1/public/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("Hello World from Afrochow API!"));
    }

    @Test
    void health_returns200() throws Exception {
        mockMvc.perform(get("/v1/public/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.service").value("afrochow-api"));
    }
}
