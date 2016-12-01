package org.kritikal.fabric.metrics;

/**
 * Created by ben on 23/02/15.
 */
public interface MetricsBuilder<T extends DbDataWarehouse> {

    public String getTableName();

    public T getMetrics();

}
