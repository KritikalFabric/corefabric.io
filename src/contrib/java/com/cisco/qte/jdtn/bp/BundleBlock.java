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

import com.cisco.qte.jdtn.bp.EidScheme;
import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.JDtnException;

/**
 * Superclass for all Bundle Blocks.
 */
public abstract class BundleBlock {
	/** Bundle of which this BundleBlock is a part */
	protected Bundle _bundle;
	
	protected BundleBlock() {
		// Nothing
	}
	
	/**
	 * Constructor from given DecodeState.
	 * @throws JDtnException on various decode errors
	 */
	public BundleBlock(Bundle bundle, DecodeState decodeState)
	throws JDtnException {
		setBundle(bundle);
	}
	
	/**
	 * Dump this object
	 * @param indent How much to indent
	 * @param detailed Whether detailed dump desired
	 * @return the dump
	 */
	public String dump(String indent, boolean detailed) {
		return "";
	}

	@Override
	public String toString() {
		return dump("", false);
	}
	
	/** Bundle of which this BundleBlock is a part */
	public Bundle getBundle() {
		return _bundle;
	}

	/** Bundle of which this BundleBlock is a part */
	public void setBundle(Bundle bundle) {
		this._bundle = bundle;
	}

	/**
	 * Encode this Bundle to the given EncodeState
	 * @param encodeState Given EncodeState
	 * @param eidScheme The EidScheme to use to encode the Bundle Block
	 * @throws JDtnException On Encoding errors
	 */
	public abstract void encode(java.sql.Connection con, EncodeState encodeState, EidScheme eidScheme)
	throws JDtnException, InterruptedException;
}
