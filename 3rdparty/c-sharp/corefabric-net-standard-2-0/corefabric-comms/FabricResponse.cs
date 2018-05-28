using System;

namespace corefabric_comms
{
    public class FabricResponse
    {
        private readonly bool _success;

        public FabricResponse(bool success)
        {
            this._success = success;
        }

        public bool success { get { return _success; } }
    }
}
