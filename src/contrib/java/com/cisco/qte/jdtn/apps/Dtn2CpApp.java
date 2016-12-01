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
import java.util.logging.Logger;

import com.cisco.qte.jdtn.bp.BPException;
import com.cisco.qte.jdtn.bp.BPManagement;
import com.cisco.qte.jdtn.bp.BpApi;
import com.cisco.qte.jdtn.bp.Bundle;
import com.cisco.qte.jdtn.bp.BundleOptions;
import com.cisco.qte.jdtn.bp.EndPointId;
import com.cisco.qte.jdtn.bp.Payload;
import com.cisco.qte.jdtn.general.JDtnException;

/**
 * JDTN implementation of the DTN2 dtncp and dtncpd apps, to test interop
 * with DTN2.
 */
public class Dtn2CpApp extends AbstractApp {

	private static final Logger _logger =
		Logger.getLogger(Dtn2CpApp.class.getCanonicalName());
	
	private static final String SOURCE_QUERY_KEYWORD = "source=";
	private static final String DEST_QUERY_KEYWORD = "file=";

	public static final String SOURCE_TAG = "send";
	public static final String DEST_TAG = "recv";
	public static final String APP_NAME = "dtncp";
	public static final String APP_TAG = "dtncp";
	public static final String APP_PATTERN = 
		APP_TAG + BPManagement.ALL_SUBCOMPONENTS_EID_PATTERN;
	
	public static final int QUEUE_DEPTH = 10;
	
	public Dtn2CpApp(String[] args) throws BPException {
		super(APP_NAME,	APP_PATTERN, QUEUE_DEPTH, args);
	}
	
	/**
	 * App Startup
	 * @see com.cisco.qte.jdtn.apps.AbstractApp#startupImpl()
	 */
	@Override
	public void startupImpl() {
		// Nothing
	}

	/**
	 * App Shutdown
	 * @see com.cisco.qte.jdtn.apps.AbstractApp#shutdownImpl()
	 */
	@Override
	public void shutdownImpl() {
		// Nothing
	}

	/**
	 * Shell API to send a File of any type
	 * @param sourceFile The path of the File to Send
	 * @param destStem The EndPointId stem for the destination
	 * @param options Bundling options
	 * @throws InterruptedException If interrupted during the process
	 * @throws JDtnException On errors
	 */
	public void sendFile(
			File sourceFile, 
			EndPointId destStem, 
			BundleOptions options) 
	throws InterruptedException, JDtnException {
		// Source EID is of the form "dtn://blah/dtncp/send?source=path"
		// Dest EID is of the form "dtn://blah/dtncp/recv?file=filename"
		EndPointId sourceEid = 
			BPManagement.getInstance().getEndPointIdStem().append(
					"/" + APP_TAG + "/" + SOURCE_TAG + 
					"?" + SOURCE_QUERY_KEYWORD + sourceFile.getAbsolutePath());
		EndPointId destEid = destStem.append(
				"/" + APP_TAG + "/" + DEST_TAG + 
				"?" + DEST_QUERY_KEYWORD + sourceFile.getName());
		Payload payload = new Payload(sourceFile, 0L, sourceFile.length());
		BpApi.getInstance().sendBundle(
				getAppRegistration(), 
				sourceEid, 
				destEid, 
				payload, 
				options);
	}
	
	/**
	 * Receive Thread callout.  We are registered to receive on demux tag
	 * "dtncp".  No matter what the destination file is specified in the
	 * destination EndPointId, we ignore that and place the File in the
	 * Media Repository.  This is a concession to the Android environment, where
	 * the file system is extremely compartmented security-wise.
	 * @see com.cisco.qte.jdtn.apps.AbstractApp#threadImpl()
	 */
	@Override
	public void threadImpl() throws Exception {
		// Receive the Bundle
		Bundle bundle = BpApi.getInstance().receiveBundle(getAppRegistration());
		EndPointId destEid = bundle.getPrimaryBundleBlock().getDestinationEndPointId();
		
		// Parse out the path from the destination EID.  The only part we
		// retain is the filename itself.
		// Dest EID is of the form "dtn://blah/dtncp/recv?file=filename"
		String query = destEid.getQuery();
		String massagedQuery = new String(query);
		// Remove the query "?"
		if (massagedQuery.startsWith("?")) {
			massagedQuery = massagedQuery.substring(1);
		}
		// Remove the query "file="
		if (!massagedQuery.startsWith(DEST_QUERY_KEYWORD)) {
			_logger.warning(
					"Ignoring Received 'cp' with mal-formed query string: " + 
					query);
			return;
		}
		massagedQuery = massagedQuery.substring(DEST_QUERY_KEYWORD.length());
		if (massagedQuery.length() == 0) {
			_logger.warning("Received 'cp' with mal-formed query string: " +
					query);
		}
		// Result is filename
		String filename = massagedQuery;
		
		// Determine the filename in the media repository
		Date date = new Date();
		File mediaFilename = MediaRepository.getInstance().formMediaFilename(
				APP_NAME, 
				date, 
				destEid.getHostNodeName(), 
				filename);
		
		// Save the payload as a file in the Media Repository
		Payload payload = bundle.getPayloadBundleBlock().getPayload();
		if (payload.isBodyDataInFile()) {
			_logger.info("Received File:" +
					" Path=" + mediaFilename.getAbsolutePath() +
					" Length=" + payload.getBodyDataFileLength());
			MediaRepository.getInstance().moveFile(
					APP_NAME,
					payload.getBodyDataFile().getAbsolutePath(),
					mediaFilename);
		} else {
			_logger.info("Received File:" +
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
