/**
Copyright (c) 2011, Cisco Systems, Inc.
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

import java.io.File;
import java.io.Reader;

/**
 * Interface for a XML Recursive Descent Parser.  This exists because of the
 * dichotomy between XML Pull Parser implementations across platforms of
 * interest:
 * <ul>
 *   <li>Android - Only org.xmlpull.v1 is available.  The javax.xml.stream
 *                 namespace is not available and off limits for reassignment.
 *   <li>Other - Only javax.xml.stream.XmlStreamReader is available
 *              (There are open source implementations but licensing issues)
 * </ul>
 * <p>
 * This interface is pared down to include only what I need.
 * @author jminer
 *
 */
public interface XmlRDParser {

	/**
	 * Events that can be returned by next()
	 */
	public enum EventType {
		/** Start of Document */
		START_DOCUMENT,
		/** Start of Element */
		START_ELEMENT,
		/** End of Element */
		END_ELEMENT,
		/** End of Document */
		END_DOCUMENT
	}
	
	/**
	 * Set up parser to start parsing given String
	 * @param str String to parse
	 * @throws XmlRdParserException on parse errors
	 */
	public void setInput(String str) throws XmlRdParserException;
	
	/**
	 * Set up parser to start parsing given File
	 * @param file Given File to parse
	 * @throws XmlRdParserException on parse errors
	 */
	public void setInput(File file) throws XmlRdParserException;
	
	/**
	 * Set up parser to start parsing given Reader
	 * @param reader Reader to parse
	 * @throws XmlRdParserException on parser errors
	 */
	public void setInput(Reader reader) throws XmlRdParserException;
	
	/**
	 * Get next Event from the Parser
	 * @return One of the EventTypes
	 * @throws XmlRdParserException on parse errors
	 */
	public EventType next() throws XmlRdParserException;
	
	/**
	 * If the parser is currently at StartElement or EndElement, then this
	 * will return the local (non-prefix) portion of the element tag.
	 * @return Local (non-prefix) portion of the element tag.
	 * @throws XmlRdParserException on parse errors
	 */
	public String getElementTag() throws XmlRdParserException;
	
	/**
	 * If the parser is currently at StartElement, then this will return the
	 * value of the attribute corresponding to the given local (non-prefix)
	 * attribute name.
	 * @param attributeName Given local (non-prefix) attribute name
	 * @return Value of the attribute, or null if no such attribute exists in
	 * the element
	 * @throws XmlRdParserException on parse errors
	 */
	public String getAttributeValue(String attributeName) throws XmlRdParserException;
}
