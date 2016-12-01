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
import java.util.logging.Logger;

import com.cisco.qte.jdtn.bp.EndPointId;
import com.cisco.qte.jdtn.component.EventBroadcaster;
import com.cisco.qte.jdtn.component.IEvent;
import com.cisco.qte.jdtn.component.IEventProcessor;
import com.cisco.qte.jdtn.events.ManagementPropertyChangeEvent;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.LeakyBucketQueue;
import com.cisco.qte.jdtn.general.LeakyBucketQueueDelayElement;
import com.cisco.qte.jdtn.general.Link;
import com.cisco.qte.jdtn.general.LinkAddress;
import com.cisco.qte.jdtn.general.Management;
import com.cisco.qte.jdtn.general.Neighbor;
import com.cisco.qte.jdtn.general.NeighborsList;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.ltp.udp.LtpUDPNeighbor;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * Superclass for a Neighbor; a node directly reachable on a Link.  This
 * interpretation of the term "Neighbor" as a directly reachable node on
 * a datalink is not used in RFC5326 or elsewhere.  In RFC5326, the term
 * "Link" is used.  But I don't like their definition of the term Link,
 * preferring instead to use the term "Link" as a Datalink or Network
 * Interface.  Therefore I had to use a different term and chose Neighbor.
 * <p>
 * A Neighbor has the following properties:
 * <ul>
 *   <li>Name - A name - User assigned name, must be unique among all Neighbors
 *       on the parent Link.
 *   <li>EngineId - The LTP EngineId of the Neighbor
 *   <li>Links - Set of Links on which this Neighbor may be reached
 *   <li>Address - The Link layer address of the Neighbor
 *   <li>OperationalState - Up or Down - Derived as AdminState && Link.OperationalState
 *   <li>AdminState - Up or Down - Available to management to enable or disable
 *       this Neighbor
 *   <li>ScheduledState - Up or Down - Available to management to enable or disable
 *       LTP timers associated with Segments to/from this Neighbor.  ScheduledState
 *       is decoupled from OperationalState and AdminState.  The idea is that
 *       ScheduledState down represents a temporary cessation in which we would
 *       still like to keep enqueued Segments around, and then finally when
 *       ScheduledState goes up the enqueued Segments spill out to the Link.
 *       Whereas OperationalState and AdminState represent unpredictable
 *       outages which might last for a long time.  In such cases, we want to
 *       avoid enqueueing Segments that might never go out.  Instead, we abort
 *       the Segment enqueue immediately.
 *   <li>LightDistance - Propagation Delay to Neighbor, expressed in Seconds
 * </ul>
 */
public abstract class LtpNeighbor extends Neighbor implements IEventProcessor {

	public static final long SEND_SEGMENT_DELAY_MSECS = 50L;
	private static final Logger _logger =
		Logger.getLogger(LtpNeighbor.class.getCanonicalName());
	
	/** Property name for mgmt change events for Neighbor EngineId property */
	public static final String ENGINEID_PROPERTY = "Neighbor.engineId";
	
	/**
	 * The LTP EngineId of the Neighbor
	 */
	protected EngineId _engineId = EngineId.getDefaultEngineId();
	
	/**
	 * Queue of Segments awaiting transmission to this Neighbor
	 */
	protected LeakyBucketQueue _segmentQueue =
		new LeakyBucketQueue(getName(), this);
	
	/** Default Segment Transmit Rate Limit, Segments per second */
	public static final double DEFAULT_SEGMENT_XMIT_RATE_LIMIT = 4000.0d;
	
	/** Segment Transmit Rate Limit, for throttling xmits to this neighbor */
	protected double _segmentXmitRateLimit = DEFAULT_SEGMENT_XMIT_RATE_LIMIT;
	
	private double _segmentSlotSecs = 1.0d / _segmentXmitRateLimit;
	private double _segmentSlotMSecs = _segmentSlotSecs * 1000.0d;
	
	/** Default value for burstSize property */
	public static final long DEFAULT_BURST_SIZE = 120L;
	
	/** Burst of segments allowed before throttling starts kicking in */
	protected long _burstSize = DEFAULT_BURST_SIZE;

	// Credit mechanism for segment transmit rate throttling
	protected long _segmentsCredit = -_burstSize;
	
	private long _lastTimeTransmitted = 0;
	
