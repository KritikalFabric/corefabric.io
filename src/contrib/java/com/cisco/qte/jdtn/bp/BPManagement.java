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
import java.util.List;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.persistance.BundleDatabase;
import com.cisco.qte.jdtn.persistance.DBInterfaceJDBC;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * Bundle Protocol Management Interface.
 */
public class BPManagement extends AbstractStartableComponent {
	private static final Logger _logger =
		Logger.getLogger(BPManagement.class.getCanonicalName());
	
	private static BPManagement _instance = null;
	
	public static final String ALL_SUBCOMPONENTS_EID_PATTERN =  "(/[^/]+)*";
	
	/** Minimum value of BundleBlockFileThreshold property */
	public static final int MIN_BUNDLE_BLOCK_FILE_THRESHOLD = 1;
	/** Maximum value of BundleBlockFileThreshold property */
	public static final int MAX_BUNDLE_BLOCK_FILE_THRESHOLD = Integer.MAX_VALUE;
	/** Default value of BundleBlockFileThreshold property */
	public static final int DEFAULT_BUNDLE_BLOCK_FILE_THRESHOLD = 1000;

	/** Default value of BundleStatusReport List Length */
	public static final int DEFAULT_BUNDLE_STATUS_REPORT_LIST_LENGTH = 100;
	/** Default value of NetworkTimeSpread property (240 seconds; 4 minutes) */
	public static final long DEFAULT_NETWORK_TIME_SPREAD = 240;
	/** Minimum BP Service ID */
	public static final int MIN_BP_SERVICE_ID = 0;
	/** Default BP Service ID */
	public static final int DEFAULT_BP_SERVICE_ID = 1;
	/** Maximum BP Service ID */
	public static final int MAX_BP_SERVICE_ID = 255;
	
	/** Default LTP Block color for Bulk Class */
	public static final BundleColor DEFAULT_BULK_COLOR = BundleColor.RED;
	/** Default LTP Block color for Normal Class */
	public static final BundleColor DEFAULT_NORMAL_COLOR = BundleColor.RED;
	/** Default LTP Block cololr for Expedited Class */
	public static final BundleColor DEFAULT_EXPEDITED_COLOR = BundleColor.RED;
	
	/** CBHE Compatibility Default (true=ion 1.0 r203 compatibility) */
	public static final boolean DEFAULT_CBHE_COMPATIBILITY = true;
	
	/** Default max number of retained bytes; 4 Meg */
	public static final long DEFAULT_MAX_RETAINED_BYTES = 4L * 1024L * 1024L;
	
	/** 
	 * BundleBlockFileThreshold property - Bundle block body size threshold
	 * above which  block body stored in file rather than in memory
	 */
	protected int _bundleBlockFileThreshold = DEFAULT_BUNDLE_BLOCK_FILE_THRESHOLD;
	
	/** EndPointId stem for all traffic to this BP Node; default is dtn:none */
	protected EndPointId _endPointIdStem;
	
	/** BundleStatusReports List Length - Max number of items in BundleStatusReports List */
	protected int _bundleStatusReportsListLength = DEFAULT_BUNDLE_STATUS_REPORT_LIST_LENGTH;
	
	/** LTP Block Color for Bulk Class Forwarded Bundles */
	protected BundleColor _bulkBlockColor = DEFAULT_BULK_COLOR;
	
	/** LTP Block Color for Normal Class Forwarded Bundles */
	protected BundleColor _normalBlockColor = DEFAULT_NORMAL_COLOR;
	
	/** LTP Block Color for Expedited Class Forwarded Bundles */
	protected BundleColor _expeditedBlockColor = DEFAULT_EXPEDITED_COLOR;
	
	/** Radius of the locally reachable network in light-seconds */
	protected long _networkTimeSpread = DEFAULT_NETWORK_TIME_SPREAD;
	
	/** BP Statistics */
	protected BpStats _bpStats = new BpStats();
	
	/** LTP Service ID for the BP Layer */
	protected int _bpServiceId = DEFAULT_BP_SERVICE_ID;
	
