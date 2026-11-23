# AI Root Cause Analysis (AI RCA)

[![Maven Central](https://img.shields.io/maven-central/v/io.quarkiverse.ai.rca/quarkus-ai-rca-core.svg?label=Maven%20Central)](https://search.maven.org/artifact/io.quarkiverse.ai.rca/quarkus-ai-rca-core)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

LLM-powered Root Cause Analysis of distributed systems using Quarkus, LangChain4j, and pluggable observability platforms (Grafana LGTM, Elastic) via MCP.

For detailed documentation, see the [docs](docs/modules/ROOT/pages/index.adoc).

## Requirements

* **Java 21+** and **Maven 3.9+** (or use the included `./mvnw` wrapper)
* **Docker** or **Podman** — Quarkus Dev Services automatically starts the observability stack (LGTM or Elastic), MySQL, and Toxiproxy containers
* **[uvx](https://docs.astral.sh/uv/)** — used to run the `mcp-grafana` MCP server for LGTM (`pip install uv` or `brew install uv`); not needed for Elastic
* **LLM API key** — at least one of: `OPENAI_API_KEY`, `GROK_API_KEY`, `GEMINI_API_KEY`, or `WATSONX_API_KEY`

## How to run / test the AI part

From project root, run this 3 commands

(1) ./mvn.proxy.sh quarkus:dev

Wait for (1) to **fully** start, so (2) and (3) share the **same** LGTM resources.

(2) ./mvn.app.sh quarkus:dev

(3) ./mvn.ai.sh quarkus:dev

In (3) you can select the LLM provider via `-Dllm.provider=<name>` (defaults to `openai` via `.mvn/maven.config`):
* `-Dllm.provider=openai` - OpenAI (default)
* `-Dllm.provider=grok` - Grok xAI
* `-Dllm.provider=watsonx` - WatsonX
* `-Dllm.provider=gemini` - Gemini

Similarly, the observability platform is selected via `-Dobservability.platform=<name>` (defaults to `lgtm`):
* `-Dobservability.platform=lgtm` - Grafana LGTM (default) — Tempo + Grafana MCP
* `-Dobservability.platform=elastic` - Elastic — Kibana Agent Builder MCP

Each value activates the corresponding Maven profile, which sets `quarkus.profile` and pulls in the right dependencies.

---

(1) Starts a simple `proxy` app, that just forwards the request to an "actual" `app`. 

(2) Starts an `app` that based on the `poke` value returns http code.

```java
    @GET
    @Path("/poke")
    @Produces(MediaType.TEXT_PLAIN)
    public Response poke(@QueryParam("value") Integer value) {
        log.info("Poking ... " + value);
        arr[0] = value;
        if (inRange(400, 600, value)) {
            Response.Status status = Response.Status.fromStatusCode(value);
            throw new WebApplicationException(
                    "App error: " + status.getStatusCode(),
                    Response.status(status)
                            .entity(status.getReasonPhrase())
                            .build()
            );
        }
        Integer copy = value;
        if (!inRange(200, 600, value)) {
            copy = 200; // plain ok
        }
        return Response.status(copy).entity("Poked with " + value).build();
    }
```

(3) Starts the actual AI analysis application.

Set the API key for your chosen LLM provider before starting the AI module:

```bash
export OPENAI_API_KEY=sk-...   # for default (llm.provider=openai)
export GROK_API_KEY=xai-...    # for -Dllm.provider=grok
```

To enable Dev MCP (source examination, dashboard generation), the companion apps must have `quarkus.dev-mcp.enabled=true` in their `application.properties`, and the AI module needs to know their ports:

```bash
./mvn.ai.sh quarkus:dev -Dapp.ports=8081,8082
```

---

Poke the `proxy` app via browser or curl: `http://localhost:8081/poke?value=<your choice of http code>`

Poke it a few times with different `http codes`, so we get different traces, logs, metrics, ...

---

You can check any app's DevUI on where the Grafana / LGTM is available - for any other queries, etc

### Standalone AI module

To quickly start just the AI module with Dev Services (LGTM auto-starts):

```bash
./dev-ai.sh              # analysis only (no source exam / dashboard write)
./dev-ai.sh 8081,8082    # with Dev MCP for source exam + dashboard generation
```

This runs the AI module in Quarkus dev mode, automatically starting LGTM via Dev Services. The ports argument is optional — it tells the AI module which application ports to connect to via Dev MCP for source examination and writing dashboards into your app sources. The target applications must be running in Quarkus dev mode (which exposes the Dev MCP endpoint).

For production use with an existing observability stack, use a pre-built uber-jar.

Pre-built runner JARs are published to [GitHub Packages](https://github.com/quarkiverse/quarkus-ai-rca/packages) for each combination of observability platform and LLM provider:

| Platform | LLM | Classifier |
|---|---|---|
| LGTM | OpenAI | `lgtm-openai` |
| LGTM | Gemini | `lgtm-gemini` |
| LGTM | WatsonX | `lgtm-watsonx` |
| Elastic | OpenAI | `elastic-openai` |
| Elastic | Gemini | `elastic-gemini` |
| Elastic | WatsonX | `elastic-watsonx` |

Grok (xAI) reuses the OpenAI runner with a different base URL — use the `openai` variant and set `GROK_API_KEY`.

Download a runner JAR (replace `VERSION` and `CLASSIFIER`):

```bash
mvn dependency:copy \
  -Dartifact=io.quarkiverse.ai.rca:quarkus-ai-rca-runner:VERSION:jar:CLASSIFIER \
  -DremoteRepositories=github::default::https://maven.pkg.github.com/quarkiverse/quarkus-ai-rca \
  -DoutputDirectory=.
```

Run against your LGTM stack:

```bash
java -Dgrafana.endpoint=http://localhost:3000 \
     -Dtempo-mcp.endpoint=http://localhost:3200 \
     -jar quarkus-ai-rca-runner-*-lgtm-openai.jar
```

Run against your Elastic stack:

```bash
java -Delasticsearch.endpoint=http://localhost:9200 \
     -Dkibana.endpoint=http://localhost:5601 \
     -jar quarkus-ai-rca-runner-*-elastic-openai.jar
```

Or build and run locally:

```bash
./mvn.ai.sh package -DskipTests
./run-ai.sh http://localhost:3000 http://localhost:3200              # analysis only
./run-ai.sh http://localhost:3000 http://localhost:3200 8081,8082    # with app ports
```

### Manual LGTM (without DevServices)

Use `run-lgtm.sh` to start LGTM and the AI module in one command:

```bash
./run-lgtm.sh                  # no app.ports
./run-lgtm.sh 8081,8082        # with app.ports for Dev MCP
```

Or do it manually. To connect to an externally started LGTM stack instead of using Quarkus DevServices, start LGTM manually:

```bash
docker run -p 3000:3000 -p 4317:4317 -p 4318:4318 -p 3200:3200 grafana/otel-lgtm:0.24.0
```

Then run each Quarkus app with the `lgtm` profile. The `ai` module also needs an LLM profile (e.g. `openai`, `grok`) — Quarkus supports multiple comma-separated profiles:

```bash
./mvn.proxy.sh quarkus:dev -Dquarkus.profile=lgtm
./mvn.app.sh quarkus:dev -Dquarkus.profile=lgtm
./mvn.ai.sh quarkus:dev -Dquarkus.profile=lgtm,openai
```

---

## Web UI

Once the `ai` app is running (step 3), open `http://localhost:8080` in a browser to access the **AI Root Cause Analysis** web UI.

The UI provides:
* **Traces** — number of recent traces to analyze (1–20)
* **Output Format** — HTML, Markdown, Plain Text, or AsciiDoc
* **Examine Source** — optionally include source code examination in the analysis
* **Create Dashboard** — optionally generate a dashboard JSON from the analysis

Click **Analyze** to call `/analyze/{n}` and view results in three collapsible sections:
1. **Analysis** — the main AI-generated trace/log/metric analysis (always present)
2. **Examined Sources** — source code insights rendered as Markdown (when enabled)
3. **Dashboard** — generated dashboard definition as JSON (when enabled)

Each section has a **Copy** button for clipboard export.

---

## Integration tests

Use `run-integration-test.sh` to run integration tests with flexible LLM and test type selection.

The script takes up to three arguments:
1. **Test type** — `chaos` (ChaosIntegrationTest), `db` (DbIntegrationTest), or `weather` (WeatherIntegrationTest) (default: `chaos`)
2. **AI profile** — which LLM powers `TelemetryAiService` / `DevMcpAiService` (default: `openai`)
3. **Scorer** — which LLM scores the test evaluation (default: `openai`)

```bash
./run-integration-test.sh                       # chaos, both openai
./run-integration-test.sh chaos grok            # chaos, AI=grok, scorer=openai
./run-integration-test.sh chaos openai grok     # chaos, AI=openai, scorer=grok
./run-integration-test.sh chaos grok grok       # chaos, both grok
./run-integration-test.sh db openai             # db, AI=openai, scorer=openai
./run-integration-test.sh db grok grok          # db, both grok
./run-integration-test.sh weather openai grok   # weather, AI=openai, scorer=grok
```

Run all test types (chaos, db, weather) for a given platform and AI combo:
```bash
./run-all-integration-tests.sh <platform> <ai> <scorer>

./run-all-integration-tests.sh elastic openai grok   # Elastic, AI=openai, scorer=grok
./run-all-integration-tests.sh elastic grok openai   # Elastic, AI=grok, scorer=openai
./run-all-integration-tests.sh lgtm openai grok      # LGTM, AI=openai, scorer=grok
```

Supported AI profiles: `openai`, `grok`, `gemini`, `watsonx`.
Supported scorers: `openai` (default), `grok`, `watsonx`.

Grok (xAI) reuses the `quarkus-langchain4j-openai` extension with `base-url=https://api.x.ai/v1`.
Set `GROK_API_KEY` env var before running with grok.
Set `WATSONX_BASE_URL`, `WATSONX_API_KEY`, and `WATSONX_PROJECT_ID` env vars before running with watsonx.

### Running specific test methods

Use `run-integration-methods.sh` to run individual test methods:

```bash
./run-integration-methods.sh <chaos|db|weather> <ai-profile> [scorer] <method1> [method2] ...
```

Pass an empty string `''` for scorer to use the default (openai).

```bash
# Chaos tests
./run-integration-methods.sh chaos openai grok analyzeIntermittentFailures analyzeNetworkPartition
./run-integration-methods.sh chaos grok '' analyzeRequestFlood

# DB tests
./run-integration-methods.sh db openai '' analyzeNormalTraffic

# Weather tests
./run-integration-methods.sh weather openai grok analyzeWeatherSlowApi
```

Available chaos test methods:

| Method | Scenario |
|---|---|
| `analyzeNormalTraffic` | Baseline healthy requests |
| `analyzeErrorTraffic` | HTTP 500, 403, random 5xx |
| `analyzeLatency` | Injected delays (3-5s) |
| `analyzeResourcePressure` | Memory allocation, CPU burn, memory leaks |
| `analyzeCascadingFailure` | Exception + threadpool + GC churn |
| `analyzeLockContention` | Synchronized lock contention |
| `analyzeIntermittentFailures` | ~60% random failure rate |
| `analyzeNetworkPartition` | App stopped/restarted mid-test |
| `analyzeRequestFlood` | Multiple delayed requests + error |
| `analyzeDeadlock` | Thread deadlock with timeout detection |
| `examineSourceCode` | Source code examination via Dev MCP |
| `generateDashboard` | Grafana dashboard generation from analysis |

Available db test methods:

| Method | Scenario |
|---|---|
| `analyzeNormalTraffic` | Database person queries (by name, surname) |
| `analyzeSlowQueries` | Slow queries via Toxiproxy latency injection (3s delay) |
| `analyzePoolExhaustion` | Connection pool exhaustion via 4s latency + small pool + concurrent requests |
| `analyzeDbOutage` | Database outage via Toxiproxy connection cut |

Available weather test methods:

| Method | Scenario |
|---|---|
| `analyzeWeatherNormal` | REST client calls to Open-Meteo API, healthy state |
| `analyzeWeatherSlowApi` | Toxiproxy 3s latency on weather API |
| `analyzeWeatherApiOutage` | Weather API outage via Toxiproxy connection cut |
