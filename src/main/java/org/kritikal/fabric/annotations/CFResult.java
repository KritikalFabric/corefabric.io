package org.kritikal.fabric.annotations;

public class CFResult<T> {
    public final boolean success;
    public final Throwable throwable;
    public final String message;
    public final T t;
    public CFResult(boolean success, T result) {
        super();
        this.success = success;
        this.t = result;
        this.throwable = null;
        this.message = null;
    }
    public CFResult(Throwable throwable) {
        super();
        this.success = false;
        this.t = null;
        this.throwable = throwable;
        this.message = null;
    }
    public CFResult(String message) {
        super();
        this.success = false;
        this.t = null;
        this.throwable = null;
        this.message = message;
    }
    public CFResult() {
        super();
        this.success = false;
        this.t = null;
        this.throwable = null;
        this.message = null;
    }
    public boolean succeeded() { return success; }
    public boolean failed() { return !success; }
    public Throwable cause() { return throwable; }
    public T result() { return t; }
}
