using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Net.Http;
using System.Text;
using System.Threading.Tasks;

using Newtonsoft.Json;
using Plugin.Connectivity;

using corefabric_comms;

namespace corefabricapp
{
    public class FabricDataStore : IDataStore<Item>
    {
        public static bool is_connected { 
            get {
                return CrossConnectivity.Current.IsConnected;
            }
        }

        readonly Uri BaseAddress = new Uri($"{App.BackendUrl}/");

        public FabricDataStore()
        {
        }

        public async Task<IEnumerable<Item>> GetItemsAsync(bool ignore)
        {
            var tuple = await CF.fabric.GetStringAsync(BaseAddress, $"api/item");
            var items = await Task.Run(() => tuple.Item1.success ? JsonConvert.DeserializeObject<IEnumerable<Item>>(tuple.Item2 /* string, json encoding */) : null);
            return items;
        }

        public async Task<Item> GetItemAsync(string id)
        {
            var tuple = await CF.fabric.GetStringAsync(BaseAddress, $"api/item/{id}");
            return await Task.Run(() => tuple.Item1.success ? JsonConvert.DeserializeObject<Item>(tuple.Item2) : null);
        }

        public async Task<bool> AddItemAsync(Item item)
        {
            if (item == null)
                return false;

            var serializedItem = JsonConvert.SerializeObject(item);

            var response = await CF.fabric.PostAsync(BaseAddress, $"api/item", new StringContent(serializedItem, Encoding.UTF8, "application/json"));
            return response.success;
        }

        public async Task<bool> UpdateItemAsync(Item item)
        {
            if (item == null || item.Id == null)
                return false;

            var serializedItem = JsonConvert.SerializeObject(item);
            var buffer = Encoding.UTF8.GetBytes(serializedItem);
            var byteContent = new ByteArrayContent(buffer);

            var response = await CF.fabric.PutAsync(BaseAddress, $"api/item/{item.Id}", byteContent);
            return response.success;
        }

        public async Task<bool> DeleteItemAsync(string id)
        {
            if (string.IsNullOrEmpty(id))
                return false;

            var response = await CF.fabric.DeleteAsync(BaseAddress, $"api/item/{id}");
            return response.success;
        }
    }
}
