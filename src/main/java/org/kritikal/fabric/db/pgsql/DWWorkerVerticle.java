package org.kritikal.fabric.db.pgsql;

import com.datastax.driver.core.*;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.policies.RetryPolicy;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.kritikal.fabric.core.ConfigurationManager;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.metrics.MetricsConfiguration;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.kritikal.fabric.core.Configuration;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ben on 22/02/15.
 */
public abstract class DWWorkerVerticle extends AbstractVerticle implements Handler<Message<JsonObject>> {

    public final boolean DEBUG = CoreFabric.ServerConfiguration.DEBUG;

    public Logger logger = null;
    List<String> addresses;
    final static DbContainer readContainer = new DbContainer(ConfigurationManager.DEFAULT_CONCURRENCY);
    final static DbContainer writeContainer = new DbContainer(ConfigurationManager.DEFAULT_CONCURRENCY);
    List<MessageConsumer> mcList = null;

    public void start() throws Exception
    {
        super.start();

        logger = LoggerFactory.getLogger(getClass());

        if (!Constants.LOADED) {
            logger.fatal("Could not load jdbc driver");
            return;
        }

        addresses = new ArrayList<String>();
        JsonArray ary = config().getJsonArray("addresses");
        for (int i = 0; i < ary.size(); ++i)
            addresses.add(ary.getString(i));

        mcList = new ArrayList<>();
        for(String address : addresses)
            mcList.add(vertx.eventBus().localConsumer(address, this));
    }

    protected static Cluster cluster = null;
    public final static RetryPolicy retryPolicy = new RetryPolicy() {
        @Override
        public RetryDecision onReadTimeout(Statement statement, ConsistencyLevel cl, int requiredResponses, int receivedResponses, boolean dataRetrieved, int nbRetry) {
            return RetryDecision.ignore();
        }

        @Override
        public RetryDecision onWriteTimeout(Statement statement, ConsistencyLevel cl, WriteType writeType, int requiredAcks, int receivedAcks, int nbRetry) {
            return RetryDecision.retry(cl);
        }

        @Override
        public RetryDecision onUnavailable(Statement statement, ConsistencyLevel cl, int requiredReplica, int aliveReplica, int nbRetry) {
            return RetryDecision.retry(cl);
        }
    };

    void connectCluster()
    {
        if (MetricsConfiguration.USE_CASSANDRA) {
            synchronized (this) {
                if (cluster != null && cluster.isClosed()) cluster = null;
                if (cluster != null) cluster.close();
                cluster = null;
                PoolingOptions poolingOptions = new PoolingOptions();
                poolingOptions.setMaxConnectionsPerHost(HostDistance.LOCAL, 1024);
                poolingOptions.setMaxConnectionsPerHost(HostDistance.REMOTE, 1024);
                poolingOptions.setCoreConnectionsPerHost(HostDistance.LOCAL, 128);
                poolingOptions.setCoreConnectionsPerHost(HostDistance.REMOTE, 128);
                poolingOptions.setMaxSimultaneousRequestsPerConnectionThreshold(HostDistance.LOCAL, 128);
                poolingOptions.setMaxSimultaneousRequestsPerConnectionThreshold(HostDistance.REMOTE, 128);
                poolingOptions.setMaxSimultaneousRequestsPerHostThreshold(HostDistance.LOCAL, 128);
                poolingOptions.setMaxSimultaneousRequestsPerHostThreshold(HostDistance.REMOTE, 128);
                cluster = Cluster.builder().addContactPoint("cassandra.test").withRetryPolicy(retryPolicy).withPoolingOptions(poolingOptions).build();
                cluster.getConfiguration().getSocketOptions().setConnectTimeoutMillis(60000);
                cluster.getConfiguration().getSocketOptions().setReadTimeoutMillis(60000);
            }
        }
    }

    public ConnectionInformation connect(Configuration cfg) throws SQLException
    {
        ConnectionInformation ci = new ConnectionInformation(this);
        ci.conRead = readContainer.connect(cfg, vertx.fileSystem(), true);
        ci.conWrite = writeContainer.connect(cfg, vertx.fileSystem(), false);
        ci.session = null;
        if (ci.conRead == null) throw new SQLException("Unable to connect conRead");
        if (ci.conWrite == null) throw new SQLException("Unable to connect conWrite");
        return ci;
    }

    public ConnectionInformation reconnect(ConnectionInformation ci) {
        if (MetricsConfiguration.USE_CASSANDRA) {
            if (ci.session != null) {
                ci.session.close();
                ci.session = null;
            }
            if (cluster != null && cluster.isClosed()) {
                cluster.close();
                cluster = null;
            }
            if (cluster == null) connectCluster();
            try {
                ci.session = cluster.connect();
            } catch (IllegalStateException ise) {
                if (cluster != null) {
                    try {
                        cluster.close();
                    } catch (Throwable t) {

                    } finally {
                        cluster = null;
                    }
                }
                connectCluster();
                ci.session = cluster.connect();
            }
        }
        return ci;
    }

    public void release(ConnectionInformation ci) throws SQLException
    {
        if (ci.conRead != null) {
            ci.conRead.close();
            ci.conRead = null;
        }
        if (ci.conWrite != null) {
            ci.conWrite.close();
            ci.conWrite = null;
        }
        if (ci.session != null) {
            ci.session.close();
            ci.session = null;
        }
    }

    public void stop() throws Exception
    {
        if (MetricsConfiguration.USE_CASSANDRA) {
            synchronized (this) {
                if (cluster != null) {
                    cluster.close();
                    cluster = null;
                }
            }
        }
        if (mcList != null)
            for (MessageConsumer mc : mcList)
                mc.unregister();
        mcList = null;

        readContainer.close();
        writeContainer.close();

        super.stop();
    }

    public abstract void handle(Message<JsonObject> message);

}
