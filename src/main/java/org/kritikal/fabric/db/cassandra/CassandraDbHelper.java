package org.kritikal.fabric.db.cassandra;

import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * Created by ben on 19/06/15.
 */
public class CassandraDbHelper {

    public static String quote(int input) {
        return Integer.toString(input);
    }

    public static String quote(long input) {
        return Long.toString(input);
    }

    public static String quote(String input) {
        StringBuilder output;
        output = new StringBuilder();
        output.append("'");
        for (char ch : input.toCharArray()) {
            if (ch == '\'') {
                output.append("''");
            }
            else
                output.append(ch);
        }
        output.append("'");
        return output.toString();
    }

    public static void quoteTo(byte[] protobuf, StringBuilder sb) {
        if (protobuf == null) {
            sb.append("NULL");
        } else {
            sb.append("0x");
            // http://stackoverflow.com/questions/15429257/how-to-convert-byte-array-to-hexstring-in-java
            for (int i = 0; i < protobuf.length; i++) {
                int halfbyte = (protobuf[i] >>> 4) & 0x0F;
                int two_halfs = 0;
                do {
                    if ((0 <= halfbyte) && (halfbyte <= 9))
                        sb.append((char) ('0' + halfbyte));
                    else
                        sb.append((char) ('a' + (halfbyte - 10)));
                    halfbyte = protobuf[i] & 0x0F;
                } while(two_halfs++ < 1);
            }
        }
    }

    final static String iso8601cassandra = "yyyy-MM-dd'T'HH:mm:ssZ";
    final public static String toIso8601_Cassandra(java.util.Date date) {
        SimpleDateFormat sdfIso8601_Cassandra = new SimpleDateFormat(iso8601cassandra);
        return sdfIso8601_Cassandra.format(date);
    }
    final public static java.util.Date fromIso8601_Cassandra(String string) throws ParseException {
        SimpleDateFormat sdfIso8601_Cassandra = new SimpleDateFormat(iso8601cassandra);
        return sdfIso8601_Cassandra.parse(string);
    }

    public static String quote_timestamp(java.util.Date dt) {
        return "'" + toIso8601_Cassandra(dt) + "'";
    }
}
