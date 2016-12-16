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
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.bp.BPException;
import com.cisco.qte.jdtn.bp.BPManagement;
import com.cisco.qte.jdtn.bp.BpApi;
import com.cisco.qte.jdtn.bp.Bundle;
import com.cisco.qte.jdtn.bp.BundleColor;
import com.cisco.qte.jdtn.bp.BundleOptions;
import com.cisco.qte.jdtn.bp.DefaultRoute;
import com.cisco.qte.jdtn.bp.EndPointId;
import com.cisco.qte.jdtn.bp.Payload;
import com.cisco.qte.jdtn.bp.Route;
import com.cisco.qte.jdtn.bp.RouteTable;
import com.cisco.qte.jdtn.bp.PrimaryBundleBlock.BPClassOfServicePriority;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Link;
import com.cisco.qte.jdtn.general.LinkAddress;
import com.cisco.qte.jdtn.general.Neighbor;
import com.cisco.qte.jdtn.general.NeighborsList;
import com.cisco.qte.jdtn.ltp.IPAddress;
import com.cisco.qte.jdtn.tcpcl.TcpClLink;
import com.cisco.qte.jdtn.tcpcl.TcpClManagement;
import com.cisco.qte.jdtn.tcpcl.TcpClNeighbor;

/**
 * Router Application; sources and responds to /Router/Hello messages.
 * XXX Add Route Aging
 */
public class RouterApp extends AbstractApp {

	private static final Logger _logger =
		Logger.getLogger(RouterApp.class.getCanonicalName());
	
	public static final String APP_NAME = "Router";
	public static final String APP_PATTERN =
		"Router" + BPManagement.ALL_SUBCOMPONENTS_EID_PATTERN;
	private static final String HELLO_SUBSTRING = "Hello";
	
	public static final int QUEUE_DEPTH = 10;
	public static final long SEND_HELLO_SLEEP_MSECS = 180000L;
	public static final long SEND_HELLO_THREAD_JOIN_MSECS = 2000L;
	public static final byte ROUTER_VERSION = (byte)1;
	
	private Thread _sendHelloThread = null;
	
