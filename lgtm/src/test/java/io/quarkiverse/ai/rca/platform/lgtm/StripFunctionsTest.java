package io.quarkiverse.ai.rca.platform.lgtm;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class StripFunctionsTest {

    @Test
    void testExtractRootSpanStartTime() throws IOException {
        String traceJson;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("traces.json")) {
            assertNotNull(is, "traces.json should exist in test resources");
            traceJson = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        String startTime = LgtmStripFunctions.extractRootSpanStartTime(traceJson);

        assertNotNull(startTime, "Root span start time should not be null");
        assertEquals("2026-05-12T15:10:49.074740Z", startTime);
    }

    @Test
    void testExtractRootSpanStartTime_NullInput() {
        assertNull(LgtmStripFunctions.extractRootSpanStartTime(null));
    }

    @Test
    void testExtractRootSpanStartTime_EmptyInput() {
        assertNull(LgtmStripFunctions.extractRootSpanStartTime(""));
        assertNull(LgtmStripFunctions.extractRootSpanStartTime("   "));
    }

    @Test
    void testExtractRootSpanStartTime_InvalidJson() {
        assertNull(LgtmStripFunctions.extractRootSpanStartTime("{invalid json}"));
    }

    @Test
    void testExtractRootSpanStartTime_NoTrace() {
        String json = "{\"notATrace\": {}}";
        assertNull(LgtmStripFunctions.extractRootSpanStartTime(json));
    }

    @Test
    void testExtractRootSpanStartTime_NoRootSpan() {
        String json = """
                {
                  "trace": {
                    "services": [{
                      "scopes": [{
                        "spans": [{
                          "spanId": "child",
                          "parentSpanId": "parent",
                          "startTimeUnixNano": "1778598649074740000"
                        }]
                      }]
                    }]
                  }
                }
                """;
        assertNull(LgtmStripFunctions.extractRootSpanStartTime(json));
    }

    @Test
    void testStripMetrics() {
        String metricsJson = """
                {
                  "data": [
                    {
                      "metric": {
                        "__name__": "jvm_memory_used_bytes",
                        "area": "heap"
                      },
                      "value": [1778614717.591, "1000000"]
                    },
                    {
                      "metric": {
                        "__name__": "netty_allocator_pooled_arenas",
                        "allocator_type": "PooledByteBufAllocator"
                      },
                      "value": [1778614717.591, "20"]
                    },
                    {
                      "metric": {
                        "__name__": "system_cpu_usage"
                      },
                      "value": [1778614717.591, "0.15"]
                    },
                    {
                      "metric": {
                        "__name__": "jvm_info_total",
                        "version": "21"
                      },
                      "value": [1778614717.591, "1.0"]
                    },
                    {
                      "metric": {
                        "__name__": "poke_value",
                        "app": "poke"
                      },
                      "value": [1778614717.591, "401"]
                    },
                    {
                      "metric": {
                        "__name__": "otel_sdk_processor_log_queue_size",
                        "job": "quarkus-ai-rca-app"
                      },
                      "value": [1778614717.591, "0"]
                    },
                    {
                      "metric": {
                        "__name__": "target_info",
                        "job": "quarkus-ai-rca-app"
                      },
                      "value": [1778614717.591, "1"]
                    },
                    {
                      "metric": {
                        "__name__": "http_server_requests_max_milliseconds",
                        "uri": "/poke"
                      },
                      "value": [1778614717.591, "125.5"]
                    }
                  ]
                }
                """;

        var stripFn = LgtmStripFunctions.METRICS;
        var result = stripFn.apply(
                dev.langchain4j.service.tool.ToolExecutionResult.builder()
                        .resultText(metricsJson)
                        .build());

        String stripped = result.resultText();

        assertTrue(stripped.contains("jvm_memory_used_bytes"), "Should keep jvm_memory_used_bytes");
        assertTrue(stripped.contains("system_cpu_usage"), "Should keep system_cpu_usage");
        assertTrue(stripped.contains("poke_value"), "Should keep poke_value");
        assertTrue(stripped.contains("http_server_requests_max_milliseconds"),
                "Should keep http_server_requests_max_milliseconds");

        assertFalse(stripped.contains("netty_allocator_pooled_arenas"), "Should remove netty_allocator_pooled_arenas");
        assertFalse(stripped.contains("jvm_info_total"), "Should remove jvm_info_total");
        assertFalse(stripped.contains("otel_sdk_processor_log_queue_size"), "Should remove otel_sdk_* metrics");
        assertFalse(stripped.contains("target_info"), "Should remove target_info");
    }
}
