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
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.bp.BPManagement;
import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.LinkAddress;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.tcpcl.TcpClEvent.SubEventTypes;
import com.cisco.qte.jdtn.tcpcl.TcpClNeighbor;

/**
 * TCP Convergence Layer State Machine for TcpClNeighbor; There can be two
 * instances of this running per Neighbor:
 * <ul>
 *   <li> "Initiator", or "Active" instance; constructed when the Neighbor is
 *        constructed; starts in STOPPED state.  Responsible for initiating
 *        a connection to the Neighboring peer when demand (in the form of
 *        blocks to send) arises.
 *   <li> "Acceptor", or "Passive" instance; constructed when we accept a
 *        TCP connection from the Neighboring peer.  Starts in WAIT_HEADER
 *        state.
 * </ul>
 */
public class TcpClStateMachine extends AbstractStartableComponent {
	private static final Logger _logger =
		Logger.getLogger(TcpClStateMachine.class.getCanonicalName());
	
	private static final int EVENT_QUEUE_CAPACITY = 32;
	private static final int SOCKET_RECV_BUFFER = 64 * 1024;
	private static final long ACK_TIMEOUT_MSECS = 10000L;
	
	private TcpClNeighbor _neighbor = null;
	
	private enum SenderState {
		/** We are stopped, awaiting a start event from management */
		STOPPED_STATE,
		/** We are awaiting demand for service or a remote connection */
		IDLE_STATE,
		/** We are awaiting completion of connection initiation */
		CONN_INITIATOR_STATE,
		/** We are awaiting arrival of Contact Header from peer */
		WAIT_HEADER_STATE,
		/** We are waiting for a block to transmit */
		WAIT_BLOCK_TO_SEND_STATE,
		/** We are waiting for an Ack from the last segment transmitted */
		WAIT_ACK_STATE,
		/** We have closed the connection and are doing a timed wait before reconnect */
		DELAY_BEFORE_RECONNECT_STATE
	}
	private SenderState _senderState = SenderState.STOPPED_STATE;
	private Socket _socket = null;
	private ArrayBlockingQueue<TcpClEvent> _eventQueue = 
		new ArrayBlockingQueue<TcpClEvent>(EVENT_QUEUE_CAPACITY);
	private Thread _initiatorThread = null;
	private Thread _receiverThread = null;
	private Timer _idleTimer = null;
	private Timer _reconnectTimer = null;
	private Timer _keepAliveTimer = null;
	private Timer _ackTimer = null;
	
	/** Negotiated acknowledge data segments */
	private boolean _negotiatedAckDataSegments = 
		TcpClNeighbor.ACK_DATA_SEGS_DEFAULT;
	/** Negotiated reactive fragmentation */
	private boolean _negotiatedReactiveFragmentation = 
		TcpClNeighbor.REACTIVE_FRAGMENTATION_DEFAULT;
	/** Negotiated Bundle Naks */
	private boolean _negotiatedNackDataSegments = 
		TcpClNeighbor.NACK_DATA_SEGMENTS_DEFAULT;
	/** Negotiated KeepAlive Time, Secs */
	private int _negotiatedKeepAliveTimeSecs = 
		TcpClNeighbor.KEEPALIVE_INTERVAL_SECS_DEFAULT;
	/** Idle Timer derived from Negotiated KeepAliveTime, Secs */
	private int _negotiatedIdleTimeSecs =
		TcpClNeighbor.IDLE_KEEPALIVE_MULTIPLIER * TcpClNeighbor.KEEPALIVE_INTERVAL_SECS_DEFAULT;
	
	// State variables keeping track of Block Transmit in progress
	/** Block currently being transmitted */
	private TcpClDataBlock _currentXmitBlock = null;
	/** Next offset into current block to transmit */
	private long _currentXmitBlockOffset = 0;
	/** Length of segment of current block in process of transmission */
	private int _currentXmitSegmentLength = 0;
	/** Total length of current block */
	private long _currentXmitBlockLength = 0;
	/** Cumulative number of bytes in current block transmitted */
	private long _currentCumXmitBlockLength = 0;
	/** List of outgoing Blocks awaiting transmission */
	private ArrayList<TcpClDataBlock> _pendingXmitBlocks = new ArrayList<TcpClDataBlock>();
	
	// State variables keeping track of Block Receipt in progress
	/** List of Segments received for current block being received */
	private ArrayList<TcpClDataSegment> _currentRecvBlock =
		new ArrayList<TcpClDataSegment>();
	/** Cumulative number of bytes received in block currently being received */
	private long _recvBlockCumReceivedLength = 0;
	
	private Thread _eventThread = null;
	private boolean _isAccept = false;
	
	public static final long CONNECTION_BACKOFF_MIN = 1000;
	public static final long CONNECTION_BACKOFF_MAX = 60000;
	private long _connectionBackoff = CONNECTION_BACKOFF_MIN;
	
