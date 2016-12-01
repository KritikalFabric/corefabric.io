package io.corefabric.pi.appweb;

/**
 * Created by ben on 03/11/2016.
 */
/**
 * Created by ben on 7/6/16.
 */
public interface IDocApiProvider {

    void open_singleton(UIDocApiWorkerVerticle.Context context);

    void open(UIDocApiWorkerVerticle.Context context);

    void list(UIDocApiWorkerVerticle.Context context);

    void upsert(UIDocApiWorkerVerticle.Context context);

}