	/** Protocol version we will use for outgoing Bundles (set to 4 so can interop
	    w/ ohio U code and w/ Billy's Stack */
	protected int _outboundProtocolVersion = PrimaryBundleBlock.DEFAULT_PROTOCOL_VERSION;
	
	/** Default EndPointId Scheme */
	public static final EidScheme DEFAULT_EID_SCHEME = EidScheme.DTN_EID_SCHEME;
	
	/** EndpointId Scheme globally in effect for this instance of BP */
	protected EidScheme _eidScheme = DEFAULT_EID_SCHEME;
	
	/** Default 'Hold Bundle if no route' */
	public static final boolean DEFAULT_HOLD_BUNDLE_IF_NO_ROUTE = false;
	
	/** Hold bundle if no route found to forward bundle */
	protected boolean _holdBundleIfNoRoute = DEFAULT_HOLD_BUNDLE_IF_NO_ROUTE;
	
	/**
	 * When using Compressed Bundle Header Encoding:
	 * <ul>
	 *   <li>false = Strict - according to draft-irtf-dtnrg-cbhe-03
	 *   <li>true = Compatibility with ION 1.0 r203
	 * </ul>
	 */
	protected boolean _cbheCompatibility = DEFAULT_CBHE_COMPATIBILITY;
	
	/** Max number of bytes which BP will retain at any instant */
	protected long _maxRetainedBytes = DEFAULT_MAX_RETAINED_BYTES;
	
	/** Default value for implementation of DBInterface -- 
	 * com.cisco.qte.jdtn.persistance.DBInterfaceJDBC */
	public static final String DB_INTERFACE_IMPL_DEFAULT =
		DBInterfaceJDBC.class.getCanonicalName();
	
	/** FQ class name of implementation of DBInterface */
	protected String _dbInterfaceClassName = DB_INTERFACE_IMPL_DEFAULT;
	
	// List of received BundleStatusReports
	private ArrayList<BundleStatusReport> _bundleStatusReportList =
		new ArrayList<BundleStatusReport>();
	
	/**
	 * Get singleton instance
	 * @return Singleton instance
	 */
	public static BPManagement getInstance() {
		if (_instance == null) {
			_instance = new BPManagement();
		}
		if (_instance.getEidScheme() == null) {
			_logger.severe("BPManagement._eidScheme == null");
		}
		return _instance;
	}
	
	/**
	 * Private constructor
	 */
	private BPManagement() {
		super("BPManagement");
	}
	
	/**
	 * Set all Management properties to default values
	 */
	public void setDefaults() {
		setBundleBlockFileThreshold(DEFAULT_BUNDLE_BLOCK_FILE_THRESHOLD);
		setEndPointIdStem(EndPointId.defaultEndPointId);
		setBundleStatusReportsListLength(DEFAULT_BUNDLE_STATUS_REPORT_LIST_LENGTH);
		setBulkBlockColor(BundleColor.RED);
		setNormalBlockColor(BundleColor.RED);
		setExpeditedBlockColor(BundleColor.RED);
		RouteTable.getInstance().clear();
		setNetworkTimeSpread(DEFAULT_NETWORK_TIME_SPREAD);
		setBpServiceId(DEFAULT_BP_SERVICE_ID);
		setOutboundProtocolVersion(PrimaryBundleBlock.DEFAULT_PROTOCOL_VERSION);
		setEidScheme(DEFAULT_EID_SCHEME);
		setCBHECompatibility(DEFAULT_CBHE_COMPATIBILITY);
		setMaxRetainedBytes(DEFAULT_MAX_RETAINED_BYTES);
		setHoldBundleIfNoRoute(DEFAULT_HOLD_BUNDLE_IF_NO_ROUTE);
		setDbInterfaceClassName(DB_INTERFACE_IMPL_DEFAULT);
		
		EidMap.getInstance().setDefaults();
		BundleDatabase.getInstance().setDefaults();
	}
	