	/**
	 * Constructor for an "active" connection; a Connection initiated by
	 * this host to the Neighbor.  We start the state machine in 
	 * STOPPED_STATE, awaiting a management START event.
	 * @param neighbor The Neighbor
	 */
	public TcpClStateMachine(TcpClNeighbor neighbor) {
		super("TcpClStateMachine(" + neighbor.getName() + ")");
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("TcpClStateMachine constructor; initiator");
		}
		_isAccept = false;
		_neighbor = neighbor;
		_senderState = SenderState.STOPPED_STATE;
		startEventThread();
	}

	/**
	 * Constructor for a "passive" connection; a Connection initiated by
	 * the Neighbor and accepted by this host.  The connection is already
	 * established so we jump directly to WAIT_HEADER_STATE.
	 * @param neighbor
	 * @param socket
	 * @throws IOException 
	 * @throws JDtnException 
	 * @throws InterruptedException 
	 */
	public TcpClStateMachine(TcpClNeighbor neighbor, Socket socket) 
	throws JDtnException, IOException, InterruptedException {
		super("TcpClStateMachine(" + neighbor.getName() + ")");
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("TcpClStateMachine constructor; acceptor");
			_logger.finer("Accepted connection from " + neighbor.getName());
		}
		_isAccept = true;
		_neighbor = neighbor;
		_socket = socket;
		_senderState = SenderState.WAIT_HEADER_STATE;
		startIdleTimer();
		startReceiver(_socket);
		restartKeepAliveTimer();
		startEventThread();
	}
		
	/**
	 * Start the event processing thread for this state machine
	 */
	private void startEventThread() {
		_eventThread = new Thread(new EventProcessingThread());
		_eventThread.setName("TcpCl-events-" + _neighbor.getName());
		_eventThread.start();
	}
	
	/**
	 * Handle Management START command for this component.  Enqueues a
	 * START event to the event queue.
	 */
	@Override
	protected void startImpl() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("startImpl()");
		}
		if (_senderState == SenderState.STOPPED_STATE) {
			try {
				_eventQueue.put(new TcpClStartEvent());
			} catch (InterruptedException e) {
				// Nothing
			}
		}
	}
	
	/**
	 * Handle Management STOP command for this component.  Enqueues a
	 * STOP event to the event queue.
	 */
	@Override
	protected void stopImpl() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("stopImpl()");
		}
		if (_senderState != SenderState.STOPPED_STATE) {
			try {
				_eventQueue.put(new TcpClStopEvent());
			} catch (InterruptedException e) {
				// Nothing
			}
		}
	}
	
	/**
	 * Dump this component
	 * @param indent Amount of indentation
	 * @param detailed Whether detailed dump desired
	 * @return String containing dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "TcpClStateMachine\n");
		sb.append(indent + "  State=" + _senderState + "\n");
		sb.append(indent + "  NegotiatedAckDataSegments=" + _negotiatedAckDataSegments + "\n");
		sb.append(indent + "  NegotiatedNackDataSegments=" + _negotiatedNackDataSegments + "\n");
		sb.append(indent + "  NegotiatedKeepAliveTimeSecs=" + _negotiatedKeepAliveTimeSecs + "\n");
		sb.append(indent + "  NegotiatedReactiveFragmentation=" + _negotiatedReactiveFragmentation + "\n");
		sb.append(indent + "  NegotiatedIdleTimeSecs=" + _negotiatedIdleTimeSecs + "\n");
		sb.append(super.dump(indent + "  ", detailed));
		return sb.toString();
	}
	
	/**
	 * Request from upper layer to transmit given Data Block.  We enqueue:
	 * <ul>
	 *   <li>DEMAND_SERVICE_EVENT - Tells the state machine to initiate a
	 *   connection.
	 *   <li>OutboundBlockEvent - Tells the state machine to internal enqueue
	 *   the outbound block and possibly start transmitting it.
	 * </ul>
	 * to the event queue.
	 * @param block Given Data Block
	 * @throws InterruptedException if interrupted waiting for queue space
	 */
	public void enqueueOutboundBlock(TcpClDataBlock block) 
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("enqueueOutboundBlock");
		}
		_eventQueue.put(new TcpClEvent(SubEventTypes.DEMAND_SERVICE_EVENT));
		_eventQueue.put(new OutboundBlockEvent(block));
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
					"enqueueOutboundBlock DONE");
		}
	}
	
	/**
	 * Event processing Thread.  Receives events from the event queue and
	 * dispatches them to process* methods.
	 */
	public class EventProcessingThread implements Runnable {
		/**
		 * Event processing Thread main loop; dequeues events from event
		 * queue and dispatches to lower level methods.
		 */
		public void run() {
			try {
				
				while (!Thread.interrupted()) {
					if (GeneralManagement.isDebugLogging()) {
						_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
								"State = " + _senderState);
					}
					
					// Get next event from event queue
					TcpClEvent event = _eventQueue.take();
					if (GeneralManagement.isDebugLogging()) {
						_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
								"Event " + event.getSubEventType());
					}
					try {
						// Dispatch the event to process* method
						switch (event.getSubEventType()) {
						case DEMAND_SERVICE_EVENT:
							processDemandServiceEvent(event);
							break;
						case START_EVENT:
							processStartEvent(event);
							break;
						case STOP_EVENT:
							processStopEvent(event);
							break;
						case SOCKET_CONNECTED_EVENT:
							processTcpConnectedEvent(event);
							break;
						case SOCKET_ACCEPTED_EVENT:
							// Ignore this; the state machine no longer handles
							// such events via event mechanism, rather, handles
							// it as secondary constructor.
							break;
						case OUTBOUND_BLOCK_EVENT:
							processOutboundBlockEvent((OutboundBlockEvent)event);
							break;
						case INBOUND_SEGMENT_EVENT:
							processInboundSegmentEvent(event);
							break;
						case CONNECTION_CLOSED_REMOTE:
							processConnectionClosedRemoteEvent(event);
							break;
						case IDLE_TIMER_EVENT:
							processIdleTimerEvent(event);
							break;
						case KEEPALIVE_TIMER_EVENT:
							processKeepAliveTimerEvent(event);
							break;
						case RECONNECT_TIMER_EVENT:
							processReconnectTimerEvent(event);
							break;
						case SET_PARAMETER_EVENT:
							// Ignore this, a vestige of an experiment gone
							// awry.  We should not see this event any more.
							_logger.warning((_isAccept ? "Acceptor: " : "Initiator: ") +
								"Unexpected Set Parameter Event");
							break;
						case ACK_TIMER_EVENT:
							processAckTimerEvent(event);
							break;
						}
					} catch (JDtnException e) {
						_logger.log(Level.SEVERE, "EventProcessingThread", e);
						
					} catch (IOException e) {
						_logger.log(Level.SEVERE, "EventProcessingThread", e);
					}
				}
				
			} catch (InterruptedException e) {
				// Nothing
			}
		}
	}
	
	/**
	 * Process a Management Start event.  We only process this event from
	 * STOPPED state.  We go into IDLE state, awaiting a demand for service
	 * before we start up the Connection.
	 * @param event Start event
	 * @throws InterruptedException if interrupted during processing
	 */
	private void processStartEvent(TcpClEvent event) 
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"State=" + _senderState + "; " +
				"processStartEvent");
		}
		switch (_senderState) {
		case STOPPED_STATE:
			if (_pendingXmitBlocks.isEmpty()) {
				_senderState = SenderState.IDLE_STATE;
			} else {
				startInitiator();
				_senderState = SenderState.CONN_INITIATOR_STATE;
				break;
			}
			break;
			
		default:
			_logger.warning((_isAccept ? "Acceptor: " : "Initiator: ") +
					"Ignoring Start Event in state " + _senderState);
			break;
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"processStartEvent DONE");
		}
	}
	
	/**
	 * Process a Demand for Service event.  We only process this event from
	 * IDLE state.  We initiate a connection.
	 * @param event The Demand for Service event
	 * @throws InterruptedException if interrupted during processing
	 */
	private void processDemandServiceEvent(TcpClEvent event) 
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"State=" + _senderState + "; " +
				"processDemandServiceEvent");
		}
		switch (_senderState) {
		case IDLE_STATE:
			startInitiator();
			_senderState = SenderState.CONN_INITIATOR_STATE;
			break;
			
		default:
			// Ignore
			break;
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"processDemandServiceEvent DONE");
		}
	}
	
	/**
	 * Process a Stop event from Management.  Depending on what state we're
	 * in, we do what's necessary to get us back down to STOPPED state.
	 * @param event the event
	 * @throws InterruptedException If interrupted during processing
	 * @throws JDtnException TcpCl specific errors
	 * @throws IOException I/O errors
	 */
	private void processStopEvent(TcpClEvent event) 
	throws InterruptedException, JDtnException, IOException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"State=" + _senderState + "; " +
				"processStopEvent");
		}
		switch (_senderState) {
		case STOPPED_STATE:
			// No action necessary
			break;
		case IDLE_STATE:
			_senderState = SenderState.STOPPED_STATE;
			break;
		case CONN_INITIATOR_STATE:
			stopInitiator();
			_senderState = SenderState.STOPPED_STATE;
			break;
			
		case WAIT_HEADER_STATE:
			// Fall thru
		case WAIT_BLOCK_TO_SEND_STATE:
			// Fall thru
		case WAIT_ACK_STATE:
			sendShutdown(false, 0);
			stopReceiver();
			stopIdleTimer();
			stopKeepAliveTimer();
			stopReconnectTimer();
			_senderState = SenderState.STOPPED_STATE;
			break;
			
		case DELAY_BEFORE_RECONNECT_STATE:
			stopReconnectTimer();
			stopIdleTimer();
			stopKeepAliveTimer();
			_senderState = SenderState.STOPPED_STATE;
			break;
		}
		closeSocket();
		clearCurrentXmitState();
		_pendingXmitBlocks.clear();
		
		clearCurrentRecvState();
		
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"processStopEvent DONE");
		}
	}

	/**
	 * Process a 'Tcp Connection Event'.  We have successfully initiated a
	 * connection.  If we're expecting such an event, then we start
	 * our receiver Thread and send a Contact Header
	 * @param event Tcp Connection Event
	 * @throws InterruptedException if interrupted during processing
	 * @throws IOException on I/O erros
	 * @throws JDtnException on TcpCl specific errors
	 */
	private void processTcpConnectedEvent(TcpClEvent event) 
	throws InterruptedException, JDtnException, IOException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"State=" + _senderState + "; " +
				"processTcpConnectedEvent");
		}
		if (!(event instanceof TcpConnectedEvent)) {
			_logger.severe(
					"event not instanceof TcpConnectedEvent: " +
					event.getEventType() + ", " +
					event.getSubEventType());
			return;
		}
		TcpClManagement.getInstance().getStatistics().nConnects++;
		TcpConnectedEvent conEvent = (TcpConnectedEvent)event;
		_socket = conEvent.getSocket();
		
		switch (_senderState) {
		case CONN_INITIATOR_STATE:
			stopInitiator();
			_senderState = SenderState.WAIT_HEADER_STATE;
			startReceiver(conEvent.getSocket());
			sendContactHeader();
			startIdleTimer();
			break;
			
		default:
			_logger.warning((_isAccept ? "Acceptor: " : "Initiator: ") +
					"Ignoring TCP Connected event in state " + _senderState);
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"processTcpConnectedEvent DONE");
		}
	}
	
	/**
	 * Process an incoming TcpCl Segment.  We dispatch the
	 * Segment down to a lower level method depending on segment type.
	 * @param event The event
	 * @throws IOException on I/O errors
	 * @throws JDtnException on TcpCl specific errors
	 * @throws InterruptedException If interrupted during processing
	 */
	private void processInboundSegmentEvent(TcpClEvent event)
	throws IOException, JDtnException, InterruptedException {
		TcpClManagement.getInstance().getStatistics().nSegmentsRcvd++;
		restartIdleTimer();
		InboundSegmentEvent segEvent = (InboundSegmentEvent)event;
		TcpClSegment segment = segEvent.getSegment();
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer(
					(_isAccept ? "Acceptor: " : "Initiator: ") +
					"State=" + _senderState + "; " +
					"processInboundSegmentEvent");
			_logger.finest(segment.dump("", true));
		}
		switch (segment.getSegmentType()) {
		case SEG_TYPE_CONTACT_HEADER:
			processContactHeader((TcpClContactHeaderSegment)segment);
			break;
		case SEG_TYPE_ACTIVE_SESSION:
			TcpClActiveSessionSegment sessionSeg =
				(TcpClActiveSessionSegment)segment;
			switch (sessionSeg.getType()) {
			case TcpClActiveSessionSegment.ACK_SEGMENT_TYPE:
				processAckSegment((TcpClBundleAckSegment)sessionSeg);
				break;
			case TcpClActiveSessionSegment.DATA_SEGMENT_TYPE:
				processInboundDataSegment((TcpClDataSegment)sessionSeg);
				break;
			case TcpClActiveSessionSegment.KEEPALIVE_SEGMENT_TYPE:
				processKeepAliveSegment((TcpClKeepAliveSegment)sessionSeg);
				break;
			case TcpClActiveSessionSegment.REFUSE_SEGMENT_TYPE:
				processRefuseSegment((TcpClBundleNackSegment)sessionSeg);
				break;
			case TcpClActiveSessionSegment.SHUTDOWN_SEGMENT_TYPE:
				processShutdownSegment((TcpClShutdownSegment)sessionSeg);
				break;
			default:
				_logger.severe(
						"Unrecognized Active Sessiojn Segment type: " +
						sessionSeg.getType());
				break;
			}
			break;
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"processInboundSegmentEvent DONE");
		}
	}
	
	/**
	 * Process a Contact Header Segment.  We only process this in
	 * WAIT_HEADER_STATE.  We extract the proposals from the contact header,
	 * reconcile them with our own configured options, and advance to
	 * WAIT_BLOCK_TO_SEND_STATE.
	 * @param segment Contact Header segment
	 * @throws IOException on I/O errors
	 * @throws JDtnException on Tcp CL specific errors
	 */
	private void processContactHeader(TcpClContactHeaderSegment segment) 
	throws IOException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
					"State=" + _senderState + "; " +
					"processContactHeader");
		}
		TcpClManagement.getInstance().getStatistics().nContactHdrsRcvd++;
		restartIdleTimer();
		switch (_senderState) {
		case WAIT_BLOCK_TO_SEND_STATE:
			// Sometimes, the connection gets confused and DTN2 sends
			// a Contact Header where we're in this state.
			//$FALL-THROUGH$
		case WAIT_HEADER_STATE:
			if (_isAccept) {
				sendContactHeader();
			}
			// Reconcile sender's proposals with our configured options
			if (_neighbor.isAckDataSegments() && segment.isBundleAcks()) {
				_negotiatedAckDataSegments = true;
			} else {
				_negotiatedAckDataSegments = false;
			}
			
			// We don't support reactive fragmentation; we don't propose it in
			// our contact header, and so no matter what the other side
			// proposes, we will not do it.
			_negotiatedReactiveFragmentation = false;
			
			if (segment.isNacks()) {
				_negotiatedNackDataSegments = true;
			} else {
				_negotiatedNackDataSegments = false;
			}
			
			_negotiatedKeepAliveTimeSecs = Math.min(
					_neighbor.getKeepAliveIntervalSecs(),
					segment.getKeepAliveIntervalSecs());
			if (_negotiatedKeepAliveTimeSecs == 0) {
				_negotiatedIdleTimeSecs = 0;
			} else if (_negotiatedIdleTimeSecs < TcpClNeighbor.IDLE_KEEPALIVE_MULTIPLIER * _negotiatedKeepAliveTimeSecs) {
				_negotiatedIdleTimeSecs = TcpClNeighbor.IDLE_KEEPALIVE_MULTIPLIER * _negotiatedKeepAliveTimeSecs;
			}
			if (_negotiatedKeepAliveTimeSecs > 0) {
				restartKeepAliveTimer();
			}
			_senderState = SenderState.WAIT_BLOCK_TO_SEND_STATE;
			transmitNextBlock();
			break;
			
		default:
			_logger.warning((_isAccept ? "Acceptor: " : "Initiator: ") +
					"Ignoring Contact Header in state " + _senderState);
			break;
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"processContactHeader DONE");
		}
	}
	
	/**
	 * Process request from upper layers to send a Data Block.
	 * @param event
	 * @throws IOException
	 * @throws JDtnException
	 * @throws InterruptedException
	 */
	private void processOutboundBlockEvent(OutboundBlockEvent event) 
	throws IOException, JDtnException, InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"State=" + _senderState + "; " +
				"processOutboundBlockEvent");
		}
		TcpClDataBlock dataBlock = event.getDataBlock();
		_pendingXmitBlocks.add(dataBlock);
		TcpClLink link = _neighbor.findOperationalTcpClLink();
		if (link == null) {
			_logger.warning((_isAccept ? "Acceptor: " : "Initiator: ") +
				"No operational Link for outbound Block");
			return;
		}
		switch (_senderState) {
		case WAIT_BLOCK_TO_SEND_STATE:
			// Send next Block in _pendingXmitBlocks
			transmitNextBlock();
			break;
			
		case WAIT_ACK_STATE:
			// Waiting for Ack from peer; Nothing right now
			break;
			
		default:
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine((_isAccept ? "Acceptor: " : "Initiator: ") +
						"Delaying processing of outbound block in state " + _senderState);
			}
			break;
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"processOutboundBlockEvent DONE");
		}
	}
	
	/**
	 * Process incoming Bundle Ack Segment.  If expecting an Ack, then we
	 * go to look for more blocks to transmit from current block, or more
	 * blocks to transmit from _pendingXmitBlocks.
	 * @param segment Bundle Ack segment
	 * @throws IOException on I/O Error
	 * @throws JDtnException On TcpCl specific errors
	 * @throws InterruptedException if interrupted during processing
	 */
	private void processAckSegment(TcpClBundleAckSegment segment)
	throws IOException, JDtnException, InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"State=" + _senderState + "; " +
				"processAckSegment");
		}
		TcpClManagement.getInstance().getStatistics().nAckSegmentsRcvd++;
		TcpClLink link = _neighbor.findOperationalTcpClLink();
		if (link == null) {
			_logger.warning((_isAccept ? "Acceptor: " : "Initiator: ") +
				"No operational Link for outbound Block");
			return;
		}
		switch (_senderState) {
		case WAIT_ACK_STATE:
			restartIdleTimer();
			stopAckTimer();
			if (isMoreSegmentsToSend()) {
				transmitNextSegmentCurrentBlock(link, _currentXmitBlock);
				_senderState = SenderState.WAIT_ACK_STATE;
				startAckTimer();
			} else {
				TcpClManagement.getInstance().getStatistics().nBlocksSent++;
				TcpClAPI.getInstance().blockTransmitted(_currentXmitBlock);
				_senderState = SenderState.WAIT_BLOCK_TO_SEND_STATE;
				transmitNextBlock();
			}
			break;
			
		case WAIT_BLOCK_TO_SEND_STATE:
			restartIdleTimer();
			transmitNextBlock();
			break;
			
		default:
			_logger.warning((_isAccept ? "Acceptor: " : "Initiator: ") +
					"Ignoring Ack Segment in state " + _senderState);
			break;
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"processAckSegment DONE");
		}
	}
	
	/**
	 * Process incoming Data Segment.  We add it to _currentRecvBlock.  If
	 * acks are called for, then we ack it.  If this is the end of a Block,
	 * then we reassemble segments from _currentRecvBlock and deliver to
	 * higher layer.
	 * @param segment
	 * @throws JDtnException
	 * @throws IOException
	 */
	private void processInboundDataSegment(TcpClDataSegment segment)
	throws JDtnException, IOException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
					"State=" + _senderState + "; " +
					"processInboundDataSegment");
		}
		TcpClManagement.getInstance().getStatistics().nDataSegmentsRcvd++;
		restartIdleTimer();
		if (segment.isStart()) {
			_currentRecvBlock.clear();
		}
		_currentRecvBlock.add(segment);
		_recvBlockCumReceivedLength += segment.getLength();
		if (_negotiatedAckDataSegments) {
			sendAck(_recvBlockCumReceivedLength);
		}
		if (segment.isEnd()) {
			// Deliver the Block
			byte[] buffer = new byte[(int)_recvBlockCumReceivedLength];
			int offset = 0;
			for (TcpClDataSegment segment2 : _currentRecvBlock) {
				System.arraycopy(segment2.getData(), 0, buffer, offset, segment2.getLength());
				offset += segment2.getLength();
			}
			TcpClDataBlock dataBlock = new TcpClDataBlock(
					buffer, 
					buffer.length, 
					_neighbor.findOperationalTcpClLink(), 
					_neighbor, 
					null);
			TcpClManagement.getInstance().getStatistics().nBlocksRcvd++;
			TcpClAPI.getInstance().notifyInboundBlock(dataBlock, dataBlock.length);
			clearCurrentRecvState();
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"processInboundDataSegment DONE");
		}
	}
	
	/**
	 * Processing incoming KeepAlive Segment.  We restart the idle timer.
	 * @param segment KeepAlive Segment
	 */
	private void processKeepAliveSegment(TcpClKeepAliveSegment segment) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"State=" + _senderState + "; " +
				"processKeepAliveSegment");
		}
		TcpClManagement.getInstance().getStatistics().nKeepAlivesRcvd++;
		switch (_senderState) {
		case STOPPED_STATE:
			// Fall thru
		case IDLE_STATE:
			// Fall thru
		case CONN_INITIATOR_STATE:
			// Fall thru
		case DELAY_BEFORE_RECONNECT_STATE:
			_logger.warning(
					(_isAccept ? "Acceptor: " : "Initiator: ") +
					"Ignoring incoming KeepAlive Segment in state " + 
					_senderState);
			break;
		case WAIT_ACK_STATE:
			restartIdleTimer();
			break;
		case WAIT_BLOCK_TO_SEND_STATE:
			restartIdleTimer();
			break;
		case WAIT_HEADER_STATE:
			restartIdleTimer();
			break;
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"processKeepAliveSegment DONE");
		}
	}
	
	/**
	 * Process a Refuse (Bundle Nack) segment.  This is sent in lieu of a
	 * Bundle Ack to indicate that the peer doesn't want to accept the
	 * Bundle.  We notify upper layer.  Then we look for more to xmit.
	 * @param segment
	 * @throws JDtnException On TcpCl specific errors
	 * @throws IOException On I/O errors
	 */
	private void processRefuseSegment(TcpClBundleNackSegment segment) 
	throws IOException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"State=" + _senderState + "; " +
				"processRefuseSegment");
		}
		TcpClManagement.getInstance().getStatistics().nNackSegmentsRcvd++;
		switch (_senderState) {
		case WAIT_BLOCK_TO_SEND_STATE:
			// Fall thru
		case WAIT_ACK_STATE:
			TcpClManagement.getInstance().getStatistics().nBlocksSentErrors++;
			TcpClAPI.getInstance().notifyOutboundBlockError(
					_currentXmitBlock,
					new JDtnException("Bundle Refused"));
			_currentXmitBlock = null;
			
			_senderState = SenderState.WAIT_BLOCK_TO_SEND_STATE;
			restartIdleTimer();
			transmitNextBlock();
			break;
			
		default:
			_logger.severe((_isAccept ? "Acceptor: " : "Initiator: ") +
					"Unexpected Bundle Nack in state " + _senderState);
			break;
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"processRefuseSegment DONE");
		}
	}
	
	/**
	 * Process an incoming Shutdown Segment from the peer.  Depending on state,
	 * we do what is necessary to shut down the connection.  This can include
	 * going into a delay before we attempt to reconnect.
	 * @param segment Shutdown Segment.
	 * @throws InterruptedException
	 * @throws JDtnException
	 * @throws IOException
	 */
	private void processShutdownSegment(TcpClShutdownSegment segment) 
	throws InterruptedException, JDtnException, IOException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
					"State=" + _senderState + "; " +
					"processShutdownSegment: reason=" +
					TcpClShutdownSegment.reasonToString(segment.getReason()));
		}
		TcpClManagement.getInstance().getStatistics().nShutdownsRcvd++;
		switch (_senderState) {
		case STOPPED_STATE:
			_logger.fine((_isAccept ? "Acceptor: " : "Initiator: ") +
					"Ignoring Shutdown Segment in state " + _senderState);
			break;
			
		case IDLE_STATE:
			// Fall thru
		case CONN_INITIATOR_STATE:
			_logger.warning((_isAccept ? "Acceptor: " : "Initiator: ") +
					"Ignoring Shutdown Segment in state " + _senderState);
			break;
			
		case WAIT_ACK_STATE:
			if (_currentXmitBlock != null) {
				if (_pendingXmitBlocks.isEmpty()) {
					_pendingXmitBlocks.add(_currentXmitBlock);
				} else {
					_pendingXmitBlocks.add(_pendingXmitBlocks.size() - 1, _currentXmitBlock);
				}
				_currentXmitBlock = null;
			}
			// Fall thru
			//$FALL-THROUGH$
		case WAIT_HEADER_STATE:
			// Fall thru
		case WAIT_BLOCK_TO_SEND_STATE:
			// Fall thru
			sendShutdown(false, 0);
			stopReceiver();
			if (_isAccept) {
				// If we're an acceptor; stop all timers and go to idle state.
				// Notify our associated Neighbor, who will stop us.  The
				// Neighbor will start up a new acceptor State Machine on the
				// next incoming connection.
				_senderState = SenderState.IDLE_STATE;
				stopIdleTimer();
				stopAckTimer();
				stopKeepAliveTimer();
				closeSocket();
				_neighbor.notifyConnectionClosed(this);
				
			} else if (segment.isReconnectDelayRqstd()) {
				stopIdleTimer();
				_senderState = SenderState.DELAY_BEFORE_RECONNECT_STATE;
				startReconnectTimer(segment.getReconnectDelaySecs());
			} else if (!_pendingXmitBlocks.isEmpty()) {
				stopIdleTimer();
				_eventQueue.add(new TcpClEvent(SubEventTypes.DEMAND_SERVICE_EVENT));
				_senderState = SenderState.IDLE_STATE;
			} else { 
				stopIdleTimer();
				_senderState = SenderState.IDLE_STATE;
			}
			closeSocket();
			break;
		case DELAY_BEFORE_RECONNECT_STATE:
			_logger.warning((_isAccept ? "Acceptor: " : "Initiator: ") +
					"Ignoring Shutdown segment in state " + _senderState);
			break;
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"processShutdownSegment DONE");
		}
	}
	
	/**
	 * Process the event wherein the peer closes the TCP connection.  Again,
	 * we do what is necessary to shutdown the connection.
	 * @param event
	 * @throws JDtnException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void processConnectionClosedRemoteEvent(TcpClEvent event) 
	throws JDtnException, IOException, InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"State=" + _senderState + "; " +
				"processConnectionClosedRemoteEvent");
		}
		TcpClManagement.getInstance().getStatistics().nDisconnects++;
		switch (_senderState) {
		case STOPPED_STATE:
			// Fall thru
		case IDLE_STATE:
			// Fall thru
		case CONN_INITIATOR_STATE:
			_logger.fine((_isAccept ? "Acceptor: " : "Initiator: ") +
					"Ignoring Connection Closed in state " + _senderState);
			break;
			
		case WAIT_HEADER_STATE:
			// Fall thru
		case WAIT_BLOCK_TO_SEND_STATE:
			// Fall thru
		case WAIT_ACK_STATE:
			stopReceiver();
			if (_isAccept) {
				// If we're an acceptor; stop all timers and go to idle state.
				// Notify our associated Neighbor, who will stop us.  The
				// Neighbor will start up a new acceptor State Machine on the
				// next incoming connection.
				_senderState = SenderState.IDLE_STATE;
				stopIdleTimer();
				stopAckTimer();
				stopKeepAliveTimer();
				_neighbor.notifyConnectionClosed(this);
				
			} else if (_neighbor.isDelayBeforeReconnection()) {
				stopIdleTimer();
				_senderState = SenderState.DELAY_BEFORE_RECONNECT_STATE;
				startReconnectTimer(_neighbor.getReconnectionDelaySecs());
			} else if (!_pendingXmitBlocks.isEmpty()) {
				stopIdleTimer();
				_eventQueue.add(new TcpClEvent(SubEventTypes.DEMAND_SERVICE_EVENT));
				_senderState = SenderState.IDLE_STATE;
			} else {
				stopIdleTimer();
				_senderState = SenderState.IDLE_STATE;
			}
			closeSocket();
			break;
			
		case DELAY_BEFORE_RECONNECT_STATE:
			_logger.warning((_isAccept ? "Acceptor: " : "Initiator: ") +
					"Ignoring Connection Closed event in state " + _senderState);
			closeSocket();
			break;
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"processConnectionClosedRemoteEvent DONE");
		}
	}
	
	/**
	 * Process Idle Timer expiration.  Bring the connection down.
	 * @param event The event
	 * @throws InterruptedException if interrupted during process
	 * @throws JDtnException on TcpCl specific errors
	 * @throws IOException on I/O errors
	 */
	private void processIdleTimerEvent(TcpClEvent event) 
	throws InterruptedException, JDtnException, IOException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"State=" + _senderState + "; " +
				"processIdleTimerEvent");
		}
		TcpClManagement.getInstance().getStatistics().nIdleExpires++;
		switch (_senderState) {
		case STOPPED_STATE:
			// Fall Thru
		case IDLE_STATE:
			// Fall Thru
		case CONN_INITIATOR_STATE:
			stopIdleTimer();
			break;
		case WAIT_HEADER_STATE:
			// Got idle timeout waiting for Contact Header
			try {
				sendShutdown(true, TcpClShutdownSegment.IDLE_TIMEOUT_REASON);
			} catch (Exception e) {
				_logger.log(Level.SEVERE, "sendShutdown()", e);
			}
			stopReceiver();
			if (_isAccept) {
				// If we're an acceptor; stop all timers and go to idle state.
				// Notify our associated Neighbor, who will stop us.  The
				// Neighbor will start up a new acceptor State Machine on the
				// next incoming connection.
				_senderState = SenderState.IDLE_STATE;
				stopIdleTimer();
				stopAckTimer();
				stopKeepAliveTimer();
				_neighbor.notifyConnectionClosed(this);
				closeSocket();

			} else if (_neighbor.isDelayBeforeReconnection()) {
				_senderState = SenderState.DELAY_BEFORE_RECONNECT_STATE;
				startReconnectTimer(_neighbor.getReconnectionDelaySecs());
				closeSocket();
			} else if (!_pendingXmitBlocks.isEmpty()) {
				_eventQueue.add(new TcpClEvent(SubEventTypes.DEMAND_SERVICE_EVENT));
				_senderState = SenderState.IDLE_STATE;
				closeSocket();
			} else {
				_senderState = SenderState.IDLE_STATE;
				closeSocket();
			}
			break;
		case WAIT_BLOCK_TO_SEND_STATE:
			// Fall thru
		case WAIT_ACK_STATE:
			sendShutdown(true, TcpClShutdownSegment.IDLE_TIMEOUT_REASON);
			stopReceiver();

			if (_isAccept) {
				// If we're an acceptor; stop all timers and go to idle state.
				// Notify our associated Neighbor, who will stop us.  The
				// Neighbor will start up a new acceptor State Machine on the
				// next incoming connection.
				_senderState = SenderState.IDLE_STATE;
				stopIdleTimer();
				stopAckTimer();
				stopKeepAliveTimer();
				_neighbor.notifyConnectionClosed(this);
				closeSocket();
				
			} else if (_neighbor.isDelayBeforeReconnection()) {
				_senderState = SenderState.DELAY_BEFORE_RECONNECT_STATE;
				startReconnectTimer(_neighbor.getReconnectionDelaySecs());
				closeSocket();
				
			} else {
				_senderState = SenderState.IDLE_STATE;
				closeSocket();
			}
			break;
		case DELAY_BEFORE_RECONNECT_STATE:
			closeSocket();
			// Otherwise ignore; reconnect timer will wake us up appropriately
			break;
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"processIdleTimerEvent DONE");
		}
	}
	
	/**
	 * Process reconnect timer expiration.  We can now reconnect.
	 * @param event
	 * @throws InterruptedException if interrupted during processing
	 */
	private void processReconnectTimerEvent(TcpClEvent event) 
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"State=" + _senderState + "; " +
				"processReconnectTimerEvent");
		}
		TcpClManagement.getInstance().getStatistics().nReconnectExpires++;
		switch (_senderState) {
		case DELAY_BEFORE_RECONNECT_STATE:
			if (!_pendingXmitBlocks.isEmpty()) {
				_eventQueue.add(new TcpClEvent(SubEventTypes.DEMAND_SERVICE_EVENT));
				_senderState = SenderState.IDLE_STATE;
			} else {
				_senderState = SenderState.IDLE_STATE;
			}
			break;
			
		default:
			_logger.warning((_isAccept ? "Acceptor: " : "Initiator: ") +
					"Ignoring Reconnect Timer Event in state " + _senderState);
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"processReconnectTimerEvent DONE");
		}
	}
	
	/**
	 * Process KeepAlive timer expiration.  Send Keep Alive segment.
	 * @param event The event
	 * @throws JDtnException on TcpCl specific errors
	 * @throws IOException On I/O errors
	 */
	private void processKeepAliveTimerEvent(TcpClEvent event) 
	throws JDtnException, IOException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"State=" + _senderState + "; " +
				"processKeepAliveTimerEvent");
		}
		TcpClManagement.getInstance().getStatistics().nKeepaliveExpires++;
		switch (_senderState) {
		case STOPPED_STATE:
			// Fall thru
		case IDLE_STATE:
			// Fall thru
		case CONN_INITIATOR_STATE:
			// Fall thru
		case WAIT_HEADER_STATE:
			// Fall thru
		case DELAY_BEFORE_RECONNECT_STATE:
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine(
						"Ignoring KeepAlive timer expiration in state " + 
						_senderState);
			}
			break;
			
		case WAIT_BLOCK_TO_SEND_STATE:
			// Fall thru
		case WAIT_ACK_STATE:
			if (_negotiatedKeepAliveTimeSecs > 0) {
				sendKeepAliveSegment();
				restartKeepAliveTimer();
			}
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"processKeepAliveTimerEvent DONE");
		}
	}
	
	/**
	 * The Ack timer has expired, signifying that we have not gotten an Ack
	 * to our last Data Segment.  We only respond to this event in
	 * WAIT_ACK_STATE.  We respond by clearing data segment send
	 * state (re-queueing the block for transmission later), and then start
	 * closing the connection.
	 * @param event Not used
	 * @throws InterruptedException 
	 */
	private void processAckTimerEvent(TcpClEvent event) 
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
					"State=" + _senderState + "; " +
				"processAckTimerEvent");
		}
		switch (_senderState) {
		case WAIT_ACK_STATE:
			stopAckTimer();
			stopIdleTimer();
			try {
				sendShutdown(true, TcpClShutdownSegment.IDLE_TIMEOUT_REASON);
			} catch (Exception e) {
				_logger.log(Level.SEVERE, "sendShutdown()", e);
			}
			stopReceiver();
			
			if (_currentXmitBlock != null) {
				_pendingXmitBlocks.add(
						_pendingXmitBlocks.size() - 1, 
						_currentXmitBlock);
				clearCurrentXmitState();
			}
			clearCurrentRecvState();
			closeSocket();
			if (_isAccept) {
				_senderState = SenderState.IDLE_STATE;
				stop();
				
			} else if (_neighbor.isDelayBeforeReconnection()) {
				_senderState = SenderState.DELAY_BEFORE_RECONNECT_STATE;
				startReconnectTimer(_neighbor.getReconnectionDelaySecs());
			} else {
				_eventQueue.add(new TcpClEvent(SubEventTypes.DEMAND_SERVICE_EVENT));
				_senderState = SenderState.IDLE_STATE;
			}
			break;
			
		default:
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine(
						"Ignoring Ack timer expiration in state " + 
						_senderState);
			}
			break;
		}
	}

	/**
	 * Clear state fields concerning block currently being received
	 */
	private void clearCurrentRecvState() {
		_currentRecvBlock.clear();
		_recvBlockCumReceivedLength = 0;
	}

	/**
	 * Clear state fields concerning block currently being transmitted
	 */
	private void clearCurrentXmitState() {
		_currentXmitBlock = null;
		_currentXmitBlockOffset = 0;
		_currentXmitSegmentLength = 0;
		_currentXmitBlockLength = 0;
		_currentCumXmitBlockLength = 0;
	}

	/**
	 * Close the Socket
	 */
	private void closeSocket() {
		if (_socket != null && !_socket.isClosed()) {
			try {
				_socket.close();
			} catch (Exception e) {
				// Nothing
			} finally {
				_socket = null;
			}
		}
	}
	
	/**
	 * Sent Contact Header
	 * @throws JDtnException on TcpCl errors
	 * @throws IOException on I/O errors
	 */
	private void sendContactHeader() throws JDtnException, IOException {
		TcpClContactHeaderSegment segment = new TcpClContactHeaderSegment();
		segment.setBundleAcks(_neighbor.isAckDataSegments());
		segment.setEndpointId(BPManagement.getInstance().getEndPointIdStem());
		if (_neighbor.isKeepAlive()) {
			segment.setKeepAliveIntervalSecs(_neighbor.getKeepAliveIntervalSecs());
		} else {
			segment.setKeepAliveIntervalSecs(TcpClNeighbor.KEEPALIVE_INTERVAL_SECS_INFINITY);
		}
		if (_neighbor.isAckDataSegments()) {
			segment.setNacks(true);
		} else {
			segment.setNacks(false);
		}
		segment.setReactiveFragmentation(false);
		segment.setVersion(TcpClContactHeaderSegment.VERSION_DEFAULT);
		sendSegment(segment);
		TcpClManagement.getInstance().getStatistics().nContactHdrsSent++;
	}
	
	/**
	 * Start sending the next Block from the _pendingXmitBlocks queue.
	 * @throws IOException on I/O errors
	 * @throws JDtnException on TcpCl errors
	 */
	private void transmitNextBlock() throws IOException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"sendNextBlock");
		}
		if (!_pendingXmitBlocks.isEmpty()) {
			_currentXmitBlock = _pendingXmitBlocks.remove(0);
			if (!_currentXmitBlock.link.isLinkOperational()) {
				_logger.warning((_isAccept ? "Acceptor: " : "Initiator: ") +
					"No operational Link for outbound Block");
				return;
			}
			
			if (_negotiatedAckDataSegments) {
				transmitFirstSegmentCurrentBlock(_currentXmitBlock.link, _currentXmitBlock);
				_senderState = SenderState.WAIT_ACK_STATE;
				startAckTimer();
				
			} else {
				sendAllSegmentsCurrentBlock(_currentXmitBlock.link, _currentXmitBlock);
				_senderState = SenderState.WAIT_BLOCK_TO_SEND_STATE;
				transmitNextBlock();
			}
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"sendNextBlock DONE");
		}
	}
	
	/**
	 * Send the first segment of the given block
	 * @param link Link on which to send
	 * @param block The block 
	 * @throws IOException on I/O errors
	 * @throws JDtnException on TcpCl errors
	 */
	private void transmitFirstSegmentCurrentBlock(TcpClLink link, TcpClDataBlock block) 
	throws IOException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"sendFirstSegmentCurrentBlock");
		}
		_currentXmitBlockLength = block.length;
		_currentXmitBlockOffset = 0;
		boolean first = true;
		boolean last = false;
		_currentXmitSegmentLength = (int)(_currentXmitBlockLength - _currentXmitBlockOffset);
		if (_currentXmitSegmentLength > link.getMaxSegmentSize()) {
			_currentXmitSegmentLength = link.getMaxSegmentSize();
		}
		if (_currentXmitBlockOffset + _currentXmitSegmentLength >= _currentXmitBlockLength) {
			last = true;
		}
		sendDataSegment(link, block, _currentXmitSegmentLength, _currentXmitBlockOffset, first, last);
		_currentCumXmitBlockLength = _currentXmitSegmentLength;
		_currentXmitBlockOffset += _currentXmitSegmentLength;
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"sendFirstSegmentCurrentBlock DONE");
		}
	}
	
	/**
	 * Send the next segment of the given block
	 * @param link Link on which to send
	 * @param block The block
	 * @throws IOException on I/O errors
	 * @throws JDtnException on TcpCl errors
	 */
	private void transmitNextSegmentCurrentBlock(TcpClLink link, TcpClDataBlock block)
	throws IOException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"sendNextSegmentCurrentBlock");
		}
		boolean first = false;
		boolean last = false;
		_currentXmitSegmentLength = (int)(_currentXmitBlockLength - _currentXmitBlockOffset);
		if (_currentXmitSegmentLength > link.getMaxSegmentSize()) {
			_currentXmitSegmentLength = link.getMaxSegmentSize();
		}
		if (_currentXmitBlockOffset + _currentXmitSegmentLength >= _currentXmitBlockLength) {
			last = true;
		}
		sendDataSegment(link, block, _currentXmitSegmentLength, _currentXmitBlockOffset, first, last);
		_currentCumXmitBlockLength += _currentXmitSegmentLength;
		_currentXmitBlockOffset += _currentXmitSegmentLength;
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"sendNextSegmentCurrentBlock DONE");
		}
	}
	
	/**
	 * Determine if there are more segments to send in the current block
	 * @return True if more segments to send
	 */
	private boolean isMoreSegmentsToSend() {
		if (_currentXmitBlockOffset >= _currentXmitBlockLength) {
			return false;
		}
		return true;
	}
	
	/**
	 * Send all segments in the current block
	 * @param link Linke to send on
	 * @param block Current block
	 * @throws IOException on I/O errors
	 * @throws JDtnException on TcpCl errors
	 */
	private void sendAllSegmentsCurrentBlock(TcpClLink link, TcpClDataBlock block)
	throws IOException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"sendAllSegmentsCurrentBlock");
		}
		transmitFirstSegmentCurrentBlock(link, block);
		while (isMoreSegmentsToSend()) {
			transmitNextSegmentCurrentBlock(link, block);
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"sendAllSegmentsCurrentBlock DONE");
		}
	}
	
	/**
	 * Send Data Segment of given Block
	 * @param link Link to send on
	 * @param block Given Block
	 * @param dataLen Length of segment
	 * @param dataOffset Offset of segment w/in block
	 * @param first Whether this segment is first in block
	 * @param last Whether this segment is last in block
	 * @throws IOException on I/O errors
	 * @throws JDtnException on TcpCl errors
	 */
	private void sendDataSegment(
			TcpClLink link,
			TcpClDataBlock block,
			int dataLen, 
			long dataOffset,
			boolean first,
			boolean last)
	throws IOException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"sendDataSegment");
		}
		byte[] buffer = null;
		
		buffer = new byte[dataLen];
		Utils.copyBytes(block.buffer, (int)dataOffset, buffer, 0, dataLen);
		
		TcpClDataSegment segment = new TcpClDataSegment();
		segment.setData(buffer);
		segment.setLength(dataLen);
		segment.setStart(first);
		segment.setEnd(last);
		
		sendSegment(segment);
		TcpClManagement.getInstance().getStatistics().nDataSegmentsSent++;
		
		if (_negotiatedAckDataSegments) {
			_senderState = SenderState.WAIT_ACK_STATE;
		} else {
			if (segment.isEnd()) {
				TcpClManagement.getInstance().getStatistics().nBlocksSent++;
				TcpClAPI.getInstance().blockTransmitted(block);
			}
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"sendDataSegment DONE");
		}
	}
	
	/**
	 * Send a KeepAlive Segment
	 * @throws JDtnException on TcpCl errors
	 * @throws IOException on I/O errors
	 */
	private void sendKeepAliveSegment() throws JDtnException, IOException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"sendKeepAliveSegment");
		}
		TcpClKeepAliveSegment segment = new TcpClKeepAliveSegment();
		sendSegment(segment);
		TcpClManagement.getInstance().getStatistics().nKeepAlivesSent++;
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"sendKeepAliveSegment DONE");
		}
	}
	
	/**
	 * Send Bundle Acknowledgement
	 * @param receivedLength Received length to include in ack
	 * @throws JDtnException on TcpCl errors
	 * @throws IOException on I/O errors
	 */
	private void sendAck(long receivedLength)
	throws JDtnException, IOException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"sendAck");
		}
		TcpClBundleAckSegment segment = new TcpClBundleAckSegment();
		segment.setAckLength(receivedLength);
		sendSegment(segment);
		TcpClManagement.getInstance().getStatistics().nAckSegmentsSent++;
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"sendAck DONE");
		}
	}
	
	/**
	 * Send a Shutdown message
	 * @param includeReason If true, will include reason code in message
	 * @param reason Reason code, one of TcpClShutdownSegment.***_REASON
	 * @throws JDtnException on TcpCl errors
	 * @throws IOException on I/O errors
	 */
	private void sendShutdown(boolean includeReason, int reason)
	throws JDtnException, IOException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"sendShutdown");
		}
		TcpClShutdownSegment segment = new TcpClShutdownSegment();
		if (includeReason) {
			segment.setReasonIncluded(true);
			segment.setReason(reason);
		} else {
			segment.setReasonIncluded(false);
		}
		if (_neighbor.isDelayBeforeReconnection()) {
			segment.setReconnectDelayRqstd(true);
			segment.setReconnectDelaySecs(_neighbor.getReconnectionDelaySecs());
		}
		sendSegment(segment);
		TcpClManagement.getInstance().getStatistics().nShutdownsSent++;
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"sendShutdown DONE");
		}
	}
	
	/**
	 * Send given segment.
	 * @param segment Given segment
	 * @throws JDtnException on TcpCl errors
	 * @throws IOException on I/O errors
	 */
	public void sendSegment(TcpClSegment segment)
	throws JDtnException, IOException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"sendSegment");
			_logger.finest(segment.dump("", true));
		}
		if (_socket.isClosed()) {
			_eventQueue.add(new TcpClEvent(TcpClEvent.SubEventTypes.CONNECTION_CLOSED_REMOTE));
			_logger.warning("Socket has been closed; cannot send segment");
			return;
		}
		EncodeState encodeState = new EncodeState();
		segment.encode(encodeState);
		encodeState.close();
		byte[] rawBuf = encodeState.getByteBuffer();
		try {
			_socket.getOutputStream().write(rawBuf, 0, rawBuf.length);
			_socket.getOutputStream().flush();
		} catch (SocketException e) {
			if (e.getMessage().equals("Socket is closed")) {
				_eventQueue.add(new TcpClEvent(TcpClEvent.SubEventTypes.CONNECTION_CLOSED_REMOTE));
			} else {
				throw new JDtnException("Socket Exception: " + e.getMessage());
			}
		} catch (NullPointerException e) {
			_eventQueue.add(new TcpClEvent(TcpClEvent.SubEventTypes.CONNECTION_CLOSED_REMOTE));
			throw new JDtnException("Socket has probably been closed; cannot send segment");
		}
		TcpClManagement.getInstance().getStatistics().nSegmentsSent++;
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"sendSegment DONE");
		}
	}
	
	/**
	 * Start the Idle Timer
	 */
	private void startIdleTimer() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"startIdleTimer");
		}
		if (!_isAccept && _negotiatedIdleTimeSecs > 0) {
			if (_idleTimer != null) {
				stopIdleTimer();
			}
			_idleTimer = new Timer();
			TimerTask timerTask = new TimerTask() {
				@Override
				public void run() {
					try {
						_eventQueue.put(new TcpClEvent(
							TcpClEvent.SubEventTypes.IDLE_TIMER_EVENT));
					} catch (InterruptedException e) {
						// Nothing
					}
				}
			};
			_idleTimer.schedule(
					timerTask, 
					_negotiatedIdleTimeSecs * 1000);
		}
	}
	
	/**
	 * Stop the Idle Timer
	 */
	private void stopIdleTimer() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"stopIdleTimer");
		}
		if (!_isAccept) {
			if (_idleTimer != null) {
				_idleTimer.cancel();
				_idleTimer.purge();
				_idleTimer = null;
			}
		}
	}
	
	/**
	 * Stop and then restart the Idle Timer
	 */
	private void restartIdleTimer() {
		stopIdleTimer();
		startIdleTimer();
	}
	
	/**
	 * Start the Reconnect Timer
	 * @param timeSecs Amount of time, secs
	 */
	private void startReconnectTimer(int timeSecs) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"startReconnectTimer");
		}
		if (_reconnectTimer != null) {
			stopReconnectTimer();
		}
		_reconnectTimer = new Timer();
		TimerTask timerTask = new TimerTask() {
			@Override
			public void run() {
				try {
					_eventQueue.put(new TcpClEvent(
						TcpClEvent.SubEventTypes.RECONNECT_TIMER_EVENT));
				} catch (InterruptedException e) {
					// Nothing
				}
			}
		};
		_reconnectTimer.schedule(timerTask, timeSecs * 1000);
