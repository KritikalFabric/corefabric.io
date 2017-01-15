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
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.bp.BPException;
import com.cisco.qte.jdtn.bp.BPManagement;
import com.cisco.qte.jdtn.bp.BpApi;
import com.cisco.qte.jdtn.bp.Bundle;
import com.cisco.qte.jdtn.bp.BundleOptions;
import com.cisco.qte.jdtn.bp.EndPointId;
import com.cisco.qte.jdtn.bp.IpnEndpointId;
import com.cisco.qte.jdtn.bp.Payload;
import com.cisco.qte.jdtn.general.JDtnException;

/**
 * JDTN Counterpart to ION bpsource and bpsink test application (basically a
 * texting application, using DTN as underlying transport).
 * Sends a line of text to an ION destination.  Receives that line of text,
 * prints it, and stores it in the Media Repository.
 */
public class IonSourceSinkApp extends AbstractApp {
	private static final Logger _logger =
		Logger.getLogger(IonSourceSinkApp.class.getCanonicalName());
	
	public static final String APP_NAME = "IonSourceSink";
	public static final String APP_PATTERN =
		"IonSourceSink" + BPManagement.ALL_SUBCOMPONENTS_EID_PATTERN;
	
	public static final int QUEUE_DEPTH = 10;
	public static final String EXTENSION = ".txt";
	/**
	 * Constructor
	 * @param args not used
	 * @throws BPException on errors
	 */
	public IonSourceSinkApp(String[] args) throws BPException {
		super(APP_NAME, APP_PATTERN, QUEUE_DEPTH, args);
	}
	
	/**
	 * Shell callable API to Source an ION 'bpsource' bundle
	 * @param destEid Destination EndPoint ID Stem
	 * @param options Bundle Options
	 * @param text Text to send in the 'bpsource' bundle
	 * @throws InterruptedException if interrupted during process
	 * @throws JDtnException On various error conditions
	 */
	public void source(EndPointId destEid, BundleOptions options, String text)
	throws InterruptedException, JDtnException {
		EndPointId sourceEid = BPManagement.getInstance().getEndPointIdStem();
		sourceEid = sourceEid.append("/" + APP_NAME);
		if (!(destEid instanceof IpnEndpointId)) {
			destEid = destEid.append("/" + APP_NAME);
		}
		byte[] bytes = text.getBytes();
		Payload payload = new Payload(bytes, 0, bytes.length);
		
		_logger.info("Sending Bundle");
		Bundle bundle =
			BpApi.getInstance().sendBundle(
					getAppRegistration(), 
					sourceEid, 
					destEid, 
					payload, 
					options);
		if (_logger.isLoggable(Level.FINEST)) {
			_logger.info(bundle.dump("", true));
		}
	}
	
	@Override
	public void shutdownImpl() {
		// Nothing
	}

	@Override
	public void startupImpl() {
		// Nothing
	}

	/**
	 * Call from super from separate Thread to perform one receive cycle.
	 * In our case, we receive a Bundle, and, when we get one, display its
	 * contents and store it to the Media Repository.
	 */
	@Override
	public void threadImpl() throws Exception {
		Bundle bundle = BpApi.getInstance().receiveBundle(getAppRegistration());
		EndPointId source = bundle.getPrimaryBundleBlock().getSourceEndpointId();
		EndPointId dest = bundle.getPrimaryBundleBlock().getDestinationEndPointId();
		Payload payload = bundle.getPayloadBundleBlock().getPayload();
		_logger.info("Received BPSource Bundle from " + source);
		_logger.info("BPSource Dest = " + dest);
		String sourceStr = source.getHostNodeName();
		MediaRepository.File mediaFilename = MediaRepository.getInstance().formMediaFilename(
				APP_NAME, new Date(), sourceStr, EXTENSION);
		_logger.info("Media filename=" + mediaFilename);
		if (!payload.isBodyDataInFile()) {
			MediaRepository.getInstance().spillByteArrayToFile(
					APP_NAME, 
					payload.getBodyDataBuffer(), 
					payload.getBodyDataMemOffset(), 
					payload.getBodyDataMemLength(), 
					mediaFilename);
			String text = new String(
					payload.getBodyDataBuffer(), 
					payload.getBodyDataMemOffset(), 
					payload.getBodyDataMemLength());
			_logger.info("Payload Text=" + text);
		} else {
			_logger.info("Payload file " + payload.getBodyDataFile().getAbsolutePath());
			MediaRepository.getInstance().moveFile(
					APP_NAME, 
					payload.getBodyDataFile().getAbsolutePath(), 
					mediaFilename);
		}
		payload.delete();
	}

}
