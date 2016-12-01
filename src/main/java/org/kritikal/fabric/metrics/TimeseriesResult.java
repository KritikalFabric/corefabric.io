package org.kritikal.fabric.metrics;

/**
 * Created by ben on 23/02/15.
 */
public class TimeseriesResult<T extends DbBuilder> implements Result<T> {

    public TimeseriesResult(java.util.Date t1, java.util.Date t2, T t) {
        this.t1 = t1;
        this.t2 = t2;
        this.t = t;
    }

    private final java.util.Date t1;
    private final java.util.Date t2;
    private T t;

    @Override
    public T getResult() {
        return t;
    }

    public java.util.Date getStart() {
        return t1;
    }

    public java.util.Date getEnd() {
        return t2;
    }
}
