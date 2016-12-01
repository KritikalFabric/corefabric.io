package org.kritikal.fabric.metrics;

/**
 * Created by ben on 16/06/15.
 */
public interface DimensionValue {

    String getStringValue();

    void addToInsertCql(StringBuilder sb);

    void addToInsertSql(StringBuilder sb);

}
