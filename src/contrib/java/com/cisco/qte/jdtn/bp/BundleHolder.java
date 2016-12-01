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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.general.GeneralManagement;

/**
 * A mechanism which holds Bundles for a specified delay period, or until
 * it is removed.
 */
public class BundleHolder {
	private static final Logger _logger =
		Logger.getLogger(BundleHolder.class.getCanonicalName());
	
	private static BundleHolder _instance = null;
	
	private ArrayList<BundleHoldTuple> _holdList = 
		new ArrayList<BundleHoldTuple>();
	public HashMap<Bundle, BundleHoldTuple> _bundleHashMap =
		new HashMap<Bundle, BundleHoldTuple>();
	
	private Timer _timer = null;
	
	/**
	 * Get singleton instance
	 * @return Singleton instance
	 */
	public static BundleHolder getInstance() {
		if (_instance == null) {
			_instance = new BundleHolder();
		}
		return _instance;
	}
	
	/**
	 * Protected constructor to enforce singleton pattern
	 */
	protected BundleHolder() {
		// Nothing
	}
	
	/**
	 * Start this component
	 */
	public synchronized void start() {
		_logger.finer("start()");
		if (_timer != null) {
			throw new IllegalStateException(
				"start() called but already started");
		}
		_holdList.clear();
		_bundleHashMap.clear();
		_timer = new Timer();
	}
	
	/**
	 * Stop this component
	 */
	public synchronized void stop() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("stop()");
		}
		if (_timer == null) {
			throw new IllegalStateException(
				"stop() called but already stopped");
		}
		_timer.cancel();
		_timer.purge();
		_timer = null;
		while (!_holdList.isEmpty()) {
			BundleHoldTuple tuple = _holdList.remove(0);
			tuple.callback.notifyBundleHoldCancel(tuple.bundle);
		}
		_bundleHashMap.clear();
	}
	
	/**
	 * Add given Bundle to BundleHolder for specified delay period.  We keep
	 * a record of the Bundle.  When the specified delay time elapses, we
	 * callback through given callback interface.
	 * @param bundle Given Bundle
	 * @param delay Given delay, mSecs
	 * @param callback Whom to callback when delay period expires
	 * @return True if operation is successful, false otherwise
	 */
	public synchronized boolean addBundle(
			Bundle bundle, 
			long delay, 
			BundleHoldCallback callback) {
		if (_timer == null) {
			_logger.severe("BundleHolder has not been started");
			return false;
		}
		if (_bundleHashMap.containsKey(bundle)) {
			_logger.severe("BundleHolder already holding Bundle " +
					bundle.getBundleId());
			return false;
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("addBundle; bundle=" + bundle.getBundleId() + "; delay=" + delay);
		}
		bundle.addRetentionConstraint(
				Bundle.RETENTION_CONSTRAINT_DELAY_HOLD);
		final BundleHoldTuple tuple = new BundleHoldTuple();
		tuple.bundle = bundle;
		tuple.delay = delay;
		tuple.callback = callback;
		_holdList.add(tuple);
		_bundleHashMap.put(bundle, tuple);
		
		tuple.timerTask =
			new TimerTask() {

			@Override
			public void run() {
				synchronized (BundleHolder.getInstance()) {
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("BundleHolder.TimerTask: Bundle " + 
								tuple.bundle.getBundleId());
					}
					tuple.bundle.removeRetentionConstraint(
						Bundle.RETENTION_CONSTRAINT_DELAY_HOLD);
					_holdList.remove(tuple);
					_bundleHashMap.remove(tuple.bundle);
					tuple.callback.notifyBundleHoldDone(tuple.bundle);
				}
			}
		};
		_timer.schedule(tuple.timerTask, delay);
		return true;
	}
	
	/**
	 * Remove given Bundle from BundleHolder delay hold.
	 * @param bundle Given Bundle
	 * @return True if operation is successful, false otherwise
	 */
	public synchronized boolean removeBundle(Bundle bundle) {
		if (_timer == null) {
			_logger.severe(
				"BundleHolder has not been started");
			return false;
		}
		BundleHoldTuple tuple = _bundleHashMap.get(bundle);
		if (tuple == null) {
			_logger.severe(
				"Bundle is not being held in BundleHolder: " +
					bundle.getBundleId());
			return false;
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("removeBundle; bundle=" + bundle.getBundleId());
		}
		bundle.removeRetentionConstraint(Bundle.RETENTION_CONSTRAINT_DELAY_HOLD);
		_holdList.remove(tuple);
		_bundleHashMap.remove(bundle);
		tuple.timerTask.cancel();
		tuple.callback.notifyBundleHoldCancel(bundle);
		return true;
	}
	
	/**
	 * Dump the state of the BundleHolder
	 * @param indent Amount of indentation
	 * @param detail Whether to produce detailed dump
	 * @return Dump
	 */
	public synchronized String dump(String indent, boolean detail) {
		StringBuilder sb = new StringBuilder(indent + "BundleHolder");
		sb.append(indent + "  NItems=" + _holdList.size() + "\n");
		if (detail) {
			for (BundleHoldTuple tuple : _holdList) {
				sb.append(indent + "  BundleHoldTuple\n");
				sb.append(indent + "    Delay=" + tuple.delay + "\n");
				sb.append(tuple.bundle.dump(indent + "    ", detail));
			}
		}
		return sb.toString();
	}
	
	/**
	 * A tuple class to hold the Bundle being held, it's delay, the callback
	 * when hold is done, and the TimerTask timing the delay.
	 */
	public class BundleHoldTuple {
		/** Bundle being held */
		public Bundle bundle;
		/** Hold elay, mSecs*/
		public long delay;
		/** Whom to callback when hold expires or is cancelled */
		public BundleHoldCallback callback;
		/** TimerTask timing the delay */
		public TimerTask timerTask;
		
		/**
		 * Overridden to make 'bundle' the only field compared
		 */
		@Override
		public boolean equals(Object other) {
			if (other == null || !(other instanceof BundleHoldTuple)) {
				return false;
			}
			BundleHoldTuple otherTuple = (BundleHoldTuple)other;
			return
				bundle.equals(otherTuple.bundle);
		}
		
		/**
		 * Overridden cause we overrode equals()
		 */
		@Override
		public int hashCode() {
			return bundle.hashCode();
		}
	}
	
	/**
	 * Callback interface for delay held Bundles
	 */
	public interface BundleHoldCallback {
		/**
		 * Specified delay has expired, Bundle has been removed from delay hold.
		 * @param bundle Affected Bundle.
		 */
		public void notifyBundleHoldDone(Bundle bundle);
		
		/**
		 * Delay hold has been cancelled for a Bundle
		 * @param bundle Affected Bundle
		 */
		public void notifyBundleHoldCancel(Bundle bundle);
	}
	
}
