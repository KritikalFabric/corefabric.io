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
package com.cisco.qte.jdtn.ltp;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.general.Address;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.LeakyBucketQueue;
import com.cisco.qte.jdtn.general.LeakyBucketQueueCollection;
import com.cisco.qte.jdtn.general.LeakyBucketQueueElement;
import com.cisco.qte.jdtn.general.Link;
import com.cisco.qte.jdtn.general.LinkAddress;
import com.cisco.qte.jdtn.general.Neighbor;
import com.cisco.qte.jdtn.general.NeighborsList;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.ltp.udp.LtpUDPLink;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * Superclass for all LTP Links.  A Link is a DataLink layer, or a network
 * interface.  It has a name, an address, and a Link State (Up or down).  This
 * class provides a Thread for polling the Link State, and for posting a
 * blocking receive on the Link.  It has a method to send a frame over the
 * Link to a specific Neighbor.
 * <p>
 * This interpretation of the term "Link" as a Network Interface is not the same
 * as that in the RFC and in implementations I've seen.  In those 
 * interpretations, the term "Link" is more closely related to what I call a 
 * "Neighbor".  However, I prefer my interpretation because it brings the
 * concept of a network interface into the picture, especially when used in the
 * context of a network interface that is a multi-access datalink.
 * <p>
 * Among its properties, a Link has:
 * <ul>
 *   <li>Name - a Name (must be unique among all Links)
 *   <li>Address - a "Link-layer" address
 *   <li>Admin State - available to management to enable/disable the Link
 *   <li>Datalink State - A state established by the subclass implemntation
 *       advising whether this Link is operative.
 *   <li>Operational State - A derived State defined as Admin State && DatalinkState
 *   <li>List of Neighbors reachable via this Link
 * </ul>
 */
public abstract class LtpLink extends Link {
	// How long to wait when we are terminating a Thread
	private static final long JOIN_TIMEOUT_MSECS = 1000L;
	// Period between polls of Link State
	private static final long LINK_MONITOR_PERIOD_MSECS = 4000L;
	
	// How long to sleep in Receive Thread after receive exception
	private static final long LINK_RECEIVE_PERIOD_MSECS = 10000L;
	// Default max number of claim counts per report
	private static final int DEFAULT_MAX_CLAIM_COUNT_PER_REPORT = 150;
	// Default report timeout; mSecs
	private static final int DEFAULT_REPORT_TIMEOUT_MSECS = 60000;
	// Default cancel timeout; mSecs
	private static final int DEFAULT_CANCEL_TIMEOUT_MSECS = 2000;
	// Default Checkpoint timeout; mSecs
	private static final int DEFAULT_CHECKPOINT_TIMEOUT_MSECS = 60000;
	
	private static final Logger _logger =
		Logger.getLogger(LtpLink.class.getCanonicalName());
	
	/**
	 * The address of the Link
	 */
	protected Address _address = Address.nullAddress;
	
	/**
	 * Max number of claims allowed in a ReportSegment sent on this Link.
	 * This parameter limits the size of each ReportSegment to a value
	 * appropriate for the Link's MTU.
	 */
	protected int _maxClaimCountPerReport = DEFAULT_MAX_CLAIM_COUNT_PER_REPORT;
	
	/**
	 * Report Timeout, mSecs; amount of time to wait for Report Ack to Report
	 */
	protected int _reportTimeout = DEFAULT_REPORT_TIMEOUT_MSECS;
	
	/**
	 * Cancel Timeout, mSecs; amount of time to wait for Cancel Ack to Cancel
	 */
	protected int _cancelTimeout = DEFAULT_CANCEL_TIMEOUT_MSECS;
	
	/**
	 * Checkpoint Timneout; mSecs; amount of time to wait for Report after Checkpoint
	 */
	protected int _checkpointTimeout = DEFAULT_CHECKPOINT_TIMEOUT_MSECS;
	
