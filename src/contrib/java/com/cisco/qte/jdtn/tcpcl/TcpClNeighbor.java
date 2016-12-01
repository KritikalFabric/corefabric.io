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
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Link;
import com.cisco.qte.jdtn.general.LinkAddress;
import com.cisco.qte.jdtn.general.Neighbor;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * TCP Convergence Layer Neighbor; implements TCP CL properties and runs the
 * TCP CL protocol.
 */
public class TcpClNeighbor extends Neighbor {
	private static final Logger _logger =
		Logger.getLogger(TcpClNeighbor.class.getCanonicalName());
	
	public static final boolean INITIATOR_DEFAULT = true;
	public static final boolean ACK_DATA_SEGS_DEFAULT = true;
	
	public static final boolean KEEP_ALIVES_DEFAULT = true;
	/** We do not support reactive fragmentation */
	public static final boolean REACTIVE_FRAGMENTATION_DEFAULT = false;
	
	public static final int KEEPALIVE_INTERVAL_SECS_DEFAULT = 10;
	public static final int KEEPALIVE_INTERVAL_SECS_INFINITY = 0;
	public static final int KEEPALIVE_INTERVAL_SECS_MIN = 0;
	public static final int KEEPALIVE_INTERVAL_SECS_MAX = Integer.MAX_VALUE;
	
	public static final boolean DELAY_BEFORE_RECONNECTION_DEFAULT = false;
	
	public static final int RECONNECTION_DELAY_SECS_DEFAULT = 4;
	public static final int RECONNECTION_DELAY_SECS_MIN = 0;
	public static final int RECONNECTION_DELAY_SECS_MAX = Integer.MAX_VALUE;
	public static final int RECONNECTION_DELAY_SECS_INFINITY = 0;
	
	public static final int IDLE_KEEPALIVE_MULTIPLIER = 2;
	public static final boolean IDLE_CONNECTION_SHUTDOWN_DEFAULT = true;
	public static final int IDLE_CONNECTION_SHUTDOWN_DELAY_SECS_DEFAULT =
		IDLE_KEEPALIVE_MULTIPLIER * KEEPALIVE_INTERVAL_SECS_DEFAULT;
	public static final int IDLE_CONNECTION_SHUTDOWN_DELAY_SECS_MIN = 0;
	public static final int IDLE_CONNECTION_SHUTDOWN_DELAY_SECS_MAX = 65535;
	public static final int IDLE_CONNECTION_SHUTDOWN_DELAY_SECS_INFINITY = 0;
	
	/** We support receipt of Nacks but don't send them */
	public static final boolean NACK_DATA_SEGMENTS_DEFAULT = true;
	
	/** Whether to acknowledge Data Segments */
	private boolean _ackDataSegments = ACK_DATA_SEGS_DEFAULT;
	/** Whether to send Keepalive Segments */
	private boolean _keepAlive = KEEP_ALIVES_DEFAULT;
	/** Number of seconds between Keepalive Segments */
	private int _keepAliveIntervalSecs = KEEPALIVE_INTERVAL_SECS_DEFAULT;
	/** Whether to delay before reconnection */
	private boolean _delayBeforeReconnection = DELAY_BEFORE_RECONNECTION_DEFAULT;
	/** Reconnection Delay -- 0 = infinite delay */
	private int _reconnectionDelaySecs = RECONNECTION_DELAY_SECS_DEFAULT;
	/** Whether we are to shutdown the connection when idle */
	private boolean _idleConnectionShutdown = IDLE_CONNECTION_SHUTDOWN_DEFAULT;
	/** How long must connection be idle before shutdown, secs; 0=infinity */
	private int _idleConnectionShutdownDelaySecs = 
		IDLE_CONNECTION_SHUTDOWN_DELAY_SECS_DEFAULT;
	
	private TcpClStateMachine _initiatorStateMachine = null;
	private TcpClStateMachine _acceptorStateMachine = null;
	
