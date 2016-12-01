package org.kritikal.fabric.core;

import io.vertx.core.eventbus.DeliveryOptions;

/**
 * Created by ben on 15/11/15.
 */
public class VERTXDEFINES {

    private final static long LONGTIMEOUT = 5*3600000l;

    public final static DeliveryOptions DELIVERY_OPTIONS = new DeliveryOptions().setSendTimeout(LONGTIMEOUT);

}
