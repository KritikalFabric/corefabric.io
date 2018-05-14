package org.kritikal.fabric.core;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.kritikal.fabric.daemon.MqttBrokerVerticle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Created by ben on 07/03/2016.
 */
public class ConfigurationManager {
    public static class Shim {
        public static void apply(JsonObject globalConfig) {
            JsonObject node = globalConfig.getJsonObject("node", new JsonObject());
            appConfigUri = node.getString("app_config", "http://127.0.0.1:8082/");
            JsonObject node_db = node.getJsonObject("node_db", new JsonObject());
            host = node_db.getString("host", "localhost");
            port = node_db.getInteger("port", 5432);
            db = node_db.getString("db", "corefabric__node_db");
            user = node_db.getString("user", "postgres");
            password = node_db.getString("password", "password");
            tmp = node.getString("tmp", "/var/tmp");
        }
        public static String appConfigUri;
        public static String tmp;
        public static String host;
        public static int port;
        public static String db;
        public static String user;
        public static String password;
        public static String getMiniConnectionString() {
            return "jdbc:postgresql://" + host + ":" + port + "/" + db + "?charSet=UTF8";
        }

        public static String getAdminConnectionString() {
            return "jdbc:postgresql://" + host + ":" + port + "/postgres?charSet=UTF8";
        }

        public static String getConnectionString() {
            return "jdbc:postgresql://" + host + ":" + port + "/" + db + "?charSet=UTF8";
        }

        public static String getConnectionStringWithUsername() {
            return "jdbc:postgresql://" + host + ":" + port + "/" + db + "?charSet=UTF8&user=" + user + "&password=" + password;
        }

        public static String getDbUser() { return user; }

        public static String getDbPassword() { return password; }
    }

    private final static String localTopic(final String instancekey) {
        return "$cf-zone/" + instancekey + "/local";
    }
    private final static String clusterTopic(final String instancekey) {
        return "$cf-zone/" + instancekey + "/|cluster";
    }

    public final static int DEFAULT_CONCURRENCY = 16;
    public final static int BULKCOPY_CONCURRENCY = 2;
    public final static long TTL = 5 * /*60 * */1000;

    final static Logger logger = LoggerFactory.getLogger(ConfigurationManager.class);

    final static ConcurrentHashMap<String, Configuration> staticConfiguration = new ConcurrentHashMap<>();

    public static void getConfigurationAsync(Vertx vertx, String instancekey, Consumer<Configuration> consumer) {
        Configuration c = staticConfiguration.compute(instancekey, (s, configuration) -> {
            if (configuration == null) {
                configuration = new Configuration(instancekey);
                configuration.state = Configuration.State.UNKNOWN;
            }
            return configuration;
        });
        if (c.state == Configuration.State.UNKNOWN || c.refreshAfter == 0 || c.refreshAfter > new java.util.Date().getTime()) {
            getJsonClusterConfigurationAsync(vertx, instancekey, c, jsonClusterConfig -> {
                if (c.refreshAfter == 0) {
                    c.reset();
                }
                c.refreshAfter = new java.util.Date().getTime() + TTL;
                c.applyInstanceConfig(jsonClusterConfig);
                c.state = Configuration.State.LIVE;
                getJsonLocalConfigurationAsync(vertx, instancekey, c, jsonLocalConfig -> {
                    c.applyLocalConfig(jsonLocalConfig);

                    vertx.executeBlocking(f -> {
                        consumer.accept(c);
                        f.complete();
                    }, false, r -> {});
                });
            });
        } else {
            vertx.executeBlocking(f -> {
                consumer.accept(c);
                f.complete();
            }, false, r -> {});
        }
    }

    public static void getConfigurationSync(Vertx vertx, String instancekey, Consumer<Configuration> consumer) {
        Configuration c = staticConfiguration.compute(instancekey, (s, configuration) -> {
            if (configuration == null) {
                configuration = new Configuration(instancekey);
                configuration.state = Configuration.State.UNKNOWN;
            }
            return configuration;
        });
        if (c.state == Configuration.State.UNKNOWN || c.refreshAfter == 0 || c.refreshAfter > new java.util.Date().getTime()) {
            getJsonClusterConfigurationSync(vertx, instancekey, c, jsonClusterConfig -> {
                if (c.refreshAfter == 0) {
                    c.reset();
                }
                c.refreshAfter = new java.util.Date().getTime() + TTL;
                c.applyInstanceConfig(jsonClusterConfig);
                c.state = Configuration.State.LIVE;
                getJsonLocalConfigurationSync(vertx, instancekey, c, jsonLocalConfig -> {
                    c.applyLocalConfig(jsonLocalConfig);
                    consumer.accept(c);
                });
            });
        } else {
            consumer.accept(c);
        }
    }

