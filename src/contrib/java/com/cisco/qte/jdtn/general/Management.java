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

import java.io.*;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.apps.AppManager;
import com.cisco.qte.jdtn.bp.BPManagement;
import com.cisco.qte.jdtn.bp.BpApi;
import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.component.EventBroadcaster;
import com.cisco.qte.jdtn.events.ManagementPropertyChangeEvent;
import com.cisco.qte.jdtn.ltp.LtpManagement;
import com.cisco.qte.jdtn.tcpcl.TcpClManagement;
import com.cisco.qte.jdtn.udpcl.UdpClManagement;
import com.cisco.qte.jdtn.general.XmlRDParser;
import org.kritikal.fabric.contrib.jdtn.JDtnConfig;

/**
 * Configuration/Management component for JDtn.  Supports configuration properties for
 * <ul>
 *   <li>General configuration
 *   <li>BP configuration
 *   <li>LTP configuration
 * </ul>
 * Supports reading and writing config from/to a config file.  The config file
 * is located in one of the following places (in order in which we look):
 * <ul>
 *   <li>{com.cisco.qte.jdtn.configFile}/JDtnConfig.xml
 *   <li>{user.home}/JDtnConfig.xml
 *   <li>|resourcePath|/JDtnConfig.xml (only on read config)
 * </ul>
 * where {xxx} signifies System.Properties lookup.
 * and |resourcePath| signifies the base of the application deployment
 * path.  The idea of this is that the deployment can contain a default
 * JDtnConfig.xml when it is deployed.  But the default config is never
 * written to; we write to one of the other paths.
 * <p>
 * Shares Management chores with the following other components:
 * <ul>
 *   <li>GeneralManagement
 *   <li>BPManagement
 *   <li>LtpManagement
 * </ul>
 */
public class Management extends AbstractStartableComponent {

	public static final int CONFIG_VERSION = 3;
	
	private static final Logger _logger =
		Logger.getLogger(Management.class.getCanonicalName());
	
	private static Management _instance = new Management();
	
	/** Filename of JDTN Config file */
	//public static final String CONFIG_FILENAME = "jDtnConfig.xml";
	/** System Property name of location of JDTN Config File */
	//public static final String CONFIG_FILENAME_SYS_PROP = "com.cisco.qte.jdtn.configFile";
	/** Resource name of default JDTN Config File */
	//public static final String CONFIG_RESOURCE_NAME = "/jDtnConfig.xml";
	
	private boolean _initialized = false;
	
	// Thread which is run at system exit
	private Thread exitThread = null;
	// Singleton instance which contains system exit hook
	private Runnable exitHook = null;

	/**
	 * Get singleton instance
	 * @return Singleton instance
	 */
	public static Management getInstance() {
		return _instance;
	}
	
	/**
	 * Construct singleton instance whose sole purpose is to provide a Thread
	 * for system exit hook
	 */
	private Management() {
		super("Management");
		_instance = this;
		EventBroadcaster.getInstance().createBroadcastGroup(
				Management.class.getCanonicalName());
		EventBroadcaster.getInstance().createBroadcastGroup(
				LinksList.class.getCanonicalName());
		initialize();
	}
	
	/**
	 * Internal initialization
	 */
	public void initialize() {
		if (!_initialized) {
			_initialized = true;
			loggingFixup();
			setDefaults();
			loadConfig();
			exitHook = new Runnable() {
				public void run() {
					try {
						stop();
						EventBroadcaster.getInstance().deleteBroadcastGroup(
								Management.class.getCanonicalName());
						EventBroadcaster.getInstance().deleteBroadcastGroup(
								LinksList.class.getCanonicalName());
					} catch (InterruptedException e) {
						_logger.log(Level.SEVERE, "run()", e);
					}
				}
			};
			exitThread = new Thread(exitHook);
			Runtime.getRuntime().addShutdownHook(exitThread);
		}
	}
	
