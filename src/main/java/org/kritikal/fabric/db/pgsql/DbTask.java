package org.kritikal.fabric.db.pgsql;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import org.kritikal.fabric.core.Configuration;

import java.sql.SQLException;

/**
 * Created by ben on 03/06/15.
 */
public interface DbTask {

    // should also have public const static NAME = "";

    String getName();

    void setIter(int iter);

    void execute(java.sql.Connection con, Configuration configuration, Vertx vertx, JsonObject config) throws SQLException;

}
