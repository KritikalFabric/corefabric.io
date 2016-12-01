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
 * An enum which indicates where the bundle in the persistent store came from.
 */
public enum BundleSource {

	/** Bundle was sourced by this endpoint */
	SOURCED,
	/** Bundle was forwarded to us */
	FORWARDED;
	
	/**
	 * Convert the enum to a String which can be parsed later
	 * @param bundleSource The enum to be converted
	 * @return A String representation of the enum
	 * @throws IllegalArgumentException If the enum to be converted is somehow
	 * outside the range of the enum.
	 */
	public static String toParseableString(BundleSource bundleSource) 
	throws IllegalArgumentException {
		switch (bundleSource) {
		case FORWARDED:
			return "Forwarded";
		case SOURCED:
			return "Sourced";
		default:
			throw new IllegalArgumentException(
					"BundleSource invalid: " + bundleSource);
		}
	}
	
	/**
	 * Parse given String to a BundleSource enum
	 * @param str Given String
	 * @return Corresponding BundleSource enum
	 * @throws IllegalArgumentException If given String cannot be parsed to a
	 * valid BundleSource enum.
	 */
	public static BundleSource parseBundleSource(String str) 
	throws IllegalArgumentException {
		if (str.equalsIgnoreCase("Forwarded")) {
			return FORWARDED;
		} else if (str.equalsIgnoreCase("Sourced")) {
			return SOURCED;
		} else {
			throw new IllegalArgumentException(
					"Invalid String rep for BundleSource: " + str);
		}
	}
}