	// Link Receive Thread
	private Thread _linkReceiveThread = null;
	// Link State Monitor Thread
	private Thread _linkMonitorThread = null;
	// Link Transmit Thread
	private Thread _linkTransmitThread = null;
	// Outbound Segment Queue
	LeakyBucketQueueCollection _segmentQueue =
		new LeakyBucketQueueCollection(getName());
	// Statistic: # bits sent; updated by LinkTransmitThread
	private long _bitsSent = 0;
	// Statistic: Average bit rate (Avgd over LINK_MONITOR_PERIOD)
	private long _bitRateSent = 0;
	// Statistic: Max Average bit rate encountered
	private long _maxBitRateSent = 0;

	/**
	 * Parse a &lt; Link &gt; element from a JDTN XML config file, including
	 * embedded &lt; Neighbor &gt;  elements and the closing &lt; /Link &gt;.
	 * @param parser XML Parser
	 * @return The new Link parsed out of the Config file
	 * @throws JDtnException on JDtn specific parse errors
	 * @throws XMLStreamException On general parse errors
	 * @throws IOException On I/O errors
	 * @throws InterruptedException 
	 */
	public static LtpLink parseLink(
			XmlRDParser parser, 
			String linkName, 
			Link.LinkType linkType) 
	throws JDtnException, XmlRdParserException, IOException, InterruptedException {
		// <Link 
		//    --- Link defined attributes ---
		//    maxClaimCount='n'
		//    bpsLimit='n'
		//    reportTimeout='n'
		//    cancelTimeout='n'
		//    checkpointTimeout='n'
		//  >
		//    <UdpNeighbor .../>
		// </Link>
		//
		// Create the Link
		if (linkType == LinkType.LINK_TYPE_LTP_UDP) {
			LtpUDPLink udpLink = LtpUDPLink.parseUdpLink(parser, linkName);
					
			// Optional attribute maxClaimCount
			Integer intVal = Utils.getIntegerAttribute(
					parser, 
					"maxClaimCount", 
					1, 
					Integer.MAX_VALUE);
			if (intVal != null) {
				udpLink.setMaxClaimCountPerReport(intVal.intValue());
			}
			
			// Optional attribute bpsLimit; retained for compatibility but
			// has no effect.
			Long longValue = Utils.getLongAttribute(
					parser,
					"bpsLimit",
					1,
					Long.MAX_VALUE);
			if (longValue != null) {
				_logger.warning(
						"bpsLimit property on Links is no longer supported;" +
						" this configuration has no effect");
			}
			
			// Optional attribute reportTimeout
			intVal = Utils.getIntegerAttribute(
					parser, 
					"reportTimeout", 
					1, 
					Integer.MAX_VALUE);
			if (intVal != null) {
				udpLink.setReportTimeout(intVal.intValue());
			}
			
			// Optional attribute cancelTimeout
			intVal = Utils.getIntegerAttribute(
					parser, 
					"cancelTimeout", 
					1, 
					Integer.MAX_VALUE);
			if (intVal != null) {
				udpLink.setCancelTimeout(intVal.intValue());
			}
			
			// Optional attribute checkpointTimeout
			intVal = Utils.getIntegerAttribute(
					parser, 
					"checkpointTimeout", 
					1, 
					Integer.MAX_VALUE);
			if (intVal != null) {
				udpLink.setCheckpointTimeout(intVal.intValue());
			}
	
			// Parse Neighbors; This is legacy code to parse Rev 1.0 config,
			// wherein <Neighbor> elements appear as children to <Link> elements.
			XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
			while (event == XmlRDParser.EventType.START_ELEMENT) {
				if (!parser.getElementTag().equals("Neighbor")) {
					throw new LtpException("Expecting <Neighbor>");
				}
				
				Neighbor neighbor = Neighbor.parseNeighbor(parser);
				NeighborsList.getInstance().addNeighbor(neighbor);
				for (LinkAddress linkAddress : neighbor.getLinkAddresses()) {
					linkAddress.setLink(udpLink);
				}
				
				event = Utils.nextNonTextEvent(parser);
			}
			
			// Now expecting </Link>
			if (event != XmlRDParser.EventType.END_ELEMENT || 
				!parser.getElementTag().equals("Link")) {
				throw new LtpException("Expecting </Link>");
			}
			return udpLink;
			
		} else {
			throw new JDtnException("LinkType=" + linkType);
		}
	}
	
