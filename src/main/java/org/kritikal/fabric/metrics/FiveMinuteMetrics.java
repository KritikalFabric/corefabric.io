package org.kritikal.fabric.metrics;

import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import org.kritikal.fabric.db.pgsql.ConnectionInformation;
import org.kritikal.fabric.db.pgsql.DWWorkerVerticle;
import io.vertx.core.logging.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * Created by ben on 21/02/15.
 */
public final class FiveMinuteMetrics<T extends DbDataWarehouse> extends Period implements Time, MetricsBuilder<T> {

    public FiveMinuteMetrics(String tableName, java.sql.Timestamp today, T t) {
        this.tableName = tableName;

        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime(new Date(today.getTime()));
        int minute = cal.get(GregorianCalendar.MINUTE);
        minute = minute - minute % 5;
        cal.set(GregorianCalendar.MINUTE, minute);
        cal.set(GregorianCalendar.SECOND, 0);
        cal.set(GregorianCalendar.MILLISECOND, 0);

        start = cal.getTime().getTime();
        cal.add(GregorianCalendar.MINUTE, 5);
        end = cal.getTime().getTime();

        this.t = t;
    }

    final String tableName;

    final T t;

    public T getMetrics() {
        return t;
    }

    public String getTableName() { return tableName; }

    public String createDdl(String keyspace) {
        return t.createDdl(keyspace, tableName);
    }

    public void insert(Logger logger, ConnectionInformation ci, ArrayList<ResultSetFuture> futures, String keyspace) throws SQLException {
        t.insert(keyspace, tableName, logger, ci, futures, new java.sql.Timestamp(start), new java.sql.Timestamp(end));
    }
}