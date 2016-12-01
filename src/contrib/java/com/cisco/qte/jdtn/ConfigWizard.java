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
package com.cisco.qte.jdtn;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;

import com.cisco.qte.jdtn.bp.BPManagement;
import com.cisco.qte.jdtn.bp.EndPointId;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.LinkAddress;
import com.cisco.qte.jdtn.general.Management;
import com.cisco.qte.jdtn.ltp.IPAddress;
import com.cisco.qte.jdtn.ltp.LtpManagement;
import com.cisco.qte.jdtn.tcpcl.TcpClLink;
import com.cisco.qte.jdtn.tcpcl.TcpClManagement;
import com.cisco.qte.jdtn.tcpcl.TcpClNeighbor;

/**
 * Generates a JDTN configuration file from a few simple questions.  This will
 * be good for run-of-the-mill configurations involving:
 * <ul>
 *   <li>Dtn: URI Scheme only
 *   <li>IPV4 only
 *   <li>A single network interface to the outside world
 *   <li>Either Router or non-Router configurations
 *   <li>Simple routing configuration in which there is a single Router and
 *       all other nodes register with that Router and set up default route
 *       to that Router.
 *   <li>All apps installed
 * </ul>
  *
 */
public class ConfigWizard {

	private static String _nodeName;
	private static NetworkInterface _networkInterface;
	private static IPAddress _ipAddress;
	private static IPAddress _defaultRouterIPAddress;
	private static String _defaultRouterNodeName;
	
	private static boolean _isSafConfig = true;
	private static String _safClientLabel = "java-test";
	private static String _safFwderAddr = "172.18.153.34";
	private static String _safFwderPort = "5000";
	private static String _safUsername = "java";
	private static String _safPassword = "javajavajava";
	
	/**
	 * Main program; Generates a configuration by conducting a text-based
	 * dialog with the user.
	 * @param args not used
	 */
	public static void main(String[] args) {
		
		try {
			Management.getInstance().start();
			Management.getInstance().setDefaults();
			
			BufferedReader reader =
				new BufferedReader(new InputStreamReader(System.in));
			
			chooseNodeName(reader);

			chooseNetworkInterface(reader);
			chooseInetAddress(reader);
			
			chooseSafOrHubSpoke(reader);
			if (_isSafConfig) {
				chooseSafArguments(reader);
			}
			chooseRouterOrNode(reader);
			
			generateConfig();
			
			Management.getInstance().saveConfig();
			Management.getInstance().stop();
			
			System.out.println("Done");
			System.exit(0);
			
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}		
	}

