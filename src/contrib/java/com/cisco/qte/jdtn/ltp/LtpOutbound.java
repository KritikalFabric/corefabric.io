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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.component.AbstractEventProcessorThread;
import com.cisco.qte.jdtn.component.EventBroadcaster;
import com.cisco.qte.jdtn.component.IEvent;
import com.cisco.qte.jdtn.component.StopEvent;
import com.cisco.qte.jdtn.events.BlockCancelEvent;
import com.cisco.qte.jdtn.events.BlockEvent;
import com.cisco.qte.jdtn.events.CancelAckSegmentEvent;
import com.cisco.qte.jdtn.events.CancelSegmentEvent;
import com.cisco.qte.jdtn.events.CancelTimerExpiredEvent;
import com.cisco.qte.jdtn.events.CheckpointTimerExpiredEvent;
import com.cisco.qte.jdtn.events.JDTNEvent;
import com.cisco.qte.jdtn.events.LinksEvent;
import com.cisco.qte.jdtn.events.NeighborScheduledStateChangeEvent;
import com.cisco.qte.jdtn.events.OutboundBlockEvent;
import com.cisco.qte.jdtn.events.ReportSegmentEvent;
import com.cisco.qte.jdtn.events.SegmentEvent;
import com.cisco.qte.jdtn.events.SegmentTransmitStartedEvent;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.Link;
import com.cisco.qte.jdtn.general.LinkListener;
import com.cisco.qte.jdtn.general.LinksList;
import com.cisco.qte.jdtn.general.Neighbor;
import com.cisco.qte.jdtn.ltp.OutboundBlock.LtpSenderState;
import com.cisco.qte.jdtn.ltp.Segment.SegmentType;
import org.kritikal.fabric.contrib.jdtn.BlobAndBundleDatabase;

/**
 * Outbound processing portion of LTP Engine.  Maintains a List of outbound
 * Blocks.  Processes events on these Blocks as they proceed through the
 * Ltp Sender process.  
 * <p>
 * Note: We do not implement recommendations for Replay Handling from Section
 * 9.2 of RFC5326.
 */
