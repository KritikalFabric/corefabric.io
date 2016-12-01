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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Link;
import com.cisco.qte.jdtn.general.LinkAddress;
import com.cisco.qte.jdtn.general.LinksList;
import com.cisco.qte.jdtn.general.Neighbor;
import com.cisco.qte.jdtn.general.NeighborsList;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.ltp.udp.LtpUDPLink;
import com.cisco.qte.jdtn.ltp.udp.LtpUDPNeighbor;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * LTP Management Interface.
 * Supports the following configuration properties:
 * <ul>
 *   <li> EngineId - The LTP EngineId of this LTP endpoint.  The EngineId
 *   uniquely identifies this LTP endpoint among all other endpoints it might
 *   come in contact with.
 *   <li>TestInterface - The name of a network interface on this host which is 
 *   used in unit testing.
 *   <li>LtpUdpRecvBufferSize - Socket receiver buffer size for Ltp over Udp
 *   Links.
 *   <li>LtpUdpPort - Udp port number for Ltp over Udp sockets
 *   <li>LtpMaxRetransmits - Maximum number of retransmits of Blocks with
 *   timed out receipt of Report Segment before declaring the Block undeliverable.
 *   <li>LtpMaxReportRetransmits - Maximum number of retransmits of Report
 *   Segments with timed out receipt of Report Ack before decaring Report
 *   Segment undeliverable.
 * </ul>
 * <p>
 * Also supports addition and deletion of:
 * <ul>
 *   <li>UDP Links - an instance of Ltp over Udp over a network interface.
 *   <li>UDP Neighbor - an Ltp endpoint directly reachable from this endpoint.
 * </ul>
 * These properties are made persistent via the Configuration component.
 */
public class LtpManagement extends AbstractStartableComponent {

	private static final Logger _logger =
		Logger.getLogger(LtpManagement.class.getCanonicalName());
	
	private static LtpManagement _instance = null;
	
	/** Min value for LtpMaxReportRetransmits property */
	public static final int MIN_LTP_MAX_REPORT_RETRANSMITS = 1;
	/** Max value for LtpMaxReportRetransmits property */
	public static final int MAX_LTP_MAX_REPORT_RETRANSMITS = 64;
	/** Default value for LtpMaxReportRetransmits property */
	public static final int LTP_MAX_REPORT_RETRANSMISSIONS_DEFAULT = 8;

	/** Min value for LtpMaxRetransmits property */
	public static final int MIN_LTP_MAX_RETRANSMITS = 1;
	/** Max value for LtpMaxRetransmits property */
	public static final int MAX_LTP_MAX_RETRANSMITS = 64;
	/** Default value for LtpMaxRetransmits property */
	public static final int LTP_MAX_RETRANSMISSIONS_DEFAULT = 60;

	/** Default value for LtpMaxCancelRetransmits property */
	public static final int LTP_MAX_CANCEL_RETRANSMISSIONS_DEFAULT = 8;
	
	/** Min value for LtpUdpRecvBufferSize property */
	public static final int MIN_LTP_UDP_RECV_BUF_SIZE = 8192;
	/** Max value for LtpUdpRecvBufferSize property */
	public static final int MAX_LTP_UDP_RECV_BUF_SIZE = Integer.MAX_VALUE;
	/** Default value for LtpUdpRecvBufferSize property */
	public static final int LTP_UDP_RECV_BUFFER_DEFAULT = 2000000;

	/** Min value for LtpUdpPort property */
	public static final int MIN_LTP_UDP_PORT = 64;
	/** Max value for LtpUdpPort property */
	public static final int MAX_LTP_UDP_PORT = 65507;
	/**
	 * 10.1.  UDP Port Number for LTP
	 *  The UDP port number 1113 with the name "ltp-deepspace" has been
	 *  reserved for LTP deployments.  An LTP implementation may be
	 *  implemented to operate over UDP datagrams using this port number for
	 *  study and testing over the Internet.
	 */
	public static final int LTP_UDP_PORT_DEFAULT = 1113;
	
