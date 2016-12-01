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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An Endpoint ID, a URI which identifies the source or receipient of a Bundle.
 *	4.4.  Endpoint IDs
 *	   The destinations of bundles are bundle endpoints, identified by text
 *	   strings termed "endpoint IDs" (see Section 3.1).  Each endpoint ID
 *	   conveyed in any bundle block takes the form of a Uniform Resource
 *	   Identifier (URI; [URI]).  As such, each endpoint ID can be
 *	   characterized as having this general structure:
 *  < scheme name > : < scheme-specific part, or "SSP" >
 */
public class EndPointId {

	private static final Logger _logger =
		Logger.getLogger(EndPointId.class.getCanonicalName());
	
	/** Default EndPointId String: "dtn:none" */
	public static final String DEFAULT_ENDPOINT_ID_STRING = "dtn:none";
	/** Default Scheme: "dtn" */
	public static final String DEFAULT_SCHEME = "dtn";
	/** Defaut Scheme Specific Part: "none" */
	public static final String DEFAULT_SSP = "none";
	
	/** Max length in bytes of EndPointId scheme. 
	 * 4.4.  Endpoint IDs
	 *  As used for the purposes of the bundle protocol, neither the length
	 *  of a scheme name nor the length of an SSP may exceed 1023 bytes.
	 */
	public static final int MAX_SCHEME_LENGTH = 1023;
	/** Max length in bytes of EndPointId scheme specific part.
	 * 4.4.  Endpoint IDs
	 *  As used for the purposes of the bundle protocol, neither the length
	 *  of a scheme name nor the length of an SSP may exceed 1023 bytes.
	 */
	public static final int MAX_SSP_LENGTH = 1023;
	
	public static EndPointId defaultEndPointId;
	static {
		try {
			defaultEndPointId = new EndPointId(DEFAULT_ENDPOINT_ID_STRING);
		} catch (BPException e) {
			_logger.log(Level.SEVERE, "Initializing default EndPointId", e);
		}
	}
		
	/** The URI underlying this class */
	protected URI _uri = null;
	/** Pattern used in isPrefixOf() */
	private Pattern _pattern = null;	// constructed on demand
	
	/**
	 * Create a new EndPointId from the given EndPointId.
	 * @param original Given EndPointId
	 * @throws BPException on errors
	 */
	public static EndPointId createEndPointId(EndPointId original)
	throws BPException {
		return createEndPointId(original.getEndPointIdString());
	}
	
	/**
	 * Create a new EndPointId from the given scheme and scheme specific part.
	 * @param scheme The scheme
	 * @param ssp The scheme specific part
	 * @return The EndPointId created
	 * @throws BPException if arguments are poorly formatted
	 */
	public static EndPointId createEndPointId(String scheme, String ssp)
	throws BPException {
		String epidStr = scheme + ":" + ssp;
		return createEndPointId(epidStr);
	}
	
	/**
	 * Create a new EndPointId parsed from the given String.  Will create an
	 * instance of any scheme supported (ipn: and dtn:)
	 * @param epidStr Given String
	 * @return EndPointId of the desired type
	 * @throws BPException If the given String is poorly formatted
	 */
	public static EndPointId createEndPointId(String epidStr)
	throws BPException {
		if (epidStr.startsWith(IpnEndpointId.SCHEME_NAME + ":")) {
			return new IpnEndpointId(epidStr);
		} else {
			return new EndPointId(epidStr);
		}
	}
	
	/**
	 * Get the "Default" EndPointId: dtn:none
	 * @return Default EndPointId
	 */
	public static EndPointId getDefaultEndpointId() {
		return new EndPointId();
	}

	/**
	 * Construct a new EndPointId containing the default EndPointId: dtn:none
	 */
	public EndPointId() {
		try {
			_uri = new URI(DEFAULT_ENDPOINT_ID_STRING);
			
		} catch (URISyntaxException e) {
			_logger.log(Level.SEVERE, "constructing default URI", e);
		}
	}
	
	/**
	 * Construct a new EndPointId from the given String
	 * @param epidStr Given String
	 * @throws BPException If given String is not a valid URI
	 */
	protected EndPointId(String epidStr) throws BPException {
		initThis(epidStr);
	}

	/**
	 * Construct a new EndPointId from the given Scheme and Scheme Specific
	 * Part (SSP)
	 * @param scheme Given Scheme
	 * @param ssp Given SSP
	 * @throws BPException if scheme or ssp are invalid
	 */
	public EndPointId(String scheme, String ssp) throws BPException {
		initThis(scheme + ":" + ssp);
	}
	
	/**
	 * Construct new EndPointId by appending given String to this EndpointId
	 * String.  Must include any separators yourself if desired.
	 * @param str String to append
	 * @return new EndPointId
	 * @throws BPException if result is poorly formed URI
	 */
	public EndPointId append(String str) throws BPException {
		return EndPointId.createEndPointId(getEndPointIdString() + str);
	}
	
