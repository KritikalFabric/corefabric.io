package org.kritikal.fabric.db.pgsql;

/**
 * Created by ben on 5/25/14.
 */
public class Constants {

    static {
        boolean loaded = false;
        try
        {
            Class.forName("org.postgresql.Driver");
            loaded = true;
        }
        catch (Exception e)
        {
            loaded = false;
        }
        LOADED = loaded;
    }

    public static final boolean LOADED;
    public static long INITIAL_BASIC_DATA_SOURCE_SLEEP = 331l;
    public static long MAX_CONNECTION_LIFETIME_MILLIS = 60*997l;
}
