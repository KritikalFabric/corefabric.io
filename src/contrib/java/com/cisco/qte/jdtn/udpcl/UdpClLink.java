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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.general.Address;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.LeakyBucketQueueCollection;
import com.cisco.qte.jdtn.general.LeakyBucketQueueDelayElement;
import com.cisco.qte.jdtn.general.LeakyBucketQueueElement;
import com.cisco.qte.jdtn.general.Link;
import com.cisco.qte.jdtn.general.LinkAddress;
import com.cisco.qte.jdtn.general.Neighbor;
import com.cisco.qte.jdtn.general.NeighborsList;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.ltp.IPAddress;
import com.cisco.qte.jdtn.udpcl.UdpClEvent.UdpClSubEvents;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * UDP Convergence Layer implementation of Link
 */
public class UdpClLink extends Link implements Runnable {
	private static final Logger _logger =
		Logger.getLogger(UdpClLink.class.getCanonicalName());
	
	public static final int UDPCL_EVENT_QUEUE_CAPACITY = 32;
	
	/** State Machine States */
	public enum UdpClState {
		STOPPED_STATE,
		OPENING_STATE,
		OPENED_STATE
	}
	
	/** Default value for UdpPort property */
	public static final int UDP_PORT_DEFAULT = 4556;
	public static final int UDP_PORT_MIN = 1000;
	public static final int UDP_PORT_MAX = 65535;
	
	/** Default value for Ipv6 property */
	public static final boolean IPV6_DEFAULT = false;
	/** Maximum block size we can accomodate */
	public static final int MAX_PACKET_LEN = 65507;

	/** UDP Port */
	private int _udpPort = UDP_PORT_DEFAULT;
	/** IP Address of this Link */
	private IPAddress _ipAddress;
	/** Network Interface name this Link represents */
	private String _ifName = null;
	/** True => IPV6 version of the interface wanted; false => IPV4 version */
	private boolean _ipv6 = IPV6_DEFAULT;

	private UdpClState _state = UdpClState.STOPPED_STATE;
	private DatagramSocket _socket = null;
	private NetworkInterface _networkInterface = null;
	private ArrayBlockingQueue<UdpClEvent> _eventQueue =
		new ArrayBlockingQueue<UdpClEvent>(UDPCL_EVENT_QUEUE_CAPACITY);
	private Thread _eventThread = null;
	private Thread _receiverThread;
	private Thread _transmitterThread;
	private Thread _socketOpenThread = null;
	
	// Outbound Segment Queue
	LeakyBucketQueueCollection _segmentQueue =
		new LeakyBucketQueueCollection(getName());
	
	/**
	 * Constructor
	 * @param name Link name
	 */
	public UdpClLink(String name) {
		super(name);
		_state = UdpClState.STOPPED_STATE;
	}
	
