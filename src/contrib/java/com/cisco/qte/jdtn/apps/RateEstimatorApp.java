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
package com.cisco.qte.jdtn.apps;

import java.io.File;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.bp.BPException;
import com.cisco.qte.jdtn.bp.BPManagement;
import com.cisco.qte.jdtn.bp.BpApi;
import com.cisco.qte.jdtn.bp.Bundle;
import com.cisco.qte.jdtn.bp.BundleColor;
import com.cisco.qte.jdtn.bp.BundleId;
import com.cisco.qte.jdtn.bp.BundleOptions;
import com.cisco.qte.jdtn.bp.EndPointId;
import com.cisco.qte.jdtn.bp.Payload;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.ltp.LtpManagement;
import com.cisco.qte.jdtn.ltp.LtpNeighbor;

/**
 * This App attempts to estimate the optimum setting for the Segment Rate
 * Limit property for a specified Neighbor.  It does so by sending a series of
 * bundles to the Neighbor, using a range of settings for the Segment Rate
 * Limit property.  It observes two properties of each bundle transmit:
 * <ul>
 *   <li>Amount of time required to send the bundle
 *   <li>Number of resends required
 * </ul>
 */
public class RateEstimatorApp extends AbstractApp {

	private static final Logger _logger =
		Logger.getLogger(RateEstimatorApp.class.getCanonicalName());
	
	public static final String APP_NAME = "RateEstimator";
	public static final String APP_PATTERN =
		"RateEstimator" + BPManagement.ALL_SUBCOMPONENTS_EID_PATTERN;
	public static final int QUEUE_DEPTH = 10;
	public static final long JOIN_TIMEOUT_MSECS = 2000L;
	public static final long INTER_BUNDLE_SLEEP = 1000L;
	
	private Thread _xmitThread = null;
	private RateEstimatorXmitThread _xmitter = null;
	private final Object _sync = new Object();
	
	/**
	 * Constructor
	 * @param args not used
	 * @throws BPException from super
	 */
	public RateEstimatorApp(String[] args) throws BPException {
		super(APP_NAME, APP_PATTERN, QUEUE_DEPTH, args);
	}
	
	/**
	 * Start up a process to measure the Segment Rate Limit for the given
	 * Neighbor.
	 * @param neighbor Given Neighbor
	 * @param file A File to transfer
	 */
	public void estimateRateLimit(LtpNeighbor neighbor, MediaRepository.File file)
	throws JDtnException {
		synchronized (_sync) {
			if (_xmitThread != null) {
				throw new JDtnException("Estimator in progress");
			}
			_xmitter = new RateEstimatorXmitThread(neighbor, file);
			_xmitThread = new Thread(_xmitter);
			_xmitThread.setName("RateEstimator");
			_xmitThread.start();
		}
	}
	
	/**
	 * Implementation supplied method called when App is started up
	 */
	@Override
	public void startupImpl() {
		// Nothing
	}

	/**
	 * Implementation supplied method called when App is shut down
	 */
	@Override
	public void shutdownImpl() {
		synchronized (_sync) {
			if (_xmitThread != null) {
				_xmitThread.interrupt();
				try {
					_xmitThread.join(JOIN_TIMEOUT_MSECS);
				} catch (InterruptedException e) {
					// Ignore
				}
				_xmitThread = null;
				_xmitter = null;
			}
		}
	}

	/**
	 * State of Estimator
	 */
	private enum EstimatorState {
		IDLE,
		RAMPING_UP,
		BINARY_SEARCH
	}
	
	/**
	 * Events enqueued to Estimator
	 */
	protected enum EstimatorEvent {
		XMIT_SUCCESSFUL,
		XMIT_FAILED
	}
	
	/**
	 * Inner class Thread which performs the Rate Limit Estimation.  We vary
	 * the Rate Limit for the current neighbor over a range of values, and
	 * measure the time it takes to perform a transmit of a large Bundle.
	 * We attempt to fine the optimum Rate Limit which gives rise to the
	 * minimum transmit time.
	 */
	public class RateEstimatorXmitThread implements Runnable {