	/**
	 * Parse the General section of the config file.  It is assumed that the
	 * parse is currently sitting on the &lt; BP &gt; element.  We parse the
	 * attributes of the &lt; BP &gt; element and the closing &lt; /BP &gt;,
	 * as well as embedded elements.
	 * @param parser Parser doing the parsing
	 * @throws XmlPullParserException On General parse errors
	 * @throws IOException On I/O errors during parse
	 * @throws JDtnException On GeneralConfig specific parse errors
	 */
	public void parse(XmlRDParser parser) 
	throws XmlRdParserException, IOException, JDtnException {
		// <BP
		//   endPointId='endPointIdStem'
		//   bundleBlockFileThreshold='n'
		//   bundleStatusReportListLength='n'
		//   bulkColor="red|green"
		//   normalColor="red|green"
		//   expeditedColor="red|green"
		//   networkTimeSpread='n'
		//   bpServiceId='n'
		//   outboundProtocolVersion='n'
		//   endpointIdScheme='dtn|ipn'
		//   reverseCBHE='true|false'
		//   maxRetainedBytes='n'
		//   holdBundleIfNoRoute='true/false'
		//   dbInterface='classname'
		//   <RouteTable ...>
		//     ...
		// >
		// </BP>
		String endPointStr = parser.getAttributeValue("endPointId");
		if (endPointStr == null || endPointStr.length() == 0) {
			endPointStr = EndPointId.DEFAULT_ENDPOINT_ID_STRING;
		}
		EndPointId eidStem = EndPointId.createEndPointId(endPointStr);
		setEndPointIdStem(eidStem);
		
		Integer intValue = 
			Utils.getIntegerAttribute(
					parser, 
					"bundleBlockFileThreshold", 
					MIN_BUNDLE_BLOCK_FILE_THRESHOLD, 
					MAX_BUNDLE_BLOCK_FILE_THRESHOLD);
		if (intValue != null) {
			setBundleBlockFileThreshold(intValue.intValue());
		}
		
		intValue =
			Utils.getIntegerAttribute(
					parser, 
					"bundleStatusReportListLength", 
					1, 
					Integer.MAX_VALUE);
		if (intValue != null) {
			setBundleStatusReportsListLength(intValue.intValue());
		}
		
		String colorStr = parser.getAttributeValue("bulkColor");
		if (colorStr != null && colorStr.length() > 0) {
			if (colorStr.equalsIgnoreCase("red")) {
				setBulkBlockColor(BundleColor.RED);
			} else if (colorStr.equalsIgnoreCase("green")) {
				setBulkBlockColor(BundleColor.GREEN);
			} else {
				throw new BPException("Invalid Bulk Block Color");
				
			}
		}
		
		colorStr = parser.getAttributeValue("normalColor");
		if (colorStr != null && colorStr.length() > 0) {
			if (colorStr.equalsIgnoreCase("red")) {
				setNormalBlockColor(BundleColor.RED);
			} else if (colorStr.equalsIgnoreCase("green")) {
				setNormalBlockColor(BundleColor.GREEN);
			} else {
				throw new BPException("Invalid Normal Block Color");
				
			}
		}
		
		colorStr = parser.getAttributeValue("expeditedColor");
		if (colorStr != null && colorStr.length() > 0) {
			if (colorStr.equalsIgnoreCase("red")) {
				setExpeditedBlockColor(BundleColor.RED);
			} else if (colorStr.equalsIgnoreCase("green")) {
				setExpeditedBlockColor(BundleColor.GREEN);
			} else {
				throw new BPException("Invalid Expedited Block Color");
				
			}
		}
		
		intValue = Utils.getIntegerAttribute(
				parser, 
				"networkTimeSpread", 
				0, 
				Integer.MAX_VALUE);
		if (intValue != null) {
			setNetworkTimeSpread(intValue.longValue());
		}
		
		intValue = Utils.getIntegerAttribute(
				parser, 
				"bpServiceId", 
				MIN_BP_SERVICE_ID, 
				MAX_BP_SERVICE_ID);
		if (intValue != null) {
			setBpServiceId(intValue.intValue());
		}
		
		intValue = Utils.getIntegerAttribute(
				parser, 
				"outboundProtocolVersion",
				PrimaryBundleBlock.MIN_PROTOCOL_VERSION,
				PrimaryBundleBlock.MAX_PROTOCOL_VERSION);
		if (intValue != null) {
			setOutboundProtocolVersion(intValue.intValue());
		}
		
		String schemeStr = parser.getAttributeValue("endPointIdScheme");
		if (schemeStr != null && schemeStr.length() > 0) {
			if (schemeStr.equalsIgnoreCase("dtn")) {
				setEidScheme(EidScheme.DTN_EID_SCHEME);
			} else if (schemeStr.equalsIgnoreCase("ipn")) {
				setEidScheme(EidScheme.IPN_EID_SCHEME);
			} else {
				throw new BPException("Expecting 'dtn' or 'ipn' as allowed values of 'endPointIdScheme' attribute");
			}
		}
		
		Boolean bValue = Utils.getBooleanAttribute(parser, "reverseCBHE");
		if (bValue != null) {
			setCBHECompatibility(bValue.booleanValue());
		}
		
		Long lValue = Utils.getLongAttribute(
				parser, 
				"maxRetainedBytes", 
				0L, 
				Long.MAX_VALUE);
		if (lValue != null) {
			setMaxRetainedBytes(lValue.longValue());
		}
		
		bValue = Utils.getBooleanAttribute(parser, "holdBundleIfNoRoute");
		if (bValue != null) {
			setHoldBundleIfNoRoute(bValue.booleanValue());
		}
		
		String dbIfName = Utils.getStringAttribute(parser, "dbInterface");
		if (dbIfName != null) {
			setDbInterfaceClassName(dbIfName);
		}
		
		// Parse BundleDatabase attributes
		BundleDatabase.getInstance().parse(parser);
		
		// Look for embedded elements: <RouteTable> and <EidMap>
		XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
		while (event == XmlRDParser.EventType.START_ELEMENT) {
			String tag = parser.getElementTag();
			if (tag.equals("RouteTable")) {
				RouteTable.getInstance().parse(parser);
			} else if (tag.equals("EidMap")) {
				EidMap.getInstance().parse(parser);
			} else {
				throw new BPException("Expecting <RouteTable>");
			}
			
			event = Utils.nextNonTextEvent(parser);
		}
		
		// Expecting </BP>
		if (event != XmlRDParser.EventType.END_ELEMENT || 
			!parser.getElementTag().equals("BP")) {
			throw new BPException("Expecting </BP>");
		}
	}
	
