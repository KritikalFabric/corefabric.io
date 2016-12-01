package org.kritikal.fabric.dtn.jdtn;

import com.cisco.qte.jdtn.Shell;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.ibrdtnapi.BpApplication;
import org.ibrdtnapi.BundleHandler;
import org.ibrdtnapi.entities.Bundle;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

public class JDTNBridgeVerticle extends AbstractVerticle  implements BundleHandler
    {

        private Shell myShell;

//  public void start() {
    // Create an HTTP server which simply returns "Hello World!" to each request.
    // If a configuration is set it get the specified name
//    String name = config().getString("name", "DTN World!");
//    vertx.createHttpServer().requestHandler(req -> req.response().end("Hello " + name + "!")).listen(8080);
//  }

  Logger logger;
  MessageConsumer mcRecv;
        private BpApplication bundleSender;

        @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);

    config().put("name","fbb2ca21-d81c-479a-b17c-90f29e01fd5f");
  }

  @Override
  public void start(Future<Void> startFuture) {
    logger = LoggerFactory.getLogger(getClass());

    String localaddress=this.config().getString("name");
    if (localaddress==null || localaddress.trim().isEmpty()) localaddress=UUID.randomUUID().toString();
    final String finalLocaladdress = localaddress;
      logger.info("\tLocal address: "+localaddress);
    bundleSender = new BpApplication(finalLocaladdress);

    mcRecv = getVertx().eventBus().localConsumer(localaddress, (event) -> {
      final JsonObject mqttMessage = (JsonObject)event.body();
      final String topic = mqttMessage.getString("topic");
      final byte[] payload = mqttMessage.getBinary("body.0"); //Todo: How to handle multi block bundles...
      Bundle bundle = new Bundle(finalLocaladdress, payload);


        Set<String> headerkeys=event.headers().names();
        for (Iterator<String> iterator = headerkeys.iterator(); iterator.hasNext(); ) {
            String someheaderkey = iterator.next();
            if (someheaderkey.equalsIgnoreCase("custodian")) bundle.setCustodian(event.headers().get(someheaderkey));
            else if (someheaderkey.equalsIgnoreCase("destination")) bundle.setDestination(event.headers().get(someheaderkey));
            else if (someheaderkey.equalsIgnoreCase("flags")) bundle.setFlags(Integer.parseInt(event.headers().get(someheaderkey)));
            else if (someheaderkey.equalsIgnoreCase("lifetime")) bundle.setLifetime(Integer.parseInt(event.headers().get(someheaderkey)));
            else if (someheaderkey.equalsIgnoreCase("blocks")) bundle.setBlockNumber(Integer.parseInt(event.headers().get(someheaderkey)));
            else if (someheaderkey.equalsIgnoreCase("reportto")) bundle.setReportto(event.headers().get(someheaderkey));
            else if (someheaderkey.equalsIgnoreCase("sequenceno")) bundle.setSequencenumber(Integer.parseInt(event.headers().get(someheaderkey)));
            else if (someheaderkey.equalsIgnoreCase("source")) bundle.setSource(event.headers().get(someheaderkey));
            else if (someheaderkey.equalsIgnoreCase("timestamp")) bundle.setTimestamp(Long.parseLong(event.headers().get(someheaderkey)));

        }

      //String destination = "dtn://Alexs-Mac-Pro.local/sender";


      // TODO: implementation of on-message callback; this code runs
      // TODO: inside the vertx event loop therefore must not block.


                getVertx().executeBlocking(f -> {
                    try {
                        // TODO: any blocking code must go here.
                        bundleSender.send(bundle);
                        f.complete();
                    }
                    catch (Throwable t){
                        f.fail(t);
                    }
                }, false, r -> {});
    });
   // MqttBrokerVerticle.mqttBroker().apiSubscribe("$dtn/#", uuid.toString());

    // TODO: create DTN client here and use it with care :)
    // TODO: this is a single-threaded worker verticle therefore code may block if it wants.
    // TODO: but if you're doing that why not use a SyncVerticle?
    bundleSender.setHandler(this);

      logger.info("DTN Neighbor list");
    logger.info(bundleSender.getNeighborList());
    logger.info("\tStarted.");
    startFuture.complete();
  }

  public void stop(Future<Void> stopFuture) {
    mcRecv.unregister();
      bundleSender.stop();
    stopFuture.complete();
    logger.info("\tFinished.");
  }

    @Override
    public void onReceive(Bundle bundle) {

        JsonObject mqttMessage=new JsonObject();
        DeliveryOptions options=new DeliveryOptions(mqttMessage);
        options.addHeader("custodian", bundle.getCustodian());
        options.addHeader("destination", bundle.getDestination());

        options.addHeader("flags", bundle.getFlags()+"");
        options.addHeader("lifetime", bundle.getLifetime()+"");
        options.addHeader("blocks", bundle.getNumberOfBlocks()+"");
        options.addHeader("reportto", bundle.getReportto());
        options.addHeader("sequenceno", bundle.getSequencenumber()+"");
        options.addHeader("source", bundle.getSource());
        options.addHeader("timestamp", bundle.getTimestamp() + "");
        int blocks=bundle.getNumberOfBlocks();
        for (int i = 0; i < blocks; i++) {
            mqttMessage.put("body."+i, bundle.getDecoded(i));
        }

        logger.info("Received a bundle converted to json " + mqttMessage.toString());

        String vertxDestination=bundle.getDestination().substring(bundle.getDestination().lastIndexOf("/") + 1);

        mqttMessage.put("topic",vertxDestination);

        logger.info("Vertx destination address is " + vertxDestination);
        getVertx().eventBus().publish(vertxDestination, mqttMessage,options);

        //logger.info("Received a bundle "+bundle.toString());
    }
}

