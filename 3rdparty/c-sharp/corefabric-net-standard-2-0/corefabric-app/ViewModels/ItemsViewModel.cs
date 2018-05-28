using System;
using System.Collections.ObjectModel;
using System.Diagnostics;
using System.Threading.Tasks;

namespace corefabricapp
{
    public class ItemsViewModel : BaseViewModel
    {
        // TODO: our API will contain plumbing for ObservableCollection<T>
        //
        public ObservableCollection<Item> Items { get; set; }

        public Command LoadItemsCommand { get; set; }
        public Command AddItemCommand { get; set; }

        public ItemsViewModel()
        {
            Title = "Browse";
            Items = new ObservableCollection<Item>();

            // TODO: CF.fabric.subscribe(Items, "corefabric-app/development/demo/api/item/#");

            LoadItemsCommand = new Command(async () => await ExecuteLoadItemsCommand());
            AddItemCommand = new Command<Item>(async (Item item) => await AddItem(item));
        }

        async Task ExecuteLoadItemsCommand()
        {
            if (IsBusy)
                return;

            IsBusy = true;

            try
            {
                var items = await DataStore.GetItemsAsync(true);
                Items.Clear();
                foreach (var item in items)
                {
                    Items.Add(item);
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine(ex);
                Items.Clear();
            }
            finally
            {
                IsBusy = false;
            }
        }

        async Task AddItem(Item item)
        {
            // TODO: our API will eventually contain a local push-buffer of changes
            //       for REST / FabricApi plumbing
            await DataStore.AddItemAsync(item);

            // TODO: eventually our API will plumb stuff so well this is also automatic
            //
            await ExecuteLoadItemsCommand();
        }
    }
}
