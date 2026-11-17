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
    public List<String> searchTraces(int count) {
        String esql = String.format(
                "FROM traces-apm*" +
                        " | STATS count = COUNT(*) BY trace.id" +
                        " | SORT count DESC" +
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

            if (traceIds.isEmpty()) {
                JsonNode hits = root.path("hits").path("hits");
                if (hits.isArray()) {
                    for (JsonNode hit : hits) {
                        String traceId = hit.path("_source").path("trace").path("id").asText();
                        if (!traceId.isEmpty() && !traceIds.contains(traceId)) {
                            traceIds.add(traceId);
                            if (traceIds.size() >= count) {
                                break;
                            }
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
        Instant start = instant.minus(Duration.ofMinutes(2));
        Instant end = instant.plus(Duration.ofMinutes(2));

        String esql = String.format(
                "FROM metrics-apm*" +
                        " | WHERE @timestamp >= \"%s\" AND @timestamp <= \"%s\"" +
                        " | STATS avg_duration = AVG(transaction.duration.us)," +
                        " count = COUNT(*)" +
                        " BY service.name, transaction.name",
                start, end);
        return executeEsql(esql);
    }

    private String executeEsql(String query) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("execute_esql")
                .arguments("{\"query\":\"" + escapeJson(query) + "\"}")
                .build();
        return kibanaMcpClient.executeTool(request).resultText();
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

            JsonNode hits = root.path("hits").path("hits");
            if (hits.isArray() && !hits.isEmpty()) {
                return hits.get(0).path("_source").path("@timestamp").asText(null);
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
