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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.bp.EndPointId;
import com.cisco.qte.jdtn.component.AbstractEventProcessor;
import com.cisco.qte.jdtn.component.EventBroadcaster;
import com.cisco.qte.jdtn.component.IEvent;
import com.cisco.qte.jdtn.events.ManagementPropertyChangeEvent;
import com.cisco.qte.jdtn.ltp.LtpException;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * Collection of all Neighbors.
 */
public class NeighborsList extends AbstractEventProcessor
implements Iterable<Neighbor> {

	private static final NeighborsList _instance = new NeighborsList();
	private static final Logger _logger =
		Logger.getLogger(NeighborsList.class.getCanonicalName());
	private final List<Neighbor> _neighbors =
		new LinkedList<Neighbor>();
	private final HashMap<String, Neighbor> _nameMap =
		new HashMap<String, Neighbor>();

	/**
	 * Get singleton instance
	 */
	public static NeighborsList getInstance() {
		return _instance;
	}
	
	/**
	 * Constructor.  Private so enforces singleton pattern.
	 */
	private NeighborsList() {
		super("NeighborsList");
	}
	
	
	/**
	 * Start up of the NeighborsList
	 */
	@Override
	protected void startImpl() {
		EventBroadcaster.getInstance().registerEventProcessor(
				Management.class.getCanonicalName(), this);
	}
	
	/**
	 * Shutdown of the NeighborsList
	 */
	@Override
	protected void stopImpl() {
		LinkedList<Neighbor> tempNeighbors = new LinkedList<Neighbor>();
		for (Neighbor neighbor : tempNeighbors) {
			try {
				removeNeighbor(neighbor);
			} catch (LtpException e) {
				_logger.log(Level.SEVERE, "removeNeighbor() failed", e);
			}
			_nameMap.remove(neighbor.getName());
			neighbor.shutdown();
		}
		EventBroadcaster.getInstance().unregisterEventProcessor(
				Management.class.getCanonicalName(), this);
	}
	
	/**
	 * Parse configuration file, starting at the &lt; Neighbors &>t; element
	 * and ending at the &lt; /Neighbors &>t; tag.  It is assumed that the
	 * parser is currently sitting on the &lt; Neighbors &>t; tag.  Embedded
	 * inside the &lt; Neighbors &>t; is a series of &lt; Neighbor &>t; tags;
	 * each of which describes one Neighbor.  We parse each of these as well,
	 * and construct and add each such Neighbor to this. 
	 * @param parser Given Parser
	 * @throws JDtnException On JDtn specific parse errors
	 * @throws XMLStreamrException On general paser errors
	 * @throws IOException On I/O errors during parse
	 * @throws InterruptedException 
	 */
	public void parseNeighbors(XmlRDParser parser) 
	throws XmlRdParserException, IOException, JDtnException, InterruptedException {
		// We start out expecting a series of <Neighbor> elements
		XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
		while (event == XmlRDParser.EventType.START_ELEMENT) {
			if (!parser.getElementTag().equals("Neighbor")) {
				throw new JDtnException("Expecting '<Neighbor>'");
			}
			Neighbor neighbor = Neighbor.parseNeighbor(parser);
			addNeighbor(neighbor);
			
			event = Utils.nextNonTextEvent(parser);
		}
		// Now expecting </Neighbors>
		if (event != XmlRDParser.EventType.END_ELEMENT || 
			!parser.getElementTag().equals("Neighbors")) {
			throw new JDtnException("Expecting '</Neighbors>'");
		}
	}
	
	/**
	 * Write the list of Links & Neighbors to the given PrintWriter.  This consists of the
	 * &lt; Neighbors &gt; tag surrounding a series of &lt; Neighbor &gt; tags.
	 * @param pw Given PrintWriter
	 */
	public void writeConfig(PrintWriter pw) {
		pw.println("    <Neighbors>");
		for (Neighbor neighbor : _neighbors) {
			neighbor.writeConfig(pw);
		}
		pw.println("    </Neighbors>");
	}
	
	/**
	 * Add given Neighbor to this.  The given Neighbor must have a unique
	 * name and EngineId attribute, and this is enforced.
	 * @param neighbor
	 * @throws LtpException if uniqueness enforcement fails.
	 */
	public void addNeighbor(Neighbor neighbor) throws LtpException {
		if (neighbor.getName() == null) {
			throw new LtpException("Neighbor has null name");
		}
		synchronized (this) {
			if (_nameMap.get(neighbor.getName()) != null) {
				throw new LtpException("Already a Neighbor with name " + neighbor.getName());
			}
			_neighbors.add(neighbor);
			_nameMap.put(neighbor.getName(), neighbor);
		}
	}
	
	/**
	 * Remove given Neighbor from this.  It must have been previously installed.
	 * @param neighbor Given Neighbor
	 * @throws LtpException If given Neighbor has not been previously installed
	 * into this.
	 */
	public void removeNeighbor(Neighbor neighbor) throws LtpException {
		synchronized (this) {
			if (_nameMap.get(neighbor.getName()) == null) {
				throw new LtpException("Not a Neighbor with name " + neighbor.getName());
			}
			_neighbors.remove(neighbor);
			_nameMap.remove(neighbor.getName());
		}
		neighbor.removeNeighborImpl();
	}
	
	/**
	 * Remove all Neighbors from this
	 * @throws LtpException Shouldn't happen
	 */
	public void removeAllNeighbors() throws LtpException {
		while (!_neighbors.isEmpty()) {
			Neighbor neighbor = _neighbors.get(0);
			removeNeighbor(neighbor);
		}
	}
	
	/**
	 * Find the Neighbor with the given Name
	 * @param neighborName Given Name
	 * @return the Neighbor found or null if none found.
	 */
	public Neighbor findNeighborByName(String neighborName) {
		return _nameMap.get(neighborName);
	}
	
	/**
	 * Find a Neighbor with the given EndPointId.  Note: low performance.
	 * @return The Neighbor found or null if none found.
	 */
	public Neighbor findNeighborByEndpointId(EndPointId targetEndpointId) {
		synchronized (this) {
			for (Neighbor neighbor : _neighbors) {
				if (targetEndpointId.equals(neighbor.getEndPointIdStem())) {
					return neighbor;
				}
			}
		}
		return null;
	}
	
	/**
	 * Interface used in findNeighborByPredicate()
	 */
	public interface NeighborPredicate {
		/**
		 * Determine if given Neighbor is that desired
		 * @param neighbor Given Neighbor
		 * @param arg caller supplied argument
		 * @return True if the given Neighbor is that desired
		 */
		public boolean isNeighborAccepted(Neighbor neighbor, Object arg);
	}
	
	/**
	 * Search the NeighborsList for a Neighbor matching given predicate
	 * @param predicate Given predicate
	 * @param arg caller supplied argument
	 * @return First matching Neighbor or null if none.
	 */
	public Neighbor findNeighborByPredicate(
			NeighborPredicate predicate,
			Object arg) {
		for (Neighbor neighbor : this) {
			if (predicate.isNeighborAccepted(neighbor, arg)) {
				return neighbor;
			}
		}
		return null;
	}
	
	/**
	 * Interface used in findNeighborByPredicate()
	 */
	public interface NeighborPredicate2 {
		/**
		 * Determine if given Neighbor is that desired
		 * @param neighbor Given Neighbor
		 * @param arg1 caller supplied argument
		 * @param arg2 caller supplied argument
		 * @return True if the given Neighbor is that desired
		 */
		public boolean isNeighborAccepted(Neighbor neighbor, Object arg1, Object arg2);
	}
	
	/**
	 * Search the NeighborsList for a Neighbor matching given predicate
	 * @param predicate Given predicate
	 * @param arg1 caller supplied argument
	 * @param arg2 caller supplied argument
	 * @return First matching Neighbor or null if none.
	 */
	public Neighbor findNeighborByPredicate(
			NeighborPredicate2 predicate,
			Object arg1,
			Object arg2) {
		for (Neighbor neighbor : this) {
			if (predicate.isNeighborAccepted(neighbor, arg1, arg2)) {
				return neighbor;
			}
		}
		return null;
	}
	
	/**
	 * Find a Neighbor with the given Address.  Note: low performance.
	 * @param targetAddress The address to look for
	 * @return The Neighbor found or null if none found.
	 */
	public Neighbor findNeighborByAddress(Address targetAddress) {
		synchronized (this) {
			for (Neighbor neighbor : _neighbors) {
				if (neighbor.hasAddress(targetAddress)) {
					return neighbor;
				}
			}
			return null;
		}
	}
	
	/**
	 * Find a Neighbor reachable on given Link with the given Address.
	 * Note: low performance.
	 * @param link The given Link
	 * @param targetAddress The address to look for
	 * @return The Neighbor found or null if none found.
	 */
	public Neighbor findNeighborByAddressAndLink(Link link, Address targetAddress) {
		LinkAddress linkAddress = new LinkAddress(link, targetAddress);
		synchronized (this) {
			for (Neighbor neighbor : _neighbors) {
				if (neighbor.hasLinkAddress(linkAddress)) {
					return neighbor;
				}
			}
			return null;
		}
	}
	
	/**
	 * Return an Iteration on this NeighborsList, suitable for use in for loops.
	 * @return what I said
	 */
	@Override
	public Iterator<Neighbor> iterator() {
		return _neighbors.iterator();
	}

	@Override
	protected void processEventImpl(IEvent event) {
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
	 * Called when a property change occurs.  We are interested in Neighbor
	 * property changes so that we can adjust our tables accordingly.
	 */
	private void propertyChanged(Object source, String propertyName,
			Object oldValue, Object newValue) {
		
		if (propertyName.equals(Neighbor.NAME_PROPERTY)) {
			// Name has changed
			Neighbor neighbor = (Neighbor)source;
			String oldName = (String)oldValue;
			boolean exists = false;
			if (oldValue != null) {
				// Old name non-null
				exists = _nameMap.containsKey(oldName);
				if (exists) {
					// Old name exists in _nameMap
					_nameMap.remove(oldName);
				}
			}
			String newName = (String)newValue;
			if (exists && newValue != null) {
				// Old name exists in _nameMap && name being set to non-null value
				_nameMap.put(newName, neighbor);
			}
				
		}
	}

	/**
	 * Provide a short(er) String representation of this
	 */
	@Override
	public String toString() {
		return dump("", false);
	}
	
	/**
	 * Dump the state of This
	 * @param indent Indentation
	 * @param detailed Whether want verbose display
	 * @return String containing dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "NeighborsList\n");
		synchronized (this) {
			for (Neighbor neighbor : _neighbors) {
				sb.append(neighbor.dump(indent + "  ", detailed));
			}
		}
		return sb.toString();
	}
	
}
