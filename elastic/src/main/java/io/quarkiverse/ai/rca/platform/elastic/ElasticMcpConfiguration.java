package io.quarkiverse.ai.rca.platform.elastic;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import io.quarkiverse.ai.rca.StripMcpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkiverse.langchain4j.mcp.runtime.http.QuarkusStreamableHttpMcpTransport;
import io.quarkus.arc.properties.IfBuildProperty;
import io.vertx.core.Vertx;
import jakarta.enterprise.inject.Disposes;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@IfBuildProperty(name = "test.fixture-mcp", stringValue = "false", enableIfMissing = true)
public class ElasticMcpConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ElasticMcpConfiguration.class);

    @ConfigProperty(name = "kibana.endpoint")
    String kibanaEndpoint;

    @ConfigProperty(name = "elastic.mcp.api-key")
    Optional<String> elasticMcpApiKey;

    @Singleton
    @McpClientName("kibana")
    public McpClient kibanaMcpClient(Vertx vertx) {
        var builder = new QuarkusStreamableHttpMcpTransport.Builder()
                .mcpClientName("kibana")
                .url(kibanaEndpoint + "/api/agent_builder/mcp")
                .httpClient(vertx.createHttpClient());

        elasticMcpApiKey.ifPresent(apiKey ->
                builder.headers(Map.of("Authorization", "ApiKey " + apiKey)));

        McpTransport transport = builder.build();
        DefaultMcpClient client = new DefaultMcpClient.Builder()
                .clientName("kibana")
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(30))
                .build();
        return new StripMcpClient(client, ElasticStripFunctions.ESQL);
    }

    public void destroyMcpClient(@Disposes McpClient client) throws Exception {
        log.info("Closing the mcp client: " + client.key());
        client.close();
    }
}
