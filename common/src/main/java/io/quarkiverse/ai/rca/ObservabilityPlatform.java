package io.quarkiverse.ai.rca;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * SPI for pluggable observability backends (e.g. Grafana LGTM, Elastic, Datadog).
 * <p>
 * Each implementation handles platform-specific concerns internally:
 * MCP/API client setup, query language formatting (TraceQL, LogQL, PromQL, etc.),
 * and noise filtering. Methods return pre-processed, LLM-ready data.
 * <p>
 * Implementations must be CDI beans (e.g. {@code @ApplicationScoped}) and will be
 * injected into the AI analysis pipeline automatically.
 */
public interface ObservabilityPlatform {

    /**
     * Short identifier for this platform (e.g. "lgtm", "elastic").
     * Used to build prompt-override keys ({@code {platform}_{provider}_{model}})
     * and to load platform-specific prompt fragments.
     */
    String platformName();

    /**
     * Platform-specific prompt fragment appended after the base system message.
     * Loads {@code {platformName}-prompt.txt} from the classpath by default.
     */
    default String promptFragment() {
        String resource = platformName() + "-prompt.txt";
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            return is != null ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Search for the most recent traces.
     *
     * @param count maximum number of trace IDs to return
     * @return list of trace identifiers, most recent first
     */
    List<String> searchTraces(int count);

    /**
     * Retrieve the full trace (spans, attributes, status) for a given trace ID.
     * Implementations should strip SDK noise and irrelevant metadata before returning.
     *
     * @param traceId the trace identifier
     * @return filtered trace data as a string
     */
    String getTrace(String traceId);

    /**
     * Retrieve logs correlated with a specific trace.
     *
     * @param traceId the trace identifier to query logs for
     * @return filtered log entries as a string
     */
    String getLogsForTrace(String traceId);

    /**
     * Return the start time of a trace's root span.
     * Used to anchor metric queries to the correct time window.
     *
     * @param traceId the trace identifier
     * @return ISO-8601 datetime string of the trace start
     */
    String getTraceStartTime(String traceId);

    /**
     * Query system and application metrics around a given point in time.
     * Implementations should filter out low-signal metrics (static counters,
     * histogram buckets, per-region breakdowns, etc.) before returning.
     *
     * @param datetime ISO-8601 datetime string to center the query window on
     * @return filtered metrics data as a string
     */
    String getMetrics(String datetime);
}
