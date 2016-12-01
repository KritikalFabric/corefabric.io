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

import com.cisco.qte.jdtn.ltp.IPAddress;
import com.cisco.qte.jdtn.ltp.LtpException;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * A Link and Address tuple
 */
public class LinkAddress {
	
	/** The Link member of the tuple */
	private Link _link = null;
	/** Property name for Link property */
	public static final String LINK_PROPERTY = "LinkAddress.Link";
	/** The Address member of the tuple */
	private Address _address = null;
	/** Property name for Address property */
	public static final String ADDRESS_PROPERTY = "LinkAddress.Address";
	
	/**
	 * Constructor; when Link is known but Address is not.  The Link property is
	 * set to the argument.  The Address member is set to null.
	 * @param link The value for the Link member of the tuple
	 */
	public LinkAddress(Link link) {
		setLink(link);
		setAddress(null);
	}
	
	/**
	 * Constructor; when Address is know but Link is not.  The Address property is
	 * set to the argument.  The Link member is set to null.
	 * @param address The value for the Address member of the tuple
	 */
	public LinkAddress(Address address) {
		setLink(null);
		setAddress(address);
	}
	
	/**
	 * Constructor; when both members of tuple are known.
	 * @param link The value for the Link member of the tuple
	 * @param address The value for the Address member of the tuple
	 */
	public LinkAddress(Link link, Address address) {
		setLink(link);
		setAddress(address);
	}
	
	/**
	 * Parse &lt; LinkAddress &gt; element of the config file.  It is assumed
	 * that the parse is currently sitting on the &lt; LinkAddress &gt; .  We
	 * examine the attributes of the element and construct a corresponding
	 * LinkAddress object.  We advance the parse, expecting the
	 * &lt; /LinkAddress &gt; end marker.
	 * @param parser The XML parser
	 * @return Newly constructed LinkAddress object
	 * @throws XMLStreamException on generic parse errors
	 * @throws IOException On I/O errors
	 * @throws JDtnException on specific parse errors
	 */
	public static LinkAddress parse(XmlRDParser parser) 
	throws XmlRdParserException, IOException, JDtnException {
		String linkName = Utils.getStringAttribute(parser, "link");
		if (linkName == null) {
			throw new LtpException("No 'link' attribute in 'LinkAddress' element");
		}
		Link link = LinksList.getInstance().findLinkByName(linkName);
		if (link == null) {
			throw new LtpException("'link' attribute doesn't identify a configured Link");
		}
		String addrType = Utils.getStringAttribute(parser, "addressType");
		if (addrType == null) {
			throw new LtpException("No 'addressType' attribute in 'LinkAddress' element");
		}
		String addrStr = Utils.getStringAttribute(parser, "address");
		if (addrStr == null) {
			throw new LtpException("no 'address' attribute in 'LinkAddress' element");
		}
		Address address;
		if (addrType.equals("ip")) {
			address = new IPAddress(addrStr);
		} else if (addrType.equals("binary")) {
			address = new Address(addrStr);
		} else {
			throw new LtpException("Unsupported 'addressType' argument: " + addrType);
		}
		LinkAddress linkAddress = new LinkAddress(link, address);
		XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
		if (event != XmlRDParser.EventType.END_ELEMENT ||
			!parser.getElementTag().equals("LinkAddress")) {
			throw new LtpException("'</LinkAddress>' expected");
		}
		return linkAddress;
	}
	
	/**
	 * Write configure for this LinkAddress to given PrintWriter; i.e., a single
	 * &lt; LinkAddress &gt; element.
	 * @param pw Given PrintWriter
	 */
	public void writeConfig(PrintWriter pw) {
		pw.println("          <LinkAddress");
		pw.println("            link='" + getLink().getName() + "'");
		pw.println("            address='" + getAddress().toParseableString() + "'");
		if (getAddress() instanceof IPAddress) {
			pw.println("            addressType='ip'");
		} else {
			pw.println("            addressType='binary'");
		}
		pw.println("          />");
	}
	
	/** The Link member of the tuple */
	public Link getLink() {
		return _link;
	}
	/** The Link member of the tuple */
	public void setLink(Link link) {
		Link priorLink = _link;
		this._link = link;
		Management.getInstance().fireManagementPropertyChangeEvent(this, LINK_PROPERTY, priorLink, _link);
	}
	
	/** The Address member of the tuple */
	public Address getAddress() {
		return _address;
	}
	/** The Address member of the tuple */
	public void setAddress(Address address) {
		Address priorAddress = _address;
		this._address = address;
		Management.getInstance().fireManagementPropertyChangeEvent(this, ADDRESS_PROPERTY, priorAddress, address);
	}
	
	/**
	 * Dump this LinkAddress object
	 * @param indent How much indentation for each line of output
	 * @param detailed Whether detailed dump required
	 * @return String containing the dump
	 */
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "LinkAddress\n");
		if (_link == null) {
			sb.append(indent + "  Link=null\n");
		} else {
			sb.append(_link.dump(indent + "  ", detailed));
		}
		if (_address == null) {
			sb.append(indent + "  Address=null\n");
		} else {
			sb.append(_address.dump(indent + "  ", detailed));
		}
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return 
		"LinkAddress{" + 
		"Link=" + getLink().getName() +
		"Address=" + getAddress().toParseableString() +
		"}";
	}
	
	@Override
	public boolean equals(Object otherObj) {
		if (otherObj == null || !(otherObj instanceof LinkAddress)) {
			return false;
		}
		LinkAddress other = (LinkAddress)otherObj;
		
		if (_link == null && _address == null) {
			// both components of tuple are null
			return true;
			
		} else if (_link == null) {
			// Link is null, Address is non-null
			return _address.equals(other.getAddress());
			
		} else if (_address == null) {
			// Link is non-null, Address is null
			return _link.equals(other.getLink());
			
		} else {
			// Neither are null
			return getLink().equals(other.getLink()) &&
				getAddress().equals(other.getAddress());
		}
	}
	
	@Override
	public int hashCode() {
		return getLink().hashCode() + getAddress().hashCode();
	}
	
}
