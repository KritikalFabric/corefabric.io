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
package com.cisco.qte.jdtn.general;


/**
 * Abstraction for any kind of "link-layer" address, represented by a sequence
 * of bytes.  This can include non-link-layers which masquerade as link-layers
 * such as LTP over UDP.
 */
public class Address {
	/**
	 * Sequence of bytes for null address; a single byte of 0.
	 */
	public static final byte[] nullAddressBytes = {0};
	
	/**
	 * The Null Address
	 */
	public static final Address nullAddress = new Address(nullAddressBytes);
	
	/**
	 * The sequence of bytes comprising the Address
	 */
	protected byte[] _addressBytes = nullAddressBytes;
	
	/**
	 * Constructor; default value of sequence of bytes is null address.
	 */
	public Address() {
		// Nothing
	}

	/**
	 * Contsructor from a String.  The input String is assumed to be of the
	 * form "hh{.hh...}", where each "hh" is one or two hex digits encoding a
	 * single (unsigned) byte of the address.
	 * @param inStr InputString
	 * @throws JDtnException If the input String is not formatted as described.
	 */
	public Address(String inStr) throws JDtnException {
		String[] words = inStr.split("\\.");
		if (words != null && words.length >= 1) {
			_addressBytes = new byte[words.length];
			int ix = 0;
			for (String word : words) {
				int num = Integer.parseInt(word, 16);
				if (num >= 0 && num <= 0xff) {
					try {
						_addressBytes[ix++] = Utils.intToByteUnsigned(num);

					} catch (Exception e) {
						throw new JDtnException("Invalid hex encoding: " + inStr);
					}
					
				} else {
					throw new JDtnException("Hex component out of range for byte: " + inStr);
				}
			}
		} else {
			throw new JDtnException("Address format error: " + inStr);
		}
	}
	
	/**
	 * Constructor for Address containing given sequence of bytes
	 * @param addressBytes Given byte sequence
	 */
	public Address(byte[] addressBytes) {
		setAddressBytes(addressBytes);
	}
	
	/**
	 * Get the sequence of bytes comprising this Address
	 * @return what I said
	 */
	public byte[] getAddressBytes() {
		return _addressBytes;
	}

	/**
	 * Set the sequence of bytes comprising this Address
	 * @param addressBytes what I said
	 */
	public void setAddressBytes(byte[] addressBytes) {
		this._addressBytes = addressBytes;
	}
	
	/**
	 * Converts this Address to a String of the form: "hh{.hh...}", where each
	 * "hh" is two hex digits representing one of the bytes of the Address.
	 * @return Parseable Address
	 */
	public String toParseableString() {
		return Utils.dumpBytes("", _addressBytes, 0, _addressBytes.length, false);
	}
	
	/**
	 * Convert to a String; Intended for debug; not intended to be parseable
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
		StringBuffer sb = new StringBuffer(indent + "Address\n");
		sb.append(Utils.dumpBytes(indent + "  ", _addressBytes, 0, _addressBytes.length, detailed));
		return sb.toString();
	}

	/**
	 * Address Equality: byte sequences same size and all bytes equal
	 */
	@Override
	public boolean equals(Object thatObj) {
		if (thatObj == null) {
			return false;
		}
		if (!(thatObj instanceof Address)) {
			return false;
		}
		Address that = (Address)thatObj;
		return Utils.compareByteArrays(_addressBytes, that._addressBytes);
	}

	/**
	 * Because we overrode equals()
	 */
	@Override
	public int hashCode() {
		return Utils.byteArrayHashCode(_addressBytes);
	}
	
}
