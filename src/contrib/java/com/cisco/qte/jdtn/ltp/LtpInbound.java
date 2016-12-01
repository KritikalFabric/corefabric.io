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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.component.AbstractEventProcessorThread;
import com.cisco.qte.jdtn.component.EventBroadcaster;
import com.cisco.qte.jdtn.component.IEvent;
import com.cisco.qte.jdtn.component.StopEvent;
import com.cisco.qte.jdtn.events.CancelTimerExpiredEvent;
import com.cisco.qte.jdtn.events.InboundSegmentEvent;
import com.cisco.qte.jdtn.events.JDTNEvent;
import com.cisco.qte.jdtn.events.LinksEvent;
import com.cisco.qte.jdtn.events.NeighborScheduledStateChangeEvent;
import com.cisco.qte.jdtn.events.ReportSegmentTimerExpiredEvent;
import com.cisco.qte.jdtn.events.SegmentTransmitStartedEvent;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Link;
import com.cisco.qte.jdtn.general.LinkListener;
import com.cisco.qte.jdtn.general.LinksList;
import com.cisco.qte.jdtn.general.Neighbor;
import com.cisco.qte.jdtn.ltp.InboundBlock.LtpReceiverState;
import com.cisco.qte.jdtn.ltp.Segment.SegmentType;

/**
 * Inbound processing for Ltp.  Performs the state processing for inbound
 * Segments.  Maintains a List of Blocks that are undergoing reassembly.
 * Delivers completed Blocks to LtpApi.
 * In some cases, delegates to LtpOutbound.
 * <p>
 * Note: We do not implement recommendations for Replay Handling from Section
 * 9.2 of RFC5326.
 */
