package org.duig.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class IdControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnSingleId() throws Exception {
        mockMvc.perform(get("/api/id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.idHex").isString())
                .andExpect(jsonPath("$.timestamp").isNumber())
                .andExpect(jsonPath("$.datacenterId").isNumber())
                .andExpect(jsonPath("$.machineId").isNumber())
                .andExpect(jsonPath("$.sequence").isNumber());
    }

    @Test
    void shouldReturnBulkIds() throws Exception {
        mockMvc.perform(get("/api/id/bulk?count=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    void shouldCapBulkCountAtMax() throws Exception {
        mockMvc.perform(get("/api/id/bulk?count=5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1000)));
    }

    @Test
    void shouldUseDefaultCountForBulk() throws Exception {
        mockMvc.perform(get("/api/id/bulk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)));
    }

    @Test
    void bulkIdsShouldHaveAllFields() throws Exception {
        mockMvc.perform(get("/api/id/bulk?count=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].idHex").isString())
                .andExpect(jsonPath("$[0].timestamp").isNumber())
                .andExpect(jsonPath("$[0].datacenterId").isNumber())
                .andExpect(jsonPath("$[0].machineId").isNumber())
                .andExpect(jsonPath("$[0].sequence").isNumber());
    }
}
