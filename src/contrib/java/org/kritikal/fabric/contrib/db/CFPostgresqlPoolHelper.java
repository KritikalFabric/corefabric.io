package org.kritikal.fabric.contrib.db;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.dbcp2.BasicDataSource;

import java.util.function.Consumer;

/**
 * Created by ben on 5/15/16.
 */
public class CFPostgresqlPoolHelper {

    public static Logger logger = LoggerFactory.getLogger(CFPostgresqlPoolHelper.class);

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
        basicDataSource.setValidationQuery(CFPostgresqlCfg.POOL_VALIDATION_QUERY);
        basicDataSource.setTestOnBorrow(CFPostgresqlCfg.POOL_TEST_BORROW);
        basicDataSource.setTestOnReturn(CFPostgresqlCfg.POOL_TEST_RETURN);
        basicDataSource.setTestWhileIdle(CFPostgresqlCfg.POOL_TEST_IDLE);
        basicDataSource.setLifo(false);
        basicDataSource.setRollbackOnReturn(false);
        basicDataSource.setDefaultTransactionIsolation(CFPostgresqlCfg.DEFAULT_TRANSACTION_ISOLATION);
        config.accept(basicDataSource);
        basicDataSource.setInitialSize(concurrency);
        basicDataSource.setMinIdle(2);
        //basicDataSource.setMaxTotal(concurrency);
        //try { basicDataSource.createConnection().close(); } catch (Throwable t) { } // error will happen again for sure
        //try  { Thread.sleep(Constants.INITIAL_BASIC_DATA_SOURCE_SLEEP); } catch (InterruptedException ie) { }
        return basicDataSource;
    }
}
