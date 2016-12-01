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
package com.cisco.qte.jdtn.bp;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.cisco.qte.jdtn.component.AbstractEventProcessor;
import com.cisco.qte.jdtn.component.EventBroadcaster;
import com.cisco.qte.jdtn.component.IEvent;
import com.cisco.qte.jdtn.events.LinksEvent;
import com.cisco.qte.jdtn.general.Link;
import com.cisco.qte.jdtn.general.LinkListener;
import com.cisco.qte.jdtn.general.LinksList;
import com.cisco.qte.jdtn.general.Neighbor;
import com.cisco.qte.jdtn.general.NeighborsList;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * A Route; a mapping from a set of EndPointIds to a designated
 * next-hop Link and Neighbor.
 */
public class Route extends AbstractEventProcessor implements LinkListener {
	private static final Logger _logger =
		Logger.getLogger(Route.class.getCanonicalName());
	
	/** R.E. Pattern for matching against EndPointId */
	protected String destinationPattern;
	
	private Link _link = null;
	private Neighbor _neighbor = null;
	private Pattern _pattern;
	private boolean _temporary = false;
	
	/**
	 * Parse &lt; Route &gt; element in XML Configuration and construct a
	 * Route from the configuration.  Assumes parser is sitting on the
	 * &lt; Route &gt;.  Parses up to and including the closing
	 * &lt; /Route &gt;.
	 * @param parser Parser
	 * @return new Route
	 * @throws BPException on &lt; Route &gt; specific parsing errors
	 * @throws IOException On I/O errors reading config file
	 * @throws XMLStreamException on general parsing errors
	 */
	public static Route parse(XmlRDParser parser) 
	throws BPException, XmlRdParserException, IOException {
		// <route
		//   name='name'
		//   pattern='pattern'
		//   link='linkName'
		//   neighbor='neighborName'
		// </route>
		String name = parser.getAttributeValue("name");
		if (name == null || name.length() == 0) {
			throw new BPException("Missing 'name' attribute");
		}
		
		String pattern = parser.getAttributeValue("pattern");
		if (pattern == null || pattern.length() == 0) {
			throw new BPException("Missing 'pattern' attribute");
		}
		
		String linkName = parser.getAttributeValue("link");
		if (linkName == null || linkName.length() == 0) {
			throw new BPException("Missing 'link' attribute");
		}
		
		String neighborName = parser.getAttributeValue("neighbor");
		if (neighborName == null || neighborName.length() == 0) {
			throw new BPException("Missing 'neighbor' attribute");
		}
		
		Route route = new Route(name, pattern, linkName, neighborName);
		
		XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
		if (event != XmlRDParser.EventType.END_ELEMENT ||
			!parser.getElementTag().equals("Route")) {
			throw new BPException("Expecting </Route>");
		}
		return route;
	}
	
	/**
	 * Write configuration for this Route out to given PrintWriter.  We output
	 * a full &lt; Route &gt; element and closing.
	 * @param pw Given PrintWriter
	 */
	public void writeConfig(PrintWriter pw) {
		if (!isTemporary()) {
			pw.println("      <Route");
			pw.println("        name='" + getName() + "'");
			pw.println("        pattern='" + getDestinationPattern() + "'");
			pw.println("        link='" + getNextHopLinkName() + "'");
			pw.println("        neighbor='" + getNextHopNeighborName() + "'");
			pw.println("      />");
		}
	}
	
	/**
	 * Construct a Route with given data
	 * @param name Name of the Route
	 * @param pattern R.E. Pattern for matching against EndPointIds
	 * @param nextHopLinkName Name of Next Hop Link
	 * @param nextHopNeighborName Name of Next Hop Neighbor
	 * @throws BPException if nextHopLinkName doesn't name a valid Link or
	 * nextHopNeighborName doesn't name a valid Neighbor
	 */
	public Route(
			String name,
			String pattern, 
			String nextHopLinkName, 
			String nextHopNeighborName) throws BPException {
		super(name);
		this.destinationPattern = pattern;
		
		_link = LinksList.getInstance().findLinkByName(nextHopLinkName);
		if (_link == null) {
			throw new BPException("Link Name " + nextHopLinkName + 
					" doesn't name a valid Link");
		}
		_neighbor = NeighborsList.getInstance().findNeighborByName(nextHopNeighborName);
		if (_neighbor == null) {
			throw new BPException("Neighbor Name " + nextHopNeighborName +
					" doesn't name a valid Neighbor");
		}
		if (_neighbor.findLinkNamed(_link.getName()) == null) {
			throw new BPException("Neighbor " + _neighbor.getName() +
					" doesn't list a Link named " + nextHopLinkName);
		}
		EventBroadcaster.getInstance().registerEventProcessor(
				LinksList.class.getCanonicalName(), 
				this);
		_link.addLinkListener(this);
		
		try {
			_pattern = Pattern.compile(destinationPattern);
		} catch (PatternSyntaxException e) {
			throw new BPException(e);
		}
	}

	/**
	 * Determine if the given EndPointId matches the DestinationPattern for the
	 * Route.
	 * @param endPointId Given EndPointId
	 * @return What I said
	 */
	public boolean matches(EndPointId endPointId) {
		Matcher matcher = _pattern.matcher(endPointId.getEndPointIdString());
		return matcher.matches();
	}
	
