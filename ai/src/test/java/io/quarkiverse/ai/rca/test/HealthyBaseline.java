package io.quarkiverse.ai.rca.test;

/**
 * Scoring rubric appended to criteria of tests where nothing is wrong.
 * The generic scorer scale ("identifies the issues") gives a judge no clear
 * way to reward a correct "all healthy" verdict, so spell it out.
 */
final class HealthyBaseline {

    static final String RUBRIC = """

            This is a HEALTHY BASELINE: no issue was injected. Score it as follows:
            - 1.0 if the analysis concludes the system is healthy (no issues, severity LOW or none) \
            and does not invent problems.
            - Do NOT lower the score for not discussing absent signals (missing chaos keywords, \
            metrics that were not collected, thresholds that were not crossed) or for brief \
            recommendations such as "no action required".
            - 0.4 or lower if the analysis reports any latency, error, database or resource \
            problem as MEDIUM or HIGH severity, or recommends investigating one.""";

    private HealthyBaseline() {
    }
}
