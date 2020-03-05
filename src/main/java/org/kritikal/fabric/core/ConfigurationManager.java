package org.kritikal.fabric.core;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.daemon.MqttBrokerVerticle;
import org.kritikal.fabric.db.pgsql.DbInstanceContainer;

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
            appConfigUri = node.getString("app_config_uri", "http://127.0.0.1:8082/");
            if (appConfigUri.contains("127.0.0.1") || appConfigUri.contains("localhost")) {
                appConfigForceSynchronousHttp = true;
            } else {
                appConfigForceSynchronousHttp = false;
            }
            try {
                URL url = new URL(appConfigUri);
                appConfigHost = url.getHost();
                appConfigPort = url.getPort();
            }
            catch (Exception e) {
                logger.fatal("configuration-manager\tCannot parse [" + appConfigUri + "]\t" + e.getMessage());
                appConfigHost = "127.0.0.1";
                appConfigPort = 8082;
                appConfigForceSynchronousHttp = true;
            }
            JsonObject node_db = node.getJsonObject("node_db", new JsonObject());
            host = node_db.getString("host", "localhost");
            port = node_db.getInteger("port", 5432);
            db = node_db.getString("db", "corefabric__node_db");
            user = node_db.getString("user", "postgres");
            password = node_db.getString("password", "password");
            tmp = node.getString("tmp", "/var/tmp");
            dbInstanceContainer = new DbInstanceContainer(2);
            dbInstanceContainer.initialise(node_db);
        }
        public static DbInstanceContainer dbInstanceContainer;
        public static String appConfigUri;
        public static String appConfigHost;
        public static int appConfigPort;
        public static boolean appConfigForceSynchronousHttp = true;
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
    public final static long TTL = 5 * /*60 * */1000; // 5 seconds, in milliseconds.

    final static Logger logger = LoggerFactory.getLogger(ConfigurationManager.class);

    final static ConcurrentHashMap<String, Configuration> staticConfiguration = new ConcurrentHashMap<>();

    public static void getConfigurationAsync(Vertx vertx, String instancekey, Consumer<Configuration> consumer) {
        final long t = new java.util.Date().getTime();
        Configuration c1 = staticConfiguration.compute(instancekey, (s, configuration) -> {
            if (configuration == null) {
                configuration = new Configuration(instancekey);
                configuration.state = Configuration.State.UNKNOWN;
            }
            return configuration;
        });
        if (c1.state == Configuration.State.UNKNOWN || c1.refreshAfter == 0 || t > c1.refreshAfter) {
            c1 = new Configuration(instancekey);
            c1.state = Configuration.State.UNKNOWN;
            final Configuration c = c1;
            getJsonClusterConfigurationAsync(vertx, instancekey, c, jsonClusterConfig -> {
                c.refreshAfter = new java.util.Date().getTime() + TTL;
                c.applyInstanceConfig(jsonClusterConfig);
                c.state = Configuration.State.LIVE;
                getJsonLocalConfigurationAsync(vertx, instancekey, c, jsonLocalConfig -> {
                    vertx.executeBlocking(f -> {
                        c.applyLocalConfig(jsonLocalConfig); // blocking, TODO: use DbInstance for pooling
                        consumer.accept(c);
                        f.complete();
                    }, false, r -> {
                        staticConfiguration.put(instancekey, c);
                    });
                });
            });
        } else {
            final Configuration c = c1;
            vertx.executeBlocking(f -> {
                consumer.accept(c);
                f.complete();
            }, false, r -> {});
        }
    }

    public static void getConfigurationSync(Vertx vertx, String instancekey, Consumer<Configuration> consumer) {
        final long t = new java.util.Date().getTime();
        Configuration c1 = staticConfiguration.compute(instancekey, (s, configuration) -> {
            if (configuration == null) {
                configuration = new Configuration(instancekey);
                configuration.state = Configuration.State.UNKNOWN;
            }
            return configuration;
        });
        if (c1.state == Configuration.State.UNKNOWN || c1.refreshAfter == 0 || t > c1.refreshAfter) {
            c1 = new Configuration(instancekey);
            c1.state = Configuration.State.UNKNOWN;
            final Configuration c = c1;
            getJsonClusterConfigurationSync(vertx, instancekey, c, jsonClusterConfig -> {
                c.refreshAfter = new java.util.Date().getTime() + TTL;
                c.applyInstanceConfig(jsonClusterConfig);
                c.state = Configuration.State.LIVE;
                getJsonLocalConfigurationSync(vertx, instancekey, c, jsonLocalConfig -> {
                    c.applyLocalConfig(jsonLocalConfig);
                    consumer.accept(c);
                    staticConfiguration.put(instancekey, c);
                });
            });
        } else {
            consumer.accept(c1);
        }
    }

    /**
     * ONLY USE FROM UNIT TESTS
     */
    public static Configuration getConfigurationSyncInline(Vertx vertx, String instancekey) {
        final long t = new java.util.Date().getTime();
        Configuration c1 = staticConfiguration.compute(instancekey, (s, configuration) -> {
            if (configuration == null) {
                configuration = new Configuration(instancekey);
                configuration.state = Configuration.State.UNKNOWN;
            }
            return configuration;
        });
        if (c1.state == Configuration.State.UNKNOWN || c1.refreshAfter == 0 || t > c1.refreshAfter) {
            c1 = new Configuration(instancekey);
            c1.state = Configuration.State.UNKNOWN;
            final Configuration c = c1;
            final JsonObject jsonClusterConfig = getJsonClusterConfigurationSyncInline(vertx, instancekey, c);
            c.refreshAfter = new java.util.Date().getTime() + TTL;
            c.applyInstanceConfig(jsonClusterConfig);
            c.state = Configuration.State.LIVE;
            final JsonObject jsonLocalConfig = getJsonLocalConfigurationSyncInline(vertx, instancekey, c);
            c.applyLocalConfig(jsonLocalConfig);
            staticConfiguration.put(instancekey, c);
        }
        return c1;
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
        final long t = new java.util.Date().getTime();
        Configuration c1 = staticConfiguration.compute(instancekey, (s, configuration) -> {
            if (configuration == null) {
                configuration = new Configuration(instancekey);
                configuration.state = Configuration.State.UNKNOWN;
            }
            return configuration;
        });
        if (c1.state == Configuration.State.UNKNOWN || c1.refreshAfter == 0 || t > c1.refreshAfter) {
            c1 = new Configuration(instancekey);
            c1.state = Configuration.State.UNKNOWN;
            final Configuration c = c1;
            final JsonObject jsonClusterConfig = new JsonObject(getJsonClusterConfigurationBlockingApi(instancekey));
            c.refreshAfter = new java.util.Date().getTime() + TTL;
            c.applyInstanceConfig(jsonClusterConfig);
            c.state = Configuration.State.LIVE;
            final JsonObject jsonLocalConfig = getJsonLocalConfiguration(c);
            c.applyLocalConfig(jsonLocalConfig);
            staticConfiguration.put(instancekey, c);
        }
        return c1;
    }

    private static String getJsonClusterConfigurationBlockingApi(String instancekey)
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

    // TODO: go via an instanceconfigdbverticle like appconfigdbverticle to get this information async via messages
    private static JsonObject getJsonLocalConfiguration(final Configuration originalConfiguration) {
        JsonObject content = new JsonObject();
        if (originalConfiguration.change.exit) return content;
        try {
            Connection con = Shim.dbInstanceContainer.connect(null, false);
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

    public static void invalidate(String instancekey) {
        staticConfiguration.remove(instancekey);
        MqttBrokerVerticle.asyncBroker().apiRemove(clusterTopic(instancekey), (v0id)->{});
    }

    private static void getJsonClusterConfigurationAsync(Vertx vertx, String instancekey, final Configuration originalConfiguration, Consumer<JsonObject> consumer) {
        ConcurrentLinkedQueue<Consumer<JsonObject>> q = clusterQueue.computeIfAbsent(instancekey, (k) -> { return new ConcurrentLinkedQueue<Consumer<JsonObject>>(); });
        q.add(consumer);

        final String topic = clusterTopic(instancekey);
        MqttBrokerVerticle.asyncBroker().apiPeek(topic, (cachedMqttObject) -> {
            if(null == cachedMqttObject) {
                if (Shim.appConfigForceSynchronousHttp) {
                    clusterRequests.compute(instancekey, (k, v) -> {
                        if (null == v || !v) {
                            v = true;
                            vertx.executeBlocking(f ->
                            {
                                // get it synchronously
                                String json = getJsonClusterConfigurationBlockingApi(instancekey);

                                try {
                                    MqttBrokerVerticle.syncBroker().syncApiPublish(topic, json.getBytes("UTF-8"), 0, true, TTL);
                                } catch (UnsupportedEncodingException uee) {
                                    logger.fatal("", uee);
                                }
                                clusterRequests.put(instancekey, false);
                                nextStep(json, q);
                            }, false, r -> {
                            });
                        }
                        return v;
                    });
                } else {
                    // full async path
                    final HttpClient httpClient = vertx.createHttpClient();
                    httpClient.getNow(Shim.appConfigPort, Shim.appConfigHost, Shim.appConfigUri + instancekey, response -> {
                        response.bodyHandler(buffer -> {
                            final StringBuilder bodyBuilder = new StringBuilder();
                            try {
                                bodyBuilder.append(new String(buffer.getBytes(0, buffer.length()), "UTF-8"));
                            }
                            catch (UnsupportedEncodingException uee) {
                                logger.fatal("configuration-manager\t" + uee.toString());
                            }
                            final String s = bodyBuilder.toString();
                            final String json = s.isEmpty() ? "{}" : s;
                            try {
                                MqttBrokerVerticle.asyncBroker().apiPublish(topic, json.getBytes("UTF-8"), 0, true, TTL, (vo1d) -> {
                                    nextStep(json, q);
                                });
                            } catch (UnsupportedEncodingException uee) { logger.fatal("", uee); }
                        });
                    });
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
        });
    }

    private static void getJsonClusterConfigurationSync(Vertx vertx, String instancekey, final Configuration originalConfiguration, Consumer<JsonObject> consumer) {
        ConcurrentLinkedQueue<Consumer<JsonObject>> q = clusterQueue.computeIfAbsent(instancekey, (k) -> { return new ConcurrentLinkedQueue<Consumer<JsonObject>>(); });
        q.add(consumer);

        final String topic = clusterTopic(instancekey);
        JsonObject cachedMqttObject = MqttBrokerVerticle.syncBroker().syncApiPeek(topic);
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
                String json = getJsonClusterConfigurationBlockingApi(instancekey);

                try { MqttBrokerVerticle.syncBroker().syncApiPublish(topic, json.getBytes("UTF-8"), 0, true, TTL); } catch (UnsupportedEncodingException uee) { logger.fatal("", uee); }
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
        JsonObject cachedMqttObject = MqttBrokerVerticle.syncBroker().syncApiPeek(topic);
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
                json = getJsonClusterConfigurationBlockingApi(instancekey);

                try { MqttBrokerVerticle.syncBroker().syncApiPublish(topic, json.getBytes("UTF-8"), 0, true, TTL); } catch (UnsupportedEncodingException uee) { logger.fatal("", uee); }
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
        JsonObject cachedMqttObject = MqttBrokerVerticle.syncBroker().syncApiPeek(topic);
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

            try { MqttBrokerVerticle.syncBroker().syncApiPublish(topic, json.getBytes("UTF-8"), 0, true, TTL); } catch (UnsupportedEncodingException uee) { logger.fatal("", uee); }
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
        MqttBrokerVerticle.asyncBroker().apiPeek(topic, (cachedMqttObject) -> {
            if(null == cachedMqttObject) {
                localRequests.compute(instancekey, (k, v) -> {
                    if (null == v || !v) {
                        v = true;
                        vertx.executeBlocking(f ->
                        {
                            // get it synchronously

                            JsonObject content = getJsonLocalConfiguration(originalConfiguration);

                            String json = content.toString();

                            try { MqttBrokerVerticle.syncBroker().syncApiPublish(topic, json.getBytes("UTF-8"), 0, true, TTL); } catch (UnsupportedEncodingException uee) { logger.fatal("", uee); }
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

        });
    }

    private static void getJsonLocalConfigurationSync(Vertx vertx, String instancekey, final Configuration originalConfiguration, Consumer<JsonObject> consumer) {
        ConcurrentLinkedQueue<Consumer<JsonObject>> q = localQueue.computeIfAbsent(instancekey, (k) -> { return new ConcurrentLinkedQueue<Consumer<JsonObject>>(); });
        q.add(consumer);

        final String topic = localTopic(instancekey);
        JsonObject cachedMqttObject = MqttBrokerVerticle.syncBroker().syncApiPeek(topic);
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

                try { MqttBrokerVerticle.syncBroker().syncApiPublish(topic, json.getBytes("UTF-8"), 0, true, TTL); } catch (UnsupportedEncodingException uee) { logger.fatal("", uee); }
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
