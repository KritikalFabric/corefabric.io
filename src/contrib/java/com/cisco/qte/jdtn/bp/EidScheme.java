/**
Copyright (c) 2011, Cisco Systems, Inc.
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

/**
 * Enumerates the several EndPointId schemes we support.
 */
public enum EidScheme {
	/** RFC 5050 "dtn:" Eid Scheme */
	DTN_EID_SCHEME,
	/** Compressed Bundle Header "ipn:" Eid Scheme */
	IPN_EID_SCHEME
	;

	/**
	 * Convert given String naming an EndPointID Scheme to a EidScheme
	 * @param eidSchemeStr Given String
	 * @return Corresponding EidScheme
	 * @throws BPException If given String doesn't name any valid EidScheme
	 */
	public static EidScheme parseEidScheme(String eidSchemeStr)
	throws BPException {
		if (eidSchemeStr.equals("dtn")) {
			return EidScheme.DTN_EID_SCHEME;
		} else if (eidSchemeStr.equals("ipn")) {
			return EidScheme.IPN_EID_SCHEME;
		} else {
			throw new BPException("Invalid EidScheme: " + eidSchemeStr + 
					"; must be 'dtn' or 'ipn'");
		}
	}
	
	/**
	 * Convert given EidScheme to a String
	 * @param eidScheme Given EidScheme
	 * @return Corresponding EidScheme String
	 */
	public static String eidSchemeToString(EidScheme eidScheme) {
		switch (eidScheme) {
		case IPN_EID_SCHEME:
			return "ipn";
		default:
			return "dtn";
		}
	}
	
}
