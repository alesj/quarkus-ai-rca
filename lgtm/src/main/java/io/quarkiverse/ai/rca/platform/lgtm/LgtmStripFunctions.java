package io.quarkiverse.ai.rca.platform.lgtm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import dev.langchain4j.service.tool.ToolExecutionResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class LgtmStripFunctions {

    private static Function<ToolExecutionResult, ToolExecutionResult> wrap(Function<String, String> fn) {
        return ter -> {
            String result = ter.resultText();
            String modified = fn.apply(result);
            return ToolExecutionResult.builder().resultText(modified).build();
        };
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> USELESS_TRACE_FIELDS = new HashSet<>(Arrays.asList(
            "telemetry.sdk.language",
            "telemetry.sdk.name",
            "telemetry.sdk.version",
            "webengine.name",
            "webengine.version",
            "host.name",
            "http.response.body.size",
            "kind",
            "metrics"));

    private static final Set<String> USELESS_LOG_FIELDS = new HashSet<>(Arrays.asList(
            "telemetry_sdk_language",
            "telemetry_sdk_name",
            "telemetry_sdk_version",
            "webengine_name",
            "webengine_version",
            "host_name",
            "flags",
            "log_logger_namespace",
            "observed_timestamp",
            "severity_number",
            "detected_level"));

    private static final Set<String> LOCALHOST_FIELDS = new HashSet<>(Arrays.asList(
            "client.address",
            "server.address"));

    private static void removeUselessFields(JsonNode node, Set<String> uselessFields) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            for (String fieldName : new ArrayList<>(obj.propertyNames())) {
                JsonNode fieldValue = obj.get(fieldName);

                if (uselessFields.contains(fieldName)) {
                    obj.remove(fieldName);
                    continue;
                }

                if (LOCALHOST_FIELDS.contains(fieldName) &&
                        fieldValue.isTextual() &&
                        (fieldValue.asText().equals("127.0.0.1") || fieldValue.asText().equals("localhost"))) {
                    obj.remove(fieldName);
                    continue;
                }

                if (fieldName.equals("message") && fieldValue.isTextual() && fieldValue.asText().isEmpty()) {
                    obj.putNull(fieldName);
                    continue;
                }

                removeUselessFields(fieldValue, uselessFields);
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                removeUselessFields(item, uselessFields);
            }
        }
    }

    private static final Function<String, String> STRIP_TRACE = trace -> {
        if (trace == null || trace.isBlank()) {
            return trace;
        }

        try {
            JsonNode root = MAPPER.readTree(trace);
            removeUselessFields(root, USELESS_TRACE_FIELDS);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return trace;
        }
    };

    public static Function<ToolExecutionResult, ToolExecutionResult> TRACE = wrap(STRIP_TRACE);

    private static final Function<String, String> STRIP_LOG_DATA = logData -> {
        if (logData == null || logData.isBlank()) {
            return logData;
        }

        try {
            JsonNode root = MAPPER.readTree(logData);
            removeUselessFields(root, USELESS_LOG_FIELDS);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return logData;
        }
    };

    public static Function<ToolExecutionResult, ToolExecutionResult> LOG_DATA = wrap(STRIP_LOG_DATA);

    private static final Set<String> USELESS_METRIC_PREFIXES = new HashSet<>(Arrays.asList(
            "netty_",
            "jvm_info",
            "target_info",
            "system_cpu_count",
            "process_files_max",
            "process_start_time",
            "jvm_memory_max_bytes",
            "jvm_buffer_",
            "jvm_classes_",
            "jvm_threads_started",
            "jvm_threads_states",
            "jvm_threads_peak",
            "jvm_threads_daemon",
            "jvm_gc_live_data",
            "jvm_gc_max_data",
            "jvm_memory_usage_after_gc",
            "jvm_memory_committed_bytes",
            "process_cpu_time",
            "worker_pool_completed_total",
            "otel_sdk_",
            "http_server_connections_duration",
            "http_client_connections_duration"));

    private static final Set<String> USELESS_METRIC_SUFFIXES = new HashSet<>(Arrays.asList(
            "_bucket",
            "_created"));

    private static boolean isUselessMetric(String metricName) {
        if (metricName == null) {
            return false;
        }

        for (String prefix : USELESS_METRIC_PREFIXES) {
            if (metricName.startsWith(prefix)) {
                return true;
            }
        }

        for (String suffix : USELESS_METRIC_SUFFIXES) {
            if (metricName.endsWith(suffix)) {
                return true;
            }
        }

        if ((metricName.endsWith("_sum") || metricName.endsWith("_count")) &&
                (metricName.contains("_milliseconds") || metricName.contains("_bytes"))) {
            return true;
        }

        return false;
    }

    private static boolean hasUselessLabels(JsonNode metricLabels) {
        if (metricLabels.has("id")) {
            return true;
        }

        if (metricLabels.has("allocator_type")) {
            return true;
        }

        if (metricLabels.has("pool_name")) {
            String poolName = metricLabels.path("pool_name").asText("");
            if (poolName.equals("vert.x-internal-blocking")) {
                return true;
            }
        }

        if (metricLabels.has("le")) {
            return true;
        }

        return false;
    }

    private static final Function<String, String> STRIP_METRICS = metrics -> {
        if (metrics == null || metrics.isBlank()) {
            return metrics;
        }

        try {
            JsonNode root = MAPPER.readTree(metrics);

            JsonNode dataArray = root.path("data");

            if (!dataArray.isArray()) {
                return metrics;
            }

            ObjectNode result = MAPPER.createObjectNode();
            var filteredData = MAPPER.createArrayNode();

            for (JsonNode item : dataArray) {
                JsonNode metric = item.path("metric");
                String metricName = metric.path("__name__").asText("");

                if (isUselessMetric(metricName)) {
                    continue;
                }

                if (hasUselessLabels(metric)) {
                    continue;
                }

                filteredData.add(item);
            }

            result.set("data", filteredData);
            return MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            return metrics;
        }
    };

    public static Function<ToolExecutionResult, ToolExecutionResult> METRICS = wrap(STRIP_METRICS);

    public static String extractRootSpanStartTime(String traceJson) {
        if (traceJson == null || traceJson.isBlank()) {
            return null;
        }

        try {
            JsonNode root = MAPPER.readTree(traceJson);
            JsonNode trace = root.path("trace");

            if (trace.isMissingNode()) {
                return null;
            }

            JsonNode services = trace.path("services");
            if (!services.isArray()) {
                return null;
            }

            for (JsonNode service : services) {
                JsonNode scopes = service.path("scopes");
                if (!scopes.isArray()) {
                    continue;
                }

                for (JsonNode scope : scopes) {
                    JsonNode spans = scope.path("spans");
                    if (!spans.isArray()) {
                        continue;
                    }

                    for (JsonNode span : spans) {
                        if (!span.has("parentSpanId")) {
                            String startTimeNano = span.path("startTimeUnixNano").asText();
                            if (!startTimeNano.isEmpty()) {
                                long nanos = Long.parseLong(startTimeNano);
                                long seconds = nanos / 1_000_000_000L;
                                long nanoAdjustment = nanos % 1_000_000_000L;
                                Instant instant = Instant.ofEpochSecond(seconds, nanoAdjustment);
                                return instant.toString();
                            }
                        }
                    }
                }
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
