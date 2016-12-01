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

import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.bp.AppRegistration;
import com.cisco.qte.jdtn.bp.BPException;
import com.cisco.qte.jdtn.bp.BpApi;
import com.cisco.qte.jdtn.bp.BpListener;
import com.cisco.qte.jdtn.bp.Bundle;
import com.cisco.qte.jdtn.bp.BundleId;
import com.cisco.qte.jdtn.bp.EndPointId;
import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.ltp.CancelSegment;

/**
 * Abstract superclass for all built-in Apps
 */
public abstract class AbstractApp extends AbstractStartableComponent
implements BpListener, Runnable {

	private static final Logger _logger =
		Logger.getLogger(AbstractApp.class.getCanonicalName());
	
	public static final long JOIN_TIME_MSECS = 1000L;
	/** App Pattern for the App (see AppRegistration for more info) */
	protected String _appPattern;
	/** Receive Queue depth for the App */
	protected int _queueDepth;
	/** App Registration */
	protected AppRegistration _appRegistration = null;
	/** App Receive Thread */
	protected Thread _thread;
	/** Whether App installation should be saved in config file */
	protected boolean _saveInConfig = true;
	/** Arguments passed to App */
	protected String[] _args = null;
	/** Whether BpApi App registration is by AppPattern String or
	 * App Number (ION style)
	 */
	protected boolean _isIonApp = false;
	/** If ION style app, the app number its registered under */
	protected int _appNumber;
	
	/**
	 * Constructor
	 * @param name Name of App
	 * @param appPattern App Pattern
	 * @param queueDepth Queue Depth
	 * @param args Arguments to Application (null if none required)
	 * @throws BPException on errors
	 */
	public AbstractApp(String name, String appPattern, int queueDepth, String[] args)
	throws BPException {
		super(name);
		setName(name);
		setArguments(args);
		_appPattern = appPattern;
		_queueDepth = queueDepth;
		_isIonApp = false;
	}

	/**
	 * Constructor
	 * @param name Name of App
	 * @param appNumber ION App Number
	 * @param queueDepth Queue Depth
	 * @param args Arguments to Application (null if none required)
	 * @throws BPException on errors
	 */
	public AbstractApp(String name, int appNumber, int queueDepth, String[] args)
	throws BPException {
		super(name);
		setName(name);
		setArguments(args);
		_appPattern = Integer.toString(appNumber);
		_appNumber = appNumber;
		_queueDepth = queueDepth;
		_isIonApp = true;
	}

	/**
	 * The subclass can call this to inform that App Construction cannot
	 * continue because of App Specific errors; allows super a chance to
	 * clean up after itself.  In this case, this means unregistering from
	 * BpApi.
	 */
	protected void abortAppConstruction() {
		if (_appRegistration != null) {
			try {
				BpApi.getInstance().deregisterApplication(_appRegistration);
			} catch (BPException e) {
				// Ignore
			}
			_appRegistration = null;
		}
	}
	
	/**
	 * Start up the App.
	 */
	@Override
	protected void startImpl() {
		try {
			// Register app to receive bundles
			if (!_isIonApp) {
				_appRegistration = BpApi.getInstance().registerApplication(
						_appPattern, 
						_queueDepth, 
						this);
			} else {
				_appRegistration = BpApi.getInstance().registerApplication(
						_appNumber, 
						_queueDepth, 
						this);
			}
			startupImpl();
			_thread = new Thread(this);
			_thread.start();
			
		} catch (Exception e) {
			_logger.log(Level.SEVERE, "startup()", e);
			
			try {
				BpApi.getInstance().deregisterApplication(_appRegistration);
				AppManager.getInstance().uninstallApp(getName());
				
			} catch (JDtnException e1) {
				// Nothing
			} catch (InterruptedException e1) {
				// Nothing
			}
		}
	}
	
	/**
	 * Shutdown the app
	 * @throws BPException on errors
	 */
	@Override
	protected void stopImpl() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("Shutdown App " + getName());
		}
		if (_thread != null) {
			_thread.interrupt();
			try {
				_thread.join(JOIN_TIME_MSECS);
			} catch (InterruptedException e) {
				// Nothing
			}
			_thread = null;
		}
		try {
			BpApi.getInstance().deregisterApplication(_appRegistration);
		} catch (BPException e) {
			_logger.log(Level.SEVERE, "shutdown()", e);
		}
		shutdownImpl();
	}

	/**
	 * Main method for the receive Thread; calls out to implementation's
	 * threadImpl() to perform one receive iteration.
	 */
	@Override
	public void run() {
		try {
			while (!Thread.interrupted()) {
				threadImpl();
			}
		} catch (InterruptedException e) {
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("AbstractApp(" + getName() + ") Interrupted");
			}
			
		} catch (Exception e) {
			if (GeneralManagement.isDebugLogging()) {
				_logger.log(Level.SEVERE, "AbstractApp(" + getName() + ")", e);
			}
		}
	}
	
	/**
	 * Dump this object
	 * @param indent Amount of indentation
	 * @param detailed Whether detailed dump desired
	 * @return Dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "Application\n");
		sb.append(indent + "  Name=" + getName() + "\n");
		sb.append(indent + "  Class=" + this.getClass().getCanonicalName() + "\n");
		sb.append(indent + "  State=" + (_thread != null ? "Started" : "Stopped") + "\n");
		if (_args != null) {
			sb.append(indent + "  Args=");
			for (String arg : _args) {
				sb.append(arg + " ");
			}
			sb.append("\n");
		}
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return dump("", false);
	}
	
	/**
	 * Implementation supplied method called when App is started up
	 */
	public abstract void startupImpl();
	
	/**
	 * Implementation supplied method called when App is shut down
	 */
	public abstract void shutdownImpl();
	
	/**
	 * Implementation supplied method to perform one receive iteration.
	 * @throws Exception on errors
	 */
	public abstract void threadImpl() throws Exception;
	
	/**
	 * Implementation can override; called when Bundle Custody Accepted downstream.
	 * @param bundleId The BundleId of the affected Bundle
	 * @param reporter EndPointId of the BP Node which accepted custody
	 */
	@Override
	public void onBundleCustodyAccepted(
			BundleId bundleId, 
			EndPointId reporter) {
		// Ignore
	}

	/**
	 * Implementation can override; called when this node releases custody
	 * of a Bundle.
	 * @param bundleId The BundleId of the affected Bundle
	 */
	@Override
	public void onBundleCustodyReleased(BundleId bundleId) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onBundleCustodyReleased");
		}
	}

	/**
	 * Implementation can override; called when Bundle Deleted due to
	 * anomalous conditions by downstream BP node.
	 * @param bundleId The BundleId of the affected Bundle
	 * @param reporter EndPointId of the BP Node which reported the event
	 */
	@Override
	public void onBundleDeletedDownstream(
			BundleId bundleId, 
			EndPointId reporter,
			int reason) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onBundleDeletedDownstream");
		}
	}

	/**
	 * Implementation can override; called when Bundle delivered to
	 * destination BP node.
	 * @param bundleId The BundleId of the affected Bundle
	 * @param reporter EndPointId of the BP Node which reported the event
	 */
	@Override
	public void onBundleDeliveredDownstream(
			BundleId bundleId,
			EndPointId reporter) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onBundleDeliveredDownstream");
		}
	}

	/**
	 * Implementation can override; called when Bundle forwarded to
	 * a downstream BP node.
	 * @param bundleId The BundleId of the affected Bundle
	 * @param reporter EndPointId of the BP Node which reported the event
	 */
	@Override
	public void onBundleForwardedDownstream(
			BundleId bundleId,
			EndPointId reporter) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onBundleForwardedDownstream");
		}
	}

	/**
	 * Implementation can override; called when Bundle lifetime expired without
	 * Bundle reaching destination BP Node.
	 * @param bundleId The BundleId of the affected Bundle
	 */
	@Override
	public void onBundleLifetimeExpired(BundleId bundleId) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onBundleLifetimeExpired");
		}
	}

	/**
	 * Implementation can override; called when downstream BP node reports
	 * Bundle received.
	 * @param bundleId The BundleId of the affected Bundle
	 * @param reporter EndPointId of the BP Node which reported the event
	 */
	@Override
	public void onBundleReceivedDownstream(
			BundleId bundleId,
			EndPointId reporter) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onBundleReceivedDownstream");
		}
	}

	/**
	 * Called when bundle retention limit exceeded on an outbound bundle.  We
	 * don't do anything.  The event is logged elsewhere.
	 * @see com.cisco.qte.jdtn.bp.BpListener#onRetentionLimitExceeded(com.cisco.qte.jdtn.bp.Bundle)
	 */
	@Override
	public void onRetentionLimitExceeded(Bundle bundle) {
		// Nothing
	}

	/**
	 * Implementation can override; called when Bundle transmission was
	 * canceled.
	 * @param bundle Bundle that got canceled
	 * @param reason Why cancelled, one of CancelSegment.REASON_CODE_*
	 */
	@Override
	public void onBundleTransmissionCanceled(Bundle bundle, byte reason) {
		_logger.warning("Bundle Transmission Cancelled: " + 
				CancelSegment.reasonCodeToString(reason));
		_logger.warning(bundle.getBundleId().dump("", true));
	}
	
	/**
	 * Get the AppRegistration for this App
	 * @return What I said
	 */
	public AppRegistration getAppRegistration() {
		return _appRegistration;
	}

	/** Whether App installation should be saved in config file */
	public boolean isSaveInConfig() {
		return _saveInConfig;
	}

	/** Whether App installation should be saved in config file */
	public void setSaveInConfig(boolean saveInConfig) {
		this._saveInConfig = saveInConfig;
	}

	public String[] getArgumentss() {
		return _args;
	}
	
	public void setArguments(String[] args) {
		_args = args;
	}

}
