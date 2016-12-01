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

import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * A default Route; a Route which matches anything
 */
public class DefaultRoute extends Route {

	/**
	 * Construct new DefaultRoute with given arguments
	 * @param name Name of Route
	 * @param nextHopLinkName Name of Next Hop Link
	 * @param nextHopNeighborName Name of Next Hop Neighbor
	 * @throws BPException on super constructor errors
	 */
	public DefaultRoute(
			String name, 
			String nextHopLinkName,
			String nextHopNeighborName) throws BPException {
		super(name, ".*", nextHopLinkName, nextHopNeighborName);
	}

	/**
	 * Override; unconditionally returns true
	 */
	@Override
	public boolean matches(EndPointId endPointId) {
		return true;
	}

	/**
	 * Create a DefaultRoute by parsing XML from config file.  Assumes parser is
	 * on &lt; DefaultRoute &gt; element.  We parse attributes for the
	 * DefaultRoute.  We parse thru and including the terminating
	 * &lt; /DefaultRoute &gt; element
	 * @param parser
	 * @return Newly constructed DefaultRoute
	 * @throws BPException on JDTN specific errors
	 * @throws XMLStreamException On general parse errors
	 * @throws IOException On I/O errors
	 */
	public static DefaultRoute parse(XmlRDParser parser)
	throws BPException, XmlRdParserException, IOException {
		// <DefaultRoute
		//   name='name'
		//   link='linkName'
		//   neighbor='neighborName'
		// />
		String name = parser.getAttributeValue("name");
		if (name == null || name.length() == 0) {
			throw new BPException("Missing 'name' attribute");
		}
		
		String linkName = parser.getAttributeValue("link");
		if (linkName == null || linkName.length() == 0) {
			throw new BPException("Missing 'link' attribute");
		}
		
		String neighborName = parser.getAttributeValue("neighbor");
		if (neighborName == null || neighborName.length() == 0) {
			throw new BPException("Missing 'neighbor' attribute");
		}
		
		DefaultRoute route = new DefaultRoute(name, linkName, neighborName);
		
		XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
		if (event != XmlRDParser.EventType.END_ELEMENT ||
			!parser.getElementTag().equals("DefaultRoute")) {
			throw new BPException("Expecting </DefaultRoute>");
		}
		return route;
	}
	
	/**
	 * Write config for DefaultRoute to given PrintWriter as a
	 * &lt; DefaultRoute &gt; element.
	 */
	@Override
	public void writeConfig(PrintWriter pw) {
		pw.println("      <DefaultRoute");
		pw.println("        name='" + getName() + "'");
		pw.println("        link='" + getNextHopLinkName() + "'");
		pw.println("        neighbor='" + getNextHopNeighborName() + "'");
		pw.println("      />");
	}

	@Override
	public boolean equals(Object thatObj) {
		if (!super.equals(thatObj)) {
			return false;
		}
		if (thatObj == null || !(thatObj instanceof DefaultRoute)) {
			return false;
		}
		return true;
	}
	
	@Override
	public int hashCode() {
		return super.hashCode();
	}
	
}
