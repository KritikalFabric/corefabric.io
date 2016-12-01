package org.kritikal.fabric.metrics;

import org.kritikal.fabric.core.exceptions.FabricError;

import java.util.ArrayList;

/**
 * Created by ben on 17/06/15.
 */
public class CoordinatesQuery {
    public final CoordinatesProvider coordinatesProvider1;
    public final CoordinatesProvider coordinatesProvider2;
    public final CoordinatesProvider coordinatesProvider3;

    public CoordinatesQuery(ArrayList<Dimension> axis) {
        if (axis.size() > 0) throw new FabricError("Invalid axis");
        coordinatesProvider1 = null;
        coordinatesProvider2 = null;
        coordinatesProvider3 = null;
    }

    public CoordinatesQuery(ArrayList<Dimension> axis, CoordinatesProvider coordinatesProvider1) {
        if (axis.size() > 1) throw new FabricError("Invalid axis");
        if (axis.get(0).getName().equals(coordinatesProvider1.getDimension().getName())) {
            this.coordinatesProvider1 = coordinatesProvider1;
            this.coordinatesProvider2 = null;
            this.coordinatesProvider3 = null;
        } else {
            throw new FabricError("Axis mis-match");
        }
    }

    public CoordinatesQuery(ArrayList<Dimension> axis, CoordinatesProvider coordinatesProvider1, CoordinatesProvider coordinatesProvider2) {
        if (axis.size() > 2) throw new FabricError("Invalid axis");
        if (    axis.get(0).getName().equals(coordinatesProvider1.getDimension().getName()) &&
                axis.get(1).getName().equals(coordinatesProvider2.getDimension().getName())) {
            this.coordinatesProvider1 = coordinatesProvider1;
            this.coordinatesProvider2 = coordinatesProvider2;
            this.coordinatesProvider3 = null;
        } else if (axis.get(0).getName().equals(coordinatesProvider2.getDimension().getName()) &&
                   axis.get(1).getName().equals(coordinatesProvider1.getDimension().getName())) {
            this.coordinatesProvider1 = coordinatesProvider2;
            this.coordinatesProvider2 = coordinatesProvider1;
            this.coordinatesProvider3 = null;
        } else {
            throw new FabricError("Axis mis-match");
        }
    }

    public CoordinatesQuery(ArrayList<Dimension> axis, CoordinatesProvider coordinatesProvider1, CoordinatesProvider coordinatesProvider2, CoordinatesProvider coordinatesProvider3) {
        if (axis.size() > 3) throw new FabricError("Invalid axis");
        if (    axis.get(0).getName().equals(coordinatesProvider1.getDimension().getName()) &&
                axis.get(1).getName().equals(coordinatesProvider2.getDimension().getName()) &&
                axis.get(2).getName().equals(coordinatesProvider3.getDimension().getName())) {
            this.coordinatesProvider1 = coordinatesProvider1;
            this.coordinatesProvider2 = coordinatesProvider2;
            this.coordinatesProvider3 = coordinatesProvider3;
        } else if ( axis.get(0).getName().equals(coordinatesProvider2.getDimension().getName()) &&
                axis.get(1).getName().equals(coordinatesProvider1.getDimension().getName()) &&
                axis.get(2).getName().equals(coordinatesProvider3.getDimension().getName())) {
            this.coordinatesProvider1 = coordinatesProvider2;
            this.coordinatesProvider2 = coordinatesProvider1;
            this.coordinatesProvider3 = coordinatesProvider3;
        } else if ( axis.get(0).getName().equals(coordinatesProvider2.getDimension().getName()) &&
                axis.get(1).getName().equals(coordinatesProvider3.getDimension().getName()) &&
                axis.get(2).getName().equals(coordinatesProvider1.getDimension().getName())) {
            this.coordinatesProvider1 = coordinatesProvider2;
            this.coordinatesProvider2 = coordinatesProvider3;
            this.coordinatesProvider3 = coordinatesProvider1;
        } else if ( axis.get(0).getName().equals(coordinatesProvider3.getDimension().getName()) &&
                axis.get(1).getName().equals(coordinatesProvider2.getDimension().getName()) &&
                axis.get(2).getName().equals(coordinatesProvider1.getDimension().getName())) {
            this.coordinatesProvider1 = coordinatesProvider3;
            this.coordinatesProvider2 = coordinatesProvider2;
            this.coordinatesProvider3 = coordinatesProvider1;
        } else if ( axis.get(0).getName().equals(coordinatesProvider3.getDimension().getName()) &&
                axis.get(1).getName().equals(coordinatesProvider1.getDimension().getName()) &&
                axis.get(2).getName().equals(coordinatesProvider2.getDimension().getName())) {
            this.coordinatesProvider1 = coordinatesProvider3;
            this.coordinatesProvider2 = coordinatesProvider1;
            this.coordinatesProvider3 = coordinatesProvider2;
        } else {
            throw new FabricError("Axis mis-match");
        }
    }
}
