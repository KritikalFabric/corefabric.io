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
package com.cisco.qte.jdtn.udpcl;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * UDP Convergence Layer Neighbor
 */
public class UdpClNeighbor extends Neighbor implements IEventProcessor {

	private static final Logger _logger =
		Logger.getLogger(UdpClNeighbor.class.getCanonicalName());
	
	/** Default Segment Transmit Rate Limit, Segments per second */
	public static final double DEFAULT_SEGMENT_XMIT_RATE_LIMIT = 50.0d;
	
	/** Segment Transmit Rate Limit, for throttling xmits to this neighbor */
	protected double _segmentXmitRateLimit = DEFAULT_SEGMENT_XMIT_RATE_LIMIT;
	
	private double _segmentSlotSecs = 1.0d / _segmentXmitRateLimit;
	private double _segmentSlotMSecs = _segmentSlotSecs * 1000.0d;
	
	/** Default value for burstSize property */
	public static final long BURST_SIZE_DEFAULT = 2;
	
	/** Burst of segments allowed before throttling starts kicking in */
	protected long _burstSize = BURST_SIZE_DEFAULT;

	/**
	 * Queue of Segments awaiting transmission to this Neighbor
	 */
	protected LeakyBucketQueue _segmentQueue =
		new LeakyBucketQueue(getName(), this);
	
	// Credit mechanism for segment transmit rate throttling
	protected long _segmentsCredit = -_burstSize;
	private long _lastTimeTransmitted = 0;
	
	/**
	 * Constructor
	 * @param name Neighbor name
	 */
	public UdpClNeighbor(String name) {
		super(name);
	}
	
	/**
	 * Parse a Neighbor.  It is assume that the parse is sitting on the
	 * &lt; Neighbor &gt; element.  We will parse UdpClNeighbor specific
	 * attributes.  We will create the UdpClNeighbor, and then parse past
	 * the &lt; /Neighbor &gt; tag
	 * @param parser The parser
	 * @param name Name of neighbor
	 * @param neighborType Type of neighbor
	 * @return Created UdpClNeighbor
	 * @throws XMLStreamException On parse errors
	 * @throws IOException On I/O errors
	 * @throws JDtnException On JDTN specific errors
	 */
	public static UdpClNeighbor parseNeighbor(
			XmlRDParser parser, 
			String name, 
			NeighborType neighborType)
	throws XmlRdParserException, IOException, JDtnException {
		// Parse UdpClNeighbor specific attributes
		//   segmentXmitRateLimit='n'
		Double spsLimit = Utils.getDoubleAttribute(parser, "segmentXmitRateLimit", 0, Double.MAX_VALUE);
		//   burstSize='n'
		Long burstSize = Utils.getLongAttribute(parser, "burstSize", 0, Long.MAX_VALUE);
		
		// Create the Neighbor and then set its attributes
		UdpClNeighbor neighbor = new UdpClNeighbor(name);
		if (spsLimit != null) {
			neighbor.setSegmentXmitRateLimit(spsLimit);
		}
		if (burstSize != null) {
			neighbor.setBurstSize(burstSize);
		}
		
		// Parse embedded <LinkAddress> elements
		XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
		while (event == XmlRDParser.EventType.START_ELEMENT &&
				parser.getElementTag().equals("LinkAddress")) {
			
			LinkAddress linkAddr = LinkAddress.parse(parser);
			neighbor.addLinkAddress(linkAddr);
			
			event = Utils.nextNonTextEvent(parser);
		}
		
		// Parse </Neighbor> tag
		if (event != XmlRDParser.EventType.END_ELEMENT || 
			!parser.getElementTag().equals("Neighbor")) {
			throw new JDtnException("Expecting </Neighbor>");
		}
		
		return neighbor;
	}
	
	/**
	 * Write out UdpClNeighbor specific attributes to config file.
	 * @see com.cisco.qte.jdtn.general.Neighbor#writeConfigImpl(java.io.PrintWriter)
	 */
	@Override
	protected void writeConfigImpl(PrintWriter pw) {
		if (getSegmentXmitRateLimit() != DEFAULT_SEGMENT_XMIT_RATE_LIMIT) {
			pw.println("          segmentXmitRateLimit='" + getSegmentXmitRateLimit() + "'");
		}
		if (getBurstSize() != BURST_SIZE_DEFAULT) {
			pw.println("          burstSize='" + getBurstSize() + "'");
		}
	}
	
