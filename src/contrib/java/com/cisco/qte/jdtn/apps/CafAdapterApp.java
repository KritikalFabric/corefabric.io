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
package com.cisco.qte.jdtn.apps;

import java.io.StringReader;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.caf.xmcp.Client;
import com.cisco.caf.xmcp.Service;
import com.cisco.caf.xmcp.SubscriptionMatchListener;
import com.cisco.caf.xmcp.Attributes.Address;
import com.cisco.caf.xmcp.Attributes.TransportProtocol;
import com.cisco.qte.jdtn.bp.BPException;
import com.cisco.qte.jdtn.bp.BPManagement;
import com.cisco.qte.jdtn.bp.DefaultRoute;
import com.cisco.qte.jdtn.bp.EndPointId;
import com.cisco.qte.jdtn.bp.Route;
import com.cisco.qte.jdtn.bp.RouteTable;
import com.cisco.qte.jdtn.component.EventBroadcaster;
import com.cisco.qte.jdtn.component.IEvent;
import com.cisco.qte.jdtn.component.IEventProcessor;
import com.cisco.qte.jdtn.events.CafNeighborAddedEvent;
import com.cisco.qte.jdtn.events.CafNeighborRemovedEvent;
import com.cisco.qte.jdtn.events.CafPublishEvent;
import com.cisco.qte.jdtn.events.ManagementPropertyChangeEvent;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Link;
import com.cisco.qte.jdtn.general.LinkAddress;
import com.cisco.qte.jdtn.general.LinksList;
import com.cisco.qte.jdtn.general.Management;
import com.cisco.qte.jdtn.general.NeighborsList;
import com.cisco.qte.jdtn.general.Platform;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.ltp.EngineId;
import com.cisco.qte.jdtn.ltp.IPAddress;
import com.cisco.qte.jdtn.ltp.LtpException;
import com.cisco.qte.jdtn.tcpcl.TcpClLink;
import com.cisco.qte.jdtn.tcpcl.TcpClManagement;
import com.cisco.qte.jdtn.tcpcl.TcpClNeighbor;
import com.cisco.qte.jdtn.general.XmlRDParser;

/**
 * JDTN App - adapter for CAF / XMCP 2.0
 * This is the JDTN side of the CAF Client.  The guts of the CAF Client are
 * embodied in external library CAF.jar.  This class is the interface to the
 * DAF Client.  The purpose of this class is two-fold:
 * <ul>
 *   <li>Publish the existence of this node as a DTN Service, so that other
 *   CAF clients can discover its presence.
 *   <li>Get notifications about the presence of other nodes also advertising
 *   as a DTN Service, and manipulate the JDTN Routes and Neighbor tables
 *   accordingly.
 * </ul>
 */