	/**
	 * Alternative config generator for a non-Router in case we can't run main
	 * (as is the case, for example, with Android).  Note that this method has
	 * gradually evolved into a configuration generator for Android.
	 * @param nodeName Name of this node.
	 * @param ifName Interface name
	 * @param ipAddress IP Address
	 * @param defaultRouterNodeName Name of the default router
	 * @param defaultRouterIPAddressStr IP Address of the default router
	 * @param bundleDir Directory for bundle storage
	 * @param mediaDir Directory for application's media storage
	 * @param bundleFileThreshold - Inbound Bundles with payloads above this will
	 * have payloads stored to file
	 * @param blockLengthFileThreshold - Inbound Blocks above this will have paylaods
	 * stored to file.
	 * @param segmentLengthFileThreshold - Inbound Segments above this will have
	 * payloads stored to file.
	 * @param segmentRateLimit Segment Rate Limit which this node advertises to
	 * others
	 * @param burstSize Segment Burst Size which this node advertises to others.
	 * @param isForSaf Configure SAF
	 * @param safForwarderAddress - SAF Forwarder Address
	 * @param safForwarderPort - SAF Forwarder TCP Port
	 * @param safUsername - SAF Username credential
	 * @param safPassword - SAF Password credential
	 * @param safClientLabel - SAF Client Label
	 * @param dbInterfaceClassname - FQ class name of implementation of DBInterface
	 * @throws SocketException on errors
	 * @throws JDtnException on errors
	 * @throws UnknownHostException on errors
	 * @throws InterruptedException 
	 */
	public static void generateConfig(
			String nodeName, 
			String ifName, 
			String ipAddress,
			String defaultRouterNodeName,
			String defaultRouterIPAddressStr,
			File bundleDir,
			File mediaDir,
			int bundleFileThreshold,
			int blockLengthFileThreshold,
			int segmentLengthFileThreshold,
			double segmentRateLimit,
			long burstSize,
			boolean isForSaf,
			String safForwarderAddress,
			String safForwarderPort,
			String safUsername,
			String safPassword,
			String safClientLabel,
			String dbInterfaceClassname)
	throws SocketException, JDtnException, UnknownHostException, InterruptedException {
		// Save args to fields which we use later in generateConfig()
		_nodeName = nodeName;
		_isSafConfig = isForSaf;
		_networkInterface = NetworkInterface.getByName(ifName);
		if (_networkInterface == null) {
			throw new JDtnException("Interface " + ifName + " doesn't name a valid interface");
		}
		_ipAddress = new IPAddress(ipAddress);
		_defaultRouterIPAddress = new IPAddress(defaultRouterIPAddressStr);
		_defaultRouterNodeName = defaultRouterNodeName;
		_safFwderAddr = safForwarderAddress;
		_safFwderPort = safForwarderPort;
		_safUsername = safUsername;
		_safPassword = safPassword;
		_safClientLabel = safClientLabel;
		
		// Stop JDTN
		Management.getInstance().stop();

		// Set up bundle store and media store first
		GeneralManagement.getInstance().setStoragePath(bundleDir.getAbsolutePath());
		GeneralManagement.getInstance().setMediaRepositoryPath(mediaDir.getAbsolutePath());
		
		// Thresholds
		BPManagement.getInstance().setBundleBlockFileThreshold(bundleFileThreshold);
		LtpManagement.getInstance().setBlockLengthFileThreshold(blockLengthFileThreshold);
		LtpManagement.getInstance().setSegmentLengthFileThreshold(segmentLengthFileThreshold);
		GeneralManagement.getInstance().setMySegmentRateLimit(segmentRateLimit);
		GeneralManagement.getInstance().setMyBurstSize(burstSize);
		BPManagement.getInstance().setDbInterfaceClassName(dbInterfaceClassname);
		
		// Generate remainder of config based on arguments
		generateConfig();
		
		// Save it all
		Management.getInstance().saveConfig();
		Management.getInstance().start();
	}

	/**
	 * Prompt user for and read the Node name for the node whose configuration
	 * we are generating. Populates member _nodeName.
	 * @param reader where to read answers from
	 * @throws IOException on I/O errors
	 */
	private static void chooseNodeName(BufferedReader reader)
			throws IOException {
		System.out.println("Enter node name (unique among all nodes)");
		_nodeName = reader.readLine();
	}
	
	/**
	 * Prompt user for and read a particular network interface.  Populates
	 * member _networkInterface.
	 * @param reader where to read answers from
	 * @throws IOException on I/O errors
	 */
	private static void chooseNetworkInterface(BufferedReader reader)
	throws IOException {
		while (_networkInterface == null) {
			System.out.println("Network Interfaces:");
			Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
			while (ifs.hasMoreElements()) {
				NetworkInterface intfc = ifs.nextElement();
				System.out.println("  " + intfc.getName());
				Enumeration<InetAddress> addrs = intfc.getInetAddresses();
				while (addrs.hasMoreElements()) {
					System.out.println("    InetAddress " + addrs.nextElement());
				}
			}
			System.out.println("Enter the name of the Network Interface desired");
			String ifName = reader.readLine();
			_networkInterface = NetworkInterface.getByName(ifName);
			if (_networkInterface == null) {
				System.out.println("Try again");
				continue;
			}
			if (!_networkInterface.getInetAddresses().hasMoreElements()) {
				System.out.println("That interface doesn't have any InetAddresses, try again");
			}
		}
		
	}
	
