package org.kritikal.fabric.metrics;

import java.util.function.Supplier;

/**
 * Created by ben on 20/02/15.
 */
public interface Combine<T> {

    public T combineWith(T other);

    public void mergeFrom(T other);

}
