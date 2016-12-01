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

import com.cisco.qte.jdtn.apps.AbstractApp;
import com.cisco.qte.jdtn.apps.AppManager;
import com.cisco.qte.jdtn.apps.MediaRepository;
import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.ltp.LtpNeighbor;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * General Management Interface.
 * Interface for general (not related to either BP or LTP) management
 * properties.
 */
public class GeneralManagement extends AbstractStartableComponent {

	private static final GeneralManagement _instance = new GeneralManagement();
	
	private static final String STORAGE_PATH_DEFAULT = "/tmp/JDTN";
	private static final String MEDIA_PATH_DEFAULT = "/tmp/JDTN/media";
	private static final boolean DEBUG_LOGGING_DEFAULT = false;
	private static final double MY_SEG_RATE_LIMIT_DEFAULT = LtpNeighbor.DEFAULT_SEGMENT_XMIT_RATE_LIMIT;
	private static final long MY_BURST_SIZE_DEFAULT = LtpNeighbor.DEFAULT_BURST_SIZE;
	
	/**
	 * Path to storage for all Bundles and Segments
	 */
	private String storagePath = STORAGE_PATH_DEFAULT;
	
	/**
	 * Path to storage for all Media Bundle Payloads
	 */
	private String mediaRepositoryPath = MEDIA_PATH_DEFAULT;
	
	/**
	 * Whether debug logging is enabled; this property is static because it is
	 * references all over the place.  Avoids getInstance() call.
	 */
	private static boolean debugLogging = DEBUG_LOGGING_DEFAULT;
	public static final String DEBUG_LOGGING_PROPERTY = "GeneralManagement.debugLogging";
	
	/**
	 * Segment Rate Limit I advertise to others
	 */
	private double mySegmentRateLimit = MY_SEG_RATE_LIMIT_DEFAULT;
	
	/**
	 * Segment Burst Size I advertise to others
	 */
	private long myBurstSize = MY_BURST_SIZE_DEFAULT;
	
	/**
	 * Get singleton instance
	 * @return Singleton instance
	 */
	public static GeneralManagement getInstance() {
		return _instance;
	}
	
	/**
	 * Private access constructor to enforce Singleton Pattern
	 */
	private GeneralManagement() {
		super("GeneralManagement");
	}
	
	/**
	 * Parse the General section of the config file.  It is assumed that the
	 * parse is currently sitting on the &lt; General &gt; element.  We parse the
	 * attributes of the &lt; General &gt; element and the closing &lt; /General &gt;.
	 * @param parser Parser doing the parsing
	 * @throws IOException On I/O errors during parse
	 * @throws JDtnException On GeneralConfig specific parse errors
	 * @throws XMLStreamException On General parse errors
	 */
	public void parse(XmlRDParser parser) 
	throws IOException, JDtnException, XmlRdParserException {
		// <General
		//    storagePath="path"
		//    mediaRepositoryPath="path"
		//    debugLogging="true|false"
		//    mySegmentRateLimit="double"
		//    myBurstSize="long"
		// </General
		String value = parser.getAttributeValue("storagePath");
		if (value != null && value.length() > 0) {
			setStoragePath(value);
		}
		
		value = parser.getAttributeValue("mediaRepositoryPath");
		if (value != null && value.length() > 0) {
			setMediaRepositoryPath(value);
		}
		
		Boolean boole = Utils.getBooleanAttribute(parser, "debugLogging");
		if (boole != null) {
			setDebugLogging(boole.booleanValue());
		}
		
		Double segRateLimit = Utils.getDoubleAttribute(parser, "mySegmentRateLimit", 0.0d, Double.MAX_VALUE);
		if (segRateLimit != null) {
			setMySegmentRateLimit(segRateLimit);
		}
		
		Long burstSize = Utils.getLongAttribute(parser, "myBurstSize", 0L, Long.MAX_VALUE);
		if (burstSize != null) {
			setMyBurstSize(burstSize);
		}
		
		XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
		if (event != XmlRDParser.EventType.END_ELEMENT ||
			!parser.getElementTag().equals("General")) {
			throw new JDtnException("Expecting </General>");
		}
	}
	
	/**
	 * Write the GeneralConfig out to the given PrintWriter, in the form of
	 * a &lt; General &gt; element.
	 * @param pw Given PrintWriter
	 */
	public void writeConfig(PrintWriter pw) {
		pw.println("  <General");
		if (!getStoragePath().equals(STORAGE_PATH_DEFAULT)) {
			pw.println("    storagePath='" + getStoragePath() + "'");
		}
		if (!getMediaRepositoryPath().equals(MEDIA_PATH_DEFAULT)) {
			pw.println("    mediaRepositoryPath='" + getMediaRepositoryPath() + "'");
		}
		if (isDebugLogging() != DEBUG_LOGGING_DEFAULT) {
			pw.println("    debugLogging='" + isDebugLogging() + "'");
		}
		if (getMySegmentRateLimit() != MY_SEG_RATE_LIMIT_DEFAULT) {
			pw.println("    mySegmentRateLimit='" + getMySegmentRateLimit() + "'");
		}
		if (getMyBurstSize() != MY_BURST_SIZE_DEFAULT) {
			pw.println("    myBurstSize='" + getMyBurstSize() + "'");
		}
		
		pw.println("  >");
		pw.println("  </General>");
	}
	
