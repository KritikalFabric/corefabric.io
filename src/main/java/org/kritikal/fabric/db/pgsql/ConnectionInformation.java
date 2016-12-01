package org.kritikal.fabric.db.pgsql;

import com.datastax.driver.core.Session;
import org.kritikal.fabric.metrics.MetricsConfiguration;

import java.sql.Connection;

/**
 * Created by ben on 22/06/2016.
 */
public final class ConnectionInformation {
    // TODO: big refactor needed
    public ConnectionInformation(DWWorkerVerticle verticle) { this.verticle = verticle; }
    public ConnectionInformation(java.sql.Connection con) { this.conRead = con; this.conWrite = con; this.verticle = null; }
    public final DWWorkerVerticle verticle;
    public Connection conRead = null;
    public Connection conWrite = null;

    public Session getSession() {
        if (MetricsConfiguration.USE_CASSANDRA) {

            if (session != null && session.isClosed()) {
                session = null;
            }
            if (session != null) return session;

            synchronized (verticle) {
                if (verticle.cluster != null && verticle.cluster.isClosed()) verticle.cluster = null;
                if (verticle.cluster == null) verticle.connectCluster();
            }
            try {
                session = verticle.cluster.connect();
            } catch (IllegalStateException ise) {
                synchronized (verticle) {
                    if (verticle.cluster != null) {
                        try {
                            verticle.cluster.close();
                        } catch (Throwable t) {

                        } finally {
                            verticle.cluster = null;
                        }
                    }
                    verticle.connectCluster();
                }
                session = verticle.cluster.connect();
            }
            return session;
        }
        else {
            return null;
        }
    }
    public void clearSession() {
        if (MetricsConfiguration.USE_CASSANDRA) {
            if (session != null) {
                try {
                    session.close();
                } catch (Throwable t) {
                }
            }
            session = null;
        }
    }
    protected Session session = null;
}
