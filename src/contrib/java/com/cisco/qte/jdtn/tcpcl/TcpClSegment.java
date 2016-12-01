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
 * TCP Convergence Layer Segment - data block received/sent
 */
public abstract class TcpClSegment {

	public enum SegmentType {
		/** Sent once after contact establishment */
		SEG_TYPE_CONTACT_HEADER,
		/** All other segments */
		SEG_TYPE_ACTIVE_SESSION
	}
	
	/** Segment Type */
	private SegmentType _segmentType;

	/**
	 * Constructor; sets Segment Type
	 * @param segmentType Segment Type
	 */
	public TcpClSegment(SegmentType segmentType) {
		setSegmentType(segmentType);
	}
	
	/**
	 * Encode this for transmission on the wire
	 * @param encoded Where to encode to
	 * @throws JDtnException on errors
	 */
	public abstract void encode(EncodeState encoded) throws JDtnException;
	
	/**
	 * Create and decode a new TcpCl segment from given decode state.  We have
	 * already read in first byte of segment
	 * @param byte0 First byte received
	 * @param decodeState Tracks bytes in stream to decode
	 * @return New TcpCl segment
	 * @throws JDtnException on decode errors
	 */
	public static TcpClSegment decodeSegment(
			byte byte0, 
			DecodeState decodeState, 
			boolean expectingContactHeader) 
	throws JDtnException {
		TcpClSegment segment = null;
		if (expectingContactHeader &&
			byte0 == TcpClContactHeaderSegment.HEADER[0]) {
			segment = new TcpClContactHeaderSegment();
			segment.decode(byte0, decodeState);
		} else {
			segment = TcpClActiveSessionSegment.decodeSegment(byte0, decodeState);
		}
		return segment;
	}
	
	/**
	 * Decode information from the wire about this Segment.  We have already
	 * read in the first byte of the Segment.
	 * @param byte0 First byte of the message
	 * @param decodeState The encoded state of the Segment we are decoding.
	 * @throws JDtnException on errors
	 */
	public abstract void decode(byte byte0, DecodeState decodeState)
	throws JDtnException;
	
	/**
	 * Dump this object
	 */
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "TcpClSegment\n");
		sb.append(indent + "  SegmentType " + segmentTypeToString(getSegmentType()) + "\n");
		return sb.toString();
	}
	
	/**
	 * Convert the SegmentType enum to a String
	 * @param segmentType Given SegmentType
	 * @return Corresponding String
	 */
	private String segmentTypeToString(SegmentType segmentType) {
		switch (segmentType) {
		case SEG_TYPE_CONTACT_HEADER:
			return "Contact Header Segment";
			
		case SEG_TYPE_ACTIVE_SESSION:
			return "Active Session Segment";
			
		default:
			return "Just to eliminate warnings";
		}
	}
	/** Segment Type */
	public SegmentType getSegmentType() {
		return _segmentType;
	}

	/** Segment Type */
	public void setSegmentType(SegmentType segmentType) {
		this._segmentType = segmentType;
	}

}