	/**
	 * Parse XML Config for a Neighbor.  We assume that parser is sitting on the
	 * &lt; Neighbor &gt; element.  We advance the parser through and including
	 * the &lt; /Neighbor &gt;.  We create a new Neighbor and return it.
	 * @param parser The Pull Parser
	 * @return The Neighbor created
	 * @throws JDtnException On JDtn specific parse errors
	 * @throws XMLStreamException On general paser errors
	 * @throws IOException On I/O errors during parse
	 */
	public static LtpNeighbor parseNeighbor(
			XmlRDParser parser,
			String neighborName,
			Neighbor.NeighborType neighborType)
	throws JDtnException, IOException, XmlRdParserException {
		// <Neighbor
		//   -- Neighbor defined attributes ---
		//   engineId="engineId" 
		//   segmentXmitRateLimit='n'
		//   burstSize='n'
		//   ...
		// </Neighbor>
		String engineIdStr = parser.getAttributeValue("engineId");
		if (engineIdStr == null || engineIdStr.length() == 0) {
			throw new JDtnException("Missing 'engineId' attribute");
		}
		EngineId engineId = new EngineId(engineIdStr);
		
		Double spsLimit = Utils.getDoubleAttribute(parser, "segmentXmitRateLimit", 0, Long.MAX_VALUE);
		Long burstSize = Utils.getLongAttribute(parser, "burstSize", 0, Long.MAX_VALUE);
		if (neighborType == NeighborType.NEIGHBOR_TYPE_LTP_UDP) {
			
			LtpUDPNeighbor neighbor = LtpUDPNeighbor.parseUdpNeighbor(parser, neighborName, engineId);
			
			if (spsLimit != null) {
				neighbor.setSegmentXmitRateLimit(spsLimit.doubleValue());
			}
			if (burstSize != null) {
				neighbor.setBurstSize(burstSize.longValue());
			}
			
			XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
			while (event == XmlRDParser.EventType.START_ELEMENT &&
					parser.getElementTag().equals("LinkAddress")) {
				
				LinkAddress linkAddr = LinkAddress.parse(parser);
				neighbor.addLinkAddress(linkAddr);
				
				event = Utils.nextNonTextEvent(parser);
			}
			
			if (event != XmlRDParser.EventType.END_ELEMENT || 
					!parser.getElementTag().equals("Neighbor")) {
				throw new JDtnException("Expecting </Neighbor>");
			}
			
			return neighbor;
			
		} else {
			throw new LtpException("Unrecognized type argument: " + neighborType);
		}
	}
	
	/**
	 * Construct Neighbor w/ given name and EngineId and with default Endpoint
	 * Id (dtn:none).  Note that later configuration after construction should
	 * set the Endpoint Id to something reasonable.
	 * @param eid Given EngineId
	 * @param name Given Name
	 */
	public LtpNeighbor(EngineId eid, String name) {
		super(name);
		setEngineId(eid);
		setEndPointIdStem(EndPointId.defaultEndPointId);
		EventBroadcaster.getInstance().registerEventProcessor(
				Management.class.getCanonicalName(), this);
	}
	
	/**
	 * Construct w/ given LinkAddress and EngineId.  Name property will be
	 * stringized version of EngineId.
	 * @param eid Given EngineId
	 * @param linkAddress Given Address
	 */
	public LtpNeighbor(EngineId eid, LinkAddress linkAddress) {
		super(eid.getEngineIdString());
		setEngineId(eid);
		addLinkAddress(linkAddress);
		setEndPointIdStem(EndPointId.defaultEndPointId);
		EventBroadcaster.getInstance().registerEventProcessor(
				Management.class.getCanonicalName(), this);
	}
	
	/**
	 * Construct w/ given LinkAddress, EngineId, and name
	 * @param eid Given EngineId
	 * @param linkAddress Given LinkAddress
	 * @param name Given Name
	 */
	public LtpNeighbor(EngineId eid, LinkAddress linkAddress, String name) {
		super(name);
		setEngineId(eid);
		addLinkAddress(linkAddress);
		setEndPointIdStem(EndPointId.defaultEndPointId);
		EventBroadcaster.getInstance().registerEventProcessor(
				Management.class.getCanonicalName(), this);
	}
	
	/**
	 * Write config for this LtpNeighbor out to given PrintWriter as attributes
	 * of &lt; Neighbor &gt; element.
	 * @param pw Given PrintWriter
	 */
	@Override
	protected void writeConfigImpl(PrintWriter pw) {
		pw.println("          engineId='" + getEngineId().getEngineIdString() + "'");
		if (getSegmentXmitRateLimit() != DEFAULT_SEGMENT_XMIT_RATE_LIMIT) {
			pw.println("          segmentXmitRateLimit='" + getSegmentXmitRateLimit() + "'");
		}
		if (getBurstSize() != DEFAULT_BURST_SIZE) {
			pw.println("          burstSize='" + getBurstSize() + "'");
		}
	}
	