	/**
	 * Start this Neighbor
	 */
	@Override
	protected void startImpl() {
		super.startImpl();
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("start()");
		}
		EventBroadcaster.getInstance().registerEventProcessor(
				Management.class.getCanonicalName(), this);
	}

	/**
	 * Stop this Neighbor
	 * @throws InterruptedException 
	 */
	@Override
	protected void stopImpl() throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("stop()");
		}
		_segmentQueue.clear();
		EventBroadcaster.getInstance().unregisterEventProcessor(
				Management.class.getCanonicalName(), this);
		super.stopImpl();
	}

	/**
	 * Dump this object
	 * @param indent Amount of indentation
	 * @param detailed If detailed dump desired
	 * @return Dump as a String
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "UdpClNeighbor\n");
		sb.append(super.dump(indent + "  ", detailed));
		sb.append(indent + "  segmentXmitRateLimit=" + getSegmentXmitRateLimit() + "\n");
		sb.append(indent + "  burstSize=" + getBurstSize() + "\n");
		sb.append(indent + "  pendingXmits=" + _segmentQueue.size() + "\n");
		return sb.toString();
	}
	
	/**
	 * Enqueue given block for transmission
	 * @param block Given Block
	 * @throws JDtnException on errors
	 */
	public void enqueueOutboundBlock(UdpClDataBlock block) 
	throws JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("sendSegment(Neighbor=" + getName() + ")");
		}
		UdpClLink link = block.link;
		if (link == null) {
			throw new JDtnException("Segment has no Link specified");
		}
		
		if (!isNeighborOperational()) {
			throw new JDtnException("Neighbor " + getName() + " is not operational");
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
		link.getSegmentQueue().enqueue(this, block);
	}
	
	
	/**
	 * Called when Neighbor removed.  We do a stop.
	 * @see com.cisco.qte.jdtn.general.Neighbor#removeLinkImpl()
	 */
	@Override
	protected void removeLinkImpl() {
		try {
			stop();
		} catch (InterruptedException e) {
			_logger.log(Level.SEVERE, "removeLinkImpl()", e);
		}
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
	
	/*
	 * @see com.cisco.qte.jdtn.general.LinkListener#onNeighborAdded(com.cisco.qte.jdtn.general.Link, com.cisco.qte.jdtn.general.Neighbor)
	 */
	@Override
	public void onNeighborAdded(Link link, Neighbor neighbor) {
		// Nothing

	}

	/*
	 * @see com.cisco.qte.jdtn.general.LinkListener#onNeighborDeleted(com.cisco.qte.jdtn.general.Link, com.cisco.qte.jdtn.general.Neighbor)
	 */
	@Override
	public void onNeighborDeleted(Link link, Neighbor neighbor) {
		// Nothing

	}

	/*
	 * @see com.cisco.qte.jdtn.general.LinkListener#onNeighborOperationalChange(com.cisco.qte.jdtn.general.Neighbor, boolean)
	 */
	@Override
	public void onNeighborOperationalChange(Neighbor neighbor,
			boolean neighborState) {
		// Nothing
	}

	/*
	 * @see com.cisco.qte.jdtn.general.LinkListener#onNeighborScheduledStateChange(com.cisco.qte.jdtn.general.Neighbor, boolean)
	 */
	@Override
	public void onNeighborScheduledStateChange(Neighbor neighbor,
			boolean neighborState) throws InterruptedException {
		// Nothing
	}

	/**
	 * This method abstracted from Neighbor to report an event from
	 * EventBroadcaster.  We are interested in ManagementPropertyChangeEvents,
	 * and if the given event is one, we dispatch to propertyChanged() for
	 * further processing.
	 */
	@Override
	public void processEvent(IEvent event) {
		if (event instanceof ManagementPropertyChangeEvent) {
			ManagementPropertyChangeEvent mEvent =
				(ManagementPropertyChangeEvent)event;
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
			UdpClLink oldLink = 
				(oldValue instanceof UdpClLink) ? (UdpClLink)oldValue : null;
			UdpClLink newLink = 
				(newValue instanceof UdpClLink) ? (UdpClLink)newValue : null;
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
	
	/**
	 * Add a LinkAddress to this Neighbor
	 * @param linkAddress LinkAddress to add
	 */
	@Override
	public void addLinkAddress(LinkAddress linkAddress) {
		super.addLinkAddress(linkAddress);
		Link link = linkAddress.getLink();
		if (link != null && link instanceof UdpClLink) {
			link.addLinkListener(this);
			updateOperationalStateForLink();
			UdpClLink udpClLink = (UdpClLink)link;
			udpClLink.getSegmentQueue().add(_segmentQueue);
		}
	}
	
	/**
	 * Remove LinkAddress from this Neighbor
	 * @param linkAddress LinkAddress
	 */
	@Override
	public void removeLinkAddress(LinkAddress linkAddress) {
		super.removeLinkAddress(linkAddress);
		Link link = linkAddress.getLink();
		if (link != null && link instanceof UdpClLink) {
			link.removeLinkListener(this);
			UdpClLink udpClLink = (UdpClLink)link;
			udpClLink.getSegmentQueue().remove(_segmentQueue);
		}
		_segmentQueue.clear();
	}
	
	/**
	 * Nothing necessary when Neighbor removed (assuming it gets stopped)
	 * @see com.cisco.qte.jdtn.general.Neighbor#removeNeighborImpl()
	 */
	@Override
	protected void removeNeighborImpl() {
		// Nothing
	}

	/**
	 * Get Neighbor Type
	 * @see com.cisco.qte.jdtn.general.Neighbor#getType()
	 */
	@Override
	public NeighborType getType() {
		return Neighbor.NeighborType.NEIGHBOR_TYPE_UDPCL;
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

}
