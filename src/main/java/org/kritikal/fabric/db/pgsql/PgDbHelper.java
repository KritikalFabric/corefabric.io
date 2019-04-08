package org.kritikal.fabric.db.pgsql;

import com.google.protobuf.ByteString;
import io.vertx.core.json.JsonArray;
import org.kritikal.fabric.core.FormatHelpers;

import java.sql.*;
import java.util.GregorianCalendar;
import java.util.UUID;

import static org.kritikal.fabric.core.FormatHelpers.fromPackedGuid;
import static org.kritikal.fabric.core.FormatHelpers.toTimestamp;

/**
 * Created by ben on 06/01/15.
 */
public class PgDbHelper {

    public static String quote(String toQuote)
    {
        if (toQuote == null) return "NULL";
        StringBuilder sb = new StringBuilder();
        sb.append('\'');
        for (char c : toQuote.toCharArray()) {
            if (c == '\'')
                sb.append("''");
            else
                sb.append(c);
        }
        sb.append('\'');
        return sb.toString();
    }

    public static String quote_arrayliteral(String toQuote)
    {
        if (toQuote == null) return "NULL";
        StringBuilder sb = new StringBuilder();
        sb.append('\"');
        for (char c : toQuote.toCharArray()) {
            if (c == '\"')
                sb.append("\\\"");
            else if (c == ',')
                sb.append("\\,");
            else if (c == '\\')
                sb.append("\\\\");
            else
                sb.append(c);
        }
        sb.append('\"');
        return sb.toString();
    }

    public static String quote_for_like(String toQuote) {
        if (toQuote == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append('\'');
        for (char c : toQuote.toCharArray()) {
            if (c == '\'')
                sb.append("''");
            else if (c == '%')
                sb.append("%%");
            else
                sb.append(c);
        }
        sb.append('\'');
        return sb.toString();
    }

    public static String quote(int toQuote)
    {
        return (new Integer(toQuote)).toString();
    }

    public static String quote(long toQuote)
    {
        return (new Long(toQuote)).toString();
    }

    public static String quote(boolean toQuote)
    {
        return toQuote ? "TRUE" : "FALSE";
    }

    public static String quote(org.kritikal.fabric.protobufs.DW.PackedGuid packedGuid)
    {
        if (packedGuid == null) return "NULL";
        return quote(fromPackedGuid(packedGuid).toString());
    }

    public static String quote(UUID guid)
    {
        if (guid == null) return "NULL";
        return quote(guid.toString());
    }

    public static String quote_date(GregorianCalendar toQuote) {
        if (toQuote == null) return "NULL";
        return quote(FormatHelpers.toIsoYMD(toQuote.getTime()));
    }

    public static String quote_date(java.util.Date toQuote) {
        if (toQuote == null) return "NULL";
        return quote(FormatHelpers.toIsoYMD(toQuote));
    }
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String quote(byte[] data)
    {
        if (data == null) return "NULL";
        char[] hexChars = new char[data.length * 2 + 4];
        hexChars[0] = '\'';
        hexChars[1] = '\\';
        hexChars[2] = 'x';
        hexChars[hexChars.length - 1] = '\'';
        for (int j = 0, k = 3; j < data.length; ++j) {
            int v = data[j] & 0xFF;
            hexChars[k++] = hexArray[v >>> 4];
            hexChars[k++] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
    public static String quote(ByteString data) {
        return quote(data.toByteArray());
    }
    public static String hex(byte[] data)
    {
        if (data == null) return "NULL";
        char[] hexChars = new char[data.length * 2 + 2];
        hexChars[0] = '\\';
        hexChars[1] = 'x';
        for (int j = 0, k = 2; j < data.length; ++j) {
            int v = data[j] & 0xFF;
            hexChars[k++] = hexArray[v >>> 4];
            hexChars[k++] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static void quoteTo(byte[] protobuf, StringBuilder sb) {
        if (protobuf == null) {
            sb.append("NULL");
        } else {
            sb.append("E'\\\\x");
            for (int j = 0; j < protobuf.length; ++j) {
                int v = protobuf[j] & 0xFF;
                sb.append(hexArray[v >>> 4]);
                sb.append(hexArray[v & 0x0F]);
            }
            sb.append("'");
        }
    }

    public static String quote_timestamp(java.sql.Timestamp ts)
    {
        return quote(ts.toString());
    }

    public static String quote_timestamp(java.util.Date dt) {
        return quote_timestamp(new Timestamp(dt.getTime()));
    }

    public static String quote_timestamp(long secondsSinceEpoch)
    {
        return quote(toTimestamp(secondsSinceEpoch).toString());
    }

    public static String varcharTrim(String string, int length) {
        if (null == string) return null;
        if (string.length() < length) return string;
        return string.substring(0, length-1);
    }

    public static Integer getInteger(java.sql.ResultSet rs, int n) throws SQLException {
        Integer result = rs.getInt(n);
        if (rs.wasNull()) return null;
        return result;
    }

    public static Long getLong(java.sql.ResultSet rs, int n) throws SQLException {
        Long result = rs.getLong(n);
        if (rs.wasNull()) return null;
        return result;
    }

    public static JsonArray jsonQuery(Connection con, String sql) throws SQLException
    {
        JsonArray ary = new JsonArray();
        // "SET SESSION CHARACTERISTICS AS TRANSACTION ISOLATION LEVEL READ UNCOMMITTED READ ONLY DEFERRABLE;"
        PreparedStatement stmt = con.prepareStatement(sql);
        try {
            if (stmt.execute()) {
                ResultSet rs = stmt.getResultSet();
                try {
                    int l = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        JsonArray row = new JsonArray();
                        for (int i = 1; i <= l; ++i) {
                            Object o = rs.getObject(i);
                            if (o == null) {
                                row.add(""); // empty string instead of null
                            } else {
                                if (o instanceof UUID)
                                    row.add(((UUID) o).toString());
                                else if (o instanceof Timestamp)
                                    row.add(((Timestamp) o).toString());
                                else
                                    row.add(o.toString());
                            }
                        }
                        ary.add(row);
                    }
                } finally {
                    rs.close();
                }
            }
        }
        finally {
            stmt.close();
        }
        return ary;
    }

}
