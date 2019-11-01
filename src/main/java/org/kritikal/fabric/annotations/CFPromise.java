package org.kritikal.fabric.annotations;

import java.util.function.Consumer;

public class CFPromise<T> {
    public void complete() {
        throw new CFPromiseEnd(new CFResult<T>(true, null));
    }
    public void complete(T t) {
        throw new CFPromiseEnd(new CFResult<T>(true, t));
    }
    public void fail(String message) {
        throw new CFPromiseEnd(new CFResult<T>(message));
    }
    public void fail(Throwable throwable) {
        throw new CFPromiseEnd(new CFResult<T>(throwable));
    }
    public void withErrorChecking(Consumer<CFPromise<T>> consumer, boolean ignored, Consumer<CFResult<T>> consumerComplete) {
        withErrorChecking(consumer, consumerComplete);
    }
    public void withErrorChecking(Consumer<CFPromise<T>> consumer, Consumer<CFResult<T>> consumerComplete) {
        CFResult result = new CFResult();
        try {
            consumer.accept(this);
        }
        catch (CFPromiseEnd end) {
            try {
                result = (CFResult<T>) end.cfResult;
            }
            catch (ClassCastException cce) {
                // ignore
            }
        }
        catch (Throwable t) {
            result = new CFResult<T>(t);
        }

        consumerComplete.accept(result);
    }
}