	/**
	 * Write BPConfig to the given PrintWriter in the form of a &lt; BP &gt; elemement
	 * and embedded elements.
	 * @param pw Given PrintWriter
	 */
	public void writeConfig(PrintWriter pw) {
		pw.println("  <BP");
		pw.println("    endPointId='" + getEndPointIdStem().getEndPointIdString() + "'");
		if (getBundleBlockFileThreshold() != DEFAULT_BUNDLE_BLOCK_FILE_THRESHOLD) {
			pw.println("    bundleBlockFileThreshold='" + getBundleBlockFileThreshold() + "'");
		}
		if (getBundleStatusReportsListLength() != DEFAULT_BUNDLE_STATUS_REPORT_LIST_LENGTH) {
			pw.println("    bundleStatusReportListLength='" + getBundleStatusReportsListLength() + "'");
		}
		if (getBulkBlockColor() != DEFAULT_BULK_COLOR) {
			pw.println("    bulkBlockColor='" + getBulkBlockColor() + "'");
		}
		if (getNormalBlockColor() != DEFAULT_NORMAL_COLOR) {
			pw.println("    normalBlockColor='" + getNormalBlockColor() + "'");
		}
		if (getExpeditedBlockColor() != DEFAULT_EXPEDITED_COLOR) {
			pw.println("    expeditedBlockColor='" + getExpeditedBlockColor() + "'");
		}
		if (getNetworkTimeSpread() != DEFAULT_NETWORK_TIME_SPREAD) {
			pw.println("    networkTimeSpread='" + getNetworkTimeSpread() + "'");
		}
		if (getBpServiceId() != DEFAULT_BP_SERVICE_ID) {
			pw.println("    bpServiceId='" + getBpServiceId() + "'");
		}
		if (getOutboundProtocolVersion() != PrimaryBundleBlock.DEFAULT_PROTOCOL_VERSION) {
			pw.println("    outboundProtocolVersion='" + getOutboundProtocolVersion() + "'");
		}
		if (getEidScheme() != DEFAULT_EID_SCHEME) {
			switch (getEidScheme()) {
			case DTN_EID_SCHEME:
				pw.println("    endPointIdScheme='dtn'");
				break;
			case IPN_EID_SCHEME:
				pw.println("    endPointIdScheme='ipn'");
				break;
			}
		}
		if (isCBHECompatibility() != DEFAULT_CBHE_COMPATIBILITY) {
			pw.println("    reverseCBHE='" + isCBHECompatibility() + "'");
		}
		if (getMaxRetainedBytes() != DEFAULT_MAX_RETAINED_BYTES) {
			pw.println("    maxRetainedBytes='" + getMaxRetainedBytes() + "'");
		}
		
		if (isHoldBundleIfNoRoute() != DEFAULT_HOLD_BUNDLE_IF_NO_ROUTE) {
			pw.println("    holdBundleIfNoRoute='" + isHoldBundleIfNoRoute() + "'");
		}
		
		if (!getDbInterfaceClassName().equals(DB_INTERFACE_IMPL_DEFAULT)) {
			pw.println("    dbInterface='" + getDbInterfaceClassName() + "'");
		}
		
		BundleDatabase.getInstance().writeConfig(pw);
		
		pw.println("  >");
		RouteTable.getInstance().writeConfig(pw);
		EidMap.getInstance().writeConfig(pw);
		pw.println("  </BP>");
	}
	