	/**
	 * Constructor - sets name
	 * @param name Name of TcpClNeighbor - locally significant
	 */
	public TcpClNeighbor(String name) {
		super(name);
		_initiatorStateMachine = new TcpClStateMachine(this);
	}
	
	/**
	 * Parse TcpClNeighbor specific attributes of the &lt; Link &gt;
	 * element.  Parse is advanced past the &lt; /Link &gt; tag.
	 * @param parser The Parser
	 * @param name The name of the TcpClNeighbor
	 * @param neighborType Type of Neighbor
	 * @return Newly created Neighbor
	 * @throws JDtnException on TcpClNeighbor specific errors
	 * @throws IOException on I/O errors
	 * @throws XMLStreamException on XML parsing errors
	 * @throws InterruptedException 
	 */
	public static TcpClNeighbor parseNeighbor(
			XmlRDParser parser, 
			String name, 
			Neighbor.NeighborType neighborType)
	throws JDtnException, XmlRdParserException, IOException, InterruptedException {
		
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("parseLink");
		}
		TcpClNeighbor neighbor = new TcpClNeighbor(name);
		
		// TcpClNeighbor specific attributes; all optional
		// ackDataSegments='t/f'
		Boolean boolVal = Utils.getBooleanAttribute(parser, "ackDataSegments");
		if (boolVal != null) {
			neighbor.setAckDataSegments(boolVal);
		}
		
		// keepAlive='t/f'
		boolVal = Utils.getBooleanAttribute(parser, "keepAlive");
		if (boolVal != null) {
			neighbor.setKeepAlive(boolVal);
		}
		
		// keepAliveInterval='n'
		Integer intVal = Utils.getIntegerAttribute(
				parser, 
				"keepAliveInterval", 
				KEEPALIVE_INTERVAL_SECS_MIN, 
				KEEPALIVE_INTERVAL_SECS_MAX);
		if (intVal != null) {
			neighbor.setKeepAliveIntervalSecs(intVal);
		}
		
		// delayBeforeReconnection='t/f'
		boolVal = Utils.getBooleanAttribute(parser, "delayBeforeReconnection");
		if (boolVal != null) {
			neighbor.setDelayBeforeReconnection(boolVal);
		}
		
		// reconnectionDelay='n'
		intVal = Utils.getIntegerAttribute(
				parser, 
				"reconnectionDelay", 
				RECONNECTION_DELAY_SECS_MIN, 
				RECONNECTION_DELAY_SECS_MAX);
		if (intVal != null) {
			neighbor.setReconnectionDelaySecs(intVal);
		}
		
		// idleConnectionShutdown='t/f'
		boolVal = Utils.getBooleanAttribute(parser, "idleConnectionShutdown");
		if (boolVal != null) {
			neighbor.setIdleConnectionShutdown(boolVal);
		}
		
		// idleConnectionShutdownDelay='t/f'
		intVal = Utils.getIntegerAttribute(
				parser, 
				"idleConnectionShutdownDelay", 
				IDLE_CONNECTION_SHUTDOWN_DELAY_SECS_MIN, 
				IDLE_CONNECTION_SHUTDOWN_DELAY_SECS_MAX);
		if (intVal != null) {
			neighbor.setIdleConnectionShutdownDelaySecs(intVal);
		}
		
		// Parse any enclosed 'LinkAddress' elements
		XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
		while (event == XmlRDParser.EventType.START_ELEMENT &&
				parser.getElementTag().equals("LinkAddress")) {
			
			LinkAddress linkAddr = LinkAddress.parse(parser);
			neighbor.addLinkAddress(linkAddr);
			
			event = Utils.nextNonTextEvent(parser);
		}
		
