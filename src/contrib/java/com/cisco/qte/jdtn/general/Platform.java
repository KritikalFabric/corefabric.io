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

import java.io.Reader;

import com.cisco.qte.jdtn.persistance.DBInterface;
import com.cisco.qte.jdtn.bp.BPManagement;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;


/**
 * Platform specific code
 * In order to reconcile differences between Android java and java on
 * other platforms, we have to jump through hoops.  Depending on the
 * platform, we choose one implementation or another and dynamically load
 * and instantiate it.
 */
public class Platform {

	/**
	 * Get an instance of XmlRDParser, a recursive descent XML parser.
	 * @return Instance of XmlRDParser
	 * @throws JDtnException encapsulates exceptions from j
	 * avax.xml.stream.XMLInputFactory
	 */
	@SuppressWarnings("unchecked")
	public static XmlRDParser getXmlStreamReader(Reader reader)
	throws JDtnException {
		try {
			
			String platform = System.getProperty("java.runtime.name");
			Class<XmlRDParser> parserClass = null;
			if (platform == null || !platform.contains("Android")) {
				// Non-Android platform.  We will use a parser based on the
				// javax.xml.stream package.
				parserClass = (Class<XmlRDParser>)Class.forName(
						"com.cisco.qte.jdtn.general.XmlRdParserStreamReader");
			} else {
				// Android platform.  We will use a parser based on the
				// org.xmlpull.v1 available on the Android platform.
				parserClass = (Class<XmlRDParser>)Class.forName(
						"com.cisco.qte.jdtng1.misc.XmlRdParserPullParser");
			}
			XmlRDParser parser = parserClass.newInstance();
			parser.setInput(reader);
			return parser;
			
		} catch (ClassNotFoundException e) {
			throw new JDtnException(e);
		} catch (InstantiationException e) {
			throw new JDtnException(e);
		} catch (IllegalAccessException e) {
			throw new JDtnException(e);
		} catch (XmlRdParserException e) {
			throw new JDtnException(e);
		}
	}
	
	/**
	 * Get an instance of DBInterface, an interface to a SQL database.
	 * @return Instance of DBInterface specific to the platform.
	 * @throws JDtnException on errors
	 */
	@SuppressWarnings("unchecked")
	public static DBInterface getDBInterface() throws JDtnException {
		try {
			// XXX Should we make this a singleton?
			String dbClassName = 
				BPManagement.getInstance().getDbInterfaceClassName();
			
			Class<DBInterface> dbifClass = null;
				dbifClass = (Class<DBInterface>)Class.forName(
						dbClassName);
			DBInterface dbif = dbifClass.newInstance();
			return dbif;
			
		} catch (ClassNotFoundException e) {
			throw new JDtnException(e);
		} catch (InstantiationException e) {
			throw new JDtnException(e);
		} catch (IllegalAccessException e) {
			throw new JDtnException(e);
		}
	}
}
