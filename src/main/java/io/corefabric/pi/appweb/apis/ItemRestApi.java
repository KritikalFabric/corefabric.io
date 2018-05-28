package io.corefabric.pi.appweb.apis;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.kritikal.fabric.fabricapi.*;

import java.io.UnsupportedEncodingException;

@FabricRestApi(url="/corefabric-app/api/", worker=false)
public class ItemRestApi {

    @FabricHttpGet(url="item")
    public JsonArray getItems(FabricRestCall call) {
        JsonArray ary = new JsonArray();
        for (int i = 1; i <= 3; ++i) {
            JsonObject o = new JsonObject();
            o.put("id", i);
            o.put("title", "title " + i);
            o.put("description", "description " + i);
            ary.add(o);
        }
        return ary;
    }

    @FabricHttpGet(url="item/:id")
    public JsonObject getItem(FabricRestCall call) {
        int id = Integer.parseInt(call.param("id"));
        JsonObject o = new JsonObject();
        o.put("id", id);
        o.put("title", "title " + id);
        o.put("description", "description " + id);
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
