package io.quarkiverse.ai.rca;

public record AnalysisResult(
        String analysis,
        String examinedSources,
        String dashboard,
        PerfInfo perf,
        String error) {

    public AnalysisResult(String analysis, String examinedSources, String dashboard, PerfInfo perf) {
        this(analysis, examinedSources, dashboard, perf, null);
    }

    static AnalysisResult error(String message, long durationMs) {
        return new AnalysisResult(null, null, null, new PerfInfo(durationMs, 0, 0, 0, 0), message);
    }

    public record PerfInfo(long durationMs, int inputTokens, int outputTokens, int totalTokens, int llmCalls) {
    }
}
