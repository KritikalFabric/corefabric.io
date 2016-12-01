package org.kritikal.fabric.metrics;

/**
 * Created by ben on 17/06/15.
 */
public interface CoordinatesProvider {
    Dimension getDimension();
    String forSelectWhereClause(String fieldName);
}
