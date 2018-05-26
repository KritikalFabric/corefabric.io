using System;
using System.Threading.Tasks;
using System.Net.Http;


namespace corefabric_comms
{
    public class Fabric
    {
        public Fabric()
        {
            System.Console.WriteLine(CF.COREFABRIC_VERSION + " - " + CF.COREFABRIC_REVISION);
        }

        public async Task<Tuple<FabricResponse, string>> GetStringAsync(Uri baseAddress, string path) {
            var result = await Task.Run(() => { return new Tuple<FabricResponse, string>(new FabricResponse(false), string.Empty); });
            return result;
        }

        public async Task<FabricResponse> PostAsync(Uri baseAddress, string path, StringContent stringContent)
        {
            var result = await Task.Run(() => { return new FabricResponse(false); });
            return result;
        }

        public async Task<FabricResponse> PutAsync(Uri baseAddress, string path, ByteArrayContent byteArrayContent)
        {
            var result = await Task.Run(() => { return new FabricResponse(false); });
            return result;
        }

        public async Task<FabricResponse> DeleteAsync(Uri baseAddress, string path)
        {
            var result = await Task.Run(() => { return new FabricResponse(false); });
            return result;
        }
    }
}
