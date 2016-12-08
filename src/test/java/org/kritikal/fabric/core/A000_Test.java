package org.kritikal.fabric.core;

import io.vertx.core.Vertx;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kritikal.platform.CoreFabricUnitTest;
import org.kritikal.platform.MyVertxUnitRunner;

/**
 * Created by ben on 08/12/2016.
 */
@RunWith(MyVertxUnitRunner.class)
public class A000_Test extends CoreFabricUnitTest {

    @Test
    public void a000_DidItWork() {
        Vertx vertx = rule.vertx();
        Assert.assertNotNull(vertx);
    }

}