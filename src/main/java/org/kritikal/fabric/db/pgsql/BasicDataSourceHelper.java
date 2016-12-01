package org.kritikal.fabric.db.pgsql;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.dbcp2.BasicDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;

/**
 * Created by ben on 5/15/16.
 */
public class BasicDataSourceHelper {

    public static Logger logger = LoggerFactory.getLogger(BasicDataSourceHelper.class);

    /**
     * Create pools based on a standard template
     */
    public static /*synchronized*/ BasicDataSource pool(int concurrency, Consumer<BasicDataSource> config)
    {
        BasicDataSource basicDataSource = new BasicDataSource();
        basicDataSource.setCacheState(true);
        basicDataSource.setDriverClassName("org.postgresql.Driver");
        basicDataSource.setDefaultAutoCommit(false);
        basicDataSource.setMaxWaitMillis(-1);
        basicDataSource.setValidationQuery("SELECT 1;");
        basicDataSource.setTestOnBorrow(true);
        basicDataSource.setTestOnReturn(false);
        basicDataSource.setTestWhileIdle(false);
        basicDataSource.setLifo(false);
        basicDataSource.setRollbackOnReturn(false);
        basicDataSource.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
        config.accept(basicDataSource);
        basicDataSource.setInitialSize(concurrency);
        basicDataSource.setMinIdle(2);
        //basicDataSource.setMaxTotal(concurrency);
        //try { basicDataSource.getConnection().close(); } catch (Throwable t) { } // error will happen again for sure
        //try  { Thread.sleep(Constants.INITIAL_BASIC_DATA_SOURCE_SLEEP); } catch (InterruptedException ie) { }
        return basicDataSource;
    }
}