	/** Min value for MinBlockLenFileThreshold property */
	public static final int MIN_BLOCK_LEN_FILE_THRESHOLD = 0;
	/** Max value for MinBlockLenFileThreshold property */
	public static final int MAX_BLOCK_LEN_FILE_THRESHOLD = Integer.MAX_VALUE;
	/** Default value for MinBlockLenFileThreshold property */
	public static final int BLOCK_LEN_FILE_THRESHOLD_DEFAULT = 1000; 

	/** Min value for MinSegmentLenFileThreshold property */
	public static final int MIN_SEGMENT_LEN_FILE_THRESHOLD = 0;
	/** Max value for MinSegmentLenFileThreshold property */
	public static final int MAX_SEGMENT_LEN_FILE_THRESHOLD = Integer.MAX_VALUE;
	/** Default value for MinSegmentLenFileThreshold property */
	public static final int SEGMENT_LEN_FILE_THRESHOLD_DEFAULT = 1000;
	
	/** Min value for SessionReceptionProblemsLimit property */
	public static final int MIN_SESSION_RECEPTION_PROBLEMS_LIMIT = 0;
	/** Max value for SessionReceptionProblemsLimit property */
	public static final int MAX_SESSION_RECEPTION_PROBLEMS_LIMIT = Integer.MAX_VALUE;
	/** Default value for SessionReceptionProblemsLimit property */
	public static final int SESSION_RECEPTION_PROBLEMS_LIMIT_DEFAULT = 10;

	/** Default value for TestInterface property for Max OS/X and Windows */
	private static final String TEST_INTERFACE_DEFAULT = "lo0";
	/** Default value for TestInterface property for Linux */
	private static final String TEST_INTERFACE_DEFAULT_LINUX = "lo";
	/** Default value for TestInterface property for Windows */
	private static final String TEST_INTERFACE_DEFAULT_WINDOWS = "lo";
	
	/** Default for Log Link Operational State Changes */
	private static final boolean LOG_LINK_OPER_STATE_CHANGES_DEFAULT = true;
	
	/**
	 * Engine ID for this LTP instance
	 */
	private EngineId _engineId;
	
	/**
	 * Max number of retransmissions for Checkpoints
	 */
	private int _ltpMaxRetransmits = LTP_MAX_RETRANSMISSIONS_DEFAULT;
	
	/**
	 * Max number of retransmissions for Report Segments
	 */
	private int _ltpMaxReportRetransmits = LTP_MAX_REPORT_RETRANSMISSIONS_DEFAULT;
	
	/**
	 * Max number of retransmissions of Cancel Segments
	 */
	private int _ltpMaxCancelRetransmits = LTP_MAX_CANCEL_RETRANSMISSIONS_DEFAULT;
	
	/**
	 * UDP Port number for LTP over UDP
	 */
	private int _ltpUdpPort = LTP_UDP_PORT_DEFAULT;
	
	/**
	 * UDP Receive buffer size
	 */
	private int _ltpUdpRecvBufferSize = LTP_UDP_RECV_BUFFER_DEFAULT;
	
	/**
	 * Interface name to be used in unit testing 
	 */
	private String _testInterface = TEST_INTERFACE_DEFAULT;
	
	/**
	 * Block length threshold to decide whether Block should be in memory or
	 * in a File.
	 */
	private int _blockLengthFileThreshold = BLOCK_LEN_FILE_THRESHOLD_DEFAULT;
	
	/**
	 * Segment length threshold to decide whether Segment should be in memory or
	 * in a File
	 */
	private int _segmentLengthFileThreshold = SEGMENT_LEN_FILE_THRESHOLD_DEFAULT;
	
	/**
	 * Limit on number of session reception problems before Session cancelled
	 */
	private int _sessionReceptionProblemsLimit = SESSION_RECEPTION_PROBLEMS_LIMIT_DEFAULT;
	
	/**
	 * Log Link operational state changes as Logger warnings
	 */
	private boolean _logLinkOperStateChanges = LOG_LINK_OPER_STATE_CHANGES_DEFAULT;
	
	/** LTP Statistics */
	protected LtpStats _ltpStats = new LtpStats();
	
