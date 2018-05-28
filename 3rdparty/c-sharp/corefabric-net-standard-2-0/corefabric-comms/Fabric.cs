using System;
using System.Threading.Tasks;
using System.Net.Http;
using System.Net.WebSockets;

namespace corefabric_comms
{
    public class Fabric
    {
        public Fabric()
        {
            System.Console.WriteLine(CF.COREFABRIC_VERSION + " - " + CF.COREFABRIC_REVISION);
        }

        private HttpClient http = new HttpClient();

        public async Task<Tuple<FabricResponse, string>> GetStringAsync(Uri baseAddress, string path) {
            var result = await Task.Run(async () =>
            {
                var r = await http.GetAsync(baseAddress + path);
                if (r.IsSuccessStatusCode) {
                    var content = await r.Content.ReadAsStringAsync();
                    return new Tuple<FabricResponse, string>(new FabricResponse(true), content); 
                } else {
                    return new Tuple<FabricResponse, string>(new FabricResponse(false), string.Empty);
                }
            });
            return result;
        }

        public async Task<FabricResponse> PostAsync(Uri baseAddress, string path, StringContent stringContent)
        {
            var result = await Task.Run(async () =>
            {
                var r = await Task.Run(() => http.PostAsync(baseAddress + path, stringContent));
                return new FabricResponse(r.IsSuccessStatusCode);
            });
            return result;
        }

        public async Task<FabricResponse> PutAsync(Uri baseAddress, string path, ByteArrayContent byteArrayContent)
        {
            var result = await Task.Run(async () =>
            {
                var r = await Task.Run(() => http.PutAsync(baseAddress + path, byteArrayContent));
                return new FabricResponse(r.IsSuccessStatusCode);
            });
            return result;
        }

        public async Task<FabricResponse> DeleteAsync(Uri baseAddress, string path)
        {
            var result = await Task.Run(async () =>
            {
                var r = await Task.Run(() => http.DeleteAsync(baseAddress + path));
                return new FabricResponse(r.IsSuccessStatusCode);
            });
            return result;
        }
    }
}
