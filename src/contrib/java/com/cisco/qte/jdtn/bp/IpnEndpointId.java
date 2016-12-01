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
 * Implements the "ipn" EndpointId Scheme.  Such EndPointIds have the form:
 * <code>
 * ipn:hostNodeNumber.serviceNumber
 * </code>
 * where:
 * <ul>
 *   <li>hostNodeNumber is a single integer which
 *   uniquely identifies the DTN node among all other DTN
 *   nodes with which this node might communicated
 *   <li>serviceNumber is a single integer which
 *   identifies a service within the DTN node.
 * </ul>
 */
public class IpnEndpointId extends EndPointId {
	private static final Logger _logger =
		Logger.getLogger(IpnEndpointId.class.getCanonicalName());
	
	/** The Scheme name: 'ipn' */
	public static final String SCHEME_NAME = "ipn";
	/** The Null EndpointId String */
	public static final String DEFAULT_IPNEID_STR = "ipn:0.0";
	
	// Pattern used to extract hostNodeNumber and serviceNumber from SSP
	// This decdlaration must occur before the static init of
	// nullIpnEndPointId
	private static final Pattern _pattern = Pattern.compile("(\\d+)\\.(\\d+)");
	
	/** The 'null' or default EndpointId */
	public static IpnEndpointId nullIpnEndPointId;
	static {
		nullIpnEndPointId = new IpnEndpointId();
	}
	
	/** The Host Node Number component of the IPN EID */
	private int _hostNodeNumber = 0;
	/** The Service Number component of the IPN EID */
	private int _serviceNumber = 0;

	/**
	 * Construct the 'null' IpnEndpointId
	 */
	public IpnEndpointId() {
		try {
			initThis("ipn:0.0");
		} catch (BPException e) {
			_logger.log(Level.SEVERE, "FIX ME", e);
		}
	}
	
	/**
	 * Construct an IpnEndpointId from the given host node number and service.
	 * @param hostNodeNumber Given host node number
	 * @param serviceNumber Given service number
	 */
	public IpnEndpointId(int hostNodeNumber, int serviceNumber) {
		try {
			initThis(
					"ipn:" + Integer.toString(hostNodeNumber) + "." + 
					Integer.toString(serviceNumber));

		} catch (BPException e) {
			_logger.log(Level.SEVERE, "FIX ME", e);
		}
	}
	
	/**
	 * Construct an IpnEndPointId from the given URI String
	 * @param epidStr Given URI String; assumed to be of the canonical form
	 * for ipn: EndPointId scheme.
	 * @throws BPException on errors, mainly format errors
	 */
	public IpnEndpointId(String epidStr) throws BPException {
		initThis(epidStr);
	}

	/**
	 * Internal initialization
	 * @param epidStr URI String
	 * @throws BPException
	 */
	private void initThis(String epidStr) throws BPException {
		try {
			if (epidStr.equalsIgnoreCase(EndPointId.DEFAULT_ENDPOINT_ID_STRING)) {
				_uri = new URI(DEFAULT_IPNEID_STR);
			} else {
				_uri = new URI(epidStr);
			}
			
		} catch (URISyntaxException e1) {
			throw new BPException(e1);
		}

		if (_uri.getScheme() == null) {
			throw new BPException("No scheme specified: " + epidStr);
		}
		if (!_uri.getScheme().equals(SCHEME_NAME)) {
			throw new BPException("'ipn:' scheme not specified: " + epidStr);
		}
		if (_uri.getSchemeSpecificPart() == null) {
			throw new BPException("No scheme specific part specified: " + epidStr);
		}
		if (getScheme() == null) {
			throw new BPException("No scheme specified: " + epidStr);
		}
		if (getSchemeSpecificPart() == null) {
			throw new BPException("No scheme specific part specified: " + epidStr);
		}
		Matcher matcher = _pattern.matcher(getSchemeSpecificPart());
		if (!matcher.matches()) {
			throw new BPException("Scheme specific part doesn't match 'number.number'");
		}
		String hostString = matcher.group(1);
		if (hostString == null) {
			throw new BPException("Missing hostNumber in " + epidStr);
		}
		try {
			_hostNodeNumber = Integer.parseInt(hostString);
		} catch (NumberFormatException e) {
			throw new BPException("Poorly formatted hostNodeNumber in " + epidStr);
		}
		String serviceString = matcher.group(2);
		if (serviceString == null) {
			throw new BPException("Missing serviceNumber in " + epidStr);
		}
		try {
			_serviceNumber = Integer.parseInt(serviceString);
		} catch (NumberFormatException e) {
			throw new BPException("Poorly formatted serviceNumber in " + epidStr);
		}
	}
	
	@Override
	public String getHostNodeName() {
		return Integer.toString(_hostNodeNumber);
	}

	@Override
	public String getServicePath() {
		return Integer.toString(_serviceNumber);
	}

	/**
	 * Determine if this EndPointId is a prefix for a possibly longer EndPointId.
	 * @param endPointId EndPointId to gets against
	 * @return True if this stem is a prefix for given EndPointId
	 */
	@Override
	public boolean isPrefixOf(EndPointId endPointId) {
		if (getEndPointIdString().startsWith(endPointId.getEndPointIdString())) {
			return true;
		}
		return false;
	}
	
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "IpnEndpointId\n");
		sb.append(indent + "  HostNodeNumber=" + _hostNodeNumber + "\n");
		sb.append(indent + "  ServiceNumber=" + _serviceNumber + "\n");
		return sb.toString();
	}

	@Override
	public String toString() {
		return dump("", false);
	}

	/** The Host Node Number component of the IPN EID */
	public int getHostNodeNumber() {
		return _hostNodeNumber;
	}

	/** The Host Node Number component of the IPN EID */
	public void setHostNodeNumber(int hostNodeNumber) {
		this._hostNodeNumber = hostNodeNumber;
	}

	/** The Service Number component of the IPN EID */
	public int getServiceNumber() {
		return _serviceNumber;
	}

	/** The Service Number component of the IPN EID */
	public void setServiceNumber(int serviceNumber) {
		this._serviceNumber = serviceNumber;
	}

	@Override
	public boolean isNull() {
		return 
			getHostNodeNumber() == 0 &&
			getServiceNumber() == 0;
	}

}