	public static LtpManagement getInstance() {
		if (_instance == null) {
			_instance = new LtpManagement();
		}
		return _instance;
	}
	
	private LtpManagement() {
		super("LtpManagement");
	}
	
	/**
	 * Set all parameters to defaults
	 */
	public void setDefaults() {
		try {
			setEngineId(EngineId.getDefaultEngineId());
			setLtpUdpPort(LTP_UDP_PORT_DEFAULT);
			setLtpUdpRecvBufferSize(LTP_UDP_RECV_BUFFER_DEFAULT);
			setLtpMaxRetransmits(LTP_MAX_RETRANSMISSIONS_DEFAULT);
			setLtpMaxCancelRetransmits(LTP_MAX_CANCEL_RETRANSMISSIONS_DEFAULT);
			setLtpMaxReportRetransmits(LTP_MAX_REPORT_RETRANSMISSIONS_DEFAULT);
			setBlockLengthFileThreshold(BLOCK_LEN_FILE_THRESHOLD_DEFAULT);
			setSessionReceptionProblemsLimit(SESSION_RECEPTION_PROBLEMS_LIMIT_DEFAULT);
			LinksList.getInstance().removeAllLinks();
			NeighborsList.getInstance().removeAllNeighbors();
			
			if (System.getProperty("os.name").contains("Linux")) {
				setTestInterface(TEST_INTERFACE_DEFAULT_LINUX);
			} else if (System.getProperty("os.name").contains("Mac OS X")) {
				setTestInterface(TEST_INTERFACE_DEFAULT);
			} else if (System.getProperty("os.name").contains("Windows")) {
				setTestInterface(TEST_INTERFACE_DEFAULT_WINDOWS);
			}
			
			setLogLinkOperStateChanges(LOG_LINK_OPER_STATE_CHANGES_DEFAULT);
			
		} catch (LtpException e) {
			_logger.log(Level.SEVERE, "setDefaults()", e);
		} catch (InterruptedException e) {
			_logger.log(Level.SEVERE, "setDefaults()", e);
		}
	}
	
