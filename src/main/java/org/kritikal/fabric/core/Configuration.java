package org.kritikal.fabric.core;

import io.vertx.core.json.JsonObject;

/**
 * Created by ben on 07/03/2016.
 */
public class Configuration {
    public Configuration(String instancekey) {
        this.instancekey = instancekey;
    }
    public final String instancekey;
    public String getAdminConnectionString() { return ConfigurationManager.Shim.getAdminConnectionString(); }
    public String getConnectionString() { return ConfigurationManager.Shim.getConnectionString(); }
    public String getMiniConnectionString() { return ConfigurationManager.Shim.getMiniConnectionString(); }
    public String getConnectionStringWithUsername() { return ConfigurationManager.Shim.getConnectionStringWithUsername(); }
    public String getDbUser() { return ConfigurationManager.Shim.getDbUser(); }
    public String getDbPassword() { return ConfigurationManager.Shim.getDbPassword(); }
    public class Change {
        public volatile boolean exit = false;
    }
    public final Change change = new Change();
    public enum State {UNKNOWN, LIVE, ERROR};
    public State state = State.UNKNOWN;
    public long refreshAfter = 0l;

    public void invalidate() {
        this.refreshAfter = 0l; // 1 jan 1970 00:00:00 UTC
    }

    private boolean needsReset = false;
    public void reset() {
        synchronized (this) {
            needsReset = true;
        }
    }
    public void applyInstanceConfig(JsonObject instanceConfig) { /* does nothing by default */ }
    public void applyLocalConfig(JsonObject localConfiguration) { /* does nothing by default */ }
}
