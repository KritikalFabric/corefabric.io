package io.corefabric.pi.appweb.apis;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.kritikal.fabric.fabricapi.*;

import java.io.UnsupportedEncodingException;

@FabricRestApi(url="/corefabric-app/api/", worker=true)
public class ItemRestApi {

    @FabricHttpGet(url="item")
    public JsonArray getItems(FabricRestCall call) {
        return new JsonArray();
    }

    @FabricHttpGet(url="item/:id")
    public JsonObject getItem(FabricRestCall call) {
        int id = Integer.parseInt(call.param("id"));
        JsonObject o = new JsonObject();
        o.put("id", id);
        return o;
    }

    @FabricHttpPost(url="item")
    public void addItem(FabricRestCall call) {
        // void
        JsonObject object = call.object();
    }

    @FabricHttpPut(url="item/:id")
    public void updateItem(FabricRestCall call) {
        // void
        int id = Integer.parseInt(call.param("id"));
        JsonObject object = call.object();
    }

    @FabricHttpDelete(url="item/:id")
    public void deleteItem(FabricRestCall call) {
        // void
        int id = Integer.parseInt(call.param("id"));
    }
}