public class LtpOutbound extends AbstractEventProcessorThread
implements LinkListener, SegmentTransmitCallback {

	public static final int LTP_OUTBOUND_EVENT_QUEUE_CAPACITY = 1000;
	public static final long LTP_OUTBOUND_JOIN_DELAY_MSECS = 2000L;
	
	@SuppressWarnings("hiding")
	private static final Logger _logger =
		Logger.getLogger(LtpOutbound.class.getCanonicalName());
	
	// Singleton instance
	private static LtpOutbound _instance = null;
	
	// Block List; list of Blocks in process of being transmitted.
	private ArrayList<OutboundBlock> _outboundBlockList =
		new ArrayList<OutboundBlock>();
	// Map from SessionId to Outbound Block
	private HashMap<SessionId, OutboundBlock> _mapSessionIdToBlock =
		new HashMap<SessionId, OutboundBlock>();
	// Amount of outbound data we're retaining, bytes
	private long _outboundBytes = 0;
	// java.util.Timer for all timers
	private Timer _timer = null;
	
	/**
	 * Get singleton instance
	 * @return Singleton instance
	 */
	public static LtpOutbound getInstance() {
		if (_instance == null) {
			_instance = new LtpOutbound();
		}
		return _instance;
	}
	
	private LtpOutbound() {
		super("LtpOutbound", LTP_OUTBOUND_EVENT_QUEUE_CAPACITY);
	}
	
	/**
	 * Startup.  Adds itself as a Listener for all
	 * current Links; also adds itself as a Listener on LinksList.
	 * Starts up event processing thread.
	 */
	@Override
	protected void startImpl() {
		_timer = new Timer();
		for (Link link : LinksList.getInstance()) {
			if (link instanceof LtpLink) {
				link.addLinkListener(this);
			}
		}
		
		EventBroadcaster.getInstance().registerEventProcessor(
				LinksList.class.getCanonicalName(), 
				this);
		super.startImpl();
	}
	
	/**
	 * Shutdown LtpOutbound.
	 * Shuts down event processing Threa.d
	 * Removes itself as a Listener for all Links
	 * and for LinksList.
	 * @throws InterruptedException 
	 */
	private void shutdown() throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("shutdown()");
		}
			
		// Clear timers
		_timer.purge();
		_timer.cancel();
		_timer = null;
		
		// Unlisten to Links
		EventBroadcaster.getInstance().unregisterEventProcessor(
				LinksList.class.getCanonicalName(), 
				this);
		for (Link link : LinksList.getInstance()) {
			if (link instanceof LtpLink) {
				link.removeLinkListener(this);
			}
		}

		java.sql.Connection con = BlobAndBundleDatabase.getInstance().getInterface().createConnection();
		try {
			// Clear Outbound Block list
			while (!_outboundBlockList.isEmpty()) {
				OutboundBlock block = _outboundBlockList.get(0);
				removeOutboundBlock(con, block);
			}
		}
		finally {
			try { con.close(); } catch (SQLException e) { _logger.warning(e.getMessage()); }
		}
	}
	
	/**
	 * Enqueue the given Block for transmit.  Called from high layers via
	 * LtpAPI.  This may block long enough for
	 * space to appear on the LtpOutbound event queue.  Once enqueued, the
	 * Block will be transmitted once the Link gets around to it.
	 * @param block Block to be transmitted
	 * @throws InterruptedException if interrupted while trying to enqueue job
	 * on Job Queue.
	 * @throws LtpException on queue full on attempt to transmit Block.
	 */
	public void enqueueOutboundBlock(OutboundBlock block)
	throws InterruptedException {
		OutboundBlockEvent event = new OutboundBlockEvent(block);
		processEvent(event);
	}
	
	/**
	 * Client request to Cancel a Block previously enqueued for transmission.
	 * May block.
	 * @param block Block to be cancelled
	 * @param reason why block is being cancelled; see CancelSegment.REASON...
	 * @throws InterruptedException if interrupted while blocked
	 */
	public void cancelBlock(OutboundBlock block, byte reason) 
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("cancelBlock");
		}
		BlockCancelEvent event = new BlockCancelEvent(block, reason);
		processEvent(event);
	}
	
	/**
	 * Handle a newly arrived ReportSegment.  Called from LtpInbound.
	 * @param reportSegment The newly arrived ReportSegment
	 * @throws InterruptedException if interrupted while blocked.
	 */
	public void onReportSegment(ReportSegment reportSegment) 
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onReportSegment");
		}
		
		ReportSegmentEvent event =
			new ReportSegmentEvent(reportSegment);
		processEvent(event);
	}
	
	/**
	 * Handle newly arrived CancelSegment.  Called from LtpInbound.
	 * This is a request from the receiver
	 * of the Block to cancel transmission of the Block.
	 * @param cancelSegment Cancel Segment
	 * @throws InterruptedException if interrupted
	 */
	public void onCancelSegment(CancelSegment cancelSegment) 
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onCancelSegment()");
		}
		
		CancelSegmentEvent event =
			new CancelSegmentEvent(cancelSegment);
		processEvent(event);
	}
	
	/**
	 * Handle a received CancelAckSegment.  Called from LtpInbound.
	 * @param cancelAckSegment Given CancelAckSegment
	 * @throws InterruptedException if interrupted
	 */
	public void onCancelAckSegment(CancelAckSegment cancelAckSegment)
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onCancelAckSegment");
		}
		CancelAckSegmentEvent event =
			new CancelAckSegmentEvent(cancelAckSegment);
		processEvent(event);
	}

	/**
	 * Called when a higher level component has changed "Scheduled" state of the given
	 * Neighbor, to either inhibit transmission or to enable transmission.  This
	 * is in response to a scheduled change in Neighbor State, a planned event.
	 * @param neighbor The affected Neighbor
	 * @param neighborUp Whether the Neighbor has been scheduled up (true) or down.
	 * @throws InterruptedException 
	 */
	@Override
	public void onNeighborScheduledStateChange(Neighbor neighbor, boolean neighborUp)
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onNeighborScheduledStateChange()");
		}
		if (neighbor instanceof LtpNeighbor) {
			NeighborScheduledStateChangeEvent event =
				new NeighborScheduledStateChangeEvent(
						(LtpNeighbor)neighbor, neighborUp);
			processEvent(event);
		}
	}
	
	/**
	 * Called when a Segment is 'on the wire'; i.e., it's transmission has
	 * started.
	 * @param link Link on which Segment was transmitted
	 * @param segment Segment transmitted
	 * @throws InterruptedException if interrupted while waiting for queue space
	 */
	@Override
	public void onSegmentTransmitStarted(LtpLink link, Segment segment) 
	throws InterruptedException {
		
		if (segment.isData() || segment.isCancelSegmentFromSender()) {
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("onSegmentTransmitStarted()");
			}
			SegmentTransmitStartedEvent event =
				new SegmentTransmitStartedEvent(segment, link);
			processEvent(event);
		}
	}
	
	/**
	 * Event Processing Handler; Receive events and dispatches.
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
				throw new IllegalArgumentException("Event not instanceof JDTNEvent");
			}
			JDTNEvent event = (JDTNEvent)iEvent;
			java.sql.Connection con = BlobAndBundleDatabase.getInstance().getInterface().createConnection();
			try {
				switch (event.getEventType()) {
					case LINKS_EVENT:
						LinksEvent lEvent = (LinksEvent) event;
						switch (lEvent.getLinksEventSubtype()) {
							case LINK_ADDED_EVENT:
								onLinkAdded(lEvent.getLink());
								break;
							case LINK_REMOVED_EVENT:
								onLinkRemoved(lEvent.getLink());
								break;
							default:
								_logger.severe("Unknown LINKS_EVENT: " +
										lEvent.getLinksEventSubtype());
								break;
						}
						break;

					case OUTBOUND_BLOCK:
						BlockEvent blockEvent = (BlockEvent) event;
						processOutboundBlock((OutboundBlock) blockEvent.getBlock());
						break;

					case BLOCK_CANCEL:
						BlockCancelEvent bce = (BlockCancelEvent) event;
						processCancelBlock((OutboundBlock) bce.getBlock(), bce.getReason());
						break;

					case REPORT_SEGMENT:
						SegmentEvent segEvent = (SegmentEvent) event;
						processReportSegment(con, (ReportSegment) segEvent.getSegment());
						break;

					case CANCEL_SEGMENT:
						segEvent = (SegmentEvent) event;
						processCancelSegment(con, (CancelSegment) segEvent.getSegment());
						break;

					case CANCEL_ACK_SEGMENT:
						segEvent = (SegmentEvent) event;
						processCancelAckSegment(con, (CancelAckSegment) segEvent.getSegment());
						break;

					case NEIGHBOR_SCHEDULED_STATE_CHANGE:
						NeighborScheduledStateChangeEvent nssce =
								(NeighborScheduledStateChangeEvent) event;
						processNeighborScheduledStateChange(nssce.getNeighbor(), nssce.isUp());
						break;

					case SEGMENT_TRANSMIT_STARTED:
						SegmentTransmitStartedEvent stse =
								(SegmentTransmitStartedEvent) event;
						processSegmentTransmitStarted(con, stse.getLink(), stse.getSegment());
						break;

					case CHECKPOINT_TIMER_EXPIRED:
						CheckpointTimerExpiredEvent ctee =
								(CheckpointTimerExpiredEvent) event;
						onCheckpointTimerExpired((DataSegment) ctee.getSegment());
						break;

					case CANCEL_TIMER_EXPIRED:
						CancelTimerExpiredEvent catee =
								(CancelTimerExpiredEvent) event;
						onCancelTimerExpired(
								con,
								(CancelSegment) catee.getSegment(),
								(OutboundBlock) catee.getBlock());
						break;

					default:
						_logger.severe("Unknown event type: " + event.getEventType());
						break;
				}

				try { con.commit(); } catch (SQLException e) { _logger.warning(e.getMessage()); }
			}
			finally {
				try { con.close(); } catch (SQLException e) { _logger.warning(e.getMessage()); }
			}
			
		} catch (LtpException e) {
			_logger.log(Level.SEVERE, "LtpOutbound", e);
			LtpApi.getInstance().onSystemError("LtpOutbound", e);
			
		} catch (InterruptedException e) {
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("LtpOutbound interrupted");
			}
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("LtpOutbound shutting down");
		}
	}
	
	/**
	 * Process the given Block for transmit.  Called from higher layers via
	 * LtpAPi.  This may block long enough for
	 * space to appear on the destination Neighbor's Segment queue.  Once enqueued, the
	 * Block will be transmitted once the Link gets around to it.
	 * @param block Block to be transmitted
	 * @throws InterruptedException if interrupted while trying to enqueue job
	 * on Job Queue.
	 * @throws LtpException on queue full on attempt to transmit Block.
	 */
	private void processOutboundBlock(OutboundBlock block)
	throws InterruptedException, LtpException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("New Outbound Session ");
			if (_logger.isLoggable(Level.FINER)) {
				_logger.finer(block.getSessionId().dump("", true));
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.finest(block.dump("  ", true));
				}
			}
		}
		LtpManagement.getInstance()._ltpStats.nBlocksSent++;
		
		// Notify client that the session has started
 		LtpApi.getInstance().onSessionStarted(block);
		
		// Add Block to outbound list and to map
		_outboundBlockList.add(block);
		_mapSessionIdToBlock.put(block.getSessionId(), block);
		_outboundBytes += block.getDataLength();
		
		if (block.getRedDataLength() > 0) {
			// Block of all-red or mixed Color; transmit Red Segments
			block.setLtpSenderState(LtpSenderState.RP_XMIT);
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Session => " + block.getLtpSenderState());
			}
			for (DataSegment dataSegment : block) {
				if (dataSegment.isRedData()) {
					LtpManagement.getInstance()._ltpStats.nDataSegmentsSent++;
					try {
						transmitSegment(dataSegment, block.getLink(), block.getNeighbor());
					} catch (LtpException e) {
						LtpApi.getInstance().onSystemError("Sending Red DataSegment", e);
						cancelBlock(block, CancelSegment.REASON_CODE_SYSTEM_CANCELED);
						throw(e);
					}
				} else {
					break;
				}
			}
		} else {
			// Block of all-green segments; transmit them all
			block.setLtpSenderState(LtpSenderState.FG_XMIT);
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Session => " + block.getLtpSenderState());
			}
			for (DataSegment dataSegment : block) {
				LtpManagement.getInstance()._ltpStats.nDataSegmentsSent++;
				try {
					transmitSegment(dataSegment, block.getLink(), block.getNeighbor());
				} catch (LtpException e) {
					LtpApi.getInstance().onSystemError("Sending Green DataSegment", e);
					cancelBlock(block, CancelSegment.REASON_CODE_SYSTEM_CANCELED);
					throw(e);
				}
			}
		}
		// Next piece of the puzzle occurs in onSegmentTransmitStarted().
	}
	
	/**
	 * Remove given Block from outbound List; close it out; mark it CLOSED
	 * @param block Given Block
	 */
	private void removeOutboundBlock(java.sql.Connection con, OutboundBlock block) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("removeOutboundBlock()");
			if (_logger.isLoggable(Level.FINER)) {
				_logger.finer(block.getSessionId().dump("", true));
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.finest(block.dump("  ", true));
				}
			}
		}
		block.setLtpSenderState(LtpSenderState.CLOSED);
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("Session => " + block.getLtpSenderState());
		}
		_mapSessionIdToBlock.remove(block.getSessionId());
		_outboundBlockList.remove(block);
		block.closeBlock(con);
		_outboundBytes -= block.getDataLength();
		try { con.commit(); } catch (SQLException e) { _logger.warning(e.getMessage()); }
	}
	
	/**
	 * Internal method to find a block in our Job queue with the given SessionId
	 * @param sessionId Given SessionId
	 * @return Matching Block or null if none
	 */
	private OutboundBlock findBlockBySessionId(SessionId sessionId) {
			return _mapSessionIdToBlock.get(sessionId);
	}
	
	/**
	 * Process Upper layer request to Cancel a Block previously enqueued for
	 * transmission.  May block.
	 * @param block Block to be cancelled
	 * @param reason why block is being cancelled; see CancelSegment.REASON...
	 * @throws InterruptedException if interrupted while blocked
	 */
	private void processCancelBlock(OutboundBlock block, byte reason) 
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("processCancelBlock()");
			if (_logger.isLoggable(Level.FINER)) {
				_logger.finer(block.getSessionId().dump("", true));
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.finest(block.dump("  ", true));
				}
			}
		}
		
		// Make sure block transmission is in progress
		if (findBlockBySessionId(block.getSessionId()) == null) {
			_logger.warning("Block to be cancelled is not in outbound queue");
			return;
		}
		
		// Stop all CheckpointTimers for the Block
		for (DataSegment dataSegment : block) {
			stopCheckpointTimer(dataSegment);
		}
		
		// Build and send CancelSegment; set block state to CancelSent.
		// Then we'll await the Cancel Ack or Cancel Timeout for further action.
		CancelSegment cancelSegment =
			new CancelSegment(
					SegmentType.CANCEL_SEGMENT_FROM_SENDER,
					reason, 
					block.getSessionId());
		block.setLtpSenderState(LtpSenderState.CS_SENT);
		block.setOutstandingCancelSegment(cancelSegment);
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("Session => " + block.getLtpSenderState());
		}
		try {
			LtpManagement.getInstance()._ltpStats.nCancelsSent++;
			transmitSegment(cancelSegment, block.getLink(), block.getNeighbor());
		} catch (LtpException e) {
			LtpApi.getInstance().onSystemError("Sending CancelSegment", e);
			
			// Go ahead and start the cancel timer now.  It will fire later
			// and we will attempt to resend the Cancel Segment then.
			startCancelTimer(cancelSegment, block);
			return;
		}
	}
	
	/**
	 * Handle a newly arrived ReportSegment.  Called from LtpInbound.
	 * First, we unconditionally send a ReportAckSegment back.  
	 * We locate the Block mentioned by
	 * the SessionId in the ReportSegment.  We go through the claims in the
	 * ReportSegment and match them against outstanding Segments in the Block.
	 * For each such match, we flag the Segment as acked.  Finally, if all
	 * Segments are acked, then the block transmit is complete.  We remove
	 * and cancel all outstanding timers on the Segments of the Block.
	 * We remove the Block from the Outbound Block list and notify the LtpApi
	 * that the Block has been transmitted.  This can block.  Any DataSegments
	 * in the block which remain unacked are re-sent.
	 * @param reportSegment The newly arrived ReportSegment
	 * @throws InterruptedException if interrupted while blocked.
	 */
	private void processReportSegment(java.sql.Connection con, ReportSegment reportSegment)
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("processReportSegment()");
			if (_logger.isLoggable(Level.FINER)) {
				_logger.finer(reportSegment.getReportSerialNumber().dump("", true));
				if (_logger.isLoggable(Level.FINER)) {
					_logger.finer(reportSegment.dump("  ", true));
				}
			}
		}
		
		// 6.13.  Retransmit Data
		//   This procedure is triggered by the reception of an RS segment.
		//   Response: first, an RA segment with the same report serial number as
		//   the RS segment is issued and is, in concept, appended to the queue of
		//   internal operations traffic bound for the receiver.  If the RS
		//   segment is redundant -- i.e., either the indicated session is unknown
		//   (for example, the RS segment is received after the session has been
		//   completed or canceled) or the RS segment's report serial number
		//   matches that of an RS segment that has already been received and
		//   processed -- then no further action is taken.  Otherwise, the
		//   procedure below is followed.
		// Make sure the report has claims; if not, we're done
		if (reportSegment.getReceptionClaimCount() < 1) {
			_logger.warning("Report Segment has no claims");
			_logger.warning(reportSegment.dump("  ", true));
			return;
		}
		
		// Send a Report Ack; I'm going to go ahead and send ReportAck even
		// if we don't find a matching session.  It seems safer that way
		ReportAckSegment reportAckSegment =
			new ReportAckSegment(
					SegmentType.REPORT_ACK_SEGMENT,
					reportSegment.getReportSerialNumber(),
					reportSegment.getSessionID());
		try {
			LtpManagement.getInstance()._ltpStats.nReportAcksSent++;
			transmitSegment(reportAckSegment, reportSegment.getLink(), 
					reportSegment.getNeighbor());
		} catch (LtpException e) {
			_logger.log(Level.SEVERE, "Sending ReportAckSegment", e);
			LtpApi.getInstance().onSystemError("Sending ReportAckSegment", e);
			// Couldn't send ReportAck at this time.  Other side will timeout
			// and resend the Report.  Go ahead and process this ReportSegment,
			// this seems to be better than returning and waiting for the timeout.
		}
		
		// Find outbound block mentioned by ReportSegment; if we don't find it,
		// we're done
		OutboundBlock block = findBlockBySessionId(reportSegment.getSessionID());
		if (block == null) {
			// A scenario in which this is possible:
			// Node A              Node B
			// -->      CP+EOB     -->
			// <--      Report --< 
			// -->      Report ACK /// Gets lost
			// Removes Block
			// From OutboundList
			//                Report Timer Expires
			//      --< Report --< Resends Report
			// Finds no block for Report
			_logger.warning("No matching Block for ReportSegment; probably lost ReportAck; resending ReportAck");
			if (GeneralManagement.isDebugLogging()) {
				if (_logger.isLoggable(Level.FINER)) {
					_logger.finer(reportSegment.dump("", true));
				}
			}
			return;
		}
		
		// Make sure we're in appropriate state; i.e., either transmitting
		// segments of all-red or mixed block, or awaiting acks on red segments.
		LtpSenderState state = block.getLtpSenderState();
		if (state != LtpSenderState.RP_XMIT && 
			state != LtpSenderState.GP_XMIT &&
			state != LtpSenderState.WAIT_RP_ACK) {
			_logger.warning("Got ReportSegment on Block in state " + 
					block.getLtpSenderState());
			if (GeneralManagement.isDebugLogging()) {
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.finest(reportSegment.dump("  ", true));
					_logger.finest(block.dump("  ", true));
				}
			}
			return;
		}
		
		// This is the <RX> procedure from RFC5326 "8.1 Sender"
		// Go thru each claim in the report and find matching DataSegment,
		// Mark each such DataSegment Acked.
		// Performance is O(nSegmentsInBlock * nClaimsInReportSegment)
		// If this turns out to be a performance problem, we can improve
		// average performance but not worst case performance by reversing
		// the order of the loops.  However, I prefer keeping these decisions
		// inside ReportSegment.
		for (DataSegment dataSegment : block) {
			if (reportSegment.covers(dataSegment)) {
				dataSegment.setAcked(true);
				stopCheckpointTimer(dataSegment);
			}
		}
		
		// Figure out what Red segments need to be re-sent based on what Receiver
		// claims to have received.  We will only re-send within the scope of
		// the ReportSegment.
		// This is the RP_RXMT procedure from "8.1 Sender"
		// We don't treat RP_RXMT as a state.  It's really not
		// according to "8.1 Sender"; it's more like a subroutine.
		// Retransmit all un-acked ("missed") Red Segments
		// First, construct a list of all missed Red Segments
		ArrayList<DataSegment> missingSegments = new ArrayList<DataSegment>();
		for (DataSegment dataSegment : block) {
			if (reportSegment.isResendRequired(dataSegment)) {
				missingSegments.add(dataSegment);
			}
		}
		// Now go thru and re-transmit missing segments
		boolean hadToResend = !missingSegments.isEmpty();
		while (!missingSegments.isEmpty()) {
			DataSegment dataSegment = missingSegments.remove(0);
			if (missingSegments.isEmpty()) {
				// Last missing segment
				if (dataSegment.getSegmentType() == 
					SegmentType.RED_NOTCP_NOT_EORP_NOT_EOB) {
					// Last Missing Segment is not a CP.  Make it a CP.
					dataSegment.setSegmentType(
							SegmentType.RED_CP_NOT_EORP_NOT_EOB);
					block.setNextCheckpointSerialNumber(dataSegment);
				}
			}
			if (dataSegment.isCheckpoint()) {
				// Make sure it has non-zero ReportSerialNumber so other
				// side knows its a re-send.
				dataSegment.setReportSerialNumber(
						reportSegment.getReportSerialNumber());
				startCheckpointTimer(dataSegment);
			}
			if (GeneralManagement.isDebugLogging()) {
				if (_logger.isLoggable(Level.FINER)) {
					_logger.finer("Resending DataSegment(SegmentType="+
							dataSegment.getSegmentType() + ")");
					_logger.finer("Resent SessionId=" +
							dataSegment.getSessionID() + ")");
					_logger.finer("Resent ReportSerialNumber=" +
							dataSegment.getReportSerialNumber() + ")");
				}
			}
			try {
				LtpManagement.getInstance()._ltpStats.nDataSegmentsSent++;
				LtpManagement.getInstance()._ltpStats.nDataSegmentResends++;
				transmitSegment(dataSegment, block.getLink(), 
						block.getNeighbor());
			} catch (LtpException e) {
				_logger.log(Level.SEVERE, "Re-sending DataSegment", e);
				LtpApi.getInstance().onSystemError("Re-sending DataSegment", e);
				cancelBlock(block, CancelSegment.REASON_CODE_SYSTEM_CANCELED);
			}
		}					
		
		// Figure out where to go now.
		switch (block.getLtpSenderState()) {
		case WAIT_RP_ACK:
			if (block.isOutboundBlockComplete() && !hadToResend) {
				if (GeneralManagement.isDebugLogging()) {
					_logger.fine("Outbound Session Complete");
					if (_logger.isLoggable(Level.FINER)) {
						_logger.finer(block.getSessionId().dump("", true));
					}
				}
				// 6.12.  Signify Transmission Completion
				//   This procedure is triggered at the earliest time at which (a) all
				//   data in the block are known to have been transmitted *and* (b) the
				//   entire red-part of the block -- if of non-zero length -- is known to
				//   have been successfully received.  Condition (a) is signaled by
				//   arrival of a link state cue indicating the de-queuing (for
				//   transmission) of the EOB segment for the block.  Condition (b) is
				//   signaled by reception of an RS segment whose reception claims, taken
				//   together with the reception claims of all other RS segments
				//   previously received in the course of this session, indicate complete
				//   reception of the red-part of the block.
				// Response: a transmission-session completion notice (Section 7.4) is
				//   sent to the local client service associated with the session, and the
				//   session is closed: the "Close Session" procedure (Section 6.20) is
				//   invoked.
				// Block fully received; it is done; deliver it
				// Remove Block from Outbound Block list
				removeOutboundBlock(con, block);
				// Notify LtpApi that Block was sent
				LtpApi.getInstance().onBlockSent(block);
			}
			break;
			
		default:
			// No further action required
		}
	}
	
	/**
	 * Handle newly arrived CancelSegment.  Called from LtpInbound.
	 * This is a request from the receiver
	 * of the Block to cancel transmission of the Block.  We find the Block
	 * mentioned by the SessionId in the CancelSegment.  We then undertake the
	 * steps necessary to cancel the Block, including:
	 * <ul>
	 *   <li> Notify LtpApi that Block has been canceled
	 *   <li> Send Cancel Ack to originator of CancelSegment
	 * </ul>
	 * @param cancelSegment Cancel Segment
	 * @throws InterruptedException if interrupted
	 */
	private void processCancelSegment(java.sql.Connection con, CancelSegment cancelSegment)
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("processCancelSegment()");
			if (_logger.isLoggable(Level.FINER)) {
				_logger.finer(cancelSegment.getSessionID().dump("", true));
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.finest(cancelSegment.dump("  ", true));
				}
			}
		}
		
		// 6.17.  Acknowledge Cancellation
		//   This procedure is triggered by the reception of a Cx segment.
		//   Response: in the case of a CS segment where there is no transmission
		//   queue-set bound for the sender (possibly because the receiver is a
		//   receive-only device), then no action is taken.  Otherwise:
		//   ...
		//      - If the received segment is a CR segment, a CAR (cancel
		//        acknowledgment to block receiver) segment is issued and is, in
		//        concept, appended to the queue of internal operations traffic
		//        bound for the receiver.
		//   It is possible that the Cx segment has been retransmitted because a
		//   previous responding acknowledgment CAx (cancel acknowledgment)
		//   segment was lost, in which case there will no longer be any record of
		//   the session of which the segment is one token.  If so, no further
		//   action is taken.

		// Find the outgoing block mentioned by the CancelSegment
		OutboundBlock block = findBlockBySessionId(cancelSegment.getSessionID());
		if (block == null) {
			_logger.warning("CancelSegment doesn't match any outstanding Blocks");
			_logger.warning(cancelSegment.dump("  ", true));
			return;
		}
		
		// Remove any outbound segments for this Block from the Link
		block.getLink().onBlockCancelled(block);

		// Close out the block and notify LtpApi that it was cancelled
		removeOutboundBlock(con, block);
		LtpApi.getInstance().onBlockTransmitCancelled(block, cancelSegment.getReasonCode());

		// Send Cancel Ack to originator no matter what state we're in.
		CancelAckSegment cancelAckSegment =
			new CancelAckSegment(SegmentType.CANCEL_SEGMENT_ACK_TO_RECEIVER);
		cancelAckSegment.setSessionID(block.getSessionId());
		try {
			LtpManagement.getInstance()._ltpStats.nCancelAcksSent++;
			transmitSegment(cancelAckSegment, block.getLink(), block.getNeighbor());
		} catch (LtpException e) {
			_logger.log(Level.SEVERE, "Sending CancelAckSegment", e);
			LtpApi.getInstance().onSystemError("Sending CancelAckSegment", e);
			// We could not transmit the CancelAck at this time.  The other
			// side will timeout on the CancelAck and eventually resend the
			// Cancel.
		}
	}
	
	/**
	 * Handle a received CancelAckSegment.  Called from LtpInbound.
	 * We have previously sent a Cancel Request
	 * cancelling the transmission of a Block we had started.  We are awaiting
	 * receipt of a CancelAckSegment.  We find the Block corresponding
	 * to the Session Id mentioned in the CancelAckSegment.  We then blow
	 * away the Block from our queue and callback to LtpApi.
	 * @param cancelAckSegment Given CancelAckSegment
	 * @throws InterruptedException if interrupted
	 */
	private void processCancelAckSegment(java.sql.Connection con, CancelAckSegment cancelAckSegment)
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("processCancelAckSegment()");
			if (_logger.isLoggable(Level.FINER)) {
				_logger.finer(cancelAckSegment.getSessionID().dump("", true));
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.finest(cancelAckSegment.dump("  ", true));
				}
			}
		}
		
		// Find the Block mentioned by the CancelAck
		OutboundBlock block = findBlockBySessionId(cancelAckSegment.getSessionID());
		if (block == null) {
			_logger.warning("LtpOutbound: Unexpected CancelAck Segment; " + 
				"corresponding block not found");
			_logger.warning(cancelAckSegment.dump("  ", true));
			return;
		}
		
		// Make sure we're expecting a Cancel Ack
		if (block.getLtpSenderState() != LtpSenderState.CS_SENT) {
			_logger.warning("LtpOutbound: Unexpected CancelAck Segment; " +
					"Block has not been cancelled");
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
		
		// 6.19.  Cancel Session
		//   This procedure is triggered internally by one of the other procedures
		//   described above.
		//   Response: all segments of the affected session that are currently
		//   queued for transmission can be deleted from the outbound traffic
		//   queues.  All countdown timers currently associated with the session
		//   are deleted.  Note: If the local LTP engine is the sender, then all
		//   remaining data retransmission buffer space allocated to the session
		//   can be released.
		// Close out Block and notify LtpApi Block was Cancelled
		// Remove any outbound segments for this Block from the Link
		block.getLink().onBlockCancelled(block);
		// Remove the outbound Block
		removeOutboundBlock(con, block);
		byte reason = CancelSegment.REASON_CODE_SYSTEM_CANCELED;
		if (block.getOutstandingCancelSegment() != null) {
			reason = block.getOutstandingCancelSegment().getReasonCode();
		}
		LtpApi.getInstance().onBlockTransmitCancelled(block, reason);		
	}
	
	/**
	 * Internal method to send the given Segment over given Link to given Neighbor,
	 * Also arranges that we'll get a callback to onSegmentTransmitted() when
	 * the Segment is transmitted.
	 * @param link Given Link
	 * @param neighbor Given Neighbor
	 * @param segment Segment to send
	 * @throws InterruptedException if interrupted while waiting for queue space
	 * @throws LtpException if Neighbor's segment queue has been full for some time.
	 */
	private void transmitSegment(Segment segment, LtpLink link, LtpNeighbor neighbor)
	throws InterruptedException, LtpException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("transmitSegment(Type=" + segment.getSegmentType() + ")");
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
	 * Called when a higher level component has changed "Scheduled" state of the given
	 * Neighbor, to either inhibit transmission or to enable transmission.  This
	 * is in response to a scheduled change in Neighbor State, a planned event.
	 * @param neighbor The affected Neighbor
	 * @param neighborUp Whether the Neighbor has been scheduled up (true) or down.
	 */
	private void processNeighborScheduledStateChange(LtpNeighbor neighbor, boolean neighborUp) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("processNeighborScheduledStateChange(" + neighbor.getName() + ")");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(neighbor.dump("  ", true));
			}
		}
		for (OutboundBlock block : _outboundBlockList) {
			if (block.getNeighbor().equals(neighbor)) {
				if (block.getLtpSenderState() != LtpSenderState.CLOSED) {
					resumeOrSuspendTimerInBlock(neighbor, neighborUp, block);
				}
			}
		}
	}

	/*
	 * Resume or suspend the appropriate timer depending on block state.
	 */
	private void resumeOrSuspendTimerInBlock(
			LtpNeighbor neighbor,
			boolean neighborUp, 
			OutboundBlock block) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("resumeOrSuspendTimerInBlock()");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(neighbor.dump("  ", true));
				_logger.finest(block.dump("  ", true));
			}
		}
		switch (block.getLtpSenderState()) {
		case CS_SENT:
			// Cancel sent; resume or suspend CancelTimer
			CancelSegment cancelSegment = block.getOutstandingCancelSegment();
			if (cancelSegment != null) {
				if (neighborUp) {
					// 6.6.  Resume Timers
					//   This procedure is triggered by the arrival of a link state cue
					//   indicating the start of transmission from a specified remote LTP
					//   engine to the local LTP engine.  Normally, this event is inferred
					//   from advance knowledge of the remote engine's planned transmission
					//   schedule.
					//   Response: expected arrival time is adjusted for every acknowledging
					//   segment that the remote engine is expected to return, for which the
					//   countdown timer has been suspended.
					stopCancelTimer(cancelSegment, block);
				} else {
					// 6.5.  Suspend Timers
					//  This procedure is triggered by the arrival of a link state cue
					//   indicating the cessation of transmission from a specified remote LTP
					//   engine to the local LTP engine.  Normally, this event is inferred
					//   from advance knowledge of the remote engine's planned transmission
					//   schedule.
					//   Response: countdown timers for the acknowledging segments that the
					//   remote engine is expected to return are suspended as necessary based
					//   on the following procedure.
					startCancelTimer(cancelSegment, block);
				}
			} else {
				_logger.warning("No outstanding CancelSegment for the Block");
			}
			break;
			
		case CLOSED:
			// Nothing
			break;
			
		case RP_XMIT:
			// Fall thru
		case GP_XMIT:
			// Fall thru
		case WAIT_RP_ACK:
			// In any of the above states, we have Red Segments in transit which
			// may not have received Report Segments.  Resume or suspend
			// Checkpoint timer
			for (DataSegment segment : block) {
				if (neighborUp) {
					// 6.6.  Resume Timers
					//   This procedure is triggered by the arrival of a link state cue
					//   indicating the start of transmission from a specified remote LTP
					//   engine to the local LTP engine.  Normally, this event is inferred
					//   from advance knowledge of the remote engine's planned transmission
					//   schedule.
					//   Response: expected arrival time is adjusted for every acknowledging
					//   segment that the remote engine is expected to return, for which the
					//   countdown timer has been suspended.
					if (!segment.isAcked() && segment.isCheckpoint()) {
						startCheckpointTimer(segment);
					}
				} else {
					// 6.5.  Suspend Timers
					//  This procedure is triggered by the arrival of a link state cue
					//   indicating the cessation of transmission from a specified remote LTP
					//   engine to the local LTP engine.  Normally, this event is inferred
					//   from advance knowledge of the remote engine's planned transmission
					//   schedule.
					//   Response: countdown timers for the acknowledging segments that the
					//   remote engine is expected to return are suspended as necessary based
					//   on the following procedure.
					if (!segment.isAcked() && segment.isCheckpoint()) {
						stopCheckpointTimer(segment);
					}
				}
			}
			break;
			
		default:
			// No further action necessary
			break;
		}
		
	}
	
	/**
	 * Called when a Segment is 'on the wire'.  We advance the Ltp Sender State
	 * Machine where appropriate and necessary
	 * @param link Link on which Segment was transmitted
	 * @param segment Segment transmitted
	 * @throws InterruptedException if interrupted while waiting for queue space
	 */
	private void processSegmentTransmitStarted(java.sql.Connection con, LtpLink link, Segment segment)
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("processSegmentTransmitStarted(SegmentType=" + 
					segment.getSegmentType() + ")");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(segment.dump("  ", true));
			}
		}
		if (segment instanceof DataSegment) {
			// Segment Transmitted was a DataSegment
			DataSegment dataSegment = (DataSegment)segment;
			dataSegment.setSent(true);
			Block dBlock = dataSegment.getBlock();
			if (!(dBlock instanceof OutboundBlock)) {
				_logger.severe("FIX THIS!");
				return;
			}
			OutboundBlock block = (OutboundBlock)dBlock;
			switch (block.getLtpSenderState()) {
			case FG_XMIT:
				// All Green block.  If this was last segment of block
				// then we're done.
				if (dataSegment.getSegmentType() == SegmentType.GREEN_EOB) {
					// No more Segments; this session is Done
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("Outbound Session Complete");
						if (_logger.isLoggable(Level.FINER)) {
							_logger.finer(block.getSessionId().dump("", true));
						}
					}
					removeOutboundBlock(con, block);
					LtpApi.getInstance().onSegmentsTransmitted(block);
					// 6.12.  Signify Transmission Completion
					//   This procedure is triggered at the earliest time at which (a) all
					//   data in the block are known to have been transmitted *and* (b) the
					//   entire red-part of the block -- if of non-zero length -- is known to
					//   have been successfully received.  Condition (a) is signaled by
					//   arrival of a link state cue indicating the de-queuing (for
					//   transmission) of the EOB segment for the block.  Condition (b) is
					//   signaled by reception of an RS segment whose reception claims, taken
					//   together with the reception claims of all other RS segments
					//   previously received in the course of this session, indicate complete
					//   reception of the red-part of the block.
					// Response: a transmission-session completion notice (Section 7.4) is
					//   sent to the local client service associated with the session, and the
					//   session is closed: the "Close Session" procedure (Section 6.20) is
					//   invoked.
					LtpApi.getInstance().onBlockSent(block);
				}					
				break;
				
			case RP_XMIT:
				// Transmitting Red Segments of all-Red or mixed-color Block
				if (dataSegment.isCheckpoint() && !dataSegment.isAcked()) {
					// 6.2.  Start Checkpoint Timer
					//   This procedure is triggered by the arrival of a link state cue
					//   indicating the de-queuing (for transmission) of a CP segment.				
					//   Response: the expected arrival time of the RS segment that will be
					//   produced on reception of this CP segment is computed, and a countdown
					//   timer is started for this arrival time.  However, if it is known that
					//   the remote LTP engine has ceased transmission (Section 6.5), then
					//   this timer is immediately suspended, because the computed expected
					//   arrival time may require an adjustment that cannot yet be computed.
					startCheckpointTimer(dataSegment);
				}
				
				// Figure out if Block should change state.  It will change
				// state if this block is EOB or EORP
				switch (dataSegment.getSegmentType()) {
				case RED_CP_EORP_NOT_EOB:
					// END OF RED PART, not END OF BLOCK
					// Green Segments follow
					// Advance state to transmitting Green Segments of block
					// Transmit Green Segments in Block
					block.setLtpSenderState(LtpSenderState.GP_XMIT);
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("Session => " + block.getLtpSenderState());
					}
					for (DataSegment nextDataSegment : block) {
						if (!nextDataSegment.isRedData()) {
							try {
								LtpManagement.getInstance()._ltpStats.nDataSegmentsSent++;
								transmitSegment(nextDataSegment, block.getLink(), 
										block.getNeighbor());
							} catch (LtpException e) {
								_logger.severe("Transmitting Green Segment " + 
										nextDataSegment + " of mixed Block " + block);
								// Could not transmit Green DataSegment at this time.
								// It's green, so its unreliable.  It will get lost.
							}
						}
					}
					break;
					
				case RED_CP_EORP_EOB:
					// END OF RED PART, END OF BLOCK, No Segments follow
					// Advance state to await Acks for all Segments
					block.setLtpSenderState(LtpSenderState.WAIT_RP_ACK);
					LtpApi.getInstance().onSegmentsTransmitted(block);
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("Session => " + block.getLtpSenderState());
					}
					break;
					
				default:
					// Not End of Block nor End of Red Part
					// Nothing needs to be done; no state change
				}
				break;
				
			case GP_XMIT:
				// Transmitting Green Segments from mixed color Block
				if (dataSegment.getSegmentType() == SegmentType.GREEN_EOB) {
					// This was last Green Segment from Block; no segments follow
					// Advance state to await Acks for all Segments
					block.setLtpSenderState(LtpSenderState.WAIT_RP_ACK);
					LtpApi.getInstance().onSegmentsTransmitted(block);
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("Session => " + block.getLtpSenderState());
					}
					// If all Red Segments have been Acked then we're done
					if (block.isOutboundBlockComplete()) {
						if (GeneralManagement.isDebugLogging()) {
							_logger.fine("Outbound SessionComplete");
						}
						removeOutboundBlock(con, block);
						// 6.12.  Signify Transmission Completion
						//   This procedure is triggered at the earliest time at which (a) all
						//   data in the block are known to have been transmitted *and* (b) the
						//   entire red-part of the block -- if of non-zero length -- is known to
						//   have been successfully received.  Condition (a) is signaled by
						//   arrival of a link state cue indicating the de-queuing (for
						//   transmission) of the EOB segment for the block.  Condition (b) is
						//   signaled by reception of an RS segment whose reception claims, taken
						//   together with the reception claims of all other RS segments
						//   previously received in the course of this session, indicate complete
						//   reception of the red-part of the block.
						// Response: a transmission-session completion notice (Section 7.4) is
						//   sent to the local client service associated with the session, and the
						//   session is closed: the "Close Session" procedure (Section 6.20) is
						//   invoked.
						LtpApi.getInstance().onBlockSent(block);
					}
				}
				break;
				
			case WAIT_RP_ACK:
				// Awaiting Reports on Checkpoints
				if (dataSegment.isCheckpoint() && !dataSegment.isAcked()) {
					// 6.2.  Start Checkpoint Timer
					//   This procedure is triggered by the arrival of a link state cue
					//   indicating the de-queuing (for transmission) of a CP segment.				
					//   Response: the expected arrival time of the RS segment that will be
					//   produced on reception of this CP segment is computed, and a countdown
					//   timer is started for this arrival time.  However, if it is known that
					//   the remote LTP engine has ceased transmission (Section 6.5), then
					//   this timer is immediately suspended, because the computed expected
					//   arrival time may require an adjustment that cannot yet be computed.
					startCheckpointTimer(dataSegment);
				}
				break;
				
			default:
				// No further action necessary
				break;
			}
			
		} else if (segment instanceof CancelSegment) {
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
			OutboundBlock block = findBlockBySessionId(sessionId);
			if (block != null) {
				block.setOutstandingCancelSegment(cancelSegment);
				startCancelTimer(cancelSegment, block);
				
			} else {
				_logger.warning("CancelSegment transmitted but no matching block");
				_logger.warning(cancelSegment.dump("  ", true));
			}
		}
	}

	/**
	 * Start Checkpoint Timer on given DataSegment
	 * @param dataSegment Given DataSegment
	 */
	private void startCheckpointTimer(final DataSegment dataSegment) {
		if (dataSegment.getNeighbor().isNeighborScheduledUp()) {
			if (dataSegment.getCheckpointTimerTask() != null) {
				stopCheckpointTimer(dataSegment);
			}
			LtpManagement.getInstance().getLtpStats().nCkPtTimerStarts++;
			dataSegment.setCheckpointTimerTask(
				new TimerTask() {
					@Override
					public void run() {
						OutboundBlock block = (OutboundBlock)dataSegment.getBlock();
						if (block.getLtpSenderState() == LtpSenderState.CLOSED) {
							_logger.warning("CheckPoint Timer expired on block in CLOSED state");
							_logger.warning(block.dump("", false));
						}
						CheckpointTimerExpiredEvent event =
							new CheckpointTimerExpiredEvent(dataSegment);
						try {
							processEvent(event);
						} catch (InterruptedException e) {
							// Ignore
						}
					}
				}
			);
			_timer.schedule(
					dataSegment.getCheckpointTimerTask(), 
					dataSegment.getLink().getCheckpointTimeout());
		}
	}
	
	/**
	 * Stop the Checkpoint Timer on given DataSegment
	 * @param dataSegment Given DataSegment
	 */
	private void stopCheckpointTimer(DataSegment dataSegment) {
		if (dataSegment.getCheckpointTimerTask() != null) {
			LtpManagement.getInstance().getLtpStats().nCkPtTimerStops++;
			dataSegment.getCheckpointTimerTask().cancel();
			_timer.purge();
			dataSegment.setCheckpointTimerTask(null);
		}
	}
	
	/**
	 * Called when Checkpoint Timer expires on given DataSegment;
	 * i.e., a Timeout occurs waiting for a RS (Report Segment) in
	 * response to a prior DataSegment transmission.
	 * @param dataSegment DataSegment whose RS expired
	 */
	private void onCheckpointTimerExpired(DataSegment dataSegment) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onCheckpointTimerExpired()");
			if (_logger.isLoggable(Level.FINER)) {
				_logger.finer(dataSegment.getSessionID().dump("", true));
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.finest(dataSegment.dump("  ", true));
				}
			}
		}
		LtpManagement.getInstance()._ltpStats.nCheckpointExpirations++;
	
		Block dBlock = dataSegment.getBlock();
		if (!(dBlock instanceof OutboundBlock)) {
			_logger.severe("FIX THIS!");
			return;
		}
		OutboundBlock block = (OutboundBlock)dBlock;
		switch (block.getLtpSenderState()) {
		case RP_XMIT:
			// Fall thru
		case GP_XMIT:
			// Fall thru
		case WAIT_RP_ACK:
			// 6.7.  Retransmit Checkpoint
			//   This procedure is triggered by the expiration of a countdown timer
			//   associated with a CP segment.
			//   Response: if the number of times this CP segment has been queued for
			//   transmission exceeds the checkpoint retransmission limit established
			//   for the local LTP engine by network management, then the session of
			//   which the segment is one token is canceled: the "Cancel Session"
			//   procedure (Section 6.19) is invoked, a CS with reason-code RLEXC is
			//   appended to the (conceptual) application data queue, and a
			//   transmission-session cancellation notice (Section 7.5) is sent back
			//   to the client service that requested the transmission.
			//   Otherwise, a new copy of the CP segment is appended to the
			//   (conceptual) application data queue for the destination LTP engine.
			if (dataSegment.isTooManyTransmitEnqueues()) {
				// Cancel Block
				_logger.warning("Retransmission Limit Exceeded");
				_logger.warning(block.getSessionId().dump("", true));
				
				try {
					cancelBlock(
							block, 
							CancelSegment.REASON_CODE_RETRANS_LIMIT_EXCEEDED);
					
				} catch (InterruptedException e) {
					_logger.log(
							Level.SEVERE, 
							"Cancelling block after retransmission limit exceeded",
							e);
					// Make sure Timer Task cancelled
					stopCheckpointTimer(dataSegment);
				}
				
			} else {
				// Resend DataSegment
				try {
					LtpManagement.getInstance()._ltpStats.nDataSegmentsSent++;
					LtpManagement.getInstance()._ltpStats.nDataSegmentResends++;
					transmitSegment(
							dataSegment,
							dataSegment.getLink(), 
							dataSegment.getNeighbor());
					
				} catch (LtpException e) {
					_logger.log(
							Level.SEVERE, 
							"Resending segment " + dataSegment, 
							e);
					// Could not resend Checkpoint.  Go ahead and start
					// its checkpoint timer again, and we'll try to resend
					// when it's done.
					startCheckpointTimer(dataSegment);
					
				} catch (InterruptedException e) {
					_logger.log(
							Level.SEVERE, 
							"Resending segment " + dataSegment, 
							e);
					// Make sure Timer Task cancelled
					stopCheckpointTimer(dataSegment);
				}
			}
			break;
			
		default:
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Checkpoint Timer expired on Block in state " + 
						block.getLtpSenderState());
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.finest(dataSegment.dump("  ", true));
					_logger.finest(block.dump("  ", false));
				}
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
			final OutboundBlock block) {
		
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
	private void stopCancelTimer(CancelSegment cancelSegment, OutboundBlock block) {
		if (block.getCancelTimerTask() != null) {
			block.getCancelTimerTask().cancel();
			_timer.purge();
			block.setCancelTimerTask(null);
		}
	}
	
	/**
	 * Called when the CancelTimer expires.  I.e., we have sent a CancelSegment
	 * cancelling an Outbound block and we were awaiting a Cancel Ack, but
	 * the CancelTimer expired without receiving a Cancel Ack.
	 * @param segment The CancelSegment sent to cancel a Block
	 * @param block The Block being cancelled
	 * @throws InterruptedException if interrupted
	 */
	private void onCancelTimerExpired(java.sql.Connection con, CancelSegment segment, OutboundBlock block)
	throws InterruptedException {
		LtpManagement.getInstance()._ltpStats.nCancelExpirations++;
		if (block.getLtpSenderState() == LtpSenderState.CS_SENT) {
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
				// 6.19.  Cancel Session
				//   This procedure is triggered internally by one of the other procedures
				//   described above.
				//   Response: all segments of the affected session that are currently
				//   queued for transmission can be deleted from the outbound traffic
				//   queues.  All countdown timers currently associated with the session
				//   are deleted.  Note: If the local LTP engine is the sender, then all
				//   remaining data retransmission buffer space allocated to the session
				//   can be released.
				_logger.warning("Cancel Segment exceeded resend limit");
				_logger.warning(block.getSessionId().dump("", true));
				if (GeneralManagement.isDebugLogging()) {
					if (_logger.isLoggable(Level.FINEST)) {
						_logger.finest(segment.dump("  ", true));
					}
				}
				removeOutboundBlock(con, block);
				LtpApi.getInstance().onBlockTransmitCancelled(
						block, 
						CancelSegment.REASON_CODE_RETRANS_LIMIT_EXCEEDED);
				
			} else {
				// Resend CancelSegment
				try {
					LtpManagement.getInstance()._ltpStats.nCancelsSent++;
					transmitSegment(segment, block.getLink(), block.getNeighbor());
					
				} catch (LtpException e) {
					// Could not retransmit Cancel Segment at this time.  Start
					// the Cancel timer and try again later.
					_logger.log(Level.SEVERE, "retransmit cancel segment " + segment, e);
					startCancelTimer(segment, block);
					
				} catch (InterruptedException e) {
					_logger.log(Level.SEVERE, "retransmit cancel segment " + segment, e);
					// Make sure Timer Task gets cancelled
					stopCancelTimer(segment, block);
				}
			}
		} else {
			_logger.warning("CancelTimer expiration when block not in CS_SENT State");
			_logger.warning(segment.dump("  ", true));
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
		StringBuffer sb = new StringBuffer(indent + "LtpOutbound\n");
		sb.append(indent + "  Outbound Bytes=" + _outboundBytes + "\n");
		for (Block block : _outboundBlockList) {
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
	public void onNeighborOperationalChange(Neighbor neighbor, boolean neighborUp) {
		// Not used
	}

	@Override
	public void onLinkOperationalStateChange(Link link, boolean linkOperational) {
		// Not Used
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
