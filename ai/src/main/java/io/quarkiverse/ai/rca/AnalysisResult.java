package io.quarkiverse.ai.rca;

public record AnalysisResult(
        String analysis,
        String examinedSources,
        String dashboard,
        PerfInfo perf) {
    public record PerfInfo(long durationMs, int inputTokens, int outputTokens, int totalTokens, int llmCalls) {
    }
}