	/**
	 * Startup operations of the JDTN Stack
	 */
	@Override
	protected void startImpl() {
		_logger.fine("startImpl()");
		_logger.fine("JDTN Starting Up");
		EventBroadcaster.getInstance().start();
		GeneralManagement.getInstance().start();
		LtpManagement.getInstance().start();
		BPManagement.getInstance().start();
		AppManager.getInstance().start();
		TcpClManagement.getInstance().start();
		UdpClManagement.getInstance().start();
		
	}
	
	/**
	 * Shutdown the JDTN Stack
	 * @throws InterruptedException 
	 */
	@Override
	protected void stopImpl() throws InterruptedException {
		_logger.fine("JDTN Shutting Down UDPCL");
		UdpClManagement.getInstance().stop();
		TcpClManagement.getInstance().stop();
		AppManager.getInstance().stop();
		BpApi.getInstance().stop();
		BPManagement.getInstance().stop();
		LtpManagement.getInstance().stop();
		GeneralManagement.getInstance().stop();
		EventBroadcaster.getInstance().stop();
	}
	
	/**
	 * There are certain situations where java.util.logging will not log INFO
	 * and above to the console.  This leaves us unable to report potentially
	 * serious errors (such as, for example, parse errors in the config file).
	 * Detect and warn the user about such a situation.
	 */
	private void loggingFixup() {
		Logger logger = Logger.getLogger("");
		Handler[] handlers = logger.getHandlers();
		if (handlers == null || handlers.length == 0) {
			System.out.println();
			System.out.println("NOTE!");
			System.out.println("There are no handlers installed in the Logging library");
			System.out.println("I have no way of fixing this problem myself");
			System.out.println("Perhaps you specified '-Djava.util.logging.config.file=filename' but 'filename' doesn't exist");
			System.out.println("You need to fix this; otherwise, serious error messages will be missed");
			System.out.println();
		}
	}
	
	/**
	 * Reset all properties to default values
	 */
	public void setDefaults() {
		_logger.fine("setDefaults");
		
		GeneralManagement.getInstance().setDefaults();
		BPManagement.getInstance().setDefaults();
		LtpManagement.getInstance().setDefaults();
		TcpClManagement.getInstance().setDefaults();
		UdpClManagement.getInstance().setDefaults();
	}
	
