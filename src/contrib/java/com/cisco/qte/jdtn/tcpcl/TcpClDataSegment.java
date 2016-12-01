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
import com.cisco.qte.jdtn.general.Utils;

/**
 * TCP Convergence Layer Data Segment; Segment containing Bundle data
 */
public class TcpClDataSegment extends TcpClActiveSessionSegment {

	public static final int START_FLAG = 0x02;
	public static final int END_FLAG = 0x01;
	
	/** Length of data in this Segment */
	private int _length;
	/** Data buffer */
	private byte[] _data;
	
	/**
	 * Constructor
	 */
	public TcpClDataSegment() {
		super(TcpClActiveSessionSegment.DATA_SEGMENT_TYPE);
	}

	/**
	 * Encode this for transmission on the wire
	 * @param encoded Where to encode to
	 */
	@Override
	public void encode(EncodeState encoded) throws JDtnException {
		super.encode(encoded);
		Utils.sdnvEncodeInt(getLength(), encoded);
		byte[] data = getData();
		encoded.append(data, 0, data.length);
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
		super.decode(byte0, decodeState);
		setLength(Utils.sdnvDecodeInt(decodeState));
		setData(decodeState.getBytes(getLength()));
	}
	
	/**
	 * Dump this object
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "TcpClDataSegment\n");
		sb.append(super.dump(indent + "  ", detailed));
		sb.append(indent + "  isStart=" + isStart() + "\n");
		sb.append(indent + "  isEnd=" + isEnd() + "\n");
		sb.append(indent + "  Length=" + getLength() + "\n");
		if (detailed) {
			sb.append(Utils.dumpBytes(indent + "  ", getData(), 0, getLength()));
		}
		return sb.toString();
	}
	
	/** Data Segment is start of bundle */
	public boolean isStart() {
		return (getFlags() & START_FLAG) != 0;
	}
	
	/** Data Segment is start of bundle */
	public void setStart(boolean start) {
		if (start) {
			setFlags(getFlags() | START_FLAG);
		} else {
			setFlags(getFlags() & ~START_FLAG);
		}
	}
	
	/** Data Segment is end of bundle */
	public boolean isEnd() {
		return (getFlags() & END_FLAG) != 0;
	}
	
	/** Data Segment is end of bundle */
	public void setEnd(boolean end) {
		if (end) {
			setFlags(getFlags() | END_FLAG);
		} else {
			setFlags(getFlags() & ~END_FLAG);
		}
	}

	/** Length of data */
	public int getLength() {
		return _length;
	}

	/** Length of data */
	public void setLength(int length) {
		this._length = length;
	}

	/** Data buffer */
	public byte[] getData() {
		return _data;
	}

	/** Data buffer */
	public void setData(byte[] data) {
		this._data = data;
	}
	
}
