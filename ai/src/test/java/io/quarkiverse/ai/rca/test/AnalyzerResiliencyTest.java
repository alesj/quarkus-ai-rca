package io.quarkiverse.ai.rca.test;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.resiliencytesting.Faults;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
@TestProfile(ResiliencyTestProfile.class)
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class AnalyzerResiliencyTest {

    @Inject
    Faults faults;

    @Test
    void analyzeGracefulWhenLlmUnavailable() {
        try (var ignored = faults.target(ChatModel.class).unavailable()) {
            given()
                    .when().get("/analyze/1")
                    .then()
                    .statusCode(200)
                    .body("error", notNullValue())
                    .body("error", containsString("Analysis failed"))
                    .body("analysis", nullValue());
        }
    }

    @Test
    void analyzeGracefulWhenLlmHangs() {
        try (var ignored = faults.target(ChatModel.class).hangs()) {
            given()
                    .when().get("/analyze/1")
                    .then()
                    .statusCode(200)
                    .body("error", notNullValue())
                    .body("analysis", nullValue());
        }
    }
}