		private static final int EVENT_QUEUE_CAPACITY = 8;
		private static final double INITIAL_RAMP_UP_RATE_LIMIT = 50.0d;
		private static final double RAMP_UP_RATE_LIMIT_PERCENT = 0.5d;
		private static final double RATE_LIMIT_TOLERANCE = 0.05d;
		private static final long RESEND_TOLERANCE = 16;
		private static final int TRIES = 3;
		public static final long EVENT_WAIT_MSECS = 2 * 60 * 1000L;
		
		private final LtpNeighbor _neighbor;
		private final MediaRepository.File _file;
		private double _currentRateLimit = INITIAL_RAMP_UP_RATE_LIMIT;
		private double _initialRateLimit = -1.0d;
		private double _optimumRateLimit = INITIAL_RAMP_UP_RATE_LIMIT;
		private double _bsearchUpperRateLimit = 0.0d;
		private double _bsearchLowerRateLimit = 0.0d;
		private double _bsearchMiddleRateLimit = 0.0d;
		private long _currentResendCount = 0;
		private long _previousResendCount = 0;
		private long _minimumDeltaTime = Long.MAX_VALUE;
		private Bundle _bundle = null;
		
		private ArrayBlockingQueue<EstimatorEvent> _eventQueue =
			new ArrayBlockingQueue<EstimatorEvent>(EVENT_QUEUE_CAPACITY);
		private EstimatorState _state = EstimatorState.IDLE;
		
		/**
		 * Constructor for RateEstimatorXmitThread
		 * @param neighbor The Neighbor whose Rate limit we want to estimate
		 * @param file A File to send in the test Bundles.  This should be a
		 * large File, such as a 1.3 - 1.6 MB image file.
		 */
		public RateEstimatorXmitThread(LtpNeighbor neighbor, MediaRepository.File file) {
			_neighbor = neighbor;
			_file = file;
			_state = EstimatorState.RAMPING_UP;
			_initialRateLimit = neighbor.getSegmentXmitRateLimit();
		}
		