	/**
	 * Parse the LTP section of the config file.  It is assumed that the parse
	 * is currently sitting on the &lt; LTP &gt; element.  We parse the &lt; LTP &gt; element,
	 * embedded &lt; Links &gt; element, and the &lt; /LTP &gt;.
	 * @param parser Parser doing parsing
	 * @throws JDtnException On LTP specific parsing errors
	 * @throws XmlPullParserException On general parsing errors
	 * @throws IOException On I/O errors during parsing
	 * @throws InterruptedException 
	 */
	public void parse(XmlRDParser parser) 
	throws JDtnException, XmlRdParserException, IOException, InterruptedException {
		// <LTP
		//    engineId="engineId"
		//    ltpUdpPort="port"
		//    ltpUdpRecvBufSize="size"
		//    testInterface="ifName"
		//    ltpMaxRetransmits="n"
		//    ltpMaxReportRetransmits="n"
		//    ltpMaxCancelRetransmits='n'
		//    ltpBlockLengthFileThreshold="n"
		//    ltpSegmentLengthFileThreshold="n"
		//    sessionReceptionProblemsLimit="n"
		// >
		//    <Links ... >
		//    <Neighbors ... >
		// </LTP>
		String value = parser.getAttributeValue("engineId");
		if (value != null && value.length() > 0) {
			EngineId eid = new EngineId(value);
			setEngineId(eid);
		}
		
		Integer intValue = null;
		
		intValue = Utils.getIntegerAttribute(
				parser, "ltpUdpPort", 
				MIN_LTP_UDP_PORT, MAX_LTP_UDP_PORT);
		if (intValue != null) {
			setLtpUdpPort(intValue.intValue());
		}
		
		intValue = Utils.getIntegerAttribute(
				parser, "ltpUdpRecvBufSize", 
				MIN_LTP_UDP_RECV_BUF_SIZE, MAX_LTP_UDP_RECV_BUF_SIZE);
		if (intValue != null) {
			setLtpUdpRecvBufferSize(intValue.intValue());
		}

		intValue = Utils.getIntegerAttribute(
				parser, "ltpMaxRetransmits", 
				MIN_LTP_MAX_RETRANSMITS, MAX_LTP_MAX_RETRANSMITS);
		if (intValue != null) {
			setLtpMaxRetransmits(intValue.intValue());
		}

		intValue = Utils.getIntegerAttribute(
				parser, 
				"ltpMaxCancelRetransmits", 
				0, 
				Integer.MAX_VALUE);
		if (intValue != null) {
			setLtpMaxCancelRetransmits(intValue.intValue());
		}
		
		intValue = Utils.getIntegerAttribute(
				parser, "ltpMaxReportRetransmits", 
				MIN_LTP_MAX_REPORT_RETRANSMITS, MAX_LTP_MAX_REPORT_RETRANSMITS);
		if (intValue != null) {
			setLtpMaxReportRetransmits(intValue.intValue());
		}
		
		value = parser.getAttributeValue("testInterface");
		if (value != null && value.length() > 0) {
			setTestInterface(value);
		}
		
		intValue = Utils.getIntegerAttribute(
				parser, "ltpBlockLengthFileThreshold", 
				MIN_BLOCK_LEN_FILE_THRESHOLD, MAX_BLOCK_LEN_FILE_THRESHOLD);
		if (intValue != null) {
			setBlockLengthFileThreshold(intValue.intValue());
		}
		
		intValue = Utils.getIntegerAttribute(
				parser, "ltpSegmentLengthFileThreshold",
				MIN_SEGMENT_LEN_FILE_THRESHOLD, MAX_SEGMENT_LEN_FILE_THRESHOLD);
		if (intValue != null) {
			setSegmentLengthFileThreshold(intValue.intValue());
		}
		
		intValue = Utils.getIntegerAttribute(
				parser, "sessionReceptionProblemsLimit", 
				MIN_SESSION_RECEPTION_PROBLEMS_LIMIT, 
				MAX_SESSION_RECEPTION_PROBLEMS_LIMIT);
		if (intValue != null) {
			setSessionReceptionProblemsLimit(intValue.intValue());
		}
		
		Boolean boolValue = Utils.getBooleanAttribute(
				parser, "logLinkOperStateChanges");
		if (boolValue != null) {
			setLogLinkOperStateChanges(boolValue.booleanValue());
		}
		
		XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
		
		while (event == XmlRDParser.EventType.START_ELEMENT) {
			if (parser.getElementTag().equals("Links")) {
				LinksList.getInstance().parseLinks(parser);
			} else if (parser.getElementTag().equals("Neighbors")) {
				NeighborsList.getInstance().parseNeighbors(parser);
			} else {
				throw new LtpException("Unrecognized start of element: " +
					parser.getElementTag());
			}
			
			event = Utils.nextNonTextEvent(parser);
		}
		
		if (event != XmlRDParser.EventType.END_ELEMENT ||
			!parser.getElementTag().equals("LTP")) {
			throw new LtpException("Expecting </LTP>");
		}
	}
	
