package org.rj.modelgen.llm.state;

import java.util.concurrent.atomic.AtomicBoolean;

public class CancellationRequest {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

}
