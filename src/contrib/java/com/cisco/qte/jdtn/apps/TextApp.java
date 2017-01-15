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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
 * TextNote Application
 */
public class TextApp extends AbstractApp {

	private static final Logger _logger =
		Logger.getLogger(TextApp.class.getCanonicalName());
	
	public static final String APP_NAME = "Text";
	public static final String APP_PATTERN =
		"text" + BPManagement.ALL_SUBCOMPONENTS_EID_PATTERN;
	
	public static final int QUEUE_DEPTH = 10;
	
	private static int _messageCounter = 0;
	
	/**
	 * Constructor; merely calls super constructor
	 * @param args not used
	 * @throws BPException
	 */
	public TextApp(String[] args) throws BPException {
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
	 * Called from Management to send a Text message.  We will construct a
	 * Bundle and enqueue it into BP, and then return at that point.  There
	 * is currently no way to track the Bundle's progress.
	 * @param toEid Destination EndPointId.  Should be a "bare" Eid (e.g.,
	 * dtn:node, without appending app id)
	 * @param message Message to send Text Message to send
	 * @param bundleOptions Desired Bundling Options
	 * @throws InterruptedException If interrupted while waiting.
	 * @throws JDtnException
	 */
	public void sendText(String toEid, String message, BundleOptions bundleOptions)
	throws InterruptedException, JDtnException {
		java.sql.Connection con = BlobAndBundleDatabase.getInstance().getInterface().createConnection();
		try {
			// Determine dest and source Eids
			EndPointId dest = EndPointId.createEndPointId(toEid + "/" + APP_NAME.toLowerCase());
			EndPointId source = BPManagement.getInstance().getEndPointIdStem().append("/" + APP_NAME.toLowerCase());

			// Set up the bundle payload; send in-memory
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(bos);
			try {
				dos.writeByte(0x30);						// CmdCode
				dos.writeInt(_messageCounter++);			// message counter
				dos.writeLong(System.currentTimeMillis());	// shell start time
				dos.writeInt(message.length());
				dos.writeChars(message);

			} catch (IOException e) {
				throw new BPException(e);
			} finally {
				try {
					dos.close();
				} catch (IOException e) {
					// Ignore
				}
				try {
					bos.close();
				} catch (IOException e) {
					// Ignore
				}
			}

			// Send a Bundle containing the Payload.
			Payload payload = new Payload(bos.toByteArray(), 0, bos.size());

			try { con.commit(); } catch (SQLException e) {
				_logger.warning(e.getMessage());
			}

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
	 * Called from super to perform one text note receive cycle.  We pause
	 * indefinitely waiting to receive a text note.  Once we receive one,
	 * we save it to the media repository.
	 */
	@Override
	public void threadImpl() throws Exception {
		java.sql.Connection con = BlobAndBundleDatabase.getInstance().getInterface().createConnection();
		try {
			// Receive a Text Note Bundle
			Bundle bundle = BpApi.getInstance().receiveBundle(getAppRegistration());
			_logger.fine("Received TextNote Bundle");

			Payload payload = bundle.getPayloadBundleBlock().getPayload();

			// Extract the text note from the Bundle
			InputStream is;
			if (payload.isBodyDataInFile()) {
				is = payload.getBodyDataFile().inputStream(con);
			} else {
				is = new ByteArrayInputStream(
						payload.getBodyDataBuffer(),
						payload.getBodyDataMemOffset(),
						payload.getBodyDataMemLength());
			}
			DataInputStream dis = new DataInputStream(is);
			dis.readByte(); // skip the command
			dis.readInt();	// skip the message id
			dis.readLong();	// skip the timestamp
			int length = dis.readInt();
			StringBuffer sb = new StringBuffer();
			for (int j = 0; j < length; j++) {
				sb.append(dis.readChar());
			}
			String rxMessage = sb.toString();
			is.close();
			dis.close();

			// Delete the payload if it's in a File
			if (payload.isBodyDataInFile()) {
				if (!payload.getBodyDataFile().delete(con)) {
					_logger.severe("Cannot delete payload file " + payload.getBodyDataFile());
				}
			}

			try { con.commit(); } catch (SQLException e) {
				_logger.warning(e.getMessage());
			}

			// Save message to media repository
			MediaRepository.File file =
				MediaRepository.getInstance().formMediaFilename(
						APP_NAME,
						new Date(),
						bundle.getPrimaryBundleBlock().getSourceEndpointId().getHostNodeName(),
						".txt");
			MediaRepository.getInstance().spillByteArrayToFile(
					APP_NAME,
					rxMessage.getBytes(),
					0,
					length,
					file);
			_logger.info("Text Message from: " +
					bundle.getPrimaryBundleBlock().getSourceEndpointId().getEndPointIdString() +
					" " + file.getAbsolutePath() +
					" " + length +
					" bytes");
			_logger.info(rxMessage);
		}
		finally {
			try { con.close(); } catch (SQLException e) { _logger.warning(e.getMessage()); }
		}
	}

}