	/**
	 * Write configuration for this Link to given PrintWriter as a &lt; Link &gt;
	 * element.  Includes write of contained Neighbors as &lt; Neighbor &gt; elements.
	 * @param pw Given PrintWriter
	 */
	@Override
	public void writeConfigImpl(PrintWriter pw) {
		if (getMaxClaimCountPerReport() != DEFAULT_MAX_CLAIM_COUNT_PER_REPORT) {
			pw.println("        maxClaimCount='" + getMaxClaimCountPerReport() + "'");
		}
		if (getReportTimeout() != DEFAULT_REPORT_TIMEOUT_MSECS) {
			pw.println("        reportTimeout='" + getReportTimeout() + "'");
		}
		if (getCheckpointTimeout() != DEFAULT_CHECKPOINT_TIMEOUT_MSECS) {
			pw.println("        checkpointTimeout='" + getCheckpointTimeout() + "'");
		}
		if (getCancelTimeout() != DEFAULT_CANCEL_TIMEOUT_MSECS) {
			pw.println("        cancelTimeout='" + getCancelTimeout() + "'");
		}
	}
	
	/**
	 * Constructor; all properties set to defaults 
	 */
	public LtpLink() {
		// Nothing
	}
	
	/**
	 * Constructor; name set to given name; other properties defaulted.
	 * @param name Name of Link
	 */
	public LtpLink(String name) {
		super(name);
		setName(name);
	}
	
	/**
	 * Constructor; name and address set to given; others defaulted.
	 * @param name Name of Link
	 * @param address Address of Link
	 */
	public LtpLink(String name, Address address) {
		super(name);
		setAddress(address);
	}
	
