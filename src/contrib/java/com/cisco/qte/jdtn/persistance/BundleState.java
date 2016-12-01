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
package com.cisco.qte.jdtn.persistance;

/**
 * An enum indicating the state and what actions have been performed on a
 * Bundle in the persistence store.
 */
public enum BundleState {

	/** Bundle received from elsewhere */
	RECEIVED,
	/** Sourced by this endpoint */
	SOURCED,
	/** Bundle enqueued for forwarding */
	FORWARD_ENQUEUED,
	/** Bundle held */
	HELD,
	/** Bundle in custody */
	IN_CUSTODY;
	
	/**
	 * Convert the enum to a String representation
	 * @param bundleState the given enum
	 * @return String representation
	 * @throws IllegalArgumentException If the enum value is somehow outside
	 * the range of the enum
	 */
	public static String toParseableString(BundleState bundleState) 
	throws IllegalArgumentException {
		switch (bundleState) {
		case RECEIVED:
			return "Received";
		case SOURCED:
			return "Sourced";
		case FORWARD_ENQUEUED:
			return "forwardEnqueued";
		case HELD:
			return "held";
		case IN_CUSTODY:
			return "inCustody";
		default:
			throw new IllegalArgumentException("Input invalid: " + bundleState);
		}
	}
	
	/**
	 * Parse given String to a BundleState enum
	 * @param str The given String
	 * @return The corresponding BundleState enum
	 * @throws IllegalArgumentException If the given String cannot be parsed
	 * to a valid BundleState enum.
	 */
	public static BundleState parseBundleState(String str)
	throws IllegalArgumentException {
		if (str.equalsIgnoreCase("Received")) {
			return RECEIVED;
		} else if (str.equalsIgnoreCase("Sourced")) {
			return SOURCED;
		} else if (str.equalsIgnoreCase("forwardEnqueued")) {
			return FORWARD_ENQUEUED;
		} else if (str.equalsIgnoreCase("held")) {
			return HELD;
		} else if (str.equalsIgnoreCase("inCustody")) {
			return IN_CUSTODY;
		} else {
			throw new IllegalArgumentException(
					"Invalid String rep for BundleState: " + str);
		}
	}
}