		/**
		 * Runs the Rate Estimation Xmit Thread
		 */
		@Override
		public void run() {
			
			Vector<RateEstimationRecord> log = new Vector<RateEstimationRecord>();
			
			try {
				// Repeat until we say we're done
				while (_state != EstimatorState.IDLE) {
					_logger.info("Current Trial Rate Limit = " + _currentRateLimit);
					_neighbor.setSegmentXmitRateLimit(_currentRateLimit);
					
					_previousResendCount = LtpManagement.getInstance().getLtpStats().nDataSegmentResends;
					
					// Send the Bundle and wait for it to complete, or until
					// we decide it has failed.
					_logger.finer("RateEstimatorXmitThread sending Bundle");
					long xmitTime = sendBundle();
					_currentResendCount = LtpManagement.getInstance().getLtpStats().nDataSegmentResends;
					long deltaResends = _currentResendCount - _previousResendCount;
					log.add(
						new RateEstimationRecord(
							_currentRateLimit,
							xmitTime,
							deltaResends));
							
					switch (_state) {
					case RAMPING_UP:
						// We are ramping up from a low Rate Limit to a high Rate Limit
						if (xmitTime != Long.MAX_VALUE) {
							// Bundle received at destination
							
							_logger.finer("RateEstimatorXmitThread Ramp State: Xmit Successful; deltaTime=" + xmitTime);
							
							if (deltaResends > RESEND_TOLERANCE) {
								// We had to do too many resends.  This rate limit unacceptable.
								// Stop ramping and start binary search
								_logger.finer("Trial failed; Resends; Switching to binary search");
								_state = EstimatorState.BINARY_SEARCH;
								_bsearchUpperRateLimit = _currentRateLimit;
								_bsearchLowerRateLimit = _optimumRateLimit;
								_bsearchMiddleRateLimit =
									_bsearchLowerRateLimit +
									(_bsearchUpperRateLimit - _bsearchLowerRateLimit) / 2.0d;
								_currentRateLimit = _bsearchMiddleRateLimit;
								_logger.finest("RateEstimatorXmitThread Bsearch State: lower=" +
										_bsearchLowerRateLimit + "; middle=" + _bsearchMiddleRateLimit +
										"; upper=" + _bsearchUpperRateLimit);
								
							} else {
								// Tolerable Resends
								if (xmitTime < _minimumDeltaTime) {
									// Shortest time yet; this rate limit is possibly optimum
									_logger.finer("Trial succeeded; Optimum Rate Limit = " +
											_currentRateLimit);
									_minimumDeltaTime = xmitTime;
									_optimumRateLimit = _currentRateLimit;
								}
								// Continue ramping
								_currentRateLimit += RAMP_UP_RATE_LIMIT_PERCENT * _currentRateLimit;
							}
							
						} else {
							// Bundle Transmission failed
							// This rate limit unacceptable.
							// Stop ramping and start binary search.
							_logger.info("Trial failed; transmit unsuccessful; Switching to binary search");
							_state = EstimatorState.BINARY_SEARCH;
							_bsearchUpperRateLimit = _currentRateLimit;
							_bsearchLowerRateLimit = _optimumRateLimit;
							_bsearchMiddleRateLimit =
								_bsearchLowerRateLimit +
								(_bsearchUpperRateLimit - _bsearchLowerRateLimit) / 2.0d;
							_currentRateLimit = _bsearchMiddleRateLimit;
							_logger.finest("RateEstimatorXmitThread Bsearch State: lower=" +
									_bsearchLowerRateLimit + "; middle=" + _bsearchMiddleRateLimit +
									"; upper=" + _bsearchUpperRateLimit);
						}
						break;
						
					case BINARY_SEARCH:
						if (xmitTime != Long.MAX_VALUE) {
							// Successful Xmit
							_logger.finer("RateEstimatorXmitThread Bsearch State: Xmit success; deltaTime=" + xmitTime);
							
							if (deltaResends > RESEND_TOLERANCE) {
								// Too many resends.  This rate limit unacceptable.
								_logger.info("Trial Failed; resends");
								_bsearchUpperRateLimit = _bsearchMiddleRateLimit;
								_bsearchMiddleRateLimit = 
									_bsearchLowerRateLimit +
									(_bsearchUpperRateLimit - _bsearchLowerRateLimit) / 2.0d;
								_currentRateLimit = _bsearchMiddleRateLimit;
								_logger.finest("RateEstimatorXmitThread Bsearch State: lower=" +
										_bsearchLowerRateLimit + "; middle=" + _bsearchMiddleRateLimit +
										"; upper=" + _bsearchUpperRateLimit);
								double pdiff =
									(_bsearchUpperRateLimit - _bsearchLowerRateLimit) / _bsearchUpperRateLimit;
								if (pdiff < RATE_LIMIT_TOLERANCE) {
									_state = EstimatorState.IDLE;
								}
								
							} else {
								// No resends
								if (xmitTime < _minimumDeltaTime) {
									_logger.info("Trial succeeded; new optimimum Rate Limit = " + _currentRateLimit);
									_optimumRateLimit = _currentRateLimit;
									_minimumDeltaTime = xmitTime;
								}
								_bsearchLowerRateLimit = _bsearchMiddleRateLimit;
								_bsearchMiddleRateLimit =
									_bsearchLowerRateLimit +
									(_bsearchUpperRateLimit - _bsearchLowerRateLimit) / 2.0d;
								_currentRateLimit = _bsearchMiddleRateLimit;
								_logger.finest("RateEstimatorXmitThread Bsearch State: lower=" +
										_bsearchLowerRateLimit + "; middle=" + _bsearchMiddleRateLimit +
										"; upper=" + _bsearchUpperRateLimit);
								double pdiff =
									(_bsearchUpperRateLimit - _bsearchLowerRateLimit) / _bsearchUpperRateLimit;
								if (pdiff < RATE_LIMIT_TOLERANCE) {
									_state = EstimatorState.IDLE;
								}
							}
						} else {
							// Unsuccessful Xmit.  This rate limit unacceptable
							_logger.info("Trial failed; Transmit error");
							_bsearchUpperRateLimit = _bsearchMiddleRateLimit;
							_bsearchMiddleRateLimit = 
								_bsearchLowerRateLimit +
								(_bsearchUpperRateLimit - _bsearchLowerRateLimit) / 2.0d;
							_currentRateLimit = _bsearchMiddleRateLimit;
							_logger.finest("RateEstimatorXmitThread Bsearch State: lower=" +
									_bsearchLowerRateLimit + "; middle=" + _bsearchMiddleRateLimit +
									"; upper=" + _bsearchUpperRateLimit);
							double pdiff =
								(_bsearchUpperRateLimit - _bsearchLowerRateLimit) / _bsearchUpperRateLimit;
							if (pdiff < RATE_LIMIT_TOLERANCE) {
								_state = EstimatorState.IDLE;
							}
						}
						break;
						
					default:
						_logger.severe("Invalid State: " + _state);
						break;
					}
				}
				
				if (_minimumDeltaTime != Long.MAX_VALUE) {
					Thread.sleep(1000L);
					// We found an optimum
					printEventLog(log);					
					System.out.println("Optimum Rate Limit = " + _optimumRateLimit);
					_neighbor.setSegmentXmitRateLimit(_optimumRateLimit);
				} else {
					_logger.severe("Failed to find optimum rate limit");
				}
				
			} catch (InterruptedException e) {
				// Nothing
				
			} catch (Exception e) {
				_logger.log(Level.SEVERE, "RateEstimatorXmitThread: ", e);
				_neighbor.setSegmentXmitRateLimit(_initialRateLimit);
				printEventLog(log);
			}
			
			synchronized (_sync) {
				_xmitThread = null;
			}
		}

