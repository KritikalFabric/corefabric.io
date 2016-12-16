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
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
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

/**
 * Ping Application for JDTN.  This Ping functionality is something invented
 * by us as a management tool.
 * NOTE: THIS IS NOW OBSOLETE.  WE'RE USING Dtn2PingApp now instead.
 */
public class PingApp extends AbstractApp {

	private static final Logger _logger =
		Logger.getLogger(PingApp.class.getCanonicalName());
	
	public static final String APP_NAME = "Ping";
	public static final String APP_PATTERN =
		"ping" + BPManagement.ALL_SUBCOMPONENTS_EID_PATTERN;
	
	public static final int QUEUE_DEPTH = 10;
	public static final long PING_LIFETIME_SECS = 4;
	public static final long PING_REPLY_WAIT_MSECS = 10000L;
	
	private ArrayBlockingQueue<Bundle> _pingReplyQueue =
		new ArrayBlockingQueue<Bundle>(QUEUE_DEPTH);
	
	/**
	 * Constructor
	 * @param args not used
	 * @throws BPException from super constructor
	 */
	public PingApp(String[] args) throws BPException {
		super(APP_NAME, APP_PATTERN, QUEUE_DEPTH, args);
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("Ping init");
		}
	}

	/**
	 * Called when we are starting up.  We use this opportunity to register
	 * as the "default app".
	 */
	@Override
	public void startupImpl() {
		// Register this app as the default app
		try {
			BpApi.getInstance().setDefaultAppRegistration(getAppRegistration());
		} catch (BPException e) {
			_logger.log(Level.SEVERE, "setDefaultAppRegistration", e);
		}
	}

	/**
	 * Called when we are shutting down.  We clear the Ping Reply Queue and
	 * deregister as the "default app"
	 */
	@Override
	public void shutdownImpl() {
		_pingReplyQueue.clear();
		try {
			// Deregister as default app registration
			BpApi.getInstance().setDefaultAppRegistration(null);
		} catch (BPException e) {
			_logger.log(Level.SEVERE, "setDefaultAppRegistration(null)", e);
		}
	}

	/**
	 * Perform a set of Ping/Ping Reply Cycles.  Intended to be called from 
	 * management.  Runs for an extended period of time and blocks.  Allows
	 * caller to monitor progress of Ping events via the given callbacks.
	 * @param toEid EndPointId to Ping; should be a "bare" Eid, such as
	 * "dtn:node", without any app specifiers appended, although this isn't
	 * enforced.
	 * @param count Number Number of Ping Cycles to perform
	 * @param callbacks Methods to callback on various Ping events
	 */
	public void doPing(String toEid, int count, PingCallbacks callbacks) {
		// Remove stale ping replies
		_pingReplyQueue.clear();
		try {
			EndPointId destEid = EndPointId.createEndPointId(toEid);
			EndPointId sourceEid = EndPointId.createEndPointId(
					BPManagement.getInstance().getEndPointIdStem().append("/ping"));
			
			//send loop starts
			long minTime = Long.MAX_VALUE;
			long maxTime = Long.MIN_VALUE;
			long averageTime = 0;
			int receiveCount = 0;
			int errorCount = 0;
			int transmitCount = 0;
			for (; transmitCount < count; transmitCount++) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				DataOutputStream dos = new DataOutputStream(baos);
                dos.writeByte(0x30); // echo request
                dos.writeInt(transmitCount); // ping packet number
                dos.writeLong(System.currentTimeMillis()); // ping start time
                String txMessage = "Ping Test " + transmitCount;
                dos.writeInt(txMessage.length());
                dos.writeChars(txMessage);
                dos.flush();
                baos.close();

                callbacks.onPingRequest(destEid, transmitCount, count);
                
                // Send Ping Request
                Payload payload = new Payload(baos.toByteArray(), 0, baos.size());
                BundleOptions bundleOptions = new BundleOptions();
                bundleOptions.lifetime = PING_LIFETIME_SECS;
				BpApi.getInstance().sendBundle(
						getAppRegistration(), 
						sourceEid, 
						destEid, 
						payload, 
						null);
				
				// Receive Ping Reply
				Bundle recvBundle = _pingReplyQueue.poll(
						PING_REPLY_WAIT_MSECS, 
						TimeUnit.MILLISECONDS);
				if (recvBundle != null) {
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("Ping Reply");
					}
	                long endTime = System.currentTimeMillis();
					payload = recvBundle.getPayloadBundleBlock().getPayload();
					InputStream is = null;
					if (payload.isBodyDataInFile()) {
						is = payload.getBodyDataFile().inputStream();
					
					} else {
						is = new ByteArrayInputStream(
								payload.getBodyDataBuffer(), 
								payload.getBodyDataMemOffset(), 
								payload.getBodyDataMemLength());					
					}
					DataInputStream dis = new DataInputStream(is);
	                @SuppressWarnings("unused")
					byte cmd = (byte) (dis.readByte() >>> 4); // skip the command
	                @SuppressWarnings("unused")
					int pingID = dis.readInt();
	                long startTime = dis.readLong();
	                int length = dis.readInt();
	                StringBuffer sb = new StringBuffer();
	                for (int j = 0; j < length; j++) {
	                    sb.append(dis.readChar());
	                }
	                @SuppressWarnings("unused")
					String rxMessage = sb.toString();
	                dis.close();
	
	                long rtt = endTime - startTime;
	                callbacks.onPingReply(rtt, ++receiveCount);
	
	                if (rtt > maxTime) {
	                    maxTime = rtt;
	                }
	                if (rtt < minTime) {
	                    minTime = rtt;
	                }
	
	                averageTime += rtt;
					
	                // finally, remove the file
	                payload.delete();

				} else {
					callbacks.onPingReplyTimeout(++errorCount);
                    if (errorCount > 2) {
                        break;
                    }
				}
			}
            if (receiveCount > 0) {
            	callbacks.onPingDone(transmitCount, receiveCount, minTime, maxTime, averageTime);
            } else {
                // no reply's received
            	callbacks.onNoPingReplies(destEid);
            }
            
		} catch (Exception e) {
			_logger.log(Level.SEVERE, "ping", e);
			callbacks.onPingException(e);
		}
	}
	
	/**
	 * Perform a set of Ping/Ping Reply Cycles.  Intended to be called from 
	 * management.  Runs for an extended period of time and blocks.
	 * @param toEid EndPointId to Ping; should be a "bare" Eid, such as
	 * "dtn:node", without any app specifiers appended, although this isn't
	 * enforced.  All Ping events, such as replies and done, result in logging
	 * of such events.
	 * @param count Number Number of Ping Cycles to perform
	 */
	public void doPing(String toEid, int count) {
		doPing(toEid, count, new PingDefaultCallbacks());
	}
	
	/**
	 * Receive Thread for the Ping App.  Receives a single Ping Bundle and
	 * replies to it.  The Received Bundle is delivered to us by virtue of
	 * the fact that either:
	 * <ul>
	 *   <li>The Bundle Destination Eid matches our app's registration.
	 *   <li>The Bundle Destination Eid matches our default registration.
	 * </ul>
	 */
	@Override
	public void threadImpl() throws JDtnException, InterruptedException {
		Bundle bundle = BpApi.getInstance().receiveBundle(getAppRegistration());
		EndPointId source = bundle.getPrimaryBundleBlock().getSourceEndpointId();
		EndPointId dest = bundle.getPrimaryBundleBlock().getDestinationEndPointId();
		Payload payload = bundle.getPayloadBundleBlock().getPayload();
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("Received Ping from " + source);
			_logger.fine("Ping Dest = " + dest);
		}
		
		// Rules for our "invented" ping functionality Mark I:
		// If the destination Eid exactly matches our node Eid (e.g., dtn:node,
		// without any appended app identifiers), then this is
		// a ping request and we need to reply to it.  We reply to it by setting:
		// SourceEid = our node Eid + "/Admin"
		// DestEid = Received Bundle Source Eid
		if (dest.equals(BPManagement.getInstance().getEndPointIdStem())) {
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Sending Reply");
			}
			try {
				dest = EndPointId.createEndPointId(source);
				source = EndPointId.createEndPointId(BPManagement.getInstance().getEndPointIdStem().append("/Admin"));
				BpApi.getInstance().sendBundle(
						getAppRegistration(), 
						source, 
						dest, 
						payload, 
						null);
			} catch (Exception e) {
				_logger.severe("Error sending Ping reply: " + e.getMessage());
				_logger.severe("Error ignored");
			}
		}
		
		// Otherwise, it could be a ping reply. A Ping Reply is distinguished
		// by a destination EID of the form "dtn:node/ping"
		// We need to deliver it to the
		// foreground ping origination process.
		else if (dest.getEndPointIdString().endsWith("/ping")) {
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Delivering to PingReplyQueue");
			}
			if (!_pingReplyQueue.offer(bundle)) {
				_logger.severe("Ping Reply Queue is full");
			}
			
		}
		
		// Otherwise it is a mis-addressed Bundle; no app registrations matched
		// it and we don't match it.
		else {
			_logger.severe("No Application is registered to handle incoming Bundle; " +
					"Destination EID= " + dest.getEndPointIdString());
			_logger.severe("Source EID: " + source.getEndPointIdString());
			payload.delete();
		}
		
	}

}
