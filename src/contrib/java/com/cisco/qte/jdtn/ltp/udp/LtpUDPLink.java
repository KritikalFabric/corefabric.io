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
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Link;
import com.cisco.qte.jdtn.general.LinkAddress;
import com.cisco.qte.jdtn.general.Neighbor;
import com.cisco.qte.jdtn.general.NeighborsList;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.ltp.EngineId;
import com.cisco.qte.jdtn.ltp.IPAddress;
import com.cisco.qte.jdtn.ltp.LtpLink;
import com.cisco.qte.jdtn.ltp.LtpException;
import com.cisco.qte.jdtn.ltp.LtpManagement;
import com.cisco.qte.jdtn.ltp.LtpNeighbor;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;
import org.kritikal.fabric.contrib.jdtn.BlobAndBundleDatabase;

/**
 * UDP implementation of LTP Link.  Uses DatagramSocket to send/ receive.
 */
public class LtpUDPLink extends LtpLink {
	// This is only valid for Ethernet, IPV4, no IP options.  If using
	// something else then need to configure the maxFrameSize.
	private static final int MAX_ETHERNET_FRAME_SIZE = 1492;
	private static final int IP_HEADER_SIZE = 20;
	private static final int UDP_HEADER_SIZE = 8;
	private static final int SAFE_SLOP = 64;
	/** The default max frame size; valid only for Ethernet, IPV4, no IP options */
	protected static final int DEFAULT_MAX_FRAME_SIZE =
		MAX_ETHERNET_FRAME_SIZE - IP_HEADER_SIZE - UDP_HEADER_SIZE - SAFE_SLOP;
	
	private static final Logger _logger =
		Logger.getLogger(LtpUDPLink.class.getCanonicalName());

	/** The IPAddress of the Link */
	protected IPAddress _ipAddress = null;
	/** The system network interface name of the Link */
	protected String _ifName = null;
	/** The system network interface of the Link */
	protected NetworkInterface _networkInterface = null;
	/** Whether this is an IPV6 (true) or IPV4 (false) link */
	protected boolean _iPV6 = false;
	/** Whether to deliver traffic from unknown neighbors */
	protected boolean _deliverTrafficFromUnknownNeighbors = true;
	
	private DatagramSocket _socket = null;
	private boolean _isSocketOpen = false;
	private int _maxFrameSize = DEFAULT_MAX_FRAME_SIZE;
	
	/**
	 * Parse a UDPLink from given XMLPullParser and create a UDPLink.  It is
	 * assumed that the parser is sitting on a &lt; Link &gt; element.
	 * We pull UDPLink specific attributes out of the parser and create a
	 * UDPLink.  We do not advance the state of the parse, leaving that to the 
	 * caller.
	 * @param parser The given Parser
	 * @param linkName The name of the link, arbitrary id for the Link
	 * @return The UDPLink created
	 * @throws JDtnException On XML structure errors
	 * @throws XmlRdParserException On XML parse errors
	 * @throws IOException On I/O Errors
	 */
	public static LtpUDPLink parseUdpLink(XmlRDParser parser, String linkName)
	throws JDtnException, XmlRdParserException, IOException {
		// <Link 
		//    type="udp" 		(already parsed by caller)
		//    linkName="name" 	(already parsed by caller)
		//    ifName="ifname"
		//    ipv6="boolean" 
		//    maxFrameSize="n">
		//    <UdpNeighbor .../>
		// </zlink>
		String ifName = parser.getAttributeValue("ifName");
		if (ifName == null || ifName.length() == 0) {
			throw new JDtnException("Missing required 'ifName' attribute");
		}
		
		// Extract the 'ipv6' attribute
		String value = parser.getAttributeValue("ipv6");
		if (value == null || value.length() == 0) {
			value = "false";
		}
		boolean wantIPV6 = false;
		try {
			wantIPV6 = Boolean.parseBoolean(value);
		} catch (Exception e) {
			throw new JDtnException("Invalid UdpLink 'ipv6' attribute");
		}
		
		// Create the UDPLink
		LtpUDPLink udpLink = LtpUDPLink.createUDPLink(linkName, ifName, wantIPV6);
		if (udpLink == null) {
			throw new JDtnException(
					"No UDPLink available for linkName=" + linkName +
					" ifName=" + ifName + " and IPV6 requirement=" + wantIPV6);
		}
		
		// Install maxFrameSize attribute if configured
		Integer intValue = Utils.getIntegerAttribute(
				parser, "maxFrameSize", 0, Integer.MAX_VALUE);
		if (intValue != null) {
			udpLink.setMaxFrameSize(intValue.intValue());
		}
		
		return udpLink;
	}
	
