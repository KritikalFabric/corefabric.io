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
package com.cisco.qte.jdtn.tcpcl;

import java.io.IOException;
import java.io.PrintWriter;

import com.cisco.qte.jdtn.bp.BpTcpClAdapter;
import com.cisco.qte.jdtn.bp.EndPointId;
import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Link;
import com.cisco.qte.jdtn.general.LinksList;
import com.cisco.qte.jdtn.general.Neighbor;
import com.cisco.qte.jdtn.general.NeighborsList;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.ltp.IPAddress;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * Management component for TCP Convergence Layer
 */
public class TcpClManagement extends AbstractStartableComponent {

	private static TcpClManagement _instance = null;
	
	public static final long BLOCK_FILE_THRESHOLD_DEFAULT = 10000;
	public static final long BLOCK_FILE_THRESHOLD_MIN = 100;
	public static final long BLOCK_FILE_THRESHOLD_MAX = Long.MAX_VALUE;
	
	public static final boolean ACCEPT_UNCONFIG_NEIGHBORS_DEFAULT = true;
	
	private TcpClStats statistics = new TcpClStats();
	
	/** Payload length beyond which we will encode bundles into a file.
	 * XXX Not used */
	private long _blockFileThreshold = BLOCK_FILE_THRESHOLD_DEFAULT;
	
	/** Accept connections from unknown Neighbors */
	private boolean _acceptUnconfiguredNeighbors = 
		ACCEPT_UNCONFIG_NEIGHBORS_DEFAULT;
	
	public static TcpClManagement getInstance() {
		if (_instance == null) {
			_instance = new TcpClManagement();
		}
		return _instance;
	}
	
	private TcpClManagement() {
		super("TcpClManagement");
	}
	
	/**
	 * Set all configuration to default values
	 */
	public void setDefaults() {
		setBlockFileThreshold(BLOCK_FILE_THRESHOLD_DEFAULT);
		setAcceptUnconfiguredNeighbors(ACCEPT_UNCONFIG_NEIGHBORS_DEFAULT);
	}
	
	/**
	 * Parse TcpCl configuration.  It is assumed that the parse is sitting 
	 * on th &lt; TcpCl &gt; element.  We will parse past the
	 * &lt; /TcpCl &gt; tag.
	 * @param parser
	 * @throws JDtnException on JDTN specific errors
	 * @throws XmlStreamException on general parse errors
	 * @throws IOException on I/O errors
	 */
	public void parseConfig(XmlRDParser parser) 
	throws JDtnException, XmlRdParserException, IOException {
		Long blockFileThreshod = Utils.getLongAttribute(
				parser, 
				"blockFileThreshold", 
				BLOCK_FILE_THRESHOLD_MIN, 
				BLOCK_FILE_THRESHOLD_MIN);
		if (blockFileThreshod != null) {
			getInstance().setBlockFileThreshold(blockFileThreshod);
		}
		
		Boolean acceptUnconfigNeighbors = 
			Utils.getBooleanAttribute(parser, "acceptUnconfigNeighbors");
		if (acceptUnconfigNeighbors != null) {
			getInstance().setAcceptUnconfiguredNeighbors(acceptUnconfigNeighbors);
		}
		
		XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
		if (event != XmlRDParser.EventType.END_ELEMENT ||
			!parser.getElementTag().equals("TcpCl")) {
			throw new JDtnException("Expecting </TcpCl>; event=" + event);
		}
	}
	
	/**
	 * Output TcpCl configuration
	 * @param pw PrintWriter to write to
	 */
	public void writeConfig(PrintWriter pw) {
		pw.println("  <TcpCl");
		if (getBlockFileThreshold() != BLOCK_FILE_THRESHOLD_DEFAULT) {
			pw.println("    blockFileThreshold='" + getBlockFileThreshold() + "'");
		}
		if (isAcceptUnconfiguredNeighbors() != ACCEPT_UNCONFIG_NEIGHBORS_DEFAULT) {
			pw.println(
					"    acceptUnconfigNeighbors='" + 
					isAcceptUnconfiguredNeighbors() + "'");
		}
		pw.println("  />");
	}
	
	/**
	 * Start TcpCl protocol operations
	 */
	@Override
	protected void startImpl() {
		BpTcpClAdapter.getInstance().start();
		TcpClAPI.getInstance().start();
		for (Neighbor neighbor : NeighborsList.getInstance()) {
			if (neighbor instanceof TcpClNeighbor) {
				((TcpClNeighbor)neighbor).start();
			}
		}
	}
	
	/**
	 * Stop TcpCl protocol operations
	 * @throws InterruptedException 
	 */
	@Override
	protected void stopImpl() throws InterruptedException {
		for (Neighbor neighbor : NeighborsList.getInstance()) {
			if (neighbor instanceof TcpClNeighbor) {
				((TcpClNeighbor)neighbor).stop();
			}
		}
		TcpClAPI.getInstance().stop();
		BpTcpClAdapter.getInstance().stop();
	}

	/**
	 * Get TcpCl statistics
	 * @return Statistics
	 */
	public TcpClStats getStatistics() {
		return statistics;
	}
	
	/**
	 * Clear TcpCl statistics
	 */
	@Override
	public void clearStatistics() {
		statistics.clear();
	}
	
