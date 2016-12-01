package org.kritikal.fabric.metrics;

import com.datastax.driver.core.Row;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;

/**
 * Created by ben on 20/02/15.
 */
public abstract class CountDistinct<S, T, U extends CountDistinct<S,T,U>> implements Combine<U>, Clone<U>, Aggregate, Consumer<T>, DbBuilder {

    public abstract U create();

    public abstract T createT();

    protected HashSet<T> set = new HashSet<>();

    @Override
    public U clone() {
        U u = create();
        u.set.addAll(this.set);
        return u;
    }

    public void consume(T t) {
        set.add(t);
    }

    public U combineWith(U other) {
        U o = create();
        o.set.addAll(this.set);
        o.set.addAll(other.set);
        return o;
    }

    public void mergeFrom(U other) {
        this.set.addAll(other.set);
    }

    public Long valueOf() {
        return (long)set.size();
    }
}
