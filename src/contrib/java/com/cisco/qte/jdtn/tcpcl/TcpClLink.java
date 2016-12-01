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
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.bp.BPException;
import com.cisco.qte.jdtn.bp.EndPointId;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Link;
import com.cisco.qte.jdtn.general.LinkAddress;
import com.cisco.qte.jdtn.general.Neighbor;
import com.cisco.qte.jdtn.general.NeighborsList;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.ltp.IPAddress;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * TCP Convergence Layer Link
 * NOTE: We do not support reactive fragmentation
 */
public class TcpClLink extends Link {
	private static final Logger _logger =
		Logger.getLogger(TcpClLink.class.getCanonicalName());
	
	public static final boolean IPV6_DEFAULT = false;
	// TCPCL spec says max segment should be 2-3 times the Link MTU.
	public static final int MAX_SEG_SIZE_DEFAULT = 4500;
	public static final int MAX_SEG_SIZE_MIN = 32;
	public static final int MAX_SEG_SIZE_MAX = 32 * 1024 * 1024;
	
	public static final int TCPCL_PORT_DEFAULT = 4556;
	public static final int TCPCL_PORT_MIN = 1000;
	public static final int TCPCL_PORT_MAX = 65535;
	
	/** IP Address of this Link */
	private IPAddress _ipAddress;
	/** Network Interface name this Link represents */
	private String _ifName = null;
	/** True => IPV6 version of the interface wanted; false => IPV4 version */
	private boolean _ipv6 = IPV6_DEFAULT;
	/** Max number of bytes in a TCPCL Data Segment */
	private int _maxSegmentSize = MAX_SEG_SIZE_DEFAULT;
	/** The TCP Port number upon which we will accept TcpCl connections */
	private int _tcpPort = TCPCL_PORT_DEFAULT;
	/** The system network interface of the Link */
	protected NetworkInterface _networkInterface = null;
	
	private boolean _connectionAcceptorStarted = false;
	private Thread _connectionAcceptorThread = null;
	private ServerSocket _serverSocket = null;

	/**
	 * Constructor
	 * @param name Link Name
	 */
	public TcpClLink(String name) {
		super(name);
	}
	
	/**
	 * Parse attributes of the &lt; Link &gt; element specific to TcpClLink.
	 * Advances the parse past the &lt; /Link &gt; tag.
	 * @param parser
	 * @param name
	 * @param linkType
	 * @return The TcpClLink created
	 * @throws JDtnException on TcpClLink specific errors
	 * @throws IOException  on I/O errors
	 * @throws XMLStreamException  on XML parsing errors
	 * @throws InterruptedException 
	 */
	public static TcpClLink parseLink(
			XmlRDParser parser, 
			String name, 
			Link.LinkType linkType) 
	throws JDtnException, XmlRdParserException, IOException, InterruptedException {

		// Parse Attributes beyond those parsed by super
		//    ifName="ifname" - REQUIRED
		String ifName = parser.getAttributeValue("ifName");
		if (ifName == null || ifName.length() == 0) {
			throw new JDtnException("Required attribute 'ifName' missing");
		}
		
		//    ipv6="boolean" - Needed to createTcpClLink()
		Boolean ipv6 = Utils.getBooleanAttribute(parser, "ipv6");
		if (ipv6 == null) {
			ipv6 = IPV6_DEFAULT;
		}
		
		// Create the Link
		TcpClLink link = createTcpClLink(name, ifName, ipv6);
		
		// Parse optional attributes
		//    maxSegmentSize='n'
		Integer intVal = Utils.getIntegerAttribute(
				parser, 
				"maxSegmentSize", 
				MAX_SEG_SIZE_MIN, 
				MAX_SEG_SIZE_MAX);
		if (intVal != null) {
			link.setMaxSegmentSize(intVal);
		}
		
		//   tcpPort='n'
		intVal = Utils.getIntegerAttribute(
				parser,
				"tcpPort",
				TCPCL_PORT_MIN,
				TCPCL_PORT_MAX);
		if (intVal != null) {
			link.setTcpPort(intVal);
		}

		// Parse </Link>
		XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
		if (event != XmlRDParser.EventType.END_ELEMENT ||
			!parser.getElementTag().equals("Link")) {
			throw new JDtnException("Missing /Link");
		}
		
		return link;
	}
	
