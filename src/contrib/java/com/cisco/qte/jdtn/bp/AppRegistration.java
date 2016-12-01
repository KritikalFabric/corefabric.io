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

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.cisco.qte.jdtn.bp.EidScheme;

/**
 * An Application Registration into BpApi.  Properties include:
 * <ul>
 *   <li>AppPattern - A R.E. expression partially describing EndPointIds that
 *       the app will be generating and for which the app will receive Bundles.
 *   <li>Listener - Callback interface to the App.
 *   <li>RegistrationState
 * </ul>
 * Let us suppose that an application is interested in Bundles with the EndPointId
 * <code> dtn:xxx/Text/yyy </code>
 * <p>
 * In this case, the App Pattern might be
 * <code> /Text(/[.+])* </code>
 * <p>
 * From the App Pattern, we will construct a derived property: the
 * EndPointPattern.  This is constructed by prepending this
 * node's EndPointIdStem to the App Pattern.  The EndPointPattern forms the
 * pattern we will use to match the Destination EndPointId of incoming Bundles
 * to a particular application.  E.g., if the EndPointIdStem for this node is
 * <code> dtn:foo </code>
 * then we will construct an EndPointPattern
 * <code> dtn:foo/Test/Text(/[.+])* </code>
 * <p>
 * Limitations against RFC 5050:
 *   <ul>
 *     <li>We do not implement PASSIVE state in Application Registrations.  All
 *     registrations are ACTIVE all of the time.  This limitation comes about
 *     because:
 *     <ul>
 *       <li>I don't fully understand the spec regarding delivery/non-delivery of
 *           Bundles when registration is in PASSIVE state.
 *       <li>From what I do understand about it, I don't see the need.
 *     </ul>
 *     <li>Because of the above point, we do not implemented the "defer delivery"
 *         delivery failure mode.  We only implement the "Abandon" delivery
 *         failure mode.
 *   </ul>
 */
public class AppRegistration {
	/** The application pattern, a regular expression */
	protected String _appPattern;
	/** The Listener interface provided by the App */
	protected BpListener _listener;
	/** The R.E. Pattern which any EndPointId must match to be considered applicable
	 * to registered application */
	protected String _endPointPattern;
	// R.E. pattern for matching EndPointIds.
	private Pattern _pattern;
	// How deep App's receive queue should be
	private int _queueDepth;
	
	// Received Bundle queue
	private LinkedBlockingQueue<Bundle> _receiveQueue;
	
	/**
	 * Constructor
	 * @param appPattern App Pattern for the registration. This is a regular
	 * expression which is used to distinguish Bundles for this Application
	 * among all other Applications.
	 * @param listener App's listener
	 * @param queueDepth receive queue depth
	 * @throws BPException On Regular Expression syntax error
	 */
	public AppRegistration(
			String appPattern, 
			BpListener listener, 
			int queueDepth)
	throws BPException {
		setAppPattern(appPattern);
		setListener(listener);
		setQueueDeptth(queueDepth);
		if (BPManagement.getInstance().getEidScheme() == EidScheme.DTN_EID_SCHEME) {
			setEndPointPattern(
					BPManagement.getInstance().getEndPointIdStem().getEndPointIdString() + "/" +
					appPattern);
		} else {
			String eidPattern = 
				BPManagement.getInstance().getEndPointIdStem().getEndPointIdString();
			int index = eidPattern.lastIndexOf(".");
			eidPattern = eidPattern.substring(0, index);
			eidPattern += "." + appPattern;
			setEndPointPattern(eidPattern);
		}
		
		_receiveQueue = new LinkedBlockingQueue<Bundle>(queueDepth);
		
		try {
			_pattern = Pattern.compile(getEndPointPattern());
			
		} catch (PatternSyntaxException e) {
			throw new BPException(e);
		}
	}
	
	/**
	 * Determine if the given EndPointId matches this Registration.
	 * @param endPointId Given EndPointId
	 * @return True if matches
	 */
	public boolean matches(EndPointId endPointId) {
		Matcher matcher = _pattern.matcher(endPointId.getEndPointIdString());
		return matcher.matches();
	}
	
	/**
	 * Deliver received Bundle to this App.  Called from BpApi to enqueue received
	 * Bundles.
	 * @param bundle Received Bundle
	 * @param waitTime Max time to wait for queue space to become available, mSecs
	 * @throws InterruptedException if interrupted while waiting
	 */
	public void deliverBundle(Bundle bundle, long waitTime) 
	throws InterruptedException {
		_receiveQueue.offer(bundle, waitTime, TimeUnit.MILLISECONDS);
	}
	
	/**
	 * Wait for next received Bundle for this App, up to given timeout.  Called
	 * from BpApi to wait for next bundle to be enqueued.
	 * @param waitTime Max time to wait, mSecs
	 * @return Bundle received or null
	 * @throws InterruptedException if interrupted during wait
	 */
	public Bundle waitNextBundle(long waitTime) throws InterruptedException {
		return _receiveQueue.poll(waitTime, TimeUnit.MILLISECONDS);
	}
	
	/**
	 * Wait for next received Bundle for this App.  Can Block indefinitely.
	 * Called from BpApi.
	 * @return Bundle received
	 * @throws InterruptedException if interrupted during wait
	 */
	public Bundle waitNextBundle() throws InterruptedException {
		return _receiveQueue.take();
	}
	
	/**
	 * Dump this object
	 * @param indent How much to indent
	 * @param detailed Whether detailed dump desired
	 * @return the dump
	 */
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "AppRegistration\n");
		sb.append(indent + "  AppPattern=" + getAppPattern() + "\n");
		sb.append(indent + "  EndPointPattern=" + getEndPointPattern() + "\n");
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return dump("", false);
	}
	
	/** The application pattern; a R.E. */
	public String getAppPattern() {
		return _appPattern;
	}

	/** The application pattern; a R.E. */
	protected void setAppPattern(String endPointAppId) {
		this._appPattern = endPointAppId;
	}

	/** The Listener interface provided by the App */
	protected BpListener getListener() {
		return _listener;
	}

	/** The Listener interface provided by the App */
	private void setListener(BpListener listener) {
		this._listener = listener;
	}

	/** The Prefix which any EndPointId must match to be considered applicable
	 * to registered application */
	public String getEndPointPattern() {
		return _endPointPattern;
	}

	/** The Prefix which any EndPointId must match to be considered applicable
	 * to registered application */
	private void setEndPointPattern(String endPointPattern) {
		this._endPointPattern = endPointPattern;
	}

	/** Receive queue depth */
	public int getQueueDepth() {
		return _queueDepth;
	}

	/** Receive queue depth */
	private void setQueueDeptth(int queueDepth) {
		this._queueDepth = queueDepth;
	}
	
}
