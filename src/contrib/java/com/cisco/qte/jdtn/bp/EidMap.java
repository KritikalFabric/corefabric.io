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
package com.cisco.qte.jdtn.bp;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * Mappings from IPN: to DTN: EndPointIds.  Maintains two-way mappings
 * (equivalences) between a set of IPN: EndPointId and DTN: EndPointIds.
 */
public class EidMap extends AbstractStartableComponent {
	private static final Logger _logger =
		Logger.getLogger(EidMap.class.getCanonicalName());
	
	private static EidMap _instance = null;
	
	private HashMap<EndPointId, IpnEndpointId> _dtnToIpnMap =
		new HashMap<EndPointId, IpnEndpointId>();
	private HashMap<IpnEndpointId, EndPointId> _ipnToDtnMap =
		new HashMap<IpnEndpointId, EndPointId>();
	
	/**
	 * Get singleton Instance
	 * @return Singleton instance
	 */
	public static EidMap getInstance() {
		if (_instance == null) {
			_instance = new EidMap();
		}
		return _instance;
	}
	
	/**
	 * Protected access constructor
	 */
	protected EidMap() {
		super("EidMap");
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("EidMap()");
		}
	}
	
	/**
	 * Start this component
	 */
	@Override
	protected void startImpl() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("startImpl()");
		}
		addDefaultMapping();
	}

	/**
	 * Stop this component
	 */
	@Override
	protected void stopImpl() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("stopImpl()");
		}
		removeDefaultMapping();
	}

	/**
	 * Set to default state; clears all mappings
	 */
	public void setDefaults() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("setDefaults()");
		}
		_dtnToIpnMap.clear();
		_ipnToDtnMap.clear();
		addDefaultMapping();
	}
	
	/**
	 * Parse from config file.  It is assume that the parse is sitting on the
	 * &lt; EidMap &gt; element.  We parse all contained &lt; EidMapEntry &gt;
	 * sub-elements, adding a Dtn <-> Ipn EID Mapping for each.  We also
	 * parse the ending &lt; /EidMap &gt; tag.
	 * @param parser The config file parser
	 * @throws XmlPullParserException on general parsing errors
	 * @throws IOException On general I/O errors
	 * @throws JDtnException  on JDTN specific errors
	 */
	public void parse(XmlRDParser parser) 
	throws XmlRdParserException, IOException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("parse()");
		}
		// General structure of EidMap info:
		//   <EidMap>
		//     <EidMapEntry dtnEid='dtnEid' ipnEid='ipnEid />
		//     ...
		//   </EidMap>
		// Parse each <EidMapEntry>
		XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
		while (event == XmlRDParser.EventType.START_ELEMENT) {
			if (!parser.getElementTag().equals("EidMapEntry")) {
				throw new BPException("Expecting <EidMapEntry>");
			}
			// Get 'dtnEid' attribute
			String dtnEidStr = Utils.getStringAttribute(parser, "dtnEid");
			if (dtnEidStr == null) {
				throw new BPException("Missing attribute 'dtnEid'");
			}
			EndPointId dtnEid = EndPointId.createEndPointId(dtnEidStr);
			if (!dtnEid.getScheme().equals(EndPointId.DEFAULT_SCHEME)) {
				throw new BPException("First argument not 'dtn' Eid");
			}
			// Get 'ipnEid' attribute
			String ipnEidStr = Utils.getStringAttribute(parser, "ipnEid");
			if (ipnEidStr == null) {
				throw new BPException("Missing attribute 'ipnEid'");
			}
			EndPointId ipnEid = EndPointId.createEndPointId(ipnEidStr);
			if (!ipnEid.getScheme().equals(IpnEndpointId.SCHEME_NAME) ||
				!(ipnEid instanceof IpnEndpointId)) {
				throw new BPException("Second argument not 'ipn' Eid");
			}
			// Add the mapping
			addMapping(dtnEid, (IpnEndpointId)ipnEid);
			
			// Parse </EidMapEntry>
			event = Utils.nextNonTextEvent(parser);
			if (event != XmlRDParser.EventType.END_ELEMENT ||
				!parser.getElementTag().equals("EidMapEntry")) {
				throw new BPException("Expecting </EidMapEntry>");
			}
			event = Utils.nextNonTextEvent(parser);
		}
		
		// Parse </EidMap>
		if (event != XmlRDParser.EventType.END_ELEMENT ||
			!parser.getElementTag().equals("EidMap")) {
			throw new JDtnException("Expecting '</EidMap>'");
		}
	}
	
	/**
	 * Write EidMap to config file.  We only do this if there are entries
	 * in the map.
	 * @param pw PrintWrite to output to
	 */
	public void writeConfig(PrintWriter pw) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("writeConfig()");
		}
		if (EidMap.getInstance().size() > 0) {
			pw.println("    <EidMap>");
			for (EndPointId dtnEid : _dtnToIpnMap.keySet()) {
				IpnEndpointId ipnEid = _dtnToIpnMap.get(dtnEid);
				if (!isDefaultMapping(dtnEid, ipnEid)) {
					pw.println("      <EidMapEntry");
					pw.println("        dtnEid='" + dtnEid.getEndPointIdString() + "'");
					pw.println("        ipnEid='" + ipnEid.getEndPointIdString() + "'");
					pw.println("      />");
				}
			}
			pw.println("    </EidMap>");
		}
	}
	
	// Add an entry to map 'dtn:none' to 'ipn:0.0'
	private void addDefaultMapping() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("addDefaultMapping()");
		}
		try {
			addMapping(
					EndPointId.DEFAULT_ENDPOINT_ID_STRING, 
					IpnEndpointId.DEFAULT_IPNEID_STR);
		} catch (BPException e) {
			_logger.log(Level.SEVERE, "EidMap default mapping", e);
		}
	}
	
	// Remove entry mapping 'dtn:none' to 'ipn:0.0'
	private void removeDefaultMapping() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("removeDefaultMapping()");
		}
		try {
			removeMapping(EndPointId.DEFAULT_ENDPOINT_ID_STRING);
		} catch (BPException e) {
			_logger.log(Level.SEVERE, "EidMap default mapping", e);
		}
	}
	
	// Determine if given mapping is 'dtn:none' <=> 'ipn:0.0'
	private boolean isDefaultMapping(EndPointId dtnEid, IpnEndpointId ipnEid) {
		if (dtnEid.getEndPointIdString().equalsIgnoreCase(EndPointId.DEFAULT_ENDPOINT_ID_STRING) &&
			ipnEid.getEndPointIdString().equalsIgnoreCase(IpnEndpointId.DEFAULT_IPNEID_STR)) {
			return true;
		}
		return false;
	}
	
	/**
	 * Add a mapping between a 'dtn' Eid and an 'ipn' Eid
	 * @param dtnEidStr String containing the 'dtn' Eid
	 * @param ipnEidStr String containing the 'ipn' Eid
	 * @throws BPException if there is already a mapping for dtnEid <=> ipnEid,
	 * or if dtnEidStr is not a valid 'dtn' scheme EndPointId,
	 * or if ipnEidStr is not a valid 'ipn' scheme EndPointId.
	 */
	public void addMapping(String dtnEidStr, String ipnEidStr)
	throws BPException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("addMapping(<String>" +
					dtnEidStr + " <=> " +
					ipnEidStr + ")");
		}
		EndPointId dtnEid = EndPointId.createEndPointId(dtnEidStr);
		IpnEndpointId ipnEid = new IpnEndpointId(ipnEidStr);
		addMapping(dtnEid, ipnEid);
	}
	
	/**
	 * Add a mapping between a 'dtn' Eid and an 'ipn' Eid
	 * @param dtnEid The 'dtn' Eid
	 * @param ipnEid The 'ipn' Eid
	 * @throws BPException if there is already a mapping for dtnEid <=> ipnEid,
	 * or if dtnEid is not a 'dtn' scheme EndPointId.
	 */
	public synchronized void addMapping(EndPointId dtnEid, IpnEndpointId ipnEid) 
	throws BPException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("addMapping(" +
					dtnEid.getEndPointIdString() + " <=> " +
					ipnEid.getEndPointIdString() + ")");
		}
		if (!dtnEid.getScheme().equals(EndPointId.DEFAULT_SCHEME)) {
			throw new BPException("First argument is not a 'dtn' EndPointId");
		}
		if (_dtnToIpnMap.containsKey(dtnEid)) {
			if (_ipnToDtnMap.containsKey(ipnEid)) {
				// Full Mapping already exists; silently ignore
				return;
			}
			if (GeneralManagement.isDebugLogging()) {
				_logger.finer("addMapping(" +
						dtnEid.getEndPointIdString() + " <=> " +
						ipnEid.getEndPointIdString() + ") Entry already exists");
				_logger.finest(dump("", true));
			}
			throw new BPException("There is already a mapping for DTN EID: " +
					dtnEid.getEndPointIdString());
		}
		if (_ipnToDtnMap.containsKey(ipnEid)) {
			throw new BPException("There is already a mapping for IPN EID: " +
					ipnEid.getEndPointIdString());
		}
		_dtnToIpnMap.put(dtnEid, ipnEid);
		_ipnToDtnMap.put(ipnEid, dtnEid);
	}
	
	/**
	 * Remove a mapping between a 'dtn' Eid and an 'ipn' Eid
	 * @param dtnEidStr The 'dtn' Eid String
	 * @throws BPException If no mapping, or dtnEidStr poorly formatted
	 */
	public synchronized void removeMapping(String dtnEidStr)
	throws BPException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("removeMapping(" + dtnEidStr + ")");
		}
		EndPointId dtnEid = EndPointId.createEndPointId(dtnEidStr);
		IpnEndpointId ipnEid = getIpnEid(dtnEid);
		if (ipnEid == null) {
			throw new BPException("No mapping for " + dtnEid.getEndPointIdString());
		}
		removeMapping(dtnEid, ipnEid);
	}
	
	/**
	 * Remove a mapping between a 'dtn' Eid and an 'ipn' Eid
	 * @param dtnEid The 'dtn' Eid
	 * @param ipnEid The 'ipn' Eid
	 * @throws BPException if there is not a mapping for dtnEid <=> ipnEid,
	 * or if dtnEid is not a 'dtn' scheme EndPointId.
	 */
	public synchronized void removeMapping(EndPointId dtnEid, IpnEndpointId ipnEid) 
	throws BPException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("removeMapping(" +
					dtnEid.getEndPointIdString() + " <=> " +
					ipnEid.getEndPointIdString() + ")");
		}
		if (!_dtnToIpnMap.containsKey(dtnEid)) {
			throw new BPException("There is not a mapping for DTN EID: " +
					dtnEid.getEndPointIdString());
		}
		if (!_ipnToDtnMap.containsKey(ipnEid)) {
			throw new BPException("There is not a mapping for IPN EID: " +
					ipnEid.getEndPointIdString());
		}
		_dtnToIpnMap.remove(dtnEid);
		_ipnToDtnMap.remove(ipnEid);
	}
	
	/**
	 * Dump this object
	 * @param indent Amount of indentation
	 * @param detailed if want detailed dump
	 * @return String containing dump
	 */
	@Override
	public synchronized String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "EidMap\n");
		for (EndPointId dtnEid : _dtnToIpnMap.keySet()) {
			sb.append(
					indent +
					"  DtnEid=" + dtnEid.getEndPointIdString() +
					" <=> IpnEid=" + _dtnToIpnMap.get(dtnEid).getEndPointIdString() +
					"\n");
		}
		return sb.toString();
	}
	
	/**
	 * Get the IPN Eid mapped to given DTN Eid
	 * @param dtnEidStr Given DTN Eid String
	 * @return Mapped IPN Eid or null if none mapped
	 * @throws BPException if dtnEidStr is poorly formed
	 */
	public String getIpnEidStr(String dtnEidStr) 
	throws BPException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("getIpnEidStr(" + dtnEidStr + ")");
		}
		EndPointId dtnEid = EndPointId.createEndPointId(dtnEidStr);
		IpnEndpointId ipnEid = getIpnEid(dtnEid);
		if (ipnEid == null) {
			if (GeneralManagement.isDebugLogging()) {
				_logger.finer("getIpnEidStr(" + dtnEidStr + ") = null");
			}
			return null;
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("removeMapping(" + dtnEidStr + ") = " +
					ipnEid.getEndPointIdString());
		}
		return ipnEid.getEndPointIdString();
	}
	
	/**
	 * Get the IPN Eid mapped to given DTN Eid
	 * @param dtnEid Given DTN Eid
	 * @return Mapped IPN Eid or null if none mapped
	 */
	public synchronized IpnEndpointId getIpnEid(EndPointId dtnEid) {
		IpnEndpointId ipnEid = _dtnToIpnMap.get(dtnEid);
		if (ipnEid == null) {
			if (GeneralManagement.isDebugLogging()) {
				_logger.finer("getIpnEid(" + dtnEid.getEndPointIdString() + 
						") = null");
			}
			return null;
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("getIpnEid(" + dtnEid.getEndPointIdString() + 
					") = " + ipnEid.getEndPointIdString());
		}
		return ipnEid;
	}
	
	/**
	 * Get the DTN Eid mapped to given IPN Eid
	 * @param ipnEidStr Given IPN Eid String
	 * @return Mapped DTN Eid String or null if none mapped
	 * @throws BPException if ipnEidStr is poorly formed
	 */
	public String getDtnEidStr(String ipnEidStr)
	throws BPException {
		IpnEndpointId ipnEid = new IpnEndpointId(ipnEidStr);
		EndPointId dtnEid = getDtnEid(ipnEid);
		if (dtnEid == null) {
			if (GeneralManagement.isDebugLogging()) {
				_logger.finer("getDtnEidStr(" + ipnEidStr + ") = null");
			}
			return null;
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("getDtnEidStr(" + ipnEidStr + ") = " +
					dtnEid.getEndPointIdString());
		}
		return dtnEid.getEndPointIdString();
	}
	
	/**
	 * Get the DTN Eid mapped to given IPN Eid
	 * @param ipnEid Given IPN Eid
	 * @return Mapped DTN Eid or null if none mapped
	 */
	public synchronized EndPointId getDtnEid(IpnEndpointId ipnEid) {
		EndPointId dtnEid = _ipnToDtnMap.get(ipnEid);
		if (dtnEid == null) {
			if (GeneralManagement.isDebugLogging()) {
				_logger.finer("getDtnEid(" + ipnEid.getEndPointIdString() +
						") = null");
			}
			return null;
		}
			
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("getDtnEid(" + ipnEid.getEndPointIdString() + ") = " +
					dtnEid.getEndPointIdString());
		}
		return dtnEid;
	}
	
	/**
	 * Get the number of mappings
	 * @return Number of mappings
	 */
	public int size() {
		return _dtnToIpnMap.size();
	}
}