	/**
	 * Write LTPConfig to the given PrintWriter in the form of a &lt; LTP &gt; elemement
	 * and embedded &lt; Links &gt; element.
	 * @param pw Given PrintWriter
	 */
	public void writeConfig(PrintWriter pw) {
		pw.println("  <LTP");
		pw.println("    engineId='" + getEngineId().getEngineIdString() + "'");
		if (getLtpUdpPort() != LTP_UDP_PORT_DEFAULT) {
			pw.println("    ltpUdpPort='" + getLtpUdpPort() + "'");
		}
		if (getLtpUdpRecvBufferSize() != LTP_UDP_RECV_BUFFER_DEFAULT) {
			pw.println("    ltpUdpRecvBufSize='" + getLtpUdpRecvBufferSize() + "'");
		}
		if (getLtpMaxRetransmits() != LTP_MAX_RETRANSMISSIONS_DEFAULT) {
			pw.println("    ltpMaxRetransmits='" + getLtpMaxRetransmits() + "'");
		}
		if (getLtpMaxCancelRetransmits() != LTP_MAX_CANCEL_RETRANSMISSIONS_DEFAULT) {
			pw.println("    ltpMaxCancelRetransmits='" + getLtpMaxCancelRetransmits() + "'");
		}
		if (getLtpMaxReportRetransmits() != LTP_MAX_REPORT_RETRANSMISSIONS_DEFAULT) {
			pw.println("    ltpMaxReportRetransmits='" + getLtpMaxReportRetransmits() + "'");
		}
		pw.println("    testInterface='" + getTestInterface() + "'");
		if (getBlockLengthFileThreshold() != BLOCK_LEN_FILE_THRESHOLD_DEFAULT) {
			pw.println("    ltpBlockLengthFileThreshold='" + getBlockLengthFileThreshold() + "'");
		}
		if (getSegmentLengthFileThreshold() != SEGMENT_LEN_FILE_THRESHOLD_DEFAULT) {
			pw.println("    ltpSegmentLengthFileThreshold='" + getSegmentLengthFileThreshold() + "'");
		}
		if (getSessionReceptionProblemsLimit() != SESSION_RECEPTION_PROBLEMS_LIMIT_DEFAULT) {
			pw.println("    sessionReceptionProblemsLimit='" + getSessionReceptionProblemsLimit() + "'");
		}
		if (isLogLinkOperStateChanges() != LOG_LINK_OPER_STATE_CHANGES_DEFAULT) {
			pw.println("    logLinkOperStateChanges='" + isLogLinkOperStateChanges() + "'");
		}
		
		pw.println("  >");
		LinksList.getInstance().writeConfig(pw);
		NeighborsList.getInstance().writeConfig(pw);
		pw.println("  </LTP>");
	}

