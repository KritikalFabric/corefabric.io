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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.ltp.LtpException;
import com.cisco.qte.jdtn.ltp.LtpLink;
import com.cisco.qte.jdtn.ltp.LtpManagement;
import com.cisco.qte.jdtn.tcpcl.TcpClLink;
import com.cisco.qte.jdtn.udpcl.UdpClLink;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * Generic abstraction for what we refer to as a Link, closely aligned with
 * the concept of a Network Interface.  Abstract superclass for Ltplink,
 * TcpClLink, and UdpClLink.
 */
public abstract class Link extends AbstractStartableComponent {

	private static final Logger _logger =
		Logger.getLogger(Link.class.getCanonicalName());
	
	private static long _sequenceNumber = 0;
	
	// List of Listeners on the Link
	private final List<LinkListener> _listeners =
		new ArrayList<LinkListener>();
	
	/**
	 * The Type of the Link
	 */
	protected LinkType _linkType = getLinkTypeImpl();
	
	/**
	 * The Link Operational State; Up (true) or down (false). It
	 * is derived as the logical AND of linkDatalinkUp and linkAdminState.
	 */
	protected boolean _linkOperational = true;
	
	/**
	 * The Datalink state, Up (true) or down (false).  This is determined by the
	 * Link subclass implementation.
	 */
	protected boolean _linkDatalinkUp = true;
	
	/** Default admin State */
	public static final boolean DEFAULT_LINK_ADMIN_STATE = true;
	
	/**
	 * The Link admin state, Up (true) or down (false).  This is determined by
	 * management.  Default is Up (true).
	 */
	protected boolean _linkAdminState = DEFAULT_LINK_ADMIN_STATE;
	
	/**
	 * Parse &lt; Link &gt; element.  It is assumed that the parse is sitting
	 * on the &lt; Link &gt; element.
	 * @param parser The Parser
	 * @return A Link created from the &lt; Link &gt; element.
	 * @throws JDtnException On JDTN specific exceptions
	 * @throws XMLStreamException On general parse errors
	 * @throws IOException On I/O errors
	 * @throws InterruptedException If we are interrupted
	 */
	public static Link parseLink(XmlRDParser parser)
	throws JDtnException, XmlRdParserException, IOException, InterruptedException {
		// <Link 
		//    type="type" 				Required
		//    linkName="name" 			Required
		//    adminState="true|false" 
		
		// Required 'type' attribute
		String typeStr = parser.getAttributeValue("type");
		if (typeStr == null || typeStr.length() == 0) {
			throw new LtpException("Missing 'type' attribute in <Link>");
		}
		LinkType linkType = getLinkType(typeStr);
		
		// Required 'linkName' attribute
		String name = parser.getAttributeValue("linkName");
		if (name == null || name.length() == 0) {
			throw new LtpException("Missing 'linkName' attribute in <Link>");
		}
			
		// Optional 'adminState' attribute
		Boolean adminState = Utils.getBooleanAttribute(parser, "adminState");
		if (adminState == null) {
			adminState = new Boolean(true);
		}
		
		Link link = null;
		switch (linkType) {
		case LINK_TYPE_LTP_UDP:
			link = LtpLink.parseLink(parser, name, linkType);
			break;
			
		case LINK_TYPE_TCPCL:
			link = TcpClLink.parseLink(parser, name, linkType);
			break;
			
		case LINK_TYPE_UDPCL:
			link = UdpClLink.parseLink(parser, name, linkType);
			break;
			
		default:
			throw new IllegalArgumentException("linkType=" + linkType);
		}
		
		link.setLinkAdminUp(adminState);
		
		return link;
	}
	