//		if (GeneralManagement.isDebugLogging()) {
//			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
//				"startReconnectTimer DONE");
//		}
	}
	
	/**
	 * Stop Reconnect Timer
	 */
	private void stopReconnectTimer() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"stopReconnectTimer");
		}
		if (_reconnectTimer != null) {
			_reconnectTimer.cancel();
			_reconnectTimer.purge();
			_reconnectTimer = null;
		}
	}
	
	/**
	 * Start KeepAlive Timer
	 */
	private void startKeepAliveTimer() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"startKeepAliveTimer");
		}
		if (_neighbor.isKeepAlive()) {
			if (_keepAliveTimer != null) {
				stopKeepAliveTimer();
			}
			_keepAliveTimer = new Timer();
			TimerTask timerTask = new TimerTask() {
				@Override
				public void run() {
					try {
						_eventQueue.put(new TcpClEvent(
							TcpClEvent.SubEventTypes.KEEPALIVE_TIMER_EVENT));
					} catch (InterruptedException e) {
						// Nothing
					}
				}
			};
			_keepAliveTimer.schedule(
					timerTask, 
					_neighbor.getKeepAliveIntervalSecs() * 1000);
		}
	}
	
	/**
	 * Stop KeepAlive Timer
	 */
	private void stopKeepAliveTimer() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"stopKeepAliveTimer");
		}
		if (_keepAliveTimer != null) {
			_keepAliveTimer.cancel();
			_keepAliveTimer.purge();
			_keepAliveTimer = null;
		}
	}
	
	/**
	 * Restart the KeepAlive Timer
	 */
	private void restartKeepAliveTimer() {
		stopKeepAliveTimer();
		startKeepAliveTimer();
	}
	
	/**
	 * Start the Ack Timer
	 */
	private void startAckTimer() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"startAckTimer");
		}
		if (_ackTimer != null) {
			stopAckTimer();
		}
		_ackTimer = new Timer();
		TimerTask timerTask = new TimerTask() {
			@Override
			public void run() {
				try {
					_eventQueue.put(new TcpClEvent(
							TcpClEvent.SubEventTypes.ACK_TIMER_EVENT));
				} catch (InterruptedException e) {
					// Nothing
				}
			}
		};
		_ackTimer.schedule(timerTask, ACK_TIMEOUT_MSECS);
	}
	
	/**
	 * Stop the Ack Timer
	 */
	private void stopAckTimer() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"stopAckTimer");
		}
		if (_ackTimer != null) {
			_ackTimer.cancel();
			_ackTimer.purge();
			_ackTimer = null;
		}
	}
	
	
	/**
	 * Start Connection Initiator Thread
	 * @throws InterruptedException if interrupted during processing
	 */
	private void startInitiator() throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"startInitiator");
		}
		if (_initiatorThread != null) {
			stopInitiator();
		}
		_initiatorThread = new Thread(new ConnectionInitiator(), "tcpInitiator");
		_initiatorThread.start();
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"startInitiator DONE");
		}
	}

	/**
	 * Stop the Connection Initiator Thread
	 * @throws InterruptedException if interrupted during processing
	 */
	private void stopInitiator() throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"stopInitiator");
		}
		if (_initiatorThread != null) {
			_initiatorThread.interrupt();
			_initiatorThread.join(2000L);
			_initiatorThread = null;
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"stopInitiator DONE");
		}
	}
	
	/**
	 * Start the Receiver Thread
	 * @param socket Socket to perform receives on
 	 * @throws InterruptedException if interrupted during processing
	 */
	private void startReceiver(Socket socket) throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"startReceiver");
		}
		if (_receiverThread != null) {
			stopReceiver();
		}
		_receiverThread = new Thread(new ReceiverThread(_socket), "receiver");
		_receiverThread.start();
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"startReceiver DONE");
		}
	}
	
	/**
	 * Stop the Receiver Thread
	 * @throws InterruptedException if interrupted during processing
	 */
	private void stopReceiver() throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"stopReceiver");
		}
		_receiverThread.interrupt();
		_receiverThread.join(2000L);
		_receiverThread = null;
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
				"stopReceiver DONE");
		}
	}
	
	/**
	 * Connection Initiator Thread
	 */
	public class ConnectionInitiator implements Runnable {
		/**
		 * Initiate a connection to the peer
		 */
		@Override
		public void run() {
			try {
				// Find an operational Link Address for the Neighbor
				LinkAddress linkAddress = null;
				InetAddress inetAddress = null;
				while (linkAddress == null) {
					if (Thread.interrupted()) {
						if (GeneralManagement.isDebugLogging()) {
							_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
							"Interrupted");
						}
						return;
					}
					linkAddress = _neighbor.findOperationalLinkAddress();
					if (linkAddress == null) {
						if (GeneralManagement.isDebugLogging()) {
							_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
								"Waiting for Link Address for this Neighbor");
						}
						Thread.sleep(4000L);
					} else {
						if (!(linkAddress.getLink() instanceof TcpClLink)) {
							_logger.severe(
									(_isAccept ? "Acceptor: " : "Initiator: ") +
									"Link for Neighbor " + _neighbor.getName() + 
									" is not a TcpClLink");
							linkAddress = null;
							Thread.sleep(4000L);
							continue;
						}
						try {
							inetAddress = InetAddress.getByName(
									linkAddress.getAddress().toParseableString());
						} catch (UnknownHostException e) {
							_logger.severe(
									(_isAccept ? "Acceptor: " : "Initiator: ") +
									"Link for Neighbor " + _neighbor.getName() + 
									" has invalid IP Address");
							linkAddress = null;
							Thread.sleep(4000L);
							continue;
						}
					}
				}
				
				TcpClLink link = (TcpClLink)linkAddress.getLink();
					
				// Now try to open a connection with exponential backoff
				Socket socket = null;
				_connectionBackoff = CONNECTION_BACKOFF_MIN;
				while (socket == null) {
					if (Thread.interrupted()) {
						if (GeneralManagement.isDebugLogging()) {
							_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
							"Interrupted");
						}
						return;
					}
					try {
						_logger.fine((_isAccept ? "Acceptor: " : "Initiator: ") +
								"Trying connection to " + inetAddress);
						socket = new Socket(inetAddress, link.getTcpPort());
						if (GeneralManagement.isDebugLogging()) {
							_logger.fine((_isAccept ? "Acceptor: " : "Initiator: ") +
									"Connected to "
									+ socket.getRemoteSocketAddress());
						}
						
						// Got a Connection.  Signal state machine.
						_eventQueue.put(new TcpConnectedEvent(socket));
						
					} catch (InterruptedIOException e) {
						if (GeneralManagement.isDebugLogging()) {
							_logger.fine((_isAccept ? "Acceptor: " : "Initiator: ") +
								"Socket connect interrupted");
						}
						return;
						
					} catch (ConnectException e) {
						if (e.getMessage().contains("Connection refused") ||
							e.getMessage().contains("Operation timed out")) {
							if (GeneralManagement.isDebugLogging()) {
								_logger.fine((_isAccept ? "Acceptor: " : "Initiator: ") +
										"Connection to " + inetAddress + " refused or timed out");
							}
							Thread.sleep(_connectionBackoff);
							_connectionBackoff *= 2;
							if (_connectionBackoff > CONNECTION_BACKOFF_MAX) {
								_connectionBackoff = CONNECTION_BACKOFF_MAX;
							}
							
						} else {
							_logger.log(
									Level.SEVERE, 
									(_isAccept ? "Acceptor: " : "Initiator: ") +
									"Socket connect", e);
							Thread.sleep(_connectionBackoff);
						}
							
					} catch (IOException e) {
						_logger.log(Level.SEVERE, "Socket connect", e);
						Thread.sleep(_connectionBackoff);

					} catch (InterruptedException e) {
						if (GeneralManagement.isDebugLogging()) {
							_logger.fine((_isAccept ? "Acceptor: " : "Initiator: ") +
								"EventQueue insertion interrupted");
						}
						return;
					}
				}
					
			} catch (InterruptedException e) {
				if (GeneralManagement.isDebugLogging()) {
					_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
					"ConnectionInitiator interrupted");
				}
			}
			if (GeneralManagement.isDebugLogging()) {
				_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
					"ConnectionInitiator terminating");
			}
		}
	}
	
	/**
	 * Receiver Thread
	 */
	public class ReceiverThread implements Runnable {
		private Socket _rtSocket = null;
		private boolean _expectingContactHeader = true;
		
		public ReceiverThread(Socket socket) {
			_rtSocket = socket;
		}
		
		/**
		 * Read PDUs from peer until we're interrupted
		 */
		public void run() {
			try {
				_rtSocket.setReceiveBufferSize(SOCKET_RECV_BUFFER);
				while (!Thread.interrupted()) {
					
					// Read first byte of PDU
					int int0;
					try {
						int0 = _rtSocket.getInputStream().read();
					} catch (SocketException e1) {
						if (e1.getMessage().equals("Connection reset") ||
							e1.getMessage().equals("Operation timed out") ||
							e1.getMessage().equals("Connection timed out")) {
							if (GeneralManagement.isDebugLogging()) {
								_logger.fine((_isAccept ? "Acceptor: " : "Initiator: ") +
									"EOF Detected on Socket");
							}
							_eventQueue.put(
									new TcpClEvent(
											TcpClEvent.SubEventTypes.CONNECTION_CLOSED_REMOTE));
							
						} else if (e1.getMessage().equals("Socket closed")) {
							if (GeneralManagement.isDebugLogging()) {
								_logger.fine((_isAccept ? "Acceptor: " : "Initiator: ") +
									"Socket Closed detected on socket");
							}
							// This is a local close, not a remote close.  Means
							// the connection is being stopped.  No stimulation of
							// the State Machine needed.  Just drop out.
							
						} else {
							_logger.log(Level.SEVERE, "ReceiverThread", e1);
						}
						break;
					}
					if (int0 == -1) {
						if (GeneralManagement.isDebugLogging()) {
							_logger.fine((_isAccept ? "Acceptor: " : "Initiator: ") +
								"EOF detected on socket");
						}
						_eventQueue.put(
							new TcpClEvent(
								TcpClEvent.SubEventTypes.CONNECTION_CLOSED_REMOTE));
						break;
					}
					
					// Decode remaining bytes of PDU
					byte byte0 = Utils.intToByteUnsigned(int0);
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine(String.format("Received byte 0x%02x", int0));
					}
					DecodeState decodeState = 
						new DecodeState(_rtSocket.getInputStream());
					try {
						TcpClSegment segment = 
							TcpClSegment.decodeSegment(
									byte0, 
									decodeState, 
									_expectingContactHeader);
						if (segment instanceof TcpClContactHeaderSegment) {
							_expectingContactHeader = false;
						}
						
						// Enqueue InboundSegmentEvent to the State Machine
						_eventQueue.put(new InboundSegmentEvent(segment));
						
					} catch (JDtnException e) {
						_logger.log(Level.SEVERE, "ReceiverThread", e);
					}
				}
			} catch (InterruptedException e) {
				if (GeneralManagement.isDebugLogging()) {
					_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
					"ReceiverThread interrupted");
				}
				
			} catch (IOException e) {
				_logger.log(Level.SEVERE, "ReceiverThread", e);
				
			} finally {
				try {
					_rtSocket.close();
				} catch (IOException e) {
					// Nothing
				}
			}
			
			if (GeneralManagement.isDebugLogging()) {
				_logger.finer((_isAccept ? "Acceptor: " : "Initiator: ") +
					"ReceiverThread terminating");
			}
		}
	}
	
	/**
	 * Determine if this State Machine is an Acceptor State Machine
	 * @return True if this State Machine is an Acceptor
	 */
	public boolean isAcceptor() {
		return _isAccept;
	}
	
}
