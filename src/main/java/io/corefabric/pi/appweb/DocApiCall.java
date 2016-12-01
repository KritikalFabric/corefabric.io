package io.corefabric.pi.appweb;

import com.hazelcast.core.IMap;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.daemon.MqttBrokerVerticle;
import org.kritikal.fabric.core.VERTXDEFINES;
import org.kritikal.fabric.core.VertxHelpers;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

/**
 * Created by ben on 03/11/2016.
 */
public final class DocApiCall {
    private final static Logger logger  = LoggerFactory.getLogger(DocApiCall.class);
    public DocApiCall(UIDocApiWorkerVerticle.Context context, String topic) {
        this.context = context;
        this.unique = "$/" + context.corefabric + "/api/u/" + CoreFabric.ServerConfiguration.zone + "/" + CoreFabric.ServerConfiguration.name + "/" + topic + "/|";
        this.topic = "$/-/api/d/" + CoreFabric.ServerConfiguration.zone + "/" + CoreFabric.ServerConfiguration.name + "/" + topic + "/|";
        this.statusTopic =  "$/" + CoreFabric.ServerConfiguration.instance + "/api/s/" + context.cfg.instancekey + "/" + this.uuid + "/|";
        this.o = new JsonObject();
        currentCall.put(this.unique, this.uuid);
    }
    public DocApiCall(UIDocApiWorkerVerticle.Context context, String topic, JsonObject o) {
        this.context = context;
        this.unique = "$/" + context.corefabric + "/api/u/" + CoreFabric.ServerConfiguration.zone + "/" + CoreFabric.ServerConfiguration.name + "/" + topic + "/|";
        this.topic = "$/-/api/d/" + CoreFabric.ServerConfiguration.zone + "/" + CoreFabric.ServerConfiguration.name + "/" + topic + "/|";
        this.statusTopic =  "$/" + CoreFabric.ServerConfiguration.instance + "/api/s/" + this.uuid.toString() + "/|";
        this.o = o;
        currentCall.put(this.unique, this.uuid);
    }
    public final String unique;
    private final IMap<String, UUID> currentCall = CoreFabric.getHazelcastInstance().getMap("appweb.docapi");
    public final boolean hasExited() {
        return !currentCall.getOrDefault(unique, null).equals(uuid);
    }
    public final UUID uuid = UUID.randomUUID();
    public final String topic;
    public final String statusTopic;
    public final JsonObject o;
    public final UIDocApiWorkerVerticle.Context context;
    public final void publish() {
        try {
            MqttBrokerVerticle.mqttBroker().apiPublish(topic, VertxHelpers.toString(o, o).getBytes("UTF-8"), 2, true);
        } catch (UnsupportedEncodingException ue) {
            logger.warn(ue);
        }
    }

    public final void reply() {
        JsonObject reply = new JsonObject();
        reply.put("success", true);
        reply.put("topic", topic);
        reply.put("statusTopic", statusTopic);
        context.message.reply(reply, VERTXDEFINES.DELIVERY_OPTIONS);
    }
}