	/**
	 * Find a Neighbor which has the given Engine ID
	 * @param engineId Given EngineID
	 * @return Neighbor or null if none
	 */
	public static LtpNeighbor findNeighborByEngineId(EngineId engineId) {
		// Get Neighbor with given EngineId
		LtpNeighbor neighbor = (LtpNeighbor)
			NeighborsList.getInstance().findNeighborByPredicate(
				new NeighborsList.NeighborPredicate() {
					
					@Override
					public boolean isNeighborAccepted(
							Neighbor neighbor2,
							Object arg) {
						if (!(arg instanceof EngineId)) {
							return false;
						}
						if (!(neighbor2 instanceof LtpNeighbor)) {
							return false;
						}
						EngineId engineId2 = (EngineId)arg;
						LtpNeighbor ltpNeighbor = (LtpNeighbor)neighbor2;
						
						if (ltpNeighbor.getEngineId().equals(engineId2)) {
							return true;
						}
						return false;
					}
				},
				engineId
			);
		return neighbor;
	}
	
	/**
	 * Find an operational Link to which this Neighbor can be reached.
	 * @return The first Link found, in unpredictable order, or null if there
	 * are no such Links.
	 */
	@SuppressWarnings("null")
	@Override
	public Link findOperationalLink() {
		for (LinkAddress linkAddress : _linkAddresses) {
			Link link = linkAddress.getLink();
			if (link instanceof LtpLink &&
				link != null &&
				link.isLinkOperational()) {
				return linkAddress.getLink();
			}
		}
		return null;
	}
	
