package io.corefabric.pi.appweb.apis;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.kritikal.fabric.fabricapi.*;

import java.io.UnsupportedEncodingException;

@FabricStreamApi(url="/corefabric-app/stream/")
public class ItemStreamApi {

    // TODO: return result ref, publish when retrieved via broker [public channel, $response channel]
    @FabricHttpGet(url="item")
    public byte[] getItems(String url) {
        try {
            return (new JsonArray()).encode().getBytes("utf-8");
        }
        catch (UnsupportedEncodingException uee) {
            throw new RuntimeException(uee);
        }
    }

    // TODO: return result ref, publish when retrieved via broker [public channel, $response channel]
    @FabricHttpGet(url="item/:id")
    public byte[] getItem(String url) {
        try {
            return (new JsonObject()).encode().getBytes("utf-8");
        }
        catch (UnsupportedEncodingException uee) {
            throw new RuntimeException(uee);
        }
    }

    // TODO: return result ref, publish when retrieved via broker [public channel, $response channel]
    @FabricHttpPost(url="item")
    public void addItem(String url, byte[] content) {
        // void
    }

    // TODO: return result ref, publish when retrieved via broker [public channel, $response channel]
    @FabricHttpPut(url="item/:id")
    public void updateItem(String url, byte[] content) {
        // void
    }

    // TODO: return result ref, publish when retrieved via broker [public channel, $response channel]
    @FabricHttpDelete(url="item/:id")
    public void deleteItem(String url) {
        // void
    }

}
