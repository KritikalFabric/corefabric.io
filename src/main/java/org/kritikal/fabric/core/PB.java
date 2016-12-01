package org.kritikal.fabric.core;

import java.util.UUID;
import org.kritikal.fabric.protobufs.DW;

/**
 * Created by ben on 21/06/15.
 */
public class PB {
    // account for microsoft's jumbled guid packing into a byte array
    public static UUID fromPackedGuid(DW.PackedGuid guid)
    {
        return new UUID(guid.getMsb(), guid.getLsb());
    }

    public static DW.PackedGuid toPackedGuid(UUID uuid)
    {
        if (uuid == null) return null;

        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        DW.PackedGuid.Builder builder = DW.PackedGuid.newBuilder();
        builder.setMsb(msb);
        builder.setLsb(lsb);
        return builder.build();
    }

    public static DW.O toDwo(Long l) {
        DW.O.Builder builder = DW.O.newBuilder();
        builder.setDataType(DW.O.DataTypes.CLASS_LONG);
        builder.setLong(l);
        return builder.build();
    }

    public static DW.O toDwo(String s) {
        DW.O.Builder builder = DW.O.newBuilder();
        builder.setDataType(DW.O.DataTypes.CLASS_STR);
        builder.setStr(s);
        return builder.build();
    }

    public static DW.O toDwo(UUID uuid) {
        DW.O.Builder builder = DW.O.newBuilder();
        builder.setDataType(DW.O.DataTypes.CLASS_UUID);
        builder.setUuid(toPackedGuid(uuid));
        return builder.build();
    }

    public static DW.O toDwo(DW.Set set) {
        DW.O.Builder builder = DW.O.newBuilder();
        builder.setDataType(DW.O.DataTypes.CLASS_SET);
        builder.setSet(set);
        return builder.build();
    }

    public static DW.O toDwo(DW.Map map) {
        DW.O.Builder builder = DW.O.newBuilder();
        builder.setDataType(DW.O.DataTypes.CLASS_MAP);
        builder.setMap(map);
        return builder.build();
    }

    public static DW.O toDwo(DW.O tuple1, DW.O tuple2) {
        DW.O.Builder builder = DW.O.newBuilder();
        builder.setDataType(DW.O.DataTypes.CLASS_TUPLE);
        builder.addTupleItem(tuple1);
        builder.addTupleItem(tuple2);
        return builder.build();
    }

    public static DW.O toDwo(DW.O tuple1, DW.O tuple2, DW.O tuple3) {
        DW.O.Builder builder = DW.O.newBuilder();
        builder.setDataType(DW.O.DataTypes.CLASS_TUPLE);
        builder.addTupleItem(tuple1);
        builder.addTupleItem(tuple2);
        builder.addTupleItem(tuple3);
        return builder.build();
    }
}