public class LtpInbound extends AbstractEventProcessorThread
implements LinkListener, SegmentTransmitCallback {

	public static final int LTP_INBOUND_EVENT_QUEUE_CAPACITY = 1300;
	public static final long LTP_INBOUND_JOIN_DELAY_MSECS = 2000L;
	
	@SuppressWarnings("hiding")
	private static final Logger _logger =
		Logger.getLogger(LtpInbound.class.getCanonicalName());
	
	private static LtpInbound _instance = null;
	// List of Inbound Blocks
	private ArrayList<InboundBlock> _inboundBlockList =
		new ArrayList<InboundBlock>();
	// Map SessionId to Inbound Block
	private HashMap<SessionId, InboundBlock> _mapSessionIdToBlock =
		new HashMap<SessionId, InboundBlock>();
	
	// Timer
	private Timer _timer = null;
	
	/**
	 * Get singleton instance
	 * @return Singleton instance
	 */
	public static LtpInbound getInstance() {
		if (_instance == null) {
			_instance = new LtpInbound();
		}
		return _instance;
	}
	
	private LtpInbound() {
		super("LtpInbound", LTP_INBOUND_EVENT_QUEUE_CAPACITY);
	}
	
	/**
	 * Startup; Registers with all known links; registers with LinksList
	 * for notification on all Link adds/removes.
	 */
	@Override
	protected void startImpl() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("startImpl()");
		}
		_timer = new Timer();
		for (Link link : LinksList.getInstance()) {
			if (link instanceof LtpLink) {
				link.addLinkListener(this);
			}
		}
		EventBroadcaster.getInstance().registerEventProcessor(
				LinksList.class.getCanonicalName(), this);
		super.startImpl();
	}
	
	/**
	 * Shutdown; Deregisters with all known links and with LinksList.
	 * Closes all pending Blocks.
	 */
	private void shutdown() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("stopImpl()");
		}
		_timer.purge();
		_timer.cancel();
		_timer = null;
		
		EventBroadcaster.getInstance().unregisterEventProcessor(
				LinksList.class.getCanonicalName(), this);
		
		for (Link link : LinksList.getInstance()) {
			if (link instanceof LtpLink) {
				link.removeLinkListener(this);
			}
		}
		
		while (!_inboundBlockList.isEmpty()) {
			InboundBlock block = _inboundBlockList.remove(0);
			removeInboundBlock(block);
		}
	}
	
	/**
	 * Called when a new Segment arrives on a Link.
	 * @param segment Newly arrived Segment
	 * @throws InterruptedException If interrupted when blocked.
	 */
	public void onInboundSegment(Segment segment) 
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onInboundSegment()");
		}
		
		// Decide which event processing Thread to send it to
		if (segment.isReportSegment()) {
			// LtpOutbound gets all ReportSegments
			LtpManagement.getInstance()._ltpStats.nReportSegmentsReceived++;
			ReportSegment reportSegment = (ReportSegment)segment;
			LtpOutbound.getInstance().onReportSegment(reportSegment);
			
		} else if (segment.isCancelSegmentFromReceiver()) {
			// LtpOutbound gets all Cancel Segments from Receiver
			LtpManagement.getInstance()._ltpStats.nCancelsReceived++;
			CancelSegment cancelSegment = (CancelSegment)segment;
			LtpOutbound.getInstance().onCancelSegment(cancelSegment);
			
		} else {
			// All others, enqueue to LtpInbound
			InboundSegmentEvent event =
				new InboundSegmentEvent(segment);
			processEvent(event);
		}
	}
	
	/**
	 * Called when given Segment is "on the wire".
	 * If the Segment is a ReportSegment, then we start its ReportSegment Timer.
	 * @throws InterruptedException 
	 */
	@Override
	public void onSegmentTransmitStarted(LtpLink link, Segment segment)
	throws InterruptedException {
		
		if (segment.isReportSegment() || segment.isCancelSegmentFromReceiver()) {
			if (GeneralManagement.isDebugLogging()) {
				_logger.finer("onSegmentTransmitStarted()");
			}
			SegmentTransmitStartedEvent event =
				new SegmentTransmitStartedEvent(segment, link);
			processEvent(event);
		}
	}
	
	/**
	 * Called when a higher level component has changed "Scheduled" state of the given
	 * Neighbor, to either inhibit transmission or to enable transmission.  This
	 * is in response to a scheduled change in Neighbor State, a planned event.
	 * @param neighbor The affected Neighbor
	 * @param neighborScheduledUp Whether the Neighbor has been scheduled up (true) or down.
	 * @throws InterruptedException if interrupted
	 */
	@Override
	public void onNeighborScheduledStateChange(
			Neighbor neighbor,
			boolean neighborScheduledUp) 
	throws InterruptedException {		
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onNeighborScheduledStateChange()");
		}
		
		if (neighbor instanceof LtpNeighbor) {
			NeighborScheduledStateChangeEvent event =
				new NeighborScheduledStateChangeEvent(
						(LtpNeighbor)neighbor, 
						neighborScheduledUp);
			processEvent(event);
		}
	}
	
	/**
	 * Thread event handler
	 */
	@Override
	public void processEventImpl(IEvent iEvent) {
		try {
			if (iEvent instanceof StopEvent) {
				StopEvent sEvent = (StopEvent)iEvent;
				shutdown();
				sEvent.notifySyncProcessed();
				return;
			} else if (!(iEvent instanceof JDTNEvent)) {
				throw new IllegalArgumentException("Event not instance of JDTNEvent");
			}
			JDTNEvent event = (JDTNEvent)iEvent;
			switch (event.getEventType()) {

			case LINKS_EVENT:
				LinksEvent lEvent = (LinksEvent)iEvent;
				switch (lEvent.getLinksEventSubtype()) {
				case LINK_ADDED_EVENT:
					onLinkAdded(lEvent.getLink());
					break;
				case LINK_REMOVED_EVENT:
					onLinkRemoved(lEvent.getLink());
					break;
				default:
					_logger.severe("Unknown LinksEvent (" + lEvent.getLinksEventSubtype() + ")");
					break;
				}
				break;
				
			case INBOUND_SEGMENT:
				InboundSegmentEvent ise =
					(InboundSegmentEvent)event;
				processInboundSegment(ise.getSegment());
				break;
				
			case CANCEL_TIMER_EXPIRED:
				CancelTimerExpiredEvent ctee =
					(CancelTimerExpiredEvent)event;
				processCancelTimerExpired(
						(CancelSegment)ctee.getSegment(), 
						(InboundBlock)ctee.getBlock());
				break;
				
			case REPORT_SEGMENT_TIMER_EXPIRED:
				ReportSegmentTimerExpiredEvent rstee =
					(ReportSegmentTimerExpiredEvent)event;
				processReportSegmentTimerExpired(
						(ReportSegment)rstee.getSegment());
				break;
				
			case SEGMENT_TRANSMIT_STARTED:
				SegmentTransmitStartedEvent stse =
					(SegmentTransmitStartedEvent)event;
				processSegmentTransmitStarted(stse.getLink(), stse.getSegment());
				break;
				
			case NEIGHBOR_SCHEDULED_STATE_CHANGE:
				NeighborScheduledStateChangeEvent nssce =
					(NeighborScheduledStateChangeEvent)event;
				processNeighborScheduledStateChange(
						nssce.getNeighbor(), 
						nssce.isUp());
				break;
				
			default:
				_logger.severe("Unknown event " + event.getEventType());
				break;
			}
			
		} catch (InterruptedException e) {
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("LtpInbound interrupted");
			}
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("LtpInbound terminating");
		}
	}
	
	/**
	 * Cancel an InboundBlock in the process of being received.
	 * @param block Block to be cancelled
	 * @param reason why block is being cancelled; see CancelSegment.REASON...
	 * @throws InterruptedException if interrupted while blocked
	 */
	private void cancelBlock(InboundBlock block, byte reason) 
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("cancelBlock()");
			if (_logger.isLoggable(Level.FINER)) {
				_logger.finer(block.getSessionId().dump("", true));
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.finest(block.dump("  ", true));
				}
			}
		}
		
		// Build and send CancelSegment; set block state to CancelSent.
		// Then we'll await the Cancel Ack or Cancel Timeout for further action.
		CancelSegment cancelSegment =
			new CancelSegment(
					SegmentType.CANCEL_SEGMENT_FROM_RECEIVER,
					reason, 
					block.getSessionId());
		block.setLtpReceiverState(LtpReceiverState.CR_SENT);
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("Session => " + block.getLtpReceiverState());
		}
		try {
			LtpManagement.getInstance()._ltpStats.nCancelsSent++;
			transmitSegment(cancelSegment, block.getLink(), block.getNeighbor());
		} catch (LtpException e) {
			_logger.log(Level.SEVERE, "Transmitting CancelSegment", e);
			LtpApi.getInstance().onSystemError("Transmitting CancelSegment", e);
		}
		block.setOutstandingCancelSegment(cancelSegment);
	}
	
	/**
	 * Cancel a DataSegment when we don't know the Block it is part of
	 * @param dataSegment Given DataSegment
	 * @param reason Reason for cancellation
	 * @throws InterruptedException
	 */
	private void cancelSegment(DataSegment dataSegment, byte reason)
			throws InterruptedException {
		CancelSegment cancelSegment = new CancelSegment(
				SegmentType.CANCEL_SEGMENT_FROM_RECEIVER,
				reason, 
				dataSegment.getSessionID());

		try {
			LtpManagement.getInstance()._ltpStats.nCancelAcksSent++;
			transmitSegment(cancelSegment, dataSegment.getLink(),
					dataSegment.getNeighbor());
		} catch (LtpException e) {
			_logger.log(Level.SEVERE, "send CancelSegment to neighbor "
					+ dataSegment.getNeighbor(), e);
			LtpApi.getInstance().onSystemError("Sending Cancel Segment", e);
			// Neighbor is down, so cannot send CancelSegment.
			// We can't really do any more recovery.
		}
	}

	/**
	 * Add given Block to list of Inbound Blocks awaiting reassembly.
	 * Set its state to CLOSED, the beginning of processing of a received Block.
	 * @param block Given Block
	 * @throws InterruptedException If interrupted during block
	 * @throws JDtnException on various errors
	 */
	private void addInboundBlock(InboundBlock block) {
		block.setLtpReceiverState(LtpReceiverState.CLOSED);
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("Session => " + block.getLtpReceiverState());
		}
		_inboundBlockList.add(block);
		_mapSessionIdToBlock.put(block.getSessionId(), block);
	}
	
	/**
	 * Remove given Block from list of Inbound Blocks.  Set its receiver state
	 * to CLOSED (the end of processing of Block), and close the Block to clean
	 * it up.
	 * @param block Given Block
	 */
	private void removeInboundBlock(InboundBlock block) {
		_inboundBlockList.remove(block);
		_mapSessionIdToBlock.remove(block.getSessionId());
		block.setLtpReceiverState(LtpReceiverState.CLOSED);
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("Session => " + block.getLtpReceiverState());
		}
		block.closeBlock();
	}
	
	/**
	 * Find Block matching given SessionId in Inbound Blocks list.
	 * @param sessionId GIven SessionId
	 * @return Matching Block or null if none
	 */
	private InboundBlock findBlockBySessionId(SessionId sessionId) {
			return _mapSessionIdToBlock.get(sessionId);
	}
	
	/**
	 * Called when a new Segment arrives on a Link.
	 * We dispatch to lower level based on the type of Segment. Can block.
	 * @param segment Newly arrived Segment
	 * @throws InterruptedException If interrupted when blocked.
	 */
	private void processInboundSegment(Segment segment) 
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("processInboundSegment(type=" + segment.getSegmentType() + ")");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(segment.dump("  ", true));
			}
		}
		
		if (segment instanceof DataSegment) {
			LtpManagement.getInstance()._ltpStats.nDataSegmentsReceived++;
			DataSegment dataSegment = (DataSegment)segment;
			processInboundDataSegment(dataSegment);
			
		} else if (segment instanceof CancelSegment) {
			LtpManagement.getInstance()._ltpStats.nCancelsReceived++;
			CancelSegment cancelSegment = (CancelSegment)segment;
			processInboundCancelSegment(cancelSegment);

		} else if (segment instanceof CancelAckSegment) {
			LtpManagement.getInstance()._ltpStats.nCancelAcksReceived++;
			CancelAckSegment cancelAckSegment = (CancelAckSegment)segment;
			processInboundCancelAckSegment(cancelAckSegment);
			
		} else if (segment instanceof ReportAckSegment) {
			LtpManagement.getInstance()._ltpStats.nReportAcksReceived++;
			ReportAckSegment reportAckSegment = (ReportAckSegment)segment;
			processInboundReportAckSegment(reportAckSegment);
			
		} else {
			_logger.warning("Received segment with unexpected segment type");
			_logger.warning(segment.dump("  ", true));
		}
		
	}
	
	/**
	 * Process newly arrived DataSegment.
	 * @param dataSegment
	 * @throws InterruptedException
	 */
	private void processInboundDataSegment(DataSegment dataSegment) 
	throws InterruptedException {
		
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("processInboundDataSegment(" + dataSegment.getSegmentType() + ")");
			if (_logger.isLoggable(Level.FINER)) {
				_logger.finer(dataSegment.getSessionID().dump("", true));
			}
		}
		
		// 6.0 Internal Procedures - in the event that the client service
		// identified by the data segment does not exist at the local LTP
		// engine; if the data segment contains data from the red-part
        // of the block, a CR with reason-code UNREACH MUST be enqueued for 
		// transmission to the block sender.  A CR with reason-code UNREACH
        // SHOULD be similarly enqueued for transmission to the data sender
        // even if the data segment contained data from the green-part of
        // the block transmission to the block sender
		if (!LtpApi.getInstance().isClientRegisteredForServiceId(
				dataSegment.getClientServiceId())) {
			_logger.severe("No client registered for ServiceId " + 
					dataSegment.getClientServiceId());
			cancelSegment(dataSegment, CancelSegment.REASON_CODE_SYSTEM_CANCELED);
			return;
		}

		// See if DataSegment is part of a Block being reassembled
		InboundBlock block = findBlockBySessionId(dataSegment.getSessionID());
		if (block == null) {
			// This DataSegment is not part of any Block being reassembled.
			// It could be a "redundant" re-send of a prior Block that was
			// completly acked and delivered.  If this is the case, we'll just
			// discard the DataSegment.
			if (dataSegment.isResend()) {
				
				_logger.warning(
						"DataSegment is redundant re-send; Discarding");
				if (GeneralManagement.isDebugLogging()) {
					if (_logger.isLoggable(Level.FINEST)) {
						_logger.finest(dataSegment.dump("  ", true));
					}
				}
				return;
			}
			
			// This is the first Segment of a new Block
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("New Inbound Session");
				if (_logger.isLoggable(Level.FINER)) {
					_logger.finer(dataSegment.getSessionID().dump("", true));
				}
			}
			try {
				LtpManagement.getInstance()._ltpStats.nBlocksReceived++;
				block = new InboundBlock(dataSegment);
			} catch (JDtnException e) {
				_logger.log(Level.SEVERE, "Creating InboundBlock", e);
				LtpApi.getInstance().onSystemError("Creating InboundBlock", e);
				cancelSegment(dataSegment, CancelSegment.REASON_CODE_SYSTEM_CANCELED);
				return;
			}
			addInboundBlock(block);
			LtpApi.getInstance().onSessionStarted(block);
			
		} else {
			// Not first segment of block; append to Block
			try {
				block.addInboundSegment(dataSegment);
			} catch (LtpException e) {
				_logger.log(Level.SEVERE, "Appending InboundBlock", e);
				LtpApi.getInstance().onSystemError("Appending InboundBlock", e);
				cancelBlock(block, CancelSegment.REASON_CODE_SYSTEM_CANCELED);
				return;
			}
		}

		if (block.isMiscolored(block, dataSegment)) {
			 // 6.21.  Handle Miscolored Segment
			 //  This procedure is triggered by the arrival of either (a) a red-part
			 //  data segment whose block offset begins at an offset higher than the
			 //  block offset of any green-part data segment previously received for
			 //  the same session or (b) a green-part data segment whose block offset
			 //  is lower than the block offset of any red-part data segment
			 //  previously received for the same session.  The arrival of a segment
			 //  matching either of the above checks is a violation of the protocol
			 //  requirement of having all red-part data as the block prefix and all
			 //  green-part data as the block suffix.
			 //  Response: the received data segment is simply discarded.
			 //  The Cancel Session procedure (Section 6.19) is invoked and a CR
			 //  segment with reason-code MISCOLORED SHOULD be enqueued for
			 //  transmission to the data sender.
			 //  Note: If there is no transmission queue-set bound for the sender
			 //  (possibly because the local LTP engine is running on a receive-only
			 //  device), or if the receiver knows that the sender is functioning in a
			 // "beacon" (transmit-only) fashion, a CR segment need not be sent.
			 //  A reception-session cancellation notice (Section 7.6) is sent to the
			 //  client service.
			_logger.warning("Miscolored Block");
			if (GeneralManagement.isDebugLogging()) {
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.finest(block.dump("", true));
					_logger.finest(dataSegment.dump("", true));
				}
			}
			cancelBlock(block, CancelSegment.REASON_CODE_MISCOLORED);
			return;			
		}
		
		// 6.11.  Send Reception Report
		//   This procedure is triggered by either (a) the original reception of a
		//   CP segment (the checkpoint serial number identifying this CP is new)
		//   (b) an implementation-specific circumstance pertaining to a
		//   particular block reception session for which no EORP has yet been
		//   received ("asynchronous" reception reporting).
		//   Response: if the number of reception problems detected for this
		//   session exceeds a limit established for the local LTP engine by
		//   network management, then the affected session is canceled: the
		//   "Cancel Session" procedure (Section 6.19) is invoked, a CR segment
		//   with reason-code RLEXC is issued and is, in concept, appended to the
		//   queue of internal operations traffic bound for the LTP engine that
		//   originated the session, and a reception-session cancellation notice
		//   (Section 7.6) is sent to the client service identified in each of the
		//   data segments received in this session.  One possible limit on
		//   reception problems would be the maximum number of reception reports
		//   that can be issued for any single session.
		if (dataSegment.isCheckpoint() && !dataSegment.isResend()) {
			if (block.isTooManyReceptionProblems()) {
				cancelBlock(block, CancelSegment.REASON_CODE_RETRANS_LIMIT_EXCEEDED);
				return;
			}
		}
		
		// 6.9.  Signify Red-Part Reception
		//   This procedure is triggered by the arrival of a CP segment when the
		//   EORP for this session has been received (ensuring that the size of
		//   the data block's red-part is known; this includes the case where the
		//   CP segment itself is the EORP segment) and all data in the red-part
		//   of the block being transmitted in this session have been received.
		//   Response: a red-part reception notice (Section 7.3) is sent to the
		//   specified client service.
		if (dataSegment.isCheckpoint()) {
			if (block.isAllRedDataReceived()) {
				LtpApi.getInstance().onRedPartReceived(block);
			}
		}
				
		// 6.10.  Signify Green-Part Segment Arrival
		//   This procedure is triggered by the arrival of a data segment whose
		//   content is a portion of the green-part of a block.
		//   Response: a green-part segment arrival notice (Section 7.2) is sent
		//   to the specified client service.
		if (dataSegment.isGreenData()) {
			LtpApi.getInstance().onGreenSegmentReceived(dataSegment);
		}
		
		// Process received segment according to rfc5326 8.2 Receiver State Machine
		SegmentType segmentType = dataSegment.getSegmentType();
		switch (block.getLtpReceiverState()) {
		case CLOSED:
			// First DataSegment received for this Block
			// Fall thru
		case DS_REC:
			// Receiving DataSegments
			if (dataSegment.isRedData()) {
				// Red segment arrived
				// RCV_RP is a transitory state while we figure out what next
				block.setLtpReceiverState(LtpReceiverState.RCV_RP);
				if (GeneralManagement.isDebugLogging()) {
					_logger.fine("Session => " + block.getLtpReceiverState());
				}
				switch (segmentType) {
				case RED_NOTCP_NOT_EORP_NOT_EOB:
					// Red segment; more Red to follow
					block.setLtpReceiverState(LtpReceiverState.DS_REC);
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("Session => " + block.getLtpReceiverState());
					}
					break;
				case RED_CP_NOT_EORP_NOT_EOB:
					// Red segment; Checkpoint; more Red to follow
					// Send Report
					try {
						sendReceptionReport(block, dataSegment);
					} catch (LtpException e) {
						_logger.log(Level.SEVERE, "Sending Reception Report", e);
						LtpApi.getInstance().onSystemError("Sending Reception Report", e);
						cancelBlock(block, CancelSegment.REASON_CODE_SYSTEM_CANCELED);
						return;
					}
					block.setLtpReceiverState(LtpReceiverState.DS_REC);
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("Session => " + block.getLtpReceiverState());
					}
					break;
				case RED_CP_EORP_NOT_EOB:
					// Red segment; checkpoint; End of Red Part; Green to follow
					// Send Report
					try {
						sendReceptionReport(block, dataSegment);
					} catch (LtpException e) {
						_logger.log(Level.SEVERE, "Sending Reception Report", e);
						LtpApi.getInstance().onSystemError("Sending Reception Report", e);
						cancelBlock(block, CancelSegment.REASON_CODE_SYSTEM_CANCELED);
						return;
					}
					block.setLtpReceiverState(LtpReceiverState.DS_REC);
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("Session => " + block.getLtpReceiverState());
					}
					break;
				case RED_CP_EORP_EOB:
					// Red segment; End of Red Part, End of Block, checkpoint; no segments to follow
					// Send Report
					try {
						sendReceptionReport(block, dataSegment);
					} catch (LtpException e) {
						_logger.log(Level.SEVERE, "Sending Reception Report", e);
						LtpApi.getInstance().onSystemError("Sending Reception Report", e);
						cancelBlock(block, CancelSegment.REASON_CODE_SYSTEM_CANCELED);
						return;
					}
					block.setLtpReceiverState(LtpReceiverState.WAIT_RP_REC);
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("Session => " + block.getLtpReceiverState());
					}
					break;
					
				default:
					// Ignore; no other segment types possible for Red DataSegments
					_logger.severe("FIXME");
					break;
				}
				
			} else {
				// Green segment arrived
				// RCV_GP is a transitory state while we figure out where next
				block.setLtpReceiverState(LtpReceiverState.RCV_GP);
				if (GeneralManagement.isDebugLogging()) {
					_logger.fine("Session => " + block.getLtpReceiverState());
				}
				switch (segmentType) {
				case GREEN_NOT_EOB:
					block.setLtpReceiverState(LtpReceiverState.DS_REC);
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("Session => " + block.getLtpReceiverState());
					}
					break;
				case GREEN_EOB:

					block.setLtpReceiverState(LtpReceiverState.WAIT_RP_REC);
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("Session => " + block.getLtpReceiverState());
					}
					if (block.isInboundBlockComplete()) {
						if (block.isAllGreenDataReceived()) {
							deliverCompletedBlock(block);
						} else {
							_logger.warning("Received Green EOB but not all green segments received");
							cancelBlock(block, CancelSegment.REASON_CODE_SYSTEM_CANCELED);
							return;
						}
					}
					break;
					
				default:
					// Ignore; no other segment types possible for Green Data Segments
					_logger.severe("FIXME");
					break;
				}
			}			
			break;
			
		case WAIT_RP_REC:
			// We have received EOB on the Block
			// Now awaiting remaining Segments and final ReportAck.
			switch (segmentType) {
			case RED_NOTCP_NOT_EORP_NOT_EOB:
				// Suppose that there's a mixed
				// color block, a non-Checkpoint segment is dropped, all Report
				// Segments get acked.  But no ReportAcks can complete the Block
				// because of that missing non-Checkpoint segment.
				// Sender will notice in all Reports that there is
				// a missing segment, that missing non-Checkpoint segment.  It will
				// retransmit the missing segment, and it will come here. So I
				// think I need the following.
//				if (block.isInboundBlockComplete()) {
//					deliverCompletedBlock(block);
//				}
				break;
				
			case RED_CP_NOT_EORP_NOT_EOB:
				// Received block is Checkpoint, not EORP
				// Send discretionary Report
				try {
					sendReceptionReport(block, dataSegment);
				} catch (LtpException e) {
					_logger.log(Level.SEVERE, "Sending Reception Report", e);
					LtpApi.getInstance().onSystemError("Sending Reception Report", e);
					cancelBlock(block, CancelSegment.REASON_CODE_SYSTEM_CANCELED);
					return;
				}
				break;
			case RED_CP_EORP_NOT_EOB:
				// Received block is Checkpoint, EORP
				// Send mandatory Report
				try {
					sendReceptionReport(block, dataSegment);
				} catch (LtpException e) {
					_logger.log(Level.SEVERE, "Sending Reception Report", e);
					LtpApi.getInstance().onSystemError("Sending Reception Report", e);
					cancelBlock(block, CancelSegment.REASON_CODE_SYSTEM_CANCELED);
					return;
				}
				break;
			case RED_CP_EORP_EOB:
				// Received block is a Checkpoint, EORP, EOB.
				// Send mandatory Report
				try {
					sendReceptionReport(block, dataSegment);
				} catch (LtpException e) {
					_logger.log(Level.SEVERE, "Sending Reception Report", e);
					LtpApi.getInstance().onSystemError("Sending Reception Report", e);
					cancelBlock(block, CancelSegment.REASON_CODE_SYSTEM_CANCELED);
					return;
				}
				break;

			default:
				// Progression of states should not allow this to happen
				_logger.severe("FIXME");
				break;
			}
			break;
			
		case CR_SENT:
			// Awaiting Cancel Ack to our prior Cancel Segment.
			// Ignore this DataSegment
			break;
			
		default:
			// RCV_RP and RCV_GP are transitory states and we shouldn't
			// be in these states when a segment arrives.
			_logger.severe("FIXME!");
			break;
		}		
	}

	/**
	 * Process a newly arrived CancelSegment (from Sender).  Can block.
	 * <p>
	 * We handle it by:
	 * <ul>
	 *   <li>Send CancelAck
	 *   <li>Cancel the affected block
	 *   <li>Send Block Cancelled to LtpApi
	 * </ul>
	 * @param cancelSegment Newly arrived CancelSegment
	 * @throws InterruptedException If interrupted while blocked
	 */
	private void processInboundCancelSegment(CancelSegment cancelSegment) 
	throws InterruptedException {
		
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("processInboundCancelSegment()");
			if (_logger.isLoggable(Level.FINER)) {
				_logger.finer(cancelSegment.getSessionID().dump("", true));
			}
		}
			
		// 6.17.  Acknowledge Cancellation
		//   This procedure is triggered by the reception of a Cx segment.
		//   Response: in the case of a CS segment where there is no transmission
		//   queue-set bound for the sender (possibly because the receiver is a
		//   receive-only device), then no action is taken.  Otherwise:
		//      - If the received segment is a CS segment, a CAS (cancel
		//        acknowledgment to block sender) segment is issued and is, in
		//        concept, appended to the queue of internal operations traffic
		//        bound for the sender.
		//   It is possible that the Cx segment has been retransmitted because a
		//   previous responding acknowledgment CAx (cancel acknowledgment)
		//   segment was lost, in which case there will no longer be any record of
		//   the session of which the segment is one token.  If so, no further
		//   action is taken.
		// Received CancelSegment from sender of Block
		// Send CancelAck
		CancelAckSegment cancelAckSegment = 
			new CancelAckSegment(
					SegmentType.CANCEL_SEGMENT_ACK_TO_SENDER,
					cancelSegment.getSessionID());
		try {
			LtpManagement.getInstance()._ltpStats.nCancelAcksSent++;
			transmitSegment(cancelAckSegment, cancelSegment.getLink(), cancelSegment.getNeighbor());
		} catch (LtpException e) {
			// Error transmitting CancelAckSegment
			_logger.log(Level.SEVERE, "Transmitting CancelAckSegment", e);
			LtpApi.getInstance().onSystemError("Transmitting CancelAckSegment", e);
		}

		// Find the Block being cancelled
		InboundBlock block = findBlockBySessionId(cancelSegment.getSessionID());
		if (block == null) {
			_logger.warning("No active block found for received CancelSegment");
			_logger.warning(cancelSegment.dump("  ", true));
			return;
		}
		
		// Remove any outbound segments for this Block from the Link
		block.getLink().onBlockCancelled(block);
		
		// Remove Block and all its segments from our queues
		removeInboundBlock(block);
		// Deliver BlockCancelled notice to LtpApi
		LtpApi.getInstance().onBlockReceiveCancelled(block, cancelSegment.getReasonCode());
	}
	
	/**
	 * Process a newly arrived CancelAckSegment.  Can be either:
	 * <ul>
	 *   <li>CancelAckSegment to receiver - We handle this
	 *   <li>CancelAckSegment to sender - We delegate to LtpOutbound
	 * </ul>
	 * <p>
	 * We handle it by doing the following:
	 * <ul>
	 *   <li>Remove the Block and its segments from our queues.
	 *   <li>Remove all outstanding ReportSegments for the session and
	 *   Cancel all RSTimers on all of block's segments
	 *   <li>Deliver a BlockCancelled notice to LtpApi
	 * </ul>
	 * @param cancelAckSegment
	 * @throws InterruptedException if interrupted
	 */
	private void processInboundCancelAckSegment(CancelAckSegment cancelAckSegment)
	throws InterruptedException {
		
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("processInboundCancelAckSegment()");
			if (_logger.isLoggable(Level.FINER)) {
				_logger.finer(cancelAckSegment.getSessionID().dump("", true));
			}
		}
		if (cancelAckSegment.isCancelAckSegmentToReceiver()) {
			// Cancel Ack is To Receiver of Block being cancelled (Us)
			// Make sure Cancel Ack references an active Inbound Block
			InboundBlock block = findBlockBySessionId(cancelAckSegment.getSessionID());
			if (block == null) {
				_logger.warning("No active Block for received CancelAckSegment");
				_logger.warning(cancelAckSegment.dump("  ", true));
				return;
			}
			
			// Make sure the affected Block is in CR_SENT state
			if (block.getLtpReceiverState() != LtpReceiverState.CR_SENT) {
				_logger.warning("Received CancelAckSegment on Block not in CS_SENT state");
				_logger.warning(cancelAckSegment.dump("  ", true));
				_logger.warning(block.dump("  ", true));
				return;
			}
			
			// 6.18.  Stop Cancel Timer
			//   This procedure is triggered by the reception of a CAx segment.
			//   Response: the timer associated with the Cx segment is deleted, and
			//   the session of which the segment is one token is closed, i.e., the
			//   "Close Session" procedure (Section 6.20) is invoked.
			// Stop Cancel Timer
			stopCancelTimer(block.getOutstandingCancelSegment(), block);
			
			// Remove any outbound segments for this Block from the Link
			block.getLink().onBlockCancelled(block);
			
			// Remove Block and all its segments from our queues
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Inbound Session Cancelled");
			}
			removeInboundBlock(block);
			// Deliver BlockCancelled notice to LtpApi
			byte reason = CancelSegment.REASON_CODE_SYSTEM_CANCELED;
			if (block.getOutstandingCancelSegment() != null) {
				reason = block.getOutstandingCancelSegment().getReasonCode();
			}
			LtpApi.getInstance().onBlockReceiveCancelled(block, reason);			
			
		} else if (cancelAckSegment.isCancelAckSegmentToSender()) {
			// Cancel Ack is to Sender of Block being cancelled (Us)
			// We need to clean up our state as transmitter of block
			// Delegate to LtpOutbound
			LtpOutbound.getInstance().onCancelAckSegment(cancelAckSegment);
		}
	}

	/**
	 * Process newly arrived ReportAckSegment.  We find the Block mentioned
	 * by the ReportAckSegment via SessionId.  We find the outstanding ReportSegment
	 * mentioned by the ReportAckSegment via ReportSerialNumber.  We iterate
	 * thru the Claims in the ReportSegment.  For each such Claim, we mark the
	 * corresponding DataSegment in the Block as acked.  If all DataSegments
	 * have been acked, then we deliver the Block to the LtpApi.
	 * @param reportAckSegment newly arrived ReportAckSegment.
	 * @throws InterruptedException 
	 */
	private void processInboundReportAckSegment(ReportAckSegment reportAckSegment) 
	throws InterruptedException {

		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("processInboundReportAckSegment()");
			if (_logger.isLoggable(Level.FINER)) {
				_logger.finer(reportAckSegment.getSessionID().dump("", false));
				_logger.finer(reportAckSegment.getReportSerialNumber().dump("", false));
			}
		}
		
		// Find block by SessionId in ReportAckSegment
		InboundBlock block = findBlockBySessionId(reportAckSegment.getSessionID());
		if (block == null) {
			// The ReportAckSegment references a Block which doesn't exist.
			// It was probably a redundant Report Ack on a completed Block.
			_logger.warning("Redundant Inbound Report Ack Segment discarded");
			if (GeneralManagement.isDebugLogging()) {
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.finest(reportAckSegment.dump("  ", true));
				}
			}
			return;
		}
		
		// Find the outstanding ReportSegment being acked by ReportSerialNumber
		ReportSegment reportSegment =
			block.getOutstandingReportSegment(
					reportAckSegment.getReportSerialNumber());
		if (reportSegment == null) {
			_logger.warning("No active ReportSegment for Inbound Report Ack Segment");
			if (GeneralManagement.isDebugLogging()) {
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.finest(reportAckSegment.dump("  ", true));
					_logger.finest(block.dump("  ", true));
				}
			}
			return;
		}
		// ReportSegment has been acked, remove it from Block's list of
		// outstanding ReportSegments.
		block.removeReportSegment(reportSegment);
		
		// 6.14.  Stop RS Timer
		//   This procedure is triggered by the reception of an RA.
		//   Response: the countdown timer associated with the original RS segment
		//   (identified by the report serial number of the RA segment) is
		//   deleted.  If no other countdown timers associated with RS segments
		//   exist for this session, then the session is closed: the "Close
		//   Session" procedure (Section 6.20) is invoked.
		// Kill the ReportSegment RS Timer
		stopReportSegmentTimer(reportSegment);
		
		// Iterate thru the DataSegments in the Block and see if the ReportSegment
		// asserts that the DataSegment has been received.  If so, mark it acked.
		// Performance is O(nSegmentsInBlock * nClaimsInReportSegment)
		// If this turns out to be a performance problem, we can improve
		// average performance but not worst case performance by reversing
		// the order of the loops.  However, I prefer keeping these decisions
		// inside ReportSegment.
		for (DataSegment dataSegment : block) {
			if (reportSegment.covers(dataSegment)) {
				dataSegment.setAcked(true);
			}
		}
		
		// Figure out next state
		switch (block.getLtpReceiverState()) {
		case DS_REC:
			// We've already done what needs to be done.
			// No state transition because in DS_REC we're still looking for EOB
			break;
			
		case WAIT_RP_REC:
			// In WAIT_RP_REC, We've seen EOB and are awaiting ACK of all Segments
			// If all DataSegments in the Block are acked, then deliver the
			// Block.
			if (block.isInboundBlockComplete()) {
				deliverCompletedBlock(block);
			}			
			break;
			
		case CR_SENT:
			// In CR_SENT, the Block is being cancelled.  Other side might
			// be sending ReportAcks until it notices the Cancel we sent.
			// So just ignore this ReportAck.
			break;
			
		default:
			// In all other states, ReportAck is unexpected
			_logger.warning("Unexpected ReportAck for Block in state " +
					block.getLtpReceiverState());
			_logger.warning(reportAckSegment.dump("  ", true));
			_logger.warning(block.dump("  ", true));
			break;
		}
			
	}
	
	/**
	 * Build a ReceptionReport reporting state of Session represented by given
	 * Block and send it.
	 * @param block The affected Block
	 * @param dataSegment The DataSegment triggering the ReportSegment
	 * @throws LtpException on errors
	 * @throws InterruptedException If interrupted
	 */
	private void sendReceptionReport(
			InboundBlock block, 
			DataSegment dataSegment)
	throws LtpException, InterruptedException {

		// 6.11.  Send Reception Report
		//   ...
		//   As many RS segments must be produced as are needed in order to report
		//   on all data reception within the scope of the report, given whatever
		//   data size constraints are imposed by the underlying communication
		//   service.  The RS segments are, in concept, appended to the queue of
		//   internal operations traffic bound for the LTP engine that originated
		//   the indicated session.  The lower bound of the first RS segment of
		//   the report MUST be the reception report's lower bound.  The upper
		//   bound of the last RS segment of the report MUST be the reception
		//   report's upper bound.
		
		List<ReportSegment> reportSegments = null;
		if (!dataSegment.isResend()) {
			// 6.11.  Send Reception Report
			//   If production of the reception report was triggered by reception of a
			//   checkpoint:
			//      - The upper bound of the report SHOULD be the upper bound (the sum
			//        of the offset and length) of the checkpoint data segment, to
			//        minimize unnecessary retransmission.  Note: If a discretionary
			//        checkpoint is lost but subsequent segments are received, then by
			//        the time the retransmission of the lost checkpoint is received
			//        the receiver would have segments at block offsets beyond the
			//        upper bound of the checkpoint.  For deployments where bandwidth
			//        economy is not critical, the upper bound of a synchronous
			//        reception report MAY be the maximum upper bound value among all
			//        red-part data segments received so far in the affected session.
			// ...
		    //  - If the checkpoint was not issued in response to a report
		    //    segment, this report is a "primary" reception report.  The lower
		    //    bound of the first primary reception report issued for any
		    //    session MUST be zero.  The lower bound of each subsequent
		    //    primary reception report issued for the same session SHOULD be
		    //    the upper bound of the prior primary reception report issued for
		    //    the session, to minimize unnecessary retransmission.  Note: For
		    //    deployments where bandwidth economy is not critical, the lower
		    //    bound of every primary reception report MAY be zero.
			// NOTE: I'm taking the tack of setting the lower bound for the
			// ReportSegment to zero.
			reportSegments =
				ReportSegment.generateReceptionReport(
						dataSegment,
						block, 
						new CheckpointSerialNumber(
								dataSegment.getCheckpointSerialNumber()),
						new ReportSerialNumber(
								block.incrementReportSerialNumber()));

		} else {
		    //  - If the checkpoint was itself issued in response to a report
		    //    segment, then this report is a "secondary" reception report.  In
		    //    that case, the lower bound of the report SHOULD be the lower
		    //    bound of the report segment to which the triggering checkpoint
		    //    was itself a response, to minimize unnecessary retransmission.
		    //    Note: For deployments where bandwidth economy is not critical,
		    //    the lower bound of the report MAY instead be zero.			
			reportSegments =
				ReportSegment.generateReceptionReport(
						dataSegment,
						block, 
						new CheckpointSerialNumber(
								dataSegment.getCheckpointSerialNumber()),
						new ReportSerialNumber(
								block.incrementReportSerialNumber()));
		}

		for (ReportSegment reportSegment : reportSegments) {
			if (GeneralManagement.isDebugLogging()) {
				if (_logger.isLoggable(Level.FINER)) {
					_logger.finer("sendReportSegment(ReportSerial=" + 
							reportSegment.getReportSerialNumber() + ")");
					_logger.finer(reportSegment.getSessionID().dump("", false));
				}
			}
			block.addReportSegment(reportSegment);
			LtpManagement.getInstance()._ltpStats.nReportSegmentsSent++;
			transmitSegment(reportSegment, dataSegment.getLink(), dataSegment.getNeighbor());			
		}
	}
	
	/**
	 * The given Block has been completely received.  We need to deliver it
	 * to our upper layer client.  Specifically, we:
	 * <ul>
	 *   <li>Remove the Block from the Inbound Blocks list, and remove its
	 *   segments from the inbound Segments list.
	 *   <li>Remove all outstanding ReportSegments and kill associated timers.
	 *   <li>Deliver the Block to LtpApi
	 * </ul>
	 * @param block Given Block
	 * @throws InterruptedException 
	 */
	private void deliverCompletedBlock(InboundBlock block) 
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("deliverCompletedBlock()");
			if (_logger.isLoggable(Level.FINER)) {
				_logger.finer(block.getSessionId().dump("", true));
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.finest(block.dump("  ", true));
				}
			}
		}
		
		removeInboundBlock(block);
		
		try {
			block.inboundBlockComplete();
			LtpApi.getInstance().onBlockReceived(block);
		} catch (JDtnException e) {
			_logger.log(Level.SEVERE, "Delivering Completed Block", e);
			LtpApi.getInstance().onSystemError("Delivering Completed Block", e);
			cancelBlock(block, CancelSegment.REASON_CODE_SYSTEM_CANCELED);
		}
	}

	/**
	 * Send the given Segment on the given Link to the given Neighbor.
	 * Also arranges that we'll get a callback to onSegmentTransmitted() when
	 * the Segment is transmitted.
	 * @param segment Segment to transmit
	 * @param link Link to transmit on
	 * @param neighbor Neighbor to transmit to
	 * @throws InterruptedException if interrupted waiting for queue space
	 * @throws LtpException if Neighbor's Segment queue has been full for some time
	 */
	private void transmitSegment(Segment segment, LtpLink link, LtpNeighbor neighbor) 
	throws InterruptedException, LtpException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("transmitSegment(" + segment.getSegmentType() + ")");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(segment.dump("  ", true));
			}
		}
		segment.setLink(link);
		segment.setNeighbor(neighbor);
		segment.incrementNTransmitEnqueues();
		segment.setSegmentTransmitCallback(this);
		neighbor.transmitSegment(segment);
	}

	/**
	 * Called when given Segment is "on the wire".
	 * If the Segment is a ReportSegment, then we start its ReportSegment Timer.
	 */
	private void processSegmentTransmitStarted(LtpLink link, Segment segment) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("processSegmentTransmitStarted(" + segment.getSegmentType() + ")");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(segment.dump("  ", true));
			}
		}
		
		if (segment.isReportSegment() && (segment instanceof ReportSegment)) {
			ReportSegment reportSegment = (ReportSegment)segment;
			
			// See if there's an active session associated with this Report Segment.
			InboundBlock block = findBlockBySessionId(reportSegment.getSessionID());
			if (block == null) {
				_logger.severe("Transmitted ReportSegment, but associated Block has been closed");
				if (GeneralManagement.isDebugLogging()) {
					if (_logger.isLoggable(Level.FINER)) {
						_logger.finer(reportSegment.getReportSerialNumber().dump("", false));
						_logger.finer(reportSegment.getSessionID().dump("", false));
					}
				}
				return;
			}
			
			// Make sure that this ReportSegment is an outstanding ReportSegment
			// for the Block.
			if (block.getOutstandingReportSegment(reportSegment.getReportSerialNumber()) == null) {
				_logger.severe("Transmitted ReportSegment, but no outstanding ReportSegment is recorded with this serial number");
				if (GeneralManagement.isDebugLogging()) {
					if (_logger.isLoggable(Level.FINER)) {
						_logger.finer(reportSegment.getReportSerialNumber().dump("", false));
						_logger.finer(reportSegment.getSessionID().dump("", false));
					}
				}
				return;
			}
			
			// 6.3.  Start RS Timer
			//   This procedure is triggered by the arrival of a link state cue
			//   indicating the de-queuing (for transmission) of an RS segment.
			//   Response: the expected arrival time of the RA (report acknowledgment)
			//   segment in response to the reception of this RS segment is computed,
			//   and a countdown timer is started for this arrival time.  However, as
			//   in Section 6.2, if it is known that the remote LTP engine has ceased
			//   transmission (Section 6.5), then this timer is immediately suspended,
			//   because the computed expected arrival time may require an adjustment
			//   that cannot yet be computed.
			startReportSegmentTimer(reportSegment);
			
		} else if (segment.isCancelSegmentFromReceiver()) {
			// 6.15.  Start Cancel Timer
			//   This procedure is triggered by arrival of a link state cue indicating
			//   the de-queuing (for transmission) of a Cx segment.
			//   Response: the expected arrival time of the CAx segment that will be
			//   produced on reception of this Cx segment is computed and a countdown
			//   timer for this arrival time is started.  However, if it is known that
			//   the remote LTP engine has ceased transmission (Section 6.5), then
			//   this timer is immediately suspended, because the computed expected
			//   arrival time may require an adjustment that cannot yet be computed.
			// Segment Transmitted was a CancelSegment
			// Start the CancelTimer for the Block for timeout of Cancel Ack
			CancelSegment cancelSegment = (CancelSegment)segment;
			SessionId sessionId = cancelSegment.getSessionID();
			InboundBlock block = findBlockBySessionId(sessionId);
			if (block == null) {
				_logger.warning("CancelSegment transmitted but no matching block");
				_logger.warning(cancelSegment.dump("  ", true));
				return;
			}
			block.setOutstandingCancelSegment(cancelSegment);
			startCancelTimer(cancelSegment, block);

		}
	}

	/**
	 * Internal method to start the ReportSegment timer for the given ReportSegment
	 * @param reportSegment
	 */
	private void startReportSegmentTimer(final ReportSegment reportSegment) {
		if (reportSegment.getNeighbor().isNeighborScheduledUp()) {
			reportSegment.setRsTimerTask(
				new TimerTask() {
					@Override
					public void run() {
						ReportSegmentTimerExpiredEvent event =
							new ReportSegmentTimerExpiredEvent(reportSegment);
						try {
							processEvent(event);
						} catch (InterruptedException e) {
							// Ignore
						}
					}
				}
			);
		
		}
		_timer.schedule(
				reportSegment.getRsTimerTask(), 
				reportSegment.getLink().getReportTimeout());
	}

	/**
	 * Internal method to stop the ReportSegment timer for the given ReportSegment
	 * @param reportSegment
	 */
	private void stopReportSegmentTimer(ReportSegment reportSegment) {
		if (reportSegment.getRsTimerTask() != null) {
			reportSegment.getRsTimerTask().cancel();
			reportSegment.setRsTimerTask(null);
			_timer.purge();
		}
	}
	
	/**
	 * Called when a ReportSegmentTimer expires.  I.e., we have sent a
	 * ReportSegment and we have timed-out awaiting a ReportAckSegment.
	 * This is the <RX> procedure of rfc5326 8.2 Receiver State Machine.
	 * @param reportSegment ReportSegment whose RS timer expired
	 * @throws InterruptedException 
	 */
	private void processReportSegmentTimerExpired(ReportSegment reportSegment) 
	throws InterruptedException {
		_logger.warning("Report Segment Timer expired; timeout waiting for Report Ack");
		if (GeneralManagement.isDebugLogging()) {
			if (_logger.isLoggable(Level.FINER)) {
				_logger.finer(reportSegment.getReportSerialNumber().dump("", true));
				_logger.finer(reportSegment.getSessionID().dump("", true));
			}
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(reportSegment.dump("  ", true));
			}
		}
		LtpManagement.getInstance()._ltpStats.nReportExpirations++;
		
		// Find affected Block
		InboundBlock block = findBlockBySessionId(reportSegment.getSessionID());
		if (block == null) {
			_logger.warning("No active block found for expired ReportSegment");
			return;
		}
	
		// Make sure Block in a State that makes sense for RS Timer expire.
		switch (block.getLtpReceiverState()) {
		case DS_REC:
			break;
		case RCV_RP:
			break;
		case RCV_GP:
			break;
		case WAIT_RP_REC:
			break;
			
		case CR_SENT:
			// We have sent a Cancel request and are awaiting a Cancel Ack.
			// A ReportSegment timer expiration can happen here, but it is
			// not germane.  Just ignore it.
			_logger.warning("Block is in CR_SENT so ignoring RS Timer expiration");
			return;
			
		case CLOSED:
			// Block may have been completely cancelled and we didn't mop up
			// timers.
			_logger.warning("Block is in CLOSED so ignoring RS Timer expiration");
			_logger.warning(reportSegment.dump("  ", true));
			_logger.warning(block.dump("  ", true));
			return;
		}
		
		// Make sure that the ReportSegment has not been acked.  If it was
		// acked, it would have been removed from the Block.
		ReportSegment otherReport = 
			block.getOutstandingReportSegment(
					reportSegment.getReportSerialNumber());
		if (otherReport == null) {
			// This could be a stale timer expiration; since report was acked				
			_logger.warning("Block has no outstanding ReportSegment for timer expiration");
			_logger.warning(reportSegment.dump("  ", true));
			_logger.warning(block.dump("  ", true));
			return;
		}
		
		// 6.8.  Retransmit RS
		// This procedure is triggered by either (a) the expiration of a
		//   countdown timer associated with an RS segment or (b) the reception of
		//   a CP segment for which one or more RS segments were previously issued
		//   -- a redundantly retransmitted checkpoint.
		//   Response: if the number of times any affected RS segment has been
		//   queued for transmission exceeds the report retransmission limit
		//   established for the local LTP engine by network management, then the
		//   session of which the segment is one token is canceled: the "Cancel
		//   Session" procedure (Section 6.19) is invoked, a CR segment with
		//   reason-code RLEXC is queued for transmission to the LTP engine that
		//   originated the session, and a reception-session cancellation notice
		//   (Section 7.6) is sent to the client service identified in each of the
		//   data segments received in this session.
		if (reportSegment.getNTransmitEnqueues() > 
			LtpManagement.getInstance().getLtpMaxReportRetransmits()) {
			
			// Too many retransmits of ReportSegment.
			_logger.warning("Report retransmit limit reached");
			if (GeneralManagement.isDebugLogging()) {
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.finest(reportSegment.dump("  ", true));
					_logger.finest(block.dump("  ", true));
				}
			}
			
			// Send a Cancel Request
			cancelBlock(block, CancelSegment.REASON_CODE_RETRANS_LIMIT_EXCEEDED);
			
		} else {
			//   Otherwise, a new copy of each affected RS segment is queued for
			//   transmission to the LTP engine that originated the session.
			try {
				if (GeneralManagement.isDebugLogging()) {
					_logger.fine("onReportSegmentTimerExpired: Resending Report Segment");
					if (_logger.isLoggable(Level.FINER)) {
						_logger.finer(reportSegment.dump("  ", true));
						_logger.finer(block.dump("  ", false));
					}
				}
				LtpManagement.getInstance()._ltpStats.nReportSegmentsSent++;
				transmitSegment(reportSegment, reportSegment.getLink(), 
						reportSegment.getNeighbor());
				
			} catch (LtpException e) {
				_logger.log(Level.SEVERE, "Retransmitting ReportSegment " + 
						reportSegment, e);
				
			} catch (InterruptedException e) {
				_logger.log(Level.SEVERE, "Retransmitting ReportSegment " + 
						reportSegment, e);
				// Make sure Timer Task gets cancelled
				stopReportSegmentTimer(reportSegment);
			}
		}
	}

	/**
	 * Start the CancelTimer for the given CancelSegment, cancelling the given Block
	 * @param cancelSegment Given Cancel Segment
	 * @param block Given Block
	 */
	private void startCancelTimer(
			final CancelSegment cancelSegment,
			final InboundBlock block) {
		
		if (block.getNeighbor().isNeighborScheduledUp()) {
			block.setCancelTimerTask(
				new TimerTask() {				
					@Override
					public void run() {
						CancelTimerExpiredEvent event =
							new CancelTimerExpiredEvent(cancelSegment, block);
						try {
							processEvent(event);
						} catch (InterruptedException e) {
							// Ignore
						}
					}
				}
			);
			_timer.schedule(
					block.getCancelTimerTask(), 
					cancelSegment.getLink().getCancelTimeout());
		}
	}

	/**
	 * Stop the CancelTimer for the given CancelSegment, cancelling the given Block
	 * @param cancelSegment Given Cancel Segment
	 * @param block Given Block
	 */
	private void stopCancelTimer(
			CancelSegment cancelSegment,
			InboundBlock block) {
		if (cancelSegment != null && block.getCancelTimerTask() != null) {
			block.getCancelTimerTask().cancel();
			_timer.purge();
			block.setCancelTimerTask(null);
		}
	}
	
	/**
	 * Called when the CancelTimer expires.  I.e., we have sent a CancelSegment
	 * cancelling an Inbound block and we were awaiting a Cancel Ack, but
	 * the CancelTimer expired without receiving a Cancel Ack.
	 * @param segment The CancelSegment sent to cancel a Block
	 * @param block The Block being cancelled
	 */
	private void processCancelTimerExpired(CancelSegment segment, InboundBlock block) {
		LtpManagement.getInstance()._ltpStats.nCancelExpirations++;
		if (block.getLtpReceiverState() == LtpReceiverState.CR_SENT) {
			// 6.16.  Retransmit Cancellation Segment
			//   This procedure is triggered by the expiration of a countdown timer
			//   associated with a Cx segment.
			//   Response: if the number of times this Cx segment has been queued for
			//   transmission exceeds the cancellation retransmission limit
			//   established for the local LTP engine by network management, then the
			//   session of which the segment is one token is simply closed: the
			//   "Close Session" procedure (Section 6.20) is invoked.
			//   Otherwise, a copy of the cancellation segment (retaining the same
			//   reason-code) is queued for transmission to the appropriate LTP
			//   engine.
			if (segment.getNTransmitEnqueues() > LtpManagement.getInstance().getLtpMaxCancelRetransmits()) {
				// Too many retransmits of CancelSegment.  Give up
				_logger.warning("Cancel Segment exceeded resend limit");
				_logger.warning(segment.dump("  ", true));
				removeInboundBlock(block);
				byte reason = CancelSegment.REASON_CODE_SYSTEM_CANCELED;
				if (block.getOutstandingCancelSegment() != null) {
					reason = block.getOutstandingCancelSegment().getReasonCode();
				}
				LtpApi.getInstance().onBlockReceiveCancelled(block, reason);
				
			} else {
				// Resend CancelSegment
				// Restart Cancel Timer
				try {
					LtpManagement.getInstance()._ltpStats.nCancelsSent++;
					transmitSegment(segment, block.getLink(), block.getNeighbor());
					startCancelTimer(segment, block);
					
				} catch (LtpException e) {
					_logger.log(Level.SEVERE, "retransmit cancel segment " + segment, e);
				} catch (InterruptedException e) {
					_logger.log(Level.SEVERE, "retransmit cancel segment " + segment, e);
					// Make sure TimerTask gets cancelled
					stopCancelTimer(block.getOutstandingCancelSegment(), block);
				}
			}
		} else {
			_logger.warning("CancelTimer expiration when block not in CR_SENT State");
			_logger.warning(segment.dump("  ", true));
		}
	}

	/**
	 * Called when a higher level component has changed "Scheduled" state of the given
	 * Neighbor, to either inhibit transmission or to enable transmission.  This
	 * is in response to a scheduled change in Neighbor State, a planned event.
	 * @param neighbor The affected Neighbor
	 * @param neighborScheduledUp Whether the Neighbor has been scheduled up (true) or down.
	 */
	private void processNeighborScheduledStateChange(LtpNeighbor neighbor,
			boolean neighborScheduledUp) {
		
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onNeighborScheduledStateChange()");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(neighbor.dump("  ", true));
			}
		}
		
		for (InboundBlock block : _inboundBlockList) {
			if (block.getNeighbor().equals(neighbor)) {
				for (ReportSegment reportSegment : block.iterateOutstandingReportSegments()) {
					if (neighborScheduledUp) {
						// 6.6.  Resume Timers
						//   This procedure is triggered by the arrival of a link state cue
						//   indicating the start of transmission from a specified remote LTP
						//   engine to the local LTP engine.  Normally, this event is inferred
						//   from advance knowledge of the remote engine's planned transmission
						//   schedule.
						//   Response: expected arrival time is adjusted for every acknowledging
						//   segment that the remote engine is expected to return, for which the
						//   countdown timer has been suspended.
						startReportSegmentTimer(reportSegment);
						
					} else {
						// 6.5.  Suspend Timers
						//   This procedure is triggered by the arrival of a link state cue
						//   indicating the cessation of transmission from a specified remote LTP
						//   engine to the local LTP engine.  Normally, this event is inferred
						//   from advance knowledge of the remote engine's planned transmission
						//   schedule.
						//   Response: countdown timers for the acknowledging segments that the
						//   remote engine is expected to return are suspended as necessary based
						//   on the following procedure.
						stopReportSegmentTimer(reportSegment);
					}
				}
			}
		}
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
		StringBuffer sb = new StringBuffer(indent + "LtpInbound\n");
		for (Block block : _inboundBlockList) {
			sb.append(block.dump(indent + "  ", detailed));
		}
		return sb.toString();
	}
	
	/**
	 * Called when a new Link is added.  We register as Listener on new Link.
	 */
	private void onLinkAdded(Link link) {
		link.addLinkListener(this);
	}

	/**
	 * Called when a new Link is removed.  We deregister as Listener on Link.
	 */
	private void onLinkRemoved(Link link) {
		link.removeLinkListener(this);
	}
	
	@Override
	public void onLinkOperationalStateChange(Link link, boolean linkOperational) {
		// Nothing
	}

	@Override
	public void onNeighborOperationalChange(Neighbor neighbor, boolean neighborUp) {
		// Nothing
	}

	@Override
	public void onNeighborAdded(Link link, Neighbor neighbor) {
		// Nothing
	}

	@Override
	public void onNeighborDeleted(Link link, Neighbor neighbor) {
		// Nothing
	}

}