	/**
	 * Called from Shell to create a UdpClLink.
	 * @param linkName Name of the Link
	 * @param ifName Corresponding network interface name
	 * @param ipv6 Whether an IPV6 or IPV4 interface is desired
	 * @return Created UdpClLink
	 * @throws JDtnException on errors
	 */
	public static UdpClLink createUdpClLink(
			String linkName, 
			String ifName, 
			boolean ipv6) throws JDtnException {

		// Try to find an address for the UdpClLink
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
							if (ipv6 && (inetAddress instanceof Inet6Address)) {
								// IPV6 Link
								IPAddress ipAddress = new IPAddress(inetAddress);
								UdpClLink link = new UdpClLink(linkName);
								link.setIfName(ifName);
								link.setIpAddress(ipAddress);
								link.setNetworkInterface(intfc);
								link.setIpv6(ipv6);
								return link;
	
							} else if (!ipv6 && (inetAddress instanceof Inet4Address)) {
								// IPV4 Link
								IPAddress ipAddress = new IPAddress(inetAddress);
								UdpClLink link = new UdpClLink(linkName);
								link.setIfName(ifName);
								link.setIpAddress(ipAddress);
								link.setNetworkInterface(intfc);
								link.setIpv6(ipv6);
								return link;
	
							}
						}
					}
				}
			}
		} catch (Exception e) {
			_logger.log(Level.SEVERE, "UdpClLink-" + linkName + ": " +
					"Searching for InetAddress for UDPLink " + linkName, e);
		}
		throw new JDtnException(
				"createUdpClLink(linkName=" + linkName + 
				", ifName=" + ifName +
				", wantIPVF=" + ipv6 +
				") failed to find matching NetworkInterface");

	}
	
	/**
	 * Parse config file for UdpClLink.  It is assume that the parse is sitting
	 * on the &lt; Link &gt; element.  We parse out UdpClLink specific
	 * attributes.  We create the UdpClLink, and then we parse the
	 * &lt; /Link &gt; tag.
	 * @param parser The parser
	 * @param name The Link name
	 * @param linkType The Link type
	 * @return Created UdpClLink
	 * @throws JDtnException on JDTN specific errors
	 * @throws IOException On I/O errors
	 * @throws XMLStreamException on parser errors
	 */
	public static UdpClLink parseLink(
			XmlRDParser parser, 
			String name, 
			Link.LinkType linkType) 
	throws JDtnException, IOException, XmlRdParserException {
		// Link parameters for UdpClLink
		//    ifName="ifname" - REQUIRED
		String ifName = parser.getAttributeValue("ifName");
		if (ifName == null || ifName.length() == 0) {
			throw new JDtnException("Required attribute 'ifName' missing");
		}
		
		//    ipv6="boolean" - OPTIONAL but needed for createudpClLink()
		Boolean ipv6 = Utils.getBooleanAttribute(parser, "ipv6");
		if (ipv6 == null) {
			ipv6 = IPV6_DEFAULT;
		}
		
		// Create the Link
		UdpClLink link = createUdpClLink(name, ifName, ipv6);
		
		// Parse </Link>
		XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
		if (event != XmlRDParser.EventType.END_ELEMENT ||
			!parser.getElementTag().equals("Link")) {
			throw new JDtnException("Missing /Link");
		}
		
		return link;
	}
	
	/**
	 * Write UdpClLink specific attributes to the config file.
	 * @see com.cisco.qte.jdtn.general.Link#writeConfigImpl(java.io.PrintWriter)
	 */
	@Override
	protected void writeConfigImpl(PrintWriter pw) {
		pw.println("        ifName='" + getIfName() + "'");
		if (isIpv6() != IPV6_DEFAULT) {
			pw.println("        ipv6='" + isIpv6() + "'");
		}
	}

	/**
	 * Determine if the network interface corresponding to this Link still has
	 * the InetAddress assigned to it matching the configured properties of
	 * this Link.
	 * @return What I said
	 */
	private boolean hasAssignedIPAddress() {
		IPAddress ipAddress = getIpAddress();
		InetAddress inetAddr = ipAddress.getInetAddress();
		boolean isIpv6 = isIpv6();
		Enumeration<InetAddress> addresses = _networkInterface.getInetAddresses();
		while (addresses.hasMoreElements()) {
			InetAddress inetAddress = addresses.nextElement();
			if (inetAddress.equals(inetAddr)) {
				if (inetAddress instanceof Inet6Address && isIpv6) {
					return true;
				} else if (inetAddress instanceof Inet4Address && !isIpv6) {
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Called on Management Start event.  We open a socket and start up our
	 * Transmit and Receive threads.
	 * @see com.cisco.qte.jdtn.general.Link#linkStartImpl()
	 */
	@Override
	protected void linkStartImpl() throws Exception {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("UdpClLink-" + getName() + ": " + "linkStartImpl()");
		}
		_eventQueue.put(new UdpClEvent(UdpClSubEvents.START_EVENT));
		_eventThread = new Thread(this, "Event-" + getName());
		_eventThread.start();
	}

	/**
	 * Called on Management Stop event.  We stop our Transmit and Receive
	 * Threads and close our socket.
	 * @see com.cisco.qte.jdtn.general.Link#linkStopImpl()
	 */
	@Override
	protected void linkStopImpl() throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("UdpClLink-" + getName() + ": " + "linkStopImpl()");
		}
		_eventQueue.put(new UdpClEvent(UdpClSubEvents.STOP_EVENT));
	}

	/**
	 * The run method for the event processing Thread.  Recevies enqueued
	 * events and dispatches to lower level methods.
	 */
	public void run() {
		try {
			while (!Thread.interrupted()) {
				if (GeneralManagement.isDebugLogging()) {
					_logger.fine("UdpClLink-" + getName() + ": " + 
							"UdpCl EventThread; State=" + _state);
				}
				// Dequeue next event from event queue
				UdpClEvent event = _eventQueue.take();
				if (GeneralManagement.isDebugLogging()) {
					_logger.fine("UdpClLink-" + getName() + ": " + 
							"UdpCl EventThread; Event=" + event.getSubEvent());
				}
				
				// Dispatch event
				switch (event.getSubEvent()) {
				case START_EVENT:
					processStartEvent();
					break;
				case SOCKET_OPENED_EVENT:
					processSocketOpenedEvent();
					break;
				case SOCKET_CLOSED_EVENT:
					processSocketClosedEvent();
					break;
				case STOP_EVENT:
					processStopEvent();
					Thread.currentThread().interrupt();
					break;
				}
			}
		} catch (InterruptedException e) {
			// Nothing
		}
	}
	
	/**
	 * Process a Start Event.  If presently stopped, start opening the
	 * Socket.
	 */
	private void processStartEvent() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("UdpClLink-" + getName() + ": " + 
					"processStartEvent()");
		}
		switch (_state) {
		case STOPPED_STATE:
			_state = UdpClState.OPENING_STATE;
			startSocketOpen();
			break;
			
		default:
			_logger.fine("UdpClLink-" + getName() + ": " + 
					"Ignoring Start Event in state " + _state);
			break;
		}
	}
	
	/**
	 * Process a Socket Opened Event.  Start receiver and transmitter.
	 * @throws InterruptedException  if interrupted
	 */
	private void processSocketOpenedEvent() throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("UdpClLink-" + getName() + ": " + 
					"processSocketOpenedEvent()");
		}
		switch (_state) {
		case OPENING_STATE:
			_state = UdpClState.OPENED_STATE;
			setLinkDatalinkUp(true);
			stopSocketOpen();
			startReceiver();
			startTransmitter();
			break;
			
		default:
			_logger.fine("UdpClLink-" + getName() + ": " + 
					"Ignoring Socket Opened Event in state " + _state);
			break;
		}
	}
	
	/**
	 * Process a 'Socket Closed' Event.  Stop receiver and transmitter, start
	 * Socket opener.
	 * @throws InterruptedException
	 */
	private void processSocketClosedEvent() throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("UdpClLink-" + getName() + ": " + 
					"processSocketClosedEvent()");
		}
		switch (_state) {
		case OPENED_STATE:
			_state = UdpClState.OPENING_STATE;
			setLinkDatalinkUp(false);
			stopTransmitter();
			stopReceiver();
			closeSocket();
			startSocketOpen();
			break;
			
		default:
			_logger.fine("UdpClLink-" + getName() + ": " + 
					"Ignoring Socket Closed Event in state " + _state);
			break;
		}
	}
	
	/**
	 * Process a 'Stop' Event.  Stop all that needs to be stopped.
	 * @throws InterruptedException if interrupted
	 */
	private void processStopEvent() throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("processStopEvent()");
		}
		switch (_state) {
		case STOPPED_STATE:
			_logger.fine("UdpClLink-" + getName() + ": " + 
					"Ignoring Stop Event in state " + _state);
			break;
			
		case OPENING_STATE:
			_state = UdpClState.STOPPED_STATE;
			stopSocketOpen();
			closeSocket();
			_segmentQueue.clear();
			break;
			
		case OPENED_STATE:
			_state = UdpClState.STOPPED_STATE;
			setLinkDatalinkUp(false);
			stopTransmitter();
			stopReceiver();
			closeSocket();
			_segmentQueue.clear();
			break;
		}
	}
	
	/**
	 * Internal method to close the socket.
	 */
	private void closeSocket() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("UdpClLink-" + getName() + ": " + "closeSocket()");
		}
		if (_socket != null) {
			_socket.close();
			_socket = null;
		}
	}

	/**
	 * Start a Thread to open the Socket.
	 */
	public void startSocketOpen() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("UdpClLink-" + getName() + ": " + "startSocketOpen()");
		}
		if (_socketOpenThread == null) {
			_socketOpenThread = new Thread(new SocketOpenerThread());
			_socketOpenThread.setName("udpClOpen-" + getName());
			_socketOpenThread.start();
		}
	}
	
	/**
	 * Stop the Thread that's trying to open the Socket.
	 * @throws InterruptedException If interrupted during the process
	 */
	public void stopSocketOpen() throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("UdpClLink-" + getName() + ": " + "stopSocketOpen()");
		}
		if (_socketOpenThread != null) {
			_socketOpenThread.interrupt();
			_socketOpenThread.join(2000L);
			if (_socketOpenThread.isAlive()) {
				_logger.severe("UdpClLink-" + getName() + ": " + 
						"Socket Opener Thread won't die");
			}
			_socketOpenThread = null;
		}
	}
	
	/**
	 * A Thread which repeatedly tries to open the Socket, with exponential
	 * backoff delay.  If successfully opens Socket, then starts the
	 * Transmitter and Receiver Threads.
	 */
	public class SocketOpenerThread implements Runnable {
		
		public void run() {
			try {
				long backoffDelay = 1000L;
				
				// Repeat until we successfully open socket
				while (_socket == null) {
					// Unless we get interrupted
					if (Thread.interrupted()) {
						break;
					}
					try {
						// Try to open the Socket
						_socket = new DatagramSocket(getUdpPort(),
								getIpAddress().getInetAddress());
						_socket.setReuseAddress(true);
						
						// Successfully opened Socket; tell the state machine
						// and we're done
						_eventQueue.add(new UdpClEvent(UdpClSubEvents.SOCKET_OPENED_EVENT));
						break;

					} catch (SocketException e) {
						if (!e.getMessage().equals("Can't assign requested address")  &&
							!e.getMessage().equals("Address already in use")) {
							_logger.finer("UdpClLink-" + getName() + ": " + 
									"Error opening UdpCl Socket on link "
											+ getName() + ": " + e.getMessage());
						}
						Thread.sleep(backoffDelay);
						backoffDelay *= 2;
						if (backoffDelay > 60000L) {
							backoffDelay = 60000L;
						}
					}
				}
			} catch (InterruptedException e) {
				// Nothing
			}
			if (GeneralManagement.isDebugLogging()) {
				_logger.finer("UdpClLink-" + getName() + ": " + 
						"SocketOpenerThread terminating");
			}
		}
	}
	
	/**
	 * Start the Receiver Thread
	 */
	private void startReceiver() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("UdpClLink-" + getName() + ": " + "startReceiver()");
		}
		if (_receiverThread == null) {
			_receiverThread = new Thread(new Receiver(this), "udpClRx-" + getName());
			_receiverThread.start();
		}
	}
	
	/**
	 * Stop the Receiver Thread
	 * @throws InterruptedException if interrupted during the process
	 */
	private void stopReceiver() throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("UdpClLink-" + getName() + ": " + "stopReceiver()");
		}
		if (_receiverThread != null) {
			// A Thread sitting on a DatagramSocket.receive() will not respond
			// to an interrupt.  Later on, we will close the Socket, and that
			// will break the ReceiverThread out of its receive.  So we just
			// do a short join here.
			_receiverThread.interrupt();
			_receiverThread.join(200L);
			if (_receiverThread.isAlive()) {
				_logger.finer("UdpClLink-" + getName() + ": " + 
						"Receiver Thread won't die");
			}
			_receiverThread = null;
		}
	}

	/**
	 * Receiver Thread
	 */
	public class Receiver implements Runnable {
		private UdpClLink _link;
		
		/**
		 * Constructor
		 * @param link Parent Link of this inner class object
		 */
		public Receiver(UdpClLink link) {
			_link = link;
		}
		
		/**
		 * Thread run method
		 */
		public void run() {
			// Set up for reuse of a single DatagramPacket
			byte[] buf = new byte[MAX_PACKET_LEN];
			DatagramPacket packet = new DatagramPacket(buf, MAX_PACKET_LEN);
			try {
				// Repeat until thread gets interrupted
				while (!Thread.interrupted()) {
					
					if (!isLinkOperational()) {
						if (GeneralManagement.isDebugLogging()) {
							_logger.fine("Link " + getName() + " not operational");
						}
						_eventQueue.add(new UdpClEvent(UdpClSubEvents.SOCKET_CLOSED_EVENT));
						break;
					}
					
					if (!hasAssignedIPAddress()) {
						if (GeneralManagement.isDebugLogging()) {
							_logger.warning("Link " + getName() + " lost IP address");
						}
						_eventQueue.add(new UdpClEvent(UdpClSubEvents.SOCKET_CLOSED_EVENT));
						break;
					}
					
					// Receive a Datagram
					_socket.receive(packet);
					InetAddress remoteInetAddr = packet.getAddress();
					IPAddress ipAddr = new IPAddress(remoteInetAddr);
					
					// Find associated Neighbor from the Datagram remote address
					Neighbor neighbor =
						NeighborsList.getInstance().findNeighborByAddress(ipAddr);
					if (neighbor == null) {
						_logger.severe("UdpClLink-" + getName() + ": " + 
								"No configured neighbor for incoming datagram from " + 
								remoteInetAddr);
						continue;
					} else if (!(neighbor instanceof UdpClNeighbor)) {
						_logger.severe("UdpClLink-" + getName() + ": " + 
								"Incoming datagram from non-UDP CL Neighbor: " +
								remoteInetAddr);
						continue;
					}
					UdpClNeighbor udpClNeighbor = (UdpClNeighbor)neighbor;
					
					// Package and copy received data into a Block and deliver
					// it to UdpClAPI for further processing.
					UdpClDataBlock block = new UdpClDataBlock(
							buf, 
							packet.getLength(), 
							_link, 
							udpClNeighbor, 
							null);
					if (GeneralManagement.isDebugLogging() &&
							_logger.isLoggable(Level.FINEST)) {
							_logger.finest("UdpClLink-" + getName() + ": " + 
									"Received Datagram from " +
									ipAddr.toParseableString());
							_logger.finest(block.dump("", true));
					}
					UdpClManagement.getStatistics().nBlocksRcvd++;
					UdpClAPI.getInstance().notifyInboundBlock(
							block, packet.getLength());
				} // while !Thread.interrupted
				
			} catch (SocketException e) {
				if (!e.getMessage().equals("Socket closed")) {
					_logger.log(Level.SEVERE, "UdpClLink-" + getName() + ": " + 
							"socket.receive()", e);
				} else if (GeneralManagement.isDebugLogging()) {
					_logger.fine("UdpClLink-" + getName() + ": " + 
							"Socket has been closed");
				}
				_eventQueue.add(new UdpClEvent(UdpClSubEvents.SOCKET_CLOSED_EVENT));
				
			} catch (IOException e) {
				_logger.log(Level.SEVERE, "UdpClLink-" + getName() + ": " + 
						"socket.receive()", e);
				_eventQueue.add(new UdpClEvent(UdpClSubEvents.SOCKET_CLOSED_EVENT));
			} // top level try
			
			if (GeneralManagement.isDebugLogging()) {
				if (Thread.interrupted()) {
					_logger.finer("UdpClLink-" + getName() + ": " + 
							"ReceiverThread interrupted");
				}
				_logger.finer("UdpClLink-" + getName() + ": " + 
						"ReceiverThread terminating");
			}
		} // run()
	}
	
	/**
	 * Start the Transmitter Thread
	 */
	private void startTransmitter() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("UdpClLink-" + getName() + ": " + 
					"startTransmitter()");
		}
		if (_transmitterThread == null) {
			_transmitterThread = new Thread(new Transmitter(), "udpclTx-" + getName());
			_transmitterThread.start();
		}
	}
	
	/**
	 * Stop the Transmitter Thread
	 * @throws InterruptedException if interrupted during processing
	 */
	private void stopTransmitter() throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("UdpClLink-" + getName() + ": " + 
					"stopTransmitter()");
		}
		if (_transmitterThread != null) {
			_transmitterThread.interrupt();
			_transmitterThread.join(2000L);
			if (_transmitterThread.isAlive()) {
				_logger.severe("Transmitter Thread won't die");
			}
			_transmitterThread = null;
		}
	}
	
	/**
	 * Transmitter Thread
	 */
	public class Transmitter implements Runnable {
		
		/**
		 * Thread run method
		 */
		public void run() {
			try {
				// Repeat until we're interrupted
				while (!Thread.interrupted()) {
					
					if (!isLinkOperational()) {
						if (GeneralManagement.isDebugLogging()) {
							_logger.fine("Link " + getName() + " is not operational");
						}
						_eventQueue.add(new UdpClEvent(UdpClSubEvents.SOCKET_CLOSED_EVENT));
						break;
					}
					
					if (!hasAssignedIPAddress()) {
						if (GeneralManagement.isDebugLogging()) {
							_logger.fine("Link " + getName() + " lost IP Address");
						}
						_eventQueue.add(new UdpClEvent(UdpClSubEvents.SOCKET_CLOSED_EVENT));
						break;
					}
					
					// Get next element from our queue
					LeakyBucketQueueElement elem =
						_segmentQueue.dequeue();
					if (elem != null) {
						
						if (elem instanceof LeakyBucketQueueDelayElement) {
							// Leaky bucket calls for a delay for throttling
							LeakyBucketQueueDelayElement delayElement =
								(LeakyBucketQueueDelayElement)elem;
							if (GeneralManagement.isDebugLogging()) {
								_logger.finer("Link " + getName() + " delay for " +
										delayElement.getDelayMSecs());
							}
							Thread.sleep(delayElement.getDelayMSecs());
							
						} else if (elem instanceof UdpClDataBlock) {
							// Queue element is a block to transmit
							UdpClDataBlock block = (UdpClDataBlock)elem;
							LinkAddress linkAddress =
								block.neighbor.findOperationalLinkAddress();
							Address address = linkAddress.getAddress();
							if (!(address instanceof IPAddress)) {
								_logger.log(
										Level.SEVERE, 
										"UdpClLink-" + getName() + ": " + 
										"Block desintation address is not IPAddress");
								UdpClManagement.getStatistics().nBlocksSentErrors++;
								continue;
							}
							IPAddress ipAddr = (IPAddress)address;
							
							if (GeneralManagement.isDebugLogging() &&
								_logger.isLoggable(Level.FINEST)) {
								_logger.finest("UdpClLink-" + getName() + ": " + 
										"Transmitting Datagram to " +
										ipAddr.toParseableString());
								_logger.finest(block.dump("", true));
							}
							
							/// Wrap it in a DatagramPacket
							DatagramPacket packet = new DatagramPacket(
									block.buffer, 
									block.length, 
									ipAddr.getInetAddress(), 
									getUdpPort());
							try {
								// Send the Datagram
								_socket.send(packet);
								UdpClAPI.getInstance().blockTransmitted(block);
								UdpClManagement.getStatistics().nBlocksSent++;
								
							} catch (IOException e) {
								if (e.getMessage().equals("Can't assign requested address")) {
									_logger.warning("Link " + getName() + " is down");
								} else {
									_logger.log(
											Level.SEVERE, 
											"UdpClLink-" + getName() + ": " + 
											"DatagramPacket.send", e);
								}
								UdpClAPI.getInstance().notifyOutboundBlockError(block, e);
								UdpClManagement.getStatistics().nBlocksSentErrors++;
								_eventQueue.add(new UdpClEvent(UdpClSubEvents.SOCKET_CLOSED_EVENT));
								break;
							}
							
						} else {
							_logger.severe("UdpClLink-" + getName() + ": " + 
									"Unknown LeakyBucketQueueElement type");
						}
					} // if elem != null
				} // while !Thread.interrupted
			} catch (InterruptedException e) {
				// Nothing
				if (GeneralManagement.isDebugLogging()) {
					_logger.finer("UdpClLink-" + getName() + ": " + 
							"TransmitterThread got InterruptedException");
				}
			}
			
			if (GeneralManagement.isDebugLogging()) {
				if (Thread.interrupted()) {
					_logger.finer("UdpClLink-" + getName() + ": " + 
							"TransmitterThread interrupted");
				}
				_logger.finer("UdpClLink-" + getName() + ": " + 
						"TransmitterThread terminating");
			}
		}
	}
	
	/**
	 * Dump this Object
	 * @param indent How much to indent
	 * @param detailed if want detailed dump
	 * @return String containing Dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "UdpClLink\n");
		sb.append(super.dump(indent + "  ", detailed));
		sb.append(indent + "  ifName=" + getIfName() + "\n");
		sb.append(indent + "  ipv6=" + isIpv6() + "\n");
		sb.append(indent + "  ipAddress=" + getIpAddress() + "\n");
		sb.append(indent + "  udpPort=" + getUdpPort() + "\n");
		sb.append(indent + "  State=" + _state + "\n");
		sb.append(indent + "  socket=" +
				(_socket != null ? "open" : "not open") + "\n");
		sb.append(indent + "  xmitThread=" + 
				(_transmitterThread != null ? "running" : "stopped") + "\n");
		sb.append(indent + "  recvThread=" + 
				(_receiverThread != null ? "running" : "stopped") + "\n");
		
		return sb.toString();
	}
	
	/*
	 * @see com.cisco.qte.jdtn.general.Link#linkRemovedImpl()
	 */
	@Override
	protected void linkRemovedImpl() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("UdpClLink-" + getName() + ": " + "linkRemovedImpl()");
		}
	}

	/*
	 * @see com.cisco.qte.jdtn.general.Link#getLinkTypeImpl()
	 */
	@Override
	protected LinkType getLinkTypeImpl() {
		return LinkType.LINK_TYPE_UDPCL;
	}

	/** UDP Port */
	public int getUdpPort() {
		return _udpPort;
	}

	/** UDP Port */
	public void setUdpPort(int udpPort) {
		this._udpPort = udpPort;
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

	/**
	 * Get the LeakyBucketQueueCollection for this Link
	 * @return What I said
	 */
	protected LeakyBucketQueueCollection getSegmentQueue() {
		return _segmentQueue;
	}
	
}
