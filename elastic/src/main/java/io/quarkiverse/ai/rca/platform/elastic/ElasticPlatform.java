package io.quarkiverse.ai.rca.platform.elastic;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.ai.rca.ObservabilityPlatform;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ElasticPlatform implements ObservabilityPlatform {

    @Inject
    ObjectMapper mapper;

    @Inject
    @McpClientName("kibana")
    McpClient kibanaMcpClient;

    private final Map<String, String> traceIdToStartTime = new ConcurrentHashMap<>();

    @Override
    public String platformName() {
        return "elastic";
    }

    @Override
    public List<String> searchTraces(int count) {
        String esql = String.format(
                "FROM traces-apm*" +
                        " | STATS max_ts = MAX(@timestamp), count = COUNT(*) BY trace.id" +
                        " | SORT max_ts DESC" +
                        " | LIMIT %d" +
                        " | KEEP trace.id", count);
        String result = executeEsql(esql);

        try {
            JsonNode root = mapper.readTree(result);
            JsonNode values = root.path("values");
            List<String> traceIds = new ArrayList<>();

            if (values.isArray()) {
                for (JsonNode row : values) {
                    String traceId = row.isArray() ? row.get(0).asText() : row.asText();
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
        String esql = String.format(
                "FROM traces-apm*" +
                        " | WHERE trace.id == \"%s\"" +
                        " | EVAL duration_ms = ROUND(COALESCE(transaction.duration.us, span.duration.us, 0) / 1000.0, 1)" +
                        // span.action is not present on OTel HTTP traces and KEEP of an
                        // unknown column fails the whole query with verification_exception.
                        " | KEEP @timestamp, trace.id, transaction.id, parent.id, processor.event," +
                        " transaction.name, transaction.type, transaction.result," +
                        " span.name, span.type, span.subtype," +
                        " service.name, http.response.status_code, url.full," +
                        " duration_ms" +
                        " | SORT @timestamp ASC", traceId);
        String traceData = executeEsql(esql);

        String startTime = extractTraceStartTime(traceData);
        if (startTime != null) {
            traceIdToStartTime.put(traceId, startTime);
        }

        return traceData;
    }

    @Override
    public String getLogsForTrace(String traceId) {
        String esql = String.format(
                "FROM logs-apm*" +
                        " | WHERE trace.id == \"%s\"" +
                        " | SORT @timestamp ASC" +
                        " | LIMIT 1000", traceId);
        return executeEsql(esql);
    }

    @Override
    public String getTraceStartTime(String traceId) {
        return traceIdToStartTime.remove(traceId);
    }

    @Override
    public String getMetrics(String datetime) {
        Instant instant = Instant.parse(datetime);
        String start = DateTimeFormatter.ISO_INSTANT.format(instant.minus(Duration.ofMinutes(2)));
        String end = DateTimeFormatter.ISO_INSTANT.format(instant.plus(Duration.ofMinutes(2)));

        String traceMetrics = executeTool("observability_get_trace_metrics",
                "{\"start\":\"" + start + "\",\"end\":\"" + end + "\"," +
                        "\"groupBy\":\"service.name\",\"latencyType\":\"avg\",\"sortBy\":\"latency\"}");

        String runtimeMetrics = executeTool("observability_get_runtime_metrics",
                "{\"start\":\"" + start + "\",\"end\":\"" + end + "\"}");

        return "=== Trace Metrics ===\n" + traceMetrics + "\n=== Runtime Metrics ===\n" + runtimeMetrics;
    }

    private String executeTool(String toolName, String arguments) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name(toolName)
                .arguments(arguments)
                .build();
        return kibanaMcpClient.executeTool(request).resultText();
    }

    private String executeEsql(String query) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("platform_core_execute_esql")
                .arguments("{\"query\":\"" + escapeJson(query) + "\"}")
                .build();
        String raw = kibanaMcpClient.executeTool(request).resultText();
        try {
            JsonNode root = mapper.readTree(raw);
            JsonNode esqlData = findEsqlData(root);
            if (esqlData != root) {
                return mapper.writeValueAsString(esqlData);
            }
        } catch (Exception ignored) {
        }
        return raw;
    }

    private String extractTraceStartTime(String traceData) {
        if (traceData == null || traceData.isBlank()) {
            return null;
        }

        try {
            JsonNode root = mapper.readTree(traceData);

            JsonNode values = root.path("values");
            if (values.isArray() && !values.isEmpty()) {
                JsonNode firstRow = values.get(0);
                JsonNode columns = root.path("columns");
                if (columns.isArray()) {
                    for (int i = 0; i < columns.size(); i++) {
                        if ("@timestamp".equals(columns.get(i).path("name").asText())) {
                            return firstRow.get(i).asText();
                        }
                    }
                }
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Kibana MCP wraps ES|QL results in a {@code {"results":[...]}} envelope.
     * This method navigates to the {@code esql_results} data node.
     */
    private JsonNode findEsqlData(JsonNode root) {
        JsonNode results = root.path("results");
        if (results.isArray()) {
            for (JsonNode item : results) {
                if ("esql_results".equals(item.path("type").asText())) {
                    return item.path("data");
                }
            }
        }
        return root;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
