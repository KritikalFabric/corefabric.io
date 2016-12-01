package org.kritikal.fabric.metrics;

/**
 * Created by ben on 21/02/15.
 */
public interface ToValueGetKey<T, U> {

    public T toValue();

    public U getKey();

}
