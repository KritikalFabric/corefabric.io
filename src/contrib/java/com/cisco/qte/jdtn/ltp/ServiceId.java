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
 * The LTP 'ServiceId'.  This is a byte array which identifies the 'Service'
 * to which LTP delivers Blocks.
 */
public class ServiceId {

	public static final byte[] nullServiceIdBytes = {0};
	public static final ServiceId nullServiceId = new ServiceId(nullServiceIdBytes);
	public static final ServiceId defaultServiceId = new ServiceId(nullServiceIdBytes);
	protected byte[] _serviceIdBytes = nullServiceIdBytes;
	
	/**
	 * Get the default ServiceId, a value which indicates that blocks are to be
	 * delivered to the Bundle Protocol instance.
	 */
	public static ServiceId getDefaultServiceId() {
		return defaultServiceId;
	}
	
	/**
	 * Construct a ServiceId with the given sequence of bytes comprising the
	 * desired ServiceId
	 * @param bytes Given sequence of bytes
	 */
	public ServiceId(byte[] bytes) {
		setServiceIdBytes(bytes);
	}

	/**
	 * Construct a ServiceId by decoding the given DecodeState
	 * @param decodeState Contains encoded data buffer, offset, and length.
	 * After this operation, the offset is incremented appropriately.
	 * @throws JDtnException On decoding error
	 */
	public ServiceId(DecodeState decodeState) throws JDtnException {
		setServiceIdBytes(Utils.sdnvDecodeBytes(decodeState));
	}
	
	/**
	 * Construct a ServiceId consisting of a single byte
	 * @param serviceId Given single byte
	 */
	public ServiceId(byte serviceId) {
		_serviceIdBytes = new byte[1];
		_serviceIdBytes[0] = serviceId;
	}
	
	/**
	 * Encode this ServiceId into the given encode state.
	 * @param encodeState Given encode state
	 * @throws JDtnException 
	 */
	public void encode(EncodeState encodeState) throws JDtnException {
		Utils.sdnvEncodeBytes(_serviceIdBytes, encodeState);
	}
	
	public byte[] getServiceIdBytes() {
		return _serviceIdBytes;
	}

	public void setServiceIdBytes(byte[] serviceIdBytes) {
		this._serviceIdBytes = serviceIdBytes;
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
		StringBuffer sb = new StringBuffer(indent + "ServiceId\n");
		sb.append(indent + "  ServiceId=\n");
		sb.append(Utils.dumpBytes(indent + "  ", _serviceIdBytes, 0, _serviceIdBytes.length));
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object other) {
		if (other == null || !(other instanceof ServiceId)) {
			return false;
		}
		ServiceId otherServiceId = (ServiceId)other;
		return Utils.compareSdnvDecodedArrays(_serviceIdBytes, otherServiceId._serviceIdBytes);
	}
	
	@Override
	public int hashCode() {
		return Utils.byteArrayHashCode(_serviceIdBytes);
	}
	
}
