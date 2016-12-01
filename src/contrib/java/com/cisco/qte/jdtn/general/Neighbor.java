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
package com.cisco.qte.jdtn.general;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.bp.EndPointId;
import com.cisco.qte.jdtn.bp.BPManagement;
import com.cisco.qte.jdtn.bp.EidScheme;
import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.ltp.LtpException;
import com.cisco.qte.jdtn.ltp.LtpNeighbor;
import com.cisco.qte.jdtn.tcpcl.TcpClNeighbor;
import com.cisco.qte.jdtn.udpcl.UdpClNeighbor;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * Representation of a DTN Neighbor, a peer in the DTN whom we can communicate
 * Bundles with.  Abstract superclass for all kinds of Neighbor, including
 * LtpNeighbor, TcpClNeighbor, and UdpClNeighbor.
 */
public abstract class Neighbor extends AbstractStartableComponent
implements LinkListener {

	private static final Logger _logger =
		Logger.getLogger(Neighbor.class.getCanonicalName());
	
	public static final float LIGHT_DISTANCE_SECS_DEFAULT = 0.001f;
	public static final int ROUND_TRIP_SLOP_MSECS_DEFAULT = 2000;

	/** Property name for management change events for Neighbor name property */
	public static final String NAME_PROPERTY = "Neighbor.name";
	/** Property name for management change events for Neighbor Address property */
	public static final String ADDRESS_PROPERTY = "Neighbor.address";
	/** Property name for mgmt change events for Neighbor EndpointId property */
	public static final String ENDPOINTID_PROPERTY = "Neighbor.endpointId";
	
	/**
	 * The "Link layer" addresses of the Neighbor.
	 */
	protected List<LinkAddress> _linkAddresses = new LinkedList<LinkAddress>();
	
	/**
	 * The Operational State of this Neighbor; Up or Down; Default is Down.  
	 * Determined by:
	 *   neighborOperational = linkOperational && neighorAdminUp
	 */
	protected boolean _neighborOperational = false;
	
	/** Default admin state */
	public static final boolean DEFAULT_ADMIN_UP = true;
	
	/**
	 * The Admin State of this Neighbor; Up or Down; Default is Up.  This
	 * is a state available to Managent to administratively enable or disable
	 * the Neighbor.  This state in turn helps determine Operational
	 * State according to:
	 *   neighborOperational = linkOperational && neighorAdminUp
	 */
	protected boolean _neighborAdminUp = DEFAULT_ADMIN_UP;
	
	/** Default Scheduled State */
	public static final boolean DEFAULT_SCHEDULED_UP = true;
	
	/**
	 * The Scheduled State of this Neighbor; Up or Down.  Default is Up.  This
	 * is a state available whenever Neighbor outages can be planned and scheduled.
	 * This state is not coupled to linkOperational, neighborAdminUp, or
	 * neighborOperational.
	 */
	protected boolean _neighborScheduledState = DEFAULT_SCHEDULED_UP;
	
	/**
	 * The Light Distance to the Neighbor, in Seconds
	 */
	protected float _lightDistanceSecs = LIGHT_DISTANCE_SECS_DEFAULT;
	
	/**
	 * Extra amount of time, in mSecs, beyond that implied by 2 X
	 * Light Distance propagation, used in estimating Round Trip delay.
	 * Think of this as processing delay involved in round trip.
	 */
	protected int _roundTripSlopMSecs = ROUND_TRIP_SLOP_MSECS_DEFAULT;
	
	/** EndPoint ID Stem for this Neighbor */
	protected EndPointId _endPointIdStem = null;
	
	/** True: this neighbor is temporary, don't save to config file */
	protected boolean _temporary = false;
	
	/** The Eid Scheme used to encode and decode bundles for this Neighbor */
	protected EidScheme _eidScheme = BPManagement.DEFAULT_EID_SCHEME;
	
	/**
	 * Constructor; sets name of the Neighbor
	 * @param name Name of Neighbor
	 */
	public Neighbor(String name) {
		super(name);
	}
	
	/**
	 * Parse XML Config for a Neighbor.  We assume that parser is sitting on the
	 * &lt; Neighbor &gt; element.  We advance the parser through and including
	 * the &lt; /Neighbor &gt;.  We create a new Neighbor and return it.
	 * @param parser The Pull Parser
	 * @return The Neighbor created
	 * @throws JDtnException On JDtn specific parse errors
	 * @throws XMLStreamException On general paser errors
	 * @throws IOException On I/O errors during parse
	 * @throws InterruptedException 
	 */
	public static Neighbor parseNeighbor(XmlRDParser parser)
	throws JDtnException, IOException, XmlRdParserException, InterruptedException {
		// <Neighbor 
		//   type="type"
		//   name="name"
		//   adminState="up|down|true|false"
		//   roundTripSlop='routeTripSlop'
		//   endPointIdStem='eid'
		//   links='linkName1 ...'
		//   addresses='address1 ...'
		//   eidScheme='dtn|ipn'
		//   otherAttributes...>
		//   <LinkAddress linkAddrAttributes... />
		//   ...
		// </Neighbor>
		String type = parser.getAttributeValue("type");
		if (type == null || type.length() == 0) {
			throw new JDtnException("Missing 'type' attribute");
		}
		NeighborType neighborType = getNeighborType(type);
		
		String name = parser.getAttributeValue("name");
		if (name == null || name.length() == 0) {
			throw new JDtnException("Missing 'name' attribute");
		}
		
		Boolean adminState = Utils.getBooleanAttribute(parser, "adminState");
		if (adminState == null) {
			adminState = new Boolean(true);
		}

		Integer roundSlop = Utils.getIntegerAttribute(parser, "roundTripSlop", 0, Integer.MAX_VALUE);
		String eidStr = parser.getAttributeValue("endPointIdStem");
		Double lightDistance = Utils.getDoubleAttribute(parser, "lightDistanceSecs", 0.0, Double.MAX_VALUE);
		String eidSchemeStr = Utils.getStringAttribute(parser, "eidScheme");
		
		Neighbor neighbor = null;
		switch (neighborType) {
		case NEIGHBOR_TYPE_LTP_UDP:
			neighbor = LtpNeighbor.parseNeighbor(parser, name, neighborType);
			break;
			
		case NEIGHBOR_TYPE_TCPCL:
			neighbor = TcpClNeighbor.parseNeighbor(parser, name, neighborType);
			break;
			
		case NEIGHBOR_TYPE_UDPCL:
			neighbor = UdpClNeighbor.parseNeighbor(parser, name, neighborType);
			break;
			
		default:
			throw new LtpException("Unrecognized type argument: " + type);
		}

		neighbor.setNeighborAdminUp(adminState);
		if (roundSlop != null) {
			neighbor.setRoundTripSlopMSecs(roundSlop.intValue());
		}
		if (eidStr != null && eidStr.length() > 0) {
			neighbor.setEndPointIdStem(EndPointId.createEndPointId(eidStr));
		}
		if (lightDistance != null) {
			neighbor.setLightDistanceSecs((float)lightDistance.doubleValue());
		}
		if (eidSchemeStr != null) {
			neighbor.setEidScheme(EidScheme.parseEidScheme(eidSchemeStr));
		}
		
		// Neighbor advances the parse beyond the </Neighbor>
		// so we don't need to.
		return neighbor;
		
	}
	
	/**
	 * Write config for this Neighbor out to given PrintWriter as a
	 * &lt; Neighbor &gt; element.
	 * @param pw Given PrintWriter
	 */
	public void writeConfig(PrintWriter pw) {
		if (!isTemporary()) {
			pw.println("        <Neighbor");
			pw.println("          type='" + getNeighborTypeString(getType()) + "'");
			pw.println("          name='" + getName() + "'");
			if (isNeighborAdminUp() != DEFAULT_ADMIN_UP) {
				pw.println("          adminState='" + isNeighborAdminUp() + "'");
			}
			if (getRoundTripSlopMSecs() != ROUND_TRIP_SLOP_MSECS_DEFAULT) {
				pw.println("          roundTripSlop='" + getRoundTripSlopMSecs() + "'");
			}
			if (isNeighborScheduledUp() != DEFAULT_SCHEDULED_UP) {
				pw.println("          scheduledUp='" + isNeighborScheduledUp() + "'");
			}
			pw.println("          endPointIdStem='" + getEndPointIdStem().getEndPointIdString() + "'");
			if (getLightDistanceSecs() != LIGHT_DISTANCE_SECS_DEFAULT) {
				pw.println("          lightDistanceSecs='" + getLightDistanceSecs() + "'");
			}
			if (getEidScheme() != BPManagement.DEFAULT_EID_SCHEME) {
				pw.println("          eidScheme='" + getEidScheme() + "'");
			}
			writeConfigImpl(pw);
			pw.println("        >");
			for (LinkAddress linkAddress : _linkAddresses) {
				linkAddress.writeConfig(pw);
			}
			pw.println("        </Neighbor>");
		}
	}
	
	/**
	 * Subclass provided method to get Neighbor type
	 * @return Neighbor type
	 */
	public abstract NeighborType getType();
	
	/**
	 * Subclass provided method to write specific attributes to given PrintWriter
	 * @param pw Given PrintWriter
	 */
	protected abstract void writeConfigImpl(PrintWriter pw);
	
	/**
	 * Determine if this Neighbor has the given LinkAddress
	 * @param targetLinkAddress Given LinkAddress
	 * @return true if this Neighbor has given LinkAddress
	 */
	public boolean hasLinkAddress(LinkAddress targetLinkAddress) {
		for (LinkAddress linkAddress : _linkAddresses) {
			if (targetLinkAddress.equals(linkAddress)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Determine if this Neighbor has the given Address.
	 * @param targetAddress Given Address
	 * @return True if this Neighbor has given Address
	 */
	public boolean hasAddress(Address targetAddress) {
		for (LinkAddress linkAddress : _linkAddresses) {
			if (linkAddress.getAddress().equals(targetAddress)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Determine if this Neighbor is reachable via given Link
	 * @param link Given Link
	 * @return True if this Neighbor is reachable via given Link
	 */
	public boolean hasLink(Link link) {
		for (LinkAddress linkAddress : _linkAddresses) {
			if (linkAddress.getLink().equals(link)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Internal method to update the operational state based on the state of
	 * the first operational associated Link found.
	 */
	protected void updateOperationalStateForLink() {
		Link operationalLink = findOperationalLink();
		if (operationalLink != null) {
			// There is at least one operational Link
			setNeighborOperational(this._neighborAdminUp);
		} else {
			// There are no operational Links
			setNeighborOperational(false);
		}
	}
	
	/**
	 * Convert to String; intended for debug; not really parseable
	 */
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
		StringBuffer sb = new StringBuffer(indent + "Neighbor\n");
		sb.append(indent + "  Name=" + getName() + "\n");
		sb.append(indent + "  AdminState=" + isNeighborAdminUp() + "\n");
		sb.append(indent + "  OperState=" + isNeighborOperational() + "\n");
		sb.append(indent + "  ScheduledUp=" + isNeighborScheduledUp() + "\n");
		sb.append(indent + "  LightSeconds=" + getLightDistanceSecs() + "\n");
		sb.append(indent + "  RoundTripSlop=" + getRoundTripSlopMSecs() + " mSecs\n");
		sb.append(indent + "  EndPointIdStem=" + getEndPointIdStem().getEndPointIdString() + "\n");
		sb.append(indent + "  EidScheme=" + getEidScheme() + "\n");
		if (detailed) {
			sb.append(indent + "  LinkAddresses:\n");
			for (LinkAddress linkAddress : _linkAddresses) {
				sb.append(linkAddress.dump(indent + "    ", detailed));
			}
		}
		return sb.toString();
	}

	/**
	 * Get the NeighborOperational property
	 * @return NeighborOperational property
	 */
	public boolean isNeighborOperational() {
		return _neighborOperational;
	}

	/**
	 * Set the NeighborOperational property.  Besides setting the corresponding
	 * member, if there is a change then we callback to the Link to notify
	 * it of the NeighborOperational state change.
	 * @param neighborOperational New value for NeighborOperational property
	 */
	private void setNeighborOperational(boolean neighborOperational) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("setNeighborOperational(Neighbor=" + getName() + 
					", Operational=" + neighborOperational + ")");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(dump("  ", true));
			}
		}
		boolean priorState = this._neighborOperational;
		this._neighborOperational = neighborOperational;
		for (LinkAddress linkAddress : _linkAddresses) {
			if (_neighborOperational != priorState) {
				linkAddress.getLink().notifyNeighborOperationalStateChange(
						this, _neighborOperational);
			}
		}
	}

	/**
	 * Get NeighborAdminUp property
	 * @return What I said
	 */
	public boolean isNeighborAdminUp() {
		return _neighborAdminUp;
	}

	/**
	 * Set the NeighborAdminUp property.  We set the corresponding member.
	 * In addition, if there is a change, then we call set the NeighborOperational
	 * property, according to:
	 * <p>
	 *   neighborOperational = neighborAdminUp && linkOperational
	 * <p>
	 * This can have the side effect of notifying the Link listeners of a change
	 * in the Neighbor Operational state.
	 * @param neighborAdminUp New value for NeighborAdminUp property
	 */
	public void setNeighborAdminUp(boolean neighborAdminUp) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("setNeighborAdminUp(Neighbor=" + getName() + 
					", AdminUp=" + neighborAdminUp + ")");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(dump("  ", true));
			}
		}
		boolean priorState = this._neighborAdminUp;
		this._neighborAdminUp = neighborAdminUp;
		updateOperationalStateForLink();
		if (this._neighborAdminUp != priorState) {
			setNeighborOperational(this._neighborAdminUp);
		}
	}
	
	/**
	 * Get the NeighborScheduled property of this Neighbor
	 * @return NeighborScheduled property - True => neighbor has been scheduled
	 * Up; False => neighbor has been scheduled down.
	 */
	public boolean isNeighborScheduledUp() {
		return _neighborScheduledState;
	}

	/**
	 * Set the Neighbor's Scheduled State
	 * @param neighborScheduledUp true (Up) or false (Down)
	 * @throws InterruptedException if interrupted
	 */
	public void setNeighborScheduledUp(boolean neighborScheduledUp) 
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("setNeighborScheduledState(Neighbor=" + getName() + 
					", scheduledState=" + neighborScheduledUp + ")");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(dump("  ", true));
			}
		}
		boolean priorState = this._neighborScheduledState;
		this._neighborScheduledState = neighborScheduledUp;
		if (_neighborOperational != priorState) {
			updateOperationalStateForLink();
		}
	}
	
	/**
	 * Get the set of LinkAddresses currently assigned to this Neighbor
	 * @return What I said
	 */
	public List<LinkAddress> getLinkAddresses() {
		return _linkAddresses;
	}
	
	/**
	 * Add a LinkAddress to this Neighbor.  A LinkAddress relates the Neighbor
	 * to a Link and also provides an Address by which the Neighbor can be
	 * reached.
	 * @param linkAddress LinkAddress to add
	 */
	public void addLinkAddress(LinkAddress linkAddress) {
		_linkAddresses.add(linkAddress);
		Link link = linkAddress.getLink();
		if (link != null) {
			link.addLinkListener(this);
			updateOperationalStateForLink();
		}
		Management.getInstance().fireManagementPropertyChangeEvent(this, ADDRESS_PROPERTY, null, linkAddress.getAddress());
	}
	
	/**
	 * Remove given LinkAddress from this Neighbor.
	 * @param linkAddress Given LinkAddress
	 */
	public void removeLinkAddress(LinkAddress linkAddress) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("removeLinkAddress(neighbor=" + getName() + ")");
			if (_logger.isLoggable(Level.FINEST)) {	
				_logger.finest(dump("  ", true));
			}
		}
		_linkAddresses.remove(linkAddress);
		updateOperationalStateForLink();
		removeLinkImpl();
		Management.getInstance().fireManagementPropertyChangeEvent(this, ADDRESS_PROPERTY, linkAddress.getAddress(), null);
	}
	
	
	/**
	 * Subclass supplied method called by super to inform subclass that the
	 * Neighbor is being deleted/removed from associated Link
	 */
	protected abstract void removeLinkImpl();
	
	/**
	 * Subclass supplied method to inform subclass that the Neighbor is
	 * being removed from service.
	 */
	protected abstract void removeNeighborImpl();
	
	/**
	 * Get the index into the _links/_addresses arrays of the given Address.
	 * @param address Given Address
	 * @return Index into _links/_addresses or -1 if none
	 */
	public int getAddressIndex(Address address) {
		for (int ix = 0; ix < _linkAddresses.size(); ix++) {
			LinkAddress linkAddress = _linkAddresses.get(ix);
			Address trialAddress = linkAddress.getAddress();
			if (trialAddress != null) {
				if (address.equals(trialAddress)) {
					return ix;
				}
			}
		}
		return -1;
	}
	
	/**
	 * Remove the Link Address at the given index
	 * @param index Given Index
	 * @throws LtpException If Index out of bounds of either the _addresses
	 * or _links arrays.
	 */
	public void removeLinkAddress(int index) throws LtpException {
		try {
			_linkAddresses.remove(index);
		} catch (Exception e) {
			throw new LtpException(e);
		}
	}
	
	/**
	 * Find an operational Link to which this Neighbor can be reached.
	 * @return The first Link found, in unpredictable order, or null if there
	 * are no such Links.
	 */
	public Link findOperationalLink() {
		for (LinkAddress linkAddress : _linkAddresses) {
			Link link = linkAddress.getLink();
			if (link != null && link.isLinkOperational()) {
				return linkAddress.getLink();
			}
		}
		return null;
	}
	
	/**
	 * Find a LinkAddress for this Neighbor which is associated with an
	 * operational Link.
	 * @return The first LinkAddress found, in unpredicatable order, or null if there
	 * are no such LinkAddresses.
	 */
	public LinkAddress findOperationalLinkAddress() {
		for (LinkAddress linkAddress : _linkAddresses) {
			Link link = linkAddress.getLink();
			if (link.isLinkOperational()) {
				return linkAddress;
			}
		}
		return null;
	}
	
	/**
	 * Find a Link associated with this Neighbor which has the given name.
	 * @param linkName The given Link Name
	 * @return The associated Link or null if none.
	 */
	public Link findLinkNamed(String linkName) {
		for (LinkAddress linkAddress : _linkAddresses) {
			if (linkAddress.getLink().getName().equals(linkName)) {
				return linkAddress.getLink();
			}
		}
		return null;
	}
	
	/**
	 * Get the "link level" address for this Neighbor corresponding to the
	 * given Link
	 * @param link Given Link
	 * @return Address or null if none
	 */
	public Address getAddressForLink(Link link) {
		for (LinkAddress linkAddress : _linkAddresses) {
			
			if (link.equals(linkAddress.getLink())) {
				return linkAddress.getAddress();
			}
		}
		return null;
	}
	
	/**
	 * Called by Management to shutdown this Neighbor
	 */
	public void shutdown() {
		for (LinkAddress linkAddress : _linkAddresses) {
			removeLinkAddress(linkAddress);
		}
	}
	
	/**
	 * The Light Distance to the Neighbor, in Seconds
	 */
	public float getLightDistanceSecs() {
		return _lightDistanceSecs;
	}

	/**
	 * The Light Distance to the Neighbor, in Seconds
	 */
	public void setLightDistanceSecs(float lightDistance) {
		this._lightDistanceSecs = lightDistance;
	}
	
	/**
	 * Get the propagation delay to this Neighbor
	 * @return Propagation delay, mSecs
	 */
	public int getLightTimeMSecs() {
		return (int)(_lightDistanceSecs * 1000.0f);
	}
	
	/**
	 * Get the round trip delay to/from this Neighbor.  We compute 2 *
	 * propagation delay plus some conservative slop.
	 * @return Round trip delay, mSecs
	 */
	public int getRoundTripTimeMSecs() {
		return 2 * getLightTimeMSecs() + _roundTripSlopMSecs;
	}
	
	/**
	 * The name of the Neighbor
	 */
	@Override
	public void setName(String aName) {
		Management.getInstance().fireManagementPropertyChangeEvent(
				this, NAME_PROPERTY, getName(), aName);
		setName(aName);
	}

	/**
	 * Extra amount of time, in mSecs, beyond that implied by 2 X
	 * Light Distance propagation, used in estimating Round Trip delay.
	 * Think of this as processing delay involved in round trip.
	 */
	public int getRoundTripSlopMSecs() {
		return _roundTripSlopMSecs;
	}

	/**
	 * Extra amount of time, in mSecs, beyond that implied by 2 X
	 * Light Distance propagation, used in estimating Round Trip delay.
	 * Think of this as processing delay involved in round trip.
	 */
	public void setRoundTripSlopMSecs(int roundTripSlopMSecs) {
		this._roundTripSlopMSecs = roundTripSlopMSecs;
	}

	/** EndPoint ID Stem for this Neighbor */
	public EndPointId getEndPointIdStem() {
		return _endPointIdStem;
	}

	/** EndPoint ID Stem for this Neighbor */
	public void setEndPointIdStem(EndPointId endPointIdStem) {
		Management.getInstance().fireManagementPropertyChangeEvent(
				this, ENDPOINTID_PROPERTY, _endPointIdStem, endPointIdStem);
		this._endPointIdStem = endPointIdStem;
	}

	/** True: this neighbor is temporary, don't save to config file */
	public boolean isTemporary() {
		return _temporary;
	}

	/** True: this neighbor is temporary, don't save to config file */
	public void setTemporary(boolean temporary) {
		this._temporary = temporary;
	}

	/**
	 * Supported Convergence Layer types
	 */
	public enum NeighborType {
		NEIGHBOR_TYPE_LTP_UDP,
		NEIGHBOR_TYPE_TCPCL,
		NEIGHBOR_TYPE_UDPCL
	}
	
	/**
	 * Convert given NeighborType to a String
	 * @param neighborType Given NeighborType
	 * @return Corresponding String
	 */
	public static String getNeighborTypeString(NeighborType neighborType) {
		switch (neighborType) {
		case NEIGHBOR_TYPE_LTP_UDP:
			return "udp";
			
		case NEIGHBOR_TYPE_TCPCL:
			return "tcpcl";
			
		case NEIGHBOR_TYPE_UDPCL:
			return "udpcl";
			
		default:
			throw new IllegalArgumentException("neighborType=" + neighborType);
		}
	}
	
	/**
	 * Convert given NeighborType String to a NeighborType
	 * @param neighborTypeString Given NeighborType String
	 * @return Corresponding NeighborType
	 * @throws JDtnException if neighborTypeString doesn't identify a valid
	 * NeighborType.
	 */
	public static NeighborType getNeighborType(String neighborTypeString)
	throws JDtnException {
		if (neighborTypeString.equalsIgnoreCase("udp") ||
			neighborTypeString.equalsIgnoreCase("ltpudp")) {
			return NeighborType.NEIGHBOR_TYPE_LTP_UDP;
		
		} else if (neighborTypeString.equalsIgnoreCase("tcpcl")) {
			return NeighborType.NEIGHBOR_TYPE_TCPCL;
			
		} else if (neighborTypeString.equalsIgnoreCase("udpcl")) {
			return NeighborType.NEIGHBOR_TYPE_UDPCL;
			
		} else {
			throw new JDtnException("Invalid Neighbor Type String=" + 
					neighborTypeString);
		}
	}

	/** The Eid Scheme used to encode and decode bundles for this Neighbor */
	public EidScheme getEidScheme() {
		return _eidScheme;
	}

	/** The Eid Scheme used to encode and decode bundles for this Neighbor */
	public void setEidScheme(EidScheme eidScheme) {
		this._eidScheme = eidScheme;
	}

	@Override
	protected void startImpl() {
		// Nothing
	}

	@Override
	protected void stopImpl() throws InterruptedException {
		// Nothing
	}
}
