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
import com.cisco.qte.jdtn.general.JDtnException;
import org.kritikal.fabric.contrib.jdtn.BlobAndBundleDatabase;

/**
 * Photo Note Application
 */
public class PhotoApp extends AbstractApp {

	private static final Logger _logger =
		Logger.getLogger(PhotoApp.class.getCanonicalName());
	
	public static final String APP_NAME = "Photo";
	public static final String APP_PATTERN =
		"photo" + BPManagement.ALL_SUBCOMPONENTS_EID_PATTERN;
	
	public static final int QUEUE_DEPTH = 10;

	private static final String EXTENSION = ".jpg";
	
	/**
	 * Constructor
	 * @param args not used
	 * @throws BPException on errors
	 */
	public PhotoApp(String[] args) throws BPException {
		super(APP_NAME, APP_PATTERN, QUEUE_DEPTH, args);
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
	 * Called from management to send a Photo Note.  We will construct a
	 * Bundle and enqueue it into BP, and then return at that point.  There
	 * is currently no way to track the Bundle's progress.
	 * @param toEid Destination EndPointId; base Eid w/out app suffix
	 * @param filePath Path of photo to send
	 * @param bundleOptions Bundling Options
	 * @throws JDtnException on errors
	 * @throws InterruptedException Interrupted on wait
	 */
	public void sendPhoto(
			String toEid, 
			MediaRepository.File filePath,
			BundleOptions bundleOptions) 
	throws InterruptedException, JDtnException {
		// Determine dest and source Eids
		EndPointId dest = EndPointId.createEndPointId(toEid + "/" + APP_NAME.toLowerCase());
		EndPointId source = BPManagement.getInstance().getEndPointIdStem().append("/" + APP_NAME.toLowerCase());

		java.sql.Connection con = BlobAndBundleDatabase.getInstance().getInterface().createConnection();
		try {

			if (!filePath.exists(con)) {
				throw new JDtnException("File " + filePath.getAbsolutePath() + " does not exist");
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
	 * Called from super to perform one photo note receive cycle.  We pause
	 * indefinitely waiting to receive a photo note.  Once we receive one,
	 * we save it to the media repository.
	 */
	@Override
	public void threadImpl() throws Exception {
		// Receive a Photo Note Bundle
		Bundle bundle = BpApi.getInstance().receiveBundle(getAppRegistration());
		Payload payload = bundle.getPayloadBundleBlock().getPayload();
		
		MediaRepository.File mediaFilename = MediaRepository.getInstance().formMediaFilename(
				APP_NAME, 
				new Date(), 
				bundle.getPrimaryBundleBlock().getSourceEndpointId().getHostNodeName(), 
				EXTENSION);
		
		// Store payload to Media Repository
		if (payload.isBodyDataInFile()) {
			_logger.info("Received Photo Note:" +
					" Path=" + mediaFilename.getAbsolutePath() +
					" Length=" + payload.getBodyDataFileLength());
			MediaRepository.getInstance().moveFile(
					APP_NAME,
					payload.getBodyDataFile().getAbsolutePath(),
					mediaFilename);
		} else {
			_logger.info("Received Photo Note:" +
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
