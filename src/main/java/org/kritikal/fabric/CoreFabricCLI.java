package org.kritikal.fabric;

public class CoreFabricCLI {
    public static void process_command(String args[]) {
        switch (args[1]) {
            case "--help":
            default:
                System.out.println("");
                switch (args.length > 2 ? args[2] : "") {
                    case "zone":
                        System.out.println("zone list -- show all zones");
                        System.out.println("zone get <<zone name>> -- show zone json");
                        System.out.println("zone provision <<zone name>> -- provision a new zone");
                        System.out.println("zone update <<zone name>> <<zone json file>> -- update a zone");
                        break;
                    case "instance":
                        System.out.println("instance list <<zone name>> -- show all instances for a zone");
                        System.out.println("instance get <<zone name>> <<instance name>> -- show instance json");
                        System.out.println("instance provision <<zone name>> <<instance name>> -- provision a new instance");
                        System.out.println("instance update <<zone name>> <<instance name>> <<instance json file>> -- update an instance");
                        System.out.println("instance clone <<zone name>> <<source instance name>> <<destination instance name>> -- clone an instance");
                        break;
                    default:
                        System.out.println("zone - zone administration commands");
                        System.out.println("instance - instance administration commands");
                        break;
                }
        }
    }
}
