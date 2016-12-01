package org.kritikal.fabric.db.pgsql;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.kritikal.fabric.core.ConfigurationManager;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbcp2.DelegatingConnection;
import org.kritikal.fabric.CoreFabric;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.postgresql.copy.PGCopyOutputStream;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Created by ben on 9/4/14.
 */
public abstract class CopyInManager {

    private static final boolean SLOWER = CoreFabric.ServerConfiguration.SLOWER;

    private static ConcurrentHashMap<String, BasicDataSource> poolOfPools = new ConcurrentHashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(CopyInManager.class);

    private String tableName;
    private String columns;
    public Connection con;
    private CopyIn stream;
    private String connectionString;
    private String username;
    private String password;
    private Vertx vertx;
    private long internalTimerId;
    private long total = 0;
    private long dt = 0;

    private final long CHUNK = 65536l;

    public CopyInManager(Vertx vertx, String tableName, String columns, String connectionString, String username, String password, boolean throughput)
    {
        if (!Constants.LOADED) { throw new Error("Unable to load driver"); }

        this.vertx = vertx;
        this.internalTimerId = vertx.setPeriodic((throughput ? 60 : 1) * 997L, new Handler<Long>() {
            @Override
            public void handle(Long event) {
                vertx.executeBlocking(f -> {
                    endCopy(v0id -> { pulseBlocking(); });
                    f.complete();
                }, false, r-> {});
            }
        });

        this.tableName = tableName;
        this.columns = columns;
        this.connectionString = connectionString;
        this.username = username;
        this.password = password;
    }

    public void dispose() {
        endCopy(null);
        vertx.cancelTimer(this.internalTimerId);
        vertx.cancelTimer(this.externalTimerId1);
        vertx.cancelTimer(this.externalTimerId2);
    }

    private volatile boolean exit = false;
    public static class State {
        public Connection con;
        public CopyIn stream;
        public Consumer<Void> next;
        public void cleanup() {
            if (stream != null) try { stream.endCopy(); } catch (Throwable t) { logger.warn("Ending copy", t); }
            if (con != null) try { con.close(); } catch (Throwable t) { logger.warn("Closing connection", t); }
            if (next != null) next.accept(null);
        }
    }

    public abstract void pulseBlocking();

    private void connectIfNeeded() throws Exception {
        if (con != null) return;
        con = poolOfPools.computeIfAbsent(connectionString, (k) -> {
            return BasicDataSourceHelper.pool(ConfigurationManager.BULKCOPY_CONCURRENCY, basicDataSource -> {
                basicDataSource.setUrl(connectionString);
                basicDataSource.setUsername(username);
                basicDataSource.setPassword(password);
                basicDataSource.setAccessToUnderlyingConnectionAllowed(true);
            });
        }).getConnection();
    }

    private static void writeToCopy(Buffer buffer, byte[] bytes)
    {
        buffer.appendBytes(bytes, 0, bytes.length);
    }

    public void endCopy(Consumer<Void> next)
    {
        final State state = new State();
        synchronized (this)
        {
            state.stream = stream;
            state.con = con;
            state.next = next;
            stream = null;
            con = null;
        }
        vertx.executeBlocking(f -> {
            try {
                state.cleanup();
            } finally {
                f.complete();
            }
        }, false, r -> { });
    }

    private /* synchronized */ void openStream()
    {
        try {
            connectIfNeeded();
        } catch (Exception e) {
            logger.debug("During connect if needed", e);
        }

        Throwable t = null;
        if (stream == null && con != null) {
            PGConnection pgConnection = (PGConnection) ((DelegatingConnection) con).getInnermostDelegate();
            try {
                stream = new PGCopyOutputStream(pgConnection, "COPY " + tableName + " (" + columns + ") FROM STDIN", 16 * 1024 * 1024);
            } catch (Throwable t1) {
                try {
                    endCopy(null);
                } catch (Throwable t2) {

                }
                stream = null;
                try { con.close(); } catch (Throwable t3) { }
                con = null;
                logger.fatal("", t1);
            }
        }
    }