	/**
	 * Add a Route to the Routing Table
	 * @param name Name of Route
	 * @param destinationPattern R.E. Pattern to match against EndPointIds
	 * @param nextHopLinkName Name of Next Hop Link
	 * @param nextHopNeighborName Name of Next Hop Neighbor
	 * @return The Route added
	 * @throws BPException on errors
	 */
	public Route addRoute(
			String name,
			String destinationPattern,
			String nextHopLinkName,
			String nextHopNeighborName) throws BPException {
		return RouteTable.getInstance().addRoute(
				name, 
				destinationPattern, 
				nextHopLinkName, 
				nextHopNeighborName);
	}
	
	/**
	 * Find a Route with the given Name
	 * @param name Given Route Name
	 * @return Route with that Name, or null if none
	 */
	public Route findRoute(
			String name) {
		return RouteTable.getInstance().findRoute(name);
	}
	
	/**
	 * Remove Route with the given Name
	 * @param name Given Route Name
	 * @throws BPException If no Route exists with that name
	 */
	public void removeRoute(
			String name) throws BPException {
		RouteTable.getInstance().removeRoute(name);
	}
	
	/**
	 * Get number of Routes in the RouteTable
	 * @return What I said
	 */
	public int getRouteTableSize() {
		return RouteTable.getInstance().size();
	}
	
	/**
	 * Set the Default Route
	 * @param routeName Name of ROute
	 * @param linkName Name of Next Hop Link
	 * @param neighborName Name of Next Hop Neighbor
	 * @return The DefaultRoute created
	 * @throws BPException on errors from construction of Route
	 */
	public DefaultRoute setDefaultRoute(
			String routeName, 
			String linkName, 
			String neighborName) throws BPException {
		DefaultRoute route = new DefaultRoute(routeName, linkName, neighborName);
		RouteTable.getInstance().setDefaultRoute(route);
		return route;
	}
	
	/**
	 * Get the Default Route
	 * @return DefaultRoute or null if none installed
	 */
	public DefaultRoute getDefaultRoute() {
		return RouteTable.getInstance().getDefaultRoute();
	}