	/**
	 * Read the config file and populate the configuration properties accordingly.
	 * If there exist some properties not mentioned in the config file, then
	 * they will not be changed.
	 */
	public void loadConfig() {		
		// Open the config file
		_logger.fine("loadConfig");
		Reader reader = openConfigForReading();
		if (reader == null) {
			_logger.info("Cannot find JDTN config file; proceeding with defaults");
			return;
		}
		
		// Setup a parser for the config file
		XmlRDParser parser = null;
		try {
			parser = Platform.getXmlStreamReader(reader);
		} catch (JDtnException e) {
			_logger.log(Level.SEVERE, "Trying to get XmlPullParser", e);
			try {
				reader.close();
			} catch (IOException e1) {
				// Nothing
			}
			return;
		}
		
		// Parse top level elements of config, which will be any or all of:
		// <JDtnConfig version='version'>
		//    <General> ... </General>
		//    <BP> ... </BP>
		//    <LTP> ... </LTP>
		//    <Applications> ... </Applications>
		//    <TcpCl> ... </TcpCl>
		//    <UdpCl> ... </UdpCl>
		// </JDtnConfig.
		try {
			XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
			
			// Make sure we have root element
			if (event != XmlRDParser.EventType.START_ELEMENT ||
				!parser.getElementTag().equals("JDtnConfig")) {
				_logger.severe("Config file root element not 'JDtnConfig'");
				event = XmlRDParser.EventType.END_DOCUMENT;
			}
			
			// Make sure we have a config file version attribute and that
			// config file version is compatible
			Integer version = Utils.getIntegerAttribute(parser, "version", 1, CONFIG_VERSION);
			if (version == null) {
				throw new JDtnException("No version attribute in <JDtnConfig> element");
			}
			
			// Process all second level elements
			event = Utils.nextNonTextEvent(parser);
			while (event == XmlRDParser.EventType.START_ELEMENT) {
				String name = parser.getElementTag();
				if (name.equals("General")) {
					GeneralManagement.getInstance().parse(parser);
					
				} else if (name.equals("BP")) {
					BPManagement.getInstance().parse(parser);
					
				} else if (name.equals("LTP")) {
					LtpManagement.getInstance().parse(parser);
					
				} else if (name.equals("Applications")) {
					AppManager.getInstance().parse(parser);
					
				} else if (name.equals("TcpCl")) {
					TcpClManagement.getInstance().parseConfig(parser);
					
				} else if (name.equals("UdpCl")) {
					UdpClManagement.getInstance().parseConfig(parser);
					
				} else {
					_logger.severe("Unrecognized second level tag: " + name);
					break;
				}
				event = Utils.nextNonTextEvent(parser);
			}
			
			// Make sure we have End Tag for root element
			if (event != XmlRDParser.EventType.END_ELEMENT) {
				_logger.severe("Expecting </JDtnConfig>, get event " + event);
			} else if (!parser.getElementTag().equals("JDtnConfig")) {
				_logger.severe("Expecting </JDtnConfig>, got </" + parser.getElementTag() + ">");
			} else {
				// Make sure we have END Document
				event = Utils.nextNonTextEvent(parser);
				if (event != XmlRDParser.EventType.END_DOCUMENT) {
					_logger.severe("Expecting END_DOCUMENT; got event " + event);
				}
			}
			
		} catch (Exception e) {
			_logger.log(Level.SEVERE, "Second level parse", e);
			
		} finally {
			try {
				reader.close();
			} catch (IOException e) {
				// Nothing
			}
		}		
	}
	
	/**
	 * Save the configuration properties to the config file.  All property values
	 * are saved.
	 */
	public void saveConfig() {
		_logger.fine("saveConfig()");
		Writer writer = openConfigForWriting();
		if (writer == null) {
			_logger.severe("Management.saveConfig(): Cannot open config file for writing");
			return;
		}
		PrintWriter pw = new PrintWriter(writer);
		
		pw.println("<JDtnConfig version='" + CONFIG_VERSION + "'>");
		GeneralManagement.getInstance().writeConfig(pw);
		// Note: LTPManagement must be before BPManagement
		LtpManagement.getInstance().writeConfig(pw);
		BPManagement.getInstance().writeConfig(pw);
		// Note: AppManager must be after BPManagement
		AppManager.getInstance().writeConfig(pw);
		TcpClManagement.getInstance().writeConfig(pw);
		UdpClManagement.getInstance().writeConfig(pw);
		pw.println("</JDtnConfig>");
		
		pw.close();
		try {
			writer.close();
		} catch (IOException e) {
			// Nothing
		}
		
	}
	
