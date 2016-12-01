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
 * Segment Extension - A type/Length/Value (TLV)
 */
public class SegmentExtension {

	public static final int LTP_AUTHENTICATION_EXTENSION = 0;
	public static final int LTP_COOKIE_EXTENSION = 1;
	
	/**
	 * Type/Tag of the segment extension
	 */
	protected int _type;
	
	/**
	 * Length of the segment extension
	 */
	protected int _length;
	
	/**
	 * Value associated with the segment extension
	 */
	protected byte[] _value;
	
	/**
	 * Constructor: Decode the given DecodeState into the SegmentExtension
	 * @param decodeState Decode state, giving buffer, offset, and length.
	 * The offset is updated as a result of decoding.
	 * @throws JDtnException on decoding errors
	 */
	public SegmentExtension(DecodeState decodeState) throws JDtnException {
		// Type - 1 byte tag
		setType(decodeState.getByte());
		
		// Length - SDNV
		setLength(Utils.sdnvDecodeInt(decodeState));
		
		// value - {length}-byte byte array
		_value = new byte[_length];
		for (int ix = 0; ix < _length; ix++) {
			_value[ix] = (byte)decodeState.getByte();
		}
		
	}
	
	/**
	 * Encode the Segment Extension into the given buffer.
	 * @param encodeState Given buffer.  We append to it.
	 * @throws JDtnException on encoding errors
	 */
	public void encode(EncodeState encodeState) throws JDtnException {
		// Type
		encodeState.put(_type);
		
		// Length
		Utils.sdnvEncodeInt(_length, encodeState);
		
		// Value
		for (int ix = 0; ix < _value.length; ix++) {
			encodeState.put(_value[ix]);
		}
	}
	
	public int getType() {
		return _type;
	}
	public void setType(int type) {
		this._type = type;
	}
	public int getLength() {
		return _length;
	}
	public void setLength(int length) {
		this._length = length;
	}
	public byte[] getValue() {
		return _value;
	}
	public void setValue(byte[] value) {
		this._value = value;
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
		StringBuffer sb = new StringBuffer(indent + "SegmentExtension\n");
		sb.append(indent + "  Type=" + _type + "\n");
		sb.append(indent + "  Length=" + _length + "\n");
		if (detailed) {
			sb.append("  Value=\n");
			sb.append(Utils.dumpBytes(indent + "  ", _value, 0, _value.length));
		}
		return sb.toString();
	}
	
}
