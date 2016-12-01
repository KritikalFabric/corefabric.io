package org.kritikal.fabric.metrics;

import com.datastax.driver.core.Row;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ben on 20/02/15.
 */
public abstract class DistinctValueSum<S extends ToValueGetKey<T, U>, T extends CombineClone<T>, U, Z extends DistinctValueSum<S,T,U,Z>> implements Combine<Z>, Consumer<S>, DbBuilder {

    public abstract Z create();

    public HashMap<U, T> map = new HashMap<>();

    public void consume(S s) {
        U u = s.getKey();
        T t = map.get(u);
        if (t == null) {
            map.put(u, s.toValue());
        } else {
            map.put(u, t.combineWith(s.toValue()));
        }
    }

    public Z combineWith(Z other) {
        Z o = create();
        for (Map.Entry<U, T> entry : this.map.entrySet()) {
            o.map.put(entry.getKey(), entry.getValue().clone());
        }
        for (Map.Entry<U, T> entry : other.map.entrySet()) {
            T t = o.map.get(entry.getKey());
            if (t == null) {
                o.map.put(entry.getKey(), entry.getValue().clone());
            } else {
                o.map.put(entry.getKey(), t.combineWith(entry.getValue()));
            }
        }
        return o;
    }

    public void mergeFrom(Z other) {
        for (Map.Entry<U, T> entry : other.map.entrySet()) {
            T found = this.map.putIfAbsent(entry.getKey(), entry.getValue());
            if (found != null)
                found.mergeFrom(entry.getValue());
        }
    }

    public abstract boolean addToInsertCql(StringBuilder sb);

    public abstract int readFromCassandraResultSet(Row rs, int i);
}