    /**
     * ONLY USE FROM UNIT TESTS
     */
    public static Configuration getConfigurationSyncInline(Vertx vertx, String instancekey) {
        Configuration c = staticConfiguration.compute(instancekey, (s, configuration) -> {
            if (configuration == null) {
                configuration = new Configuration(instancekey);
                configuration.state = Configuration.State.UNKNOWN;
            }
            return configuration;
        });
        if (c.state == Configuration.State.UNKNOWN || c.refreshAfter == 0 || c.refreshAfter > new java.util.Date().getTime()) {
            final JsonObject jsonClusterConfig = getJsonClusterConfigurationSyncInline(vertx, instancekey, c);
            if (c.refreshAfter == 0) {
                c.reset();
            }
            c.refreshAfter = new java.util.Date().getTime() + TTL;
            c.applyInstanceConfig(jsonClusterConfig);
            c.state = Configuration.State.LIVE;
            final JsonObject jsonLocalConfig = getJsonLocalConfigurationSyncInline(vertx, instancekey, c);
            c.applyLocalConfig(jsonLocalConfig);
        }
        return c;
    }

    private final static ConcurrentHashMap<String, ConcurrentLinkedQueue<Consumer<JsonObject>>> clusterQueue = new ConcurrentHashMap<>();
    private final static ConcurrentHashMap<String, Boolean> clusterRequests = new ConcurrentHashMap<>();

    private final static ConcurrentHashMap<String, ConcurrentLinkedQueue<Consumer<JsonObject>>> localQueue = new ConcurrentHashMap<>();
    private final static ConcurrentHashMap<String, Boolean> localRequests = new ConcurrentHashMap<>();

    private final static void nextStep(String content, ConcurrentLinkedQueue<Consumer<JsonObject>> q) {
        final JsonObject cfg = new JsonObject(content);
        Consumer<JsonObject> c = null;
        while (null != (c = q.poll())) {
            try {
                c.accept(cfg);
            }
            catch (Throwable t) {
                logger.warn("", t);
            }
        }
    }

    public static Configuration getOutsideVerticle(String instancekey)
    {
        Configuration c = staticConfiguration.compute(instancekey, (s, configuration) -> {
            if (configuration == null) {
                configuration = new Configuration(instancekey);
                configuration.state = Configuration.State.UNKNOWN;
            }
            return configuration;
        });
        if (c.state == Configuration.State.UNKNOWN || c.refreshAfter == 0 || c.refreshAfter > new java.util.Date().getTime()) {
            final JsonObject jsonClusterConfig = new JsonObject(getJsonClusterConfiguration(instancekey));
            if (c.refreshAfter == 0) {
                c.reset();
            }
            c.refreshAfter = new java.util.Date().getTime() + TTL;
            c.applyInstanceConfig(jsonClusterConfig);
            c.state = Configuration.State.LIVE;
            final JsonObject jsonLocalConfig = getJsonLocalConfiguration(c);
            c.applyLocalConfig(jsonLocalConfig);
        }
        return c;
    }

    private static String getJsonClusterConfiguration(String instancekey)
    {
        StringBuilder content = new StringBuilder();
        try {
            URL url = new URL(Shim.appConfigUri + instancekey);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setReadTimeout(5 * 1000);

                if (conn.getResponseCode() != 200) {
                    throw new IOException("Failed : HTTP error code : "
                            + conn.getResponseCode());
                }

                BufferedReader br = new BufferedReader(new InputStreamReader(
                        (conn.getInputStream())));

                String output;
                while ((output = br.readLine()) != null) {
                    content.append(output);
                }

            } finally {
                conn.disconnect();
            }
        } catch (MalformedURLException e) {
            logger.error(instancekey, e);
            return "{}";
        } catch (IOException e) {
            logger.error(instancekey, e);
            return "{}";
        }