	/**
	 * Startup General Management
	 */
	@Override
	protected void startImpl() {
		Store.getInstance().start();
		MediaRepository.getInstance().start();
	}
	
	/**
	 * Shutdown General Management
	 * @throws InterruptedException 
	 */
	@Override
	protected void stopImpl() throws InterruptedException {
		
		MediaRepository.getInstance().stop();
		Store.getInstance().stop();
	}
	
	/**
	 * Dump the state of this object
	 * @param indent Amount of indentation
	 * @param detailed True if want verbose details
	 * @return String containing dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "GeneralManagement\n");
		sb.append(indent + "  StoragePath=" + getStoragePath() + "\n");
		sb.append(indent + "  MediaRepositoryPath=" + getMediaRepositoryPath() + "\n");
		sb.append(indent + "  DebugLogging=" + isDebugLogging() + "\n");
		sb.append(indent + "  MySegmentRateLimit=" + getMySegmentRateLimit() + "\n");
		sb.append(indent + "  MyBurstSize=" + getMyBurstSize() + "\n");
		sb.append(AppManager.getInstance().dump(indent + "  ", detailed));
		return sb.toString();
	}
	
	public void setDefaults() {
		setStoragePath(STORAGE_PATH_DEFAULT);
		setMediaRepositoryPath(MEDIA_PATH_DEFAULT);
		setDebugLogging(DEBUG_LOGGING_DEFAULT);
		setMySegmentRateLimit(MY_SEG_RATE_LIMIT_DEFAULT);
		setMyBurstSize(MY_BURST_SIZE_DEFAULT);
		AppManager.getInstance().setDefaults();
	}
	
	/**
	 * Install an application
	 * @param appName Name of Application
	 * @param appClassName Fully Qualified class name of Application
	 * @param args Arguments for application
	 * @throws JDtnException on errors
	 */
	@SuppressWarnings("unchecked")
	public void addApplication(String appName, String appClassName, String[] args) 
	throws JDtnException {
		try {
			Class appClass = Class.forName(appClassName);
			AbstractApp app = AppManager.getInstance().installApp(appName, appClass, args);
			if (Management.getInstance().isStarted()) {
				app.start();
			}
			
		} catch (ClassNotFoundException e) {
			throw new JDtnException(e);
		}
	}
	
	/**
	 * Uninstall an application
	 * @param appName Name of Application
	 * @throws JDtnException on errors
	 * @throws InterruptedException 
	 */
	public void removeApplication(String appName) 
	throws JDtnException, InterruptedException {
		AppManager.getInstance().uninstallApp(appName);
	}
	
	/**
	 * Path to storage for all Bundles and Segments
	 */
	public String getStoragePath() {
		return storagePath;
	}

	/**
	 * Path to storage for all Bundles and Segments
	 */
	public void setStoragePath(String aStoragePath) {
		storagePath = aStoragePath;
	}

	/**
	 * Path to storage for all Media Bundle Payloads
	 */
	public String getMediaRepositoryPath() {
		return mediaRepositoryPath;
	}

	/**
	 * Path to storage for all Media Bundle Payloads
	 */
	public void setMediaRepositoryPath(String aMediaRepositoryPath) {
		mediaRepositoryPath = aMediaRepositoryPath;
	}

	/**
	 * Whether debug logging is enabled
	 */
	public static boolean isDebugLogging() {
		return debugLogging;
	}

	/**
	 * Whether debug logging is enabled
	 */
	public static void setDebugLogging(boolean aDebugLogging) {
		boolean priorValue = debugLogging;
		debugLogging = aDebugLogging;
		Management.getInstance().fireManagementPropertyChangeEvent(
				null,
				DEBUG_LOGGING_PROPERTY, 
				priorValue, 
				debugLogging);
	}

	/**
	 * Segment Rate Limit I advertise to others
	 */
	public double getMySegmentRateLimit() {
		return mySegmentRateLimit;
	}

	/**
	 * Segment Rate Limit I advertise to others
	 */
	public void setMySegmentRateLimit(double aMySegmentRateLimit) {
		mySegmentRateLimit = aMySegmentRateLimit;
	}

	/**
	 * Segment Burst Size I advertise to others
	 */
	public long getMyBurstSize() {
		return myBurstSize;
	}

	/**
	 * Segment Burst Size I advertise to others
	 */
	public void setMyBurstSize(long aMyBurstSize) {
		myBurstSize = aMyBurstSize;
	}
	
	
}
