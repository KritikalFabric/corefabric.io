package org.kritikal.fabric.net.dns;

import io.vertx.core.buffer.Buffer;

import java.util.ArrayList;

/**
 * Created by ben on 16/03/15.
 */
public class DnsMessage {

        /*

        The header contains the following fields:

                                            1  1  1  1  1  1
              0  1  2  3  4  5  6  7  8  9  0  1  2  3  4  5
            +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
            |                      ID                       |
            +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
            |QR|   Opcode  |AA|TC|RD|RA|   Z    |   RCODE   |
            +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
            |                    QDCOUNT                    |
            +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
            |                    ANCOUNT                    |
            +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
            |                    NSCOUNT                    |
            +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
            |                    ARCOUNT                    |
            +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+

     */

    public int ID;
    public boolean QR;
    public int Opcode;
    public boolean AA;
    public boolean TC;
    public boolean RD;
    public boolean RA;
    public int Z;
    public int RCODE;
    public int QDCOUNT;
    public int ANCOUNT;
    public int NSCOUNT;
    public int ARCOUNT;

    public ArrayList<DnsQuestionEntry> questions = null;
    public ArrayList<DnsResourceRecordEntry> answers = null;
    public ArrayList<DnsResourceRecordEntry> nameservers = null;
    public ArrayList<DnsResourceRecordEntry> additional = null;

    public DnsMessage(DnsMessage other) {
        this.ID = other.ID;
        this.QR = !other.QR;
        this.Opcode = other.Opcode;
    }

    public DnsMessage(byte[] data) {

        if (data == null) throw new DnsFormatError();

        if (data.length < 12) throw new DnsFormatError();

        ID = UnsignedConverter.uint16(data, 0);

        QR = (UnsignedConverter.uint8(data, 2) & 0b00000001) != 0;
        Opcode = (UnsignedConverter.uint8(data, 2) & 0b00011110) / 2;
        AA = (UnsignedConverter.uint8(data, 2) & 0b00100000) != 0;
        TC = (UnsignedConverter.uint8(data, 2) & 0b01000000) != 0;
        RD = (UnsignedConverter.uint8(data, 2) & 0b10000000) != 0;

        RA = (UnsignedConverter.uint8(data, 3) & 0b00000001) != 0;
        Z = (UnsignedConverter.uint8(data, 3) & 0b00001110) / 2;
        RCODE = (UnsignedConverter.uint8(data, 3) & 0b11110000) / 16;

        QDCOUNT = UnsignedConverter.uint16(data, 4);
        ANCOUNT = UnsignedConverter.uint16(data, 6);
        NSCOUNT = UnsignedConverter.uint16(data, 8);
        ARCOUNT = UnsignedConverter.uint16(data, 10);

        int i = 12;
        if (QDCOUNT > 0) {
            questions = new ArrayList<>();
            for (int j = 0; j < QDCOUNT; ++j)
            {
                DnsQuestionEntry q = new DnsQuestionEntry();
                i = q.populateFrom(data, i);
                questions.add(q);
            }
        }

        if (ANCOUNT > 0) {
            answers = new ArrayList<>();
            for (int j = 0; j < ANCOUNT; ++j)
            {
                DnsResourceRecordEntry rr = new DnsResourceRecordEntry();
                i = rr.populateFrom(data, i);
                answers.add(rr);
            }
        }

        if (NSCOUNT > 0) {
            nameservers = new ArrayList<>();
            for (int j = 0; j < NSCOUNT; ++j)
            {
                DnsResourceRecordEntry rr = new DnsResourceRecordEntry();
                i = rr.populateFrom(data, i);
                nameservers.add(rr);
            }
        }

        if (ARCOUNT > 0) {
            additional = new ArrayList<>();
            for (int j = 0; j < ARCOUNT; ++j)
            {
                DnsResourceRecordEntry rr = new DnsResourceRecordEntry();
                i = rr.populateFrom(data, i);
                additional.add(rr);
            }
        }
    }

    public Buffer createBuffer(boolean udp) {
        Buffer buffer = Buffer.buffer(1500);

        DnsResponseCompressionState compression = new DnsResponseCompressionState();

        buffer.appendBytes(UnsignedConverter.uint16(questions == null ? 0 : questions.size()));
        buffer.appendBytes(UnsignedConverter.uint16(answers == null ? 0 : answers.size()));
        buffer.appendBytes(UnsignedConverter.uint16(nameservers == null ? 0 : nameservers.size()));
        buffer.appendBytes(UnsignedConverter.uint16(additional == null ? 0 : additional.size()));

        if (questions != null) {
            for (DnsQuestionEntry question : questions)
                buffer.appendBuffer(question.getBuffer());
        }

        int i = buffer.length();

        if (answers != null) {
            for (DnsResourceRecordEntry rr : answers) {
                Buffer rrbuf = rr.getBuffer(compression, i);
                i += rrbuf.length();
                buffer.appendBuffer(rrbuf);
            }
        }

        if (nameservers != null) {
            for (DnsResourceRecordEntry rr : nameservers) {
                Buffer rrbuf = rr.getBuffer(compression, i);
                i += rrbuf.length();
                buffer.appendBuffer(rrbuf);
            }
        }

        if (additional != null) {
            for (DnsResourceRecordEntry rr : additional) {
                Buffer rrbuf = rr.getBuffer(compression, i);
                i += rrbuf.length();
                buffer.appendBuffer(rrbuf);
            }
        }

        if (udp && buffer.length() > 508) {
            TC = true; // TODO: check this is truncated flag
        }


        Buffer headerBuffer = Buffer.buffer(1500);
        headerBuffer.appendBytes(UnsignedConverter.uint16(ID));

        int header = 0;
        header ^= QR ? 0b1000000000000000 : 0;
        int b = (Opcode & 0b1111) << 11;
        header ^= b;
        header ^= AA ? 0b0000010000000000 : 0;
        header ^= TC ? 0b0000001000000000 : 0;
        header ^= RD ? 0b0000000100000000 : 0;

        header ^= RA ? 0b0000000010000000 : 0;
        b = (Z&0b111) << 4;
        header ^= b;
        b = (RCODE&0b1111);
        header ^= b;

        /*

        int header = 0;
        header ^= QR ? 0b0000000000000001 : 0;
        int b = (Opcode & 0b1111) << 1;
        header ^= b;
        header ^= AA ? 0b0000000000100000 : 0;
        header ^= TC ? 0b0000000001000000 : 0;
        header ^= RD ? 0b0000000010000000 : 0;

        header ^= RA ? 0b0000000100000000 : 0;
        b = (Z & 0b111) << 9;
        header ^= b;
        b = (RCODE & 0b1111) << 12;
        header ^= b;

        */

        headerBuffer.appendBytes(UnsignedConverter.uint16(header));

        if (udp && buffer.length() > 508) {
            headerBuffer.appendBuffer(buffer, 0, 508);
        }
        else {
            headerBuffer.appendBuffer(buffer);
        }
        return headerBuffer;
    }

}