	/**
	 * Dump the state of this object
	 * @param indent Amount of indentation
	 * @param detailed True if want verbose details
	 * @return String containing dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "LtpManagement\n");
		sb.append(_engineId.dump(indent + "  ", detailed));
		sb.append(indent + "  ltpUdpPort=" + _ltpUdpPort + "\n");
		sb.append(indent + "  ltpUdpRecvBufSize=" + _ltpUdpRecvBufferSize + "\n");
		sb.append(indent + "  ltpMaxRetransmits=" + _ltpMaxRetransmits + "\n");
		sb.append(indent + "  ltpMaxCancelRetransmits=" + _ltpMaxCancelRetransmits + "\n");
		sb.append(indent + "  ltpMaxReportRetransmits=" + _ltpMaxReportRetransmits + "\n");
		sb.append(indent + "  testInterface=" + _testInterface + "\n");
		sb.append(indent + "  ltpBlockLengthFileThreshold=" + _blockLengthFileThreshold + "\n");
		sb.append(indent + "  ltpSegmentLengthFileThreshold=" + _segmentLengthFileThreshold + "\n");
		sb.append(indent + "  sessionReceptionProblemsLimit=" + _sessionReceptionProblemsLimit + "\n");
		sb.append(indent + "  logLinkOperStateChanges=" + isLogLinkOperStateChanges() + "\n");
		sb.append(LinksList.getInstance().dump(indent + "  ", detailed));
		sb.append(NeighborsList.getInstance().dump(indent + "  ", detailed));
		sb.append(LtpApi.getInstance().dump(indent + "  ", detailed));
		sb.append(_ltpStats.dump(indent + "  ", true));
		sb.append(super.dump(indent + "  ", true));
		return sb.toString();
	}
	
	/**
	 * Dump the LTP Block queues
	 * @param indent Amount of indentation
	 * @param detailed True if want verbose details
	 * @return String containing dump
	 */
	public String dumpBlocks(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "LtpManagement\n");
		sb.append(LtpInbound.getInstance().dump(indent + "  ", detailed));
		sb.append(LtpOutbound.getInstance().dump(indent + "  ", detailed));
		return sb.toString();
	}
	
	/**
	 * Create or find a UDPLink matching the given parameters
	 * @param linkName User assigned name of the UDPLink
	 * @param ifName System network interface name for the UDPLink
	 * @param wantIPV6 True if want IPV6 UDPLink, otherwise IPV4
	 * @return The UDPLink created
	 * @throws LtpException 
	 */
	public LtpUDPLink addUDPLink(String linkName, String ifName, boolean wantIPV6) 
	throws LtpException {
		LtpUDPLink udpLink = LtpUDPLink.createUDPLink(linkName, ifName, wantIPV6);
		LinksList.getInstance().addLink(udpLink);
		udpLink.start();
		return udpLink;
	}
	
	/**
	 * Remove given UDPLink
	 * @param udpLink Given UDPLink
	 * @throws InterruptedException 
	 */
	public void removeLink(LtpLink udpLink)
	throws JDtnException, InterruptedException {
		udpLink.stop();
		LinksList.getInstance().removeLink(udpLink);
	}
	
	/**
	 * Get the UDPNeighbor on the given UDPLink which matches given IPAddress
	 * @param link Given UDPLink
	 * @param ipAddress Given IPAddress
	 * @return The matching UDPNeighbor or null if none
	 */
	public LtpUDPNeighbor findUDPNeighbor(
			LtpUDPLink link, 
			IPAddress ipAddress) {
		LtpUDPNeighbor neighbor = LtpUDPLink.findNeighborByAddressAndLink(link, ipAddress);
		return neighbor;
	}
	
	/**
	 * Add a UDPNeighbor (no IPAddress, no associated Link)
	 * @param eid Given EngineId
	 * @param name Name of the Neighbor
	 * @return UDPNeighbor created
	 * @throws JDtnException if such a UDPNeighbor already exists
	 */
	public LtpUDPNeighbor addUDPNeighbor(
			EngineId eid,
			String name) 
	throws JDtnException {
		return addUDPNeighbor(null, null, eid, name);
	}
	/**
	 * Add a UDPNeighbor with given IPAddress
	 * @param ipAddress Given IpAddress
	 * @param eid Given EngineId
	 * @param name Name of the Neighbor
	 * @return UDPNeighbor created
	 * @throws JDtnException if such a UDPNeighbor already exists
	 */
	public LtpUDPNeighbor addUDPNeighbor(
			IPAddress ipAddress,
			EngineId eid,
			String name) 
	throws JDtnException {
		return addUDPNeighbor(null, ipAddress, eid, name);
	}
	
	/**
	 * Add a UDPNeighbor with given IPAddress reachable on the given UDPLink
	 * @param link Given UDPLink
	 * @param ipAddress Given IpAddress
	 * @param eid Given EngineId
	 * @param name Name of the Neighbor
	 * @return UDPNeighbor created
	 * @throws JDtnException if such a UDPNeighbor already exists
	 */
	public LtpUDPNeighbor addUDPNeighbor(
			LtpUDPLink link, 
			IPAddress ipAddress,
			EngineId eid,
			String name) 
	throws JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("addUDPNeighbor(Name=" + name + ", EngineId=" + eid);
			if (link != null) {
				_logger.fine("  link=" + link.getName());
			}
			if (ipAddress != null) {
				_logger.fine("  IPAddress=" + ipAddress.toParseableString());
			}
		}
		
		LtpUDPNeighbor neighbor = new LtpUDPNeighbor(eid, name);
		NeighborsList.getInstance().addNeighbor(neighbor);
		LinkAddress linkAddress = new LinkAddress(link, ipAddress);
		neighbor.addLinkAddress(linkAddress);
		return neighbor;
	}
	
	/**
	 * Remove given UDPNeighbor
	 * @param neighbor Given UDPNeighbor
	 * @throws JDtnException If such a UDPNeighbor doesn't exist
	 */
	public void removeUDPNeighbor(LtpUDPNeighbor neighbor) 
	throws JDtnException {
		NeighborsList.getInstance().removeNeighbor(neighbor);
	}

	/**
	 * Find the Link with the given name
	 * @param linkName Given name
	 */
	public LtpLink findLtpLink(String linkName) {
		Link link = LinksList.getInstance().findLinkByName(linkName);
		if (link != null && link instanceof LtpLink) {
			return (LtpLink)link;
		}
		return null;
	}
	
	/**
	 * Search all Links to find a Neighbor with given name.
	 * @param neighborName Given Neighbor Name
	 * @return Neighbor found or null if none.  Note that there is nothing that
	 * prevents different Links from having Neighbors of the same name.  If that
	 * is the case, this will be the first one found.
	 */
	public LtpNeighbor findNeighbor(String neighborName) {
		Neighbor neighbor = NeighborsList.getInstance().findNeighborByName(
				neighborName);
		if (neighbor != null && neighbor instanceof LtpNeighbor) {
			return (LtpNeighbor)neighbor;
		}
		return null;
	}
	
	/**
	 * Get a UDPLink with the given name
	 * @param linkName Given name
	 */
	public LtpUDPLink getUDPLink(String linkName) {
		LtpLink link = findLtpLink(linkName);
		if (link != null && link instanceof LtpUDPLink) {
			return (LtpUDPLink)link;
		}
		return null;
	}
	
	/**
	 * Get list of Links configured on this LTP Instance
	 * @return What I said
	 */
	public LinksList getLinksList() {
		return LinksList.getInstance();
	}
	
	/**
	 * Startup Ltp Operations
	 */
	@Override
	protected void startImpl() {
		LinksList.getInstance().start();
		NeighborsList.getInstance().start();
		LtpApi.getInstance().start();
		LtpOutbound.getInstance().start();
		LtpInbound.getInstance().start();
	}
	
	/**
	 * Shutdown LTP Operations
	 * @throws InterruptedException 
	 */
	@Override
	protected void stopImpl() throws InterruptedException {
		NeighborsList.getInstance().stop();
		LinksList.getInstance().stop();
		LtpApi.getInstance().stop();
		LtpOutbound.getInstance().stop();
		LtpInbound.getInstance().stop();
	}
	
	/**
	 * Engine ID for this LTP instance
	 */
	public EngineId getEngineId() {
		return _engineId;
	}

	/**
	 * Engine ID for this LTP instance
	 */
	public void setEngineId(EngineId aEngineId) {
		_engineId = aEngineId;
	}

	/**
	 * Max number of retransmissions for Checkpoints
	 */
	public int getLtpMaxRetransmits() {
		return _ltpMaxRetransmits;
	}

	private int setIntValue(String label, int aIntVal, int minValue, int maxValue) 
	throws LtpException {
		if (aIntVal < minValue || aIntVal > maxValue) {
			throw new LtpException(
					"Value " + aIntVal +
					" for '" + label + 
					"' is out of range [" + minValue +
					", " + maxValue +
					"]");
		}
		return aIntVal;
	}
	
	/**
	 * Max number of retransmissions for Checkpoints
	 * @throws LtpException 
	 */
	public void setLtpMaxRetransmits(int aLtpMaxRetransmits) 
	throws LtpException {
		_ltpMaxRetransmits = 
			setIntValue(
					"Ltp Max Retransmits", 
					aLtpMaxRetransmits, 
					MIN_LTP_MAX_RETRANSMITS, 
					MAX_LTP_MAX_RETRANSMITS);
		
	}

	public int getLtpMaxReportRetransmits() {
		return _ltpMaxReportRetransmits;
	}

	public void setLtpMaxReportRetransmits(int aLtpMaxReportRetransmits) 
	throws LtpException {
		_ltpMaxReportRetransmits = 
			setIntValue(
					"Ltp Max Report Retransmits",
					aLtpMaxReportRetransmits,
					MIN_LTP_MAX_REPORT_RETRANSMITS,
					MAX_LTP_MAX_REPORT_RETRANSMITS);
	}

	/**
	 * UDP Port number for LTP over UDP
	 */
	public int getLtpUdpPort() {
		return _ltpUdpPort;
	}

	/**
	 * UDP Port number for LTP over UDP
	 * @throws LtpException 
	 */
	public void setLtpUdpPort(int aLtpUdpPort) throws LtpException {
		_ltpUdpPort = 
			setIntValue(
					"Ltp Over UDP Port Number",
					aLtpUdpPort,
					MIN_LTP_UDP_PORT,
					MAX_LTP_UDP_PORT);
	}

	/**
	 * UDP Receive buffer size
	 */
	public int getLtpUdpRecvBufferSize() {
		return _ltpUdpRecvBufferSize;
	}

	/**
	 * UDP Receive buffer size
	 * @throws LtpException 
	 */
	public void setLtpUdpRecvBufferSize(int aLtpUdpRecvBufferSize) 
	throws LtpException {
		_ltpUdpRecvBufferSize = 
			setIntValue(
					"LTP Over UDP Socket Receive Buffer Size",
					aLtpUdpRecvBufferSize,
					MIN_LTP_UDP_RECV_BUF_SIZE,
					MAX_LTP_UDP_RECV_BUF_SIZE);
	}

	/**
	 * Interface name to be used in unit testing
	 */
	public String getTestInterface() {
		return _testInterface;
	}

	/**
	 * Interface name to be used in unit testing
	 */
	public void setTestInterface(String aTestInterface) {
		_testInterface = aTestInterface;
	}

	/**
	 * Block length threshold to decide whether Block should be in memory or
	 * in a File.
	 */
	public int getBlockLengthFileThreshold() {
		return _blockLengthFileThreshold;
	}

	/**
	 * Block length threshold to decide whether Block should be in memory or
	 * in a File.
	 * @throws LtpException 
	 */
	public void setBlockLengthFileThreshold(int blockLengthFileThreshold) 
	throws LtpException {
		_blockLengthFileThreshold = 
			setIntValue(
					"Block Length File Threshold",
					blockLengthFileThreshold,
					MIN_BLOCK_LEN_FILE_THRESHOLD,
					MAX_BLOCK_LEN_FILE_THRESHOLD);
	}

	/**
	 * Segment length threshold to decide whether Segment should be in memory or
	 * in a File
	 */
	public int getSegmentLengthFileThreshold() {
		return _segmentLengthFileThreshold;
	}

	/**
	 * Segment length threshold to decide whether Segment should be in memory or
	 * in a File
	 * @throws LtpException 
	 */
	public void setSegmentLengthFileThreshold(int segmentLengthFileThreshold) 
	throws LtpException {
		_segmentLengthFileThreshold = 
			setIntValue(
					"Segment Length File Threshold",
					segmentLengthFileThreshold,
					MIN_SEGMENT_LEN_FILE_THRESHOLD,
					MAX_SEGMENT_LEN_FILE_THRESHOLD);
	}

	/**
	 * Limit on number of session reception problems before Session cancelled
	 */
	public int getSessionReceptionProblemsLimit() {
		return _sessionReceptionProblemsLimit;
	}

	/**
	 * Limit on number of session reception problems before Session cancelled
	 * @throws LtpException 
	 */
	public void setSessionReceptionProblemsLimit(
			int sessionReceptionProblemsLimit) throws LtpException {
		_sessionReceptionProblemsLimit = 
			setIntValue(
					"Segment Reception Problems Limit",
					sessionReceptionProblemsLimit,
					MIN_SESSION_RECEPTION_PROBLEMS_LIMIT,
					MAX_SESSION_RECEPTION_PROBLEMS_LIMIT);
	}
	
	/**
	 * Get LTP Statistics
	 * @return LTP Statistics
	 */
	public LtpStats getLtpStats() {
		return _ltpStats;
	}

	/**
	 * Max number of retransmissions of Cancel Segments
	 */
	public int getLtpMaxCancelRetransmits() {
		return _ltpMaxCancelRetransmits;
	}

	/**
	 * Max number of retransmissions of Cancel Segments
	 */
	public void setLtpMaxCancelRetransmits(int ltpMaxCancelRetransmits) {
		_ltpMaxCancelRetransmits = ltpMaxCancelRetransmits;
	}

	/**
	 * Log Link operational state changes as Logger warnings
	 */
	public boolean isLogLinkOperStateChanges() {
		return _logLinkOperStateChanges;
	}

	/**
	 * Log Link operational state changes as Logger warnings
	 */
	public void setLogLinkOperStateChanges(boolean logLinkOperStateChanges) {
		_logLinkOperStateChanges = logLinkOperStateChanges;
	}
	
}
