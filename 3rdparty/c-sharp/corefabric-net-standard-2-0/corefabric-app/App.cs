using System;

namespace corefabricapp
{
    public class App
    {
        public static bool UseMockDataStore = false;

        // your TO-DO: point at your local hosted service please or the apps can't talk to core fabric!
        public static string BackendUrl = "http://joyent-build.test.corefabric.io:1080";

        public static void Initialize()
        {
            if (UseMockDataStore)
                ServiceLocator.Instance.Register<IDataStore<Item>, MockDataStore>();
            else
                ServiceLocator.Instance.Register<IDataStore<Item>, FabricDataStore>();
        }
    }
}
