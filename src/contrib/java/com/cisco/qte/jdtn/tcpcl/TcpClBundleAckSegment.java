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
 * TCP Convergence Layer Bundle Ack Segment
 */
public class TcpClBundleAckSegment extends TcpClActiveSessionSegment {

	/** Acknowledged Length */
	private long _ackLength = 0;
	
	/**
	 * Constructor
	 */
	public TcpClBundleAckSegment() {
		super(TcpClActiveSessionSegment.ACK_SEGMENT_TYPE);
		setFlags(0);
	}

	/**
	 * Encode this for transmission on the wire
	 * @param encoded Where to encode to
	 */
	@Override
	public void encode(EncodeState encoded) throws JDtnException {
		super.encode(encoded);
		Utils.sdnvEncodeLong(getAckLength(), encoded);
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
		setAckLength(Utils.sdnvDecodeInt(decodeState));
	}
	
	/**
	 * Dump this object
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "TcpClBundleAckSegment\n");
		sb.append(super.dump(indent + "  ", detailed));
		sb.append(indent + "  ackLength=" + getAckLength() + "\n");
		return sb.toString();
	}
	
	/** Acknowledged Length */
	public long getAckLength() {
		return _ackLength;
	}

	/** Acknowledged Length */
	public void setAckLength(long ackLength) {
		this._ackLength = ackLength;
	}

}