	/**
	 * Dump all TcpCl Neighbors
	 * @param indent Amount to indent
	 * @param detailed Whether detailed dump desired
	 * @return String containing dump
	 */
	public String dumpNeighbors(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "TcpCl Neighbors\n");
		for (Neighbor neighbor : NeighborsList.getInstance()) {
			if (neighbor instanceof TcpClNeighbor) {
				sb.append(neighbor.dump(indent + "  ", detailed));
			}
		}
		return sb.toString();
	}
	
	/**
	 * Dump all TcpCl Links
	 * @param indent Amount to indent
	 * @param detailed Whether detailed dump desired
	 * @return String containing dump
	 */
	public String dumpLinks(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "TcpCl Links\n");
		for (Link link : LinksList.getInstance()) {
			if (link instanceof TcpClLink) {
				sb.append(link.dump(indent + "  ", detailed));
			}
		}
		return sb.toString();
	}
	
	/**
	 * Dump TcpCl
	 * @param indent Amount to indent
	 * @param detailed Whether detailed dump desired
	 * @return String containing dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "TcpCl\n");
		sb.append(
				indent + "  AcceptUnconfiguredNeighbors=" + 
				isAcceptUnconfiguredNeighbors() + "\n");
		sb.append(dumpLinks(indent + "  ", detailed));
		sb.append(dumpNeighbors(indent + "  ", detailed));
		sb.append(statistics.dump(indent + "  ", detailed));
		sb.append(super.dump(indent + "  ", detailed));
		return sb.toString();
	}
	
	/**
	 * Add a TcpClLink to the system
	 * @param linkName Locally-unique name of the Link
	 * @param ifName System Network Interface name
	 * @param ipv6 Whether want IPV6 (true) or IPV4 (false)
	 * @return Link created and added
	 * @throws JDtnException on errors
	 */
	public TcpClLink addLink(
			String linkName, 
			String ifName,
			boolean ipv6) throws JDtnException {
		if (LinksList.getInstance().findLinkByName(linkName) != null) {
			throw new JDtnException("There is already a link named '" + linkName + "'");
		}
		TcpClLink link = TcpClLink.createTcpClLink(linkName, ifName, ipv6);
		LinksList.getInstance().addLink(link);
		return link;
	}
	
	/**
	 * Remove a TcpClLink from the system
	 * @param linkName Locally-unique name of the Link
	 * @throws JDtnException on errors
	 * @throws InterruptedException 
	 */
	public void removeLink(String linkName)
	throws JDtnException, InterruptedException {
		Link link = LinksList.getInstance().findLinkByName(linkName);
		if (link == null) {
			throw new JDtnException("No Link named '" + linkName + "'");
		}
		if (!(link instanceof TcpClLink)) {
			throw new JDtnException("Link '" + linkName + "' is not a TcpCl Link");
		}
		link.stop();
		LinksList.getInstance().removeLink(link);
	}
	
	/**
	 * Find a TcpClLink with the given link name
	 * @param linkName Given link name
	 * @return The corresponding TcpClLink or null if none
	 */
	public TcpClLink findLink(String linkName) {
		Link link = LinksList.getInstance().findLinkByName(linkName);
		if (link == null) {
			return null;
		}
		if (!(link instanceof TcpClLink)) {
			return null;
		}
		return (TcpClLink)link;
	}
	
	/**
	 * Find a TcpClNeighbor with the given IP Address
	 * @param ipAddress given IP Address
	 * @return The corresponding TcpClNeighbor or null if none
	 */
	public TcpClNeighbor findNeighbor(IPAddress ipAddress) {
		Neighbor neighbor = 
			NeighborsList.getInstance().findNeighborByAddress(ipAddress);
		if (neighbor == null) {
			return null;
		}
		if (!(neighbor instanceof TcpClNeighbor)) {
			return null;
		}
		return (TcpClNeighbor)neighbor;
	}
	
	/**
	 * Add a TcpClNeighbor to the system
	 * @param neighborName Locally-unique name of the Neighbor
	 * @param eid EndpointID of the Neighbor (null if not yet known)
	 * @return Neighbor created and added
	 * @throws JDtnException on errors
	 */
	public TcpClNeighbor addNeighbor(String neighborName, EndPointId eid)
	throws JDtnException {
		if (NeighborsList.getInstance().findNeighborByName(neighborName) != null) {
			throw new JDtnException("Already a Neighbor named '" + neighborName + "'");
		}
		TcpClNeighbor neighbor = new TcpClNeighbor(neighborName);
		if (eid != null) {
			neighbor.setEndPointIdStem(eid);
		}
		NeighborsList.getInstance().addNeighbor(neighbor);
		return neighbor;
	}
	
	/**
	 * Remove a TcpClNeighbor from the system
	 * @param neighborName Locally-unique name of the Neighbor
	 * @throws JDtnException on errors
	 */
	public void removeNeighbor(String neighborName)
	throws JDtnException {
		Neighbor neighbor = 
			NeighborsList.getInstance().findNeighborByName(neighborName);
		if (neighbor == null) {
			throw new JDtnException(
					"No Neighbor named '" + neighborName + "'");
		}
		if (!(neighbor instanceof TcpClNeighbor)) {
			throw new JDtnException(
					"Neighbor '" + neighborName + "' is not a TcpCl Neighbor");
		}
		NeighborsList.getInstance().removeNeighbor(neighbor);
	}

	/** Payload length beyond which we will encode bundles into a file.
	 * XXX Not used */
	public long getBlockFileThreshold() {
		return _blockFileThreshold;
	}

	/** Payload length beyond which we will encode bundles into a file.
	 * XXX Not used */
	public void setBlockFileThreshold(long aBlockFileThreshold) {
		_blockFileThreshold = aBlockFileThreshold;
	}

	/** Accept connections from unknown Neighbors */
	public boolean isAcceptUnconfiguredNeighbors() {
		return _acceptUnconfiguredNeighbors;
	}

	/** Accept connections from unknown Neighbors */
	public void setAcceptUnconfiguredNeighbors(
			boolean acceptUnconfiguredNeighbors) {
		_acceptUnconfiguredNeighbors = acceptUnconfiguredNeighbors;
	}
	
}
