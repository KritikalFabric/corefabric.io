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
package com.cisco.qte.jdtn.ltp.udp;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Link;
import com.cisco.qte.jdtn.general.LinkAddress;
import com.cisco.qte.jdtn.general.Neighbor;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.ltp.EngineId;
import com.cisco.qte.jdtn.ltp.IPAddress;
import com.cisco.qte.jdtn.ltp.LtpNeighbor;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * UDP implementation for Neighbor, a directly accessible host on a link
 */
public class LtpUDPNeighbor extends LtpNeighbor {
	public static final String TYPE = "udp";
	
	@SuppressWarnings("unused")
	private static final Logger _logger =
		Logger.getLogger(LtpUDPNeighbor.class.getCanonicalName());
	
	/**
	 * Parse the given Parsing state, create a UDPNeighbor accordingly.  It is
	 * assumed that the parse is sitting on a &lt; Neighbor &gt; element and
	 * that caller has extracted generic Link attributes from it.  We extract
	 * merely the UDPNeighbor specific attributes.
	 * We do not advance the parser state at all.
	 * @param parser Parsing state
	 * @param engineId Configured EngineId of the Neighbor from super.parseNeighbor()
	 * @param name Unique name of neighbor
	 * @return New UDPNeighbor
	 * @throws IOException on I/O errors during parse
	 * @throws XMLStreamException On parse errors
	 * @throws JDtnException On UDPNeighbor specific parse errors
	 */
	public static LtpUDPNeighbor parseUdpNeighbor(
			XmlRDParser parser, 
			String name, 
			EngineId engineId) 
	throws IOException, XmlRdParserException, JDtnException {
		// No UDPNeighbor specific attributes
		LtpUDPNeighbor neighbor = new LtpUDPNeighbor(engineId, name);
		
		// For compatibility with Rev 1 config files
		String ipAddrStr = Utils.getStringAttribute(parser, "ipAddress");
		if (ipAddrStr != null) {
			IPAddress ipAddr = new IPAddress(ipAddrStr);
			LinkAddress linkAddress = new LinkAddress(null, ipAddr);
			neighbor.addLinkAddress(linkAddress);
		}
		return neighbor;
	}

	/**
	 * Constructor; sets EngineId and name to given arguments.
	 * Sets EndPointId to dtn:none.
	 * @param engineId Given EndineId
	 * @param name Given Name
	 */
	public LtpUDPNeighbor(EngineId engineId, String name) {
		super(engineId, name);
	}
	
	/**
	 * Constructor; sets EngineId and name to given arguments. Adds given
	 * LinkAddress.
	 * Sets EndPointId to dtn:none.
	 * @param engineId Given EndineId
	 * @param name Given Name
	 */
	public LtpUDPNeighbor(LinkAddress linkAddress, EngineId engineId, String name) {
		super(engineId, linkAddress, name);
	}
	
	/**
	 * Called from super when Neighbor is removed from parent Link.  We
	 * don't need to do anything.
	 */
	@Override
	protected void removeLinkImpl() {
		// Nothing
	}

	@Override
	public String toString() {
		return dump("", false);
	}
	
	@Override
	public void onNeighborScheduledStateChange(Neighbor neighbor,
			boolean neighborState) {
		// Nothing
	}

	@Override
	public Neighbor.NeighborType getType() {
		return NeighborType.NEIGHBOR_TYPE_LTP_UDP;
	}

	@Override
	public void onNeighborAdded(Link aLink, Neighbor neighbor) {
		// Nothing
	}

	@Override
	public void onNeighborDeleted(Link aLink, Neighbor neighbor) {
		// Nothing
	}

	@Override
	public void addLinkAddress(LinkAddress linkAddress) {
		if (!(linkAddress.getAddress() instanceof IPAddress)) {
			throw new InvalidParameterException("UDPNeighbors must have 'IPAddress' address");
		}
		super.addLinkAddress(linkAddress);
	}
	
}