	/**
	 * Determine the location of the config file and open it for reading
	 * @return Reader opened on the config file
	 */
	public Reader openConfigForReading() {
		return new StringReader(JDtnConfig.xml);
	}
	/* {
		File file = null;
		Reader reader = null;
		
		// Look for System property naming config file
		String filePath = System.getProperty(CONFIG_FILENAME_SYS_PROP);
		if (filePath != null) {
			file = new File(filePath, CONFIG_FILENAME);
			try {
				reader = new FileReader(file);
				_logger.fine("Config file path (Read, sysprop) = " + file.getAbsolutePath());
				return reader;
			} catch (FileNotFoundException e) {
				_logger.severe("Config file not found at sys prop defined path " +
						file.getAbsolutePath());
			}
		}
		
		// Look for config file in User.home
		String userHome = System.getProperty("user.home");
		if (userHome != null) {
			file = new File(userHome, CONFIG_FILENAME);
			try {
				reader = new FileReader(file);
				_logger.fine("Config file path (Read, userhome) = " + file.getAbsolutePath());
				return reader;
			} catch (FileNotFoundException e) {
				_logger.info("Config file not found at path " +
						file.getAbsolutePath());
			}
		}
		
		// Look for a resource containing config file
		InputStream is = Management.class.getResourceAsStream(CONFIG_RESOURCE_NAME);
		if (is != null) {
			_logger.fine("Config file path (Read, resource) = resource");
			reader = new InputStreamReader(is);
		} else {
			_logger.info("Config file not found as resource " + CONFIG_RESOURCE_NAME);
		}
		
		return reader;
	}
	
	/**
	 * Determine the location of the config file and open it for writing
	 * @return Writer opened on the config file
	 */
	public Writer openConfigForWriting() { /*
		File file = null;
		Writer writer = null;
		
		// Look for System property naming config file
		String filePath = System.getProperty(CONFIG_FILENAME_SYS_PROP);
		if (filePath != null) {
			file = new File(filePath, CONFIG_FILENAME);
			try {
				writer = new FileWriter(file);
				_logger.finest("Config file path (Write) = " + file.getAbsolutePath());
				return writer;
			} catch (IOException e) {
				// Ignore
			}
		}
		
		// Use config file in User.home (if user.home exists)
		String userHome = System.getProperty("user.home");
		if (userHome != null) {
			file = new File(userHome, CONFIG_FILENAME);
			try {
				writer = new FileWriter(file);
				_logger.finest("Config file path (Write) = " + file.getAbsolutePath());
				return writer;
			} catch (IOException e) {
				// Ignore
			}
		}
		
		// We tried...
		*/
		return null;
	}
	
	/**
	 * Remove all writable configuration files in the various places we look
	 * for them.
	 */
	public void removeConfigFile() { /*
		File file;
		
		// Look for System property naming config file
		String filePath = System.getProperty(CONFIG_FILENAME_SYS_PROP);
		if (filePath != null) {
			file = new File(filePath, CONFIG_FILENAME);
			if (file.exists() && !file.delete()) {
				_logger.severe("Error deleting config file " + 
						file.getAbsolutePath());
			}
		}
		
		// Use config file in User.home (if user.home exists)
		String userHome = System.getProperty("user.home");
		if (userHome != null) {
			file = new File(userHome, CONFIG_FILENAME);
			if (file.exists() && !file.delete()) {
				_logger.severe("Error deleting config file " + 
						file.getAbsolutePath());
			}
		} */
	}
	
	/**
	 * Dump the state of this object
	 * @param indent Amount of indentation
	 * @param detailed True if want verbose details
	 * @return String containing dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "JDTN Management\n");
		sb.append(GeneralManagement.getInstance().dump(indent + "  ", detailed));
		sb.append(BPManagement.getInstance().dump(indent + "  ", detailed));
		sb.append(LtpManagement.getInstance().dump(indent + "  ", detailed));
		sb.append(EventBroadcaster.getInstance().dump(indent + "  ", detailed));
		sb.append(super.dump(indent + "  ", detailed));
		return sb.toString();
	}

	/**
	 * Notify all ManagementListeners that given property change event has
	 * occurred.
	 * @param source Object whose property is changing
	 * @param propertyName A unique name for the property.  This is of the
	 * form "simple_class_name.property_name".
	 * @param oldValue The value that the property had prior to the change.
	 * @param newValue The value that the property has assumed.
	 */
	public void fireManagementPropertyChangeEvent(
			Object source,
			String propertyName,
			Object oldValue,
			Object newValue) {
		try {
			EventBroadcaster.getInstance().broadcastEvent(
					Management.class.getCanonicalName(), 
					new ManagementPropertyChangeEvent(
							source, propertyName, oldValue, newValue));
		} catch (Exception e) {
			_logger.log(Level.SEVERE, "fireManagementPropertyChangeEvent()", e);
		}
	}
	
}
