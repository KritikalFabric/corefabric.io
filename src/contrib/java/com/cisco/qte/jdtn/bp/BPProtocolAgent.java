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
package com.cisco.qte.jdtn.bp;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.bp.BundleHolder.BundleHoldCallback;
import com.cisco.qte.jdtn.component.AbstractEventProcessorThread;
import com.cisco.qte.jdtn.component.IEvent;
import com.cisco.qte.jdtn.component.StopEvent;
import com.cisco.qte.jdtn.events.BundleCancellationByReceiverEvent;
import com.cisco.qte.jdtn.events.BundleCancellationEvent;
import com.cisco.qte.jdtn.events.BundleCustodyTransferTimerExpiredEvent;
import com.cisco.qte.jdtn.events.BundleEvent;
import com.cisco.qte.jdtn.events.BundleHoldCancelEvent;
import com.cisco.qte.jdtn.events.BundleTimerExpiredEvent;
import com.cisco.qte.jdtn.events.BundleTransmitCompleteEvent;
import com.cisco.qte.jdtn.events.BundleTransmitFailedEvent;
import com.cisco.qte.jdtn.events.InboundBundleEvent;
import com.cisco.qte.jdtn.events.JDTNEvent;
import com.cisco.qte.jdtn.events.OutboundBundleEvent;
import com.cisco.qte.jdtn.events.PostBundleHoldEvent;
import com.cisco.qte.jdtn.events.RestoreBundlesEvent;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Management;
import com.cisco.qte.jdtn.ltp.CancelSegment;
import com.cisco.qte.jdtn.ltp.LtpApi;
import com.cisco.qte.jdtn.ltp.LtpException;
import com.cisco.qte.jdtn.persistance.BundleDatabase;
import com.cisco.qte.jdtn.persistance.BundleDatabaseRestoreCallback;
import com.cisco.qte.jdtn.persistance.BundleSource;
import com.cisco.qte.jdtn.persistance.BundleState;

/**
 * Bundle Processor.  Performs protocol procedures and keeps track of
 * retained Bundles.
 */
