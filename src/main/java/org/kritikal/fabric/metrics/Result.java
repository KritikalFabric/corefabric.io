package org.kritikal.fabric.metrics;

/**
 * Created by ben on 23/02/15.
 */
public interface Result<T extends DbBuilder> {

    public T getResult();

}