	/**
	 * Remove the Default Route
	 */
	public void removeDefaultRoute() {
		RouteTable.getInstance().setDefaultRoute(null);
	}
	
	/**
	 * Determine if currently configured policy allows us to accept custody
	 * of the given Bundle.
	 * @param bundle Given Bundle
	 * @return What I said
	 */
	public boolean canAcceptCustody(Bundle bundle) {
		Payload payload = bundle.getPayloadBundleBlock().getPayload();
		long length = payload.getLength();
		if (_bpStats.nRetainedBytes + length > getMaxRetainedBytes()) {
			return false;
		}
		return true;
	}
	
	/**
	 * Log the receipt of a Bundle Status Report for network management purposes.
	 * @param report The Bundle Status Report
	 */
	public void logBundleStatusReport(BundleStatusReport report) {
		synchronized (_bundleStatusReportList) {
			while (_bundleStatusReportList.size() > getBundleStatusReportsListLength()) {
				_bundleStatusReportList.remove(0);
			}
			_bundleStatusReportList.add(report);
		}
	}
	
	/**
	 * Get a copy of the list of received BundleStatusReports.
	 * @return what I said
	 */
	public List<BundleStatusReport> getBundleStatusReportList() {
		return new ArrayList<BundleStatusReport>(_bundleStatusReportList);
	}
	
	/**
	 * Clear the list of received BundleStatusReports
	 */
	public void clearBundleStatusReportsList() {
		_bundleStatusReportList.clear();
	}
	
	/**
	 * Startup BPManagement
	 */
	@Override
	protected void startImpl() {
		BundleDatabase.getInstance().start();
		BpApi.getInstance().start();
		BpLtpAdapter.getInstance().start();
		BPProtocolAgent.getInstance().start();
		RouteTable.getInstance().start();
		BundleHolder.getInstance().start();
		EidMap.getInstance().start();
	}
	
	/**
	 * Shutdown BPManagement
	 * @throws InterruptedException 
	 */
	@Override
	protected void stopImpl() throws InterruptedException {
		_logger.finer("stopImpl()");
		_bundleStatusReportList.clear();
		BundleHolder.getInstance().stop();
		RouteTable.getInstance().stop();
		BPProtocolAgent.getInstance().stop();
		BpLtpAdapter.getInstance().stop();
		BpApi.getInstance().stop();
		EidMap.getInstance().stop();
		BundleDatabase.getInstance().stop();
	}
	
	/**
	 * Dump the state of this object
	 * @param indent Amount of indentation
	 * @param detailed True if want verbose details
	 * @return String containing dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "BPManagement\n");
		sb.append(indent + "  BundleBlockFileThreshold=" + getBundleBlockFileThreshold() + "\n");
		sb.append(indent + "  BulkBlockColor=" + getBulkBlockColor() + "\n");
		sb.append(indent + "  NormalBlockColor=" + getNormalBlockColor() + "\n");
		sb.append(indent + "  ExpeditedBlockColor=" + getExpeditedBlockColor() + "\n");
		sb.append(indent + "  NetworkTimeSpread=" + getNetworkTimeSpread() + "\n");
		sb.append(indent + "  BpServiceId=" + getBpServiceId() + "\n");
		sb.append(indent + "  OutboundProtocolVersion=" + getOutboundProtocolVersion() + "\n");
		sb.append(indent + "  EndpointIdScheme=" + getEidScheme() + "\n");
		sb.append(indent + "  CBHECompatibility=" + isCBHECompatibility() + "\n");
		sb.append(indent + "  MaxRetainedBytes=" + getMaxRetainedBytes() + "\n");
		sb.append(indent + "  HoldBundleIfNoRoute=" + isHoldBundleIfNoRoute() + "\n");
		sb.append(getEndPointIdStem().dump(indent + "  ", detailed));
		sb.append(BpApi.getInstance().dump(indent + "  ", detailed));
		sb.append(RouteTable.getInstance().dump(indent + "  ", detailed));
		sb.append(BPFragmentation.getInstance().dump(indent + "  ", detailed));
		sb.append(EidMap.getInstance().dump(indent + "  ", detailed));
		if (detailed) {
			sb.append(indent + "  Received Bundle Status Reports\n");
			for (BundleStatusReport report : _bundleStatusReportList) {
				sb.append(report.shortDump(indent + "    ", detailed));
			}
			sb.append(BPProtocolAgent.getInstance().dump(indent + "  ", detailed));
			sb.append(BundleDatabase.getInstance().dump(indent + "  ", detailed));
		}
		sb.append(BundleHolder.getInstance().dump(indent + "  ", detailed));
		sb.append(_bpStats.dump(indent + "  ", detailed));
		return sb.toString();
	}

	/**
	 * Request initiation of a process to restore pending bundles from the
	 * Bundles Database.
	 */
	public void requestBundleRestore() {
		BPProtocolAgent.getInstance().requestBundleRestore();
	}
	
