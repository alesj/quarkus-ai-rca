package io.quarkiverse.ai.rca.platform.lgtm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.ai.rca.ObservabilityPlatform;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class LgtmPlatform implements ObservabilityPlatform {

    @Inject
    ObjectMapper mapper;

    @Inject
    @Named("tempoMcpClient")
    McpClient tempoMcpClient;

    @Inject
    @Named("grafanaMcpClient")
    McpClient grafanaMcpClient;

    private final Map<String, String> traceIdToStartTime = new ConcurrentHashMap<>();

    @Override
    public List<String> searchTraces(int count) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("traceql-search")
                .arguments("{\"query\":\"{ nestedSetParent = -1 }\",\"limit\":" + count + "}")
                .build();
        String result = tempoMcpClient.executeTool(request).resultText();

        try {
            JsonNode root = mapper.readTree(result);
            JsonNode traces = root.path("traces");
            List<String> traceIds = new ArrayList<>();

            if (traces.isArray()) {
                for (JsonNode trace : traces) {
                    String traceId = trace.path("traceID").asText();
                    if (!traceId.isEmpty() && !traceIds.contains(traceId)) {
                        traceIds.add(traceId);
                        if (traceIds.size() >= count) {
                            break;
                        }
                    }
                }
            }

            return traceIds;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse trace search results", e);
        }
    }

    @Override
    public String getTrace(String traceId) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("get-trace")
                .arguments("{\"trace_id\":\"" + traceId + "\"}")
                .build();
        String traceJson = tempoMcpClient.executeTool(request).resultText();

        String startTime = LgtmStripFunctions.extractRootSpanStartTime(traceJson);
        if (startTime != null) {
            traceIdToStartTime.put(traceId, startTime);
        }

        return traceJson;
    }

    @Override
    public String getLogsForTrace(String traceId) {
        Instant now = Instant.now();
        Instant start = now.minus(Duration.ofHours(24));
        String logql = "{service_name=~\".+\"} | trace_id=`" + traceId + "`";
        String args = String.format(
                "{\"datasourceUid\":\"loki\"," +
                        "\"logql\":\"%s\"," +
                        "\"startRfc3339\":\"%s\"," +
                        "\"endRfc3339\":\"%s\"," +
                        "\"limit\":1000}",
                logql.replace("\"", "\\\""),
                toPrometheusTime(start),
                toPrometheusTime(now));
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("query_loki_logs")
                .arguments(args)
                .build();
        return grafanaMcpClient.executeTool(request).resultText();
    }

    @Override
    public String getTraceStartTime(String traceId) {
        return traceIdToStartTime.remove(traceId);
    }

    @Override
    public String getMetrics(String datetime) {
        Instant instant = Instant.parse(datetime);
        String result = queryPrometheusAt(toPrometheusTime(instant));
        if (isEmptyResult(result)) {
            Instant fallback = instant.plusSeconds(120);
            Instant now = Instant.now();
            if (fallback.isAfter(now)) {
                fallback = now;
            }
            result = queryPrometheusAt(toPrometheusTime(fallback));
        }
        return result;
    }

    private String toPrometheusTime(Instant instant) {
        String time = DateTimeFormatter.ISO_INSTANT.format(instant);
        if (time.contains(".")) {
            int dotIndex = time.indexOf('.');
            int zIndex = time.indexOf('Z', dotIndex);
            if (zIndex > 0) {
                time = time.substring(0, dotIndex) + "Z";
            }
        }
        return time;
    }

    private String queryPrometheusAt(String prometheusTime) {
        String args = String.format(
                "{\"datasourceUid\":\"prometheus\"," +
                        "\"expr\":\"{__name__=~'.+', job!=\\\"opentelemetry-collector\\\"}\"," +
                        "\"queryType\":\"instant\"," +
                        "\"endTime\":\"%s\"}",
                prometheusTime);
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("query_prometheus")
                .arguments(args)
                .build();
        return grafanaMcpClient.executeTool(request).resultText();
    }

    private boolean isEmptyResult(String json) {
        if (json == null || !json.trim().startsWith("{") && !json.trim().startsWith("[")) {
            return false;
        }
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode data = root.path("data");
            return !data.isArray() || data.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