    public long nRows = 0;
    public long externalTimerId1 = 0, externalTimerId2 = 0;

    private /* synchronized */ void copy(Buffer buffer, long n)
    {
        boolean pulse = false;

        synchronized (this) {

            openStream();

            if (stream != null) {
                try {
                    byte[] byteBuffer = buffer.getBytes();
                    stream.writeToCopy(byteBuffer, 0, byteBuffer.length);
                    nRows += n;
                } catch (Throwable t1) {
                    try {
                        endCopy(null);
                    } catch (Throwable t2) {
                        // ignore
                    }
                    {
                        logger.fatal("", t1);
                    }
                }
            }

            total += n;

            if (stream != null && total >= CHUNK) {
                pulse = true;
                total = 0;
            }

        }
        if (pulse) pulseBlocking();
    }

    public void copyInRows(List<List<String>> rows)
            throws IOException, SQLException
    {
        long n = 0;
        Buffer buffer = Buffer.buffer(128 * 1024);
        for (List<String> row : rows) {
            int i = 0;
            ++n;
            for (String s : row) {
                if (i++ > 0)
                    writeToCopy(buffer, new byte[]{(byte) '\t'});
                writeString(buffer, s);
            }
            writeToCopy(buffer, new byte[]{(byte) '\n'});
        }
        if (n > 0) {
            synchronized (this) {
                copy(buffer, n);
            }
            //pulseBlocking();
        }
    }

    public void copyInRow(List<String> row) {
        long n = 0;
        Buffer buffer = Buffer.buffer(128 * 1024);
        {
            int i = 0;
            ++n;
            for (String s : row) {
                if (i++ > 0)
                    writeToCopy(buffer, new byte[]{(byte) '\t'});
                writeString(buffer, s);
            }
            writeToCopy(buffer, new byte[]{(byte) '\n'});
        }
        if (n > 0) {
            synchronized (this) {
                copy(buffer, n);
            }
            //pulseBlocking();
        }
    }

    protected static void writeString(Buffer buffer, String s)
    {
        if (s == null)
        {
            writeToCopy(buffer, new byte[] { (byte)'\\', (byte)'N' });
            return;
        }

        char[] data = s.toCharArray();
        int offset = 0;

        for (int i = offset; i < data.length; ++i) {
            if (data[i] == '\t' ||
                data[i] == '\n' ||
                data[i] == '\\' ||
                data[i] == '\r' ||
                data[i] == '\"' ||
                data[i] == '\'') {
                int start = offset;
                if (i > offset) {
                    String substring = s.substring(start, i);
                    byte[] bytes = null;
                    try {
                        bytes = substring.getBytes("UTF-8");
                    }
                    catch (UnsupportedEncodingException uee) {
                        logger.fatal("Rare!", uee);
                    }
                    writeToCopy(buffer, bytes);
                }
                switch (data[i]) {
                    case '\t':
                        writeToCopy(buffer, new byte[]{(byte) '\\', (byte) 't'});
                        break;
                    case '\n':
                        writeToCopy(buffer, new byte[]{(byte) '\\', (byte) 'n'});
                        break;
                    case '\\':
                        writeToCopy(buffer, new byte[]{(byte) '\\', (byte) '\\'});
                        break;
                    case '\r':
                        writeToCopy(buffer, new byte[] { (byte)'\\', (byte)'r' });
                        break;
                    case '\"':
                        writeToCopy(buffer, new byte[] { (byte)'\\', (byte)'"' });
                        break;
                    case '\'':
                        writeToCopy(buffer, new byte[] { (byte)'\\', (byte)'\'' });
                        break;
                }
                offset = i + 1;
                continue;
            }
        }

        if (offset < data.length) {
            String substring = s.substring(offset, data.length);
            byte[] bytes = null;
            try {
                bytes = substring.getBytes("UTF-8");
            }
            catch (UnsupportedEncodingException uee) {
                logger.fatal("Rare!", uee);
            }
            writeToCopy(buffer, bytes);
        }
    }
}
