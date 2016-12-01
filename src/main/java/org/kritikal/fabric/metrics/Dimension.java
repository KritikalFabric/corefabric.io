package org.kritikal.fabric.metrics;

import java.util.Collection;

/**
 * Created by ben on 16/06/15.
 */
public interface Dimension {

    DimensionValue getDimensionValueFor(Object object);

    Collection<? extends DimensionValue> getAllValues();

    Dimension newInstance();

    String getName();

    void columnDefinitions(StringBuilder sb, String name);

    void columnNames(StringBuilder sb, String name);

    void indexDefinitions(StringBuilder sb, String tableName, String name, String suffix);

}