public class BPProtocolAgent extends AbstractEventProcessorThread
implements BundleHoldCallback {

	public static final int BP_PROTOCOL_AGENT_EVENT_QUEUE_CAPACITY = 1000;
	public static final long BP_PROTOCOL_AGENT_JOIN_MSECS = 2000L;
	public static final long BP_PROTOCOOL_AGENT_LINK_HOLD_MSECS = 1000L;
	
	@SuppressWarnings("hiding")
	private static final Logger _logger =
		Logger.getLogger(BPProtocolAgent.class.getCanonicalName());
	
	private static final BPProtocolAgent _instance = new BPProtocolAgent();
	
	// List of Outbound Retained Bundles
	private ArrayList<Bundle> _outboundBundleList =
		new ArrayList<Bundle>();
	
	// Map of BundleId to Bundle for quick lookups for Outbound retained Bundles
	private HashMap<ExtendedBundleId, Bundle>_outboundBundleMap =
		new HashMap<ExtendedBundleId, Bundle>();
	
	// List of Inbound Retained Bundles
	private ArrayList<Bundle> _inboundBundleList =
		new ArrayList<Bundle>();
	
	// Map of BundleId to Bundle for Inbound retained Bundles
	private HashMap<ExtendedBundleId, Bundle>_inboundBundleMap =
		new HashMap<ExtendedBundleId, Bundle>();
	
	// Bundle Options for Admin Records: all red
	private BundleOptions adminRecBundleOptions =
		new BundleOptions(BundleColor.RED);

	private Timer _timer = null;
	
	/**
	 * Get singleton instance
	 * @return Singleton instance
	 */
	public static BPProtocolAgent getInstance() {
		return _instance;
	}
	
	/**
	 * Private constructor
	 */
	private BPProtocolAgent() {
		super("BPProtocolAgent", BP_PROTOCOL_AGENT_EVENT_QUEUE_CAPACITY);
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("startup");
		}
	}
	
	/**
	 * Startup operation of BPProtocolAgent
	 * @throws InterruptedException 
	 * @throws IllegalStateException 
	 * @throws IllegalArgumentException 
	 */
	@Override
	protected void startImpl() {
		BPManagement.getInstance()._bpStats.nRetainedBytes = 0L;
		BPFragmentation.getInstance().startup();
		_timer = new Timer();
		super.startImpl();
	}
	
	/**
	 * Shutdown operation of BPProtocolAgent
	 * @throws InterruptedException 
	 */
	private void shutdown()  {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("shutdown");
		}
		
		_timer.purge();
		_timer.cancel();
		_timer = null;
		
		while (!_outboundBundleList.isEmpty()) {
			Bundle bundle = _outboundBundleList.remove(0);
			_outboundBundleMap.remove(bundle.getExtendedBundleId());
			bundle.close();
		}
		while (!_inboundBundleList.isEmpty()) {
			Bundle bundle = _inboundBundleList.remove(0);
			_inboundBundleMap.remove(bundle.getExtendedBundleId());
			bundle.close();
		}
		BPManagement.getInstance()._bpStats.nRetainedBytes = 0L;		
	}
	
	/**
	 * Called from BpApi to cancel a pending Bundle Transmission
	 * @param bundle Bundle to Cancel
	 * @throws InterruptedException 
	 * @throws JDtnException 
	 * @throws BPException 
	 */
	public void cancelBundleTransmission(Bundle bundle)
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("cancelBundleTransmission()");
		}
		
		BundleCancellationEvent event =
			new BundleCancellationEvent(bundle);
		processEvent(event);
	}
	
	/**
	 * Enqueue a Bundle for transmission.  Called from BpApi to start
	 * transmission on a local App generated bundle.
	 * @param bundle Given Bundle
	 * @throws InterruptedException If interrupted while waiting to enqueue
	 * @throws BPException If cannot accept Bundle due to space
	 */
	public void enqueueOutboundBundle(Bundle bundle)
	throws InterruptedException, BPException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("enqueueOutboundBundle()");
		}
		
		if (!BPManagement.getInstance().canAcceptCustody(bundle)) {
			// Accepting this Bundle would exceed configured retention limit
			throw new RetentionLimitExceededException(
					"Accepting this Bundle would exceed configured retention limit");
		}
		OutboundBundleEvent event =
			new OutboundBundleEvent(bundle);
		processEvent(event);
	}
	
	/**
	 * Called from adaptation layer when Bundle successfully transmitted.
	 * @param bundle Bundle successfully transmitted
	 * @throws InterruptedException if interrupted
	 */
	public void onBundleTransmitComplete(Bundle bundle) 
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onBundleTransmitComplete()");
		}
		
		BundleTransmitCompleteEvent event =
			new BundleTransmitCompleteEvent(bundle);
		processEvent(event);
	}
	
	/**
	 * Called from adaptation layer on receipt of a Bundle,
	 * Bundle Received Procedure.
	 */
	public void onBundleReceived(Bundle bundle) throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onBundleReceived()");
		}
		
		InboundBundleEvent event =
			new InboundBundleEvent(bundle);
		processEvent(event);
	}
	
	/**
	 * Called from adaptation layer on Bundle transmit failure
	 * @param bundle The bundle whose transmission failed
	 * @throws InterruptedException if processing interrupted
	 */
	public void onBundleTransmitFailed(Bundle bundle) throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onBundleTransmitFailed()");
		}
		
		BundleTransmitFailedEvent event = new BundleTransmitFailedEvent(bundle);
		processEvent(event);
	}
	
	/**
	 * Called from Adaptation Layer when a Bundle Transmission gets cancelled
	 * by receiver.
	 * @param bundle The cancelled Bundle
	 * @param reason Why cancelled, one of CancelSegment.REASON_CODE_
	 * @throws InterruptedException *
	 */
	public void onBundleTransmitCancelledByReceiver(Bundle bundle, byte reason) 
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onBundleTransmitCancelledByReceiver()");
		}
		
		BundleCancellationByReceiverEvent event =
			new BundleCancellationByReceiverEvent(bundle, reason);
		processEvent(event);
	}
	
	/**
	 * Request that BPProtocolAgent restore Bundles from Bundles Database
	 */
	public void requestBundleRestore() {
		// Enqueue event to start restoration of Bundles from BundleDatabase
		try {
			processEvent(new RestoreBundlesEvent());
		} catch (InterruptedException e) {
			_logger.log(Level.SEVERE, "processEvent()", e);
		}
	}
	
	/**
	 * Called from BundleHolder when a Bundle has spent required delay on
	 * hold and must now be processed
	 * @param bundle Affected Bundle
	 * @throws InterruptedException If interrupt occurs when enqueueing.
	 */
	protected void onPostBundleHold(Bundle bundle) throws InterruptedException {
		PostBundleHoldEvent event =
			new PostBundleHoldEvent(bundle);
		processEvent(event);
	}
	
	/**
	 * Called from BundleHolder when a Bundle on Hold has its hold cancelled
	 * before the scheduled time interval.
	 * @param bundle Affected Bundle
	 * @throws InterruptedException If interrupt occurs when enqueueing.
	 */
	protected void onBundleHoldCancel(Bundle bundle) throws InterruptedException {
		BundleHoldCancelEvent event = new BundleHoldCancelEvent(bundle);
		processEvent(event);
	}
	
	/**
	 * Thread event handler.  Processes and
	 * Dispatches to processXXX() methods based on Event type.
	 * @param iEvent The event to process
	 */
	@Override
	protected void processEventImpl(IEvent iEvent) throws Exception {
		try {
			if (iEvent instanceof StopEvent) {
				shutdown();
			}
			else if (!(iEvent instanceof JDTNEvent)) {
				throw new IllegalArgumentException("Event not instanceof JDTNEvent");
			}
			else {
				JDTNEvent event = (JDTNEvent)iEvent;
				switch (event.getEventType()) {
				case INBOUND_BUNDLE:
					BundleEvent bundleEvent = (BundleEvent)event;
					processBundleReceived(bundleEvent.getBundle());
					break;
					
				case OUTBOUND_BUNDLE:
					bundleEvent = (BundleEvent)event;
					try {
						processOutboundBundleEnqueued(bundleEvent.getBundle());
					} catch (RetentionLimitExceededException e) {
						_logger.severe("BPProtocolAgent: Bundle Retention Limit Exceeded");
						BpApi.getInstance().onRetentionLimitExceeded(bundleEvent.getBundle());
					}
					break;
					
				case BUNDLE_CANCEL:
					BundleCancellationEvent bce = (BundleCancellationEvent)event;
					processBundleCancellationEvent(bce.getBundle());
					break;
					
				case BUNDLE_TRANSMIT_COMPLETE:
					bundleEvent = (BundleEvent)event;
					processBundleTransmitted(bundleEvent.getBundle());
					break;
					
				case BUNDLE_TRANSMIT_FAILED:
					BundleTransmitFailedEvent btfe = (BundleTransmitFailedEvent)event;
					processBundleTransmitFailed(btfe.getBundle());
					break;
					
				case BUNDLE_CANCEL_BY_RECEIVER:
					BundleCancellationByReceiverEvent bcbre =
						(BundleCancellationByReceiverEvent)event;
					processBundleTransmitCancelledByReceiver(bcbre.getBundle(), bcbre.getReason());
					break;
					
				case BUNDLE_TIMER_EXPIRED:
					bundleEvent = (BundleEvent)event;
					processBundleTimerExpired(bundleEvent.getBundle());
					break;
					
				case BUNDLE_CUSTODY_TIMER_EXPIRED:
					bundleEvent = (BundleEvent)event;
					processCustodyXferTimerExpired(bundleEvent.getBundle());
					break;
					
				case BUNDLE_HOLD_CANCELLED:
					BundleHoldCancelEvent bhce = (BundleHoldCancelEvent)event;
					processBundleHoldCancel(bhce.getBundle());
					break;
					
				case POST_BUNDLE_HOLD:
					PostBundleHoldEvent pbhe = (PostBundleHoldEvent)event;
					processPostBundleHoldEvent(pbhe.getBundle());
					break;
					
				case RESTORE_BUNDLES_EVENT:
					processRestoreBundlesEvent();
					break;
					
				default:
					_logger.severe("Unknown event type");
					break;
				}
			}
			
		} catch (BPException e) {
			_logger.log(Level.SEVERE, "BPProtocolAgent", e);
			LtpApi.getInstance().onSystemError("BPProtocolAgent", e);
		} catch (JDtnException e) {
			_logger.log(Level.SEVERE, "BPProtocolAgent", e);
			LtpApi.getInstance().onSystemError("BPProtocolAgent", e);
		}
	}
	
	/**
	 * Process request to restore Bundles from the Bundles Database
	 */
	private void processRestoreBundlesEvent() {
		try {
			BundleDatabase.getInstance().restoreBundles(
				new BundleDatabaseRestoreCallback() {
					
					@Override
					public void restoreBundle(Bundle bundle, BundleSource bundleSource,
							BundleState bundleState) {
						_logger.fine("Restoring Bundle");
						_logger.fine("State=" + BundleState.toParseableString(bundleState));
						_logger.fine("Source=" + BundleSource.toParseableString(bundleSource));
						_logger.fine(bundle.dump("", true));
						try {
							switch (bundleState) {
							case SOURCED:
								processOutboundBundleEnqueued(bundle);
								break;
								
							case RECEIVED:
								processBundleReceived(bundle);
								break;
								
							case FORWARD_ENQUEUED:
								switch (bundleSource) {
								case FORWARDED:
									addBundleToInboundList(bundle);
									break;
								case SOURCED:
									addBundleToOutboundList(bundle);
									break;
								}
								startBundleTimer(bundle);
								forwardBundle(bundle);
								break;
								
							case HELD:
								switch (bundleSource) {
								case FORWARDED:
									addBundleToInboundList(bundle);
									break;
								case SOURCED:
									addBundleToOutboundList(bundle);
									break;
								}
								startBundleTimer(bundle);
								BundleHolder.getInstance().addBundle(
										bundle, 
										BP_PROTOCOOL_AGENT_LINK_HOLD_MSECS, 
										BPProtocolAgent.getInstance());
								break;
								
							case IN_CUSTODY:
								switch (bundleSource) {
								case FORWARDED:
									addBundleToInboundList(bundle);
									break;
								case SOURCED:
									addBundleToOutboundList(bundle);
									break;
								}
								startBundleTimer(bundle);
								break;
								
							default:
								_logger.severe("Invalid BundleState on Bundle Restoration");
							}
						} catch (Exception e) {
							_logger.log(Level.SEVERE, "restoreBundle()", e);
						}
					}
				}
			);
		} catch (Exception e) {
			_logger.log(Level.SEVERE, "Bundle Restoration", e);
		}
	}
	
	/**
	 * Internal method to add Given Bundle to our retained list
	 * @param bundle Given Bundle
	 */
	private void addBundleToOutboundList(Bundle bundle) {
		_outboundBundleList.add(bundle);
		_outboundBundleMap.put(bundle.getExtendedBundleId(), bundle);
	}
	
	/**
	 * Internal method to remove Given Bundle from our retained list
	 * @param bundle Given Bundle
	 */
	private void removeBundleFromOutboundList(Bundle bundle) {
		_outboundBundleMap.remove(bundle.getExtendedBundleId());
		_outboundBundleList.remove(bundle);
	}
	
	/**
	 * Internal method to query if given Bundle is in retained list
	 * @param bundle Bundle to query for
	 * @return True if in list
	 */
	private boolean isBundleInOutboundList(Bundle bundle) {
		return _outboundBundleMap.containsKey(bundle.getExtendedBundleId());
	}
	
	/**
	 * Internal method to add Given Bundle to our Inbound retained list
	 * @param bundle Bundle to add
	 */
	private void addBundleToInboundList(Bundle bundle) {
		_inboundBundleList.add(bundle);
		_inboundBundleMap.put(bundle.getExtendedBundleId(), bundle);
	}
	
	/**
	 * Internal method to remove Given Bundle from our Inbound retained list
	 * @param bundle Bundle to add
	 */
	private void removeBundleFromInboundList(Bundle bundle) {
		_inboundBundleList.remove(bundle);
		_inboundBundleMap.remove(bundle.getExtendedBundleId());
	}
	
	/**
	 * Internal method to query if given Bundle is in Inbound retained list
	 * @param bundle Bundle to query for
	 * @return True if in list
	 */
	private boolean isBundleInInboundList(Bundle bundle) {
		return _inboundBundleMap.containsKey(bundle.getExtendedBundleId());
	}
	
	/**
	 * Internal method to add given Bundle to either Inbound retained list or
	 * Outbound retained list, depending on whether its an Inbound or Outbound
	 * bundle.
	 * @param bundle
	 */
	private void addRetainedBundle(Bundle bundle) {
		if (bundle.isInboundBundle()) {
			addBundleToInboundList(bundle);
		} else {
			addBundleToOutboundList(bundle);
		}
	}
	
	/**
	 * Internal method to remove given Bundle from either Inbound retained
	 * list or Outbound retained list (but not both)
	 * @param bundle Bundle to remove
	 */
	private void removeRetainedBundle(Bundle bundle) {
		if (!bundle.isInboundBundle() && isBundleInOutboundList(bundle)) {
			removeBundleFromOutboundList(bundle);
		} else if (bundle.isInboundBundle() && isBundleInInboundList(bundle)) {
			removeBundleFromInboundList(bundle);
		}
	}
	
	/**
	 * Determine if given Bundle is either in Inbound or Outbound retained list.
	 * @param bundle Given Bundle
	 * @return True if bundle is in either list.
	 */
	private boolean isBundleRetained(Bundle bundle) {
		if (bundle.isInboundBundle() && isBundleInInboundList(bundle)) {
			return true;
		}
		if (!bundle.isInboundBundle() && isBundleInOutboundList(bundle)) {
			return true;
		}
		return false;
	}
	
	/**
	 * Process request for Cancellation of a pending Bundle Transmission
	 * @param bundle Bundle to Cancel
	 * @throws InterruptedException 
	 * @throws JDtnException On JDTN Specific error
	 * @throws SQLException On Bundle database error
	 */
	private void processBundleCancellationEvent(Bundle bundle) 
	throws InterruptedException, SQLException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("processBundleCancellationEvent()");
		}
		/*
		 * 5.15.  Canceling a Transmission
		 *  When requested to cancel a specified transmission, where the bundle
		 *  created upon initiation of the indicated transmission has not yet
		 *  been discarded, the bundle protocol agent must delete that bundle for
		 *  the reason "transmission cancelled".  For this purpose, the procedure
		 *  defined in Section 5.13 must be followed.
		 */
		if (isBundleRetained(bundle)) {
			try {
				deleteBundle(bundle, BundleStatusReport.REASON_CODE_TRANSMIT_CANCELED);
			} catch (JDtnException e) {
				_logger.log(Level.SEVERE, "deleteBundle()", e);
				LtpApi.getInstance().onSystemError("deleteBundle", e);
			}
			
		} else {
			_logger.warning("Bundle to be cancelled is not in retained list");
		}
	}
	
	/**
	 * Process an Outbound Bundle for transmission.  Called from BpApi to start
	 * transmission on a local App generated bundle.
	 * @param bundle Given Bundle
	 * @throws InterruptedException if interrupted
	 * @throws JDtnException On JDTN specific errors
	 * @throws SQLException On Database errors
	 */
	private void processOutboundBundleEnqueued(Bundle bundle)
	throws InterruptedException, JDtnException, SQLException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("processOutboundBundleEnqueued()");
		}
		
		if (isBundleInOutboundList(bundle)) {
			if (GeneralManagement.isDebugLogging()) {
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.finest("Bundle to be transmitted is already in retained list");
					_logger.finest(bundle.dump("", true));
				}
			}
			throw new BPException("Bundle to be transmitted is already in retained list");
		}
		
		// Make sure we have enough room to accept the bundle
		if (!BPManagement.getInstance().canAcceptCustody(bundle)) {
			if (GeneralManagement.isDebugLogging()) {
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.finest("Outbound bundle exceeds bundle retention constraint");
					_logger.finest(bundle.dump("", true));
				}
			}
			discardBundleUnconditionally(bundle);
			throw new BPException("Outbound bundle exceeds bundle retention constraint");
		}
		
		// Introduce the Bundle into the Database; we have to assume an
		// EidScheme since we don't know the actual EidScheme until we forward
		BundleDatabase.getInstance().introduceBundle(
				bundle, 
				BundleSource.SOURCED, 
				BundleState.SOURCED, 
				BPManagement.getInstance().getEidScheme());
		
		// Update statistics
		long nBytes = bundle.getPayloadBundleBlock().getPayload().getLength();
		BPManagement.getInstance()._bpStats.nRetainedBytes += nBytes;
		BPManagement.getInstance()._bpStats.nDataBundlesSourced++;
		addRetainedBundle(bundle);
		startBundleTimer(bundle);
		
		/*
		 * 5.2.  Bundle Transmission
		 *  The steps in processing a bundle transmission request are:		
		 *  Step 1:   If custody transfer is requested for this bundle
		 *     transmission and, moreover, custody acceptance by the source node
		 *     is required, then either the bundle protocol agent must commit to
		 *     accepting custody of the bundle -- in which case processing
		 *     proceeds from Step 2 -- or the request cannot be honored and all
		 *     remaining steps of this procedure must be skipped.  The bundle
		 *     protocol agent must not commit to accepting custody of a bundle if
		 *     the conditions under which custody of the bundle may be accepted
		 *     are not satisfied.  The conditions under which a node may accept
		 *     custody of a bundle whose destination is not a singleton endpoint
		 *     are not defined in this specification.
		 */
		if (bundle.getPrimaryBundleBlock().isCustodyTransferRequested()) {
			// Check for ability to accept custody of Bundle already done above.
			// Declare ourselves the custodian of the Block
			bundle.getPrimaryBundleBlock().setCustodianEndPointId(
					BPManagement.getInstance().getEndPointIdStem());
			bundle.addRetentionConstraint(Bundle.RETENTION_CONSTRAINT_CUSTODY_ACCEPTED);
		}
		
		/*
		 *  Step 2:   Transmission of the bundle is initiated.  An outbound
		 *     bundle must be created per the parameters of the bundle
		 *     transmission request, with current custodian endpoint ID set to
		 *     the null endpoint ID "dtn:none" and with the retention constraint
		 *     "Dispatch pending".  The source endpoint ID of the bundle must be
		 *     either the ID of an endpoint of which the node is a member or the
		 *     null endpoint ID "dtn:none".
		 */
		bundle.addRetentionConstraint(Bundle.RETENTION_CONSTRAINT_DISPATCH_PENDING);
		BundleDatabase.getInstance().updateRetentionConstraint(bundle);
		
		/*
		 *  Step 3:   Processing proceeds from Step 1 of Section 5.4.
		 */
		try {
			// We modify the forwarding procedure slightly.  In case of Bundles
			// originating from us, we don't need to send Custody Signal.
			forwardFromUs(bundle);
		} catch (JDtnException e) {
			if (GeneralManagement.isDebugLogging()) {
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.log(Level.FINEST, "Exception trying to forward Bundle", e);
					_logger.finest(bundle.dump("", true));
				}
			}
			discardBundleUnconditionally(bundle);
			throw new JDtnException(e);
		}
	}
	
	/**
	 * Modified Forwarding Procedure for Bundles originating from us.  We
	 * don't need to send Custody Signals in this case.
	 * @param bundle Bundle (originating from us) that needs to be forwarded
	 * @throws JDtnException on errors
	 * @throws InterruptedException interrupted from Wait
	 * @throws SQLException on database errors
	 */
	protected void forwardFromUs(Bundle bundle)
	throws JDtnException, InterruptedException, SQLException
	{
		/*
		 *	5.4.  Bundle Forwarding			
		 *	   Step 1:   The retention constraint "Forward pending" must be added to
		 *	      the bundle, and the bundle's "Dispatch pending" retention
		 *	      constraint must be removed.
		 */
		bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_DISPATCH_PENDING);
		bundle.addRetentionConstraint(Bundle.RETENTION_CONSTRAINT_FORWARD_PENDING);
		BundleDatabase.getInstance().updateRetentionConstraint(bundle);

		/*
		 *  Step 2:   The bundle protocol agent must determine whether or not
		 *     forwarding is contraindicated for any of the reasons listed in
		 *     Figure 12.  In particular:
		 *       The bundle protocol agent must determine which endpoint(s) to
		 *        forward the bundle to.  The bundle protocol agent may choose
		 *        either to forward the bundle directly to its destination
		 *        endpoint (if possible) or to forward the bundle to some other
		 *        endpoint(s) for further forwarding.  The manner in which this
		 *        decision is made may depend on the scheme name in the
		 *        destination endpoint ID but in any case is beyond the scope of
		 *        this document.  If the agent finds it impossible to select any
		 *        endpoint(s) to forward the bundle to, then forwarding is
		 *        contraindicated.
         *       Provided the bundle protocol agent succeeded in selecting the
		 *	     endpoint(s) to forward the bundle to, the bundle protocol agent
		 *	     must select the convergence layer adapter(s) whose services
		 *	     will enable the node to send the bundle to the nodes of the
		 *	     minimum reception group of each selected endpoint.  The manner
		 *	     in which the appropriate convergence layer adapters are
		 *	     selected may depend on the scheme name in the destination
		 *	     endpoint ID but in any case is beyond the scope of this
		 *	     document.  If the agent finds it impossible to select
		 *	     convergence layer adapters to use in forwarding this bundle,
		 *	     then forwarding is contraindicated.
		 */
		Route route =
			RouterManager.getInstance().findRoute(bundle);
		if (route == null) {
			// No route to forward Bundle.  Policy decision: do we hold the
			// Bundle, hoping a route will become available before the Bundle
			// expires?  Or do we discard the bundle?
			if (BPManagement.getInstance().isHoldBundleIfNoRoute()) {
				BundleHolder.getInstance().addBundle(bundle, BP_PROTOCOOL_AGENT_LINK_HOLD_MSECS, this);
				BundleDatabase.getInstance().bundleHeld(bundle);
				return;
				
			} else {
				/*
				 *  Step 3:   If forwarding of the bundle is determined to be
				 *     contraindicated for any of the reasons listed in Figure 12, then
				 *     the Forwarding Contraindicated procedure defined in Section 5.4.1
				 *     must be followed; the remaining steps of Section 5 are skipped at
				 *     this time.
				 */
				_logger.severe("No route for destination " +
						bundle.getPrimaryBundleBlock().getDestinationEndPointId());
				bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_FORWARD_PENDING);
				BundleDatabase.getInstance().updateRetentionConstraint(bundle);
				discardBundleIfNoLongerConstrained(bundle);
				BpApi.getInstance().onBundleTransmissionCancelled(
						bundle, 
						CancelSegment.REASON_CODE_UNREACHABLE);
				return;
			}
		}
		
		// Now that we have a Route, update previously unknown values in
		// Bundle Database
		BundleDatabase.getInstance().updateLink(bundle, route.getLink());
		BundleDatabase.getInstance().updateEidScheme(bundle, route.getNeighbor().getEidScheme());

		/*
		 *  Step 5:   For each endpoint selected for forwarding, the bundle
		 *     protocol agent must invoke the services of the selected
		 *     convergence layer adapter(s) in order to effect the sending of the
		 *     bundle to the nodes constituting the minimum reception group of
		 *     that endpoint.  Determining the time at which the bundle is to be
		 *     sent by each convergence layer adapter is an implementation
		 *     matter.
		 *     To keep from possibly invalidating bundle security, the sequencing
		 *     of the blocks in a forwarded bundle must not be changed as it
		 *     transits a node; received blocks must be transmitted in the same
		 *     relative order as that in which they were received.  While blocks
		 *     may be added to bundles as they transit intermediate nodes,
		 *     removal of blocks that do not have their 'Discard block if it
		 *     can't be processed' flag in the block processing control flags set
		 *     to 1 may cause security to fail.
		 */
		if (route instanceof DelayedRoute) {
			// Router is asking to delay transmission of the Bundle
			DelayedRoute delayedRoute = (DelayedRoute)route;
			BundleHolder.getInstance().addBundle(bundle, delayedRoute.getDelay(), this);
			BundleDatabase.getInstance().bundleHeld(bundle);
			return;
			
		} else if (!route.getLink().isLinkOperational() ||
				   !route.getNeighbor().isNeighborOperational()) {
			// Outbound Link or Neighbor is not up; try again later
			BundleHolder.getInstance().addBundle(bundle, BP_PROTOCOOL_AGENT_LINK_HOLD_MSECS, this);
			BundleDatabase.getInstance().bundleHeld(bundle);
			return;
		}
		
		BPManagement.getInstance()._bpStats.nDataBundlesFwded++;
		BundleDatabase.getInstance().bundleForwardEnqueued(bundle);
		ConvergenceLayerMux.getInstance().transmitBundle(
				bundle, 
				route, 
				bundle.getBundleOptions().blockColor);
	}
	
	/**
	 * Bundle Forwarding Procedure.  Attempt to forward the Bundle to next hop
	 * or to destination.
	 * @param bundle Bundle to forward
	 * @throws JDtnException on errors
	 * @throws InterruptedException on interrupt during wait
	 * @throws JDtnException on errors
	 * @throws SQLException on Bundle database errors
	 */
	protected void forwardBundle(Bundle bundle) 
	throws InterruptedException, JDtnException, SQLException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("forwardBundle");
		}
		
		/*
		 *	5.4.  Bundle Forwarding			
		 *	   Step 1:   The retention constraint "Forward pending" must be added to
		 *	      the bundle, and the bundle's "Dispatch pending" retention
		 *	      constraint must be removed.
		 */
		bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_DISPATCH_PENDING);
		bundle.addRetentionConstraint(Bundle.RETENTION_CONSTRAINT_FORWARD_PENDING);
		BundleDatabase.getInstance().updateRetentionConstraint(bundle);
		
		/*
		 *	4.6.  Extension Blocks
		 *	...
		 *	   Whenever a bundle is forwarded that contains one or more extension
		 *	   blocks that could not be processed, the "Block was forwarded without
		 *	   being processed" flag must be set to 1 within the block processing
		 *	   flags of each such block.  For each block flagged in this way, the
		 *	   flag may optionally be cleared (i.e., set to zero) by another node
		 *	   that subsequently receives the bundle and is able to process that
		 *	   block; the specifications defining the various extension blocks are
		 *	   expected to define the circumstances under which this flag may be
		 *	   cleared, if any.
		 */
		for (BundleBlock bundleBlock : bundle) {
			if (bundleBlock instanceof SecondaryBundleBlock) {
				SecondaryBundleBlock secondaryBundleBlock = (SecondaryBundleBlock)bundleBlock;
				if (secondaryBundleBlock.getBlockType() != SecondaryBundleBlock.BLOCK_TYPE_PAYLOAD) {
					secondaryBundleBlock.setForwardedWithoutProcessing(true);
					BundleDatabase.getInstance().updateBundleData(bundle);
				}
			}
		}
		
		/*
		 *  Step 2:   The bundle protocol agent must determine whether or not
		 *     forwarding is contraindicated for any of the reasons listed in
		 *     Figure 12.  In particular:
		 *       The bundle protocol agent must determine which endpoint(s) to
		 *        forward the bundle to.  The bundle protocol agent may choose
		 *        either to forward the bundle directly to its destination
		 *        endpoint (if possible) or to forward the bundle to some other
		 *        endpoint(s) for further forwarding.  The manner in which this
		 *        decision is made may depend on the scheme name in the
		 *        destination endpoint ID but in any case is beyond the scope of
		 *        this document.  If the agent finds it impossible to select any
		 *        endpoint(s) to forward the bundle to, then forwarding is
		 *        contraindicated.
         *       Provided the bundle protocol agent succeeded in selecting the
		 *	     endpoint(s) to forward the bundle to, the bundle protocol agent
		 *	     must select the convergence layer adapter(s) whose services
		 *	     will enable the node to send the bundle to the nodes of the
		 *	     minimum reception group of each selected endpoint.  The manner
		 *	     in which the appropriate convergence layer adapters are
		 *	     selected may depend on the scheme name in the destination
		 *	     endpoint ID but in any case is beyond the scope of this
		 *	     document.  If the agent finds it impossible to select
		 *	     convergence layer adapters to use in forwarding this bundle,
		 *	     then forwarding is contraindicated.
		 */
		Route route =
			RouterManager.getInstance().findRoute(bundle);
		if (route == null) {
			// No route to forward Bundle.  Policy decision: do we hold the
			// Bundle, hoping a route will become available before the Bundle
			// expires?  Or do we discard the bundle?
			if (BPManagement.getInstance().isHoldBundleIfNoRoute()) {
				BundleHolder.getInstance().addBundle(bundle, BP_PROTOCOOL_AGENT_LINK_HOLD_MSECS, this);
				BundleDatabase.getInstance().bundleHeld(bundle);
				return;
				
			} else {
				/*
				 *  Step 3:   If forwarding of the bundle is determined to be
				 *     contraindicated for any of the reasons listed in Figure 12, then
				 *     the Forwarding Contraindicated procedure defined in Section 5.4.1
				 *     must be followed; the remaining steps of Section 5 are skipped at
				 *     this time.
				 */
				_logger.severe("No route for destination " +
						bundle.getPrimaryBundleBlock().getDestinationEndPointId());
				bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_FORWARD_PENDING);
				BundleDatabase.getInstance().updateRetentionConstraint(bundle);
				BpApi.getInstance().onBundleTransmissionCancelled(
						bundle, 
						CancelSegment.REASON_CODE_UNREACHABLE);
				forwardingContraindicated(bundle, CustodySignal.REASON_NO_ROUTE);
				return;
			}
		}

		
		// Now that we have a Route, update previously unknown values in
		// Bundle Database
		BundleDatabase.getInstance().updateLink(bundle, route.getLink());
		BundleDatabase.getInstance().updateEidScheme(bundle, route.getNeighbor().getEidScheme());

		/*
		 * Step 4:   If the bundle's custody transfer requested flag (in the
		 *     bundle processing flags field) is set to 1, then the custody
		 *     transfer procedure defined in Section 5.10.2 must be followed.
		 * NOTE: This must be a typo in the spec.  5.10.2 is custodyRelease
		 * procedure.  CustodyRelease is not appropriate when we are forwarding
		 * the bundle; we want to retain it until next custodian accepts.
		 * I think the the appropriate procedure is 5.10.1, custodyAcceptance
		 */
		if (bundle.getPrimaryBundleBlock().isCustodyTransferRequested()) {
			try {
				custodyAcceptance(bundle, route);
			} catch (Exception e) {
				_logger.log(Level.SEVERE, "Trying to accept custody of bundle", e);
				bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_CUSTODY_ACCEPTED);
				bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_FORWARD_PENDING);
				BundleDatabase.getInstance().updateRetentionConstraint(bundle);
				try {
					deleteBundle(bundle, BundleStatusReport.REASON_CODE_NO_ADDL_INFO);
				} catch (Exception e1) {
					_logger.log(Level.SEVERE, "Deleting bundle after failure to accept custody", e);
					discardBundleUnconditionally(bundle);
				}
				return;
			}
		}
		
		/*
		 *  Step 5:   For each endpoint selected for forwarding, the bundle
		 *     protocol agent must invoke the services of the selected
		 *     convergence layer adapter(s) in order to effect the sending of the
		 *     bundle to the nodes constituting the minimum reception group of
		 *     that endpoint.  Determining the time at which the bundle is to be
		 *     sent by each convergence layer adapter is an implementation
		 *     matter.
		 *     To keep from possibly invalidating bundle security, the sequencing
		 *     of the blocks in a forwarded bundle must not be changed as it
		 *     transits a node; received blocks must be transmitted in the same
		 *     relative order as that in which they were received.  While blocks
		 *     may be added to bundles as they transit intermediate nodes,
		 *     removal of blocks that do not have their 'Discard block if it
		 *     can't be processed' flag in the block processing control flags set
		 *     to 1 may cause security to fail.
		 */
		if (route instanceof DelayedRoute) {
			// Router is asking us to delay transmission of Bundle
			DelayedRoute delayedRoute = (DelayedRoute)route;
			BundleHolder.getInstance().addBundle(bundle, delayedRoute.getDelay(), this);
			BundleDatabase.getInstance().bundleHeld(bundle);
			return;
			
		} else if (!route.getLink().isLinkOperational() ||
				   !route.getNeighbor().isNeighborOperational()) {
			// Outbound Link is not up; try again later
			BundleHolder.getInstance().addBundle(bundle, BP_PROTOCOOL_AGENT_LINK_HOLD_MSECS, this);
			BundleDatabase.getInstance().bundleHeld(bundle);
			return;
		}
		try {

			BPManagement.getInstance()._bpStats.nDataBundlesFwded++;
			BundleDatabase.getInstance().bundleForwardEnqueued(bundle);
			ConvergenceLayerMux.getInstance().transmitBundle(
					bundle, 
					route, 
					BPManagement.getInstance().getBlockColor(
							bundle.getPrimaryBundleBlock().getClassOfServicePriority()));
		} catch (Exception e) {
			_logger.log(Level.SEVERE, "Forwarding Bundle", e);
			bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_CUSTODY_ACCEPTED);
			bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_FORWARD_PENDING);
			BundleDatabase.getInstance().updateRetentionConstraint(bundle);
			try {
				deleteBundle(bundle, BundleStatusReport.REASON_CODE_NO_ADDL_INFO);
			} catch (Exception e1) {
				_logger.log(Level.SEVERE, "Deleting bundle after failure to forward it", e);
				discardBundleUnconditionally(bundle);
			}
		}
		
	}
	
	/**
	 * Forwarding Contraindicated Procedure.  Take actions when we cannot
	 * forward Bundle.
	 * @param bundle Bundle we were attempting to Forward
	 * @param reason Reason for contraindication; one of CustodySignal.REASON_*
	 * @throws JDtnException on errors
	 * @throws InterruptedException on interrupt during a wait
	 * @throws SQLException on Bundle Database errors
	 */
	private void forwardingContraindicated(Bundle bundle, int reason) 
	throws JDtnException, InterruptedException, SQLException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("forwardingContraindicated");
		}

		/*
		 *	5.4.1.  Forwarding Contraindicated
		 *	The steps in responding to contraindication of forwarding for some
		 *	   reason are:			
		 *	   Step 1:   The bundle protocol agent must determine whether or not to
		 *	      declare failure in forwarding the bundle for this reason.  Note:
		 *	      this decision is likely to be influenced by the reason for which
		 *	      forwarding is contraindicated.
		 *	   Step 2:   If forwarding failure is declared, then the Forwarding
		 *	      Failed procedure defined in Section 5.4.2 must be followed.
		 *	      Otherwise, (a) if the bundle's custody transfer requested flag (in
		 *	      the bundle processing flags field) is set to 1, then the custody
		 *	      transfer procedure defined in Section 5.10 must be followed; (b)
		 *	      when -- at some future time - the forwarding of this bundle ceases
		 *	      to be contraindicated, processing proceeds from Step 5 of
		 *	      Section 5.4.
		 * NOTE: we unconditionally decide forwarding failure.
		 */
		forwardingFailed(bundle, reason);
	}
	
	/**
	 * Forwarding Failed Procedure.  Take actions when we cannot forward
	 * Bundle.
	 * @param bundle Bundle we are attempting for Forward
	 * @param reason Reason for failure; one of CustodySignal.REASON_*
	 * @throws JDtnException on errors
	 * @throws InterruptedException on interrupt during a wait
	 * @throws SQLException on database errors
	 */
	private void forwardingFailed(Bundle bundle, int reason)
	throws JDtnException, InterruptedException, SQLException {
		_logger.warning("Bundle Forwarding failed: " +
				CustodySignal.reasonCodeToString(reason));
		_logger.warning(bundle.getExtendedBundleId().dump("", true));
		
		/*
		 *	5.4.2.  Forwarding Failed
		 *	   The steps in responding to a declaration of forwarding failure for
		 *	   some reason are:
		 *	   Step 1:   If the bundle's custody transfer requested flag (in the
		 *	      bundle processing flags field) is set to 1, custody transfer
		 *	      failure must be handled.  Procedures for handling failure of
		 *	      custody transfer for a bundle whose destination is not a singleton
		 *	      endpoint are not defined in this specification.  For a bundle
		 *	      whose destination is a singleton endpoint, the bundle protocol
		 *	      agent must handle the custody transfer failure by generating a
		 *	      "Failed" custody signal for the bundle, destined for the bundle's
		 *	      current custodian; the custody signal must contain a reason code
		 *	      corresponding to the reason for which forwarding was determined to
		 *	      be contraindicated.  (Note that discarding the bundle will not
		 *	      delete it from the network, since the current custodian still has
		 *	      a copy.)
		 */
		if (bundle.getPrimaryBundleBlock().isCustodyTransferRequested()) {
			try {
				sendCustodySignal(bundle, false, reason);
			} catch (Exception e) {
				_logger.warning("Attempt to send 'Custody Transfer Failed Signal' Failed");
				_logger.warning(e.getMessage());
			}
		}
		
		/*
		 *	   Step 2:   If the bundle's destination endpoint is an endpoint of
		 *	      which the node is a member, then the bundle's "Forward pending"
		 *	      retention constraint must be removed.  Otherwise, the bundle must
		 *	      be deleted: the bundle deletion procedure defined in Section 5.13
		 *	      must be followed, citing the reason for which forwarding was
		 *	      determined to be contraindicated.
		 */
		EndPointId destEid = 
			bundle.getPrimaryBundleBlock().getDestinationEndPointId();
		if (BpApi.getInstance().findRegistrationForEndPointId(destEid) != null) {
			bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_FORWARD_PENDING);
			BundleDatabase.getInstance().updateRetentionConstraint(bundle);
			discardBundleIfNoLongerConstrained(bundle);
		} else {
			deleteBundle(bundle, reason);
		}
	}
	
	/**
	 * Process call from adaptation layer when Bundle successfully transmitted.
	 * @param bundle Bundle successfully transmitted
	 * @throws InterruptedException If interrupted
	 * @throws JDtnException On JDTN specific errors
	 * @throws SQLException On Database errors
	 */
	private void processBundleTransmitted(Bundle bundle) 
	throws SQLException, JDtnException, InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("processBundleTransmitted");
		}
		
		// We don't need to do anything for Administrative Records sent to us
		// Otherwise, fall thru
		if (bundle.isAdminRecord()) {
			String ourHost = BPManagement.getInstance().getEndPointIdStem().getHostNodeName();
			String destHost = bundle.getPrimaryBundleBlock().getDestinationEndPointId().getHostNodeName();
			if (ourHost.equals(destHost)) {
				return;
			}
		}
		
		BundleDatabase.getInstance().bundleInCustody(bundle);
		
		// actions to perform on successful transmission of Bundle being forwarded
		if (bundle.isRetentionConstraint(Bundle.RETENTION_CONSTRAINT_FORWARD_PENDING)) {

			/*
			 *	5.4.  Bundle Forwarding
			 *  Step 6:   When all selected convergence layer adapters have informed
			 *     the bundle protocol agent that they have concluded their data
			 *     sending procedures with regard to this bundle:		
			 *     *  If the "request reporting of bundle forwarding" flag in the
			 *        bundle's status report request field is set to 1, then a bundle
			 *        forwarding status report should be generated, destined for the
			 *        bundle's report-to endpoint ID.  If the bundle has the
			 *        retention constraint "custody accepted" and all of the nodes in
			 *        the minimum reception group of the endpoint selected for
			 *        forwarding are known to be unable to send bundles back to this
			 *        node, then the reason code on this bundle forwarding status
			 *        report must be "forwarded over unidirectional link"; otherwise,
			 *        the reason code must be "no additional information".
			 */
			if (bundle.getPrimaryBundleBlock().isReportBundleForwarding()) {
				try {
					sendBundleStatusReport(
							bundle, 
							BundleStatusReport.STATUS_FLAG_BUNDLE_FORWARDED, 
							BundleStatusReport.REASON_CODE_NO_ADDL_INFO);
				} catch (JDtnException e) {
					_logger.log(Level.SEVERE, "sending Report Bundle Forwarded", e);
				} catch (InterruptedException e) {
					_logger.log(Level.SEVERE, "sending Report Bundle Forwarded", e);
				}
			}
			/*
			 *     *  The bundle's "Forward pending" retention constraint must be
			 *        removed.
			 */
			bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_FORWARD_PENDING);
			BundleDatabase.getInstance().updateRetentionConstraint(bundle);
			discardBundleIfNoLongerConstrained(bundle);
		}
	}
	
	/**
	 * Callback from convergence layer to report that Bundle transmission failed.
	 * We hold the Bundle and try again later.
	 * @param bundle Bundle whose transmission failed
	 * @throws InterruptedException If interrupted
	 * @throws JDtnException On JDTN Specific errors
	 * @throws SQLException On Bundle Database errors
	 */
	private void processBundleTransmitFailed(Bundle bundle) 
	throws SQLException, JDtnException, InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("processBundleTransmitFailed()");
			_logger.finer(bundle.getBundleId().dump("", false));
		}
		BundleHolder.getInstance().addBundle(bundle, BP_PROTOCOOL_AGENT_LINK_HOLD_MSECS, this);	
		BundleDatabase.getInstance().bundleHeld(bundle);
	}
	
	/**
	 * Start the Bundle Timer for the given Bundle
	 * @param bundle Given Bundle
	 */
	private void startBundleTimer(final Bundle bundle) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("startBundleTimer");
		}
		bundle.setBundleTimerTask(
			new TimerTask() {
				@Override
				public void run() {
					BundleTimerExpiredEvent event =
						new BundleTimerExpiredEvent(bundle);
					try {
						processEvent(event);
					} catch (InterruptedException e) {
						if (GeneralManagement.isDebugLogging()) {
							_logger.fine("Interrupted processing BundleTimer expiration");
						}
					}
				}
			}
		);
		_timer.schedule(bundle.getBundleTimerTask(), bundle.getExpirationDate());
	}
	
	/**
	 * Stop the Bundle Timer for the given Bundle
	 * @param bundle Given Bundle
	 */
	private void stopBundleTimer(Bundle bundle) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("stopBundleTimer");
		}
		if (bundle.getBundleTimerTask() != null) {
			bundle.getBundleTimerTask().cancel();
			_timer.purge();
			bundle.setBundleTimerTask(null);
		}
	}
	
	/**
	 * Called from Timer when Bundle Timer expires.  Bundle Expiration procedure.
	 * @param bundle Bundle which has expired
	 * @throws InterruptedException on interrupted during wait
	 * @throws JDtnException On JDTN Specific error
	 * @throws SQLException On Bundle database error
	 */
	private void processBundleTimerExpired(Bundle bundle) 
	throws InterruptedException, SQLException, JDtnException {
		_logger.warning("Bundle Lifetime Expired");
		_logger.warning(bundle.getExtendedBundleId().dump("", true));
		BPManagement.getInstance()._bpStats.nBundlesExpired++;
		
		/*
		 *	5.5.  Bundle Expiration
		 *	   A bundle expires when the current time is greater than the bundle's
		 *	   creation time plus its lifetime as specified in the primary bundle
		 *	   block.  Bundle expiration may occur at any point in the processing of
		 *	   a bundle.  When a bundle expires, the bundle protocol agent must
		 *	   delete the bundle for the reason "lifetime expired": the bundle
		 *	   deletion procedure defined in Section 5.13 must be followed.
		 */
		try {
			deleteBundle(bundle, BundleStatusReport.REASON_CODE_EXPIRED);
		} catch (JDtnException e) {
			_logger.log(Level.SEVERE, "deleting bundle after Bundle Timer expiration", e);
			discardBundleUnconditionally(bundle);
		}
		
		// Cancel Bundle Transmission
		try {
			BpLtpAdapter.getInstance().cancelBundleTransmit(bundle);
		} catch (LtpException e) {
			_logger.log(
					Level.SEVERE, 
					"Attempting to cancel transmission after Bundle Timer Expiration", 
					e);
		}
		
		// Notify applications
		BpApi.getInstance().onBundleExpired(bundle.getBundleId());
	}
	
	/**
	 * Start the Custody Transfer Timer for the given Bundle.  We are awaiting
	 * a Custody Signal on this Bundle from the next hop, and we want to timeout
	 * if we don't get it.
	 * @param bundle Bundle whose timer is expiring.
	 * @param route The Route on which this Bundle was forwarded
	 */
	private void startCustodyXferTimer(final Bundle bundle, final Route route) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("startCustodyXferTimer");
		}
		
		bundle.setCustodyTransferTimerTask(
			new TimerTask() {
				@Override
				public void run() {
					BundleCustodyTransferTimerExpiredEvent event =
						new BundleCustodyTransferTimerExpiredEvent(bundle);
					try {
						processEvent(event);
					} catch (InterruptedException e) {
						_logger.severe("Interrupted");
					}
				}
			}
		);
		_timer.schedule(bundle.getCustodyTransferTimerTask(), route.getRoundTripTime());
		
	}
	
	/**
	 * Stop the Custody Transfer Timer for the given Bundle.
	 * @param bundle Given Bundle
	 */
	private void stopCustodyXferTimer(Bundle bundle) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("stopCustodyXferTimer");
		}
		if (bundle.getCustodyTransferTimerTask() != null) {
			bundle.getCustodyTransferTimerTask().cancel();
			_timer.purge();
			bundle.setCustodyTransferTimerTask(null);
		}
	}
	
	/**
	 * Called when Custody Transfer Timer expires.  The Bundle is awaiting
	 * a Custody Signal asserting that custody has been accepted, but we got
	 * a timeout on that.
	 * @param bundle Given Bundle
	 * @throws InterruptedException on interrupt during wait
	 * @throws JDtnException On JDTN Specific error
	 * @throws SQLException On Bundle database error
	 */
	private void processCustodyXferTimerExpired(Bundle bundle)
	throws InterruptedException, SQLException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onCustodyXferTimerExpired");
		}
		BPManagement.getInstance()._bpStats.nCustodySignalsExpired++;
		
		/*
		 *	5.12.  Custody Transfer Failure
		 *	   Procedures for determining custody transfer failure for a bundle
		 *	   whose destination is not a singleton endpoint are not defined in this
		 *	   specification.  Custody transfer for a bundle whose destination is a
		 *	   singleton endpoint is determined to have failed at a custodial node
		 *	   for that bundle when either (a) that node's custody transfer timer
		 *	   for that bundle (if any) expires or (b) a "Failed" custody signal for
		 *	   that bundle is received at that node.
		 */
		if (bundle.isRetentionConstraint(Bundle.RETENTION_CONSTRAINT_CUSTODY_ACCEPTED)) {
			try {
				custodyTransferFailure(bundle);
				
			} catch (JDtnException e) {
				_logger.log(
						Level.SEVERE, 
						"Attempting Custody Transfer Failure procedure after Custody Xfer Timer expired", 
						e);
				bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_CUSTODY_ACCEPTED);
				BundleDatabase.getInstance().updateRetentionConstraint(bundle);
				if (bundle.getRetentionConstraint() == 0) {
					try {
						deleteBundle(bundle, BundleStatusReport.REASON_CODE_NO_ADDL_INFO);
					} catch (JDtnException e1) {
						discardBundleUnconditionally(bundle);
					}
				}
			}
		}
	}
	
	/**
	 * Discard given Bundle if all retention constraints have been removed
	 * @param bundle Given Bundle
	 * @throws JDtnException On JDTN Specific error
	 * @throws SQLException On Bundle database error
	 */
	protected void discardBundleIfNoLongerConstrained(Bundle bundle) 
	throws SQLException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			if (_logger.isLoggable(Level.FINER)) {
				_logger.fine("discardBundleIfNoLongerConstrained(retentionConstraints=" + 
						Bundle.retentionConstraintsToString(
								bundle.getRetentionConstraint()) + ")\n");
			}
		}
		if (bundle.getRetentionConstraint() == 0) {
			/*
			 *	5.14.  Discarding a Bundle
			 *	   As soon as a bundle has no remaining retention constraints it may be
			 *	   discarded.
			 *	3.1.  Definitions
			 *	   Deletion, Discarding -  A bundle protocol agent "discards" a bundle
			 *	      by simply ceasing all operations on the bundle and functionally
			 *	      erasing all references to it; the specific procedures by which
			 *	      this is accomplished are an implementation matter.  Bundles are
			 *	      discarded silently; i.e., the discarding of a bundle does not
			 *	      result in generation of an administrative record.  "Retention
			 *	      constraints" are elements of the bundle state that prevent a
			 *	      bundle from being discarded; a bundle cannot be discarded while it
			 *	      has any retention constraints.  A bundle protocol agent "deletes"
			 *	      a bundle in response to some anomalous condition by notifying the
			 *	      bundle's report-to endpoint of the deletion (provided such
			 *	      notification is warranted; see Section 5.13 for details) and then
			 *	      arbitrarily removing all of the bundle's retention constraints,
			 *	      enabling the bundle to be discarded.
			 */
			discardBundleUnconditionally(bundle);
		}
	}

	/**
	 * Unconditionally discard given Bundle.  We:
	 * <ul>
	 *   <li>Stop all Bundle timers
	 *   <li>Close the Bundle
	 *   <li>Remove Bundle from retained list.
	 * </ul>
	 * and removed.
	 * @param bundle
	 * @throws JDtnException On JDTN specific error
	 * @throws SQLException On Bundle Database error
	 */
	private void discardBundleUnconditionally(Bundle bundle) 
	throws SQLException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("discardBundleUnconditionally");
		}
		long nBytes = bundle.getPayloadBundleBlock().getPayload().getLength();
		stopBundleTimer(bundle);
		stopCustodyXferTimer(bundle);
		bundle.close();
		if (isBundleRetained(bundle)) {
			BPManagement.getInstance()._bpStats.nRetainedBytes -= nBytes;
			removeRetainedBundle(bundle);
			BundleDatabase.getInstance().bundleDeleted(bundle);
		}
	}
	
	/**
	 * Delete Bundle Procedure.  We do a little reporting first and then
	 * discard Bundle.
	 * @param bundle Given Bundle
	 * @param reason One of BundleStatusReport.REASON_CODE_*
	 * @throws BPException on errors
	 * @throws JDtnException On JDTN Specific error
	 * @throws SQLException On Bundle database error
	 * @throws InterruptedException If Interrupted during wait
	 */
	private void deleteBundle(Bundle bundle, int reason)
	throws JDtnException, InterruptedException, SQLException {
		if (GeneralManagement.isDebugLogging()) {
			if (_logger.isLoggable(Level.FINER)) {
				_logger.fine("deleteBundle(retentionConstraints=" + 
						Bundle.retentionConstraintsToString(
								bundle.getRetentionConstraint()) + ")\n");
			}
		}
		
		// If the bundle is currently being delay held, then release it from 
		//   delay hold.
		if (bundle.isRetentionConstraint(Bundle.RETENTION_CONSTRAINT_DELAY_HOLD)) {
			BundleHolder.getInstance().removeBundle(bundle);
			BundleDatabase.getInstance().bundleInCustody(bundle);
		}
		
		/*
		 *	5.13.  Bundle Deletion
		 *	   The steps in deleting a bundle are:
		 *	   Step 1:   If the retention constraint "Custody accepted" currently
		 *	      prevents this bundle from being discarded, and the destination of
		 *	      the bundle is a singleton endpoint, then:
		 *	      *  Custody of the node is released as described in Section 5.10.2.
		 */
		if (bundle.isRetentionConstraint(Bundle.RETENTION_CONSTRAINT_CUSTODY_ACCEPTED)) {
			
			custodyRelease(bundle);
			/*	      *  A bundle deletion status report citing the reason for deletion
			 *	         must be generated, destined for the bundle's report-to endpoint
			 *	         ID.
			 */
			if (!bundle.getPrimaryBundleBlock().getReportToEndPointId().equals(EndPointId.getDefaultEndpointId())) {
				try {
					sendBundleStatusReport(bundle, BundleStatusReport.STATUS_FLAG_BUNDLE_DELETED, reason);
				} catch (Exception e) {
					_logger.warning("Trying to send Deleted Report");
					_logger.warning(e.getMessage());
				}
			}

		} else {
		
			/*	      Otherwise, if the "request reporting of bundle deletion" flag in
			 *	      the bundle's status report request field is set to 1, then a
			 *	      bundle deletion status report citing the reason for deletion
			 *	      should be generated, destined for the bundle's report-to endpoint
			 *	      ID.
			 */
			if (bundle.getPrimaryBundleBlock().isReportBundleDeletion()) {
				try {
					sendBundleStatusReport(bundle, BundleStatusReport.STATUS_FLAG_BUNDLE_DELETED, reason);
				} catch (Exception e) {
					_logger.warning("Trying to send Deleted Report");
					_logger.warning(e.getMessage());
				}
			}
		}
		/*
		 *	   Step 2:   All of the bundle's retention constraints must be removed.
		 */
		bundle.setRetentionConstraint(0);
		discardBundleIfNoLongerConstrained(bundle);
	}
	
	/**
	 * Custody acceptance procedure.  We have accepted custody of a Bundle.
	 * We have forwarded it to next hop or to destination.  We are awaiting
	 * a Custody Acceptance from them.
	 * We:
	 * <ul>
	 *   <li> Flag Bundle in our custody
	 *   <li> Send a Custody Acceptance report to ReportTo if asked for.
	 *   <li> Send a Custody Acceptance Signal to Custodian
	 *   <li> Reflag ourselves as the custodian.
	 *   <li> Start Custody Transfer Timer
	 * </ul>
	 * @param bundle Bundle for which we have accepted custody
	 * @param route Route to next hop
	 * @throws InterruptedException if interrupted during wait
	 * @throws JDtnException On JDTN Specific error
	 * @throws SQLException On Bundle database error
	 */
	private void custodyAcceptance(Bundle bundle, Route route) 
	throws InterruptedException, JDtnException, SQLException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("custodyAcceptance");
		}
		/*
		 * 5.10.1.  Custody Acceptance
		 *  Procedures for acceptance of custody of a bundle whose destination is
		 *  not a singleton endpoint are not defined in this specification.
		 *  Procedures for acceptance of custody of a bundle whose destination is
		 *  a singleton endpoint are defined as follows.
		 *  The retention constraint "Custody accepted" must be added to the
		 *  bundle.
		 */
		bundle.addRetentionConstraint(Bundle.RETENTION_CONSTRAINT_CUSTODY_ACCEPTED);
		BundleDatabase.getInstance().updateRetentionConstraint(bundle);
		
		/*  If the "request reporting of custody acceptance" flag in the bundle's
		 *  status report request field is set to 1, a custody acceptance status
		 *  report should be generated, destined for the report-to endpoint ID of
		 *  the bundle.  However, if a bundle reception status report was
		 *  generated for this bundle (Step 1 of Section 5.6), then this report
		 *  should be generated by simply turning on the "Reporting node accepted
		 *  custody of bundle" flag in that earlier report's status flags byte.
		 *  The bundle protocol agent must generate a "Succeeded" custody signal
		 *  for the bundle, destined for the bundle's current custodian.
		 */
		if (bundle.getPrimaryBundleBlock().isReportCustodyAcceptance()) {
			try {
				sendBundleStatusReport(bundle, 
						BundleStatusReport.STATUS_FLAG_CUSTODY_XFERRED, 
						BundleStatusReport.REASON_CODE_NO_ADDL_INFO);
			} catch (JDtnException e) {
				_logger.log(
					Level.SEVERE,
					"Sending Custody Transferred Report",
					e);
				// Try to soldier on
			}			
		}
		
		sendCustodySignal(bundle, true, CustodySignal.REASON_NO_FURTHER_INFO);
		
		/*  The bundle protocol agent must assert the new current custodian for
		 *  the bundle.  It does so by changing the current custodian endpoint ID
		 *  in the bundle's primary block to the endpoint ID of one of the
		 *  singleton endpoints in which the node is registered.  This may entail
		 *  appending that endpoint ID's null-terminated scheme name and SSP to
		 *  the dictionary byte array in the bundle's primary block, and in some
		 *  case it may also enable the (optional) removal of the current
		 *  custodian endpoint ID's scheme name and/or SSP from the dictionary.
		 */
		bundle.getPrimaryBundleBlock().setCustodianEndPointId(
				BPManagement.getInstance().getEndPointIdStem());
		BundleDatabase.getInstance().updateBundleData(bundle);
		
		/*  The bundle protocol agent may set a custody transfer countdown timer
		 *  for this bundle; upon expiration of this timer prior to expiration of
		 *  the bundle itself and prior to custody transfer success for this
		 *  bundle, the custody transfer failure procedure detailed in
		 *  Section 5.12 must be followed.  The manner in which the countdown
		 *  interval for such a timer is determined is an implementation matter.
		 *  The bundle should be retained in persistent storage if possible.
		 */
		startCustodyXferTimer(bundle, route);
	}

	/**
	 * Custody Release procedure; remove custody constraint and if no longer
	 * retention constrained, discard the Bundle.
	 * @param bundle Bundle being released
	 * @throws JDtnException On JDTN Specific error
	 * @throws SQLException On Bundle database error
	 */
	private void custodyRelease(Bundle bundle) throws SQLException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("custodyRelease");
		}
		/*
		 *	5.10.2.  Custody Release
		 *	   Procedures for release of custody of a bundle whose destination is
		 *	   not a singleton endpoint are not defined in this specification.
		 *	   When custody of a bundle is released, where the destination of the
		 *	   bundle is a singleton endpoint, the "Custody accepted" retention
		 *	   constraint must be removed from the bundle and any custody transfer
		 *	   timer that has been established for this bundle must be destroyed.
		 */
		bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_CUSTODY_ACCEPTED);
		BundleDatabase.getInstance().updateRetentionConstraint(bundle);
		stopCustodyXferTimer(bundle);
		discardBundleIfNoLongerConstrained(bundle);
	}
	
	/**
	 * Process notification from adaptation layer on receipt of a Bundle,
	 * Bundle Received Procedure.
	 * @throws JDtnException On JDTN Specific error
	 * @throws SQLException On Bundle database error
	 */
	private void processBundleReceived(Bundle bundle) 
	throws InterruptedException, SQLException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("processBundleReceived()");
		}
		
		// Handle received Administrative Records separately
		if (bundle.isAdminRecord()) {
			
			// If the Administrative Record is directed to us, then handle it,
			// otherwise, fall thru to dispatch it.
			String ourHost = BPManagement.getInstance().getEndPointIdStem().getHostNodeName();
			String destHost = bundle.getPrimaryBundleBlock().getDestinationEndPointId().getHostNodeName();
			if (ourHost.equals(destHost)) {
	
				// Note: Admin Records directed to us are not retained in
				// Bundle Database; they are processed directly.
				try {
					processAdminRecordReceived(bundle);
				} catch (JDtnException e) {
					_logger.log(Level.SEVERE, "Processing received admin record", e);
				}
				return;
			}
		}
		
		if (isBundleInInboundList(bundle)) {
			// This can happen if:
			//   1. Bundle is a redundant retransmit and we're retaining the original
			//   2. Bundle is in process of reassembly.
			_logger.warning("Received Bundle is already in Retained Inbound List");
			if (bundle.getPrimaryBundleBlock().isCustodyTransferRequested()) {
				try {
					sendCustodySignal(bundle, false, CustodySignal.REASON_REDUNDANT_RECEPTION);
				} catch (JDtnException e) {
					_logger.log(Level.SEVERE, "Sending custody signal on redundant reception", e);
					
				} catch (InterruptedException e) {
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("Interrupted while Sending custody signal on redundant reception");
					}
				}
			}
			bundle.close();
			return;
		}
		
		// Make sure we can accept this Bundle
		long nBytes = bundle.getPayloadBundleBlock().getPayload().getLength();
		if (!BPManagement.getInstance().canAcceptCustody(bundle)) {
			_logger.warning("Cannot accept received Bundle, we would exceed retention limit");
			_logger.warning(Management.getInstance().dump("", true));
			try {
				deleteBundle(bundle, BundleStatusReport.REASON_CODE_DEPLETED_STORAGE);
			} catch (JDtnException e) {
				_logger.log(Level.SEVERE, "Deleting bundle because of depleted storage", e);
			}
			return;			
		}
		BPManagement.getInstance()._bpStats.nRetainedBytes += nBytes;		
		BPManagement.getInstance()._bpStats.nDataBundlesReceived++;
		addRetainedBundle(bundle);
		
		// Add Bundle to Bundle Database.  We use default EID Scheme, since
		// we won't know the actual EID Scheme until we forward the Bundle.
		BundleDatabase.getInstance().introduceBundle(
				bundle, 
				BundleSource.FORWARDED, 
				BundleState.RECEIVED, 
				BPManagement.getInstance().getEidScheme());
		
		/*
		 *	5.6.  Bundle Reception
		 *	   The steps in processing a bundle received from another node are:
		 *	   Step 1:   The retention constraint "Dispatch pending" must be added
		 *	      to the bundle.
		 */
		bundle.addRetentionConstraint(Bundle.RETENTION_CONSTRAINT_DISPATCH_PENDING);
		BundleDatabase.getInstance().updateRetentionConstraint(bundle);
		
		/*	   Step 2:   If the "request reporting of bundle reception" flag in the
		 *	      bundle's status report request field is set to 1, then a bundle
		 *	      reception status report with reason code "No additional
		 *	      information" should be generated, destined for the bundle's
		 *	      report-to endpoint ID.
		 */
		if (bundle.getPrimaryBundleBlock().isReportBundleReception()) {
			try {
				sendBundleStatusReport(
						bundle, 
						BundleStatusReport.STATUS_FLAG_BUNDLE_RECEIVED, 
						BundleStatusReport.REASON_CODE_NO_ADDL_INFO);
			} catch (JDtnException e) {
				_logger.log(Level.SEVERE, "sending Reception Report for received bundle", e);
				bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_DISPATCH_PENDING);
				BundleDatabase.getInstance().updateRetentionConstraint(bundle);
				if (bundle.getRetentionConstraint() == 0) {
					try {
						deleteBundle(bundle, BundleStatusReport.REASON_CODE_NO_ADDL_INFO);
					} catch (JDtnException e1) {
						discardBundleUnconditionally(bundle);
					}
				}
				return;
				
			} catch (InterruptedException e) {
				_logger.log(Level.SEVERE, "sending Reception Report for received bundle", e);
				bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_DISPATCH_PENDING);
				BundleDatabase.getInstance().updateRetentionConstraint(bundle);
				if (bundle.getRetentionConstraint() == 0) {
					try {
						deleteBundle(bundle, BundleStatusReport.REASON_CODE_NO_ADDL_INFO);
					} catch (JDtnException e1) {
						discardBundleUnconditionally(bundle);
					}
				}
				return;
			}
		}
		
		/*	   Step 3:   For each block in the bundle that is an extension block
		 *	      that the bundle protocol agent cannot process:
		 */
		ArrayList<BundleBlock> deleteList = new ArrayList<BundleBlock>();
		for (BundleBlock bundleBlock : bundle) {
			if (bundleBlock instanceof SecondaryBundleBlock) {
				SecondaryBundleBlock secondaryBundleBlock = (SecondaryBundleBlock)bundleBlock;
				if (secondaryBundleBlock.getBlockType() != SecondaryBundleBlock.BLOCK_TYPE_PAYLOAD) {
					_logger.warning("Received bundle block with unknown Block Type");
					if (GeneralManagement.isDebugLogging()) {
						if (_logger.isLoggable(Level.FINEST)) {
							_logger.finest(bundle.dump("", true));
						}
					}
					
					/*	      *  If the block processing flags in that block indicate that a
					 *	         status report is requested in this event, then a bundle
					 *	         reception status report with reason code "Block unintelligible"
					 *	         should be generated, destined for the bundle's report-to
					 *	         endpoint ID.
					 */
					if (secondaryBundleBlock.isReportStatusIfUnprocessable()) {
						try {
							sendBundleStatusReport(
									bundle, 
									BundleStatusReport.STATUS_FLAG_BUNDLE_RECEIVED, 
									BundleStatusReport.REASON_CODE_BAD_BLOCK);
						} catch (JDtnException e) {
							_logger.log(Level.SEVERE, "Sending Report on unprocessable block", e);
							// Try to soldier on
							
						} catch (InterruptedException e) {
							if (GeneralManagement.isDebugLogging()) {
								_logger.fine("Interrupted while Sending Report on unprocessable block");
							}
							try {
								deleteBundle(bundle, BundleStatusReport.REASON_CODE_NO_ADDL_INFO);
							} catch (JDtnException e1) {
								discardBundleUnconditionally(bundle);
							}
							return;
						}						
					}
					
					 /*	      *  If the block processing flags in that block indicate that the
					 *	         bundle must be deleted in this event, then the bundle protocol
					 *	         agent must delete the bundle for the reason "Block
					 *	         unintelligible"; the bundle deletion procedure defined in
					 *	         Section 5.13 must be followed and all remaining steps of the
					 *	         bundle reception procedure must be skipped.
					 */
					if (secondaryBundleBlock.isDeleteBundleIfUnprocessable()) {
						try {
							bundle.setRetentionConstraint(0);
							deleteBundle(bundle, BundleStatusReport.REASON_CODE_BAD_BLOCK);
						} catch (JDtnException e) {
							_logger.log(Level.SEVERE, "Deleting bundle with unprocessable block", e);
							
						} catch (InterruptedException e) {
							if (GeneralManagement.isDebugLogging()) {
								_logger.fine("Interrupted while Deleting bundle with unprocessable block");
							}
							try {
								deleteBundle(bundle, BundleStatusReport.REASON_CODE_NO_ADDL_INFO);
							} catch (JDtnException e1) {
								discardBundleUnconditionally(bundle);
							}
						}
						return;
					}
					
					/*	      *  If the block processing flags in that block do NOT indicate
					 *	         that the bundle must be deleted in this event but do indicate
					 *	         that the block must be discarded, then the bundle protocol
					 *	         agent must remove this block from the bundle.
					 */
					if (!secondaryBundleBlock.isDeleteBundleIfUnprocessable() &&
						secondaryBundleBlock.isDiscardBlockIfUnprocessable()) {
						deleteList.add(secondaryBundleBlock);
					}
					/*	      *  If the block processing flags in that block indicate NEITHER
					 *	         that the bundle must be deleted NOR that the block must be
					 *	         discarded, then the bundle protocol agent must set to 1 the
					 *	         "Block was forwarded without being processed" flag in the block
					 *	         processing flags of the block.
					 */
					if (!secondaryBundleBlock.isDeleteBundleIfUnprocessable() &&
						!secondaryBundleBlock.isDiscardBlockIfUnprocessable()) {
						secondaryBundleBlock.setForwardedWithoutProcessing(true);
					}
				}
			}
		}
		boolean updateNeeded = !deleteList.isEmpty();
		for (BundleBlock bundleBlock : deleteList) {
			bundle.removeBundleBlock(bundleBlock);
		}
		if (updateNeeded) {
			BundleDatabase.getInstance().updateBundleData(bundle);
		}
		
		/*	   Step 4:   If the bundle's custody transfer requested flag (in the
		 *	      bundle processing flags field) is set to 1 and the bundle has the
		 *	      same source endpoint ID, creation timestamp, and (if the bundle is
		 *	      a fragment) fragment offset and payload length as another bundle
		 *	      that (a) has not been discarded and (b) currently has the
		 *	      retention constraint "Custody accepted", custody transfer
		 *	      redundancy must be handled.  Otherwise, processing proceeds from
		 *	      Step 5.  Procedures for handling redundancy in custody transfer
		 *	      for a bundle whose destination is not a singleton endpoint are not
		 *	      defined in this specification.  For a bundle whose destination is
		 *	      a singleton endpoint, the bundle protocol agent must handle
		 *	      custody transfer redundancy by generating a "Failed" custody
		 *	      signal for this bundle with reason code "Redundant reception",
		 *	      destined for this bundle's current custodian, and removing this
		 *	      bundle's "Dispatch pending" retention constraint.
		 */
		if (checkCustodyTransferRedundancy(bundle)) {
			_logger.warning("Redundant Bundle received, bundleId=" + bundle.getExtendedBundleId());
			
			// Report redundant reception
			bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_DISPATCH_PENDING);
			BundleDatabase.getInstance().updateRetentionConstraint(bundle);
			try {
				sendCustodySignal(bundle, false, CustodySignal.REASON_REDUNDANT_RECEPTION);
			} catch (JDtnException e) {
				_logger.log(Level.SEVERE, "Sending custody signal on redundant reception", e);
				
			} catch (InterruptedException e) {
				if (GeneralManagement.isDebugLogging()) {
					_logger.fine("Interrupted while Sending custody signal on redundant reception");
				}
			}
			
			// Delete bundle
			try {
				deleteBundle(bundle, BundleStatusReport.REASON_CODE_NO_ADDL_INFO);
			} catch (JDtnException e) {
				_logger.log(Level.SEVERE, "deleting redundant bundle", e);
				discardBundleUnconditionally(bundle);
			}
		
		} else {
			/*	   Step 5:   Processing proceeds from Step 1 of Section 5.3.
			 */
			// Deliver or Forward the Bundle
			try {
				dispatchBundle(bundle);
			} catch (JDtnException e) {
				_logger.log(Level.SEVERE, "Dispatching received Bundle", e);
				try {
					deleteBundle(bundle, BundleStatusReport.REASON_CODE_NO_ADDL_INFO);
				} catch (JDtnException e2) {
					_logger.log(Level.SEVERE, "deleting redundant bundle", e2);
					discardBundleUnconditionally(bundle);
				}
			}
		}
	}
	
	/**
	 * Process received Administrative Record addressed to us
	 * @param bundle Bundle containing Administrative Record
	 * @throws InterruptedException interrupted during wait
	 * @throws JDtnException On JDTN Specific error
	 * @throws SQLException On Bundle database error
	 */
	private void processAdminRecordReceived(Bundle bundle) 
	throws JDtnException, InterruptedException, SQLException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onAdminRecordReceived");
		}
		Payload payload = bundle.getPayloadBundleBlock().getPayload();
		if (!(payload instanceof AdministrativeRecord)) {
			throw new BPException(
				"Received bundle had admin flag set but didn't have AdministrativeRecord payload");
		}
		
		AdministrativeRecord adminRecord = (AdministrativeRecord)payload;
		switch (adminRecord.getRecordType()) {
		case AdministrativeRecord.ADMIN_RECORD_TYPE_STATUS_REPORT:
			BPManagement.getInstance()._bpStats.nStatusReportsReceived++;
			processStatusReportReceived(bundle, adminRecord);
			break;
		case AdministrativeRecord.ADMIN_RECORD_TYPE_CUSTODY_SIGNAL:
			BPManagement.getInstance()._bpStats.nCustodySignalsReceived++;
			processCustodySignalReceived(bundle, adminRecord);
			break;
		default:
			throw new BPException(
				"Received Administrative Record had unknown RecordType: " + adminRecord.getRecordType());
		}		
	}
	
	/**
	 * Process Received BundleStatusReport
	 * @param reportBundle Bundle containing BundleStatusReport
	 * @param adminRecord Payload
	 * @throws JDtnException 
	 * @throws SQLException 
	 */
	private void processStatusReportReceived(
			Bundle reportBundle, 
			AdministrativeRecord adminRecord) 
	throws SQLException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onStatusReportReceived");
		}
		// Do some logging
		if (!(adminRecord instanceof BundleStatusReport)) {
			throw new BPException(
				"Administrative Record had Record Type indicating BundleStatusReport but" +
				" payload not instanceof BundleStatusReport");
		}
		
		BundleStatusReport report = (BundleStatusReport)adminRecord;
		BundleId bundleId = report.getReferencedBundleId();
		
		if (GeneralManagement.isDebugLogging()) {
			if (_logger.isLoggable(Level.FINE)) {
				if (report.isReportBundleDeleted()) {
					_logger.fine("Bundle " + bundleId + " deleted downstream");
				}
				if (report.isReportBundleDelivered()) {
					_logger.fine("Bundle " + bundleId + " delivered downstream");
				}
				if (report.isReportBundleForwarded()) {
					_logger.fine("Bundle " + bundleId + " forwarded downstream");
				}
				if (report.isReportBundleReceived()) {
					_logger.fine("Bundle " + bundleId + " received downstream");
				}
				if (report.isReportCustodyAccepted()) {
					_logger.fine("Bundle " + bundleId + " custody accepted downstream");
				}
			}
		}
		
		// Let Management know
		BPManagement.getInstance().logBundleStatusReport(report);
		
		// Let applications know
		BpApi.getInstance().onBundleStatusReport(
				report, 
				reportBundle.getPrimaryBundleBlock().getSourceEndpointId());
	}
	
	/**
	 * Process Received Custody Signal.  This is a Bundle from downstream
	 * reporting either acceptance of custody or rejection of custody of a
	 * Bundle we previously forwarded.
	 * @param custodyBundle Bundle containing Custody Signal
	 * @param adminRecord Payload
	 * @throws InterruptedException on interrupt from wait
	 * @throws JDtnException On JDTN Specific error
	 * @throws SQLException On Bundle database error
	 */
	private void processCustodySignalReceived(
			Bundle custodyBundle, 
			AdministrativeRecord adminRecord) 
	throws JDtnException, InterruptedException, SQLException {
		if (!(adminRecord instanceof CustodySignal)) {
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine(
						"Received AdministrativeRecord with Block Type indicating Custody Signal" +
						" but payload not instanceof CustodySignal");
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.finest(custodyBundle.dump("", true));
				}
			}
			throw new BPException(
					"Received AdministrativeRecord with Block Type indicating Custody Signal" +
					" but payload not instanceof CustodySignal");
		}
		CustodySignal custodySignal = (CustodySignal)adminRecord;
		if (GeneralManagement.isDebugLogging()) {
			if (_logger.isLoggable(Level.FINER)) {
				_logger.finer("onCustodySignalReceived(succeeded=" + 
						custodySignal.isCustodyXferSucceeded() + 
						", reason=" +
						custodySignal.getReason() + 
						")");
			}
		}
		BundleId bundleId = custodySignal.getReferencedBundleId();
		
		// Find the Bundle referenced by the CustodySignal so we can decide
		// what to do with it.
		// Cases:
		// 1. We sourced the Bundle and then forwarded it - it is sitting in our
		//    Outbound retention list.  OR
		// 2. We received the bundle upstream and then forwarded it downstream -
		//    it is sitting in our Inbound retention list.  OR
		// 3a. We sourced the Bundle and then forwarded it - it is sitting in our
		//    Outbound retention list. AND
		// 3b. The Bundle was addressed to us so we received it - it (a copy) is
		//    also sitting in our Intbound retention list.
		//    In this case, the copy referenced by the CustodySignal is that
		//    sitting in our Outbound retention list.
		Bundle referencedBundle = null;
		Bundle bundleFromInboundList = _inboundBundleMap.get(bundleId);
		Bundle bundleFromOutboundList = _outboundBundleMap.get(bundleId);
		if (bundleFromOutboundList != null && bundleFromInboundList == null) {
			// Case 1  We sourced it then forwarded it downstream, not to ourselves
			referencedBundle = bundleFromOutboundList;
		} else if (bundleFromInboundList != null && bundleFromOutboundList == null) {
			// Case 2 - We received from upstream and forwarded downstream
			referencedBundle = bundleFromInboundList;
		} else if (bundleFromInboundList != null && bundleFromOutboundList != null) {
			// Case 3 - We sourced bundle addressed to ourselves
			referencedBundle = bundleFromOutboundList;
		} else {
			// Bundle is in neither Inbound nor Outbound lists
			_logger.warning("Custody Signal references Bundle not in our retention list");
			if (GeneralManagement.isDebugLogging()) {
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.finest(bundleId.dump("", true));
					_logger.finest(custodySignal.dump("", true));
				}
			}
			return;
		}
		
		if (custodySignal.isCustodyXferSucceeded()) {
			/*
			 *	6.3.  Reception of Custody Signals
			 *	   For each received custody signal that has the "custody transfer
			 *	   succeeded" flag set to 1, the administrative element of the
			 *	   application agent must direct the bundle protocol agent to follow the
			 *     custody transfer success procedure in Section 5.11.
			 */
			custodyTransferSucceeded(referencedBundle);
			
		} else {
			/*
			 *  For each received custody signal that has the "custody transfer
			 *  succeeded" flag set to 0, the administrative element of the
			 *  application agent must direct the bundle protocol agent to follow the
			 *  custody transfer failure procedure in Section 5.12.
			 */
			custodyTransferRejected(referencedBundle);
		}
	}

	/**
	 * Called when we receive a Custody Signal from downstream rejecting
	 * custody transfer.
	 * @param bundle Affected Bundle
	 * @throws InterruptedException on interruption while waiting
	 * @throws JDtnException On JDTN Specific error
	 * @throws SQLException On Bundle database error
	 */
	private void custodyTransferRejected(Bundle bundle) throws JDtnException,
			InterruptedException, SQLException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("custodyTransferRejected");
		}
		_logger.warning("Custody Transfer was rejected, BundleId=" +
				bundle.getBundleId());
		
		/*	5.12.  Custody Transfer Failure
		 *	   Procedures for determining custody transfer failure for a bundle
		 *	   whose destination is not a singleton endpoint are not defined in this
		 *	   specification.  Custody transfer for a bundle whose destination is a
		 *	   singleton endpoint is determined to have failed at a custodial node
		 *	   for that bundle when either (a) that node's custody transfer timer
		 *	   for that bundle (if any) expires or (b) a "Failed" custody signal for
		 *	   that bundle is received at that node.
		 */
		if (bundle.isRetentionConstraint(Bundle.RETENTION_CONSTRAINT_CUSTODY_ACCEPTED)) {
			custodyTransferFailure(bundle);
		}
	}

	/**
	 * Custody Transfer Failure procedure.  Called when Custody Xfer Timer
	 * expires or when we get downstream rejection of custody transfer.
	 * @param bundle Affected Bundle
	 * @throws InterruptedException on interruption while waiting
	 * @throws JDtnException On JDTN Specific error
	 * @throws SQLException On Bundle database error
	 */
	private void custodyTransferFailure(Bundle bundle) 
	throws JDtnException, InterruptedException, SQLException {
		_logger.warning("Custody Transfer failed, BundleId=" + bundle.getBundleId());
		
		/*
		 *	5.12.  Custody Transfer Failure
		 *	   Procedures for determining custody transfer failure for a bundle
		 *	   whose destination is not a singleton endpoint are not defined in this
		 *	   specification.  Custody transfer for a bundle whose destination is a
		 *	   singleton endpoint is determined to have failed at a custodial node
		 *	   for that bundle when either (a) that node's custody transfer timer
		 *	   for that bundle (if any) expires or (b) a "Failed" custody signal for
		 *	   that bundle is received at that node.
		 *	   Upon determination of custody transfer failure, the action taken by
		 *	   the bundle protocol agent is implementation-specific and may depend
		 *	   on the nature of the failure.  For example, if custody transfer
		 *	   failure was inferred from expiration of a custody transfer timer or
		 *	   was asserted by a "Failed" custody signal with the "Depleted storage"
		 *	   reason code, the bundle protocol agent might choose to re-forward the
		 *	   bundle, possibly on a different route (Section 5.4).  Receipt of a
		 *	   "Failed" custody signal with the "Redundant reception" reason code,
		 *	   on the other hand, might cause the bundle protocol agent to release
		 *	   custody of the bundle and to revise its algorithm for computing
		 *	   countdown intervals for custody transfer timers.
		 * NOTE: we choose to just delete the Bundle
		 */
		deleteBundle(bundle, BundleStatusReport.REASON_CODE_NO_ADDL_INFO);
	}
	
	/**
	 * Custody Transfer Succeeded procedure.  Called when we receive a Custody
	 * Signal saying that a downstream node has accepted custody of a Bundle.
	 * @param bundle
	 * @throws JDtnException On JDTN Specific error
	 * @throws SQLException On Bundle database error
	 */
	private void custodyTransferSucceeded(Bundle bundle) 
	throws SQLException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("custodyTransferSucceeded");
		}
		
		// Notify apps
		BpApi.getInstance().onBundleCustodyReleased(bundle.getBundleId());
		
		
		/*	5.11.  Custody Transfer Success
		 *	   Procedures for determining custody transfer success for a bundle
		 *	   whose destination is not a singleton endpoint are not defined in this
		 *	   specification.
		 *	   Upon receipt of a "Succeeded" custody signal at a node that is a
		 *	   custodial node of the bundle identified in the custody signal, where
		 *	   the destination of the bundle is a singleton endpoint, custody of the
		 *	   bundle must be released as described in Section 5.10.2.
		 */
		if (bundle.isRetentionConstraint(Bundle.RETENTION_CONSTRAINT_CUSTODY_ACCEPTED)) {
			custodyRelease(bundle);
		}
	}
	
	/**
	 * Bundle dispatch procedure.  Called on received Bundle.  Here, we decide
	 * whether to deliver the Bundle or forward it.
	 * @param bundle
	 * @throws JDtnException on errors 
	 * @throws InterruptedException If interrupted out of wait
	 * @throws SQLException On Bundle database errors
	 */
	private void dispatchBundle(Bundle bundle) 
	throws JDtnException, InterruptedException, SQLException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("dispatchBundle");
		}
		/*
		 *	5.3.  Bundle Dispatching			
		 *	   The steps in dispatching a bundle are:
		 *	
		 *	   Step 1:   If the bundle's destination endpoint is an endpoint of
		 *	      which the node is a member, the bundle delivery procedure defined
		 *	      in Section 5.7 must be followed.
		 * Note: because we only support singleton EndPoints, we either deliver
		 * the Bundle or Forward it.  Not both.
		 */
		String ourHost = BPManagement.getInstance().getEndPointIdStem().getHostNodeName();
		String destHost = bundle.getPrimaryBundleBlock().getDestinationEndPointId().getHostNodeName();
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("ourHost=" + ourHost + "; destHost=" + destHost);
		}
		if (ourHost.equals(destHost)) {
			// Bundle is to us
			AppRegistration registration =
				BpApi.getInstance().findRegistrationForEndPointId(
						bundle.getPrimaryBundleBlock().getDestinationEndPointId());
			if (registration != null) {
				// Extra added; un-retain the Bundle
				bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_DISPATCH_PENDING);
				BundleDatabase.getInstance().updateRetentionConstraint(bundle);
				discardBundleIfNoLongerConstrained(bundle);
				// Deliver the Bundle
				deliverBundle(registration, bundle);
			
			} else {
				// No App registered
				_logger.warning("No Application registered for EndPointId " +
						bundle.getPrimaryBundleBlock().getDestinationEndPointId());
				bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_DISPATCH_PENDING);
				BundleDatabase.getInstance().updateRetentionConstraint(bundle);
				deleteBundle(bundle, BundleStatusReport.REASON_CODE_DEST_EID_UNKNOWN);
			}
			
		} else {
			// Start the bundle life timer.
			startBundleTimer(bundle);
			
			/*	
			 *	   Step 2:   Processing proceeds from Step 1 of Section 5.4.
			 */			
			forwardBundle(bundle);
		}
	}
	
	/**
	 * Check for Custody Transfer Redundancy.
	 * @param bundle Bundle
	 * @return True if given Bundle is redundant.
	 */
	private boolean checkCustodyTransferRedundancy(Bundle bundle) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("checkCustodyTransferRedundancy");
		}
		/*
		 *	5.6.  Bundle Reception
		 *	   Step 4:   If the bundle's custody transfer requested flag (in the
		 *	      bundle processing flags field) is set to 1 and the bundle has the
		 *	      same source endpoint ID, creation timestamp, and (if the bundle is
		 *	      a fragment) fragment offset and payload length as another bundle
		 *	      that (a) has not been discarded and (b) currently has the
		 *	      retention constraint "Custody accepted"
		 */
		// Special case; if given Bundle is from myself, to myself, then it can
		// exist in both Inbound and Outbound Retention lists.  We cannot detect
		// redundancy.  In that case, just assume not redundant.
		EndPointId myEid = BPManagement.getInstance().getEndPointIdStem();
		if (myEid.isPrefixOf(bundle.getPrimaryBundleBlock().getSourceEndpointId()) &&
			myEid.isPrefixOf(bundle.getPrimaryBundleBlock().getDestinationEndPointId())) {
			return false;
		}
		
		Bundle otherBundle = _outboundBundleMap.get(bundle.getExtendedBundleId());
		if (otherBundle != null) {
			// Found Outbound block with same BundleId
			// BundleId equality =>
			//    SourceEndPointId equality AND
			//    CreationTimestamp equality
			if (otherBundle.isRetentionConstraint(Bundle.RETENTION_CONSTRAINT_CUSTODY_ACCEPTED)) {
				// We have custody of the Outbound block
				if (otherBundle.getPrimaryBundleBlock().isFragment()) {
					// Outbound block is fragment; so consider fragmentation
					if (bundle.getPrimaryBundleBlock().getFragmentOffset() ==
						otherBundle.getPrimaryBundleBlock().getFragmentOffset() &&
						bundle.getPrimaryBundleBlock().getTotalAppDataUnitLength() ==
						otherBundle.getPrimaryBundleBlock().getTotalAppDataUnitLength()) {
						return true;
					}

				} else {
					// Outbound block is not fragment
					return true;
				}
			}
		}
					
		return false;
	}
	
	/**
	 * Bundle delivery procedure.
	 * @param registration The AppRegistration matching the Destination EndPointId
	 * of the bundle
	 * @param bundle The bundle to deliver
	 * @throws InterruptedException
	 * @throws JDtnException On JDTN Specific error
	 * @throws SQLException On Bundle database error
	 */
	private void deliverBundle(AppRegistration registration, Bundle bundle)
	throws InterruptedException, SQLException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("deliverBundle");
		}
		/*
		 *	5.7.  Local Bundle Delivery
		 *	   The steps in processing a bundle that is destined for an endpoint of
		 *	   which this node is a member are:
		 *	   Step 1:   If the received bundle is a fragment, the application data
		 *	      unit reassembly procedure described in Section 5.9 must be
		 *	      followed.  If this procedure results in reassembly of the entire
		 *	      original application data unit, processing of this bundle (whose
		 *	      fragmentary payload has been replaced by the reassembled
		 *	      application data unit) proceeds from Step 2; otherwise, the
		 *	      retention constraint "Reassembly pending" must be added to the
		 *	      bundle and all remaining steps of this procedure are skipped.
		 */
		if (bundle.getPrimaryBundleBlock().isFragment()) {
			Bundle reassembledBundle = reassembleBundle(bundle);
			if (reassembledBundle == null) {
				bundle.addRetentionConstraint(Bundle.RETENTION_CONSTRAINT_REASSEMBLY_PENDING);
				BundleDatabase.getInstance().updateRetentionConstraint(bundle);
				return;
			}
			bundle = reassembledBundle;
		}
		
		/*	   Step 2:   Delivery depends on the state of the registration whose
		 *	      endpoint ID matches that of the destination of the bundle:
		 *	      *  If the registration is in the Active state, then the bundle
		 *	         must be delivered subject to this registration (see Section 3.1
		 *	         above) as soon as all previously received bundles that are
		 *	         deliverable subject to this registration have been delivered.
		 *	      *  If the registration is in the Passive state, then the
		 *	         registration's delivery failure action must be taken (see
		 *	         Section 3.1 above).
		 * NOTE: we don't implement the PASSIVE registration state, so registrations
		 * are always active.
		 */
		BPManagement.getInstance()._bpStats.nDataBundlesDelivered++;
		registration.deliverBundle(bundle, 0L);
			
		/*	   Step 3:   As soon as the bundle has been delivered:
		 *	      *  If the "request reporting of bundle delivery" flag in the
		 *	         bundle's status report request field is set to 1, then a bundle
		 *	         delivery status report should be generated, destined for the
		 *	         bundle's report-to endpoint ID.  Note that this status report
		 *	         only states that the payload has been delivered to the
		 *	         application agent, not that the application agent has processed
		 *	         that payload.
		 */
		if (bundle.getPrimaryBundleBlock().isReportBundleDelivery()) {
			try {
				sendBundleStatusReport(
						bundle, 
						BundleStatusReport.STATUS_FLAG_BUNDLE_DELIVERED, 
						BundleStatusReport.REASON_CODE_NO_ADDL_INFO);
			} catch (JDtnException e) {
				_logger.log(Level.SEVERE, "Sending Delivered Report on delivered Bundle");
				// Soldier on
			}
		}
			
		/*	      *  If the bundle's custody transfer requested flag (in the bundle
		 *	         processing flags field) is set to 1, custodial delivery must be
		 *	         reported.  Procedures for reporting custodial delivery for a
		 *	         bundle whose destination is not a singleton endpoint are not
		 *	         defined in this specification.  For a bundle whose destination
		 *	         is a singleton endpoint, the bundle protocol agent must report
		 *	         custodial delivery by generating a "Succeeded" custody signal
		 *	         for the bundle, destined for the bundle's current custodian.
		 */
		if (bundle.getPrimaryBundleBlock().isCustodyTransferRequested()) {
			// Report custody acceptance if requested
			if (bundle.getPrimaryBundleBlock().isReportCustodyAcceptance()) {
				try {
					sendBundleStatusReport(
							bundle, 
							BundleStatusReport.STATUS_FLAG_CUSTODY_XFERRED, 
							BundleStatusReport.REASON_CODE_NO_ADDL_INFO);
				} catch (JDtnException e) {
					_logger.log(Level.SEVERE, "Sending Report Custody Accepted", e);
					// Soldier on
				}
			}
			
			// Send CustodySignal to custodian telling it we've accepted Custody
			try {
				sendCustodySignal(bundle, true, CustodySignal.REASON_NO_FURTHER_INFO);
			} catch (JDtnException e) {
				_logger.log(Level.SEVERE, "Sending Custody Signal after delivering Bundle", e);
				// Soldier on
				// Ordinarily, we would do a deleteBundle.  However, we have
				// already delivered the Bundle.  Not much we can do for recovery.
			}
			
		}
	}
	
	/**
	 * Application Data Unit Reassembly Procedure.  We delegate to
	 * BPFragmentation component.
	 * @param bundle Bundle Fragment
	 * @return Reassembled Bundle or null if none
	 * @throws InterruptedException 
	 * @throws JDtnException On JDTN Specific error
	 * @throws SQLException On Bundle database error
	 */
	private Bundle reassembleBundle(Bundle bundle) 
	throws InterruptedException, SQLException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("reassembleBundle");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(bundle.dump("", true));
			}
		}
		
		try {
			Bundle reassBundle = 
				BPFragmentation.getInstance().onIncomingFragmentedBundle(bundle);
			if (reassBundle != null) {
				reassBundle.removeRetentionConstraint(
						Bundle.RETENTION_CONSTRAINT_REASSEMBLY_PENDING);
				// Note that BPFragmentation will take care of ensuring that all
				// fragments will be removed from our retention lists.
				// All we have to do is deliver this reassembled Bundle.
			}
			return reassBundle;
			
		} catch (JDtnException e) {
			_logger.log(Level.SEVERE, "Reassemble Bundle", e);
			// Remove all constraints and delete bundle
			bundle.setRetentionConstraint(0);
			try {
				deleteBundle(bundle, BundleStatusReport.REASON_CODE_NO_ADDL_INFO);
			} catch (JDtnException e1) {
				_logger.log(Level.SEVERE, "Deleting Bundle Fragment", e1);
				discardBundleUnconditionally(bundle);
			}
			return null;
			
		}
	}
	
	/**
	 * Send a BundleStatusReport concerning the given Bundle
	 * to the "ReportTo" EID for the given Bundle
	 * @param bundle Given Bundle
	 * @param flags Flags to set in BundleStatusReport; one of
	 * BundleStatusReport.STATUS_FLAG_*
	 * @param reason Reason to embed into BundleStatusReport; one of
	 * BundleStatusReport.REASON_CODE_*
	 * @throws JDtnException
	 * @throws InterruptedException
	 * @throws SQLException 
	 */
	private void sendBundleStatusReport(
			Bundle bundle,
			int flags,
			int reason) 
	throws JDtnException, InterruptedException, SQLException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("sendBundleStatusReport");
		}
		
		/*
		 *	6.2.  Generation of Administrative Records
		 *	   Whenever the application agent's administrative element is directed
		 *	   by the bundle protocol agent to generate an administrative record
		 *	   with reference to some bundle, the following procedure must be
		 *	   followed:
		 *	   Step 1:   The administrative record must be constructed.  If the
		 *	      referenced bundle is a fragment, the administrative record must
		 *	      have the Fragment flag set and must contain the fragment offset
		 *	      and fragment length fields.  The value of the fragment offset
		 *	      field must be the value of the referenced bundle's fragment
		 *	      offset, and the value of the fragment length field must be the
		 *	      length of the referenced bundle's payload.
		 *	   Step 2:   A request for transmission of a bundle whose payload is
		 *	      this administrative record must be presented to the bundle
		 *	      protocol agent.
		 */
		BundleStatusReport report =
			new BundleStatusReport(bundle.getPrimaryBundleBlock().isFragment());
		if ((flags & BundleStatusReport.STATUS_FLAG_BUNDLE_FORWARDED) != 0) {
			report.setReportBundleForwarded(true);
			report.setForwardingTime(new AdministrativeTimeStamp());
		}
		if ((flags & BundleStatusReport.STATUS_FLAG_CUSTODY_XFERRED) != 0) {
			report.setReportCustodyAccepted(true);
			report.setCustodyAcceptanceTime(new AdministrativeTimeStamp());
		}
		if ((flags & BundleStatusReport.STATUS_FLAG_BUNDLE_DELETED) != 0) {
			report.setReportBundleDeleted(true);
			report.setDeletionTime(new AdministrativeTimeStamp());
		}
		if ((flags & BundleStatusReport.STATUS_FLAG_BUNDLE_DELIVERED) != 0) {
			report.setReportBundleDelivered(true);
			report.setDeliveryTime(new AdministrativeTimeStamp());
		}
		if ((flags & BundleStatusReport.STATUS_FLAG_BUNDLE_RECEIVED) != 0) {
			report.setReportBundleReceived(true);
			report.setReceivedTime(new AdministrativeTimeStamp());
		}
		report.setReasonCode(reason);
		if (report.isForFragment()) {
			report.setFragmentOffset(bundle.getPrimaryBundleBlock().getFragmentOffset());
			report.setFragmentLength(bundle.getPrimaryBundleBlock().getTotalAppDataUnitLength());
		}
		report.setBundleTimestamp(bundle.getPrimaryBundleBlock().getCreationTimestamp());
		report.setSourceEndPointId(bundle.getPrimaryBundleBlock().getSourceEndpointId());
		
		EndPointId reportToEid = bundle.getPrimaryBundleBlock().getReportToEndPointId();
		Bundle outBundle =
			Bundle.constructAdminRecordBundle(
					BPManagement.getInstance().getEndPointIdStem(), 
					reportToEid, 
					report, 
					adminRecBundleOptions);
		
		if (BPManagement.getInstance().getEndPointIdStem().isPrefixOf(reportToEid)) {
			// ReportTo is *Us*.  Just short circuit it.
			processStatusReportReceived(outBundle, report);
			
		} else {
			Route route = RouterManager.getInstance().findRoute(outBundle);
			if (route == null) {
				throw new BPException("Trying to send a Report but have no Route for ReportTo " +
						outBundle.getPrimaryBundleBlock().getDestinationEndPointId());
			} else if (route instanceof DelayedRoute) {
				// Router is asking us to delay transmission of the Bundle Status Report
				DelayedRoute delayedRoute = (DelayedRoute)route;
				BundleHolder.getInstance().addBundle(outBundle, delayedRoute.getDelay(), this);
				return;
				
			} else if (!route.getLink().isLinkOperational() ||
					   !route.getNeighbor().isNeighborOperational()) {
				// Outbound Link is not up; try again later
				BundleHolder.getInstance().addBundle(bundle, BP_PROTOCOOL_AGENT_LINK_HOLD_MSECS, this);
				return;
			}
			
			BPManagement.getInstance()._bpStats.nStatusReportsSent++;
			ConvergenceLayerMux.getInstance().transmitBundle(
					outBundle, 
					route, 
					adminRecBundleOptions.blockColor);
		}
	}
	
	/**
	 * Send a Custody Signal concerning the given Bundle
	 * to the "Custodian" EID in the given Bundle
	 * @param bundle Given Bundle
	 * @param succeeded Whether custodian transfer succeeded
	 * @param reason Reason; one of CustodySignal.REASON_*
	 * @throws InterruptedException
	 * @throws JDtnException On JDTN Specific error
	 * @throws SQLException On Bundle database error
	 */
	private void sendCustodySignal(Bundle bundle, boolean succeeded, int reason) 
	throws JDtnException, InterruptedException, SQLException {
		if (GeneralManagement.isDebugLogging()) {
			if (_logger.isLoggable(Level.FINER)) {
				_logger.fine("sendCustodySignal(succeeded=" + succeeded + 
						", reason=" + reason + ")");
			}
		}

		/*
		 *	6.2.  Generation of Administrative Records
		 *	   Whenever the application agent's administrative element is directed
		 *	   by the bundle protocol agent to generate an administrative record
		 *	   with reference to some bundle, the following procedure must be
		 *	   followed:
		 *	   Step 1:   The administrative record must be constructed.  If the
		 *	      referenced bundle is a fragment, the administrative record must
		 *	      have the Fragment flag set and must contain the fragment offset
		 *	      and fragment length fields.  The value of the fragment offset
		 *	      field must be the value of the referenced bundle's fragment
		 *	      offset, and the value of the fragment length field must be the
		 *	      length of the referenced bundle's payload.
		 *	   Step 2:   A request for transmission of a bundle whose payload is
		 *	      this administrative record must be presented to the bundle
		 *	      protocol agent.
		 */
		CustodySignal signal =
			new CustodySignal(bundle.getPrimaryBundleBlock().isFragment());
		signal.setCustodyXferSucceeded(succeeded);
		signal.setReason(reason);
		if (signal.isForFragment()) {
			signal.setFragmentOffset(bundle.getPrimaryBundleBlock().getFragmentOffset());
			signal.setFragmentLength(bundle.getPrimaryBundleBlock().getTotalAppDataUnitLength());
		}
		signal.setTimeStamp(new AdministrativeTimeStamp());
		signal.setBundleTimeStamp(bundle.getPrimaryBundleBlock().getCreationTimestamp());
		signal.setBundleSourceEndPointId(bundle.getPrimaryBundleBlock().getSourceEndpointId());
		
		EndPointId custodianEid = bundle.getPrimaryBundleBlock().getCustodianEndPointId();
		Bundle signalBundle =
			Bundle.constructAdminRecordBundle(
					BPManagement.getInstance().getEndPointIdStem(), 
					custodianEid, 
					signal, 
					adminRecBundleOptions);
		if (BPManagement.getInstance().getEndPointIdStem().isPrefixOf(custodianEid)) {
			// Custodian is *us*.  We're sending a Custody Signal to ourselves.
			// Short circuit it.
			processCustodySignalReceived(signalBundle, signal);
		} else {
			// Find a route to send the CustodySignal to
			Route route = RouterManager.getInstance().findRoute(signalBundle);
			if (route == null) {
				throw new BPException(
						"Trying to send a Custody Signal but have no Route for Custodian " +
						signalBundle.getPrimaryBundleBlock().getDestinationEndPointId());
			} else if (route instanceof DelayedRoute) {
				// Router is asking us to delay transmission of the Custody Signal
				DelayedRoute delayedRoute = (DelayedRoute)route;
				BundleHolder.getInstance().addBundle(signalBundle, delayedRoute.getDelay(), this);
				return;
				
			} else if (!route.getLink().isLinkOperational() ||
					   !route.getNeighbor().isNeighborOperational()) {
				// Outbound Link is not up; try again later
				BundleHolder.getInstance().addBundle(bundle, BP_PROTOCOOL_AGENT_LINK_HOLD_MSECS, this);
				return;
			}
			
			BPManagement.getInstance()._bpStats.nCustodySignalsSent++;
			ConvergenceLayerMux.getInstance().transmitBundle(
					signalBundle, 
					route, 
					adminRecBundleOptions.blockColor);
		}
	}
	
	
	/**
	 * Process notification from Adaptation Layer when a Bundle Transmission
	 * gets cancelled by the receiver.
	 * @param bundle The cancelled Bundle
	 * @param reason Why cancelled, one of CancelSegment.REASON_CODE_*
	 */
	private void processBundleTransmitCancelledByReceiver(Bundle bundle, byte reason) {
		_logger.warning("Bundle Transmit Cancelled");
		_logger.warning("Bundle " + bundle.getExtendedBundleId() + " Reason: " +
				CancelSegment.reasonCodeToString(reason));
		try {
			// Bundle transmit was cancelled.  Policy decision: Do we hold the
			// bundle and retry later on?  Or do we just give up?
			if (BPManagement.getInstance().isHoldBundleIfNoRoute()) {
				// Hold the Bundle and retry later
				BundleHolder.getInstance().addBundle(bundle, BP_PROTOCOOL_AGENT_LINK_HOLD_MSECS, this);
				BundleDatabase.getInstance().bundleHeld(bundle);
			} else {
				// Give it up
				forwardingFailed(bundle, CustodySignal.REASON_NO_FURTHER_INFO);
				BpApi.getInstance().onBundleTransmissionCancelled(bundle, reason);
			}
			
		} catch (Exception e) {
			_logger.log(Level.SEVERE, "Reporting Forwarding Failed", e);
		} finally {
			BpApi.getInstance().onBundleTransmissionCancelled(bundle, reason);
		}
	}
	
	/**
	 * Called after a Bundle goes through a Bundle Hold and now needs to be
	 * re-processed.
	 * @param bundle Affected Bundle
	 * @throws InterruptedException Thread is being interrupted
	 * @throws JDtnException On JDTN Specific error
	 * @throws SQLException On Bundle database error
	 */
	private void processPostBundleHoldEvent(Bundle bundle)
	throws JDtnException, InterruptedException, SQLException {
		Route route = RouterManager.getInstance().findRoute(bundle);
		if (route == null) {
			// No route to forward Bundle.  Policy decision: do we hold the
			// Bundle, hoping a route will become available before the Bundle
			// expires?  Or do we discard the bundle?
			if (BPManagement.getInstance().isHoldBundleIfNoRoute()) {
				BundleHolder.getInstance().addBundle(bundle, BP_PROTOCOOL_AGENT_LINK_HOLD_MSECS, this);
				BundleDatabase.getInstance().bundleHeld(bundle);
				return;
				
			} else {
				_logger.severe("No route for destination " +
						bundle.getPrimaryBundleBlock().getDestinationEndPointId());
				bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_FORWARD_PENDING);
				BundleDatabase.getInstance().updateRetentionConstraint(bundle);
				discardBundleIfNoLongerConstrained(bundle);
				BpApi.getInstance().onBundleTransmissionCancelled(
						bundle, 
						CancelSegment.REASON_CODE_UNREACHABLE);
				return;
			}
		}
		if (route instanceof DelayedRoute) {
			DelayedRoute delayedRoute = (DelayedRoute)route;
			BundleHolder.getInstance().addBundle(bundle, delayedRoute.getDelay(), this);
			BundleDatabase.getInstance().bundleHeld(bundle);
			return;
		}
		if (!route.getLink().isLinkOperational() ||
			!route.getNeighbor().isNeighborOperational()) {
			BundleHolder.getInstance().addBundle(bundle, BP_PROTOCOOL_AGENT_LINK_HOLD_MSECS, this);
			BundleDatabase.getInstance().bundleHeld(bundle);
			return;
		}
		
		if (bundle.isAdminRecord()) {
			AdministrativeRecord adminRec =
				(AdministrativeRecord)bundle.getPayloadBundleBlock().getBody();
			switch (adminRec.getRecordType()) {
			case AdministrativeRecord.ADMIN_RECORD_TYPE_STATUS_REPORT:
				BPManagement.getInstance()._bpStats.nStatusReportsSent++;
				BundleDatabase.getInstance().bundleForwardEnqueued(bundle);
				ConvergenceLayerMux.getInstance().transmitBundle(
						bundle, 
						route, 
						adminRecBundleOptions.blockColor);
				break;

			case AdministrativeRecord.ADMIN_RECORD_TYPE_CUSTODY_SIGNAL:
				BPManagement.getInstance()._bpStats.nCustodySignalsSent++;
				BundleDatabase.getInstance().bundleForwardEnqueued(bundle);
				ConvergenceLayerMux.getInstance().transmitBundle(
						bundle, 
						route, 
						adminRecBundleOptions.blockColor);
				break;
				
			default:
				_logger.severe("Unknown type of Administrative Record");
				break;
			}
			
		} else if (BPManagement.getInstance().getEndPointIdStem().isPrefixOf(
				bundle.getPrimaryBundleBlock().getSourceEndpointId())) {
			// Bundle is from us, forward it
			BPManagement.getInstance()._bpStats.nDataBundlesFwded++;
			BundleDatabase.getInstance().bundleForwardEnqueued(bundle);
			ConvergenceLayerMux.getInstance().transmitBundle(
					bundle, 
					route, 
					bundle.getBundleOptions().blockColor);
		} else {
			// Bundle is not from us, forward it
			try {
				BPManagement.getInstance()._bpStats.nDataBundlesFwded++;
				BundleDatabase.getInstance().bundleForwardEnqueued(bundle);
				ConvergenceLayerMux.getInstance().transmitBundle(
						bundle, 
						route, 
						BPManagement.getInstance().getBlockColor(
								bundle.getPrimaryBundleBlock().getClassOfServicePriority()));
			} catch (Exception e) {
				_logger.log(Level.SEVERE, "Forwarding Bundle after hold delay", e);
				bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_CUSTODY_ACCEPTED);
				bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_FORWARD_PENDING);
				try {
					deleteBundle(bundle, BundleStatusReport.REASON_CODE_NO_ADDL_INFO);
				} catch (Exception e1) {
					_logger.log(Level.SEVERE, "Deleting bundle after failure to forward it", e);
					discardBundleUnconditionally(bundle);
				}
			}
		}
	}
	
	/**
	 * Called when a Bundle is put into Bundle Hold but that hold cancelled.
	 * @param bundle Affected Bundle
	 * @throws InterruptedException 
	 * @throws JDtnException 
	 * @throws SQLException 
	 */
	private void processBundleHoldCancel(Bundle bundle) 
	throws SQLException, JDtnException, InterruptedException {
		// No need to do anything; we are the source of the cancel and we take
		// care of everything elsewhere.
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("Bundle Hold cancelled for Bundle " + bundle.getBundleId());
		}
		BundleDatabase.getInstance().bundleInCustody(bundle);
	}
	
	/**
	 * Notification that a Bundle was in Hold but the Hold got cancelled
	 * @throws JDtnException 
	 * @throws SQLException 
	 * @see com.cisco.qte.jdtn.bp.BundleHolder.BundleHoldCallback#notifyBundleHoldCancel(com.cisco.qte.jdtn.bp.Bundle)
	 */
	@Override
	public void notifyBundleHoldCancel(Bundle bundle)  {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("Bundle Hold cancelled for Bundle " + bundle.getExtendedBundleId());
		}
		try {
			onBundleHoldCancel(bundle);
		} catch (InterruptedException e) {
			// Ignore
		} catch (Exception e) {
			_logger.log(Level.SEVERE, "notifyBundleHoldCancel()", e);
		}
	}

	/**
	 * Notification that a Bundle in Hold had its delay period expired.  Try
	 * to put it back into the system.
	 * @see com.cisco.qte.jdtn.bp.BundleHolder.BundleHoldCallback#notifyBundleHoldDone(com.cisco.qte.jdtn.bp.Bundle)
	 */
	@Override
	public void notifyBundleHoldDone(Bundle bundle) {
		try {
			onPostBundleHold(bundle);
		} catch (InterruptedException e) {
			// Ignore
		}
	}
	
	/**
	 * Dump this object
	 * @param indent Amount of indentation
	 * @param detailed verbosity
	 * @return dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "BPProtocolAgent\n");
		sb.append(indent + "  Outbound Retention List\n");
		for (Bundle oBundle : _outboundBundleList) {
			sb.append(oBundle.dump(indent + "  ", detailed));
		}
		sb.append(indent + "  Inbound Retention List\n");
		for (Bundle iBundle : _inboundBundleList) {
			sb.append(iBundle.dump(indent + "  ", detailed));
		}
		return sb.toString();
	}

}
