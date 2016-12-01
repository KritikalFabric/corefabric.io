package org.kritikal.fabric.metrics;

import java.util.Collection;
import java.util.HashMap;

/**
 * Created by ben on 16/06/15.
 */
public abstract class BasicDimension<T> implements Dimension {

    public BasicDimension(String name) {
        this.name = name;
    }

    final String name;

    final HashMap<T, BasicDimensionValue<T>> values = new HashMap<>();

    public BasicDimensionValue<T> getDimensionValueFor(Object object) {
        T value = (T)object;
        BasicDimensionValue<T> dimensionValue = values.get(value);
        if (dimensionValue == null) {
            dimensionValue = new BasicDimensionValue<>(value);
            if (value != null)
                values.put(value, new BasicDimensionValue<T>(value));
        }
        return dimensionValue;
    }

    @Override
    public Collection<? extends DimensionValue> getAllValues() {
        return values.values();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return name.equals(((BasicDimension<T>)o).name);

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        return result;
    }
}
