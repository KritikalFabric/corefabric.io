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

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.general.Address;
import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Utils;

/**
 * EngineId, a number which uniquely identifies a given LTP engine within
 * a close set of communicating LTP engines.
 */
public class EngineId {

	private static final Logger _logger =
		Logger.getLogger(EngineId.class.getCanonicalName());
	
	private static final String DEFAULT_ENGINE_ID_STR = "00.00.00.01";
	
	protected byte[] _engineId;

	/**
	 * Get a suitable default for the LTP Engine ID parameter.  We search thru
	 * our interfaces looking for a suitable IP address.  If found, we use that
	 * as our EngineID.  Otherwise, we use a probably unsuitable fixed default.
	 * @return Engine ID.
	 */
	public static EngineId getDefaultEngineId() {
		EngineId engineId = new EngineId();
		Enumeration<NetworkInterface> interfaces;
		try {
			interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				NetworkInterface intfc = interfaces.nextElement();
				Enumeration<InetAddress> addrs = intfc.getInetAddresses();
				while (addrs.hasMoreElements()) {
					InetAddress addr = addrs.nextElement();
					if (!addr.isLoopbackAddress() &&
						!addr.isMulticastAddress()) {
						engineId.setEngineId(addr.getAddress());
						return engineId;
					}
				}
			}
		} catch (SocketException e) {
			_logger.log(Level.SEVERE, "getDefaultEngineId", e);
		}
		engineId.setEngineId(DEFAULT_ENGINE_ID_STR.getBytes());
		return engineId;
	}
	
	/**
	 * Constructor; sets EngineId to unsuitable fixed default
	 */
	public EngineId() {
		try {
			setEngineIdString(DEFAULT_ENGINE_ID_STR);
		} catch (LtpException e) {
			_logger.log(Level.SEVERE, "Unexpected error setting default fixed Engine Id", e);
		}
	}
	
	/**
	 * Constructor; sets EngineId to given encoded EngineId String
	 * @param engineIdString Given encoded EngineId String, encoded as:
	 * "dd.dd.dd...", where each dd is a decimal encoding of corresponding
	 * byte in the EngineId.
	 * @throws LtpException
	 */
	public EngineId(String engineIdString) throws LtpException {
		setEngineIdString(engineIdString);
	}
	
	/**
	 * Constructor: sets EngineId bytes to bytes of given IPAddress
	 * @param ipAddress Givne IPAddress
	 */
	public EngineId(Address ipAddress) {
		setEngineId(ipAddress.getAddressBytes());
	}
	
	/**
	 * Construct EngineId from given DecodeState.
	 * @param decodeState Buffer, offset, and length from which to decode.
	 * After this operaiton, offset is updated.
	 * @throws JDtnException On decode errors
	 */
	public EngineId(DecodeState decodeState) throws JDtnException {
		setEngineId(Utils.sdnvDecodeBytes(decodeState));
	}
	
	/**
	 * Encode the Engine ID to the given byte buffer.
	 * @param encodeState Given Byte buffer
	 * @throws JDtnException On encoding errors
	 */
	public void encode(EncodeState encodeState) throws JDtnException {
		Utils.sdnvEncodeBytes(_engineId, encodeState);
	}
	
	/**
	 * Get the EngineId byte array
	 * @return EngineId byte array
	 */
	public byte[] getEngineId() {
		return _engineId;
	}

	/**
	 * Set the EngineId byte array
	 * @param engineId EngineId byte array
	 */
	public void setEngineId(byte[] engineId) {
		this._engineId = engineId;
	}
	
	/**
	 * Get the EngineId encoded as a String.
	 * @return The String encoded Engine Id, encoded in the form:
	 * "dd.dd.dd...", where each d is a decimal encoding of corresponding
	 * byte in the EngineId.
	 */
	public String getEngineIdString() {
		StringBuffer sb = new StringBuffer();
		for (int ix = 0; ix < _engineId.length; ix++) {
			String hex = String.format("%d", Utils.byteToIntUnsigned(_engineId[ix]));
			sb.append(hex);
			if (ix < _engineId.length - 1) {
				sb.append(".");
			}
		}
		return sb.toString();
	}
	
	/**
	 * Set the EngineId by decoding it from given String, assuming that String
	 * encodes an EngineId in the form: 
	 * "dd.dd.dd...", where each dd is a decimal encoding of corresponding
	 * byte in the EngineId.
	 * @param engineIdString Given String
	 * @throws LtpException If given String not encoded in given form.
	 */
	public void setEngineIdString(String engineIdString) throws LtpException {
		String[] words = engineIdString.split("\\.");
		if (words.length < 1) {
			throw new LtpException(
					"Invalid format for EngineId String: " + 
					engineIdString);
		}
		_engineId = new byte[words.length];
		for (int ix = 0; ix < words.length; ix++) {
			int hh = 0;
			try {
				hh = Integer.parseInt(words[ix]);
			} catch (NumberFormatException e) {
				throw new LtpException(
						"Component of EngineId not formatted as decimal digits: " + 
						engineIdString);
			}
			if (hh < 0 || hh > 255) {
				throw new LtpException(
						"Component of EngineId out of range (0..255): " + 
						engineIdString);
			}
			_engineId[ix] = Utils.intToByteUnsigned(hh);
		}
	}
	
	/**
	 * Convert to String
	 */
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
		StringBuffer sb = new StringBuffer(indent + "EngineId=" + getEngineIdString() + "\n");
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof EngineId)) {
			return false;
		}
		EngineId that = (EngineId)obj;
		return Utils.compareSdnvDecodedArrays(_engineId, that._engineId);
	}

	@Override
	public int hashCode() {
		return Utils.byteArrayHashCode(_engineId);
	}
	
	
}
