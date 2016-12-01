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
import java.util.Random;
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
import com.cisco.qte.jdtn.general.Utils;

/**
 * DTN2 version of ping.  Not compatible with our custom version; they both
 * use the same App tag, "ping".  We are shifting toward using the dtn2
 * compatible ping exclusively.
 */
public class Dtn2PingApp extends AbstractApp {
	private static final Logger _logger =
		Logger.getLogger(Dtn2PingApp.class.getCanonicalName());
	
	public static final String APP_NAME = "Dtn2Ping";
	public static final String APP_PATTERN =
		"ping" + BPManagement.ALL_SUBCOMPONENTS_EID_PATTERN;
	
	public static final int QUEUE_DEPTH = 10;
	public static final long PING_LIFETIME_SECS = 4;
	public static final long PING_REPLY_WAIT_MSECS = 10000L;
	
	public static final String PING_TAG = "dtnping!";
	public static final byte[] PING_TAG_BYTES = PING_TAG.getBytes();
	
	private ArrayBlockingQueue<Bundle> _pingReplyQueue =
		new ArrayBlockingQueue<Bundle>(QUEUE_DEPTH);
	
	private Random _rng = new Random();
	private int _pid = Math.abs(_rng.nextInt());
	private int _nonce = 0;
	private int _seqNo = 0;
	private int _timeStampSecs;
	
