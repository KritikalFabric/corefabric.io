package org.kritikal.fabric.metrics;

import org.kritikal.fabric.core.exceptions.FabricError;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Supplier;

/**
 * Created by ben on 16/06/15.
 */
public final class Drilldown<DATA extends DrilldownData, ITEM extends DbBuilder> {

    public Drilldown(ArrayList<Dimension> dimensions, int drilldowns) {
        this.dimensions = dimensions;
        init(drilldowns);
    };

    private final ArrayList<Dimension> dimensions;

    private void init(int drilldowns) {
        for (int i = 0, l = dimensions.size(); i < l; ++i) {

            // first item in the list is everything null, so global metrics
            if (i == 0 && drilldowns >= 0) {
                ArrayList<Dimension> combination = new ArrayList<>();
                coordinateCombinations.add(combination);
            }

            // next item in the list is this dimension, everything else null
            if (drilldowns >= 1)
            {
                ArrayList<Dimension> combination = new ArrayList<>();
                combination.add(dimensions.get(i).newInstance());
                coordinateCombinations.add(combination);
            }

            // every other pair combination
            if (drilldowns >= 2)
                for (int j = i+1; j < l; ++j)
                {
                    ArrayList<Dimension> combination = new ArrayList<>();
                    combination.add(dimensions.get(i).newInstance());
                    combination.add(dimensions.get(j).newInstance());
                    coordinateCombinations.add(combination);
                }

            // finally every other triple combination
            if (drilldowns >= 3)
                for (int j = i+1; j < l; ++j)
                    for (int k = j+1; k < l; ++k)
                    {
                        ArrayList<Dimension> combination = new ArrayList<>();
                        combination.add(dimensions.get(i).newInstance());
                        combination.add(dimensions.get(j).newInstance());
                        combination.add(dimensions.get(k).newInstance());
                        coordinateCombinations.add(combination);
                    }
        }
    }

    public Dimension getDimension(String name) {
        for (Dimension dimension : dimensions)
            if (dimension.getName().equals(name))
                return dimension;
        return null;
    }

    protected final ArrayList<ArrayList<Dimension>> coordinateCombinations = new ArrayList<>();
    protected final HashMap<CoordinatesPath, ITEM> reportItems = new HashMap<>();

    private void addCombinationsTo(DATA data, ArrayList<CoordinatesPath> coordinatesToConsider, Dimension dimension1) {
        for (Object value1 : data.getValuesFor(dimension1.getName())) {
            if (value1 != null) {
                coordinatesToConsider.add(new CoordinatesPath(
                        new Coordinates(dimension1, dimension1.getDimensionValueFor(value1))
                ));
            }
        }
    }

    private void addCombinationsTo(DATA data, ArrayList<CoordinatesPath> coordinatesToConsider, Dimension dimension1, Dimension dimension2) {
        for (Object value1 : data.getValuesFor(dimension1.getName())) {
            for (Object value2 : data.getValuesFor(dimension2.getName())) {
                if (value1 != null && value2 != null) {
                    coordinatesToConsider.add(new CoordinatesPath(
                            new Coordinates(dimension1, dimension1.getDimensionValueFor(value1)),
                            new Coordinates(dimension2, dimension2.getDimensionValueFor(value2))
                    ));
                }
            }
        }
    }

    private void addCombinationsTo(DATA data, ArrayList<CoordinatesPath> coordinatesToConsider, Dimension dimension1, Dimension dimension2, Dimension dimension3) {
        for (Object value1 : data.getValuesFor(dimension1.getName())) {
            for (Object value2 : data.getValuesFor(dimension2.getName())) {
                for (Object value3 : data.getValuesFor(dimension3.getName())) {
                    if (value1 != null && value2 != null && value3 != null) {
                        coordinatesToConsider.add(new CoordinatesPath(
                                new Coordinates(dimension1, dimension1.getDimensionValueFor(value1)),
                                new Coordinates(dimension2, dimension2.getDimensionValueFor(value2)),
                                new Coordinates(dimension3, dimension3.getDimensionValueFor(value3))
                        ));
                    }
                }
            }
        }
    }

    public ArrayList<Dimension> getDimensionCombination() {
        for (ArrayList<Dimension> axis : coordinateCombinations)
            if (axis.size() == 0)
                return axis;
        return null;
    }
    public ArrayList<Dimension> getDimensionCombination(String d1) {
        for (ArrayList<Dimension> axis : coordinateCombinations)
            if (axis.size() == 1)
                if (axis.get(0).getName().equals(d1))
                    return axis;
        return null;
    }
    public ArrayList<Dimension> getDimensionCombination(String d1, String d2) {
        for (ArrayList<Dimension> axis : coordinateCombinations)
            if (axis.size() == 2)
                if (    (axis.get(0).getName().equals(d1) && axis.get(1).getName().equals(d2)) ||
                        (axis.get(1).getName().equals(d1) && axis.get(0).getName().equals(d2)))
                    return axis;
        return null;
    }

    public ArrayList<Dimension> getDimensionCombination(String d1, String d2, String d3) {
        for (ArrayList<Dimension> axis : coordinateCombinations)
            if (axis.size() == 3) {
                for (int i = 0, l = axis.size(); i < l; ++i)
                    for (int j = 0; j < l; ++j)
                        for (int k = 0; k < l; ++k) {
                            if (i == j || i == k || j == k)
                                continue;
                            if (axis.get(i).getName().equals(d1) && axis.get(j).getName().equals(d2) && axis.get(k).getName().equals(d3))
                                return axis;
                        }
            }
        return null;
    }

    public final void consume(DATA data, Supplier<ITEM> newItem) {
        ArrayList<CoordinatesPath> coordinatesToConsider = new ArrayList<>();

        for (ArrayList<Dimension> axis : coordinateCombinations) {

            if (axis.size() == 0)
                coordinatesToConsider.add(new CoordinatesPath());
            else if (axis.size() == 1)
                addCombinationsTo(data, coordinatesToConsider, axis.get(0));
            else if (axis.size() == 2)
                addCombinationsTo(data, coordinatesToConsider, axis.get(0), axis.get(1));
            else if (axis.size() == 3)
                addCombinationsTo(data, coordinatesToConsider, axis.get(0), axis.get(1), axis.get(2));
            else throw new FabricError();
        }

        for (CoordinatesPath coordinates : coordinatesToConsider) {
            ITEM item = reportItems.get(coordinates);
            if (item == null) {
                item = newItem.get();
                reportItems.put(coordinates, item);
            }
            ((Consumer<DATA>)item).consume(data);
        }
    }
}
