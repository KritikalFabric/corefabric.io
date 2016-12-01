package org.kritikal.fabric.core;

import io.vertx.core.json.JsonObject;

import java.util.function.Function;

/**
 * Created by ben on 11/26/16.
 */
public class VertxHelpers {

    /**
     * Thread-safety helper for JsonObjects.
     * @param lock object holding a mutex for this json object (hierarchy)
     * @param object parameter to pass to the compute expression
     * @param compute expression to evalute inside synchronized context
     * @return object after its compute expression has been applied.
     */
    public static JsonObject compute(Object lock, JsonObject object, Function<JsonObject, JsonObject> compute) {
        synchronized (lock) {
            return compute.apply(object);
        }
    }

    public static String toString(Object lock, JsonObject object) {
        final String s;
        synchronized (lock) {
            s = object.toString();
        }
        return s;
    }

}