public class CafAdapterApp extends AbstractApp
implements SubscriptionMatchListener, IEventProcessor {
	private static final Logger _logger =
		Logger.getLogger(CafAdapterApp.class.getCanonicalName());
	
	private static final int CAF_MAJOR_VERSION = 2;
	private static final int CAF_MINOR_VERSION = 0;

	public static final String APP_NAME = "CafAdapter";
	public static final String APP_PATTERN =
		"CafAdapter" + BPManagement.ALL_SUBCOMPONENTS_EID_PATTERN;
	public static final String XML_TAG = "DtnNode";
	
	private static final int DTN_CAF_SERVICE_ID = 635;
	private static final int DTN_CAF_SUBSERVICE_ID = 1;
	
	private static final int DTN_CAF_SERVICE_VERSION = 1;
	
	public static final int QUEUE_DEPTH = 10;

	public static final String IP_ADDRESS_ATTR_NAME = "ipAddress";
	public static final String EID_ATTR_NAME = "EndPointId";
	
	private String _linkName = null;
	private String _clientLabel = null;
	private String _routerIpAddress = null;
	private int _routerPort = 0;
	private String _userName = null;
	private String _password = null;
	private TcpClLink _link = null;
	private Hashtable<UUID, TcpClNeighbor> _neighborMap =
		new Hashtable<UUID, TcpClNeighbor>();
	private Client _cafClient = null;
	private Service _cafService = null;
	private Service _cafSubscription = null;
	private int _cafServiceVersion = DTN_CAF_SERVICE_VERSION;
	
	/**
	 * Constructor
	 * args - Array of Object containing arguments to this App.  Consists of
	 * the following:
	 * <ul>
	 *   <li>Link name
	 *   <li>Client Label
	 *   <li>IP Address of SAF Enabled Router
	 *   <li>TCP Port Number of SAF Enabled Router
	 *   <li>Username to login to SAF
	 *   <li>Password to login to SAF
	 * </ul>
	 * @throws BPException on errors
	 */
	public CafAdapterApp(String[] args) throws BPException {
		super(APP_NAME, APP_PATTERN, QUEUE_DEPTH, args);
		// Check arguments, store them as members
		if (!getName().equals(APP_NAME)) {
			abortAppConstruction();
			throw new BPException("App Name in command line must be " + APP_NAME);
		}
		if (args.length != 6) {
			abortAppConstruction();
			throw new BPException("Unexpected number of arguments: " + args.length +
					"; expecting 6 arguments");
		}
		_linkName = args[0];
		_clientLabel = args[1];
		Link link = LinksList.getInstance().findLinkByName(_linkName);
		if (link == null) {
			throw new BPException("No Link named " + _linkName);
		}
		if (!(link instanceof TcpClLink)) {
			_logger.warning("Link named " + _linkName + " is not a TcpCl Link");
			_link = null;
		} else {
			_link = (TcpClLink)link;
		}
		_routerIpAddress = args[2].toString();
		try {
			_routerPort = Integer.parseInt(args[3].toString());
		} catch (NumberFormatException e) {
			abortAppConstruction();
			throw new BPException("Invalid Integer Port: " + args[3].toString());
		}
		_userName = args[4].toString();
		_password = args[5].toString();
		
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("CafAdapterApp argument summary:");
			_logger.fine(" linkName=" + _linkName +
					" Client Label=" + _clientLabel +
					" RouterIpAddress=" + _routerIpAddress +
					" RouterPort=" + _routerPort +
					" UserName=" + _userName +
					" Password=" + _password);
		}
	}
	
	/**
	 * Called when the Application is started up.  We perform all initialization
	 * and registration with the CAF Client.
	 * @see com.cisco.qte.jdtn.apps.AbstractApp#startupImpl()
	 */
	@Override
	public void startupImpl() {
		// Create event broadcast group for broadcasting our own events
		EventBroadcaster.getInstance().createBroadcastGroup(getClass().getCanonicalName());
		
		// Set Caf Client debugging property according to our global debug
		// debug logging property; and register for notification of changes
		// to our global debug logging property
		Client.setDebug(GeneralManagement.isDebugLogging());
		EventBroadcaster.getInstance().registerEventProcessor(
				Management.class.getCanonicalName(), this);
		
		// Register with the CAF Client
		_cafClient = Client.getInstance(
				CAF_MAJOR_VERSION, 
				CAF_MINOR_VERSION, 
				_routerIpAddress, 
				_routerPort, 
				_userName, 
				_password, 
				_clientLabel,
				"", 
				0, 
				null, 
				null);
		
		// Create and configure the CAF Service
		_cafService = _cafClient.createService();
		_cafService.setServiceID(DTN_CAF_SERVICE_ID);
		_cafService.setSubServiceID(DTN_CAF_SUBSERVICE_ID);
		_cafService.generateRandomServiceInstance();
		
		// Publish to CAF
		publish();
		
		// Register notification for births and deaths of any other nodes 
		// advertising the DTN Service
		_cafSubscription = _cafClient.createService();
		_cafSubscription.setServiceID(DTN_CAF_SERVICE_ID);
		_cafSubscription.setSubServiceID(DTN_CAF_SUBSERVICE_ID);
		_cafSubscription.setServiceInstance(
				new UUID(
						-1L, -1L));
		_cafSubscription.addSubscriptionMatchListener(this);
		_cafSubscription.subscribe();
	}

	/**
	 * Publish to CAF a description of the Service we provide
	 */
	private void publish() {
		if (_link == null) {
			_logger.warning("Caf cannot publish on link " + _linkName);
			return;
		}
		UUID instance = _cafService.getServiceInstance();
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("instanceId=" + instance);
		}
		IPAddress ipAddress = _link.getIpAddress();
		Address address  = new Address(ipAddress.getAddressBytes(), _link.getTcpPort());
		_cafService.setTransportAddress(address);
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("Transport Address=" + _cafService.getTransportAddress());
		}
		_cafService.setTransportProtocol(TransportProtocol.UDP);
		StringBuilder serviceData = new StringBuilder(
			"<" + XML_TAG +
			" " + IP_ADDRESS_ATTR_NAME + "='" + _link.getIpAddress().toParseableString() + "'" +
			" " + EID_ATTR_NAME + "='" + BPManagement.getInstance().getEndPointIdStem().getEndPointIdString() + "'" +
			" BandwidthThrottle='" + GeneralManagement.getInstance().getMySegmentRateLimit() + "'" +
			" Router='" + (BPManagement.getInstance().getDefaultRoute() != null ? "false" : "true") + "'" +
			" >");
		// Notify listeners we're about to publish our service data; give
		// them a chance to add to service data
		try {
			EventBroadcaster.getInstance().broadcastEvent(
					getClass().getCanonicalName(),
					new CafPublishEvent(serviceData));
		} catch (Exception e) {
			_logger.log(Level.SEVERE, "publish()", e);
		}
		serviceData.append("</" + XML_TAG + ">");
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("serviceDataLen=" + serviceData.length() +
					": serviceData=" + serviceData);
		}
		_cafService.setServiceData(serviceData.toString().getBytes());
		_cafService.publish();
	}

	/**
	 * Something has changed, necessitating that we need to re-publish our
	 * service data.  We increment our Service version and re-publish
	 * our service data.
	 */
	public void publishUpdate() {
		synchronized (this) {
			_cafServiceVersion++;
			publish();
		}
	}
	
	/**
	 * Gives access to the subscription to get info about it, like the list
	 * of discovered neighbors.
	 * @return CAF Subscription
	 */
	public Service getSubscription() {
		return _cafSubscription;
	}
	
	/**
	 * Called when the Application is being shut down.  We shutdown the
	 * CAF Client.
	 * @see com.cisco.qte.jdtn.apps.AbstractApp#shutdownImpl()
	 */
	@Override
	public void shutdownImpl() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("Shutting down CAF Client");
		}

		EventBroadcaster.getInstance().unregisterEventProcessor(
				Management.class.getCanonicalName(), this);
		_cafClient.shutdown();
		
		EventBroadcaster.getInstance().deleteBroadcastGroup(getClass().getCanonicalName());
	}

	/**
	 * Called periodically by a Thread in our super, to implement repetitive actions an
	 * App might want to perform.  We don't have any, so we merely sleep for
	 * a long, long time.
	 * @see com.cisco.qte.jdtn.apps.AbstractApp#threadImpl()
	 */
	@Override
	public void threadImpl() throws Exception {
		// We don't use this, but don't let this thread be a cpu hog
		Thread.sleep(60000);
	}

	/**
	 * Notification from CAF Client that a new DTN Service has been discovered.
	 * We add a new Route and Neighbor for the new DTN Service.
	 * @see com.cisco.caf.xmcp.SubscriptionMatchListener#serviceFound(com.cisco.caf.xmcp.Service, com.cisco.caf.xmcp.Service)
	 */
	@Override
	public void serviceFound(Service subscription, Service newService) {
		// Make sure the notification is describing a DTN Service.  This
		// isn't really necessary, as the CAF Client should do the necessary
		// filtering.  But we do it anyway just to be absolutely sure.
		if (newService.getServiceID() != DTN_CAF_SERVICE_ID ||
			newService.getSubServiceID() != DTN_CAF_SUBSERVICE_ID) {
			_logger.info("Ignoring non-germane serviceFound for " +
					newService.getServiceID() + ":" +
					newService.getSubServiceID());
			return;
		}
		try {
			// Extract the instance from the Service.  This is a unique id
			// among all JDTN services.
			UUID key = newService.getServiceInstance();
			if (GeneralManagement.isDebugLogging()) {
				_logger.finer("New Service: instance=" + key);
			}
			
			// ServiceData contains an XML document describing the new Neighbor.
			// Parse the XML and extract new Neighbor info
			String xmlStr = new String(newService.getServiceData());
			if (GeneralManagement.isDebugLogging()) {
				_logger.finer("             Service Data=" + xmlStr);
			}
			StringReader reader = new StringReader(xmlStr);
			XmlRDParser parser = Platform.getXmlStreamReader(reader);
			XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
			if (event != XmlRDParser.EventType.START_ELEMENT) {
				_logger.severe("Expecting start tag: " + xmlStr);
				return;
			}
			if (!parser.getElementTag().equals("DtnNode")) {
				_logger.severe("Expecting <DtnNode> element tag in: " + xmlStr);
				return;
			}
			String ipAddrStr = Utils.getStringAttribute(parser, "ipAddress");
			if (ipAddrStr == null) {
				_logger.severe("Expecting attribute 'ipAddress: "
						+ xmlStr);
				return;
			}
			String eidStr = Utils.getStringAttribute(parser, "EndPointId");
			if (eidStr == null) {
				_logger.severe("Expecting attribute 'EndPointId: "
						+ xmlStr);
				return;
			}
			Double bwThrottle = Utils.getDoubleAttribute(parser,
					"BandwidthThrottle", 0.0d, Double.MAX_VALUE);
			if (bwThrottle == null) {
				_logger.severe(
						"Expecting attribute 'BandwidthThrottle': "
								+ xmlStr);
				return;
			}
			Boolean isRouter = Utils.getBooleanAttribute(parser, "Router");
			if (isRouter == null) {
				_logger.severe("Expecting attribute 'Router': "
						+ xmlStr);
				return;
			}
			String engineIdString = Utils.getStringAttribute(parser, "engineId");
			EngineId engineId;
			if (engineIdString != null) {
				try {
					engineId = new EngineId(engineIdString);
				} catch (LtpException e) {
					_logger.severe("Invalid EngineId in service advertisement: " +
							engineIdString + "; ignoring");
					return;
				}
			} else {
				// For compatibility w/ prior version, which didn't send EngineId
				engineId = new EngineId(ipAddrStr);
			}
			
			// Make adjustments to our Routes and Neighbors to accomodate the
			// new Neighbor.
			addOrModifyNeighbor(
					ipAddrStr, eidStr, bwThrottle, 
					isRouter, key, engineId,
					new String(newService.getServiceData()));
			
		} catch (Exception e) {
			_logger.log(Level.SEVERE, "Parsing serviceData", e);
		}
		
	}

	/**
	 * Notification from CAF Client that a previous existing DTN Service has
	 * gone down.  We remove corresponding Route and Neighbor.
	 * @see com.cisco.caf.xmcp.SubscriptionMatchListener#serviceLost(com.cisco.caf.xmcp.Service, com.cisco.caf.xmcp.Service)
	 */
	@Override
	public void serviceLost(Service subscription, Service dyingService) {
		// Again, this shouldn't be necessary but we're doing it anyway.
		if (dyingService.getServiceID() != DTN_CAF_SERVICE_ID
				|| dyingService.getSubServiceID() != DTN_CAF_SUBSERVICE_ID) {
			_logger.info("Ignoring non-germane serviceFound for "
					+ dyingService.getServiceID() + ":"
					+ dyingService.getSubServiceID());
			return;
		}
		
		// Extract the instance id of the dying neighbor.  Then look it up in
		// our own tables to get a reference to the affected Neighbor.
		UUID key = dyingService.getServiceInstance();
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("Dead Service: instance=" + key);
		}
		TcpClNeighbor neighbor = _neighborMap.get(key);
		if (neighbor == null) {
			if (GeneralManagement.isDebugLogging()) {
				_logger.finer("No known Neighbor for instance " + key);
			}
			return;
		}
		
		// Remove Route to Neighbor and the Neighbor itself.
		try {
			removeNeighbor(neighbor);
		} catch (BPException e) {
			_logger.log(Level.SEVERE, "Removing Neighbor", e);
		}
		
	}
	
	/**
	 * Add/Modify a Neighbor and Route to describe the newly discovered Neighbor
	 * @param ipAddrStr IP Address of Neighbor
	 * @param eidStr EndpointID of Neighbor
	 * @param bwThrottle Segment transmit rate limit parameter for Neighbor
	 * @param isRouter Not used at present
	 * @param inst CAF Instance Number
	 * @param engineId The EngineId of the Neighbor
	 * @param serviceData Service Data published by the Neighbor
	 * @throws JDtnException on errors
	 */
	private void addOrModifyNeighbor(
			String ipAddrStr, 
			String eidStr, 
			Double bwThrottle, 
			Boolean isRouter,
			UUID inst,
			EngineId engineId,
			String serviceData)
	throws JDtnException {
		if (_link == null) {
			_logger.warning("Caf cannot add neighbor on link " + _linkName);
			return;
		}
		IPAddress ipAddress = null;
		try {
			ipAddress = new IPAddress(ipAddrStr);
		} catch (UnknownHostException e) {
			throw new BPException(e);
		}
		
		EndPointId eid = EndPointId.createEndPointId(eidStr);
		if (eid.equals(BPManagement.getInstance().getEndPointIdStem())) {
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Added or Modified Neighbor is self; ignoring");
			}
			return;
		}
		
		// Add or modify Neighbor
		TcpClNeighbor neighbor =
			TcpClManagement.getInstance().findNeighbor(ipAddress);
		if (neighbor != null) {
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Modified Neighbor:" +
						" ipAddress=" + ipAddrStr +
						" eid=" + eidStr +
						" bwThrottle=" + bwThrottle +
						" isRouter=" + isRouter);
			}
			
			// Existing Neighbor with that EngineId
			neighbor.setEndPointIdStem(eid);
			
			if (!neighbor.hasAddress(ipAddress)) {
				// We don't have a record of that IP Address
				LinkAddress linkAddress = new LinkAddress(_link, ipAddress);
				neighbor.addLinkAddress(linkAddress);
			}
				
			neighbor.setTemporary(true);
			
		} else {
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("New Neighbor:" +
						" ipAddress=" + ipAddrStr +
						" eid=" + eidStr +
						" bwThrottle=" + bwThrottle +
						" isRouter=" + isRouter);
			}
			
			// New Neighbor
			neighbor = TcpClManagement.getInstance().addNeighbor(eid.getHostNodeName(), eid);
			neighbor.addLinkAddress(new LinkAddress(_link, ipAddress));
			neighbor.setTemporary(true);			
			neighbor.setEndPointIdStem(eid);
			neighbor.start();
		}
		
		// Remove any pre-existing Route for this Neighbor
		Route route = RouteTable.getInstance().findMatchingRoute(eid);
		if (route != null && !(route instanceof DefaultRoute)) {
			RouteTable.getInstance().removeRoute(route);
		}
		
		// Add New Route
		String pattern = eid.getEndPointIdString() + BPManagement.ALL_SUBCOMPONENTS_EID_PATTERN;
		route = new Route(eid.getHostNodeName(), pattern, _link.getName(), neighbor.getName());
		route.setTemporary(true);
		RouteTable.getInstance().addRoute(route);
		
		// Put or replace an entry into the map
		_neighborMap.put(inst, neighbor);
		
		// Notify listeners
		try {
			EventBroadcaster.getInstance().broadcastEvent(
					getClass().getCanonicalName(),
					new CafNeighborAddedEvent(neighbor, serviceData));
		} catch (Exception e) {
			throw new JDtnException(e);
		}
	}

	/**
	 * Remove Neighbor and Route
	 * @param neighbor Neighbor to Remove
	 * @throws BPException on Errors
	 */
	private void removeNeighbor(TcpClNeighbor neighbor) throws BPException {
		_logger.fine("Dead Neighbor:" +
				" eid=" + neighbor.getEndPointIdStem().getEndPointIdString());
		Route route = RouteTable.getInstance().findMatchingRoute(neighbor.getEndPointIdStem());
		if (route != null &&
			!(route instanceof DefaultRoute)) {
			RouteTable.getInstance().removeRoute(route);
		}
		try {
			NeighborsList.getInstance().removeNeighbor(neighbor);
		} catch (LtpException e) {
			throw new BPException("NeighborsList.removeNeighbor() failed", e);
		}
		
		// Notify listeners
		try {
			EventBroadcaster.getInstance().broadcastEvent(
					getClass().getCanonicalName(),
					new CafNeighborRemovedEvent(neighbor));
		} catch (Exception e) {
			throw new BPException(e);
		}
	}
	
	/**
	 * Called from EventBroadcaster when an event on Management is
	 * broadcast.  If the event is a ManagementPropertyChangeEvent,
	 * and the property being changed is the GeneralManagement debug logging
	 * property, then propagate the new value to the CAF client.
	 * @param event Event broadcast
	 */
	public void processEvent(IEvent event) {
		if (event instanceof ManagementPropertyChangeEvent) {
			ManagementPropertyChangeEvent mEvent =
				(ManagementPropertyChangeEvent)event;
			if (mEvent.getPropertyName().equals(GeneralManagement.DEBUG_LOGGING_PROPERTY)) {
				Client.setDebug(GeneralManagement.isDebugLogging());
			}
		}
	}
	
}
