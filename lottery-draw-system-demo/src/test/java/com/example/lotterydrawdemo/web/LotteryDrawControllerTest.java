package com.example.lotterydrawdemo.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "teaching.runner.enabled=false")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LotteryDrawControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnSameResultForDuplicateRequestIdAndExposeStats() throws Exception {
        String payload = "{\"userId\":\"user-1001\",\"requestId\":\"req-1001\"}";

        String firstResponse = mockMvc.perform(post("/api/lottery/draw")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-1001"))
                .andExpect(jsonPath("$.userId").value("user-1001"))
                .andExpect(jsonPath("$.campaignOpen").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/lottery/draw")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().json(firstResponse));

        mockMvc.perform(get("/api/lottery/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDrawCount").value(2000000))
                .andExpect(jsonPath("$.drawsIssued").value(1))
                .andExpect(jsonPath("$.remainingDraws").value(1999999))
                .andExpect(jsonPath("$.prizeStocks", hasSize(3)));
    }

    @Test
    void shouldRejectBlankIdentifiers() throws Exception {
        mockMvc.perform(post("/api/lottery/draw")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"\",\"requestId\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
