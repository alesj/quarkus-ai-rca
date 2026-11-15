package io.quarkiverse.ai.rca;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrails;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class PlainTools {

    @Inject
    ToolOutputCapture capture;

    @Inject
    ObservabilityPlatform platform;

    @OutputGuardrails(LogGuardrail.class)
    @ToolInputGuardrails(JsonValidator.class)
    @Tool("Provide last n trace ids")
    public List<String> provideLastNTraceIds(int n) {
        System.out.println("Getting last " + n + " trace IDs...");
        try {
            List<String> traceIds = platform.searchTraces(n);
            System.out.println("Got trace IDs: " + traceIds);
            capture.record("traceIds", null, traceIds);
            return traceIds;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get trace IDs", e);
        }
    }

    @OutputGuardrails(LogGuardrail.class)
    @Tool("Provide trace with trace id")
    public String traceById(String traceId) {
        System.out.println("Getting trace for ID: " + traceId);
        try {
            String traceJson = platform.getTrace(traceId);
            System.out.println("Got trace JSON (length: " + traceJson.length() + ")");
            capture.record("trace", traceId, traceJson);
            return traceJson;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get trace", e);
        }
    }

    @OutputGuardrails(LogGuardrail.class)
    @Tool("Get root span start time for trace id")
    public String getRootSpanStartTime(String traceId) {
        System.out.println("Getting start time for trace ID: " + traceId);
        String startTime = platform.getTraceStartTime(traceId);
        if (startTime == null) {
            System.out.println(
                    "WARNING: Start time not found for trace ID " + traceId + ". Call 'Provide trace with trace id' first.");
            return null;
        }
        System.out.println("Found start time: " + startTime);
        return startTime;
    }

    @OutputGuardrails(LogGuardrail.class)
    @Tool("Provide logs with trace id")
    public String logsWithTraceId(String traceId) {
        System.out.println("Getting logs for trace ID: " + traceId);
        try {
            String result = platform.getLogsForTrace(traceId);
            System.out.println("Got logs JSON (length: " + result.length() + ")");
            capture.record("logs", traceId, result);
            return result;
        } catch (Exception e) {
            System.err.println("ERROR getting logs: " + e.getMessage());
            throw new RuntimeException("Failed to get logs: " + e.getMessage(), e);
        }
    }

    @OutputGuardrails(LogGuardrail.class)
    @Tool("Get all Prometheus metrics at specific time")
    public String getAllMetricsForDatetime(String datetime) {
        System.out.println("Getting metrics for datetime: " + datetime);
        try {
            String result = platform.getMetrics(datetime);
            capture.record("metrics", datetime, result);
            return result;
        } catch (Throwable e) {
            System.err.println("ERROR getting metrics: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to get metrics: " + e.getMessage(), e);
        }
    }
}
