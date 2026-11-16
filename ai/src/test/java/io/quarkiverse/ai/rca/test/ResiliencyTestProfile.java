package io.quarkiverse.ai.rca.test;

import io.quarkiverse.resiliencytesting.junit.NetworkFaultsProfile;

import java.util.HashMap;
import java.util.Map;

public class ResiliencyTestProfile extends NetworkFaultsProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>(super.getConfigOverrides());
        config.put("test.fixture-mcp", "false");
        config.put("quarkus.observability.enabled-in-tests", "true");
        config.put("quarkus.langchain4j.openai.api-key", "dummy-key-resiliency");
        config.put("quarkus.langchain4j.openai.timeout", "5s");
        config.put("quarkus.langchain4j.openai.scorer.api-key", "dummy-key-scorer");
        config.put("quarkus.http.test-port", "0");
        return config;
    }

    @Override
    public String getConfigProfile() {
        return "openai";
    }
}