		private void printEventLog(Vector<RateEstimationRecord> log) {
			System.out.println("\nMeasurement Log:");
			System.out.println("Rate Limit       Duration (mSecs) Resends");
			System.out.println("---------------- ---------------- ----------------");
			for (RateEstimationRecord rec : log) {
				if (rec.duration == Long.MAX_VALUE) {
					System.out.println(
							String.format(
								"%16.1f Did not succeed", 
								rec.rateLimit));
				} else {
					System.out.println(
						String.format(
							"%16.1f %16d %16d", 
							rec.rateLimit,
							rec.duration,
							rec.resends));
				}
			}
		}

		/**
		 * Perform several tries at sending a Bundle to the current Neighbor,
		 * and waiting for confirmation that the transmit succeeded.  For ea
		 * try, measure the amount of time spent sending the Bundle.
		 * @return The amount of time, in mSecs, of the longest try; or
		 * Long.MAX_VALUE if we had a transmit failure
		 * @throws BPException on errors
		 * @throws InterruptedException if interrupted during wait
		 * @throws JDtnException on errors
		 */
		private long sendBundle() throws BPException,
				InterruptedException, JDtnException {
			
			long longestTime = 0;
			long t1, t2;
			
			// Perform several tries
			for (int retry = 0; retry < TRIES; retry++) {
				_eventQueue.clear();
				
				// Send a Bundle
				Payload payload = new Payload(_file, 0L, _file.length());
				EndPointId destEid = _neighbor.getEndPointIdStem().append(
						"/" +
						APP_NAME);
				BundleOptions options = new BundleOptions(BundleColor.RED);
				options.reportToEndPointId = BPManagement.getInstance().getEndPointIdStem();
				options.isReportBundleDeletion = true;
				options.isReportBundleReception = true;
				
				_logger.finer("sendBundle() try " + retry + " sending bundle");
				t1 = System.currentTimeMillis();
				_bundle = BpApi.getInstance().sendBundle(
						getAppRegistration(), 
						BPManagement.getInstance().getEndPointIdStem(), 
						destEid, 
						payload, 
						options);

				// Wait for an event
				_logger.finer("sendBundle() try " + retry + " awaiting events");
				EstimatorEvent event = _eventQueue.poll(EVENT_WAIT_MSECS, TimeUnit.MILLISECONDS);
				if (event == null) {
					throw new JDtnException("Timeout waiting for event");
				}
				if (event != EstimatorEvent.XMIT_SUCCESSFUL) {
					return Long.MAX_VALUE;
				}
				
				t2 = System.currentTimeMillis();
				long elapsed = t2 - t1;
				if (elapsed > longestTime) {
					longestTime = elapsed;
				}
				
				// Sleep to allow settling
				Thread.sleep(INTER_BUNDLE_SLEEP);
			}
			return longestTime;
		}

