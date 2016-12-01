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
package com.cisco.qte.jdtn.bp;

import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.JDtnException;

/**
 * A BundleBlock which carries the application's payload (or a fragment of it)
 */
public class PayloadBundleBlock extends SecondaryBundleBlock {

	/**
	 * Constructor
	 */
	public PayloadBundleBlock() {
		setBlockType(SecondaryBundleBlock.BLOCK_TYPE_PAYLOAD);
	}

	public PayloadBundleBlock(
			Payload payload) {
		super(SecondaryBundleBlock.BLOCK_TYPE_PAYLOAD, payload);
	}
	
	/**
	 * Construct a PayloadBundleBlock by decoding from given DecodeState
	 * @param bundle Parent Bundle of this Block
	 * @param decodeState Given Decode State
	 * @throws JDtnException On various decode errors
	 */
	public PayloadBundleBlock(Bundle bundle, DecodeState decodeState)
	throws JDtnException {
		super(bundle, decodeState, SecondaryBundleBlock.BLOCK_TYPE_PAYLOAD);
		// Super decodes all
	}
	
	/**
	 * Block contains an EID reference.
	 * Overridden - PayloadBundleBlocks cannot contain EID references
	 */
	@Override
	public boolean containsEidReference() {
		return false;
	}
	/**
	 * Block contains an EID reference
	 * Overridden - PayloadBundleBlocks cannot contain EID references
	 * @throws IllegalArgumentExcetpion if argument is true
	 */
	@Override
	public void setContainsEidReference(boolean containsEidReference) {
		if (containsEidReference) {
			throw new IllegalArgumentException("PayloadBundleBlock cannot contain EID References");
		}
	}
	
	public Payload getPayload() {
		return (Payload)getBody();
	}

	/**
	 * Dump this object
	 * @param indent How much to indent
	 * @param detailed Whether detailed dump desired
	 * @return the dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "PayloadBundleBlock\n");
		sb.append(super.dump(indent + "  ", detailed));
		return sb.toString();
	}

	@Override
	public String toString() {
		return dump("", false);
	}

}
