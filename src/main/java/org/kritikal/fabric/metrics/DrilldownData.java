package org.kritikal.fabric.metrics;

import java.util.Collection;

/**
 * Created by ben on 16/06/15.
 */
public interface DrilldownData {

    Collection<? extends Object> getValuesFor(String dimensionName);

}
