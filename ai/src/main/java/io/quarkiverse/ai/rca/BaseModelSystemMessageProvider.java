package io.quarkiverse.ai.rca;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.ModelProvider;
import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProviderWithContext;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import org.jboss.logging.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

public abstract class BaseModelSystemMessageProvider implements SystemMessageProviderWithContext {

    protected final Logger log = Logger.getLogger(getClass());

    private volatile ObservabilityPlatform platform;

    protected Consumer<String> capture() {
        InstanceHandle<ToolOutputCapture> instance = Arc.container().instance(ToolOutputCapture.class);
        return instance.isAvailable() ? instance.get() : s -> {
        };
    }

    private ObservabilityPlatform platform() {
        if (platform == null) {
            InstanceHandle<ObservabilityPlatform> instance = Arc.container().instance(ObservabilityPlatform.class);
            if (instance.isAvailable()) {
                platform = instance.get();
            }
        }
        return platform;
    }

    @Override
    public Optional<String> getSystemMessage(InvocationContext context) {
        ModelProvider provider = context.modelProvider();
        String model = context.defaultRequestParameters().modelName();
        ObservabilityPlatform p = platform();
        String platformName = p != null ? p.platformName() : "unknown";
        String key = String.format("%s_%s_%s", platformName, provider, model).toLowerCase(Locale.ROOT);
        String msg = getSystemMessage(key);
        return Optional.of(msg);
    }

    protected abstract String getSystemMessage(String key);

    protected String loadSystemMessage(String key) {
        InputStream is = getClass().getClassLoader().getResourceAsStream(key + ".txt");
        if (is != null) {
            try (is) {
                log.infof("Found %s system message.", key);
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.info("Could not read system message for: " + key, e);
            }
        }
        log.infof("System message for %s not found, fallback to base + platform fragment.", key);
        return loadBaseWithFragment();
    }

    private String loadBaseWithFragment() {
        String base = loadResource("base");
        if (base == null) {
            throw new RuntimeException("base.txt system message not found on classpath");
        }
        ObservabilityPlatform p = platform();
        if (p != null) {
            String fragment = p.promptFragment();
            if (fragment != null && !fragment.isBlank()) {
                log.infof("Appending %s platform prompt fragment.", p.platformName());
                return base + "\n\n" + fragment;
            }
        }
        return base;
    }

    private String loadResource(String name) {
        InputStream is = getClass().getClassLoader().getResourceAsStream(name + ".txt");
        if (is != null) {
            try (is) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.info("Could not read resource: " + name, e);
            }
        }
        return null;
    }
}