	/**
	 * Prompt for and read user's choice of IP address for the selected
	 * Network Interface (from member _networkInterface)
	 * @param reader where to read answers
	 * @throws IOException on I/O errors
	 */
	private static void chooseInetAddress(BufferedReader reader) throws IOException {
		while (true) {
			ArrayList<InetAddress> inetAddresses = new ArrayList<InetAddress>();
			Enumeration<InetAddress> addrs = _networkInterface.getInetAddresses();
			while (addrs.hasMoreElements()) {
				inetAddresses.add(addrs.nextElement());
			}
			if (inetAddresses.size() == 1) {
				_ipAddress = new IPAddress(inetAddresses.get(0));
				return;
			}
			System.out.println("Choose One of the Internet Addresses:");
			for (int ix = 0; ix < inetAddresses.size(); ix++) {
				System.out.println(ix + ": " + inetAddresses.get(ix));
			}
			String selectionStr = reader.readLine();
			int selection = -1;
			try {
				selection = Integer.parseInt(selectionStr);
			} catch (NumberFormatException e) {
				selection = -1;
			}
			if (selection >= 0 && selection < inetAddresses.size()) {
				InetAddress inetAddress = inetAddresses.get(selection);
				if (inetAddress instanceof Inet6Address) {
					System.err.println("Sorry we don't support Inet6 addresses in this wizard");
					continue;
				}
				_ipAddress = new IPAddress(inetAddresses.get(selection));
				return;
			}
		}
	}
	
	/**
	 * Prompt user for whether a SAF type configuration is desired; or a
	 * Hub-Spoke type configuration.  Sets member _isSafConfig
	 * accordingly
	 * @param reader Reader from which to read answers
	 * @throws IOException on I/O errors
	 */
	private static void chooseSafOrHubSpoke(BufferedReader reader) 
	throws IOException {
		System.out.println("Do you want to enable SAF (y/n)?");
		if (reader.readLine().equalsIgnoreCase("y")) {
			_isSafConfig = true;
		} else {
			_isSafConfig = false;
		}
	}
	
	/**
	 * Prompt user for SAF parameters
	 * @param reader Reader from which to read answers
	 * @throws IOException on I/O errors
	 */
	private static void chooseSafArguments(BufferedReader reader)
	throws IOException {
		System.out.println("SAF Client Label (Default=" + _safClientLabel + "): ");
		String answer = reader.readLine();
		if (answer.length() > 0) {
			_safClientLabel = new String(answer);
		}
		
		System.out.println("SAF Forwarder Address (Default=" + _safFwderAddr + "): ");
		answer = reader.readLine();
		if (answer.length() > 0) {
			_safFwderAddr = new String(answer);
		}
		
		for (;;) {
			System.out.println("SAF Forwarder Port (Default=" + _safFwderPort + "): ");
			answer = reader.readLine();
			if (answer.length() > 0) {
				try {
					Integer.parseInt(answer);
				} catch (NumberFormatException e) {
					System.err.println("Invalid integer: " + answer);
					continue;
				}
				
				_safFwderPort = new String(answer);
				break;
			} else {
				break;
			}
		}
		
		System.out.println("SAF Username (Default=" + _safUsername + "): ");
		answer = reader.readLine();
		if (answer.length() > 0) {
			_safUsername = new String(answer);
		}
		
		System.out.println("SAF Password (Default=" + _safPassword + "): ");
		answer = reader.readLine();
		if (answer.length() > 0) {
			_safPassword = new String(answer);
		}
		
	}
	
	/**
	 * Hub/Spoke Configuration:
	 * Prompt user for whether this is for a Router or a Node.  If Node, then
	 * prompt user for default router parameters.  Populates members:
	 * <ul>
	 *   <li>_isForRouter - true if we're configuring a Router
	 *   <li>if we're not configuring for a Router, further populates the
	 *       following members.
	 *   <ul>
	 *     <li>_defaultRouterNodeName
	 *     <li>_defaultRouterIPAddress
	 *   </ul>
	 * </ul>
	 * @param reader where to read answers from.
	 * @throws IOException on I/O errors
	 */
	private static void chooseRouterOrNode(BufferedReader reader)
	throws IOException {
		System.out.println("Is this configuration for a router? (y/n))");
		if (!reader.readLine().equalsIgnoreCase("y")) {
			System.out.println("Enter default router node name:");
			_defaultRouterNodeName = reader.readLine();

			System.out.println("Enter default router Inet Address:");
			_defaultRouterIPAddress = new IPAddress(reader.readLine());
		}
	}

