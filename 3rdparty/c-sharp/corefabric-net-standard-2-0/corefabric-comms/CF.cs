using System;

namespace corefabric_comms
{
    public class CF
    {
        public const string COREFABRIC_VERSION = "x.y.z";
        public const string COREFABRIC_REVISION = "<<REVISION>>";

        private static CF _CF = null;
        static CF() {
            CF._CF = new CF();
        }

        private CF() {
            if (CF._CF != null) throw new Exception("This is our singleton, use once.");
            this._fabric = new Fabric();
        }

        private Fabric _fabric;
        public static Fabric fabric {
            get {
                return _CF._fabric;
            }
        }
    }
}
