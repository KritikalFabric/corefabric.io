/**
Copyright (c) 2010, Cisco Systems, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
    this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

    * Neither the name of the Cisco Systems, Inc. nor the names of its
    contributors may be used to endorse or promote products derived from this
    software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.cisco.qte.jdtn.tcpcl;

import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.JDtnException;

/**
 * TCP Convergence Layer Active Session Segment; class of Segments sent once
 * the TCPCL Connection established and Contact Header sent
 */
public abstract class TcpClActiveSessionSegment extends TcpClSegment {
	/** Data Segment */
	public static final int DATA_SEGMENT_TYPE = 1;
	/** Ack Segment */
	public static final int ACK_SEGMENT_TYPE = 2;
	/** Refuse (Nack) Segment */
	public static final int REFUSE_SEGMENT_TYPE = 3;
	/** Keepalive Segment */
	public static final int KEEPALIVE_SEGMENT_TYPE = 4;
	/** Shutdown segment */
	public static final int SHUTDOWN_SEGMENT_TYPE = 5;
	
	public static final int TYPE_MASK = 0xf0;
	public static final int TYPE_SHFT = 4;
	public static final int FLAGS_MASK = 0x0f;
	
	/** Type of Active Session Segment; one of XXX_ACTIVE_SEGMENT_TYPE */
	private int _type;
	/** Flags dependent on Active Segment Type */
	private int _flags;
	
	public TcpClActiveSessionSegment(int type) {
		super(TcpClSegment.SegmentType.SEG_TYPE_ACTIVE_SESSION);
		setType(type);
	}

	/**
	 * Encode this for transmission on the wire
	 * @param encoded Where to encode to
	 */
	@Override
	public void encode(EncodeState encoded) throws JDtnException {
		int byte0 = (getType() << TYPE_SHFT) | (getFlags() & FLAGS_MASK);
		encoded.put(byte0);
	}
	
	/**
	 * Create and decode a new TcpClActiveSessionSegment from given decode state.
	 * @param byte0 First byte received
	 * @param decodeState Tracks bytes in stream to decode
	 * @return New TcpCl segment
	 * @throws JDtnException on decode errors
	 */
	public static TcpClActiveSessionSegment decodeSegment(
			byte byte0, 
			DecodeState decodeState) 
	throws JDtnException {
		TcpClActiveSessionSegment segment = null;
		int type = (byte0 & TYPE_MASK) >> TYPE_SHFT;
		switch (type) {
		case ACK_SEGMENT_TYPE:
			segment = new TcpClBundleAckSegment();
			break;
			
		case DATA_SEGMENT_TYPE:
			segment = new TcpClDataSegment();
			break;
			
		case KEEPALIVE_SEGMENT_TYPE:
			segment = new TcpClKeepAliveSegment();
			break;
			
		case REFUSE_SEGMENT_TYPE:
			segment = new TcpClBundleNackSegment();
			break;
			
		case SHUTDOWN_SEGMENT_TYPE:
			segment = new TcpClShutdownSegment();
			break;
			
		default:
			throw new JDtnException("Invalid type/flags byte: " +
					String.format("0x%02x", byte0));
		}
		segment.decode(byte0, decodeState);
		return segment;
	}
	
	/**
	 * Decode information from the wire about this Segment.  We have already
	 * read in the first byte of the Segment.
	 * @param byte0 First byte of segment
	 * @param decodeState Object governing decode of the remainder of the Segment
	 * @throws JDtnException on errors
	 */
	@Override
	public void decode(byte byte0, DecodeState decodeState)
	throws JDtnException {
		setType((byte0 & TYPE_MASK) >> TYPE_SHFT);
		setFlags(byte0 & FLAGS_MASK);
	}
	
	/**
	 * Dump this object
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder("TcpClActiveSessionSegment\n");
		sb.append(super.dump(indent + "  ", detailed));
		sb.append(indent + "  Subtype=" + typeToString(getType()) + "\n");
		sb.append(indent + String.format("  Flags=0x%02x\n", getFlags()));
		return sb.toString();
	}
	
	private String typeToString(int type) {
		switch (type) {
		case DATA_SEGMENT_TYPE:
			return "DataSegment";
			
		case ACK_SEGMENT_TYPE:
			return "Ack";
			
		case REFUSE_SEGMENT_TYPE:
			return "Nack";

		case KEEPALIVE_SEGMENT_TYPE:
			return "KeepAlive";
			
		case SHUTDOWN_SEGMENT_TYPE:
			return "Shutdown";
			
		default:
			return "Unknown (" + type + ")";
		}
	}
	
	/** Type of Active Session Segment; one of XXX_ACTIVE_SEGMENT_TYPE */
	public int getType() {
		return _type;
	}

	/** Type of Active Session Segment; one of XXX_ACTIVE_SEGMENT_TYPE */
	public void setType(int type) {
		this._type = type;
	}

	/** Flags dependent on Active Segment Type */
	public int getFlags() {
		return _flags;
	}

	/** Flags dependent on Active Segment Type */
	public void setFlags(int flags) {
		this._flags = flags;
	}

}
