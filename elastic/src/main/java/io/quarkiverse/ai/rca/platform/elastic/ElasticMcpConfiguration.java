package io.quarkiverse.ai.rca.platform.elastic;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import io.quarkiverse.ai.rca.StripMcpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkiverse.langchain4j.mcp.runtime.http.QuarkusStreamableHttpMcpTransport;
import io.quarkus.arc.properties.IfBuildProperty;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
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
        String mcpUrl = kibanaEndpoint + "/api/agent_builder/mcp";
        log.info("Creating Kibana MCP client, url={}, apiKey={}", mcpUrl, elasticMcpApiKey.isPresent());
        try {
            var builder = new QuarkusStreamableHttpMcpTransport.Builder()
                    .mcpClientName("kibana")
                    .url(mcpUrl)
                    .httpClient(vertx.createHttpClient());

            elasticMcpApiKey.ifPresent(apiKey ->
                    builder.headers(Map.of("Authorization", "ApiKey " + apiKey)));

            McpTransport transport = builder.build();
            DefaultMcpClient client = new DefaultMcpClient.Builder()
                    .clientName("kibana")
                    .transport(transport)
                    .toolExecutionTimeout(Duration.ofSeconds(30))
                    .build();
            log.info("Kibana MCP client created successfully");
            StripMcpClient stripClient = new StripMcpClient(client, ElasticStripFunctions.ESQL);
            try {
                var tools = stripClient.listTools();
                log.info("Kibana MCP available tools ({}):", tools.size());
                tools.forEach(t -> log.info("  tool: {} -- {}", t.name(), t.description()));
            } catch (Exception e) {
                log.warn("Failed to list Kibana MCP tools", e);
            }
            return stripClient;
        } catch (Exception e) {
            log.error("Failed to create Kibana MCP client", e);
            throw e;
        }
    }

    public void destroyMcpClient(@Disposes McpClient client) throws Exception {
        log.info("Closing the mcp client: " + client.key());
        client.close();
    }
}
