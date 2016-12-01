package org.kritikal.fabric.metrics;

/**
 * Created by ben on 23/02/15.
 */
public interface CombineClone<T> {

    public T clone();

    public T combineWith(T other);

    public void mergeFrom(T other);

}
