package io.corefabric.pi.appweb.providers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.kritikal.fabric.core.exceptions.FabricError;
import io.corefabric.pi.appweb.IDocApiProvider;
import io.corefabric.pi.appweb.UIDocApiWorkerVerticle;
import io.corefabric.pi.appweb.DocApiCall;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ben on 03/11/2016.
 */
public class HomeProvider implements IDocApiProvider {
    private final static Logger logger = LoggerFactory.getLogger(HomeProvider.class);
    public final static String PATH = "/home";

    private DocApiCall __init(UIDocApiWorkerVerticle.Context context) {
        DocApiCall call = new DocApiCall(context, context.cfg.instancekey + PATH);
        call.o.put("topics", new JsonArray());
        return call;
    }

    private DocApiCall __reinit(UIDocApiWorkerVerticle.Context context) {
        DocApiCall call = new DocApiCall(context, context.cfg.instancekey + PATH, context.apicall.getJsonObject("ad").getJsonObject("data"));
        return call;
    }

    private void __complete(DocApiCall call) {
        // TODO: flesh out data structure
        // if changed:
        //if (!currentCall.getOrDefault(call.context.cfg.instancekey, call.uuid).equals(call.uuid)) return;
        //call.publish();
    }

    @Override
    public void open_singleton(UIDocApiWorkerVerticle.Context context) {
        DocApiCall call = __init(context);

        JsonArray topics = new JsonArray();
        topics.add("nodes/pi/node-0001/demo");
        topics.add("nodes/pi/node-0002/demo");
        topics.add("nodes/pi/node-0003/demo");
        call.o.put("topics", topics);

        call.publish();
        call.reply();

        __complete(call);
    }

    @Override
    public void open(UIDocApiWorkerVerticle.Context context) {
        throw new FabricError();
    }

    @Override
    public void list(UIDocApiWorkerVerticle.Context context) {
        throw new FabricError();
    }

    @Override
    public void upsert(UIDocApiWorkerVerticle.Context context) { throw new FabricError(); }
}
