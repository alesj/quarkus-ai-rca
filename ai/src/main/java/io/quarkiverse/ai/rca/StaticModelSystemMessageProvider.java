package io.quarkiverse.ai.rca;

/**
 * Use this SMP when you only have a single model usage.
 */
public class StaticModelSystemMessageProvider extends BaseModelSystemMessageProvider {

    private volatile String msg;

    protected String getSystemMessage(String key) {
        if (msg == null) {
            msg = loadSystemMessage(key);
        }
        capture().accept(msg);
        return msg;
    }
}
