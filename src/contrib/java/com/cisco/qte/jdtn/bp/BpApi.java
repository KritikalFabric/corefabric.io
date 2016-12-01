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
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;

/**
 * The API for the Bundle Protocol Agent
 */
public class BpApi extends AbstractStartableComponent 
implements Iterable<AppRegistration> {

	private static final Logger _logger =
		Logger.getLogger(BpApi.class.getCanonicalName());
	
	private static final BpApi _instance = new BpApi();
	
	// List of current AppRegistrations
	private final ArrayList<AppRegistration> _registrationList =
		new ArrayList<AppRegistration>();
	// Map of AppPattern to AppRegistration
	private final HashMap<String, AppRegistration> _appPatternMap =
		new HashMap<String, AppRegistration>();
	// The "default" app; app to which bundles are delivered when no other
	//   app matches.
	private AppRegistration _defaultAppRegistration = null;
	
	/**
	 * Get singleton instance
	 * @return Singleton instance
	 */
	public static BpApi getInstance() {
		return _instance;
	}
	
	private BpApi() {
		super("BpApi");
	}
	
	/* (non-Javadoc)
	 * @see com.cisco.qte.jdtn.component.AbstractStartableComponent#startImpl()
	 */
	@Override
	protected void startImpl() {
		// Nothing
	}

	/* (non-Javadoc)
	 * @see com.cisco.qte.jdtn.component.AbstractStartableComponent#stopImpl()
	 */
	@Override
	protected void stopImpl() throws InterruptedException {
		// Nothing
	}
	
	/**
	 * Register an application with BpApi
	 * @param appPattern The App Pattern of the Application.  A regular expression 
	 * partially describing EndPointIds that
   	 * the app will be generating and for which the app will receive Bundles.
	 * See AppRegistration for explanation.
	 * @param listener Listener interface which App must satisfy
	 * @param queueDepth Receive queue depth
	 * @return A Registration object required for further service
	 * @throws BPException on errors
	 */
	public AppRegistration registerApplication(
			String appPattern, 
			int queueDepth,
			BpListener listener) 
	throws BPException {
		_logger.finer("registerApplication(" + appPattern + ")");
		
		if (!isStarted()) {
			throw new BPException("BpApi has not been started");
		}
		
		AppRegistration registration = 
			new AppRegistration(appPattern, listener, queueDepth);
		if (findMatchingAppRegistration(appPattern) != null) {
			throw new BPException("Already another application registered for " +
					appPattern);
		}
		synchronized (_registrationList) {
			_registrationList.add(registration);
			_appPatternMap.put(appPattern, registration);
		}
		return registration;
	}
	
	/**
	 * Register an application with BpApi
	 * @param appNumber The IPN App Nuumber
	 * @param listener Listener interface which App must satisfy
	 * @param queueDepth Receive queue depth
	 * @return A Registration object required for further service
	 * @throws BPException on errors
	 */
	public AppRegistration registerApplication(
			int appNumber,
			int queueDepth,
			BpListener listener)
	throws BPException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("registerApplication(" + appNumber + ")");
		}
		
		if (!isStarted()) {
			throw new BPException("BpApi has not been started");
		}
		String appPattern = Integer.toString(appNumber);
		AppRegistration registration = 
			new AppRegistration(appPattern, listener, queueDepth);
		if (findMatchingAppRegistration(appPattern) != null) {
			throw new BPException("Already another application registered for " +
					appPattern);
		}
		synchronized (_registrationList) {
			_registrationList.add(registration);
			_appPatternMap.put(appPattern, registration);
		}
		return registration;
	}
	
	/**
	 * Find an AppRegistration which matches the given App Pattern.  This is
	 * used internally for various purposes.
	 * @param appPattern Given App Pattern
	 * @return Matching AppRegistration or null if none
	 */
	private AppRegistration findMatchingAppRegistration(String appPattern) {
		return _appPatternMap.get(appPattern);
	}
	
	/**
	 * Find an AppRegistration which matches given EndPointId.  This is used
	 * to decide whether to deliver a received Bundle to an app.
	 * @param endPointId Given EndPointId.
	 * @return AppRegistration found or null if none
	 */
	public AppRegistration findRegistrationForEndPointId(EndPointId endPointId) {
		synchronized (_registrationList) {
			for (AppRegistration registration : _registrationList) {
				if (registration.matches(endPointId)) {
					return registration;
				}
			}
		}
		// No match found for registered apps.  See if it matches default app.
		// It matches default app if the EndPointId is addressed to us (our
		// EndPointId stem is a prefix of the given EndPointId) AND
		// there is a default app registered.
		if (BPManagement.getInstance().getEndPointIdStem().isPrefixOf(endPointId) &&
			_defaultAppRegistration != null) {
			return _defaultAppRegistration;
		}
		return null;
	}
	
	/**
	 * Deregister previously registered application
	 * @param registration Registration for application
	 * @throws BPException on errors
	 */
	public void deregisterApplication(AppRegistration registration)
	throws BPException {
		_logger.finer("deregisterApplication()");
		if (!isStarted()) {
			throw new BPException("BpApi has not been started");
		}
		
		if (GeneralManagement.isDebugLogging()) {
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(registration.dump("", true));
			}
		}
		
		if (findMatchingAppRegistration(registration.getAppPattern()) == null) {
			throw new BPException("No App registered for AppId " + registration.getAppPattern());
		}
		synchronized (_registrationList) {
			_appPatternMap.remove(registration.getAppPattern());
			_registrationList.remove(registration);
		}
	}
	
	/**
	 * Send a Bundle with the payload consisting of the given Payload body
	 * @param registration Registration for Application
	 * @param sourceEid Source EndPointId
	 * @param destEid Dest EndPointId
	 * @param payload The Payload for the Bundle
	 * @param options Options for the Send; can be null, in which case default
	 * options (reliable delivery) are in effect.
	 * @return The Bundle sent
	 * @throws JDtnException 
	 * @throws InterruptedException 
	 */
	public Bundle sendBundle(
			AppRegistration registration,
			EndPointId sourceEid,
			EndPointId destEid,
			Payload payload, 
			BundleOptions options)
	throws InterruptedException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("sendBundle()");
		}
		if (!isStarted()) {
			throw new BPException("BpApi has not been started");
		}
		
		if (GeneralManagement.isDebugLogging()) {
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(registration.dump("App:    ", true));
				_logger.finest(sourceEid.dump("Source: ", true));
				_logger.finest(destEid.dump("Dest:   ", true));
			}
		}

		if (registration == null) {
			throw new BPException("Registration null");
		}

		final String appPattern = registration.getAppPattern();
		if (appPattern == null) {
			throw new BPException("No app registered");
		}
		
		if (findMatchingAppRegistration(appPattern) == null) {
			throw new BPException("No App registered for AppId " + appPattern);
		}

		if (options == null) {
			options = new BundleOptions(payload);
		}
		
		Bundle bundle = 
			new Bundle(
					sourceEid, 
					destEid, 
					payload, 
					options);
		if (GeneralManagement.isDebugLogging()) {
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(bundle.dump("", true));
			}
		}
		
		BPProtocolAgent.getInstance().enqueueOutboundBundle(bundle);
		
		return bundle;
	}
	
	/**
	 * Cancel given Bundle
	 * @param registration Given AppRegistration
	 * @param bundle Bundle to Cancel
	 * @throws InterruptedException  If interrupted during wait
	 * @throws JDtnException On immediate errors
	 */
	public void cancelBundle(
			AppRegistration registration, 
			Bundle bundle) 
	throws JDtnException, InterruptedException {

		if (!isStarted()) {
			throw new BPException("BpApi has not been started");
		}
		
		if (findMatchingAppRegistration(registration.getAppPattern()) == null) {
			throw new BPException("No App registered for AppId " + registration.getAppPattern());
		}
		
		BPProtocolAgent.getInstance().cancelBundleTransmission(bundle);
	}
	
	/**
	 * Blocking Bundle Receive; with timeout
	 * @param registration The registration for the App
	 * @param waitTime Max amount of time to wait, mSecs
	 * @return Received Bundle or null if none received w/in specified time
	 * @throws JDtnException on errors
	 * @throws InterruptedException If interrupted during wait.
	 */
	public Bundle receiveBundle(
			AppRegistration registration,
			long waitTime)
	throws JDtnException, InterruptedException {
		
		if (!isStarted()) {
			throw new BPException("BpApi has not been started");
		}
		
		if (findMatchingAppRegistration(registration.getAppPattern()) == null) {
			throw new BPException("No App registered for AppId " + registration.getAppPattern());
		}
		
		return registration.waitNextBundle(waitTime);
	}
	
	/**
	 * Blocking Bundle Receive
	 * @param registration The registration for the App
	 * @return Received Bundle
	 * @throws JDtnException on errors
	 * @throws InterruptedException If interrupted during wait.
	 */
	public Bundle receiveBundle(
			AppRegistration registration)
	throws JDtnException, InterruptedException {
		
		if (!isStarted()) {
			throw new BPException("BpApi has not been started");
		}
		
		if (findMatchingAppRegistration(registration.getAppPattern()) == null) {
			throw new BPException("No App registered for AppId " + registration.getAppPattern());
		}
		
		return registration.waitNextBundle();
	}
	
	/**
	 * Called from BPProtocolAgent when a BundleStatusReport arrives.  We
	 * in turn notify all of our App Agents.
	 * @param report Bundle Status Report
	 */
	public void onBundleStatusReport(
			BundleStatusReport report, 
			EndPointId reporter) {
		BundleId bundleId = 
			new BundleId(
					report.getSourceEndPointId(), 
					report.getBundleTimestamp());
		ArrayList<AppRegistration> tempRegList;
		synchronized (_registrationList) {
			tempRegList =
				new ArrayList<AppRegistration>(_registrationList);
		}
		for (AppRegistration registration : tempRegList) {
			if (report.isReportBundleDeleted()) {
				registration.getListener().onBundleDeletedDownstream(
						bundleId, 
						reporter,
						report.getReasonCode());
			}
			if (report.isReportBundleDelivered()) {
				registration.getListener().onBundleDeliveredDownstream(
						bundleId, 
						reporter);
			}
			if (report.isReportBundleForwarded()) {
				registration.getListener().onBundleForwardedDownstream(
						bundleId, 
						reporter);
			}
			if (report.isReportBundleReceived()) {
				registration.getListener().onBundleReceivedDownstream(
						bundleId, 
						reporter);
			}
			if (report.isReportCustodyAccepted()) {
				registration.getListener().onBundleCustodyAccepted(
						bundleId, 
						reporter);
			}
		}
	}
	
	/**
	 * Called from BPProtocolAgent when a Bundle in our custody expires.  We
	 * call back to applications
	 * @param bundleId BundleId of expired Bundle
	 */
	public void onBundleExpired(BundleId bundleId) {
		ArrayList<AppRegistration> tempRegList;
		synchronized (_registrationList) {
			tempRegList =
				new ArrayList<AppRegistration>(_registrationList);
		}
		for (AppRegistration registration : tempRegList) {
			registration.getListener().onBundleLifetimeExpired(bundleId);
		}
	}
	
	/**
	 * Called from BPProtocolAgent when we release custody of a Bundle.  We
	 * callback to applications
	 * @param bundleId BundleId of Bundle
	 */
	public void onBundleCustodyReleased(BundleId bundleId) {
		ArrayList<AppRegistration> tempRegList;
		synchronized (_registrationList) {
			tempRegList =
				new ArrayList<AppRegistration>(_registrationList);
		}
		for (AppRegistration registration : tempRegList) {
			registration.getListener().onBundleCustodyReleased(bundleId);
		}
	}
	
	/**
	 * Called from BPProtocolAgent to report that we could not accept custody
	 * of a Bundle because doing so would cause us to exceed resource
	 * retention constraints.
	 * @param bundle Bundle which was rejected
	 */
	public void onRetentionLimitExceeded(Bundle bundle) {
		ArrayList<AppRegistration> tempRegList;
		synchronized (_registrationList) {
			tempRegList =
				new ArrayList<AppRegistration>(_registrationList);
		}
		for (AppRegistration registration : tempRegList) {
			registration.getListener().onRetentionLimitExceeded(bundle);
		}
	}
	
	/**
	 * Called from BPProtocolAgent when Bundle transmission got cancelled
	 * for some reason.  We callback to applications.
	 * @param bundle The Bundle that got cancelled
	 * @param reason Why cancelled, one of CancelSegment.REASON_CODE_*
	 */
	public void onBundleTransmissionCancelled(Bundle bundle, byte reason) {
		ArrayList<AppRegistration> tempRegList;
		synchronized (_registrationList) {
			tempRegList =
				new ArrayList<AppRegistration>(_registrationList);
		}
		for (AppRegistration registration : tempRegList) {
			registration.getListener().onBundleTransmissionCanceled(bundle, reason);
		}
	}
	
	/**
	 * Dump this object
	 * @param indent How much to indent
	 * @param detailed Whether detailed dump desired
	 * @return the dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "BpApi\n");
		ArrayList<AppRegistration> tempRegList;
		synchronized (_registrationList) {
			tempRegList =
				new ArrayList<AppRegistration>(_registrationList);
		}
		for (AppRegistration reg : tempRegList) {
			sb.append(reg.dump(indent + "  ", detailed));
		}
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return dump("", false);
	}

	/**
	 * Get an Iterator over the list of AppRegistrations, suitable for use in
	 * a for each loop
	 * @return what I said
	 */
	@Override
	public Iterator<AppRegistration> iterator() {
		return _registrationList.iterator();
	}

	/** The "default" app; app to which bundles are delivered when no other
	    app matches. */
	public AppRegistration getDefaultAppRegistration() {
		return _defaultAppRegistration;
	}

	/**
	 * Set the "default" app; app to which bundles are delivered when no other
     * app matches. 
     * @param appRegistration AppRegistration which calling app wants
     * to register.  Can be null if want to deregister.
     * @throws BPException if another app already registered as default app.
    */
	public void setDefaultAppRegistration(
			AppRegistration appRegistration) throws BPException {
		if (appRegistration == null) {
			// Caller is deregistering as default app.
			if (_defaultAppRegistration == null) {
				throw new BPException("There is no registered default app");
			}
			_defaultAppRegistration = null;
			
		} else {
			// Caller is registering as default app.
			if (_defaultAppRegistration != null) {
				throw new BPException("Another application already registered as default app");
			}
			this._defaultAppRegistration = appRegistration;
		}
	}

}
