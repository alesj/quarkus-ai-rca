package io.quarkiverse.ai.rca.platform.elastic;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import dev.langchain4j.service.tool.ToolExecutionResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class ElasticStripFunctions {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Function<ToolExecutionResult, ToolExecutionResult> wrap(Function<String, String> fn) {
        return ter -> {
            String result = ter.resultText();
            String modified = fn.apply(result);
            return ToolExecutionResult.builder().resultText(modified).build();
        };
    }

    private static final Set<String> USELESS_COLUMNS = new HashSet<>(Arrays.asList(
            "telemetry.sdk.language",
            "telemetry.sdk.name",
            "telemetry.sdk.version",
            "agent.name",
            "agent.version",
            "agent.ephemeral_id",
            "observer.hostname",
            "observer.version",
            "observer.type",
            "observer.id",
            "ecs.version",
            "event.ingested",
            "event.outcome",
            "host.hostname",
            "host.name",
            "host.os.platform",
            "host.architecture",
            "host.ip",
            "host.os.full",
            "host.os.name",
            "host.os.type",
            "host.os.version",
            "host.os.kernel",
            "process.pid",
            "process.args",
            "process.title",
            "process.executable",
            "process.runtime.name",
            "process.runtime.version",
            "process.runtime.description",
            "container.id",
            "cloud.provider",
            "cloud.region",
            "cloud.availability_zone",
            "cloud.instance.id",
            "cloud.machine.type",
            "cloud.account.id",
            "cloud.project.id",
            "kubernetes.pod.uid",
            "kubernetes.node.name",
            "kubernetes.namespace"));

    private static final Set<String> USELESS_COLUMN_PREFIXES = new HashSet<>(Arrays.asList(
            "telemetry.sdk.",
            "agent.",
            "observer.",
            "ecs.",
            "host.os.",
            "process.runtime.",
            "cloud.",
            "kubernetes."));

    private static boolean isUselessColumn(String name) {
        if (USELESS_COLUMNS.contains(name)) {
            return true;
        }
        for (String prefix : USELESS_COLUMN_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static final Function<String, String> STRIP_ESQL_RESULT = json -> {
        if (json == null || json.isBlank()) {
            return json;
        }

        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode columns = root.path("columns");
            JsonNode values = root.path("values");

            if (!columns.isArray() || !values.isArray()) {
                return stripNestedFields(root);
            }

            List<Integer> keepIndices = new ArrayList<>();
            ArrayNode filteredColumns = MAPPER.createArrayNode();
            for (int i = 0; i < columns.size(); i++) {
                String colName = columns.get(i).path("name").asText("");
                if (!isUselessColumn(colName)) {
                    keepIndices.add(i);
                    filteredColumns.add(columns.get(i));
                }
            }

            if (keepIndices.size() == columns.size()) {
                return json;
            }

            ArrayNode filteredValues = MAPPER.createArrayNode();
            for (JsonNode row : values) {
                if (row.isArray()) {
                    ArrayNode filteredRow = MAPPER.createArrayNode();
                    for (int idx : keepIndices) {
                        filteredRow.add(row.get(idx));
                    }
                    filteredValues.add(filteredRow);
                } else {
                    filteredValues.add(row);
                }
            }

            ObjectNode result = MAPPER.createObjectNode();
            result.set("columns", filteredColumns);
            result.set("values", filteredValues);
            return MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            return json;
        }
    };

    private static final Set<String> USELESS_NESTED_FIELDS = new HashSet<>(Arrays.asList(
            "telemetry.sdk.language",
            "telemetry.sdk.name",
            "telemetry.sdk.version",
            "agent.name",
            "agent.version",
            "agent.ephemeral_id",
            "observer.hostname",
            "observer.version",
            "observer.type",
            "observer.id",
            "ecs.version",
            "event.ingested",
            "host.hostname",
            "host.name",
            "process.pid",
            "process.args",
            "process.title",
            "container.id"));

    private static String stripNestedFields(JsonNode root) {
        removeFields(root, USELESS_NESTED_FIELDS);
        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return root.toString();
        }
    }

    private static void removeFields(JsonNode node, Set<String> fieldsToRemove) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            for (String fieldName : new ArrayList<>(obj.propertyNames())) {
                if (fieldsToRemove.contains(fieldName)) {
                    obj.remove(fieldName);
                    continue;
                }
                removeFields(obj.get(fieldName), fieldsToRemove);
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                removeFields(item, fieldsToRemove);
            }
        }
    }

    public static final Function<ToolExecutionResult, ToolExecutionResult> ESQL = wrap(STRIP_ESQL_RESULT);
}
