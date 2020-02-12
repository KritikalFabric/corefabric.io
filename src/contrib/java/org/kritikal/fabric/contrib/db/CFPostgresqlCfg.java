package org.kritikal.fabric.contrib.db;

import java.sql.Connection;

public final class CFPostgresqlCfg {
    public static int DEFAULT_TRANSACTION_ISOLATION = Connection.TRANSACTION_READ_UNCOMMITTED;
    public static String POOL_VALIDATION_QUERY = "SELECT 1;";
    public static boolean POOL_TEST_BORROW = true;
    public static boolean POOL_TEST_RETURN = false;
    public static boolean POOL_TEST_IDLE = false;
}