	/*
	 * Output TcpClLink specific attributes to the configuration file
	 * @see com.cisco.qte.jdtn.general.Link#writeConfigImpl(java.io.PrintWriter)
	 */
	@Override
	protected void writeConfigImpl(PrintWriter pw) {
		pw.println("        ifName='" + getIfName() + "'");
		pw.println("        ipv6='" + isIpv6() + "'");
		if (getMaxSegmentSize() != MAX_SEG_SIZE_DEFAULT) {
			pw.println("        maxSegmentSize='" + getMaxSegmentSize() + "'");
		}
		if (getTcpPort() != TCPCL_PORT_DEFAULT) {
			pw.println("        tcpPort='" + getTcpPort() + "'");
		}
	}

	/**
	 * Create a TcpClLink satisfying the given criteria
	 * @param linkName Locally-unique name desired for the Link
	 * @param ifName Network Interface Name
	 * @param wantIPV6 Whether want IPV6 (true) or IPV4 (false) 
	 * @return Link created
	 * @throws JDtnException if cannot satisfy given criteria
	 */
	public static TcpClLink createTcpClLink(
			String linkName, 
			String ifName, 
			boolean wantIPV6) throws JDtnException {
		// Try to find an address for the TcpClLink
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
								TcpClLink link = new TcpClLink(linkName);
								link.setIfName(ifName);
								link.setIpAddress(ipAddress);
								link.setNetworkInterface(intfc);
								link.setIpv6(wantIPV6);
								return link;
	
							} else if (!wantIPV6 && (inetAddress instanceof Inet4Address)) {
								// IPV4 Link
								IPAddress ipAddress = new IPAddress(inetAddress);
								TcpClLink link = new TcpClLink(linkName);
								link.setIfName(ifName);
								link.setIpAddress(ipAddress);
								link.setNetworkInterface(intfc);
								link.setIpv6(wantIPV6);
								return link;
	
							}
						}
					}
				}
			}
		} catch (Exception e) {
			_logger.log(Level.SEVERE, "Searching for InetAddress for UDPLink " + linkName, e);
		}
		throw new JDtnException(
				"createTcpClLink(linkName=" + linkName + 
				", ifName=" + ifName +
				", wantIPVF=" + wantIPV6 +
				") failed to find matching NetworkInterface");

	}
	
	/**
	 * Dump this object
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "TcpClLink\n");
		sb.append(super.dump(indent + "  ", detailed));
		sb.append(indent + "  ifName=" + getIfName() + "\n");
		sb.append(indent + "  ipv6=" + isIpv6() + "\n");
		sb.append(indent + "  ipAddress=" + getIpAddress() + "\n");
		sb.append(indent + "  MaxSegmentSize= " + getMaxSegmentSize() + "\n");
		sb.append(indent + "  TcpPort=" + getTcpPort() + "\n");
		
		return sb.toString();
	}
	
	/**
	 * Start the Connection Acceptor Thread for this Link.  This is called from
	 * a TcpClNeighbor when it wants to accept connects.  We have a 'use count'
	 * mechanism to handle multiple requests from TcpClNeighbors to start the
	 * Connection Acceptor Thread.
	 * @param port TCP Port Number
	 */
	private void startConnectionAcceptor() {
		if (!_connectionAcceptorStarted) {
			_connectionAcceptorThread = new Thread(new ConnectionAcceptor(this));
			_connectionAcceptorThread.setName("Acceptor-" + getName());
			_connectionAcceptorThread.start();
			_connectionAcceptorStarted = true;
		}
	}
	
	/**
	 * Stop the Connection Acceptor Thread for this link (taking use count into
	 * account).
	 * @throws InterruptedException If interrupted waiting for thread to die
	 */
	private void stopConnectionAcceptor() throws InterruptedException {
		if (_connectionAcceptorStarted) {
			_connectionAcceptorThread.interrupt();
			_connectionAcceptorThread.join(200L);
			// Note: apparently you cannot interrupt a Thread that's blocked
			// on a ServerSocket.accept().  The interrupt has not effect.
			// So we have to use a larger hammer consisting of closing the 
			// ServerSocket out from under the thread.  Also, since the the
			// accept won't be interrupted, we use a relatively short timeout
			// on the join(), since it will probably time out anyway.
			if (_connectionAcceptorThread.isAlive()) {
				if (_serverSocket != null) {
					try {
						_serverSocket.close();
					} catch (IOException e) {
						_logger.log(Level.SEVERE, "close ServerSocket", e);
					}
				}
			}
			_connectionAcceptorStarted = false;
		}
	}
	
	/**
	 * Thread which accepts incoming TCP connections
	 */
	public class ConnectionAcceptor implements Runnable {
		private TcpClLink _link = null;
		
		public ConnectionAcceptor(TcpClLink link) {
			_link = link;
		}
		
		public void run() {
			_serverSocket = null;
			try {
				while (!Thread.interrupted()) {
					InetAddress inetAddress = getIpAddress().getInetAddress();
					if (_serverSocket == null) {
						openServerSocket(inetAddress);
					}
					if (_serverSocket != null) {
						acceptSocketConnection();
					}
				}
				
			} catch (InterruptedException e) {
				if (GeneralManagement.isDebugLogging()) {
					_logger.fine("Acceptor Thread interrupted");
				}
			} finally {
				if (_serverSocket != null) {
					try {
						_serverSocket.close();
					} catch (IOException e) {
						// Nothing
					}
					_serverSocket = null;
				}
			}
			
		}

		/**
		 * Open the ServerSocket so we can accept connections.  Called from
		 * ConnectionAccepter Thread.  Sets member _serverSocket.
		 * @param inetAddress Local IP Address of this Link
		 * @throws InterruptedException if interrupted during process
		 */
		private void openServerSocket(InetAddress inetAddress)
				throws InterruptedException {
			// Repeat until we've successfully opened the Server Socket
			while (_serverSocket == null) {
				if (Thread.interrupted()) {
					return;
				}
				try {
					// Open the Server Socket
					_serverSocket = new ServerSocket(_tcpPort, 1, inetAddress);
					_serverSocket.setReuseAddress(true);
					
				} catch (SocketException e) {
					if (e.getMessage().contains("Address already in use")) {
						// This is most likely a temporary condition caused
						// by a previous run.  It will go away after a few
						// seconds.
						_logger.warning("Address in use, will delay and try again later");
						Thread.sleep(4000L);
						continue;
					}
					_logger.log(Level.SEVERE, "Trying to open Server Socket; will try again later: ", e);
					Thread.sleep(4000L);
					continue;
				} catch (IOException e) {
					_logger.log(Level.SEVERE, "Trying to open Server Socket; will try again later: ", e);
					Thread.sleep(4000L);
					continue;
				}
			}
		}
		
		/**
		 * Accept a connection on the ServerSocket and notify the associated
		 * Neighbor to start its state machine to bring up the connection and
		 * start receiving Bundles.  Uses member _serverSocket.
		 * @throws InterruptedException if interrupted during process
		 */
		private void acceptSocketConnection() throws InterruptedException {
			Socket socket = null;
			TcpClNeighbor tcpClNeighbor = null;
			
			// Accept a TCP connection
			try {
				socket = _serverSocket.accept();
			} catch (IOException e) {
				if (e instanceof SocketException) {
					if (e.getMessage().equals("Socket closed")) {
						if (GeneralManagement.isDebugLogging()) {
							_logger.fine("Socket closed out from under me; bailing");
						}
					} else if (e.getMessage().equals("Interrupted system call")) {
						if (GeneralManagement.isDebugLogging()) {
							_logger.fine("Socket accept interrupted; bailing");
						}
					} else {
						_logger.log(Level.SEVERE, "ServerSocket.accept(); will try again later", e);
					}
				} else {
					_logger.log(Level.SEVERE, "ServerSocket.accept(); will try again later", e);
				}
				try {
					_serverSocket.close();
				} catch (IOException e1) {
					// Nothing
				}
				_serverSocket = null;
				Thread.sleep(4000L);
				return;
			}
			InetAddress remoteAddress = socket.getInetAddress();
			IPAddress ipAddress = new IPAddress(remoteAddress);
			Neighbor neighbor = NeighborsList.getInstance()
					.findNeighborByAddress(ipAddress);
			if (neighbor == null) {
				// Got a Connection from unknown Neighbor; check policy
				// for what to do
				if (TcpClManagement.getInstance().isAcceptUnconfiguredNeighbors()) {
					
					// Create a temporary Neighbor.  This is most likely
					// a "Router Hello" message coming in.  The connection
					// will be completed, the Router Hello will be processed
					// and will result in deletion of the temporary Neighbor.
					try {
						EndPointId eid = EndPointId.createEndPointId("dtn://" + ipAddress);
						tcpClNeighbor = TcpClManagement.getInstance().addNeighbor("Temporary-Neighbor-" + UUID.randomUUID().toString(), eid);
						LinkAddress linkAddress = new LinkAddress(_link, ipAddress);
						tcpClNeighbor.addLinkAddress(linkAddress);
						tcpClNeighbor.setTemporary(true);
						neighbor = tcpClNeighbor;
					} catch (BPException e) {
						_logger.log(Level.SEVERE, "Adding New Neighbor", e);
						try {
							socket.close();
						} catch (IOException e1) {
							// Nothing
						}
						return;
					} catch (JDtnException e) {
						_logger.log(Level.SEVERE, "Adding New Neighbor", e);
						try {
							socket.close();
						} catch (IOException e1) {
							// Nothing
						}
						return;
					}
					
				} else {
					// Policy says don't accept the Connection
					_logger.severe("Connection from unknown Neighbor " +
							ipAddress + ": connection ignored");
					try {
						socket.close();
					} catch (IOException e) {
						// Nothing
					}
					Thread.sleep(4000L);
					return;
				}
			}
			if (!(neighbor instanceof TcpClNeighbor)) {
				_logger.severe("Neighbor not TcpClNeighbor: "
						+ ipAddress);
				try {
					socket.close();
				} catch (IOException e) {
					// Nothing
				}
				return;
			}
			
			// Notify the Neighbor that the connection has been accepted,
			// starting up its state machine to bring up the connection
			// and accepting Bundles.
			tcpClNeighbor = (TcpClNeighbor) neighbor;
			try {
				tcpClNeighbor.notifyConnectionAccepted(socket);
			} catch (JDtnException e) {
				_logger.log(Level.SEVERE, "notifyConnectionAccepted", e);
				try {
					socket.close();
				} catch (IOException e1) {
					// Nothing
				}
				return;
				
			} catch (IOException e) {
				_logger.log(Level.SEVERE, "notifyConnectionAccepted", e);
				try {
					socket.close();
				} catch (IOException e1) {
					// Nothing
				}
				return;
			}
		}
		
	}
	
	/**
	 * Called on Management Start Event
	 * @see com.cisco.qte.jdtn.general.Link#linkStartImpl()
	 */
	@Override
	protected void linkStartImpl() throws Exception {
		startConnectionAcceptor();
	}

	/**
	 * Called on Management Stop Event
	 * @see com.cisco.qte.jdtn.general.Link#linkStopImpl()
	 */
	@Override
	protected void linkStopImpl() throws InterruptedException {
		stopConnectionAcceptor();
	}

	/**
	 * Called when Link is removed.
	 * @see com.cisco.qte.jdtn.general.Link#linkRemovedImpl()
	 */
	@Override
	protected void linkRemovedImpl() {
		try {
			stopConnectionAcceptor();
		} catch (InterruptedException e) {
			// Nothing
		}
	}

	/**
	 * Get the convergence layer type of Link.
	 * @see com.cisco.qte.jdtn.general.Link#getLinkTypeImpl()
	 */
	@Override
	protected LinkType getLinkTypeImpl() {
		return Link.LinkType.LINK_TYPE_TCPCL;
	}

	/** Network Interface name this Link represents */
	public String getIfName() {
		return _ifName;
	}

	/** Network Interface name this Link represents */
	public void setIfName(String ifName) {
		this._ifName = new String(ifName);
	}

	/** True => IPV6 version of the interface wanted; false => IPV4 version */
	public boolean isIpv6() {
		return _ipv6;
	}

	/** True => IPV6 version of the interface wanted; false => IPV4 version */
	public void setIpv6(boolean ipv6) {
		this._ipv6 = ipv6;
	}

	/** Max number of bytes in a TCPCL Data Segment */
	public int getMaxSegmentSize() {
		return _maxSegmentSize;
	}

	/** Max number of bytes in a TCPCL Data Segment */
	public void setMaxSegmentSize(int maxSegmentSize) throws IllegalArgumentException {
		if (maxSegmentSize < MAX_SEG_SIZE_MIN ||
			maxSegmentSize > MAX_SEG_SIZE_MAX) {
			throw new IllegalArgumentException(
					"MaxSegmentSize must be in the range [" + 
					MAX_SEG_SIZE_MIN + ", " + MAX_SEG_SIZE_MAX + "]");
		}
		this._maxSegmentSize = maxSegmentSize;
	}

	/** IP Address of this Link */
	public IPAddress getIpAddress() {
		return _ipAddress;
	}

	/** IP Address of this Link */
	public void setIpAddress(IPAddress ipAddress) {
		this._ipAddress = ipAddress;
	}

	/** The system network interface of the Link */
	public NetworkInterface getNetworkInterface() {
		return _networkInterface;
	}
	
	/** The system network interface of the Link */
	public void setNetworkInterface(NetworkInterface networkInterface) {
		_networkInterface = networkInterface;
	}

	/** The TCP Port number upon which we will accept TcpCl connections */
	public int getTcpPort() {
		return _tcpPort;
	}

	/** The TCP Port number upon which we will accept TcpCl connections */
	public void setTcpPort(int tcpPort) throws InterruptedException {
		boolean previouslyStarted = _connectionAcceptorThread != null;
		if (previouslyStarted) {
			stopConnectionAcceptor();
		}
		this._tcpPort = tcpPort;
		if (previouslyStarted) {
			startConnectionAcceptor();
		}
	}
	
}
