/**
Copyright (c) 2011, Cisco Systems, Inc.
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
import java.sql.SQLException;
import java.util.Date;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.bp.BPException;
import com.cisco.qte.jdtn.bp.BPManagement;
import com.cisco.qte.jdtn.bp.BpApi;
import com.cisco.qte.jdtn.bp.Bundle;
import com.cisco.qte.jdtn.bp.BundleOptions;
import com.cisco.qte.jdtn.bp.EndPointId;
import com.cisco.qte.jdtn.bp.Payload;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import org.kritikal.fabric.contrib.jdtn.BlobAndBundleDatabase;

/**
 *
 */
public class BPSendFileApp extends AbstractApp {

	private static final Logger _logger =
		Logger.getLogger(BPSendFileApp.class.getCanonicalName());
	
	public static final String APP_NAME = "BPSendFile";
	public static final String APP_PATTERN =
		"BPSendFile" + BPManagement.ALL_SUBCOMPONENTS_EID_PATTERN;
	
	public static final int QUEUE_DEPTH = 10;
	public static final long PING_LIFETIME_SECS = 4;
	public static final long PING_REPLY_WAIT_MSECS = 10000L;
	
	private static final String EXTENSION = ".bp";
	
	public BPSendFileApp(String[] args) throws BPException {
		super(APP_NAME, APP_PATTERN, QUEUE_DEPTH, args);
	}
	
	/**
	 * @see com.cisco.qte.jdtn.apps.AbstractApp#startupImpl()
	 */
	@Override
	public void startupImpl() {
		// Nothing
	}

	/**
	 * @see com.cisco.qte.jdtn.apps.AbstractApp#shutdownImpl()
	 */
	@Override
	public void shutdownImpl() {
		// Nothing
	}

	public void sendFile(
			MediaRepository.File filePath,
			EndPointId dest,
			BundleOptions bundleOptions) 
	throws JDtnException, InterruptedException {
		java.sql.Connection con = BlobAndBundleDatabase.getInstance().getInterface().createConnection();
		try {
			// Determine dest and source Eids
			EndPointId source = BPManagement.getInstance().getEndPointIdStem().append(
					"/" + APP_NAME);

			if (!filePath.exists(con)) {
				throw new JDtnException(
						"File " + filePath.getAbsolutePath() +
						" does not exist");
			}

			// Set up bundle payload - in-file Payload
			Payload payload = new Payload(filePath, 0L, filePath.length(con));

			try { con.commit(); } catch (SQLException e) {
				_logger.warning(e.getMessage());
			}
		
			// Send a Bundle containing the Payload.
			BpApi.getInstance().sendBundle(
					getAppRegistration(),
					source,
					dest,
					payload,
					bundleOptions);
		}
		finally {
			try { con.close(); } catch (SQLException e) { _logger.warning(e.getMessage()); }
		}
	}
	
	/**
	 * @see com.cisco.qte.jdtn.apps.AbstractApp#threadImpl()
	 */
	@Override
	public void threadImpl() throws Exception {
		Bundle bundle = BpApi.getInstance().receiveBundle(getAppRegistration());
		EndPointId source = bundle.getPrimaryBundleBlock().getSourceEndpointId();
		EndPointId dest = bundle.getPrimaryBundleBlock().getDestinationEndPointId();
		Payload payload = bundle.getPayloadBundleBlock().getPayload();
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("Received BPSendFile Bundle from " + source);
			_logger.fine("Bundle Dest = " + dest);
			_logger.fine("Dest service path = " + dest.getServicePath());
		}
		MediaRepository.File mediaFilename = MediaRepository.getInstance().formMediaFilename(
				APP_NAME, 
				new Date(), 
				bundle.getPrimaryBundleBlock().getSourceEndpointId().getHostNodeName(), 
				EXTENSION);
		
		// Store payload to Media Repository
		if (payload.isBodyDataInFile()) {
			_logger.info("Received BPSendFile:" +
					" Path=" + mediaFilename.getAbsolutePath() +
					" Length=" + payload.getBodyDataFileLength());
			MediaRepository.getInstance().moveFile(
					APP_NAME,
					payload.getBodyDataFile().getAbsolutePath(),
					mediaFilename);
		} else {
			_logger.info("Received BPSendFile:" +
					" Path=" + mediaFilename.getAbsolutePath() +
					" Length=" + payload.getBodyDataMemLength());
			MediaRepository.getInstance().spillByteArrayToFile(
					APP_NAME,
					payload.getBodyDataBuffer(), 
					payload.getBodyDataMemOffset(), 
					payload.getBodyDataMemLength(), 
					mediaFilename);
		}
		


	}

}
