package org.kritikal.fabric.metrics;

/**
 * Created by ben on 20/02/15.
 */
public interface Consumer<I> {

    public void consume(I i);

}
