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

import java.util.Random;

import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Utils;

/**
 * Identifier for a Session, consisting of two parts:
 * <ul>
 *   <li>sessionOriginatorEngineID - Identifies the LTP engine which
 *   originated the session.
 *   <li>sessionNumber - unique serial number for the session.
 * </ul>
 */
public class SessionId {

	/**
	 * Session originator - the engine ID of the originator of the session
	 */
	protected EngineId _sessionOriginatorEngineId;
	
	/**
	 * Session number - typically a random number
	 */
	protected byte[] _sessionNumber;
	
	private static Random _rng = new Random();
	
	/**
	 * Constructor: Uses configured EngineId, generates random SessionNumber
	 */
	public SessionId() {
		setSessionOriginatorEngineId(LtpManagement.getInstance().getEngineId());
		generateRandomSessionNumber();
	}
	
	/**
	 * Constructor: Uses given EngineId, generates random SessionNumber
	 * @param engineId Given EngineId
	 */
	public SessionId(EngineId engineId) {
		setSessionOriginatorEngineId(engineId);
		generateRandomSessionNumber();
	}
	
	/**
	 * Constructor: Uses given EngineId and session number
	 * @param engineId Given EngineId
	 * @param sessionNumber Given sessionNumber
	 */
	public SessionId(EngineId engineId, byte[] sessionNumber) {
		setSessionOriginatorEngineId(engineId);
		setSessionNumber(sessionNumber);
	}
	
	/**
	 * Constructor: decodes members from given DecodeState
	 * @param decodeState Contains buffer to decode, offset, and length. After
	 * this operation, offset is updated
	 * @throws JDtnException on decode errors
	 */
	public SessionId(DecodeState decodeState) throws JDtnException {
		setSessionOriginatorEngineId(new EngineId(decodeState));
		setSessionNumber(Utils.sdnvDecodeBytes(decodeState));
	}
	
	/**
	 * Generate a random Session Number and install as sessionNumber member
	 */
	private void generateRandomSessionNumber() {
		int ranInt = _rng.nextInt();
		if (ranInt == 0) {
			ranInt = _rng.nextInt();
		}
		_sessionNumber = Utils.intToByteArray(ranInt);
	}
	
	/**
	 * Encode the SessionId into the given buffer
	 * @param encodeState Given buffer; we append to it
	 * @throws JDtnException on Encoding errors
	 */
	public void encode(EncodeState encodeState) throws JDtnException {
		_sessionOriginatorEngineId.encode(encodeState);
		Utils.sdnvEncodeBytes(_sessionNumber, encodeState);
	}
	
	public EngineId getSessionOriginatorEngineId() {
		return _sessionOriginatorEngineId;
	}
	public void setSessionOriginatorEngineId(EngineId sessionOriginatorEngineId) {
		this._sessionOriginatorEngineId = sessionOriginatorEngineId;
	}
	public byte[] getSessionNumber() {
		return _sessionNumber;
	}
	public void setSessionNumber(byte[] sessionNumber) {
		this._sessionNumber = sessionNumber;
	}
	
	@Override
	public String toString() {
		return dump("", false);
	}
	
	/**
	 * Dump the state of this object
	 * @param indent Amount of indentation
	 * @param detailed True if want verbose details
	 * @return String containing dump
	 */
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "SessionId\n");
		sb.append(_sessionOriginatorEngineId.dump(indent + "  ", detailed));
		sb.append(indent + "  SessionNumber=\n");
		sb.append(Utils.dumpBytes(indent + "  ", _sessionNumber, 0, _sessionNumber.length));
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object thatObj) {
		if (thatObj == null || !(thatObj instanceof SessionId)) {
			return false;
		}
		SessionId that = (SessionId)thatObj;
		if (!this._sessionOriginatorEngineId.equals(this._sessionOriginatorEngineId)) {
			return false;
		}
		return Utils.compareSdnvDecodedArrays(_sessionNumber, that._sessionNumber);
	}
	
	@Override
	public int hashCode() {
		return 
			_sessionOriginatorEngineId.hashCode() +
			Utils.byteArrayHashCode(_sessionNumber);
	}
	
}