		/**
		 * Called to indicate that transmit succeeded or failed.
		 * @param event Whether transmit succeeded or failed.
		 * @throws InterruptedException If interrupted while waiting for queue
		 * space.
		 */
		public void enqueueEvent(EstimatorEvent event) throws InterruptedException {
			_eventQueue.put(event);
		}
		
	}
	
	/**
	 * Call back from BP layer when the Bundle was deleted downstream due to
	 * anomalous conditions.  i.e., a failed transmit.
	 */
	@Override
	public void onBundleDeletedDownstream(BundleId bundleId,
			EndPointId reporter, int reason) {
		_logger.fine("onBundleDeletedDownstream");
		// Make sure we are transmitting
		if (_xmitter != null) {
			// Make sure that the failed Bundle was ours
			if (_xmitter._bundle != null &&
				_xmitter._bundle.getBundleId().equals(bundleId)) {
				try {
					// Signal Transmit Failed to the estimator thread.
					_xmitter.enqueueEvent(EstimatorEvent.XMIT_FAILED);
				} catch (InterruptedException e) {
					_logger.log(Level.SEVERE, "onBundleDeletedDownstream(): ", e);
				}
			} else {
				_logger.fine("onBundleDeletedDownstream: Not our Bundle");
			}
		} else {
			_logger.severe("onBundleDeletedDownstream(): measurement not active");
		}
	}

	/**
	 * Call back from BP layer when the Bundle was successfully received by
	 * the receipient.
	 */
	@Override
	public void onBundleReceivedDownstream(BundleId bundleId,
			EndPointId reporter) {
		_logger.fine("onBundleReceivedDownstream");
		if (_xmitter != null) {
			if (_xmitter._bundle != null &&
				_xmitter._bundle.getBundleId().equals(bundleId)) {
				try {
					_xmitter.enqueueEvent(EstimatorEvent.XMIT_SUCCESSFUL);
				} catch (InterruptedException e) {
					_logger.log(Level.SEVERE, "onBundleReceivedDownstream(): ", e);
				}
			} else {
				_logger.fine("onBundleReceivedDownstream: not our Bundle");
			}
		} else {
			_logger.severe("onBundleReceivedDownstream(): measurement not active");
		}
	}

	/**
	 * Call back from BP layer when the Bundle's lifetime expired; i.e., the
	 * Bundle did not reach its intended receipient and timed out.
	 */
	@Override
	public void onBundleLifetimeExpired(BundleId bundleId) {
		_logger.fine("onBundleLifetimeExpired");
		if (_xmitter != null) {
			if (_xmitter._bundle != null &&
				_xmitter._bundle.getBundleId().equals(bundleId)) {
				try {
					_xmitter.enqueueEvent(EstimatorEvent.XMIT_FAILED);
				} catch (InterruptedException e) {
					_logger.log(Level.SEVERE, "onBundleLifetimeExpired(): ", e);
				}
			} else {
				_logger.fine("onBundleLifetimeExpired: not our Bundle");
			}
		} else {
			_logger.severe("onBundleLifetimeExpired(): measurement not active");
		}
	}

	/**
	 * Implementation supplied method to perform one receive iteration.
	 * This implementation performs the receive side of the measurement, which
	 * basically just receives and discards the Bundle sent by the transmit
	 * side.
	 * @throws Exception on errors
	 */
	@Override
	public void threadImpl() throws Exception {
		// Receive a Bundle
		Bundle bundle =
			BpApi.getInstance().receiveBundle(getAppRegistration());
		_logger.fine("threadImpl(): Received a Bundle");
		Payload payload = bundle.getPayloadBundleBlock().getPayload();
		payload.delete();
	}

	public class RateEstimationRecord {
		public double rateLimit;
		public long duration;
		public long resends;
		
		public RateEstimationRecord(
				double aRateLimit,
				long aDuration,
				long aResends) {
			rateLimit = aRateLimit;
			duration = aDuration;
			resends = aResends;
		}
	}
	
}