	/**
	 * Constructor
	 * @param args not used
	 * @throws BPException on errors
	 */
	public RouterApp(String[] args) throws BPException {
		super(APP_NAME, APP_PATTERN, QUEUE_DEPTH, args);
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("RouterApp()");
		}
	}

	@Override
	public void startupImpl() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("startupImpl()");
		}
		if (_sendHelloThread == null) {
			_sendHelloThread = new Thread(new SendHelloThread());
			_sendHelloThread.start();
		}
	}

	@Override
	public void shutdownImpl() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("shutdownImpl()");
		}
		if (_sendHelloThread != null) {
			_sendHelloThread.interrupt();
			try {
				_sendHelloThread.join(SEND_HELLO_THREAD_JOIN_MSECS);
			} catch (InterruptedException e) {
				// Ignore
			}
			_sendHelloThread = null;
		}
	}

	/**
	 * Single-cycle receive.  Called from super periodically to perform a
	 * single receive.  In our case, we receive a single Router/Hello Bundle
	 * from a node in our network.  When we receive one, we add Routing
	 * information which allows us to reach that node in the future.
	 */
	@Override
	public void threadImpl() throws Exception {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("threadImpl()");
		}
		// Receive a /Router/Hello Bundle
		Bundle bundle =
			BpApi.getInstance().receiveBundle(getAppRegistration());
		EndPointId source = bundle.getPrimaryBundleBlock().getSourceEndpointId();
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("Received Hello from " + source.getEndPointIdString());
		}

		// Extract the Hello message from the Bundle
		Payload payload = bundle.getPayloadBundleBlock().getPayload();
		InputStream is = null;
		DataInputStream dis = null;
		try {
			if (payload.isBodyDataInFile()) {
				is = payload.getBodyDataFile().inputStream();
			} else {
				is = new ByteArrayInputStream(
						payload.getBodyDataBuffer(), 
						payload.getBodyDataMemOffset(), 
						payload.getBodyDataMemLength());
			}
			dis = new DataInputStream(is);
			// Timestamp
			dis.readLong();	// skip the timestamp
			// Version
			byte version = dis.readByte();
			if (version < ROUTER_VERSION) {
				_logger.severe("Invalid version in Router Hello; ignoring : " + version);
				return;
			}
			// Segment Rate Limit
			@SuppressWarnings("unused")
			double segRateLimit = dis.readDouble();
			// Burst Size
			@SuppressWarnings("unused")
			long burstSize = dis.readLong();
			// Length of IP Address
			int length = dis.readInt();
			// IP Address
			StringBuffer sb = new StringBuffer();
			for (int j = 0; j < length; j++) {
			    sb.append(dis.readChar());
			}
			String rxMessage = sb.toString();
			IPAddress ipAddress = new IPAddress(rxMessage);
			payload.delete();
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Neighbor IPAddress=" + ipAddress.toParseableString());
			}
			// See if we already know about the Neighbor originating this Hello
			Neighbor existingNeighbor =
				NeighborsList.getInstance().findNeighborByAddress(ipAddress);
			Link link = bundle.getLink();
			Route existingRoute = RouteTable.getInstance().findMatchingRoute(source);
			if (existingNeighbor != null &&
				existingNeighbor.getEndPointIdStem().isPrefixOf(source)) {
				// We know about this neighbor already
				if (existingRoute != null &&
					!(existingRoute instanceof DefaultRoute)) {
					// We have a (non-default) route to this neighbor
					if (existingRoute.getLink() == link) {
						// XXX Check for Address change
						// Route points to same link as that which Hello arrived on
						// We don't need to delete and re-add Neighbor nor Route
						// Update SegRateLimit and burstSize
						return;
					}
				}
			}
					
			// We will be installing a Neighbor and a Route for the node
			// sourcing the Hello bundle.  First, we need to remove any
			// existing Neighbor and Route for it.
			// First, remove the existing Route
			if (existingRoute != null && !(existingRoute instanceof DefaultRoute)) {
				if (GeneralManagement.isDebugLogging()) {
					_logger.fine("Removing Old Route to " + source.getEndPointIdString());
					if (_logger.isLoggable(Level.FINER)) {
						_logger.finer(existingRoute.dump("", true));
					}
				}
				RouteTable.getInstance().removeRoute(existingRoute);
			}
			
			// Remove the existing Neighbor entry, if any
			if (existingNeighbor != null) {
				if (GeneralManagement.isDebugLogging()) {
					_logger.fine("Removing Neighbor entry for " + source.getEndPointIdString());
					if (_logger.isLoggable(Level.FINER)) {
						_logger.finer(existingNeighbor.dump("", true));
					}
				}
				NeighborsList.getInstance().removeNeighbor(existingNeighbor);
			}
			
			// Add a TcpClNeighbor describing the source of the Hello
			String neighborName = source.getHostNodeName();
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Adding Neighbor entry for " + neighborName);
			}
			TcpClNeighbor neighbor = TcpClManagement.getInstance().addNeighbor(
					neighborName,
					EndPointId.createEndPointId(EndPointId.DEFAULT_SCHEME, "//" + neighborName));
			LinkAddress linkAddress = new LinkAddress(link, ipAddress);
			neighbor.addLinkAddress(linkAddress);
			neighbor.setEndPointIdStem(
					EndPointId.createEndPointId(source.getHostNodeName()));
			neighbor.setTemporary(true);
			neighbor.start();
			
			if (GeneralManagement.isDebugLogging()) {
				if (_logger.isLoggable(Level.FINER)) {
					_logger.finer(neighbor.dump("", true));
				}
			}
			
			// Add a Route to get to the node sourcing the Hello
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Adding Route for " + neighborName);
			}
			String routePattern = source.getEndPointIdString();
			routePattern += BPManagement.ALL_SUBCOMPONENTS_EID_PATTERN;
			Route route = BPManagement.getInstance().addRoute(
					neighborName, 
					routePattern, 
					link.getName(), 
					neighborName);
			route.setTemporary(true);
			
			if (GeneralManagement.isDebugLogging()) {
				if (_logger.isLoggable(Level.FINER)) {
					_logger.finer(route.dump("", true));
				}
			}
			
		} finally {
			if (is != null) {
				is.close();
			}
			if (dis != null) {
				dis.close();
			}
		}       
	}

	/**
	 * A Thread which periodically sends a /Router/Hello Bundle to whatever
	 * node is configured as the next hop neighbor for the DefaultRoute,
	 * if any.  If there is not DefaultRoute, we just sleep until one is
	 * configured.
	 */
	public class SendHelloThread implements Runnable {

		@Override
		public void run() {
			if (GeneralManagement.isDebugLogging()) {
				_logger.finer("SendHelloThread.run()");
			}
			try {
				while (!Thread.interrupted()) {
					DefaultRoute route = BPManagement.getInstance().getDefaultRoute();
					if (route != null) {
						// Build a /Router/Hello Bundle to send to the default
						// router.
						Neighbor neighbor = route.getNeighbor();
						if (!(neighbor instanceof TcpClNeighbor)) {
							_logger.warning(
									"SendHelloThread: Default Route is not " +
									"configured for TcpClNeighbor");
							return;
						}
						TcpClNeighbor tcpClNeighbor = (TcpClNeighbor)neighbor;
						if (!neighbor.getEndPointIdStem().equals(EndPointId.defaultEndPointId)) {
							EndPointId dest = neighbor.getEndPointIdStem().append(
									"/" +
									APP_NAME +
									"/" +
									HELLO_SUBSTRING);
							EndPointId source = BPManagement.getInstance().getEndPointIdStem();
							LinkAddress linkAddress = tcpClNeighbor.findOperationalLinkAddress();
							if (linkAddress == null) {
								_logger.warning(
										"SendHelloThread.run(): " +
										"No operational LinkAddress for neighbor " +
										tcpClNeighbor.getName());
								Thread.sleep(4000);
								continue;
							}
							Link link = linkAddress.getLink();
							if (!(link instanceof TcpClLink)) {
								_logger.warning(
										"Neighbor " + tcpClNeighbor.getName() +
										" is not associated with TcpClLink");
								continue;
							}
							TcpClLink tcpClLink = (TcpClLink)link;
							IPAddress address = tcpClLink.getIpAddress();
							String message = address.toParseableString();
							
							// Set up the bundle payload; send in-memory
							ByteArrayOutputStream bos = new ByteArrayOutputStream();
							DataOutputStream dos = new DataOutputStream(bos);
							try {
								dos.writeLong(System.currentTimeMillis());	// shell start time
								dos.writeByte(ROUTER_VERSION);
								dos.writeDouble(GeneralManagement.getInstance().getMySegmentRateLimit());
								dos.writeLong(GeneralManagement.getInstance().getMyBurstSize());
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
							
							// Note: must be Red to interoperate with Billy's Stack
							// But other reasons preclude interop with Billy's Stack
							// So making it Green; no need to be reliable since
							// we retransmit it periodically.
							BundleOptions options = new BundleOptions(BundleColor.GREEN);
							options.classOfServicePriority = BPClassOfServicePriority.BULK;
							options.lifetime = Math.max(10L, BPManagement.getInstance().getNetworkTimeSpread());
							options.reportToEndPointId = source;
							
							// Send a Bundle containing the Payload.
							if (GeneralManagement.isDebugLogging()) {
								_logger.fine("Sending Hello to " + dest.getEndPointIdString());
							}
							Payload payload = new Payload(bos.toByteArray(), 0, bos.size());
							if (GeneralManagement.isDebugLogging()) {
								if (_logger.isLoggable(Level.FINER)) {
									_logger.finer(payload.dump("", true));
								}
							}
							try {
								BpApi.getInstance().sendBundle(
										getAppRegistration(), 
										source, 
										dest, 
										payload, 
										options);
							} catch (JDtnException e) {
								if (e.getMessage().contains(
										"BpLtpAdapter has not been started")) {
									// Timing race between configuration and startup
									// Just catch it next time
									_logger.info("Send Router Hello Delayed; " + 
											"system not yet ready; " +
											"will try again next interval");
									
								} else {
									_logger.log(Level.SEVERE, "Send Hello; exception ignored", e);
								}
							}
						} else {
							if (GeneralManagement.isDebugLogging()) {
								_logger.fine("Not Sending Hello because Default Route neighbor has no EndPointId configured");
							}
						}
//					} else {
//						if (GeneralManagement.isDebugLogging()) {
//							_logger.fine("Not Sending Hello because no Default Route configured");
//						}
					}
					// Sleep for a while before we do it again
					Thread.sleep(SEND_HELLO_SLEEP_MSECS);
				}
			} catch (InterruptedException e) {
				if (GeneralManagement.isDebugLogging()) {
					_logger.fine("SendHelloThread interrupted");
				}
			} catch (BPException e) {
				_logger.log(Level.SEVERE, "Form Dest Route Eid", e);
			}
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("SendHelloThread.run() terminating");
			}
		}
	}
	
}
