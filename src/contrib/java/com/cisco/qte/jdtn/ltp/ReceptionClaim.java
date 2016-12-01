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
 * A claim about the receipt of a DataSegment of a Block.  The Claim is made
 * in terms of:
 * <ul>
 *   <li>The offset into the original Block of data to the data claimed.
 *   <li>The length starting from offset of the data claimed.
 * </ul>
 */
public class ReceptionClaim {
	/**
	 * Claim Offset; offset relative to lowerBound of containing ReceptionClaim.
	 */
	protected long _offset;
	/** Claim Length in bytes */
	protected long _length;
	
	/**
	 * Construct w/ both offset and length 0
	 */
	public ReceptionClaim() {
		_offset = 0;
		_length = 0;
	}
	
	/**
	 * Constructor with members decoded from given DecodeState
	 * @param decodeState Given DecodeState, containing the buffer of bytes to
	 * decode from, the current offset into the buffer, and the buffer lenght.
	 * When this operation has completed, the current offset in the 
	 * DecodeState is updated.
	 * @throws JDtnException on decoding errors
	 */
	public ReceptionClaim(DecodeState decodeState) throws JDtnException {
		setOffset(Utils.sdnvDecodeLong(decodeState));
		setLength(Utils.sdnvDecodeLong(decodeState));
	}
	
	/**
	 * Construct setting members from arguments
	 * @param offset Offset
	 * @param length Length
	 */
	public ReceptionClaim(long offset, long length) {
		setOffset(offset);
		setLength(length);
	}
	
	/**
	 * Copy constructor
	 * @param original Original ReceptionClaim
	 */
	public ReceptionClaim(ReceptionClaim original) {
		setOffset(original.getOffset());
		setLength(original.getLength());
	}
	
	/**
	 * Encode this ReceptionClaim into the given buffer.
	 * @param encodeState Buffer to encode into
	 * @throws JDtnException on encoding errors
	 */
	public void encode(EncodeState encodeState) throws JDtnException {
		Utils.sdnvEncodeLong(_offset, encodeState);
		Utils.sdnvEncodeLong(_length, encodeState);
	}
	
	/**
	 * Claim Offset; offset relative to lowerBound of containing ReceptionClaim.
	 */
	public long getOffset() {
		return _offset;
	}
	/**
	 * Claim Offset; offset relative to lowerBound of containing ReceptionClaim.
	 */
	public void setOffset(long offset) {
		this._offset = offset;
	}
	/** Claim Length in bytes */
	public long getLength() {
		return _length;
	}
	/** Claim Length in bytes */
	public void setLength(long length) {
		this._length = length;
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
		StringBuffer sb = new StringBuffer(indent + "ReceptionClaim\n");
		sb.append(indent + "  Offset=" + _offset + "\n");
		sb.append(indent + "  Length=" + _length + "\n");
		return sb.toString();
	}
	
}

