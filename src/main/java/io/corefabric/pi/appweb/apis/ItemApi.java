package io.corefabric.pi.appweb.apis;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.kritikal.fabric.fabricapi.*;

import java.io.UnsupportedEncodingException;

@FabricApi(url="/corefabric-app/api/", worker=true)
public class ItemApi {

    @FabricHttpGet(url="item")
    public byte[] getItems(String url) {
        try {
            return (new JsonArray()).encode().getBytes("utf-8");
        }
        catch (UnsupportedEncodingException uee) {
            throw new RuntimeException(uee);
        }
    }

    @FabricHttpGet(url="item/{id}")
    public byte[] getItem(String url) {
        try {
            return (new JsonObject()).encode().getBytes("utf-8");
        }
        catch (UnsupportedEncodingException uee) {
            throw new RuntimeException(uee);
        }
    }

    @FabricHttpPost(url="item")
    public void addItem(String url, byte[] content) {
        // void
    }

    @FabricHttpPut(url="item/{id}")
    public void updateItem(String url, byte[] content) {
        // void
    }

    @FabricHttpDelete(url="item/{id}")
    public void deleteItem(String url) {
        // void
    }
}