	/**
	 * Write UDPLink specific attributes to given PrintWriter.  We merely
	 * output attributes; not a full &lt; Link &gt; element.
	 * @param pw Given PrintWriter
	 */
	@Override
	public void writeConfigImpl(PrintWriter pw) {
		pw.println("        ifName='" + getIfName() + "'");
		pw.println("        ipv6='" + isiPV6() + "'");
		if (getMaxFrameSize() != DEFAULT_MAX_FRAME_SIZE) {
			pw.println("        maxFrameSize='" + getMaxFrameSize() + "'");
		}
	}
	
	/**
	 * Create a UDPLink on the specified NetworkInterface w/ optional IPV6 support
	 * @param linkName Name of a NetworkInterface on which to create the UDPLink
	 * @param ifName System network interface name for the UDPLink
	 * @param wantIPV6 True if want this to be an IPV6 UDPLink, otherwise it
	 * will be an IPV4 link
	 * @return UDPLink created 
	 * @throws LtpException if cannot find NetworkInterface matching arguments
	 */
	public static LtpUDPLink createUDPLink(
			String linkName, 
			String ifName, 
			boolean wantIPV6) 
	throws LtpException {
		// Try to find an address for the UDPLink
		try {
			Enumeration<NetworkInterface> interfaces = 
				NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				NetworkInterface intfc = interfaces.nextElement();
				if (intfc.getName().equals(ifName)) {
					Enumeration<InetAddress> addresses = intfc.getInetAddresses();
					while (addresses.hasMoreElements()) {
						InetAddress inetAddress = addresses.nextElement();
						if (!inetAddress.isMulticastAddress()) {
							if (wantIPV6 && (inetAddress instanceof Inet6Address)) {
								// IPV6 Link
								IPAddress ipAddress = new IPAddress(inetAddress);
								LtpUDPLink link = new LtpUDPLink(linkName, ifName, ipAddress);
								link.setNetworkInterface(intfc);
								link.setiPV6(wantIPV6);
								return link;
	
							} else if (!wantIPV6 && (inetAddress instanceof Inet4Address)) {
								// IPV4 Link
								IPAddress ipAddress = new IPAddress(inetAddress);
								LtpUDPLink link = new LtpUDPLink(linkName, ifName, ipAddress);
								link.setNetworkInterface(intfc);
								link.setiPV6(wantIPV6);
								return link;
	
							}
						}
					}
				}
			}
		} catch (Exception e) {
			_logger.log(Level.SEVERE, "Searching for InetAddress for UDPLink " + linkName, e);
		}
		throw new LtpException(
				"createUDPLink(linkName=" + linkName + 
				", ifName=" + ifName +
				", wantIPVF=" + wantIPV6 +
				") failed to find matching NetworkInterface");
	}
	
	/**
	 * Construct a UDPLink with given linkName and IPAddress
	 * @param linkName Given LinkName
	 * @param ifName Name of System network interface
	 * @param ipAddress Given IPAddress
	 * @throws Exception if cannot create a DatagramSocket
	 */
	public LtpUDPLink(
			String linkName, 
			String ifName,
			IPAddress ipAddress) throws Exception {
		super(linkName);
		setIpAddress(ipAddress);
		setAddress(ipAddress);
		setIfName(ifName);		
	}
	
	/**
	 * Get the Link State for this UDPLink
	 * @return True if Link is up
	 */
	@Override
	protected boolean getLinkStateImpl() {
		// If this host re-dhcps at any time, it may get a different IP Address
		// than it had last time we were configured.  Check for this condition,
		// and adjust accordingly.
		try {
			_networkInterface = NetworkInterface.getByName(_ifName);
		} catch (SocketException e1) {
			_logger.severe("Interface " + _ifName + " is no longer present");
			linkStopImpl();
			return false;
		}
		
		boolean found = false;
		InetAddress newInetAddress = null;
		if (_networkInterface != null) {
			Enumeration<InetAddress> inetAddresses = _networkInterface.getInetAddresses();
			while (inetAddresses.hasMoreElements()) {
				// Determine if Link's previous InetAddress has disappeared
				InetAddress inetAddress = inetAddresses.nextElement();
				if (inetAddress.equals(_ipAddress.getInetAddress())) {
					found = true;
					break;
				}
				// Along the way, determine if Link's present InetAddress is
				// a candidate for a new InetAddress for the Link.  We want
				// to preserve IP version-ness of the address.
				if (!inetAddress.isMulticastAddress()) {
					if (_ipAddress.getInetAddress() instanceof Inet6Address) {
						// Previous address was IPV6 address
						if (inetAddress instanceof Inet6Address) {
							// This address is IPV6 address
							newInetAddress = inetAddress;
						}
					} else {
						// Previous address was IPV4 address
						if (!(inetAddress instanceof Inet6Address)) {
							// This address is IPV4 address
							newInetAddress = inetAddress;
						}
					}
				}
			}
		} else {
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Interface " + _ifName + " is no longer available");
			}
			linkStopImpl();
			return false;
		}
		
		if (!found) {
			// Previously configured IP Address disappeared on us
			// Close existing socket and we will try to re-open it below
			_logger.severe(
					"UDPLink " + getName() + 
					": no longer is assigned IP Address " + 
					_ipAddress.getInetAddress());
			linkStopImpl();
			
			if (newInetAddress != null) {
				// We can replace it with this newInetAddress
				_logger.info(
						"UDPLink " + getName() + 
						": replacing with IP Address " + newInetAddress);
				IPAddress newIPAddress = new IPAddress(newInetAddress);
				setIpAddress(newIPAddress);
				setAddress(newIPAddress);
				
			} else {
				// Our configured interface doesn't have an address.
				// We cannot proceed.  Maybe next try we will have one.
				_logger.severe("Interface " + _ifName +
						" no longer has an IP Address");
				return false;
			}
		}
		
		//	Would like to use NetworkInterface.isUp(), but that's a JDK 1.6 feature and
		//  I need to restrict myself to JDK 1.5 in order to make this runnable on
		//  Android, which requires JDK 1.5.
		//		try {
		//			return networkInterface.isUp();
		//		} catch (SocketException e) {
		//			_logger.log(Level.SEVERE, "UDPLink " + name + " getLinkState()", e);
		//			return false;
		//		}
		//
		//  So, as an alternative, what I do is say that if we are able to successfully
		//  open our socket, then we say the Link is up.
		synchronized (this) {
			if (!_isSocketOpen) {
				try {
					linkStartImpl();
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("Successfully restarted socket");
					}
					return true;
				} catch (Exception e) {
					_logger.log(Level.FINE, "getLinkStateImpl()", e);
					return false;
				}
			} else {
				return true;
			}
		}
	}

	/**
	 * Get the maximum size of a frame in bytes for this Link
	 * @return Max frame size
	 */
	@Override
	public int getMaxFrameSize() {
		return _maxFrameSize;
	}
	
	/**
	 * Set the maximum size of a frame in bytes for this Link
	 * @param maxFrameSize Max frame size
	 */
	public void setMaxFrameSize(int maxFrameSize) {
		this._maxFrameSize = maxFrameSize;
	}
	
	/**
	 * Receive a DatagramPacket on the Link. Blocks.  When Datagram Packet
	 * received, calls back through notifyReceived().
	 * @throws InterruptedException 
	 */
	@Override
	protected void receiveImpl() throws JDtnException, InterruptedException {
		
		// If _socket hasn't been successfully opened, try to open it now
		synchronized (this) {
			if (!_isSocketOpen) {
				try {
					linkStartImpl();
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("Successfully restarted socket");
					}
				} catch (Exception e) {
					throw new JDtnException(e);
				}
			}
		}
		
		// Post the Receive
		byte[] buf = new byte[getMaxFrameSize()];
		DatagramPacket packet = new DatagramPacket(buf, 0, getMaxFrameSize());
		try {
			_socket.receive(packet);
			// Check if from a known Neighbor.
			IPAddress ipAddr = new IPAddress(packet.getAddress());
			Neighbor neighbor = NeighborsList.getInstance().findNeighborByAddress(ipAddr);
			if (neighbor == null) {
				// Unknown Neighbor.  This is most likely an unknown Neighbor
				// sending us a Router Hello introducing itself.  We would like
				// to deliver this packet to the Router App so that it can make
				// adjustments to the routing tables and neighbor lists.  
				if (!_deliverTrafficFromUnknownNeighbors) {
					// We're going to reject this packet
					_logger.warning(
							"Packet received and discarded from unknown neighbor at address " +
							packet.getAddress());
					return;
				}
				// we're going to create a temporary neighbor and deliver this
				// packet.
				IPAddress ipAddress = new IPAddress(packet.getAddress());
				EngineId engineId = new EngineId(ipAddress);
				LinkAddress linkAddress = new LinkAddress(this, ipAddress);
				neighbor = new LtpUDPNeighbor(linkAddress, engineId, "Temporary Neighbor");
				neighbor.addLinkAddress(linkAddress);
				neighbor.setTemporary(true);
				
			} else {
				// Found Neighbor w/ the IPAddress.  But check to see if the Link has shifted.
				// This can occur on routing recalcs. E.g., a shift-over from a
				// Wifi link to a cellular link.
				if (!neighbor.hasLink(this)) {
					// Neighbor's IP Address is known, but on a different Link than this
					// Change the Link association of the Neighbor
					int index = neighbor.getAddressIndex(ipAddr);
					neighbor.removeLinkAddress(index);
					// Now add in the correct Link Address
					LinkAddress linkAddress = new LinkAddress(this, ipAddr);
					neighbor.addLinkAddress(linkAddress);
				}
			}
			
			// Notify super about the received packet
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Received Packet, Length=" + packet.getLength());
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.finest("Received Data=" + Utils.dumpBytes("  ", buf, 0, packet.getLength()));
				}
			}
			java.sql.Connection con = BlobAndBundleDatabase.getInstance().getInterface().createConnection();
			try {
				notifyReceived(con, this, (LtpNeighbor) neighbor, buf, 0, packet.getLength());
				try { con.commit(); } catch (SQLException e) { _logger.warning(e.getMessage()); }
			}
			finally {
				try { con.close(); } catch (SQLException e) { _logger.warning(e.getMessage()); }
			}
			
		} catch (IOException e) {
			notifyLinkDatalinkDown();
			if (e.getMessage().equalsIgnoreCase("Socket closed") ||
				e.getMessage().equalsIgnoreCase("The system call was cancelled")) {
				if (GeneralManagement.isDebugLogging()) {
					_logger.fine("Link " + getName() + ": Receive() detected shutdown");
				}
			} else {
				throw new JDtnException("UDPLink " + getName() + ": packet receive", e);
			}
		}
	}

	/**
	 * Send a DatagramPacket
	 * @param neighbor Neighbor to send to
	 * @param buffer Data to send
	 * @param offset Starting offset into data
	 * @param length Length of data to send
	 * @throws JDtnException on send error
	 */
	@Override
	public void sendImpl(LtpNeighbor neighbor, byte[] buffer, int offset, int length)
	throws JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("sendImpl() Length=" + length);
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finer("sendImpl() Data=" + Utils.dumpBytes("  ", buffer, 0, length));
			}
		}
		
		if (length > getMaxFrameSize()) {
			_logger.warning(
					"Packet length " + length + 
					" exceeds max packet size: " + getMaxFrameSize());
		}
		
		// If _socket hasn't been successfully opened, try to open it now
		synchronized (this) {
			if (!_isSocketOpen) {
				try {
					linkStartImpl();
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("Successfully restarted socket");
					}
				} catch (Exception e) {
					throw new JDtnException(e);
				}
			}
		}
		
		// Make sure given Neighbor is a UDPNeighbor
		if (!(neighbor instanceof LtpUDPNeighbor)) {
			throw new JDtnException("UDPLink " + this + ": Neighbor: " + 
					neighbor + " is not a UDPLink");
		}
		LtpUDPNeighbor udpNeighbor = (LtpUDPNeighbor)neighbor;
		
		// Send the Packet
		IPAddress address = (IPAddress)udpNeighbor.getAddressForLink(this);
		DatagramPacket packet = new DatagramPacket(
				buffer, offset, length, address.getInetAddress(), 
				LtpManagement.getInstance().getLtpUdpPort());
		try {
			_socket.send(packet);
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Sent " + length + " bytes");
			}
			
		} catch (IOException e) {
			throw new JDtnException(e);
		}
	}

	/**
	 * Called when the Link is started, giving us an opportunity to 
	 * initialize our socket.
	 */
	@Override
	protected void linkStartImpl() throws Exception {
		if (GeneralManagement.isDebugLogging()) {
			if (_logger.isLoggable(Level.FINE)) {
				_logger.fine("linkStartImpl()");
				_logger.fine("Address=" + _ipAddress.dump("", true));
				_logger.fine("Port=" + LtpManagement.getInstance().getLtpUdpPort());
			}
		}
		
		// Open the DatagramSocket
		try {
			_socket = new DatagramSocket(LtpManagement.getInstance().getLtpUdpPort(), _ipAddress.getInetAddress());	
			_socket.setSendBufferSize(LtpManagement.getInstance().getLtpUdpRecvBufferSize());
			_socket.setReceiveBufferSize(LtpManagement.getInstance().getLtpUdpRecvBufferSize());
			_socket.setSoTimeout(0);
			_socket.setReuseAddress(false);
			_isSocketOpen = true;
			notifyLinkDatalinkUp();
			
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Socket Receive Buffer = " + _socket.getReceiveBufferSize());
				_logger.fine("Socekt Transmit Buffer = " + _socket.getSendBufferSize());
			}
			
		} catch (SocketException e) {
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("While opening socket, got Exception: " + e.getMessage());
			}
			linkStopImpl();
			throw e;
		}
	}
	
	/**
	 * Called when the Link is stopped, giving us an opportunity to clean up.
	 * Any pending socket operations have already been interrupted.
	 */
	@Override
	protected void linkStopImpl() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("linkStopImpl()");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(dump("  ", true));
			}
		}
		synchronized (this) {
			if (_isSocketOpen) {
				_socket.close();
				_isSocketOpen = false;
			}
		}
		notifyLinkDatalinkDown();
	}

	/**
	 * Called when Link removed from LinksList.  We don't need to do anything
	 * that's not already done.
	 */
	@Override
	protected void linkRemovedImpl() {
		// Nothing
	}

	/**
	 * Remove the UDPNeighbor which matches given arguments from this Link's
	 * list of neighbors.
	 * @param neighbor Given neighbor
	 * @throws JDtnException If no neighbor with matching IPAddress exists
	 */
	public void removeUDPNeighbor(LtpUDPNeighbor neighbor) 
	throws JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("removeUDPNeighbor()");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(neighbor.dump("  ", true));
			}
		}
		NeighborsList.getInstance().removeNeighbor(neighbor);
	}
	
	public static LtpUDPNeighbor findNeighborByAddressAndLink(
			Link link, 
			IPAddress ipAddress) {
		LtpUDPNeighbor neighbor = (LtpUDPNeighbor)NeighborsList.getInstance().findNeighborByPredicate(
			new NeighborsList.NeighborPredicate2() {
				
				@Override
				public boolean isNeighborAccepted(Neighbor neighbor2, Object arg1, Object arg2) {
					if (!(neighbor2 instanceof LtpUDPNeighbor)) {
						return false;
					}
					LtpNeighbor ltpNeighbor = (LtpNeighbor)neighbor2;
					if (!(arg1 instanceof LtpUDPLink)) {
						return false;
					}
					LtpUDPLink ltpUdpLink = (LtpUDPLink)arg1;
					if (!(arg2 instanceof IPAddress)) {
						return false;
					}
					IPAddress ipAddr = (IPAddress)arg2;
					LinkAddress linkAddress = new LinkAddress(ltpUdpLink, ipAddr);
					if (!ltpNeighbor.hasLinkAddress(linkAddress)) {
						return false;
					}
					return true;
				}
			},
			link,
			ipAddress
		);
		return neighbor;
	}
	
	@Override
	public String toString() {
		return dump("", false);
	}
	
	/**
	 * Dump the state of this object
	 * @param indent Amount of indentation
	 * @param detailed True if want verbose details
	 * @return String containing dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "UDPLink\n");
		sb.append(super.dump(indent + "  ", detailed));
		sb.append(indent + "  isIPV6=" + isiPV6() + "\n");
		sb.append(indent + "  IPAddress=" + _ipAddress + "\n");
		sb.append(indent + "  maxFrameSize=" + _maxFrameSize + "\n");
		
		return sb.toString();
	}
	
	/** The system network interface of the Link */
	public NetworkInterface getNetworkInterface() {
		return _networkInterface;
	}

	/** The system network interface of the Link */
	protected void setNetworkInterface(NetworkInterface networkInterface) {
		this._networkInterface = networkInterface;
	}

	/** Whether this is an IPV6 (true) or IPV4 (false) link */
	public boolean isiPV6() {
		return _iPV6;
	}

	/** Whether this is an IPV6 (true) or IPV4 (false) link */
	protected void setiPV6(boolean iPV6) {
		this._iPV6 = iPV6;
	}

	/** The IPAddress of the Link */
	public IPAddress getIpAddress() {
		return _ipAddress;
	}

	/** The IPAddress of the Link */
	protected void setIpAddress(IPAddress ipAddress) {
		this._ipAddress = ipAddress;
	}

	/** The system network interface name of the Link */
	public String getIfName() {
		return _ifName;
	}
	
	/** The system network interface name of the Link */
	protected void setIfName(String ifName) {
		this._ifName = ifName;
	}

	public boolean isDeliverTrafficFromUnknownNeighbors() {
		return _deliverTrafficFromUnknownNeighbors;
	}

	public void setDeliverTrafficFromUnknownNeighbors(
			boolean deliverTrafficFromUnknownNeighbors) {
		this._deliverTrafficFromUnknownNeighbors = deliverTrafficFromUnknownNeighbors;
	}

	/* (non-Javadoc)
	 * @see com.cisco.qte.jdtn.general.Link#getLinkTypeImpl()
	 */
	@Override
	protected LinkType getLinkTypeImpl() {
		return Link.LinkType.LINK_TYPE_LTP_UDP;
	}
	
}
