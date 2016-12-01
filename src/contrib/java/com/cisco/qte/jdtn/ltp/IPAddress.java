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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.general.Address;
import com.cisco.qte.jdtn.general.Utils;

/**
 * Abstraction for IP Address; a sequence of address bytes which wraps an
 * InetAddress.  I wanted something that is derived from the more generic
 * class Address.
 */
public class IPAddress extends Address {

	private static final Logger _logger = 
		Logger.getLogger(IPAddress.class.getCanonicalName());
	
	/**
	 * Sequence of bytes for the null IPAddress
	 */
	public static final byte[] nullIPAddressBytes = {0, 0, 0, 0};
	
	/**
	 * The null IPAddress
	 */
	public static IPAddress nullIPAddress;
	static {
		try {
			nullIPAddress = new IPAddress(nullIPAddressBytes);
		} catch (UnknownHostException e) {
			// Shouldn't happen
			_logger.log(Level.SEVERE, "Exception Ignored", e);			
		}
	}
	
	/**
	 * The associated InetAddress
	 */
	protected InetAddress _inetAddress;
	
	/**
	 * Constructor: set to nullIPAddress
	 */
	public IPAddress() {
		setAddressBytes(nullAddressBytes);
	}
	
	/**
	 * Constructor: set to given sequence of bytes
	 * @param addressBytes Given sequence of bytes
	 * @throws UnknownHostException from InetAddress
	 */
	public IPAddress(byte[] addressBytes) throws UnknownHostException {
		super(addressBytes);
			InetAddress inetAddress = InetAddress.getByAddress(addressBytes);
			setInetAddress(inetAddress);			
	}

	/**
	 * Constructor: set to given InetAddress
	 * @param inetAddress Given InetAddress
	 */
	public IPAddress(InetAddress inetAddress) {
		super(inetAddress.getAddress());
		setInetAddress(inetAddress);
	}
	
	/**
	 * Constructor: set to given address string in InetAddress notation or
	 * hostname.
	 * @param ipAddressStr InetAddress hostname or notation
	 * @throws UnknownHostException if invalid IP address string
	 */
	public IPAddress(String ipAddressStr) throws UnknownHostException {
		super();
		InetAddress inetAddress = InetAddress.getByName(ipAddressStr);
		setInetAddress(inetAddress);
	}
	
	/**
	 * Get the associated InetAddress
	 * @return what I said
	 */
	public InetAddress getInetAddress() {
		return _inetAddress;
	}

	/**
	 * Set the associated InetAddress
	 * @param inetAddress what I said
	 */
	public void setInetAddress(InetAddress inetAddress) {
		this._inetAddress = inetAddress;
		super.setAddressBytes(inetAddress.getAddress());
	}

	/**
	 * Override of super method to set bytes of address.  In addition to that,
	 * we set the associated InetAddress.
	 */
	@Override
	public void setAddressBytes(byte[] addressBytes) {
		super.setAddressBytes(addressBytes);
		try {
			setInetAddress(InetAddress.getByAddress(addressBytes));
		} catch (UnknownHostException e) {
			// Shouldn't happen
			_logger.log(Level.SEVERE, "Exception Ignored", e);
		}
	}
	
	/**
	 * Convert to a parseable String
	 * @return Parseable String
	 */
	@Override
	public String toParseableString() {
		return Utils.inetAddressString(_inetAddress);
	}
	
	@Override
	public String toString() {
		return "InetAddress=" + Utils.inetAddressString(_inetAddress);
	}
	
	/**
	 * Dump the state of this object
	 * @param indent Amount of indentation
	 * @param detailed True if want verbose details
	 * @return String containing dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "IPAddress\n");
		sb.append(indent + "  InetAddress=" + Utils.inetAddressString(_inetAddress) + "\n");
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object thatObj) {
		if (thatObj == null || !(thatObj instanceof IPAddress)) {
			return false;
		}
		IPAddress that = (IPAddress)thatObj;
		return _inetAddress.equals(that._inetAddress);
	}
	
	@Override
	public int hashCode() {
		return _inetAddress.hashCode();
	}
	
}