	/**
	 * Start the Link.  Starts the Link Receive Thread, the Link Monitor
	 * Thread, and the Link Receiver Thread.
	 */
	@Override
	protected void startImpl() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("start(" + getName() + ")");
		}
		super.startImpl();
		if (_linkReceiveThread == null) {
			_linkReceiveThread = new Thread(new LinkReceiveThread());
			_linkReceiveThread.setName(getName() + "Rcv");
			_linkReceiveThread.setPriority(Thread.MAX_PRIORITY);
			_linkReceiveThread.start();
		}
		if (_linkMonitorThread == null) {
			_linkMonitorThread = new Thread(new LinkMonitorThread());
			_linkMonitorThread.setName(getName() + "Mon");
			_linkMonitorThread.start();
		}
		if (_linkTransmitThread == null) {
			_linkTransmitThread = new Thread(new LinkTransmitThread());
			_linkTransmitThread.setName(getName() + "Xmt");
			_linkTransmitThread.setPriority(Thread.MAX_PRIORITY);
			_linkTransmitThread.start();
		}
	}
	
	/**
	 * Stop the link.  Stops the Link Receive Thread, Link Monitor
	 * Thread, and Link Transmit Thread.
	 */
	@Override
	protected void stopImpl() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("stop(" + getName() + ")");
		}
		if (_linkTransmitThread != null) {
			_linkTransmitThread.interrupt();
			try {
				_linkTransmitThread.join(JOIN_TIMEOUT_MSECS);
			} catch (InterruptedException e) {
				// Nothing
			}
			_linkTransmitThread = null;
		}
		if (_linkMonitorThread != null) {
			_linkMonitorThread.interrupt();
			try {
				_linkMonitorThread.join(JOIN_TIMEOUT_MSECS);
			} catch (InterruptedException e1) {
				// Nothing
			}
			_linkMonitorThread = null;
		}
		
		if (_linkReceiveThread != null) {
			_linkReceiveThread.interrupt();
			try {
				linkStopImpl();
				_linkReceiveThread.join(JOIN_TIMEOUT_MSECS);
				
			} catch (InterruptedException e) {
				// Nothing
			}
			_linkReceiveThread = null;
		}
		
		_segmentQueue.clear();
		
		super.stopImpl();
	}
	
	/**
	 * Clear statistics for this Link
	 */
	@Override
	public void clearStatistics() {
		_maxBitRateSent = 0;
	}
	
	/**
	 * Called from subclass to report a frame has been received.  We respond
	 * by decoding the frame to a Segment and enqueueing the Segment to the 
	 * LtpInbound process.
	 * @param link The Link
	 * @param neighbor The Neighbor from which the frame was received.
	 * @param buffer Buffer containing data received.  This buffer is not
	 * reused.  The buffer must be stripped of all DataLink headers and
	 * trailers and contain only the raw Ltp Frame.
	 * @param offset Offset into buffer to start of data
	 * @param length Length of received data
	 * @throws InterruptedException On Interruption
	 */
	protected void notifyReceived(
			LtpLink link, 
			LtpNeighbor neighbor, 
			byte[] buffer, 
			int offset, 
			int length) throws InterruptedException {

		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("notifyReceived(" + getName() + ", Encoded length=" + length + ")");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest("  Encoded length=" + length +
						"Encoded Data=\n" + Utils.dumpBytes("  ", buffer, 0, length));
			}
		}
		
		// Decode datagram to a Segment and enqueue the Segment to LtpInbound
		try {
			long t1 = System.currentTimeMillis();
			Segment segment = Segment.decode(buffer, offset, length);
			segment.setLink(link);
			segment.setNeighbor(neighbor);
			if (GeneralManagement.isDebugLogging()) {
				if (_logger.isLoggable(Level.FINEST)) {
					_logger.finest(segment.dump("  ", true));
				}
			}
			long t2 = System.currentTimeMillis();
			if (segment instanceof DataSegment) {
				LtpManagement.getInstance()._ltpStats.nDecodeMSecs += (t2 - t1);
			}
			
			LtpInbound.getInstance().onInboundSegment(segment);
			
		} catch (JDtnException e) {
			if (e.getMessage().contains("No space left on device")) {
				_logger.log(Level.SEVERE, "No space left to receive incoming datagram");
				
			} else {
				_logger.log(Level.SEVERE, "Processing incoming datagram", e);
			}
			_logger.severe("Discarding datagram");
		}
	}
	
	/**
	 * Runs a Thread which continually posts a blocking receive to the subclass
	 * on the Link.  Received frames will be reported by subclass via
	 * notifyReceive().
	 */
	public class LinkReceiveThread implements Runnable {

		@Override
		public void run() {
			while (!Thread.interrupted()) {
				
				try {
					// Post a blocking receive
					receiveImpl();
				} catch (JDtnException e) {
					
					// Exception on receive.
					String mesg = e.getMessage();
					if (mesg.contains("The address is not available") ||
						mesg.contains("Can't assign requested address")) {
						if (GeneralManagement.isDebugLogging()) {
							_logger.fine("Link " + getName() + ": address no longer available");
						}
					} else {
						_logger.log(Level.SEVERE, "Link " + getName() + 
								": receive exception ", e);
					}
					try {
						// Sleep for a while and try again later
						Thread.sleep(LINK_RECEIVE_PERIOD_MSECS);
					} catch (InterruptedException e2) {
						if (GeneralManagement.isDebugLogging()) {
							_logger.fine("Link " + getName() + 
									" Link Receive Thread interrupted");
						}
						break;
					}
				} catch (InterruptedException e) {
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("LinkReceiveThread interrupted");
					}
					break;
				}
			}
			
			// Thread interrupted, shutdown
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Link " + getName() + " Link Receive Thread terminating");
			}
		}
	}
	
	/**
	 * Runs a Thread which periodically checks Link State. It will call
	 * notifyLinkUp() or notifyLinkDown() appropriately.  The subclass can still
	 * call notifyLinkUp() or notifyLinkDown() in order to make the monitoring
	 * process more timely.
	 */
	public class LinkMonitorThread implements Runnable {

		@Override
		public void run() {
			_bitRateSent = 0;
			long lastBits = 0;
			long lastTime = System.currentTimeMillis();
			
			while (!Thread.interrupted()) {
				
				// Sleep between polls of link state.
				try {
					Thread.sleep(LINK_MONITOR_PERIOD_MSECS);
				} catch (InterruptedException e) {
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("Link " + getName() + 
								" Link Monitor Thread interrupted");
					}
					break;
				}
				
				// Get link Datalink state.  Install it as our LinkDataLink
				// property.  This will also have an effect on LinkOperational,
				// and may result in callouts to listeners.
				boolean linkState = getLinkStateImpl();
				setLinkDatalinkUp(linkState);
				
				// Update Transmit Bit rate
				long thisBits = _bitsSent;
				long deltaBits = thisBits - lastBits;
				long thisTime = System.currentTimeMillis();
				long deltaTime = (thisTime - lastTime) / 1000L;
				lastTime = thisTime;
				_bitRateSent = deltaBits / deltaTime;
				if (_bitRateSent > _maxBitRateSent) {
					_maxBitRateSent = _bitRateSent;
				}
				lastBits = thisBits;
				
			}
			
			// Interuppted; shut down
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Link " + getName() + " Link Monitor Thread terminating");
			}
			notifyLinkDatalinkDown();
		}
	}
	
	/**
	 * Enqueue the given Segment for transmit on this Link.  May block.
	 * @param segment Given Segment
	 * @throws InterruptedException If interrupted while blocked
	 * @throws LtpException If waited for queue space for SEND_SEGMENT_DELAY_MSECS
	 */
	public void enqueueOutboundSegment(Segment segment) 
	throws InterruptedException, LtpException {
		_segmentQueue.enqueue(segment.getNeighbor(), segment);
	}
	
	/**
	 * Runs a Thread which takes Segments enqueued for Transmit and feeds them
	 * to the Datalink.
	 */
	public class LinkTransmitThread implements Runnable {

		@Override
		public void run() {
			_bitsSent = 0L;
			try {
				while (!Thread.interrupted()) {
					
					// Wait for a Segment to Transmit
					Segment segment = (Segment)_segmentQueue.dequeue();
					
					if (!segment.isValid()) {
						// This can happen if we're so busy that we enqueue a
						// DataSegment for transmit, and before it gets actually
						// transmitted, we get a ReportSegment and enqueue same DataSegment
						// for retransmission, and before that gets transmitted
						// get get a ReportSegment saying all is received so
						// we invalidate the outbound segment.
						continue;
					}
					
					if (GeneralManagement.isDebugLogging()) {
						_logger.finer(
								"Link " + getName() + 
								" Transmitting Segment");
						if (_logger.isLoggable(Level.FINEST)) {
							_logger.finest(segment.dump("  ", true));
						}
					}
					
					// Encode the Segment to a byte[]
					long t1 = System.currentTimeMillis();
					EncodeState encodeState = new EncodeState();
					byte[] byteBuf = null;
					try {
						segment.encode(encodeState);
						encodeState.close();
						byteBuf = encodeState.getByteBuffer();
					} catch (JDtnException e) {
						_logger.log(Level.SEVERE, "Encoding Segment for Transmission", e);
						continue;
					}
					long t2 = System.currentTimeMillis();
					if (segment instanceof DataSegment) {
						LtpManagement.getInstance()._ltpStats.nEncodeMSecs += (t2 - t1);
						LtpManagement.getInstance()._ltpStats.nPayloadEncodeMSecs +=
							((DataSegment)segment).getmSecsEncodingPayload();
					}
					
					if (GeneralManagement.isDebugLogging()) {
						if (_logger.isLoggable(Level.FINEST)) {
							_logger.finest("Encoded Length=" + byteBuf.length + " Buffer=");
							_logger.finest(
									Utils.dumpBytes(
											"  ", 
											byteBuf, 
											0, 
											byteBuf.length));
						}
					}
					
					_bitsSent += byteBuf.length * 8;
					
					// Callback for notification that transmit started.
					SegmentTransmitCallback callback =
						segment.getSegmentTransmitCallback();
					if (callback != null) {
						callback.onSegmentTransmitStarted(segment.getLink(), segment);
					}

					try {
						// Transmit the Segment; believe it or not, we want
						// things in this order.  Reasoning: the transmit
						// callback ensures that appropriate timers are
						// started.  The check for NeighborOperational
						// ensures that queues won't get backed up when
						// Neighbor goes non-operational.
						if (segment.getNeighbor().isNeighborOperational()) {
							sendImpl(
									segment.getNeighbor(), byteBuf, 
									0, byteBuf.length);
						}
					} catch (JDtnException e) {
						_logger.severe(
								"Link " + getName() + 
								"; transmit to " + 
								segment.getNeighbor().getName() +								
								": " + e.getMessage());
						continue;
					}
					
				}
			} catch (InterruptedException e) {
				if (GeneralManagement.isDebugLogging()) {
					_logger.fine("LinkTransmitThread interrupted");
				}
			}
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("LinkTransmitThread terminating");
			}
		}
	}
	
	/**
	 * Called when a Block has been cancelled; We drain this link's outgoing
	 * SegmentQueue of all Segments pertaining to Block's Session.  We then
	 * drain the corresponding Neighbor's SegmentQueue of all Segments pertaining
	 * to Block's Session.  We exclude CancelSegments and CancelAckSegments from
	 * this.
	 * @param block Block being cancelled
	 * @throws InterruptedException If interrupted while doing this
	 */
	public void onBlockCancelled(Block block) throws InterruptedException {
		LtpNeighbor neighbor = block.getNeighbor();
		LeakyBucketQueue queue = _segmentQueue.getQueue(neighbor);
		if (queue != null) {
			queue.removeElementsMatching(
				new LeakyBucketQueue.QueuePredicate() {

					@Override
					public boolean matches(
							LeakyBucketQueueElement element,
							Object argument) {
						SessionId sessionId = (SessionId)argument;
						if (element instanceof Segment) {
							Segment segment = (Segment)element;
							if (segment instanceof CancelSegment ||
								segment instanceof CancelAckSegment) {
								return false;
							}
							if (sessionId.equals(segment.getSessionID())) {
								return true;
							}
						}
						return false;
					}
				}, 
				block.getSessionId()
			);
		}
	}
	
	@Override
	public String toString() {
		return dump("", false);
	}
	
	/**
	 * Dump the state of this object
	 * @param indent Amount of indentation
	 * @param detailed True if want verbose details
	 * @return String containing dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "LtpLink\n");
		sb.append(super.dump(indent + "  ", detailed));
		sb.append(getAddress().dump(indent + "  ", detailed));
		sb.append(indent + "  MaxClaimCountPerReport=" + getMaxClaimCountPerReport() + "\n");
		sb.append(indent + "  Transmit Bit Rate=" + getTransmitBitRate() + "\n");
		sb.append(indent + "  Max Xmit Bit Rate=" + _maxBitRateSent + "\n");
		sb.append(indent + "  Report Timeout=" + getReportTimeout() + " mS\n");
		sb.append(indent + "  Cancel Timeout=" + getCancelTimeout() + " mS\n");
		sb.append(indent + "  Checkpoint Timeout=" + getCheckpointTimeout() + " mS\n");
		sb.append(indent + "  SeqmentQueueLen=" + _segmentQueue.size() + "\n");
		return sb.toString();
	}
	
	/**
	 * Subclass supplied method to send a frame to the given Neighbor
	 * @param neighbor Neighbor to send to
	 * @param buffer Data to send
	 * @param offset Starting offset into data
	 * @param length Length of data to send
	 * @throws JDtnException on send error
	 */
	protected abstract void sendImpl(
			LtpNeighbor neighbor, 
			byte[] buffer, 
			int offset, 
			int length) throws JDtnException;
	
	/**
	 * Subclass supplied method to get the maximum size of a frame in bytes for
	 * this Link.
	 * @return Max frame size
	 */
	public abstract int getMaxFrameSize();
	
	/**
	 * Subclass provided method to perform a receive of a frame.  This method
	 * is not intended to be called by clients.  This is called
	 * from the Link Receive Thread.  It is expected that this method will
	 * block until a frame is available.  If/when a frame is successfully
	 * received, subclass will notify via notifyReceive().
	 * @throws JDtnException if receive is currently not possible, maybe because
	 * the link is down.
	 * @throws InterruptedException if operation interrupted
	 */
	protected abstract void receiveImpl() throws JDtnException, InterruptedException;
	
	/**
	 * Subclass provided method to get Datalink state.  This method is not
	 * intended to be called by clients.  It is called from the Link Monitor
	 * thread.  This call is only expected to block for a short period, if at
	 * all.
	 * @return True if the link is up.  False otherwise.
	 */
	protected abstract boolean getLinkStateImpl();
	
	public Address getAddress() {
		return _address;
	}
	
	public void setAddress(Address address) {
		this._address = address;
	}

	/**
	 * Max number of claims allowed in a ReportSegment sent on this Link.
	 * This parameter limits the size of each ReportSegment to a value
	 * appropriate for the Link's MTU.
	 */
	public int getMaxClaimCountPerReport() {
		return _maxClaimCountPerReport;
	}

	/**
	 * Max number of claims allowed in a ReportSegment sent on this Link.
	 * This parameter limits the size of each ReportSegment to a value
	 * appropriate for the Link's MTU.
	 */
	public void setMaxClaimCountPerReport(int maxClaimCountPerReport) {
		this._maxClaimCountPerReport = maxClaimCountPerReport;
	}

	public long getBitsTransmitted() {
		return _bitsSent;
	}

	public void setBitsTransmitted(long bitsSent) {
		this._bitsSent = bitsSent;
	}

	/** Statistic: Average bit rate (Avgd over LINK_MONITOR_PERIOD) */
	public long getTransmitBitRate() {
		return _bitRateSent;
	}

	/**
	 * Report Timeout, mSecs; amount of time to wait for Report Ack after Report
	 */
	public long getReportTimeout() {
		return _reportTimeout;
	}

	/**
	 * Report Timeout, mSecs; amount of time to wait for Report Ack after Report
	 */
	public void setReportTimeout(int reportTimeout) {
		this._reportTimeout = reportTimeout;
	}

	/**
	 * Cancel Timeout, mSecs; amount of time to wait for Cancel Ack after Cancel
	 */
	public long getCancelTimeout() {
		return _cancelTimeout;
	}

	/**
	 * Cancel Timeout, mSecs; amount of time to wait for Cancel Ack after Cancel
	 */
	public void setCancelTimeout(int cancelTimeout) {
		this._cancelTimeout = cancelTimeout;
	}

	/**
	 * Checkpoint Timneout; mSecs; amount of time to wait for Report after Checkpoint
	 */
	public long getCheckpointTimeout() {
		return _checkpointTimeout;
	}

	/**
	 * Checkpoint Timneout; mSecs; amount of time to wait for Report after Checkpoint
	 */
	public void setCheckpointTimeout(int checkpointTimeout) {
		this._checkpointTimeout = checkpointTimeout;
	}

	/**
	 * Get the LeakyBucketQueueCollection for this Link
	 * @return What I said
	 */
	protected LeakyBucketQueueCollection getSegmentQueue() {
		return _segmentQueue;
	}
	
}
