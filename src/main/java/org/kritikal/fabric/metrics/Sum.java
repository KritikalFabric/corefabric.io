package org.kritikal.fabric.metrics;

import com.datastax.driver.core.Row;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by ben on 06/06/15.
 */
public abstract class Sum<T extends Sum<T>> implements Combine<T>, Clone<T>, Aggregate, Consumer<Long>, DbBuilder {

    public abstract T createT();

    protected long value = 0;

    public void consume(Long delta)
    {
        value += delta;
    }

    public T combineWith(T other) {
        T o = createT();
        o.value = this.value + other.value;
        return o;
    }

    public void mergeFrom(T other) {
        this.value += other.value;
    }

    public T clone() {
        T o = createT();
        o.value = this.value;
        return o;
    }

    public Long valueOf() {
        return value;
    }

    @Override
    public boolean addToInsertCql(StringBuilder sb) {
        sb.append(value);
        return true;
    }

    @Override
    public int readFromCassandraResultSet(Row rs, int i) {
        value = rs.getLong(i++);
        return i;
    }
}