	/**
	 * Dump this object
	 * @param indent Amount of indentation
	 * @param detailed Whether to dump detailed info or not
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "Route\n");
		sb.append(indent + "  Name=" + getName() + "\n");
		sb.append(indent + "  DestPattern=" + getDestinationPattern() + "\n");
		sb.append(indent + "  NextHopLink=" + getNextHopLinkName() + "\n");
		sb.append(indent + "  NextHopNeighbor=" + getNextHopNeighborName() + "\n");
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return dump("", false);
	}
	
	@Override
	public boolean equals(Object thatObj) {
		if (thatObj == null || !(thatObj instanceof Route)) {
			return false;
		}
		Route that = (Route)thatObj;
		if (this._link == null || this._neighbor == null ||
			that._link == null || that._neighbor == null) {
			return false;
		}
		return
			this.getName().equals(that.getName()) &&
			this.destinationPattern.equals(that.destinationPattern) &&
			this._link.getName().equals(that._link.getName()) &&
			this._neighbor.getName().equals(that._neighbor.getName());
	}
	
	@Override
	public int hashCode() {
		int result = getName().hashCode() + destinationPattern.hashCode();
		if (_link != null) {
			result += _link.getName().hashCode();
		}
		if (_neighbor != null) {
			result += _neighbor.getName().hashCode();
		}
		return result;
	}
	
	/**
	 * Process an Event from EventBroadcaster concerning the addition
	 * or removal of a Link from the LinksList.  We are interested in
	 * Link removal.  If this route references a Link that has been removed,
	 * we remove the Route from the Route Table.
	 */
	@Override
	protected void processEventImpl(IEvent event) {
		if (event instanceof LinksEvent) {
			LinksEvent lEvent = (LinksEvent)event;
			switch (lEvent.getLinksEventSubtype()) {
			case LINK_ADDED_EVENT:
				// Nothing
				break;
			case LINK_REMOVED_EVENT:
				onLinkRemoved(lEvent.getLink());
				break;
			default:
				_logger.severe(
						"Unknown Links Event subtype: " + lEvent.getLinksEventSubtype());
			}
		}
	}
	
	/**
	 * Called from LinksList when a Link is removed.  We look to see if it is
	 * the Link we have been configured with.  If so, we remove ourself
	 * from the RouteTable.
	 */
	private void onLinkRemoved(Link link) {
		if (_link == null || _neighbor == null) {
			return;
		}
		if (link.getName().equals(_link.getName())) {
			removeFromRouteTable();
		}		
	}

	/**
	 * Remove this Route from the RouteTable in response to Link or
	 * Neighbor deletion.
	 */
	private void removeFromRouteTable() {
		RouteTable.getInstance().removeRoute(this);
		_link.removeLinkListener(this);
		EventBroadcaster.getInstance().unregisterEventProcessor(
				LinksList.class.getCanonicalName(), this);
		_link = null;
		_neighbor = null;
	}

	/**
	 * Called from Link when a Neighbor is removed.  We look to see if it is
	 * the Neighbor we have been configured wiht.  If so, we remove ourself
	 * from the Route Table.
	 */
	@Override
	public void onNeighborDeleted(Link link, Neighbor neighbor) {
		if (_link == null || _neighbor == null) {
			return;
		}
		if (neighbor.getName().equals(_neighbor.getName())) {
			removeFromRouteTable();
		}
	}

	/**
	 * Called from Link to report change in Link operational state.
	 */
	@Override
	public void onLinkOperationalStateChange(Link link, boolean linkOperational) {
		// Nothing
	}

	/**
	 * Called from Link to report addition of a Neighbor
	 */
	@Override
	public void onNeighborAdded(Link link, Neighbor neighbor) {
		// Nothing
	}

	/**
	 * Called from Link to report change in Neighbor Operational State
	 */
	@Override
	public void onNeighborOperationalChange(
			Neighbor neighbor,
			boolean neighborState) {
		// Nothing
	}

	/**
	 * Called from Link to report change in Neighbor Scheduled State
	 */
	@Override
	public void onNeighborScheduledStateChange(
			Neighbor neighbor,
			boolean neighborState) {
		// Nothing
	}

	/**
	 * Get the approximate round trip time (plus slop) for this Route.
	 * @return What I said
	 */
	public int getRoundTripTime() {
		if (_neighbor != null) {
			return _neighbor.getRoundTripTimeMSecs();
		} else {
			return 0;
		}
	}
	
	/** R.E. Pattern for matching against EndPointId */
	public String getDestinationPattern() {
		return destinationPattern;
	}

	/** Next Hop Link Name */
	public String getNextHopLinkName() {
		if (_link == null) {
			return null;
		}
		return _link.getName();
	}

	/** Next Hop Neighbor Name */
	public String getNextHopNeighborName() {
		if (_neighbor == null) {
			return null;
		}
		return _neighbor.getName();
	}

	/** The Link which this Route points to */
	public Link getLink() {
		return _link;
	}
	
	/** The Neighbor which this Route points to */
	public Neighbor getNeighbor() {
		return _neighbor;
	}

	/** Whether this Route should be saved as part of config file */
	public boolean isTemporary() {
		return _temporary;
	}

	/** Whether this Route should be saved as part of config file */
	public void setTemporary(boolean temporary) {
		this._temporary = temporary;
	}

	/* (non-Javadoc)
	 * @see com.cisco.qte.jdtn.component.AbstractStartableComponent#startImpl()
	 */
	@Override
	protected void startImpl() {
		// Nothing
	}

	/* (non-Javadoc)
	 * @see com.cisco.qte.jdtn.component.AbstractStartableComponent#stopImpl()
	 */
	@Override
	protected void stopImpl() throws InterruptedException {
		// Nothing
	}
		
}