        return content.toString();
    }

    private static JsonObject getJsonLocalConfiguration(final Configuration originalConfiguration) {
        JsonObject content = new JsonObject();
        if (originalConfiguration.change.exit) return content;
        try {
            Class.forName("org.postgresql.Driver");
            Connection con = DriverManager.getConnection(originalConfiguration.getConnectionStringWithUsername());
            con.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
            con.setAutoCommit(false);
            try {
                PreparedStatement statement = con.prepareStatement("SELECT key, value FROM node.instance_local_cfg FOR UPDATE;");
                try {
                    ResultSet rs = statement.executeQuery();
                    try {
                        while (rs.next()) {
                            String key = rs.getString(1);
                            String value = rs.getString(2);
                            content.put(key, new JsonObject(value));
                        }
                    }
                    finally {
                        rs.close();
                    }
                }
                finally {
                    statement.close();
                }
                con.commit();
            }
            finally {
                con.close();
            }
        } catch (Throwable t) {
            logger.fatal("Getting local configuration", t);
        }
        return content;
    }

    private static void getJsonClusterConfigurationAsync(Vertx vertx, String instancekey, final Configuration originalConfiguration, Consumer<JsonObject> consumer) {
        ConcurrentLinkedQueue<Consumer<JsonObject>> q = clusterQueue.computeIfAbsent(instancekey, (k) -> { return new ConcurrentLinkedQueue<Consumer<JsonObject>>(); });
        q.add(consumer);

        final String topic = clusterTopic(instancekey);
        JsonObject cachedMqttObject = MqttBrokerVerticle.mqttBroker().apiPeek(topic);
        if(null == cachedMqttObject) {
            clusterRequests.compute(instancekey, (k, v) -> {
                if (null == v || !v) {
                    v = true;
                    vertx.executeBlocking(f ->
                    {
                        // get it synchronously
                        String json = getJsonClusterConfiguration(instancekey);

                        try { MqttBrokerVerticle.mqttBroker().apiPublish(topic, json.getBytes("UTF-8"), 0, true, TTL); } catch (UnsupportedEncodingException uee) { logger.fatal("", uee); }
                        clusterRequests.put(instancekey, false);
                        nextStep(json, q);
                    }, false, r -> {
                    });
                }
                return v;
            });
        }
        else {
            String content = null;
            if (cachedMqttObject.containsKey("body")) {
                try {
                    content = new String(cachedMqttObject.getBinary("body"), "UTF-8");
                } catch (UnsupportedEncodingException uee) {
                }
                nextStep(content, q);
            }
            else {
                nextStep("{}", q);
            }
        }
    }

    private static void getJsonClusterConfigurationSync(Vertx vertx, String instancekey, final Configuration originalConfiguration, Consumer<JsonObject> consumer) {
        ConcurrentLinkedQueue<Consumer<JsonObject>> q = clusterQueue.computeIfAbsent(instancekey, (k) -> { return new ConcurrentLinkedQueue<Consumer<JsonObject>>(); });
        q.add(consumer);

        final String topic = clusterTopic(instancekey);
        JsonObject cachedMqttObject = MqttBrokerVerticle.mqttBroker().apiPeek(topic);
        if(null == cachedMqttObject) {
            ArrayList<Boolean> list = new ArrayList<>();
            clusterRequests.compute(instancekey, (k, v) -> {
                if (null == v || !v) {
                    v = true;
                    list.add(true);
                }
                return v;
            });

            if (!list.isEmpty()) {
                // get it synchronously
                String json = getJsonClusterConfiguration(instancekey);

                try { MqttBrokerVerticle.mqttBroker().apiPublish(topic, json.getBytes("UTF-8"), 0, true, TTL); } catch (UnsupportedEncodingException uee) { logger.fatal("", uee); }
                clusterRequests.put(instancekey, false);
                nextStep(json, q);
            }

        }
        else {
            String content = null;
            if (cachedMqttObject.containsKey("body")) {
                try {
                    content = new String(cachedMqttObject.getBinary("body"), "UTF-8");
                } catch (UnsupportedEncodingException uee) {
                }
                nextStep(content, q);
            }
            else {
                nextStep("{}", q);
            }
        }
    }

    private static JsonObject getJsonClusterConfigurationSyncInline(Vertx vertx, String instancekey, final Configuration originalConfiguration) {
        ConcurrentLinkedQueue<Consumer<JsonObject>> q = clusterQueue.computeIfAbsent(instancekey, (k) -> { return new ConcurrentLinkedQueue<Consumer<JsonObject>>(); });

        final String topic = clusterTopic(instancekey);
        JsonObject cachedMqttObject = MqttBrokerVerticle.mqttBroker().apiPeek(topic);
        if(null == cachedMqttObject) {
            ArrayList<Boolean> list = new ArrayList<>();
            clusterRequests.compute(instancekey, (k, v) -> {
                if (null == v || !v) {
                    v = true;
                    list.add(true);
                }
                return v;
            });

            String json = null;
            {
                // get it synchronously
                json = getJsonClusterConfiguration(instancekey);

                try { MqttBrokerVerticle.mqttBroker().apiPublish(topic, json.getBytes("UTF-8"), 0, true, TTL); } catch (UnsupportedEncodingException uee) { logger.fatal("", uee); }
                clusterRequests.put(instancekey, false);
                nextStep(json, q);

            }
            return new JsonObject(json);
        }
        else {
            String content = null;
            if (cachedMqttObject.containsKey("body")) {
                try {
                    content = new String(cachedMqttObject.getBinary("body"), "UTF-8");
                } catch (UnsupportedEncodingException uee) {
                }
                nextStep(content, q);

                return new JsonObject(content);
            }
            else {
                nextStep("{}", q);
            }
        }

        return new JsonObject();
    }

    private static JsonObject getJsonLocalConfigurationSyncInline(Vertx vertx, String instancekey, final Configuration originalConfiguration) {
        ConcurrentLinkedQueue<Consumer<JsonObject>> q = localQueue.computeIfAbsent(instancekey, (k) -> { return new ConcurrentLinkedQueue<Consumer<JsonObject>>(); });

        final String topic = localTopic(instancekey);
        JsonObject cachedMqttObject = MqttBrokerVerticle.mqttBroker().apiPeek(topic);
        if(null == cachedMqttObject) {
            ArrayList<Boolean> list = new ArrayList<>();
            localRequests.compute(instancekey, (k, v) -> {
                if (null == v || !v) {
                    v = true;
                    list.add(true);
                }
                return v;
            });

            // get it synchronously
            JsonObject content = getJsonLocalConfiguration(originalConfiguration);

            String json = content.toString();

            try { MqttBrokerVerticle.mqttBroker().apiPublish(topic, json.getBytes("UTF-8"), 0, true, TTL); } catch (UnsupportedEncodingException uee) { logger.fatal("", uee); }
            localRequests.put(instancekey, false);
            nextStep(content.toString(), q);

            return content;
        }
        else {
            String content = null;
            if (cachedMqttObject.containsKey("body")) {
                try {
                    content = new String(cachedMqttObject.getBinary("body"), "UTF-8");
                } catch (UnsupportedEncodingException uee) {
                }
                nextStep(content, q);

                return new JsonObject(content);
            }
            else {
                nextStep("{}", q);
            }
        }

        return new JsonObject();
    }

    private static void getJsonLocalConfigurationAsync(Vertx vertx, String instancekey, final Configuration originalConfiguration, Consumer<JsonObject> consumer) {
        ConcurrentLinkedQueue<Consumer<JsonObject>> q = localQueue.computeIfAbsent(instancekey, (k) -> { return new ConcurrentLinkedQueue<Consumer<JsonObject>>(); });
        q.add(consumer);

        final String topic = localTopic(instancekey);
        JsonObject cachedMqttObject = MqttBrokerVerticle.mqttBroker().apiPeek(topic);
        if(null == cachedMqttObject) {
            localRequests.compute(instancekey, (k, v) -> {
                if (null == v || !v) {
                    v = true;
                    vertx.executeBlocking(f ->
                    {
                        // get it synchronously

                        JsonObject content = getJsonLocalConfiguration(originalConfiguration);

                        String json = content.toString();

                        try { MqttBrokerVerticle.mqttBroker().apiPublish(topic, json.getBytes("UTF-8"), 0, true, TTL); } catch (UnsupportedEncodingException uee) { logger.fatal("", uee); }
                        localRequests.put(instancekey, false);
                        nextStep(content.toString(), q);
                    }, false, r -> {
                    });
                }
                return v;
            });
        }
        else {
            String content = null;
            if (cachedMqttObject.containsKey("body")) {
                try {
                    content = new String(cachedMqttObject.getBinary("body"), "UTF-8");
                } catch (UnsupportedEncodingException uee) {
                }
                nextStep(content, q);
            }
            else {
                nextStep("{}", q);
            }
        }
    }

    private static void getJsonLocalConfigurationSync(Vertx vertx, String instancekey, final Configuration originalConfiguration, Consumer<JsonObject> consumer) {
        ConcurrentLinkedQueue<Consumer<JsonObject>> q = localQueue.computeIfAbsent(instancekey, (k) -> { return new ConcurrentLinkedQueue<Consumer<JsonObject>>(); });
        q.add(consumer);

        final String topic = localTopic(instancekey);
        JsonObject cachedMqttObject = MqttBrokerVerticle.mqttBroker().apiPeek(topic);
        if(null == cachedMqttObject) {
            ArrayList<Boolean> list = new ArrayList<>();
            localRequests.compute(instancekey, (k, v) -> {
                if (null == v || !v) {
                    v = true;
                    list.add(true);
                }
                return v;
            });

            if (!list.isEmpty()) {
                // get it synchronously
                String json = getJsonLocalConfiguration(originalConfiguration).toString();

                try { MqttBrokerVerticle.mqttBroker().apiPublish(topic, json.getBytes("UTF-8"), 0, true, TTL); } catch (UnsupportedEncodingException uee) { logger.fatal("", uee); }
                localRequests.put(instancekey, false);
                nextStep(json, q);
            }

        }
        else {
            String content = null;
            if (cachedMqttObject.containsKey("body")) {
                try {
                    content = new String(cachedMqttObject.getBinary("body"), "UTF-8");
                } catch (UnsupportedEncodingException uee) {
                }
                nextStep(content, q);
            }
            else {
                nextStep("{}", q);
            }
        }
    }


}