	/**
	 * Write configuration for this Link to given PrintWriter as a &lt; Link &gt;
	 * element.  Includes write of contained Neighbors as &lt; Neighbor &gt; elements.
	 * @param pw Given PrintWriter
	 */
	public void writeConfig(PrintWriter pw) {
		pw.println("      <Link");
		pw.println("        type='" + getLinkTypeString(getLinkType()) + "'");
		pw.println("        linkName='" + getName() + "'");
		if (isLinkAdminUp() != DEFAULT_LINK_ADMIN_STATE) {
			pw.println("        adminState='" + isLinkAdminUp() + "'");
		}
		writeConfigImpl(pw);
		pw.println("      >");
		pw.println("      </Link>");
	}
	
	protected abstract LinkType getLinkTypeImpl();
	
	/**
	 * Subclass provided method to write subclass specific attributes to given
	 * PrintWriter.
	 * @param pw Given PrintWriter
	 */
	protected abstract void writeConfigImpl(PrintWriter pw);

	/**
	 * Do nothing constructor
	 */
	public Link() {
		super("Link" + _sequenceNumber++);
	}
	
	/**
	 * Constructor.  Sets name of Link
	 * @param name Name of Link; local significance only.
	 */
	public Link(String name) {
		super("Link(" + name + ")");
		setName(name);
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
		try {
			linkStartImpl();
		} catch (Exception e) {
			_logger.log(Level.SEVERE, "Exception from linkStartImpl()", e);
			return;
		}
	}
	
	/**
	 * Subclass provided method called when the Link is started.  This gives
	 * the subclass an opportunity to initialize
	 * @throws Exception if cannot start
	 */
	protected abstract void linkStartImpl() throws Exception;
	
