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
package com.cisco.qte.jdtn.udpcl;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.bp.BpUdpClAdapter;
import com.cisco.qte.jdtn.bp.EndPointId;
import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Link;
import com.cisco.qte.jdtn.general.LinksList;
import com.cisco.qte.jdtn.general.Neighbor;
import com.cisco.qte.jdtn.general.NeighborsList;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * Management component for Udp Convergence Layer
 */
public class UdpClManagement extends AbstractStartableComponent {
	private static final Logger _logger =
		Logger.getLogger(UdpClManagement.class.getCanonicalName());
	
	private static UdpClManagement _instance = null;
	
	// Default value for blockFileThreshold property
	public static final long BLOCK_FILE_THRESHOLD_DEFAULT = 10000;
	// Minimum value for blockFileThreshold property
	public static final long BLOCK_FILE_THRESHOLD_MIN = 100;
	// Maximum value for blockFileThreshold property
	public static final long BLOCK_FILE_THRESHOLD_MAX = Long.MAX_VALUE;
	
	private static UdpClStats statistics = new UdpClStats();
	
	/** Payload length beyond which we will encode bundles into a file.
	 * XXX not used */
	private long blockFileThreshold = BLOCK_FILE_THRESHOLD_DEFAULT;
	
	public static UdpClManagement getInstance() {
		if (_instance == null) {
			_instance = new UdpClManagement();
		}
		return _instance;
	}
	
	private UdpClManagement() {
		super("UdpClManagement");
	}
	
	/**
	 * Set all configuration to default values
	 */
	public void setDefaults() {
		setBlockFileThreshold(BLOCK_FILE_THRESHOLD_DEFAULT);
	}
	
	/**
	 * Parse UdpCl configuration.  It is assumed that the parse is sitting 
	 * on th &lt; UdpCl &gt; element.  We collect all UdpCl specific properties
	 * specified, and set our own properties accordingly.  We then parse past the
	 * &lt; /UdpCl &gt; tag.
	 * @param parser XML Parser
	 * @throws JDtnException on JDTN specific errors
	 * @throws XmlStreamException on general parsing errors
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
			_logger.warning("The 'blockFileThreshold' property of UdpCL is not currently used");
			setBlockFileThreshold(blockFileThreshold);
		}
		
		XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
		if (event != XmlRDParser.EventType.END_ELEMENT ||
			!parser.getElementTag().equals("UdpCl")) {
			throw new JDtnException("Expecting </UdpCl>; event=" + event);
		}
	}
	
	/**
	 * Output UdpCl configuration
	 * @param pw PrintWriter to write to
	 */
	public void writeConfig(PrintWriter pw) {
		pw.println("  <UdpCl");
		if (getBlockFileThreshold() != BLOCK_FILE_THRESHOLD_DEFAULT) {
			pw.println("    blockFileThreshold='" + getBlockFileThreshold() + "'");
		}
		pw.println("  />");
	}
	
	/**
	 * Start UdpCl protocol operations
	 */
	@Override
	protected void startImpl() {
		BpUdpClAdapter.getInstance().start();
		UdpClAPI.getInstance().start();
		for (Neighbor neighbor : NeighborsList.getInstance()) {
			if (neighbor instanceof UdpClNeighbor) {
				((UdpClNeighbor)neighbor).start();
			}
		}
	}
	
	/**
	 * Stop UdpCl protocol operations
	 * @throws InterruptedException 
	 */
	@Override
	protected void stopImpl() throws InterruptedException {
		for (Neighbor neighbor : NeighborsList.getInstance()) {
			if (neighbor instanceof UdpClNeighbor) {
				((UdpClNeighbor)neighbor).stop();
			}
		}
		UdpClAPI.getInstance().stop();
		BpUdpClAdapter.getInstance().stop();
	}

	/**
	 * Get UdpCl statistics
	 * @return Statistics
	 */
	public static UdpClStats getStatistics() {
		return statistics;
	}
	
