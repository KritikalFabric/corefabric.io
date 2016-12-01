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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * A Collection of Routes dictating mappings from EndPointId to next hop
 * information.  Used to decide if and how to forward Bundles.
 */
public class RouteTable extends AbstractStartableComponent
implements Router, Iterable<Route> {
	// Singleton instance
	private static RouteTable _instance = null;
	// List of Routes
	protected ArrayList<Route> _routes =
		new ArrayList<Route>();
	// Mapping of Route names to Routes
	protected HashMap<String, Route> _nameMap =
		new HashMap<String, Route>();
	// The DefaultRoute
	protected DefaultRoute _defaultRoute = null;
	
	/**
	 * Get singleton instance
	 * @return Singleton instance
	 */
	public static RouteTable getInstance() {
		if (_instance == null) {
			_instance = new RouteTable();
		}
		return _instance;
	}
	
	/**
	 * Restricted access constructor
	 */
	private RouteTable() {
		super("RouteTable");
	}
	
	@Override
	protected void startImpl() {
		RouterManager.getInstance().registerRouter(
				this, RouterManager.RouterPriority.LAST);
	}
	
	@Override
	protected void stopImpl() {
		RouterManager.getInstance().unregisterRouter(this);
	}
	
	/**
	 * Parse XML Configuration from the given XmlPullParser.  Assumes parser
	 * is sitting on &lt; RouteTable &gt; element.  We parse its attributes
	 * and enclosed &lt; Route &gt; elements up to an including the closing
	 * &lt; /RouteTable &gt;
	 * @param parser Given XmlPullParser
	 * @throws BPException on RouteTable specific parse errors
	 * @throws IOException on I/O errors reading XML Configuration
	 * @throws XMLStreamException on general parse errors
	 */
	public synchronized void parse(XmlRDParser parser)
	throws BPException, XmlRdParserException, IOException {
		// <RouteTable>
		//   <DefaultRoute ...>
		//   <Route ...>
		//   ...
		// </RouteTable>
		XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
		while (event == XmlRDParser.EventType.START_ELEMENT) {
			if (parser.getElementTag().equals("Route")) {
				Route route = Route.parse(parser);
				addRoute(route);
				
			} else if (parser.getElementTag().equals("DefaultRoute")) {
				DefaultRoute route = DefaultRoute.parse(parser);
				setDefaultRoute(route);
				
			} else {
				throw new BPException("Expecting <Route> or <DefaultRoute>");
			}
			
			event = Utils.nextNonTextEvent(parser);
		}
		if (event != XmlRDParser.EventType.END_ELEMENT ||
			!parser.getElementTag().equals("RouteTable")) {
			throw new BPException("Expecting </RouteTable>");
		}
	}
	
	/**
	 * Write RouteTable config out to given PrintWriter.  We write the
	 * enclosing &lt; RouteTable &gt; element and all enclosed
	 * &lt; Route &gt; elements
	 * @param pw Given PrintWriter
	 */
	public synchronized void writeConfig(PrintWriter pw) {
		pw.println("    <RouteTable>");
		if (getDefaultRoute() != null) {
			getDefaultRoute().writeConfig(pw);
		}
		for (Route route : _routes) {
			route.writeConfig(pw);
		}
		pw.println("    </RouteTable>");
	}
	
	/**
	 * Add given Route to RouteTable
	 * @param route GivenRoute
	 * @return the Route
	 * @throws BPException If Route with given Route's name already exists in
	 * RouteTable.
	 */
	public synchronized Route addRoute(Route route)
	throws BPException {
		if (_nameMap.get(route.getName()) != null) {
			throw new BPException("Route named " + route.getName() + " already exists");
		}
		_routes.add(route);
		_nameMap.put(route.getName(), route);
		return route;
	}
	
	/**
	 * Add a Route to RouteTable
	 * @param routeName Name of Route
	 * @param destinationPattern Destination Pattern for Route
	 * @param nextHopLinkName Name of Next Hop Link
	 * @param nextHopNeighborName Name of Next Hop Neighbor
	 * @return The Route added
	 * @throws BPException If nextHopLinkName or nextHopNeighborName don't name
	 * valid Link or Neighbor, or if routeName already exists in table.
	 */
	public synchronized Route addRoute(
			String routeName,
			String destinationPattern,
			String nextHopLinkName,
			String nextHopNeighborName)
	throws BPException {
		Route route =
			new Route(
					routeName, 
					destinationPattern, 
					nextHopLinkName, 
					nextHopNeighborName);
		return addRoute(route);
	}
	
	/**
	 * Remove given Route from the RouteTable
	 * @param route Route to delete
	 */
	public synchronized void removeRoute(Route route) {
		if (_nameMap.get(route.getName()) != null) {
			_routes.remove(route);
			_nameMap.remove(route.getName());
			
		} else if (route.equals(getDefaultRoute())) {
			setDefaultRoute(null);
		}
	}
	
	
	/**
	 * Remove Route with given Name from RouteTable
	 * @param routeName Name of Route to delete
	 */
	public synchronized void removeRoute(String routeName) {
		Route route = _nameMap.get(routeName);
		if (route != null) {
			removeRoute(route);
		}
	}
	
	/**
	 * Find a Route by name
	 * @param name Name
	 * @return Route in RouteTable with that name or null if none
	 */
	public synchronized Route findRoute(String name) {
		return _nameMap.get(name);
	}
	
	/**
	 * Find a Route to forward given Bundle
	 * @param bundle Bundle to route
	 * @return Route found or null if none
	 */
	@Override
	public Route findRoute(Bundle bundle) {
		return findMatchingRoute(bundle.getPrimaryBundleBlock().getDestinationEndPointId());
	}
	
	/**
	 * Find first Route in RouteTable whose destinationPattern matches
	 * given EndPointId.
	 * @param endPointId Given EndPointId
	 * @return Matching Route or null if none
	 */
	public synchronized Route findMatchingRoute(EndPointId endPointId) {
		for (Route route : _routes) {
			if (route.matches(endPointId)) {
				return route;
			}
		}
		return getDefaultRoute();
	}
	
	/**
	 * Get number of entries in RouteTable
	 * @return What I said
	 */
	public synchronized int size() {
		return _routes.size();
	}
	
	/**
	 * Remove all entries from RouteTable
	 */
	public synchronized void clear() {
		_routes.clear();
		_nameMap.clear();
	}
	
	/**
	 * Get an Iterator over the Routes in RouteTable, good for for each loops.
	 * @return What I said
	 */
	@Override
	public Iterator<Route> iterator() {
		return _routes.iterator();
	}
	
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "RouteTable\n");
		for (Route route : _routes) {
			sb.append(route.dump(indent + "  ", detailed));
		}
		if (getDefaultRoute() != null) {
			sb.append(indent + "  DefaultRoute\n");
			sb.append(getDefaultRoute().dump(indent + "    ", detailed));
		}
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return dump("", false);
	}

	/** The DefaultRoute; may be null */
	public DefaultRoute getDefaultRoute() {
		return _defaultRoute;
	}

	/** The DefaultRoute; may be null to remove it */
	public void setDefaultRoute(DefaultRoute defaultRoute) {
		this._defaultRoute = defaultRoute;
	}

}