	/**
	 * Stop the link.  Stops the Link Receive Thread, Link Monitor
	 * Thread, and Link Transmit Thread.
	 */
	@Override
	protected void stopImpl() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("stop(" + getName() + ")");
		}
		try {
			linkStopImpl();
		} catch (Exception e) {
			_logger.log(Level.SEVERE, "Exception from linkStopImpl()", e);
			return;
		}
	}
	
	/**
	 * Subclass provided method called when the Link is stopped.  This gives
	 * the subclass an opportunity to clean up.
	 */
	protected abstract void linkStopImpl() throws InterruptedException;

	/**
	 * Add given LinkListener to this Link's list of LinkListeners
	 * @param listener Given Listener
	 */
	public void addLinkListener(LinkListener listener) {
		synchronized (_listeners) {
			_listeners.add(listener);
		}
	}
	
	/**
	 * Remove given LinkListener from this Link's list of LinkListeners
	 * @param listener Given Listener
	 */
	public void removeLinkListener(LinkListener listener) {
		synchronized (_listeners) {
			_listeners.remove(listener);
		}
	}
	
	/**
	 * Called from subclass to report Link has gone down.  We set the
	 * LinkDatalinkUp property accordingly.  We also set the Link Operational
	 * State accordingly.  This may then result in callouts to Link Listeners.
	 */
	protected void notifyLinkDatalinkDown() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("notifyLinkDatalinkDown(" + getName() + ")");
		}
		setLinkDatalinkUp(false);		
	}
	
	/**
	 * Called from subclass to report Link has gone up.    We set the
	 * LinkDatalinkUp property accordingly.  We also set the Link Operational
	 * State accordingly.  This may then result in callouts to Link Liseeners.
	 */
	protected void notifyLinkDatalinkUp() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("notifyLinkDatalinkUp(" + getName() + ")");
		}
		setLinkDatalinkUp(true);
	}
	
	/**
	 * Call from LinksList when this Link removed
	 */
	public void linkRemoved() {
		linkRemovedImpl();
	}
	
	/**
	 * Subclass supplied method, called when Link is removed from LinksList.
	 */
	protected abstract void linkRemovedImpl();
	
	/**
	 * Called from Neighbor to report Neighbor has changed operational state to up or down.
	 * We call out to Link's listeners.
	 * @param neighbor Neighbor whose state has changed
	 * @param neighborState New state of Neighbor
	 */
	public void notifyNeighborOperationalStateChange(Neighbor neighbor, boolean neighborState) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("notifyNeighborStateChange(Link=" + getName() + 
					", Neighbor=" + neighbor.getName() +
					", neighborState=" + neighborState + ")");
		}
		ArrayList<LinkListener> tempListeners = null;
		synchronized (_listeners) {
			tempListeners = new ArrayList<LinkListener>(_listeners);
		}
		for (LinkListener listener : tempListeners) {
			listener.onNeighborOperationalChange(neighbor, neighborState);
		}
		
	}
	
	/**
	 * Called from Neighbor to report a scheduled, or planned, change in state.
	 * We respond by calling out to Link listeners.
	 * @param neighbor Affected Neighbor
	 * @param neighborState The Neighbor's scheduled state.
	 * @throws InterruptedException if interrupted
	 */
	public void notifyNeighborScheduledStateChange(
			Neighbor neighbor, 
			boolean neighborState) throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("notifyNeighborScheduledStateChange(Link=" + getName() +
					", Neighbor=" + neighbor.getName() +
					", neighborState=" + 
					neighborState + ")");
		}
		ArrayList<LinkListener> tempListeners = null;
		synchronized (_listeners) {
			tempListeners = new ArrayList<LinkListener>(_listeners);
		}
		for (LinkListener listener : tempListeners) {
			listener.onNeighborScheduledStateChange(neighbor, neighborState);
		}
	}
	
	/**
	 * Notify listeners that a Neighbor has been added to this Link
	 * @param neighbor The Neighbor
	 */
	public void notifyNeighborAdded(
			Neighbor neighbor) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("notifyNeighborAdded(Link=" + getName() +
					", Neighbor=" + neighbor.getName());
		}
		ArrayList<LinkListener> tempListeners = null;
		synchronized (_listeners) {
			tempListeners = new ArrayList<LinkListener>(_listeners);
		}
		for (LinkListener listener : tempListeners) {
			listener.onNeighborAdded(this, neighbor);
		}
	}
	
	/**
	 * Notify listeners that a Neighbor has been removed from this Link
	 * @param neighbor The Neighbor
	 */
	public void notifyNeighborRemoved(
			Neighbor neighbor) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("notifyNeighborRemoved(Link=" + getName() +
					", Neighbor=" + neighbor.getName());
		}
		ArrayList<LinkListener> tempListeners = null;
		synchronized (_listeners) {
			tempListeners = new ArrayList<LinkListener>(_listeners);
		}
		for (LinkListener listener : tempListeners) {
			listener.onNeighborDeleted(this, neighbor);
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
		StringBuffer sb = new StringBuffer(indent + "Link\n");
		sb.append(indent + "  Name=" + getName() + "\n");
		sb.append(indent + "  Type=" + getLinkTypeString(getLinkType()) + "\n");
		sb.append(indent + "  Operational=" + isLinkOperational() + "\n");
		sb.append(indent + "  DataLinkUp=" + isLinkDatalinkUp() + "\n");
		sb.append(indent + "  AdminState=" + isLinkAdminUp() + "\n");
		return sb.toString();
	}
	
	/**
	 * The Type of the Link
	 */
	public LinkType getLinkType() {
		return _linkType;
	}
	
	/**
	 * The Type of the Link
	 */
	public void setLinkType(LinkType linkType) {
		_linkType = linkType;
	}
	
	public boolean isLinkDatalinkUp() {
		return _linkDatalinkUp;
	}

	/**
	 * Internal method to set LinkDatalinkUp property.  Sets the linkDatalinkUp
	 * member.  Also sets the LinkOperational property.  That might result
	 * in callouts to Link listeners.
	 * @param linkDatalinkUp new value of LinkDatalinkUp
	 */
	protected void setLinkDatalinkUp(boolean linkDatalinkUp) {
		if (linkDatalinkUp != _linkDatalinkUp) {
			if (GeneralManagement.isDebugLogging()) {
				_logger.finer("setLinkDatalinkUp(" + getName() + "=" + linkDatalinkUp);
			}
			this._linkDatalinkUp = linkDatalinkUp;
			setLinkOperational(this._linkAdminState && this._linkDatalinkUp);
		}
	}

	/**
	 * Link Operational Property.  This is a property derived from other
	 * properties according to:
	 * linkOperational = linkAdminState && linkDatalinkUp
	 * @return Link Operationl Property
	 */
	public boolean isLinkOperational() {
		return _linkOperational;
	}

	/**
	 * Internal method to set the LinkOperational property.  Besides setting
	 * the value, if there is a change in it then we issue a LinkOperationalChange
	 * to listeners.
	 * @param linkUp New value of LinkOperational property
	 */
	private void setLinkOperational(boolean linkUp) {
		boolean priorLinkOperState = this._linkOperational;
		this._linkOperational = linkUp;
		if (priorLinkOperState != _linkOperational) {
			notifyLinkOperationalChange();
		}
	}

	/**
	 * Internal method to notify listeners of LinkOperational change
	 */
	private void notifyLinkOperationalChange() {
		if (LtpManagement.getInstance().isLogLinkOperStateChanges()) {
			_logger.warning(
					"Link " + getName() + " is " + 
					(isLinkOperational() ? "Up" : "Down"));
		}
		ArrayList<LinkListener> tempListeners = null;
		synchronized (_listeners) {
			tempListeners = new ArrayList<LinkListener>(_listeners);
		}
		for (LinkListener listener : tempListeners) {
			listener.onLinkOperationalStateChange(this, isLinkOperational());
		}
	}
	
	public boolean isLinkAdminUp() {
		return _linkAdminState;
	}

	/**
	 * Set the LinkAdminState property.  Besides setting the LinkAdminState,
	 * if there is a change then we also set the LinkOperational property
	 * according to linkOperational = linkAdminState && linkDatalink, and
	 * thus might cause a notification of LinkOperational change.
	 * @param newLinkAdminState New value of LinkAdminStaste.
	 */
	public void setLinkAdminUp(boolean newLinkAdminState) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("setLinkAdminState(Link=" + getName() + ", AdminState=" + 
					newLinkAdminState + ")");
		}
		boolean priorAdminState = _linkAdminState;
		_linkAdminState = newLinkAdminState;
		
		if (_linkAdminState != priorAdminState) {
			setLinkOperational(this._linkAdminState && _linkDatalinkUp);
		}
		
	}

	/**
	 * Possibilities for LinkType
	 */
	public enum LinkType {
		LINK_TYPE_LTP_UDP,
		LINK_TYPE_TCPCL,
		LINK_TYPE_UDPCL;
	}
	
	public static String getLinkTypeString(LinkType linkType) {
		switch (linkType) {
		case LINK_TYPE_LTP_UDP:
			return "udp";
			
		case LINK_TYPE_TCPCL:
			return "tcpcl";
			
		case LINK_TYPE_UDPCL:
			return "udpcl";
			
		default:
			throw new IllegalArgumentException("LinkType=" + linkType);
		}
	}
	
	public static LinkType getLinkType(String linkTypeStr)
	throws JDtnException {
		
		if (linkTypeStr.equalsIgnoreCase("udpcl")) {
			return LinkType.LINK_TYPE_UDPCL;
			
		} else if (linkTypeStr.equalsIgnoreCase("tcpcl")) {
			return LinkType.LINK_TYPE_TCPCL;
			
		} else if (linkTypeStr.equalsIgnoreCase("ltpudp") ||
				   linkTypeStr.equalsIgnoreCase("udp")) {
			return LinkType.LINK_TYPE_LTP_UDP;
		
		} else {
			throw new JDtnException("Invalid LinkTypeStr: " + linkTypeStr);
		}
			
	}
	
}
