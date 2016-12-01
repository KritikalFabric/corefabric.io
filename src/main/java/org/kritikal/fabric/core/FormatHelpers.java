package org.kritikal.fabric.core;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.UUID;

/**
 * Created by ben on 9/11/14.
 */
public class FormatHelpers {
    // account for microsoft's jumbled guid packing into a byte array
    final public static UUID fromPackedGuid(org.kritikal.fabric.protobufs.DW.PackedGuid guid)
    {
        return new UUID(guid.getMsb(), guid.getLsb());
    }

    final public static org.kritikal.fabric.protobufs.DW.PackedGuid toPackedGuid(UUID uuid)
    {
        if (uuid == null) return null;

        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        org.kritikal.fabric.protobufs.DW.PackedGuid.Builder builder = org.kritikal.fabric.protobufs.DW.PackedGuid.newBuilder();
        builder.setMsb(msb);
        builder.setLsb(lsb);
        return builder.build();
    }

    final public static UUID fromString(String stringOrNull)
    {
        if (null == stringOrNull || "".equals(stringOrNull))
            return null;
        return UUID.fromString(stringOrNull);
    }

    final public static java.sql.Timestamp toTimestamp(long unixTimestamp)
    {
        return new Timestamp(unixTimestamp * 1000l);
    }

    final public static long toTimestamp(java.sql.Timestamp timestamp)
    {
        return timestamp.getTime() / 1000l;
    }

    final public static long toTimestamp(java.util.Date date)
    {
        return date.getTime() / 1000l;
    }

    final static String isoYMD = "yyyy-MM-dd";
    final public static String toIsoYMD(java.util.Date date)
    {
        SimpleDateFormat sdfIsoYMD = new SimpleDateFormat(isoYMD);
        return sdfIsoYMD.format(date);
    }
    final public static java.util.Date fromIsoYMD(String string) throws ParseException {
        SimpleDateFormat sdfIsoYMD = new SimpleDateFormat(isoYMD);
        return sdfIsoYMD.parse(string);
    }

    final static String iso8601 = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    final public static String toIso8601(java.util.Date date) {
        SimpleDateFormat sdfIso8601 = new SimpleDateFormat(iso8601);
        return sdfIso8601.format(date);
    }
    final public static java.util.Date fromIso8601(String string) throws ParseException {
        SimpleDateFormat sdfIso8601 = new SimpleDateFormat(iso8601);
        return sdfIso8601.parse(string);
    }

    /**
     * for compatibility with .net's Guid.Empty value
     */
    public final UUID UUID_Empty = UUID.fromString("00000000-0000-0000-0000-000000000000");

    final public static java.util.Date datePart(java.util.Date now) {
        GregorianCalendar calNow = new GregorianCalendar();
        calNow.setTime(now);
        calNow.set(Calendar.HOUR_OF_DAY, 0);
        calNow.set(Calendar.MINUTE, 0);
        calNow.set(Calendar.SECOND, 0);
        calNow.set(Calendar.MILLISECOND, 0);
        return calNow.getTime();
    }

    final public static java.util.Date addDays(java.util.Date now, int nDays) {
        GregorianCalendar calNow = new GregorianCalendar();
        calNow.setTime(now);
        calNow.add(GregorianCalendar.DAY_OF_MONTH, nDays);
        return calNow.getTime();
    }
}
