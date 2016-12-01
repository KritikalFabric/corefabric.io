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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.logging.Logger;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * An implementation of XmlRDParser that uses an underlying
 * javax.xml.stream.XMLStreamReader as a delegate.
 */
public class XmlRdParserStreamReader implements XmlRDParser {
	private static final Logger _logger =
		Logger.getLogger(XmlRdParserStreamReader.class.getCanonicalName());
	private static final XMLInputFactory _factory = XMLInputFactory.newFactory();

	private XMLStreamReader _reader = null;
	
	public XmlRdParserStreamReader() {
		// Nothing
	}
	
	@Override
	public void setInput(String str) throws XmlRdParserException {
		_logger.finer("setInput(" + str + ")");
		StringReader stringReader = new StringReader(str);
		try {
			_reader = _factory.createXMLStreamReader(stringReader);
		} catch (XMLStreamException e) {
			throw new XmlRdParserException(e);
		}
	}

	@Override
	public void setInput(File file) throws XmlRdParserException {
		_logger.finer("setInput(" + file.getAbsolutePath() + ")");
		try {
			FileReader fileReader = new FileReader(file);
			_reader = _factory.createXMLStreamReader(fileReader);
		} catch (FileNotFoundException e) {
			throw new XmlRdParserException(e);
		} catch (XMLStreamException e) {
			throw new XmlRdParserException(e);
		}
	}
	
	@Override
	public void setInput(Reader reader) throws XmlRdParserException {
		_logger.finer("setInput(Reader)");
		try {
			_reader = _factory.createXMLStreamReader(reader);
		} catch (XMLStreamException e) {
			throw new XmlRdParserException(e);
		}
	}

	@Override
	public EventType next() throws XmlRdParserException {
		try {
			for (;;) {
				int event = _reader.next();
				switch (event) {
				case XMLStreamConstants.START_DOCUMENT:
					_logger.fine("START_DOCUMENT");
					return EventType.START_DOCUMENT;
					
				case XMLStreamConstants.START_ELEMENT:
					_logger.fine("START_ELEMENT " + getElementTag());
					return EventType.START_ELEMENT;
					
				case XMLStreamConstants.END_DOCUMENT:
					_logger.fine("END_DOCUMENT");
					return EventType.END_DOCUMENT;
					
				case XMLStreamConstants.END_ELEMENT:
					_logger.fine("END_ELEMENT " + getElementTag());
					return EventType.END_ELEMENT;
					
				default:
					_logger.finer("Ignoring XMLStreamReader event " + event);
				}
			}
		} catch (XMLStreamException e) {
			throw new XmlRdParserException(e);
		}
	}

	@Override
	public String getAttributeValue(String attributeName)
			throws XmlRdParserException {
		try {
			String attrValue = _reader.getAttributeValue(null, attributeName);
			if (attrValue == null || attrValue.length() == 0) {
				return null;
			}
			_logger.fine("ATTRIBUTE " + attributeName + " = " + attrValue);
			return attrValue;
		} catch (Exception e) {
			throw new XmlRdParserException(e);
		}
	}

	@Override
	public String getElementTag() throws XmlRdParserException {
		try {
			return _reader.getLocalName();
		} catch (IllegalStateException e) {
			throw new XmlRdParserException(e);
		}
	}

}
