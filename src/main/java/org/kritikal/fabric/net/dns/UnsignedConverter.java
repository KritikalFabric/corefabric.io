package org.kritikal.fabric.net.dns;

/**
 * Created by ben on 16/03/15.
 */
public class UnsignedConverter {

    public static int uint8(byte[] data, int offset)
    {
        int i = data[offset];
        if (i < 0) i += 256;
        return i;
    }

    public static int uint16(byte[] data, int offset)
    {
        int i = (uint8(data, offset) * 256) + uint8(data, offset + 1);
        return i;
    }

    public static byte[] uint16(int val)
    {
        byte[] data = new byte[2];
        data[0] = ((byte)(val/256));
        data[1] = ((byte)(val%256));
        return data;
    }

    public static long uint32(byte[] data, int offset)
    {
        long l = uint16(data, offset);
        l *= 65536;
        l += (long) uint16(data, offset + 2);
        return l;
    }

    public static byte[] uint32(long val)
    {
        byte[] data = new byte[4];
        data[0] = ((byte)((0xFF000000l&val)/(256*256*256)));
        data[1] = ((byte)((0x00FF0000l&val)/(256*256)));
        data[2] = ((byte)((0x0000FF00l&val)/(256)));
        data[3] = ((byte)((0x000000FFl&val)%256));
        return data;
    }

    public static int parseDomainNameFrom(StringBuilder sb, byte[] data, int i) {
        if (data[i] == 0) {
            return ++i;
        }
        int l = UnsignedConverter.uint8(data, i++);
        while (l > 0) {
            if ((l & 0b11000000) != 0) {
                l = l & 0b00111111;
                l *= 256;
                l += uint8(data, i++);
                UnsignedConverter.parseDomainNameFrom(sb, data, l); // ignore return
                l = 0;
            } else {
                while (l-- > 0) {
                    sb.append((char)data[i++]);
                }
                if (data[i] == 0) {
                    return ++i;
                }
                sb.append(".");
                l = UnsignedConverter.uint8(data, i++);
            }
        }
        return i;
    }

}
