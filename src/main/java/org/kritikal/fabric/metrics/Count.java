package org.kritikal.fabric.metrics;

import com.datastax.driver.core.Row;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by ben on 20/02/15.
 */
public abstract class Count<T extends Count<T>> implements Combine<T>, Clone<T>, Aggregate, Consumer<Void>, DbBuilder {

    public abstract T createT();

    protected long value = 0;

    public void setValue(long newValue) { value = newValue; }

    public void consume(Void v)
    {
        ++value;
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

    @Override
    public boolean addToInsertSql(StringBuilder sb) {
        sb.append(value);
        return true;
    }

    @Override
    public int readFromPostgresResultSet(ResultSet rs, int i) throws SQLException {
        value = rs.getLong(i++);
        return i;
    }
}
