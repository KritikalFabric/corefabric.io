package org.kritikal.fabric.metrics;

/**
 * Created by ben on 16/06/15.
 */
public class Coordinates {

    public Coordinates(Dimension dimension, DimensionValue dimensionValue) {
        this.dimension = dimension;
        this.dimensionValue = dimensionValue;
        this.path = "/" + dimension.getName() + "={" + dimensionValue.getStringValue() + "}";
    }

    protected final Dimension dimension;
    protected final DimensionValue dimensionValue;
    protected final String path;

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Coordinates)) return false;
        return path.equals(((Coordinates) obj).path);
    }
}
