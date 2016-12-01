package org.kritikal.fabric.metrics;

import org.kritikal.fabric.core.exceptions.FabricError;
import org.kritikal.fabric.db.pgsql.PgDbHelper;

import java.util.UUID;

/**
 * Created by ben on 17/06/15.
 */
public class BasicCoordinatesProvider<T> implements CoordinatesProvider {

    private final Dimension dimension;
    private final T t;

    public BasicCoordinatesProvider(Dimension dimension, T t) {
        this.dimension = dimension;
        this.t = t;
    }

    public Dimension getDimension() {
        return dimension;
    }

    public String forSelectWhereClause(String fieldName) {
        if (t instanceof Long) {
            return fieldName + " = " + PgDbHelper.quote((Long) t);
        } else if (t instanceof String) {
            return fieldName + " = " + PgDbHelper.quote(PgDbHelper.varcharTrim((String) t, 128));
        } else if (t instanceof UUID) {
            return fieldName + " = " + PgDbHelper.quote((UUID) t);
        } else throw new FabricError();
    }
}
