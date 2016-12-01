package org.kritikal.fabric.net;

import io.netty.buffer.ByteBuf;
import io.vertx.core.buffer.Buffer;

import java.util.Arrays;
import java.util.LinkedList;

/**
 * Created by ben on 8/24/14.
 */
public class BufferContainer {

    public class NeedMoreDataException extends Exception { }

    private LinkedList<Buffer> linkedList;
    private long readPosition = 0;

    private boolean rollbackMarkSet = false;
    private long rollbackReadPosition = 0;

    public BufferContainer()
    {
        linkedList = new LinkedList<>();
    }

    public void append(Buffer buffer)
    {
        linkedList.add(buffer);
    }

    public void setRollbackMark()
        throws Exception
    {
        if (rollbackMarkSet)
            throw new Exception();

        rollbackReadPosition = readPosition;
        rollbackMarkSet = true;
    }

    public void rollbackToMark()
    {
        if (rollbackMarkSet)
        {
            readPosition = rollbackReadPosition;
            rollbackMarkSet = false;
        }
    }

    public void moveRollbackMark()
            throws Exception
    {
        if (!rollbackMarkSet)
            throw new Exception();

        LinkedList<Buffer> toDispose = new LinkedList<>();
        LinkedList<Buffer> toKeep = new LinkedList<>();

        for (Buffer buf : linkedList)
        {
            long readableBytes = buf.length();
            if (readPosition >= readableBytes)
            {
                readPosition -= readableBytes;
            }
            else
            {
                toKeep.add(buf);
            }
        }

        rollbackReadPosition = readPosition;
        linkedList = toKeep;
    }

    public boolean isEmpty()
    {
        if (linkedList.isEmpty())
            return true;

        long r = readPosition;
        for (Buffer buf : linkedList)
        {
            if (r < 0) break;
            r -= buf.length();
        }
        return r == 0;
    }

    public byte peekFirstByte()
            throws NeedMoreDataException
    {
        if (linkedList.isEmpty())
            throw new NeedMoreDataException();

        long r = readPosition;
        for (Buffer buf : linkedList)
        {
            if (r < buf.length())
                return buf.getByte((int)r);
            else
                r -= buf.length();
        }

        throw new NeedMoreDataException();
    }

    public byte readByte()
            throws NeedMoreDataException
    {
        long r = readPosition;
        for (Buffer buf : linkedList)
        {
            long readableBytes = buf.length();
            if (r >= readableBytes)
            {
                r -= readableBytes;
            }
            else
            {
                readPosition++;
                return buf.getByte((int)r++);
            }
        }
        throw new NeedMoreDataException();
    }

    public void assertBytes(long length)
            throws NeedMoreDataException, Exception
    {
        if (length < 0) throw new Exception();
        if (length == 0) return; // asserts OK
        // assert that there are enough bytes to be read
        long r = readPosition;
        r += length;
        for (Buffer buf : linkedList)
        {
            long readableBytes = buf.length();
            r -= readableBytes;
        }
        if (r > 0) throw new NeedMoreDataException();
    }

    public byte[] readBytes(long length)
        throws NeedMoreDataException, Exception
    {
        if (length == 0) return null;
        if (length>Integer.MAX_VALUE) throw new Exception("Buffer overflow");
        assertBytes(length);

        long r = readPosition;
        byte[] ret = new byte[(int)length];
        long i = 0, j = length;
        for (Buffer buf : linkedList)
        {
            int readableBytes = buf.length();
            if (r > readableBytes)
            {
                r -= readableBytes;
                continue;
            }

            long k = r+j;
            if (k > readableBytes) k = readableBytes;
            for (; r<k; --j, ++readPosition)
                ret[(int)i++] = buf.getByte((int)r++);

            if (j > 0) {
                r -= readableBytes;
                continue;
            }

            break;
        }
        return ret;
    }

    public long readRemainingLength()
        throws NeedMoreDataException
    {
        // suboptimal implementation follows
        int multiplier = 1;
        long value = 0;
        byte digit = 0;
        do {
            digit = readByte();
            value += (digit & 0x7f) * multiplier;
            multiplier *= 0x80;
        } while ((digit & 0x80) != 0);
        return value;
    }

    public int readShort()
        throws NeedMoreDataException
    {
        // suboptimal implementation follows
        int value = readByte() & 0xff;
        value *= 0x100;
        value |= readByte() & 0xff;
        return value;
    }

    public long readLong()
            throws NeedMoreDataException
    {
        // suboptimal implementation follows
        long value = readByte() & 0xff;
        value *= 0x100;
        value |= readByte() & 0xff;
        value *= 0x100;
        value |= readByte() & 0xff;
        value *= 0x100;
        value |= readByte() & 0xff;
        value *= 0x100;
        value |= readByte() & 0xff;
        value *= 0x100;
        value |= readByte() & 0xff;
        value *= 0x100;
        value |= readByte() & 0xff;
        value *= 0x100;
        value |= readByte() & 0xff;
        return value;
    }

    public void skipBytes(long length)
    {
        readPosition += length;
    }
}
