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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.component.EventBroadcaster;
import com.cisco.qte.jdtn.events.LinksEvent;
import com.cisco.qte.jdtn.ltp.LtpException;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * List of all Links
 */
public class LinksList extends AbstractStartableComponent
implements Iterable<Link> {

	private static final Logger _logger =
		Logger.getLogger(LinksList.class.getCanonicalName());
	
	public static final LinksList _instance = new LinksList();
	
	// List of Links
	private final List<Link> _links =
		new ArrayList<Link>();
	// For Link lookup by name
	private final HashMap<String, Link> _nameMap =
		new HashMap<String, Link>();
	
	/**
	 * Get singleton instance
	 * @return Singleton instance
	 */
	public static LinksList getInstance() {
		return _instance;
	}
	
	/** Restricted access constructor */
	private LinksList() {
		super("LinksList");
	}
	
	/**
	 * Startup 
	 */
	@Override
	protected void startImpl() {
		for (Link link : _links) {
			link.start();
		}
	}
	
	/**
	 * Shutdown - All Links stopped and removed
	 * @throws InterruptedException 
	 */
	@Override
	protected void stopImpl() throws InterruptedException {
		for (Link link : _links) {
			link.stop();
		}
		while (!_links.isEmpty()) {
			Link link = _links.get(0);
			removeLink(link);
		}
	}
	
	/**
	 * Get list of Links
	 * @return what I said
	 */
	public List<Link> getLinks() {
		return _links;
	}
	
	/**
	 * Parse Links configuration from given XmlPullParser.  It is assumed that
	 * the parser is sitting at the &lt; Links&gt;  element.  We parse through each
	 * embedded &lt; Link&gt;  element, creating the specified Links (and each
	 * Neighbor embedded in &lt; Link&gt; , through and including the last &lt; Link&gt;  and
	 * the terminating &lt; /Links&gt; .
	 * @param parser Given XmlPullParser.
	 * @throws IOException on I/O Errors in the parse
	 * @throws XMLStreamException on general parse errors
	 * @throws JDtnException On Link specific parsing errors
	 * @throws InterruptedException 
	 */
	public void parseLinks(XmlRDParser parser)
	throws XmlRdParserException, IOException, JDtnException, InterruptedException {
		
		// We start out expecting a series of <Link> elements
		XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
		while (event == XmlRDParser.EventType.START_ELEMENT) {
			if (!parser.getElementTag().equals("Link")) {
				throw new JDtnException("Expecting '<Link>'");
			}

			Link link = Link.parseLink(parser);
			Link existingLink = findLinkByName(link.getName());
			if (existingLink != null) {
				throw new JDtnException(
						"Link " + existingLink.getName() + 
						" already exists with name " +
						link.getName());
			}
			
			addLink(link);
			link.start();
			
			event = Utils.nextNonTextEvent(parser);
		}
		
		if (event != XmlRDParser.EventType.END_ELEMENT || 
				!parser.getElementTag().equals("Links")) {
			throw new JDtnException("Expecting '</Links>'");
		}
	}
	
	/**
	 * Write the list of Links to the given PrintWriter.  This consists the
	 * &lt; Links &gt; tag surrounding a series of &lt; Link &gt; tags.
	 * @param pw Given PrintWriter
	 */
	public void writeConfig(PrintWriter pw) {
		pw.println("    <Links>");
		for (Link link : _links) {
			link.writeConfig(pw);
		}
		pw.println("    </Links>");
	}
	
	/**
	 * Find a Link with the given Name
	 * @param name Given Name
	 * @return Link found or null if none
	 */
	public Link findLinkByName(String name) {
		return _nameMap.get(name);
	}
	
	/**
	 * Get an Iterator over all Links, suitable for a for each loop
	 * @return what I said
	 */
	@Override
	public Iterator<Link> iterator() {
		return _links.iterator();
	}
	
	/**
	 * Determine if LinksList contains given Link 
	 * @param link Given link
	 * @return True if LinksList contains given Link
	 */
	public boolean contains(Link link) {
		return _links.contains(link);
	}
	
	/**
	 * Add given Link to list of Links
	 * @param link Given Link
	 * @throws LtpException  if Link already exists
	 */
	public void addLink(Link link) throws LtpException {
		if (findLinkByName(link.getName()) != null) {
			throw new LtpException("Link with name " + link.getName() + " already exists");
		}
		_links.add(link);
		_nameMap.put(link.getName(), link);	
		notifyLinkAdded(link);
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("Link " + link + " added");
		}
	}
	
	/**
	 * Remove all Links
	 * @throws InterruptedException 
	 */
	public void removeAllLinks() throws InterruptedException {
		while (!_links.isEmpty()) {
			Link link = _links.get(0);
			removeLink(link);
		}
		_nameMap.clear();
	}
	
	/**
	 * Remove given Link from list of Links.  Also stops the Link, removes all
	 * of Link's Neighbors.
	 * @param link Given Link
	 * @throws InterruptedException 
	 */
	public void removeLink(Link link) throws InterruptedException {
		_links.remove(link);
		_nameMap.remove(link.getName());
		link.stop();
		link.linkRemoved();
		
		notifyLinkedRemoved(link);
		
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("Link " + link + " removed");
		}
	}
	
	/**
	 * Notify all LinksListListeners that a Link has been added
	 * @param link Added Link
	 */
	private void notifyLinkAdded(Link link) {
		try {
			EventBroadcaster.getInstance().broadcastEvent(
					LinksList.class.getCanonicalName(), 
					new LinksEvent(LinksEvent.LinksEventSubtype.LINK_ADDED_EVENT, link));
		} catch (Exception e) {
			_logger.log(Level.SEVERE, "notifyLinkAdded()", e);
		}
	}
	
	/**
	 * Notify all LinksListListeners that a Link has been removed
	 * @param link Removed Link
	 */
	private void notifyLinkedRemoved(Link link) {
		try {
			EventBroadcaster.getInstance().broadcastEvent(
					LinksList.class.getCanonicalName(), 
					new LinksEvent(LinksEvent.LinksEventSubtype.LINK_REMOVED_EVENT, link));
		} catch (Exception e) {
			_logger.log(Level.SEVERE, "notifyLinkAdded()", e);
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
		StringBuffer sb = new StringBuffer(indent + "LinksList\n");
		for (Link link : _links) {
			sb.append(link.dump(indent + "  ", detailed));
		}
		return sb.toString();
	}
}