	/**
	 * Constructor
	 * @param args Command line arguments: ignored
	 * @throws BPException from super constructor
	 */
	public Dtn2PingApp(String[] args) throws BPException {
		super(APP_NAME, APP_PATTERN, QUEUE_DEPTH, args);
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("Dtn2PingApp()");
		}
	}
	
	/**
	 * Callout from super startup() method to signify that Management says its
	 * time to start up.
	 * Called upon app install.
	 * @see com.cisco.qte.jdtn.apps.AbstractApp#startupImpl()
	 */
	@Override
	public void startupImpl() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("startupImpl()");
		}
		// Register this app as the default app
		try {
			BpApi.getInstance().setDefaultAppRegistration(getAppRegistration());
		} catch (BPException e) {
			_logger.log(Level.SEVERE, "setDefaultAppRegistration", e);
		}
		_seqNo = 0;
	}

	/** 
	 * Call upon system shutdown, so that we can clean up before shutting down.
	 * @see com.cisco.qte.jdtn.apps.AbstractApp#shutdownImpl()
	 */
	@Override
	public void shutdownImpl() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("shutdownImpl()");
		}
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
	 * Shell.  Runs for an extended period of time and blocks.  Allows
	 * caller to monitor progress of Ping events via the given callbacks.
	 * @param toEid EndPointId to Ping; should be a "bare" Eid, such as
	 * "dtn:node", without any app specifiers appended, although this isn't
	 * enforced.
	 * @param count Number Number of Ping Cycles to perform
	 * @param callbacks Methods to callback on various Ping events
	 */
	public void doPing(String toEid, int count, PingCallbacks callbacks) {
		doPing(toEid, count, PING_LIFETIME_SECS, callbacks);
	}
	
	/**
	 * Perform a set of Ping/Ping Reply Cycles.  Intended to be called from 
	 * Shell.  Runs for an extended period of time and blocks.  Allows
	 * caller to monitor progress of Ping events via the given callbacks.
	 * @param toEid EndPointId to Ping; should be a "bare" Eid, such as
	 * "dtn:node", without any app specifiers appended, although this isn't
	 * enforced.
	 * @param count Number Number of Ping Cycles to perform
	 * @param lifetimeSecs Bundle lifetime, in seconds
	 * @param callbacks Methods to callback on various Ping events
	 */
	public void doPing(String toEid, int count, long lifetimeSecs, PingCallbacks callbacks) {
		// Remove stale ping replies
		_pingReplyQueue.clear();
		try {
			EndPointId destEid = EndPointId.createEndPointId(toEid).append("/ping");
			EndPointId sourceEid = EndPointId.createEndPointId(
					BPManagement.getInstance().getEndPointIdStem().append("/ping." + _pid));
			//send loop starts
			long minTime = Long.MAX_VALUE;
			long maxTime = Long.MIN_VALUE;
			long averageTime = 0;
			int receiveCount = 0;
			int errorCount = 0;
			int transmitCount = 0;
			for (; transmitCount < count; transmitCount++) {
				long transmitTime = System.currentTimeMillis();
				_timeStampSecs = (int)(transmitTime / 1000);

				// Build the Ping Payload
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				DataOutputStream dos = new DataOutputStream(baos);
				dos.write(PING_TAG_BYTES);
				dos.writeInt(_seqNo);
				_nonce = _rng.nextInt();
				dos.writeInt(_nonce);
				dos.writeInt(_timeStampSecs);
				dos.flush();
	            baos.close();
				
                callbacks.onPingRequest(destEid, transmitCount, count);
                
                if (GeneralManagement.isDebugLogging() && 
                	_logger.isLoggable(Level.FINEST)) {
                	_logger.finest("Ping Request:  src=" + sourceEid.dump("", true));
                	_logger.finest("               dest=" + destEid.dump("", true));
                	_logger.finest("               Payload=");
                	_logger.finest(Utils.dumpBytes(baos.toByteArray(), 0, baos.size(), true));
                }
                
                // Send Ping Request
                Payload payload = new Payload(baos.toByteArray(), 0, baos.size());
                BundleOptions bundleOptions = new BundleOptions();
                bundleOptions.lifetime = lifetimeSecs;
                bundleOptions.reportToEndPointId = sourceEid;
                bundleOptions.isReportBundleDeletion = true;
                
				BpApi.getInstance().sendBundle(
						getAppRegistration(), 
						sourceEid, 
						destEid, 
						payload, 
						bundleOptions);

				// Receive Ping Reply
				Bundle recvBundle = _pingReplyQueue.poll(
						lifetimeSecs * 1000L + 4, 
						TimeUnit.MILLISECONDS);
				if (recvBundle != null) {
	                long endTime = System.currentTimeMillis();
	                
	                // Parse the Payload of the Ping Reply
					payload = recvBundle.getPayloadBundleBlock().getPayload();
					
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("Ping Reply");
						_logger.finest("Ping Reply:  src=" + 
								recvBundle.getPrimaryBundleBlock().
									getSourceEndpointId().dump("", true));
						_logger.finest("             dest=" + 
								recvBundle.getPrimaryBundleBlock().
									getDestinationEndPointId().dump("", true));
						_logger.finest("             Payload=");
						if (!payload.isBodyDataInFile()) {
							_logger.finest(
									Utils.dumpBytes(
											payload.getBodyDataBuffer(), 
											0, 
											payload.getBodyDataMemLength(), 
											true));
						}
					}
					
					InputStream is = null;
					if (payload.isBodyDataInFile()) {
						is = new FileInputStream(payload.getBodyDataFile());
					
					} else {
						is = new ByteArrayInputStream(
								payload.getBodyDataBuffer(), 
								payload.getBodyDataMemOffset(), 
								payload.getBodyDataMemLength());					
					}
					DataInputStream dis = new DataInputStream(is);
					byte[] pingTagBytes = new byte[PING_TAG_BYTES.length];
					dis.read(pingTagBytes);
					String pingTag = new String(pingTagBytes);
					if (!pingTag.equals(PING_TAG)) {
						throw new JDtnException(
								"Ping reply tag: expected=" + PING_TAG + 
								"; actual=" + pingTag);
					}
					int rcvdSeqNo = dis.readInt();
					if (rcvdSeqNo != _seqNo) {
						throw new JDtnException(
								"Ping reply segno: expected=" + _seqNo +
								"; actual=" + rcvdSeqNo);
					}
					int nonce = dis.readInt();
					if (nonce != _nonce) {
						throw new JDtnException(
								"Ping reply nonce: expected=" + _nonce +
								"; actual=" + nonce);
					}
					int timeSecs = dis.readInt();
					if (timeSecs != _timeStampSecs) {
						_logger.warning(
								"Ping reply Time field: expected=" + _timeStampSecs +
								"; actual=" + timeSecs);
					}
					
	                dis.close();

	                long rtt = endTime - transmitTime;
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
				
				_seqNo++;
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
	 * Perform a set of Ping/Ping Reply Cycles.  Intended to be called from 
	 * management.  Runs for an extended period of time and blocks.
	 * @param toEid EndPointId to Ping; should be a "bare" Eid, such as
	 * "dtn:node", without any app specifiers appended, although this isn't
	 * enforced.  All Ping events, such as replies and done, result in logging
	 * of such events.
	 * @param count Number Number of Ping Cycles to perform
	 * @param lifetimeSecs Bundle lifetime, seconds
	 */
	public void doPing(String toEid, int count, long lifetimeSecs) {
		doPing(toEid, count, lifetimeSecs, new PingDefaultCallbacks());
	}
	
	/**
	 * Receive Thread for the Ping App.  Receives a single Ping Bundle and
	 * replies to it.  The Received Bundle is delivered to us by virtue of
	 * the fact that either:
	 * <ul>
	 *   <li>The Bundle Destination Eid matches our app's registration.
	 *   <li>The Bundle Destination Eid doesn't match any other app registration
	 *       and we are registered as the default app.  This is necessary
	 *       because Ping Reply destination EIDs are of the form
	 *       dtn://blah/ping.number
	 * </ul>
	 */
	@Override
	public void threadImpl() throws Exception {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("threadImpl()");
		}
		
		// Receive a Bundle; either a Ping or a non-registered EID
		Bundle bundle = BpApi.getInstance().receiveBundle(getAppRegistration());
		EndPointId source = bundle.getPrimaryBundleBlock().getSourceEndpointId();
		EndPointId dest = bundle.getPrimaryBundleBlock().getDestinationEndPointId();
		Payload payload = bundle.getPayloadBundleBlock().getPayload();
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("Received Bundle from " + source);
			_logger.fine("Bundle Dest = " + dest);
			_logger.fine("Dest service path = " + dest.getServicePath());
		}

		// Rules for DTN2 Ping:
		// If the destination EID is of the form dtn://node/ping then this
		// is a Ping request and we must reply to it.
		EndPointId eid = BPManagement.getInstance().getEndPointIdStem().append("/ping");
		if (dest.equals(eid)) {
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Sending Reply");
			}
			try {
				dest = EndPointId.createEndPointId(source);
				source = EndPointId.createEndPointId(BPManagement.getInstance().getEndPointIdStem().append("/ping"));
				if (GeneralManagement.isDebugLogging()) {
					_logger.fine("Sending reply to " + dest);
					_logger.fine("        source=" + source);
				}
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
		
		// Otherwise, it could be a ping reply. A Ping Reply is distinguished
		// by a destination EID of the form "dtn:node/ping.nnnnn" where
		// "nnnnn" is an unparseable process ID from the other node.
		// We need to deliver it to the
		// foreground ping origination process.
		} else {
			if (dest.getServicePath().startsWith("/ping.")) {
				if (GeneralManagement.isDebugLogging()) {
					_logger.fine("Delivering to PingReplyQueue");
				}
				if (!_pingReplyQueue.offer(bundle)) {
					_logger.severe("Ping Reply Queue is full");
				}
		
			// Otherwise it is a mis-addressed Bundle; no app registrations matched
			// it and we don't match it.
			} else {
				_logger.severe("No Application is registered to handle incoming Bundle; " +
						"Destination EID= " + dest.getEndPointIdString());
				_logger.severe("Source EID: " + source.getEndPointIdString());
				payload.delete();
			}
		}
		
	}

}