		// Advance parse past the </Neighbor> tag
		if (event != XmlRDParser.EventType.END_ELEMENT ||
			!parser.getElementTag().equals("Neighbor")) {
			throw new JDtnException("Missing '</Neighbor>'");
		}
		
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("parseLink DONE");
		}
		return neighbor;
	}
	
	/*
	 * Output TcpClNeighbor specific attributes to configuration file.
	 * @see com.cisco.qte.jdtn.general.Neighbor#writeConfigImpl(java.io.PrintWriter)
	 */
	@Override
	protected void writeConfigImpl(PrintWriter pw) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("writeConfigImpl");
		}
		if (isAckDataSegments() != ACK_DATA_SEGS_DEFAULT) {
			pw.println("          ackDataSegments='" + isAckDataSegments() + "'");
		}
		if (isKeepAlive() != KEEP_ALIVES_DEFAULT) {
			pw.println("          keepAlives='" + isKeepAlive() + "'");
		}
		if (getKeepAliveIntervalSecs() != KEEPALIVE_INTERVAL_SECS_DEFAULT) {
			pw.println("          keepAliveInterval='" + getKeepAliveIntervalSecs() + "'");
		}
		if (isDelayBeforeReconnection() != DELAY_BEFORE_RECONNECTION_DEFAULT) {
			pw.println("          delayBeforeReconnection='" + isDelayBeforeReconnection() + "'");
		}
		if (getReconnectionDelaySecs() != RECONNECTION_DELAY_SECS_DEFAULT) {
			pw.println("          reconnectionDelay='" + getReconnectionDelaySecs() + "'");
		}
		if (isIdleConnectionShutdown() != IDLE_CONNECTION_SHUTDOWN_DEFAULT) {
			pw.println("          idleConnectionShutdown='" + isIdleConnectionShutdown() + "'");
		}
		if (getIdleConnectionShutdownDelaySecs() != IDLE_CONNECTION_SHUTDOWN_DELAY_SECS_DEFAULT) {
			pw.println("          idleConnectionShutdownDelay='" + getIdleConnectionShutdownDelaySecs() + "'");
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("writeConfigImpl DONE");
		}
	}

	/**
	 * Start protocol operation
	 */
	@Override
	protected void startImpl() {
		super.startImpl();
		_initiatorStateMachine.start();
	}
	
	/**
	 * Stop protocol operation
	 * @throws InterruptedException 
	 */
	@Override
	protected void stopImpl() throws InterruptedException {
		_initiatorStateMachine.stop();
		if (_acceptorStateMachine != null) {
			_acceptorStateMachine.stop();
			_acceptorStateMachine = null;
		}
		super.stopImpl();
	}
	
	/**
	 * Dump this object
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "TcpClNeighbor\n");
		sb.append(super.dump(indent + "  ", detailed));
		sb.append(indent + "  AckDataSegs=" + isAckDataSegments() + "\n");
		sb.append(indent + "  KeepAlive=" + isKeepAlive() + "\n");
		if (isKeepAlive()) {
			sb.append(indent + "  KeepAliveInterval=" + getKeepAliveIntervalSecs() + "\n");
		}
		sb.append(indent + "  DelayBeforeReconnect=" + isDelayBeforeReconnection() + "\n");
		if (isDelayBeforeReconnection()) {
			sb.append(indent + "DelayBeforeReconnectInterval=" + getReconnectionDelaySecs() + " secs\n");
		}
		sb.append(indent + "  IdleConnectionShutdown=" + isIdleConnectionShutdown() + "\n");
		if (isIdleConnectionShutdown()) {
			sb.append(indent + "  IdleConnectionShutdownInterval=" + getIdleConnectionShutdownDelaySecs() + " secs\n");
		}
		sb.append(indent + "  InitiatorStateMachine\n");
		sb.append(_initiatorStateMachine.dump(indent + "  ", detailed));
		if (_acceptorStateMachine != null) {
			sb.append(indent + "  AcceptorStateMachine\n");
			sb.append(_acceptorStateMachine.dump(indent + "  ", detailed));
		}
		return sb.toString();
	}
	
	/**
	 * Find an Operational TcpClLink by which this Neighbor may be reached
	 * @return Link or null if none
	 */
	public TcpClLink findOperationalTcpClLink() {
		for (LinkAddress linkAddress : getLinkAddresses()) {
			Link link = linkAddress.getLink();
			if (link != null &&
				link.isLinkOperational() &&
				link instanceof TcpClLink) {
				return (TcpClLink)link;
			}
		}
		return null;
	}
	
	/**
	 * Request from upper layer to transmit given Data Block
	 * @param block Given Data Block
	 * @throws InterruptedException if interrupted waiting for queue space
	 */
	public void enqueueOutboundBlock(TcpClDataBlock block) 
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("enqueueOutboundBlock");
		}
		_initiatorStateMachine.enqueueOutboundBlock(block);
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("enqueueOutboundBlock DONE");
		}
	}
	
	/**
	 * Notification from Link that a Connection has been accepted
	 * @param socket Socket representing accepted connection
	 * @throws InterruptedException If interrupted during process
	 * @throws IOException 
	 * @throws JDtnException 
	 */
	public void notifyConnectionAccepted(Socket socket) 
	throws InterruptedException, JDtnException, IOException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("notifyConnectionAccepted");
		}
		if (_acceptorStateMachine != null) {
			_acceptorStateMachine.stop();
			_acceptorStateMachine = null;
		}
		_acceptorStateMachine = new TcpClStateMachine(this, socket);
	}
	
	/**
	 * Notification from State Machine that it has detected a closed
	 * connection.  If the State Machine is an Acceptor, then we shut it
	 * down (Next connection will launch a new State Machine).
	 * @param stateMachine The State Machine detecting the closed connection.
	 */
	public void notifyConnectionClosed(TcpClStateMachine stateMachine) {
		if (stateMachine.isAcceptor()) {
			try {
				stateMachine.stop();
			} catch (InterruptedException e) {
				_logger.log(Level.SEVERE, "stopping state machine", e);
			}
			_acceptorStateMachine = null;
		}
	}
	
	/*
	 * @see com.cisco.qte.jdtn.general.Neighbor#getType()
	 */
	@Override
	public NeighborType getType() {
		return Neighbor.NeighborType.NEIGHBOR_TYPE_TCPCL;
	}

	/**
	 * Called when a Link associated with this Neighbor is being removed
	 * @see com.cisco.qte.jdtn.general.Neighbor#removeLinkImpl()
	 */
	@Override
	protected void removeLinkImpl() {
		// XXX
		// Nothing
	}

	/**
	 * Called when a Link changes operational state
	 * @see com.cisco.qte.jdtn.general.LinkListener#onLinkOperationalStateChange(com.cisco.qte.jdtn.general.Link, boolean)
	 */
	@Override
	public void onLinkOperationalStateChange(Link link, boolean linkOperational) {
		// XXX 
		// Nothing
	}

	/**
	 * Called when Neighbor is added 
	 * @see com.cisco.qte.jdtn.general.LinkListener#onNeighborAdded(com.cisco.qte.jdtn.general.Link, com.cisco.qte.jdtn.general.Neighbor)
	 */
	@Override
	public void onNeighborAdded(Link link, Neighbor neighbor) {
		// XXX
		// Nothing
	}

	/**
	 * Called when a Neighbor is deleted
	 * @see com.cisco.qte.jdtn.general.LinkListener#onNeighborDeleted(com.cisco.qte.jdtn.general.Link, com.cisco.qte.jdtn.general.Neighbor)
	 */
	@Override
	public void onNeighborDeleted(Link link, Neighbor neighbor) {
		if (_acceptorStateMachine != null) {
			try {
				_acceptorStateMachine.stop();
			} catch (InterruptedException e) {
				_logger.log(Level.SEVERE, "accepterStateMachine stop", e);
			}
			_acceptorStateMachine = null;
		}
		try {
			_initiatorStateMachine.stop();
		} catch (InterruptedException e) {
			_logger.log(Level.SEVERE, "initiatorStateMachine stop", e);
		}
	}

	/**
	 * Called when this neighbor undergoes an Operational State Change
	 * @see com.cisco.qte.jdtn.general.LinkListener#onNeighborOperationalChange(com.cisco.qte.jdtn.general.Neighbor, boolean)
	 */
	@Override
	public void onNeighborOperationalChange(Neighbor neighbor,
			boolean neighborState) {
		// Nothing
	}

	/**
	 * Called when this neighbor undergoes a scheduled state change
	 * @see com.cisco.qte.jdtn.general.LinkListener#onNeighborScheduledStateChange(com.cisco.qte.jdtn.general.Neighbor, boolean)
	 */
	@Override
	public void onNeighborScheduledStateChange(Neighbor neighbor,
			boolean neighborState) throws InterruptedException {
		// Nothing
	}

	/**
	 * Nothing necessary when Neighbor removed (assuming it gets stopped)
	 * @see com.cisco.qte.jdtn.general.Neighbor#removeNeighborImpl()
	 */
	@Override
	protected void removeNeighborImpl() {
		// Nothing
	}

	/** Whether to acknowledge Data Segments */
	public boolean isAckDataSegments() {
		return _ackDataSegments;
	}

	/** Whether to acknowledge Data Segments 
	 * @throws InterruptedException */
	public void setAckDataSegments(boolean ackDataSegments) throws InterruptedException {
		boolean previouslyStarted = isStarted();
		if (previouslyStarted) {
			stop();
		}
		_ackDataSegments = ackDataSegments;
		if (previouslyStarted) {
			start();
		}
	}

	/** Whether to send Keepalive Segments */
	public boolean isKeepAlive() {
		return _keepAlive;
	}

	/** Whether to send Keepalive Segments 
	 * @throws InterruptedException */
	public void setKeepAlive(boolean keepAlive) throws InterruptedException {
		boolean previouslyStarted = isStarted();
		if (previouslyStarted) {
			stop();
		}
		_keepAlive = keepAlive;
		if (!_keepAlive) {
			_keepAliveIntervalSecs = KEEPALIVE_INTERVAL_SECS_INFINITY;
		}
		if (previouslyStarted) {
			start();
		}
	}

	/** Number of seconds between Keepalive Segments */
	public int getKeepAliveIntervalSecs() {
		return _keepAliveIntervalSecs;
	}

	/** Number of seconds between Keepalive Segments 
	 * @throws InterruptedException */
	public void setKeepAliveIntervalSecs(int keepAliveIntervalSecs)
	throws IllegalArgumentException, InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("setKeepAliveIntervalSecs");
		}
		if (keepAliveIntervalSecs < KEEPALIVE_INTERVAL_SECS_MIN ||
			keepAliveIntervalSecs > KEEPALIVE_INTERVAL_SECS_MAX) {
			throw new IllegalArgumentException(
					"KeepAlive Interval must be positive integer");
		}
		boolean previouslyStarted = isStarted();
		if (previouslyStarted) {
			stop();
		}
		_keepAliveIntervalSecs = keepAliveIntervalSecs;
		if (_idleConnectionShutdownDelaySecs < IDLE_KEEPALIVE_MULTIPLIER * _keepAliveIntervalSecs) {
			_idleConnectionShutdownDelaySecs = IDLE_KEEPALIVE_MULTIPLIER * _keepAliveIntervalSecs;
		}
		if (previouslyStarted) {
			start();
		}
	}

	/** Whether to delay before reconnection */
	public boolean isDelayBeforeReconnection() {
		return _delayBeforeReconnection;
	}

	/** Whether to delay before reconnection 
	 * @throws InterruptedException */
	public void setDelayBeforeReconnection(boolean delayBeforeReconnection) 
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("setDelayBeforeReconnection");
		}
		boolean previouslyStarted = isStarted();
		if (previouslyStarted) {
			stop();
		}
		_delayBeforeReconnection = delayBeforeReconnection;
		if (previouslyStarted) {
			start();
		}
	}

	/** Reconnection Delay, Secs, 0 = infinite delay */
	public int getReconnectionDelaySecs() {
		return _reconnectionDelaySecs;
	}

	/** Reconnection Delay, Secs, 0 = infinite delay 
	 * @throws InterruptedException */
	public void setReconnectionDelaySecs(int reconnectionDelaySecs)
	throws IllegalArgumentException, InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("setReconnectionDelaySecs");
		}
		if (reconnectionDelaySecs < RECONNECTION_DELAY_SECS_MIN ||
			reconnectionDelaySecs > RECONNECTION_DELAY_SECS_MAX) {
			throw new IllegalArgumentException(
					"Reconnection Delay must be in the ragnge [" +
					RECONNECTION_DELAY_SECS_MIN + ", " +
					RECONNECTION_DELAY_SECS_MAX + "]");
		}
		boolean previouslyStarted = isStarted();
		if (previouslyStarted) {
			stop();
		}
		_reconnectionDelaySecs = reconnectionDelaySecs;
		if (previouslyStarted) {
			start();
		}
	}

	/** Whether we are to shutdown the connection when idle */
	public boolean isIdleConnectionShutdown() {
		return _idleConnectionShutdown;
	}
	
	/** Whether we are to shutdown the connection when idle 
	 * @throws InterruptedException */
	public void setIdleConnectionShutdown(boolean idleConnectionShutdown) throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("setIdleConnectionShutdown");
		}
		boolean previouslyStarted = isStarted();
		if (previouslyStarted) {
			stop();
		}
		_idleConnectionShutdown = idleConnectionShutdown;
		if (previouslyStarted) {
			start();
		}
	}

	/** Whether we are to shutdown the connection when idle */
	public int getIdleConnectionShutdownDelaySecs() {
		return _idleConnectionShutdownDelaySecs;
	}

	/** How long must connection be idle before shutdown, secs; 0=infinity 
	 * @throws InterruptedException */
	public void setIdleConnectionShutdownDelaySecs(
			int idleConnectionShutdownDelaySecs) 
	throws IllegalArgumentException, InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("setIdleConnectionShutdownDelaySecs");
		}
		if (idleConnectionShutdownDelaySecs < IDLE_CONNECTION_SHUTDOWN_DELAY_SECS_MIN ||
			idleConnectionShutdownDelaySecs > IDLE_CONNECTION_SHUTDOWN_DELAY_SECS_MAX) {
			throw new IllegalArgumentException(
					"IdleConnectionShutdownDelaySecs must be in range [" +
					IDLE_CONNECTION_SHUTDOWN_DELAY_SECS_MIN + ", " +
					IDLE_CONNECTION_SHUTDOWN_DELAY_SECS_MAX + "]");
		}
		if (_idleConnectionShutdownDelaySecs < IDLE_KEEPALIVE_MULTIPLIER * _keepAliveIntervalSecs) {
			throw new IllegalArgumentException(
					"IdleConnectionShutdownDelaySecs must be at least " +
					IDLE_KEEPALIVE_MULTIPLIER + " * KeepAliveIntervalSecs (" +
					_keepAliveIntervalSecs + ")");
		}
		boolean previouslyStarted = isStarted();
		if (previouslyStarted) {
			stop();
		}
		_idleConnectionShutdownDelaySecs = idleConnectionShutdownDelaySecs;
		if (previouslyStarted) {
			start();
		}
	}

	/** How long must connection be idle before shutdown, secs; 0=infinity */
	public static int getIdleConnectionShutdownDelaySecsDefault() {
		return IDLE_CONNECTION_SHUTDOWN_DELAY_SECS_DEFAULT;
	}

}
