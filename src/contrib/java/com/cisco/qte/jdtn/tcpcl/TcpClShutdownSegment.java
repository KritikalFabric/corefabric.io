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
 * TCP Convergence Layer Shutdown Segment
 */
public class TcpClShutdownSegment extends TcpClActiveSessionSegment {

	public static final int REASON_INCLUDED_FLAG = 0x02;
	public static final int RECONNECT_DELAY_RQSTD_FLAG = 0x01;
	
	public static final int IDLE_TIMEOUT_REASON = 0;
	public static final int VERSION_MISMATCH_REASON = 1;
	public static final int BUSY_REASON = 2;
	
	public static final int RECONNECT_DELAY_SECS_DEFAULT = 10;
	
	/**
	 * If reconnect delay requested, requested number of secs to delay before
	 * reconnect 
	 */
	private int _reconnectDelaySecs = RECONNECT_DELAY_SECS_DEFAULT;
	
	/** Reason for shutdown; one of XXX_REASON */
	private int _reason;
	
	/**
	 * Constructor
	 */
	public TcpClShutdownSegment() {
		super(TcpClActiveSessionSegment.SHUTDOWN_SEGMENT_TYPE);
	}

	/**
	 * Encode this for transmission on the wire
	 * @param encoded Where to encode to
	 */
	@Override
	public void encode(EncodeState encoded) throws JDtnException {
		super.encode(encoded);
		if (isReasonIncluded()) {
			encoded.put(getReason());
		}
		if (isReconnectDelayRqstd()) {
			Utils.sdnvEncodeInt(getReconnectDelaySecs(), encoded);
		}
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
		if (isReasonIncluded()) {
			setReason(Utils.sdnvDecodeInt(decodeState));
		}
		if (isReconnectDelayRqstd()) {
			setReconnectDelaySecs(Utils.sdnvDecodeInt(decodeState));
		}
	}
	
	/**
	 * Dump this object
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "TcpClShutdownSegment\n");
		sb.append(super.dump(indent + "  ", detailed));
		if (isReasonIncluded()) {
			sb.append(indent + "  Reason=" + reasonToString(getReason()) + "\n");
		} else {
			sb.append(indent + "  No Reason Specified\n");
		}
		if (isReconnectDelayRqstd()) {
			sb.append(indent + "  Reconnect Delay=" + getReconnectDelaySecs() + " Secs\n");
		} else {
			sb.append(indent + "  No reconnect delay requested\n");
		}
			
		return sb.toString();
	}
	
	/**
	 * Get a String representation of given Reason code
	 * @param reason Given Reason code
	 * @return Corresponding String
	 */
	public static String reasonToString(int reason) {
		switch (reason) {
		case BUSY_REASON:
			return "Busy";
			
		case IDLE_TIMEOUT_REASON:
			return "Idle Timeout";
			
		case VERSION_MISMATCH_REASON:
			return "Version Mismatch";
			
		default:
			return "Unknown (" + reason + ")";
		}
	}
	
	/** Reason code is included in shutdown message */
	public boolean isReasonIncluded() {
		return (getFlags() & REASON_INCLUDED_FLAG) != 0;
	}
	
	/** Reason code is included in shutdown message */
	public void setReasonIncluded(boolean reasonIncluded) {
		if (reasonIncluded) {
			setFlags(getFlags() | REASON_INCLUDED_FLAG);
		} else {
			setFlags(getFlags() & ~REASON_INCLUDED_FLAG);
		}
	}
	
	/** Reconnect delay in secs included in shutdown message */
	public boolean isReconnectDelayRqstd() {
		return (getFlags() & RECONNECT_DELAY_RQSTD_FLAG) != 0;
	}
	
	/** Reconnect delay in secs included in shutdown message */
	public void setReconnectDelayRqstd(boolean reconnectDelayRqstd) {
		if (reconnectDelayRqstd) {
			setFlags(getFlags() | RECONNECT_DELAY_RQSTD_FLAG);
		} else {
			setFlags(getFlags() & ~RECONNECT_DELAY_RQSTD_FLAG);
		}
	}

	/**
	 * If reconnect delay requested, requested number of secs to delay before
	 * reconnect 
	 */
	public int getReconnectDelaySecs() {
		return _reconnectDelaySecs;
	}

	/**
	 * If reconnect delay requested, requested number of secs to delay before
	 * reconnect 
	 */
	public void setReconnectDelaySecs(int reconnectDelaySecs) {
		this._reconnectDelaySecs = reconnectDelaySecs;
	}

	/** Reason for shutdown; one of XXX_REASON */
	public int getReason() {
		return _reason;
	}

	/** Reason for shutdown; one of XXX_REASON */
	public void setReason(int reason) {
		this._reason = reason;
	}
	
}
