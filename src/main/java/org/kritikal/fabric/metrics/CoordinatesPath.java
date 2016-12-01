package org.kritikal.fabric.metrics;

/**
 * Created by ben on 16/06/15.
 */
public class CoordinatesPath {

    public final String path;
    public final String dimensionPath;

    public CoordinatesPath() {
        coordinates1 = null;
        coordinates2 = null;
        coordinates3 = null;
        path = "/";
        dimensionPath = "/";
    }

    public CoordinatesPath(Coordinates coordinates1) {
        this.coordinates1 = coordinates1;
        this.coordinates2 = null;
        this.coordinates3 = null;
        path = coordinates1.path;
        dimensionPath = "/" + coordinates1.dimension.getName();
    }

    public CoordinatesPath(Coordinates coordinates1, Coordinates coordinates2) {
        this.coordinates1 = coordinates1;
        this.coordinates2 = coordinates2;
        this.coordinates3 = null;
        path = coordinates1.path + coordinates2.path;
        dimensionPath = "/" + coordinates1.dimension.getName() + "/" + coordinates2.dimension.getName();
    }

    public CoordinatesPath(Coordinates coordinates1, Coordinates coordinates2, Coordinates coordinates3) {
        this.coordinates1 = coordinates1;
        this.coordinates2 = coordinates2;
        this.coordinates3 = coordinates3;
        path = coordinates1.path + coordinates2.path + coordinates3.path;
        dimensionPath = "/" + coordinates1.dimension.getName() + "/" + coordinates2.dimension.getName() + "/" + coordinates3.dimension.getName();
    }

    protected final Coordinates coordinates1;
    protected final Coordinates coordinates2;
    protected final Coordinates coordinates3;

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CoordinatesPath)) return false;
        return path.equals(((CoordinatesPath) obj).path);
    }
}