	/**
	 * Initialize this from given EndPointId String
	 * @param epidStr Given String
	 * @throws BPException If given String is not a valid EndPointId
	 */
	private void initThis(String epidStr) throws BPException {
		try {
			_uri = new URI(epidStr);
			
			// A short cut - if no scheme supplied, then supply one.
			if (_uri.getScheme() == null) {
				_uri = new URI(DEFAULT_SCHEME + "://" + epidStr);
			}
			if (_uri.getSchemeSpecificPart() == null) {
				throw new BPException("No scheme specific part in : + " +
						epidStr);
			}
			/*
			 * 4.4.  Endpoint IDs
			 *  As used for the purposes of the bundle protocol, neither the length
			 *  of a scheme name nor the length of an SSP may exceed 1023 bytes.
			 */
			if (_uri.getScheme().length() > MAX_SCHEME_LENGTH) {
				throw new BPException(
						"Scheme name exceeds " + 
						MAX_SCHEME_LENGTH + 
						" bytes in length");
			}
			if (_uri.getSchemeSpecificPart().length() > MAX_SSP_LENGTH) {
				throw new BPException(
						"Scheme Specific Part exceeds " + 
						MAX_SSP_LENGTH + 
						" bytes in length");
			}
			

		} catch (URISyntaxException e) {
			throw new BPException("URI Syntax Error Decoding String '" + epidStr + "'", e);
		}
	}
	
	/**
	 * Get a parsable String representation of this EndPointId
	 * @return String representation
	 */
	public String getEndPointIdString() {
		return _uri.toASCIIString();
	}
	
	/**
	 * Determine if this EndPointId is a prefix for a possibly longer EndPointId.
	 * @param endPointId EndPointId to gets against
	 * @return True if this stem is a prefix for given EndPointId
	 */
	public boolean isPrefixOf(EndPointId endPointId) {
		String thatStr = endPointId.getEndPointIdString();
		if (_pattern == null) {
			_pattern = Pattern.compile(getEndPointIdString() + "(/[^/]+)*");
		}
		Matcher matcher = _pattern.matcher(thatStr);
		return matcher.matches();
	}
	
	/**
	 * Determine if this EndPointId is a 'null' EndPointId; i.e., whether it
	 * stands for the 'dtn:none' EndpointId.
	 * @return True if this is null EndPointId.
	 */
	public boolean isNull() {
		return
			getScheme().equals(DEFAULT_SCHEME) &&
			getSchemeSpecificPart().equals(DEFAULT_SSP);
	}
	
	/**
	 * Dump this object
	 * @param indent How much to indent
	 * @param detailed Whether detailed dump desired
	 * @return the dump
	 */
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "EndPointId=" + 
				getEndPointIdString() + "\n");
		return sb.toString();
	}

	@Override
	public String toString() {
		return dump("", false);
	}
	
	@Override
	public boolean equals(Object otherObj) {
		if (otherObj == null || !(otherObj instanceof EndPointId)) {
			return false;
		}
		EndPointId other = (EndPointId)otherObj;
		return this._uri.equals(other._uri);
	}
	
	@Override
	public int hashCode() {
		return _uri.hashCode();
	}
	
	public String getScheme() {
		return _uri.getScheme();
	}
	
	public String getSchemeSpecificPart() {
		return _uri.getSchemeSpecificPart();
	}
	
	public String getQuery() {
		return _uri.getQuery();
	}
	
	public String getHostNodeName() {
		// Special treatment for 'dtn:none'
		if (this.equals(defaultEndPointId)) {
			_logger.info(
					"EndPointId " + _uri.toASCIIString() +
					": 'host part' is zero-length String");
			return "";
		}
		String host = _uri.getHost();
		if (host == null) {
			// The URI SSP did not start with "//"; i.e., not of the form
			// scheme://host/other stuff
			// and so the "host" part was not present.  Synthesize a "host"
			// part by assuming the following:
			// scheme:host/otherstuff
			String ssp = _uri.getSchemeSpecificPart();
			int slashPos = ssp.indexOf("/");
			if (slashPos > 0) {
				host = ssp.substring(0, slashPos);
			} else {
				// No slash in the SSP.  Assume entire SSP is host
				_logger.info(
						"EndPointId " + _uri.toASCIIString() + 
						"; assuming 'host part' = " + ssp);
				host = ssp;
			}
		}
		return host;
	}
	
	public String getServicePath() {
		// Special treatment for 'dtn:none'
		if (this.equals(defaultEndPointId)) {
			_logger.info(
					"EndPointId " + _uri.toASCIIString() +
					": 'service path' is zero-length String");
			return "";
		}
		String servicePath = _uri.getPath();
		if (servicePath == null) {
			String ssp = _uri.getSchemeSpecificPart();
			int slashPos = ssp.indexOf("/");
			if (slashPos > 0) {
				servicePath = ssp.substring(slashPos);
			} else {
				_logger.info(
						"EndPointId " + _uri.toASCIIString() +
						"; assuming zero-length 'service part'");
				servicePath = "";
			}
		}
		return servicePath;
	}
	
}
