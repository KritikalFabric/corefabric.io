package org.kritikal.fabric.dtn.jdtn;

import com.cisco.qte.jdtn.apps.AppManager;
import com.cisco.qte.jdtn.bp.BPManagement;
import com.cisco.qte.jdtn.bp.BpApi;
import com.cisco.qte.jdtn.persistance.DBInterfaceJDBC;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import com.cisco.qte.jdtn.general.Management;
import org.kritikal.fabric.contrib.jdtn.JDtnConfig;

/**
 * Created by ben on 24/10/2016.
 */
public class JsonConfigShim {

    private static boolean bootstrapped = false;

    public static void bootstrap() {
        if (!bootstrapped) {
            bootstrapped = true;
            Management.getInstance().start();
            BPManagement.getInstance().requestBundleRestore();
            AppManager.getInstance().start();
        }
    }

    public static void apply(Vertx vertx, JsonObject globalConfig) {
        JsonObject node = globalConfig.getJsonObject("node", new JsonObject());
        String hostname = node.getString("hostname", "localhost.localdomain");
        String ip4 = node.getString("ip4", "127.0.0.1");
        //TODO:String ip6 = node.getString("ip6", "::1");
        String localhostIf = node.getString("localhostIf", "lo0");
        String wanIf = node.getString("wanIf", "en0");

        JsonObject node_db = node.getJsonObject("node_db", new JsonObject());

        DBInterfaceJDBC.host = node_db.getString("host", "localhost");
        DBInterfaceJDBC.port = node_db.getInteger("port", 5432);
        DBInterfaceJDBC.db = node_db.getString("db", "corefabric__node_db");
        DBInterfaceJDBC.user = node_db.getString("user", "postgres");
        DBInterfaceJDBC.password = node_db.getString("password", "password");

        JsonObject dtn = globalConfig.getJsonObject("dtn", new JsonObject());
        boolean isRouter = dtn.getBoolean("isRouter", false);

        if (isRouter) {
            JDtnConfig.xml = "<JDtnConfig version='3'>\n" +
                    "  <General\n" +
                    "  >\n" +
                    "  </General>\n" +
                    "  <LTP\n" +
                    "    engineId='254.128.0.0.0.0.0.0.216.13.142.74.59.156.151.101'\n" +
                    "    testInterface='"+localhostIf+"'\n" +
                    "  >\n" +
                    "    <Links>\n" +
                    "      <Link\n" +
                    "        type='tcpcl'\n" +
                    "        linkName='"+localhostIf+"'\n" +
                    "        ifName='"+localhostIf+"'\n" +
                    "        ipv6='false'\n" +
                    "      >\n" +
                    "      </Link>\n" +
                    "      <Link\n" +
                    "        type='tcpcl'\n" +
                    "        linkName='"+wanIf+"'\n" +
                    "        ifName='"+wanIf+"'\n" +
                    "        ipv6='false'\n" +
                    "      >\n" +
                    "      </Link>\n" +
                    "    </Links>\n" +
                    "    <Neighbors>\n" +
                    "        <Neighbor\n" +
                    "          type='tcpcl'\n" +
                    "          name='" + hostname + "'\n" +
                    "          endPointIdStem='dtn://" + hostname + "'\n" +
                    "        >\n" +
                    "          <LinkAddress\n" +
                    "            link='"+localhostIf+"'\n" +
                    "            address='127.0.0.1'\n" +
                    "            addressType='ip'\n" +
                    "          />\n" +
                    "        </Neighbor>\n" +
                    "    </Neighbors>\n" +
                    "  </LTP>\n" +
                    "  <BP\n" +
                    "    endPointId='dtn://" + hostname + "'\n" +
                    "  >\n" +
                    "    <RouteTable>\n" +
                    "      <Route\n" +
                    "        name='" + hostname + "'\n" +
                    "        pattern='dtn://" + hostname + "(/[^/]+)*'\n" +
                    "        link='"+localhostIf+"'\n" +
                    "        neighbor='" + hostname + "'\n" +
                    "      />\n" +
                    "    </RouteTable>\n" +
                    "    <EidMap>\n" +
                    "    </EidMap>\n" +
                    "  </BP>\n" +
                    "  <Applications>\n" +
                    "    <Application\n" +
                    "      name='Router'\n" +
                    "      class='com.cisco.qte.jdtn.apps.RouterApp'\n" +
                    "    />\n" +
                    "    <Application\n" +
                    "      name='MqttBridge'\n" +
                    "      class='org.kritikal.fabric.daemon.MqttBridgeVerticle$MqttBridgeApp'\n" +
                    "    />\n" +
                    "    <Application\n" +
                    "      name='CorefabricPing'\n" +
                    "      class='io.corefabric.pi.appweb.providers.DtnConfigProvider$CorefabricPingApp'\n" +
                    "    />\n" +
                    /*
                    "    <Application\n" +
                    "      name='IonSourceSink'\n" +
                    "      class='com.cisco.qte.jdtn.apps.IonSourceSinkApp'\n" +
                    "    />\n" +
                    "    <Application\n" +
                    "      name='dtncp'\n" +
                    "      class='com.cisco.qte.jdtn.apps.Dtn2CpApp'\n" +
                    "    />\n" +
                    "    <Application\n" +
                    "      name='Text'\n" +
                    "      class='com.cisco.qte.jdtn.apps.TextApp'\n" +
                    "    />\n" +
                    "    <Application\n" +
                    "      name='Photo'\n" +
                    "      class='com.cisco.qte.jdtn.apps.PhotoApp'\n" +
                    "    />\n" +
                    "    <Application\n" +
                    "      name='Video'\n" +
                    "      class='com.cisco.qte.jdtn.apps.VideoApp'\n" +
                    "    />\n" +
                    "    <Application\n" +
                    "      name='Voice'\n" +
                    "      class='com.cisco.qte.jdtn.apps.VoiceApp'\n" +
                    "    />\n" +
                    */
                    "  </Applications>\n" +
                    "  <TcpCl\n" +
                    "  />\n" +
                    "  <UdpCl\n" +
                    "  />\n" +
                    "</JDtnConfig>\n";
        } else {
            String routerHostname = dtn.getString("routerHostname", "localrouter.localdomain");
            String routerIp4 = dtn.getString("routerIp4", "127.1.0.1");

            JDtnConfig.xml = "<JDtnConfig version='3'>\n" +
                    "  <General\n" +
                    "  >\n" +
                    "  </General>\n" +
                    "  <LTP\n" +
                    "    engineId='" + ip4 + "'\n" +
                    "    testInterface='"+localhostIf+"'\n" +
                    "  >\n" +
                    "    <Links>\n" +
                    "      <Link\n" +
                    "        type='tcpcl'\n" +
                    "        linkName='"+localhostIf+"'\n" +
                    "        ifName='"+localhostIf+"'\n" +
                    "        ipv6='false'\n" +
                    "      >\n" +
                    "      </Link>\n" +
                    "      <Link\n" +
                    "        type='tcpcl'\n" +
                    "        linkName='"+wanIf+"'\n" +
                    "        ifName='"+wanIf+"'\n" +
                    "        ipv6='false'\n" +
                    "      >\n" +
                    "      </Link>\n" +
                    "    </Links>\n" +
                    "    <Neighbors>\n" +
                    "        <Neighbor\n" +
                    "          type='tcpcl'\n" +
                    "          name='" + hostname + "'\n" +
                    "          endPointIdStem='dtn://" + hostname + "'\n" +
                    "        >\n" +
                    "          <LinkAddress\n" +
                    "            link='"+localhostIf+"'\n" +
                    "            address='127.0.0.1'\n" +
                    "            addressType='ip'\n" +
                    "          />\n" +
                    "        </Neighbor>\n" +
                    "        <Neighbor\n" +
                    "          type='tcpcl'\n" +
                    "          name='" + routerHostname + "'\n" +
                    "          endPointIdStem='dtn://" + routerHostname + "'\n" +
                    "        >\n" +
                    "          <LinkAddress\n" +
                    "            link='"+wanIf+"'\n" +
                    "            address='" + routerIp4 + "'\n" +
                    "            addressType='ip'\n" +
                    "          />\n" +
                    "        </Neighbor>\n" +
                    "    </Neighbors>\n" +
                    "  </LTP>\n" +
                    "  <BP\n" +
                    "    endPointId='dtn://" + hostname + "'\n" +
                    "  >\n" +
                    "    <RouteTable>\n" +
                    "      <DefaultRoute\n" +
                    "        name='" + routerHostname + "'\n" +
                    "        link='"+wanIf+"'\n" +
                    "        neighbor='" + routerHostname + "'\n" +
                    "      />\n" +
                    "      <Route\n" +
                    "        name='" + hostname + "'\n" +
                    "        pattern='dtn://" + hostname + "(/[^/]+)*'\n" +
                    "        link='"+localhostIf+"'\n" +
                    "        neighbor='" + hostname + "'\n" +
                    "      />\n" +
                    "    </RouteTable>\n" +
                    "    <EidMap>\n" +
                    "    </EidMap>\n" +
                    "  </BP>\n" +
                    "  <Applications>\n" +
                    "    <Application\n" +
                    "      name='Router'\n" +
                    "      class='com.cisco.qte.jdtn.apps.RouterApp'\n" +
                    "    />\n" +
                    "    <Application\n" +
                    "      name='MqttBridge'\n" +
                    "      class='org.kritikal.fabric.daemon.MqttBridgeVerticle$MqttBridgeApp'\n" +
                    "    />\n" +
                    "    <Application\n" +
                    "      name='CorefabricPing'\n" +
                    "      class='io.corefabric.pi.appweb.providers.DtnConfigProvider$CorefabricPingApp'\n" +
                    "    />\n" +
                    /*
                    "    <Application\n" +
                    "      name='IonSourceSink'\n" +
                    "      class='com.cisco.qte.jdtn.apps.IonSourceSinkApp'\n" +
                    "    />\n" +
                    "    <Application\n" +
                    "      name='dtncp'\n" +
                    "      class='com.cisco.qte.jdtn.apps.Dtn2CpApp'\n" +
                    "    />\n" +
                    "    <Application\n" +
                    "      name='Text'\n" +
                    "      class='com.cisco.qte.jdtn.apps.TextApp'\n" +
                    "    />\n" +
                    "    <Application\n" +
                    "      name='Photo'\n" +
                    "      class='com.cisco.qte.jdtn.apps.PhotoApp'\n" +
                    "    />\n" +
                    "    <Application\n" +
                    "      name='Video'\n" +
                    "      class='com.cisco.qte.jdtn.apps.VideoApp'\n" +
                    "    />\n" +
                    "    <Application\n" +
                    "      name='Voice'\n" +
                    "      class='com.cisco.qte.jdtn.apps.VoiceApp'\n" +
                    "    />\n" +
                    */
                    "  </Applications>\n" +
                    "  <TcpCl\n" +
                    "  />\n" +
                    "  <UdpCl\n" +
                    "  />\n" +
                    "</JDtnConfig>\n";
        }
    }

}