	/**
	 * Dump the Bundle lists
	 * @param indent Amount of indentation
	 * @param detailed True if want verbose details
	 * @return String containing dump
	 */
	public String dumpBundles(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "BPManagement\n");
		sb.append(BPProtocolAgent.getInstance().dump(indent + "  ", detailed));
		return sb.toString();
	}
	
	/** 
	 * BundleBlockFileThreshold property - Bundle block body size threshold
	 * above which  block body stored in file rather than in memory
	 */
	public int getBundleBlockFileThreshold() {
		return _bundleBlockFileThreshold;
	}

	/** 
	 * BundleBlockFileThreshold property - Bundle block body size threshold
	 * above which  block body stored in file rather than in memory
	 */
	public void setBundleBlockFileThreshold(int aBundleBlockFileThreshold) {
		_bundleBlockFileThreshold = aBundleBlockFileThreshold;
	}

	/** EndPointId stem for all traffice to this BP Node */
	public EndPointId getEndPointIdStem() {
		return _endPointIdStem;
	}

	/** EndPointId stem for all traffice to this BP Node */
	public void setEndPointIdStem(EndPointId endPointIdStem) {
		BPManagement.getInstance()._endPointIdStem = endPointIdStem;
	}

	/** BundleStatusReports List Length - Max number of items in BundleStatusReports List */
	public int getBundleStatusReportsListLength() {
		return _bundleStatusReportsListLength;
	}

	/** 
	 * BundleStatusReports List Length - Max number of items in BundleStatusReports List
	 * Setting this property has the side-effect of clearing the list.
	 */
	public void setBundleStatusReportsListLength(
			int bundleStatusReportsListLength) {
		_bundleStatusReportsListLength = bundleStatusReportsListLength;
		_bundleStatusReportList.clear();
		_bundleStatusReportList = 
			new ArrayList<BundleStatusReport>(bundleStatusReportsListLength);
	}

	/** LTP Block Color for Bulk Class Forwarded Bundles */
	public BundleColor getBulkBlockColor() {
		return _bulkBlockColor;
	}

	/** LTP Block Color for Bulk Class Forwarded Bundles */
	public void setBulkBlockColor(BundleColor bulkBlockColor) {
		BPManagement.getInstance()._bulkBlockColor = bulkBlockColor;
	}

	/** LTP Block Color for Normal Class Forwarded Bundles */
	public BundleColor getNormalBlockColor() {
		return _normalBlockColor;
	}

	/** LTP Block Color for Normal Class Forwarded Bundles */
	public void setNormalBlockColor(BundleColor normalBlockColor) {
		BPManagement.getInstance()._normalBlockColor = normalBlockColor;
	}

	/** LTP Block Color for Expedited Class Forwarded Bundles */
	public BundleColor getExpeditedBlockColor() {
		return _expeditedBlockColor;
	}

	/** LTP Block Color for Expedited Class Forwarded Bundles */
	public void setExpeditedBlockColor(BundleColor expeditedBlockColor) {
		BPManagement.getInstance()._expeditedBlockColor = expeditedBlockColor;
	}

	/**
	 * Get the appropriate BundleColor for a forwarded Bundle of the given
	 * Bundle Class of Service/Priority
	 * @param cos Given Class of Server / Priority
	 * @return BundleColor
	 */
	public BundleColor getBlockColor(PrimaryBundleBlock.BPClassOfServicePriority cos) {
		switch (cos)  {
		case BULK:
			return _bulkBlockColor;
		case NORMAL:
			return _normalBlockColor;
		case EXPEDITED:
			// Fall thru
		default:
			return _expeditedBlockColor;
		}
	}
	
	/** BP Statistics */
	public BpStats getBpStats() {
		return _bpStats;
	}

	/** Radius of the locally reachable network in light-seconds */
	public long getNetworkTimeSpread() {
		return _networkTimeSpread;
	}

	/** Radius of the locally reachable network in light-seconds */
	public void setNetworkTimeSpread(long networkTimeSpread) {
		BPManagement.getInstance()._networkTimeSpread = networkTimeSpread;
	}

	/** LTP Service ID for the BP Layer */
	public int getBpServiceId() {
		return _bpServiceId;
	}

	/** LTP Service ID for the BP Layer */
	public void setBpServiceId(int bpServiceId) {
		BPManagement.getInstance()._bpServiceId = bpServiceId;
	}

	/** Protocol version we will use for outgoing Bundles (set to 4 so can interop
    w/ ohio U code and w/ Billy's Stack */
	public int getOutboundProtocolVersion() {
		return _outboundProtocolVersion;
	}

	/** Protocol version we will use for outgoing Bundles (set to 4 so can interop
    w/ ohio U code and w/ Billy's Stack */
	public void setOutboundProtocolVersion(int outboundProtocolVersion) {
		BPManagement.getInstance()._outboundProtocolVersion = outboundProtocolVersion;
	}

	/** EndpointId Scheme globally in effect for this instance of BP */
	public EidScheme getEidScheme() {
		return _eidScheme;
	}

	/** EndpointId Scheme globally in effect for this instance of BP */
	public void setEidScheme(EidScheme eidScheme) {
		_eidScheme = eidScheme;
	}

	/**
	 * When using Compressed Bundle Header Encoding:
	 * <ul>
	 *   <li>false = Strict - according to draft-irtf-dtnrg-cbhe-03
	 *   <li>true = Compatibility with ION 1.0 r203
	 * </ul>
	 */
	public boolean isCBHECompatibility() {
		return _cbheCompatibility;
	}

	/**
	 * When using Compressed Bundle Header Encoding:
	 * <ul>
	 *   <li>false = Strict - according to draft-irtf-dtnrg-cbhe-03
	 *   <li>true = Compatibility with ION 1.0 r203
	 * </ul>
	 */
	public void setCBHECompatibility(boolean cbheCompatibility) {
		_cbheCompatibility = cbheCompatibility;
	}

	/** Max number of bytes which BP will retain */
	public long getMaxRetainedBytes() {
		return _maxRetainedBytes;
	}

	/** Max number of bytes which BP will retain */
	public void setMaxRetainedBytes(long maxRetainedBytes) {
		_maxRetainedBytes = maxRetainedBytes;
	}

	/** Hold bundle if no route found to forward bundle */
	public boolean isHoldBundleIfNoRoute() {
		return _holdBundleIfNoRoute;
	}

	/** Hold bundle if no route found to forward bundle */
	public void setHoldBundleIfNoRoute(boolean holdBundleIfNoRoute) {
		_holdBundleIfNoRoute = holdBundleIfNoRoute;
	}

	/** FQ class name of implementation of DBInterface */
	public String getDbInterfaceClassName() {
		return _dbInterfaceClassName;
	}

	/** FQ class name of implementation of DBInterface */
	public void setDbInterfaceClassName(String dbInterfaceClassName) {
		this._dbInterfaceClassName = dbInterfaceClassName;
	}
	
}