	/**
	 * Generate the configuration based on user's answers, as specified in
	 * all members.
	 * @throws UnknownHostException on errors dealing with IP Address
	 * @throws JDtnException on other errors
	 */
	private static void generateConfig() throws UnknownHostException, JDtnException {
		System.out.println("Generating Configuration");
		// Set my EndPoint Id stem
		EndPointId myEid = EndPointId.createEndPointId("dtn://" + _nodeName);
		BPManagement.getInstance().setEndPointIdStem(myEid);
		
		// Add a Link for the loopback interface
		TcpClLink loopLink = TcpClManagement.getInstance().addLink(
				LtpManagement.getInstance().getTestInterface(), 
				LtpManagement.getInstance().getTestInterface(), 
				false);
		// Add a neighbor for myself
		IPAddress loopbackAddress = new IPAddress("127.0.0.1");
		TcpClNeighbor meNeighbor = TcpClManagement.getInstance().addNeighbor(_nodeName, myEid);
		meNeighbor.addLinkAddress(new LinkAddress(loopLink, loopbackAddress));
		
		// Add a Link to the outside world
		TcpClLink link = TcpClManagement.getInstance().addLink(
				_networkInterface.getName(),
				_networkInterface.getName(),
				false);
		link.setIpAddress(_ipAddress);
		
		if (_defaultRouterNodeName != null && _defaultRouterIPAddress != null) {
			// Add a Neighbor for default router
			EndPointId eid = EndPointId.createEndPointId("dtn://" + _defaultRouterNodeName);
			TcpClNeighbor neighbor = TcpClManagement.getInstance().addNeighbor(_defaultRouterNodeName, eid);
			neighbor.addLinkAddress(new LinkAddress(link, _defaultRouterIPAddress));
			
			// Add a default route
			BPManagement.getInstance().setDefaultRoute(
					_defaultRouterNodeName, 
					link.getName(), 
					_defaultRouterNodeName);
		}
		
		// Add a route for myself
		BPManagement.getInstance().addRoute(
				_nodeName, 
				"dtn://" + _nodeName + BPManagement.ALL_SUBCOMPONENTS_EID_PATTERN, 
				loopLink.getName(), 
				meNeighbor.getName());
		
		// Set up SAF application if called for
		if (_isSafConfig) {
			String[] args = new String[6];
			args[0] = _networkInterface.getName();
			args[1] = _safClientLabel;
			args[2] = _safFwderAddr;
			args[3] = _safFwderPort;
			args[4] = _safUsername;
			args[5] = _safPassword;
			GeneralManagement.getInstance().addApplication("CafAdapter", "com.cisco.qte.jdtn.apps.CafAdapterApp", args);
		} else {
		
			// Set up Router application
			GeneralManagement.getInstance().addApplication("Router", "com.cisco.qte.jdtn.apps.RouterApp", null);
		}
		
		// Set up other applications
		GeneralManagement.getInstance().addApplication("IonSourceSink", "com.cisco.qte.jdtn.apps.IonSourceSinkApp", null);
		GeneralManagement.getInstance().addApplication("dtncp", "com.cisco.qte.jdtn.apps.Dtn2CpApp", null);
		GeneralManagement.getInstance().addApplication("Text", "com.cisco.qte.jdtn.apps.TextApp", null);
		GeneralManagement.getInstance().addApplication("Photo", "com.cisco.qte.jdtn.apps.PhotoApp", null);
		GeneralManagement.getInstance().addApplication("Video", "com.cisco.qte.jdtn.apps.VideoApp", null);
		GeneralManagement.getInstance().addApplication("Voice", "com.cisco.qte.jdtn.apps.VoiceApp", null);
	}
	
}