	/**
	 * Clear UdpCl statistics
	 */
	@Override
	public void clearStatistics() {
		statistics.clear();
	}
	
	/**
	 * Dump all UdpCl Neighbors
	 * @param indent Amount to indent
	 * @param detailed Whether detailed dump desired
	 * @return String containing dump
	 */
	public String dumpNeighbors(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "UdpCl Neighbors\n");
		for (Neighbor neighbor : NeighborsList.getInstance()) {
			if (neighbor instanceof UdpClNeighbor) {
				sb.append(neighbor.dump(indent + "  ", detailed));
			}
		}
		return sb.toString();
	}
	
	/**
	 * Dump all UdpCl Links
	 * @param indent Amount to indent
	 * @param detailed Whether detailed dump desired
	 * @return String containing dump
	 */
	public String dumpLinks(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "UdpCl Links\n");
		for (Link link : LinksList.getInstance()) {
			if (link instanceof UdpClLink) {
				sb.append(link.dump(indent + "  ", detailed));
			}
		}
		return sb.toString();
	}
	
	/**
	 * Dump UdpCl
	 * @param indent Amount to indent
	 * @param detailed Whether detailed dump desired
	 * @return String containing dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "UdpCl\n");
		sb.append(dumpLinks(indent + "  ", detailed));
		sb.append(dumpNeighbors(indent + "  ", detailed));
		sb.append(statistics.dump(indent + "  ", detailed));
		sb.append(super.dump(indent + "  ", detailed));
		return sb.toString();
	}
	
	/**
	 * Add a UdpClLink to the system
	 * @param linkName Locally-unique name of the Link
	 * @param ifName System Network Interface name
	 * @param ipv6 Whether want IPV6 (true) or IPV4 (false)
	 * @return Link created and added
	 * @throws JDtnException on errors
	 */
	public UdpClLink addLink(
			String linkName, 
			String ifName,
			boolean ipv6) throws JDtnException {
		if (LinksList.getInstance().findLinkByName(linkName) != null) {
			throw new JDtnException("There is already a link named '" + linkName + "'");
		}
		UdpClLink link = UdpClLink.createUdpClLink(linkName, ifName, ipv6);
		LinksList.getInstance().addLink(link);
		return link;
	}
	
	/**
	 * Remove a UdpClLink from the system
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
		if (!(link instanceof UdpClLink)) {
			throw new JDtnException("Link '" + linkName + "' is not a UdpCl Link");
		}
		link.stop();
		LinksList.getInstance().removeLink(link);
	}
	
	/**
	 * Add a UdpClNeighbor to the system
	 * @param neighborName Locally-unique name of the Neighbor
	 * @param eid EndpointID of the Neighbor (null if not yet known)
	 * @return Neighbor created and added
	 * @throws JDtnException on errors
	 */
	public UdpClNeighbor addNeighbor(String neighborName, EndPointId eid)
	throws JDtnException {
		if (NeighborsList.getInstance().findNeighborByName(neighborName) != null) {
			throw new JDtnException("Already a Neighbor named '" + neighborName + "'");
		}
		UdpClNeighbor neighbor = new UdpClNeighbor(neighborName);
		if (eid != null) {
			neighbor.setEndPointIdStem(eid);
		}
		NeighborsList.getInstance().addNeighbor(neighbor);
		return neighbor;
	}
	
	/**
	 * Remove a UdpClNeighbor from the system
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
		if (!(neighbor instanceof UdpClNeighbor)) {
			throw new JDtnException(
					"Neighbor '" + neighborName + "' is not a UdpCl Neighbor");
		}
		NeighborsList.getInstance().removeNeighbor(neighbor);
	}

	/** XXX not used */
	public long getBlockFileThreshold() {
		return blockFileThreshold;
	}

	/** XXX not used */
	public void setBlockFileThreshold(long aBlockFileThreshold) {
		blockFileThreshold = aBlockFileThreshold;
	}
	

}
