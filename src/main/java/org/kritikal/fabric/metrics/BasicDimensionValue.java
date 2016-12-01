package org.kritikal.fabric.metrics;

import org.kritikal.fabric.core.exceptions.FabricError;
import org.kritikal.fabric.db.cassandra.CassandraDbHelper;
import org.kritikal.fabric.db.pgsql.PgDbHelper;

import java.util.UUID;

/**
 * Created by ben on 16/06/15.
 */
public class BasicDimensionValue<T> implements DimensionValue {

    final T t;

    public BasicDimensionValue(T t) {
        this.t = t;
    }

    @Override
    public String getStringValue() {
        return "{"+t.toString()+"}";
    }

    @Override
    public void addToInsertCql(StringBuilder sb) {
        sb.append(",");
        if (t instanceof Long) {
            sb.append((Long) t);
        } else if (t instanceof String) {
            sb.append(CassandraDbHelper.quote((String) t));
        } else if (t instanceof UUID) {
            sb.append(((UUID) t).toString());
        } else throw new FabricError();
    }

    @Override
    public void addToInsertSql(StringBuilder sb) {
        sb.append(",");
        if (t instanceof Long) {
            sb.append((Long) t);
        } else if (t instanceof String) {
            sb.append(PgDbHelper.quote((String) t));
        } else if (t instanceof UUID) {
            sb.append(PgDbHelper.quote((UUID) t));
        } else throw new FabricError();
    }
}