	/**
	 * Enqueue given Segment for transmit to this Neighbor.
	 * @param segment Segment to transmit
	 * @throws InterruptedException on error waiting for space in queue
	 * @throws LtpException if can't enqueue because queue is full
	 */
	public void transmitSegment(Segment segment) 
	throws InterruptedException, LtpException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("sendSegment(Neighbor=" + getName() + ")");
		}
		LtpLink link = segment.getLink();
		if (link == null) {
			throw new LtpException("Segment has no Link specified");
		}
		
		if (!isNeighborOperational()) {
			throw new LtpException("Neighbor " + getName() + " is not operational");
		}
		
		// Give credit for time expired since last transmit
		long thisTimeTransmitted = System.currentTimeMillis();
		long elapsedMSecs = thisTimeTransmitted - _lastTimeTransmitted;
		_lastTimeTransmitted = thisTimeTransmitted;
		double elapsedSecs = elapsedMSecs / 1000.0d;
		double segmentSlotsElapsed = elapsedSecs / _segmentSlotSecs;
		_segmentsCredit -= segmentSlotsElapsed;
		if (_segmentsCredit < -_burstSize) {
			_segmentsCredit = -_burstSize;
		}
		
		// Take away credit for transmitting this Segment
		_segmentsCredit++;
		if (_segmentsCredit > 0) {
			// We'll have to insert a delay into the queue before transmitting.
			long delay = (long)(_segmentsCredit * _segmentSlotMSecs); 
			if (delay < 0) {
				// Bug 3177567; debug tracking
				_logger.severe(
						"Link.transmitSegment: Negative delay computed: " + 
						delay);
				_logger.severe("_segmentsCredit=" + _segmentsCredit);
				_logger.severe("_segmentSlotMSecs=" + _segmentSlotMSecs);
				_logger.severe("elapsedSecs=" + elapsedSecs);
				delay = 1;
			}
			LeakyBucketQueueDelayElement delayElement =
				new LeakyBucketQueueDelayElement(delay);
			link.getSegmentQueue().enqueue(this, delayElement);
			// Give back credit for the delay
			_segmentsCredit -= delay / _segmentSlotSecs / 1000.0d;
		}
		link.getSegmentQueue().enqueue(this, segment);
	}
	
	/**
	 * Callback from Link when it changes Operational state.  We change
	 * NeighborOperational according to:
	 * <p>
	 *   neighborOperational = neighborAdminUp && linkOperational
	 * <p>
	 * This can have the side effect of notifying the Link listeners of a change
	 * in the Neighbor Operational state.
	 * @param aLink The Link changing state; our parent Link
	 * @param linkOperational New Link Operational State
	 */
	@Override
	public void onLinkOperationalStateChange(Link aLink, boolean linkOperational) {
		updateOperationalStateForLink();
	}
	
	/**
	 * Called when a Block has been cancelled; We drain this neighbor's outgoing
	 * SegmentQueue of all Segments pertaining to Block's Session.
	 * We exclude CancelSegments and CancelAckSegments from this.
	 * @param block Block being cancelled
	 * @throws InterruptedException if operation interrupted
	 */
	public void onBlockCancelled(Block block) throws InterruptedException {
		// We don't need to do anything; Link does all necessary
	}
	
	/**
	 * The LTP EngineId of the Neighbor
	 */
	public EngineId getEngineId() {
		return _engineId;
	}
	/**
	 * The LTP EngineId of the Neighbor
	 */
	public void setEngineId(EngineId engineId) {
		Management.getInstance().fireManagementPropertyChangeEvent(
				this, ENGINEID_PROPERTY, _engineId, engineId);
		this._engineId = engineId;
	}
	
	@Override
	public void processEvent(IEvent event) throws Exception {
		if (event instanceof ManagementPropertyChangeEvent) {
			ManagementPropertyChangeEvent mEvent = (ManagementPropertyChangeEvent)event;
			propertyChanged(
					mEvent.getSource(), 
					mEvent.getPropertyName(), 
					mEvent.getOldValue(), 
					mEvent.getNewValue());
		}
	}

	/**
	 * Called from LinkAddress when one of its properties is changed.  We are
	 * interested in changes in one of our LinkAddress' Link property.  Allows
	 * us to re-assess the operational state of this Neighbor.
	 */
	private void propertyChanged(
			Object source,
			String propertyName, 
			Object oldValue, 
			Object newValue) {
		if (propertyName.equals(LinkAddress.LINK_PROPERTY)) {
			LinkAddress linkAddress = (LinkAddress)source;
			LtpLink oldLink = null;
			if (oldValue != null && oldValue instanceof LtpLink) {
				oldLink = (LtpLink)oldValue;
			}
			LtpLink newLink = null;
			if (newValue != null && newValue instanceof LtpLink) {
				newLink = (LtpLink)newValue;
			}
			if (oldLink == null && newLink == null) {
				return;
			}
			if (_linkAddresses.contains(linkAddress)) {
				if (oldLink != null) {
					oldLink.getSegmentQueue().remove(_segmentQueue);
					oldLink.removeLinkListener(this);
					_segmentQueue.clear();
					removeLinkImpl();
				}
				if (newLink != null) {
					newLink.getSegmentQueue().add(_segmentQueue);
					newLink.addLinkListener(this);
				}
				updateOperationalStateForLink();
			}
		}
	}
	
	@Override
	public void addLinkAddress(LinkAddress linkAddress) {
		super.addLinkAddress(linkAddress);
		Link link = linkAddress.getLink();
		if (link != null && link instanceof LtpLink) {
			link.addLinkListener(this);
			updateOperationalStateForLink();
			LtpLink ltpLink = (LtpLink)link;
			ltpLink.getSegmentQueue().add(_segmentQueue);
		}
	}
	
	@Override
	public void removeLinkAddress(LinkAddress linkAddress) {
		super.removeLinkAddress(linkAddress);
		Link link = linkAddress.getLink();
		if (link != null && link instanceof LtpLink) {
			link.removeLinkListener(this);
			LtpLink ltpLink = (LtpLink)link;
			ltpLink.getSegmentQueue().remove(_segmentQueue);
		}
		_segmentQueue.clear();
	}
	
	/**
	 * Called when Neighbor is removed from service.  We registered for
	 * Management.getInstance() property change notifications when constructed so we
	 * need to unregister here.
	 */
	@Override
	protected void removeNeighborImpl() {
		EventBroadcaster.getInstance().unregisterEventProcessor(
				Management.class.getCanonicalName(), this);
	}
	
	// Not Used
	@Override
	public void onNeighborOperationalChange(Neighbor neighbor, boolean neighborState) {
		// Nothing
	}

	/** Segment Transmit Rate Limit, for throttling xmits to this neighbor */
	public double getSegmentXmitRateLimit() {
		return _segmentXmitRateLimit;
	}

	/** Segment Transmit Rate Limit, for throttling xmits to this neighbor */
	public void setSegmentXmitRateLimit(double segmentXmitRateLimit) {
		this._segmentXmitRateLimit = segmentXmitRateLimit;
		_segmentSlotSecs = 1.0d / _segmentXmitRateLimit;
		_segmentSlotMSecs = _segmentSlotSecs * 1000.0d;
	}

	/** Burst of segments allowed before throttling starts kicking in */
	public long getBurstSize() {
		return _burstSize;
	}

	/** Burst of segments allowed before throttling starts kicking in */
	public void setBurstSize(long burstSize) {
		this._burstSize = burstSize;
	}

	/**
	 * The name of the Neighbor
	 */
	@Override
	public void setName(String aName) {
		super.setName(aName);
		if (_segmentQueue == null) {
			_segmentQueue = new LeakyBucketQueue(getName(), this);
		}
		_segmentQueue.setName(aName);
	}

	@Override
	public NeighborType getType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected void removeLinkImpl() {
		// TODO Auto-generated method stub
		
	}

}
