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
package com.cisco.qte.jdtn.ltp;

import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Utils;

/**
 * Cancel Segment - request to cancel a Session.  Segment header references
 * the Session to cancel, this provides a body consisting merely of a reason
 * for cancellation.
 */
public class CancelSegment extends Segment {

	public static final byte REASON_CODE_USER_CANCELLED = 0;
	public static final byte REASON_CODE_UNREACHABLE = 1;
	public static final byte REASON_CODE_RETRANS_LIMIT_EXCEEDED = 2;
	public static final byte REASON_CODE_MISCOLORED = 3;
	public static final byte REASON_CODE_SYSTEM_CANCELED = 4;
	public static final byte REASON_CODE_RETRANS_CYCLES_LIMIT_EXCEEDED = 5;
	
	/**
	 * The Reason code; one of REASON_CODE_XXX
	 */
	protected byte _reasonCode;
	
	/**
	 * Constructor; reasonCode set to CANCELLED, null SessionId
	 */
	public CancelSegment(SegmentType segType) {
		super(segType);
		setReasonCode(REASON_CODE_USER_CANCELLED);
	}
	
	/**
	 * Constructor: Specified arguments
	 * @param reasonCode Specified reason code
	 * @param sessionId Session ID of the Session being canceled
	 */
	public CancelSegment(SegmentType segType, byte reasonCode, SessionId sessionId) {
		super(segType);
		setReasonCode(reasonCode);
		setSessionID(sessionId);
	}
	
	/**
	 * Decode the Segment specific contents.  Called from super during the
	 * process of decoding the full Segment.
	 * @throws JDtnException on decode errors
	 */
	@Override
	protected void decodeContents(java.sql.Connection con, DecodeState decodeState) throws JDtnException {
		setReasonCode(Utils.decodeByte(decodeState));
	}
	
	/**
	 * Encode the CancelSegment specific contents to the given buffer.
	 * @param encodeState Given buffer
	 * @throws JDtnException on encoding errors
	 */
	@Override
	protected void encodeContents(java.sql.Connection con, EncodeState encodeState) throws JDtnException {
		encodeState.put(_reasonCode);
	}

	/**
	 * The Reason code; one of REASON_CODE_XXX
	 */
	public byte getReasonCode() {
		return _reasonCode;
	}

	/**
	 * The Reason code; one of REASON_CODE_XXX
	 */
	public void setReasonCode(byte reasonCode) {
		this._reasonCode = reasonCode;
	}
	
	@Override
	public String toString() {
		return dump("", false);
	}

	/**
	 * Translate CancelSegment reason code to a String
	 * @param reasonCode Given reason code
	 * @return Corresponding String
	 */
	public static String reasonCodeToString(byte reasonCode) {
		switch (reasonCode) {
		case REASON_CODE_USER_CANCELLED:
			return "User Cancelled";
		case REASON_CODE_UNREACHABLE:
			return "Destination Unreachable";
		case REASON_CODE_RETRANS_LIMIT_EXCEEDED:
			return "Retransmit limit exceeded";
		case REASON_CODE_MISCOLORED:
			return "Miscolored Segment";
		case REASON_CODE_SYSTEM_CANCELED:
			return "System Cancelled";
		case REASON_CODE_RETRANS_CYCLES_LIMIT_EXCEEDED:
			return "Retransmit cycles limit exceeded";
		default:
			return "Unknown Reason Code (" + reasonCode + ")";
		}
	}
	
	/**
	 * Dump the state of this object
	 * @param indent Amount of indentation
	 * @param detailed True if want verbose details
	 * @return String containing dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "CancelSegment\n");
		sb.append(super.dump(indent + "  ", detailed));
		sb.append(indent + "  ReasonCode=" + reasonCodeToString(_reasonCode) + "\n");
		return sb.toString();
	}
	
}
