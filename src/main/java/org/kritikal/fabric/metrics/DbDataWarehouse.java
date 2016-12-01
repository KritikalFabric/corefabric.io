package org.kritikal.fabric.metrics;

import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import org.kritikal.fabric.db.pgsql.DWWorkerVerticle;
import io.vertx.core.logging.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import org.kritikal.fabric.db.pgsql.ConnectionInformation;

/**
 * Created by ben on 22/02/15.
 */
public interface DbDataWarehouse {

    String createDdl(String keyspace, String tableName);

    void insert(String keyspace, String tableName, Logger logger, ConnectionInformation ci, ArrayList<ResultSetFuture> futures, java.sql.Timestamp start, java.sql.Timestamp end);

}
