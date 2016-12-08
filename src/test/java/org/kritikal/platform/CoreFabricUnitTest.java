package org.kritikal.platform;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.kritikal.fabric.CoreFabric;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
/**
 * Created by ben on 08/12/2016.
 */
@Ignore
public abstract class CoreFabricUnitTest {

    static boolean deployed = false;

    @ClassRule
    public static RunTestOnContext rule = new RunTestOnContext(new Supplier<Vertx>() {
        @Override
        public Vertx get() {
            return CoreFabric.start();
        }
    }, new BiConsumer<Vertx, Consumer<Void>>() {
        @Override
        public void accept(Vertx vertx, Consumer<Void> voidConsumer) {
            // don't!!! -- vertx.close();
            voidConsumer.accept(null);
        }
    });
}