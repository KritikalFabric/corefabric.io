package org.kritikal.fabric;

import com.hazelcast.config.*;
import com.hazelcast.core.HazelcastInstance;
import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import org.kritikal.fabric.annotations.CFMain;
import org.kritikal.fabric.annotations.CFRoleRegistry;
import org.kritikal.fabric.annotations.IRoleRegistry;
import org.kritikal.fabric.core.exceptions.FabricError;
import org.kritikal.fabric.net.mqtt.entities.PublishMessage;
import org.kritikal.fabric.net.mqtt.entities.PublishMessageStreamSerializer;
import io.corefabric.pi.CoreFabricConfigShims;
import io.corefabric.pi.CoreFabricRoleRegistry;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;

import org.kritikal.fabric.core.BuildConfig;
import org.reflections.Reflections;

/**
 * Created by ben on 07/03/2016.
 */
public class CoreFabric {
    public final static Logger logger = LoggerFactory.getLogger(CoreFabric.class);
    public static JsonObject globalConfig = null;
    private static ArrayList<String> searchNamespaces = new ArrayList<>();
    public static class ServerConfiguration {
        public static final UUID instance = UUID.randomUUID();
        public static boolean PRODUCTION = false;
        public static boolean DEBUG = true;
        public static String resolver = "localhost";
        public static final int threads = Runtime.getRuntime().availableProcessors();
        public static boolean SLOWER = true; // watch out this cooks laptops
        public static String hostname = "localhost.localdomain";
        public static String name = "localhost";
        public static String zone = "localzone";
        public static String ip4 = null;
        public static class ClusterPeer {
            public ClusterPeer(String host, int port) {
                this.host = host;
                this.port = port;
            }
            public final String host;
            public final int port;
        }
        public static boolean hazelcastJoinTcpip = false;
        public static boolean hazelcastJoinMulticast = true;
        public static final ArrayList<ClusterPeer> peers = new ArrayList<>();
        protected static void apply(JsonObject globalConfig) {
            JsonObject node = globalConfig.getJsonObject("node");
            if (node != null) {
                hostname = node.getString("hostname", "localhost.localdomain");
                name = node.getString("name", "localhost");
                zone = node.getString("zone", "localzone");
                ip4 = node.getString("ip4", "127.0.0.1");
            }
            JsonObject cluster = globalConfig.getJsonObject("cluster");
            if (cluster != null) {
                hazelcastJoinMulticast = cluster.getBoolean("multicast", true);
                hazelcastJoinTcpip = cluster.getBoolean("tcpip", false);
                if (hazelcastJoinTcpip) {
                    JsonArray ary = cluster.getJsonArray("peers");
                    if (ary != null) {
                        for (int i = 0, l = ary.size(); i < l; ++i) {
                            JsonObject peer = ary.getJsonObject(i);
                            peers.add(new ClusterPeer(peer.getString("host"), peer.getInteger("port")));
                        }
                    }
                }
            }
        }
    }
    public static void addNamespace(String ns) { searchNamespaces.add(ns); }
    static String readFile(String file) {
        StringBuilder sb = new StringBuilder();
        InputStreamReader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file));
            char[] buffer = new char[1024];
            int n = 0;
            while ((n = reader.read(buffer)) > 0) {
                String s = String.copyValueOf(buffer, 0, n);
                sb.append(s);
            }
            return sb.toString();
        } catch (IOException ioe) {
            return null;
        } finally {
            try {
                if (reader != null) reader.close();
            } catch (IOException ioe) {
            }
        }
    }
    static {
        logger.info(BuildConfig.NAME + " - " + BuildConfig.VERSION);

        {
            ArrayList<String> files = new ArrayList<>();
            files.add("/deploy/config.json");
            files.add("config.json");
            files.add("config.json.example"); // defaults for developers ;)
            for (String file : files) {
                String config_json = readFile(file);
                if (config_json == null) continue;

                // foreach <<interface>> -> 1.2.3.4 ip address
                HashMap<String, String> replacements = new HashMap<>();
                try {
                    for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                        String ipv4 = "127.0.0.1";
                        for (InetAddress inetAddress : Collections.list(networkInterface.getInetAddresses())) {
                            if (inetAddress instanceof Inet4Address) {
                                ipv4 = inetAddress.getHostAddress();
                                break;
                            }
                        }
                        replacements.put("<<" + networkInterface.getName() + ">>", ipv4);
                    }
                } catch (SocketException se) {
                    logger.warn("Unable to enumerate network interfaces while loading config.json, continuing...");
                }
                String hostname = readFile("/deploy/hostname");
                if (null != hostname) {
                    replacements.put("<<hostname>>", hostname.trim());
                    String ipv4 = readFile("/deploy/ipv4");
                    if (null != ipv4) {
                        replacements.put("<<ipv4>>", ipv4.trim());
                    }

                    for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                        config_json = config_json.replaceAll(replacement.getKey(), replacement.getValue());
                    }

                    globalConfig = new JsonObject(config_json);
                    LoggerFactory.getLogger(CoreFabric.class).info("Loaded " + file + "\n" + config_json);
                    break;
                }
            }

            if (globalConfig == null) {
                logger.fatal("Unable to locate config.json");
            }
        }
    }
    private static volatile Vertx gVertx = null;
    private static HazelcastClusterManager hazelcastClusterManager = null;
    public static HazelcastInstance getHazelcastInstance() {
        if (hazelcastClusterManager != null) return hazelcastClusterManager.getHazelcastInstance();
        throw new FabricError("Unable to instantiate hazelcast.");
    }
    public static Vertx getVertx() {
        if (gVertx != null) return gVertx;
        throw new FabricError("Unable to instantiate vertx.");
    }

    private static final Future<Vertx> clusterVertxInTheFuture() {
        ServerConfiguration.apply(globalConfig);
        VertxOptions vertxOptions = new VertxOptions();
        com.hazelcast.config.Config hazelcastConfig = new com.hazelcast.config.Config();
        hazelcastConfig.getSerializationConfig().addSerializerConfig(new SerializerConfig().setTypeClass(PublishMessage.class).setClass(PublishMessageStreamSerializer.class));
        final JoinConfig joinConfig = hazelcastConfig.getNetworkConfig().getJoin();
        final TcpIpConfig tcpIpConfig = joinConfig.getTcpIpConfig();
        if (ServerConfiguration.hazelcastJoinTcpip) {
            joinConfig.getMulticastConfig().setEnabled(false);
            try {
                tcpIpConfig.setRequiredMember(ServerConfiguration.ip4);
            }
            catch (Throwable t) {
                logger.fatal("Adding tcpip required peer " + ServerConfiguration.hostname);
            }
            ServerConfiguration.peers.forEach(clusterPeer ->
            {
                if (clusterPeer.host.equals(ServerConfiguration.hostname)) return;
                try {
                    tcpIpConfig.addMember(Inet4Address.getByName(clusterPeer.host).getHostAddress());
                }
                catch (Throwable t) {
                    logger.warn("Adding tcpip peer " + clusterPeer.host + ":" + clusterPeer.port);
                }
            });
            tcpIpConfig.setEnabled(true);
        }
        if (!ServerConfiguration.hazelcastJoinMulticast && !ServerConfiguration.hazelcastJoinTcpip) {
            joinConfig.getMulticastConfig().setEnabled(false);
            tcpIpConfig.setEnabled(false);
        }
        hazelcastClusterManager = new HazelcastClusterManager(hazelcastConfig);
        vertxOptions.setClusterManager(hazelcastClusterManager);
        vertxOptions.setEventLoopPoolSize(Runtime.getRuntime().availableProcessors());
        vertxOptions.setWorkerPoolSize(ServerConfiguration.threads);
        vertxOptions.setBlockedThreadCheckInterval(5 * 3600 * 1000);
        vertxOptions.setWarningExceptionTime(5 * 3600 * 1000);
        vertxOptions.setMetricsOptions(new DropwizardMetricsOptions().setEnabled(true).setJmxEnabled(true).setJmxDomain("io.corefabric").setRegistryName("io.corefabric"));

        final Future<Vertx> vertxFuture = Future.future();
        Vertx.clusteredVertx(vertxOptions, res -> {
            if (res.succeeded()) {
                gVertx = res.result();
                CoreFabricRoleRegistry.addCoreFabricRoles(gVertx);
                CoreFabricConfigShims.apply(gVertx);
                CoreFabric.bootstrapRoleRegistries(gVertx);
                vertxFuture.complete(gVertx);
            } else {
                vertxFuture.fail("Unable to create clustered vertx.");
            }
        });

        return vertxFuture;
    }
    public volatile static boolean exit = false;
    public static void main(String args[]) {

        if (args.length > 1) {
            CoreFabricCLI.process_command(args);
            return;
        }

        Future<Vertx> ourFutureVertx = clusterVertxInTheFuture();
        ourFutureVertx.setHandler(ar -> {
            if (ar.failed()) { exit = true; return; }
            final Vertx vertx = ar.result();
            vertx.deployVerticle("io.corefabric.pi.MainVerticle", f -> {
                if (f.failed()) { exit = true; return; }
                CoreFabric.bootstrapMain(args, vertx);
            });
        });

        // TODO: locking & become daemon?
        while(!exit) {
            try {
                Thread.sleep(9997);
            }
            catch (InterruptedException ex) {
                // ignore
            }
        }

        Runtime.getRuntime().exit(-1);
    }
    public static Vertx start() {
        if (gVertx == null) {
            synchronized (CoreFabric.class) {
                if (gVertx == null) {
                    Future<Void> future = Future.future();
                    Future<Vertx> ourFutureVertx = clusterVertxInTheFuture();
                    ourFutureVertx.setHandler(ar -> {
                        if (ar.failed()) { exit = true; future.fail(""); return; }
                        final Vertx vertx = ar.result();
                        vertx.deployVerticle("io.corefabric.pi.MainVerticle", f -> {
                            if (f.failed()) { exit = true; future.fail(""); return; }
                            logger.info("ONLINE, press ^C to exit.");
                            future.complete();
                        });
                    });
                    while (!exit && !future.isComplete()) {
                        try {
                            Thread.sleep(97);
                        } catch (InterruptedException ex) {
                            // ignore
                        }
                    }
                    if (gVertx == null || !future.succeeded()) throw new FabricError("Startup problem");
                }
            }
        }
        return gVertx;
    }
    private static void bootstrapMain(String[] args, Vertx vertx) {
        vertx.executeBlocking(future -> {
            for (String ns : searchNamespaces) {
                Reflections reflections = new Reflections(ns);
                for (Class<?> clazz : reflections.getTypesAnnotatedWith(CFMain.class)) {
                    try {
                        clazz.getMethod("main", String[].class, Vertx.class).invoke(null, args, vertx);
                    } catch (NoSuchMethodException e1) {
                        logger.fatal("corefabric.io\t\tUnable to bootstrap " + clazz.getCanonicalName() + " no entrypoint.");
                    } catch (IllegalAccessException e2) {
                        logger.fatal("corefabric.io\t\tUnable to bootstrap " + clazz.getCanonicalName() + " illegal access.");
                    } catch (InvocationTargetException e3) {
                        logger.fatal("corefabric.io\t\tUnable to bootstrap " + clazz.getCanonicalName() + " inovocation target.");
                    }
                }
            }
            future.complete(true);
        }, res -> {
            logger.info("corefabric.io\t\tONLINE, press ^C to exit.");
        });

    }
    private static void bootstrapRoleRegistries(Vertx vertx) {
        for (String ns : searchNamespaces) {
            Reflections reflections = new Reflections(ns);
            for (Class<?> clazz : reflections.getTypesAnnotatedWith(CFRoleRegistry.class)) {
                try {
                    IRoleRegistry roleRegistry = (IRoleRegistry) clazz.getConstructor().newInstance();
                    roleRegistry.addRoles(vertx);
                } catch (NoSuchMethodException e1) {
                    logger.fatal("corefabric.io\t\tUnable to bootstrap registry " + clazz.getCanonicalName() + " no entrypoint.");
                } catch (InstantiationException e2) {
                    logger.fatal("corefabric.io\t\tUnable to bootstrap registry " + clazz.getCanonicalName() + " instantiation.");
                } catch (IllegalAccessException e2) {
                    logger.fatal("corefabric.io\t\tUnable to bootstrap registry " + clazz.getCanonicalName() + " illegal access.");
                } catch (InvocationTargetException e3) {
                    logger.fatal("corefabric.io\t\tUnable to bootstrap registry " + clazz.getCanonicalName() + " inovocation target.");
                }
            }
        }
    }
}
