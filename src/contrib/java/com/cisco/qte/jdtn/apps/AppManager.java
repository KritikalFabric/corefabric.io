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
package com.cisco.qte.jdtn.apps;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.bp.BpApi;
import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;

/**
 * Management of installed applications; primarily for internal applications
 * like Ping, etc.
 */
public class AppManager extends AbstractStartableComponent {

	private static final Logger _logger =
		Logger.getLogger(AppManager.class.getCanonicalName());
	
	private static final AppManager _instance = new AppManager();
	
	// List of installed Apps
	protected ArrayList<AbstractApp> _appList =
		new ArrayList<AbstractApp>();
	// Map from String app name to AbstractApp for name lookups
	protected HashMap<String, AbstractApp> _appNameMap =
		new HashMap<String, AbstractApp>();
	
	/**
	 * Get singleton instance
	 * @return Singleton instance
	 */
	public static AppManager getInstance() {
		return _instance;
	}
	
	/**
	 * Restricted access constructor
	 */
	private AppManager() {
		super("AppManager");
	}
	
	/**
	 * Parse applications config.  Assumed parser is at &lt; Applications &gt;
	 * element.  Parses thru &lt; /Applications &gt;
	 * @param parser Given Pull Parser
	 * @throws IOException on I/O errors
	 * @throws XMLStreamException on general parse errors
	 * @throws JDtnException on JDTN specific errors
	 */
	@SuppressWarnings("unchecked")
	public void parse(XmlRDParser parser) 
	throws XmlRdParserException, IOException, JDtnException {
		// <Applications>
		//   <Application
		//     name='name'
		//     class='className'
		//     args='arguments'
		//   />
		//   ...
		// </Applications>
		
		// Make sure BpApi has been started up so we can install Apps.
		if (!BpApi.getInstance().isStarted()) {
			BpApi.getInstance().start();
		}
		
		XmlRDParser.EventType event = Utils.nextNonTextEvent(parser);
		while (event == XmlRDParser.EventType.START_ELEMENT) {
			if (!parser.getElementTag().equals("Application")) {
				throw new JDtnException("Expecting <Application>");
			}
			
			String appName = parser.getAttributeValue("name");
			if (appName == null || appName.length() == 0) {
				throw new JDtnException("Missing 'name' attribute in <Application> element");
			}
			
			String appClass = parser.getAttributeValue("class");
			if (appClass == null || appClass.length() == 0) {
				throw new JDtnException("Missing 'class' attribute in <Application> element");
			}
			
			String[] args = null;
			String argsStr = parser.getAttributeValue("args");
			if (argsStr != null && argsStr.length() != 0) {
				args = argsStr.split("\\s+");
			}
			
			try {
				Class clazz = Class.forName(appClass);
				installApp(appName, clazz, args);
				
			} catch (ClassNotFoundException e) {
				throw new JDtnException("Configured app class " + appClass + " not found");
			}
			
			event = Utils.nextNonTextEvent(parser);
			if (event != XmlRDParser.EventType.END_ELEMENT ||
				!parser.getElementTag().equals("Application")) {
				throw new JDtnException("Expecting </Application>");
			}
			
			event = Utils.nextNonTextEvent(parser);
		}
		
		if (event != XmlRDParser.EventType.END_ELEMENT ||
			!parser.getElementTag().equals("Applications")) {
			throw new JDtnException("Expecting </Applications>");
		}
		
	}
	
	public void writeConfig(PrintWriter pw) {
		if (!_appList.isEmpty()) {
			pw.println("  <Applications>");
			for (AbstractApp app : _appList) {
				// Don't write to config if app shouldn't be.
				if (!app.isSaveInConfig()) {
					continue;
				}
				//   <Application
				//     name='name'
				//     class='className'
				pw.println("    <Application");
				pw.println("      name='" + app.getName() + "'");
				pw.println("      class='" + app.getClass().getCanonicalName() + "'");
				String[] args = app.getArgumentss();
				if (args != null) {
					StringBuffer sb = new StringBuffer();
					for (String arg : args) {
						sb.append(arg);
						sb.append(" ");
					}
					pw.println("      args='" + sb.toString() + "'");
				}
				pw.println("    />");
			}
			pw.println("  </Applications>");
		}
	}
	
	public void setDefaults() {
		if (!BpApi.getInstance().isStarted()) {
			BpApi.getInstance().start();
		}
		// Uninstall all Apps
		while (!_appList.isEmpty()) {
			AbstractApp app = _appList.get(0);
			try {
				uninstallApp(app.getName());
			} catch (JDtnException e) {
				_logger.log(Level.SEVERE, "uninstallApp(" + app.getName() + ")", e);
			} catch (InterruptedException e) {
				_logger.log(Level.SEVERE, "uninstallApp(" + app.getName() + ")", e);
			}
		}
	}
	
	/**
	 * Dump this object
	 * @param indent Indentation
	 * @param detailed Verbose
	 * @return dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "Applications\n");
		for (AbstractApp app : _appList) {
			sb.append(app.dump(indent + "  ", detailed));
		}
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return dump("", false);
	}
	
	/**
	 * Called from BpApi to perform startup
	 */
	@Override
	protected void startImpl() {
		// Ping App is always installed
		try {
			AbstractApp app = installApp("Dtn2Ping", Dtn2PingApp.class, null);
			// Since PingApp is always installed, we don't need to save it in config
			app.setSaveInConfig(false);
			
		} catch (JDtnException e) {
			_logger.log(Level.SEVERE, "startup", e);
		}
		
		// Startup all installed Apps
		for (AbstractApp app : _appList) {
			app.start();
		}
	}
	
	/**
	 * Called from BpApi when shutting down
	 */
	@Override
	protected void stopImpl() {
		// Uninstall all Apps
		while (!_appList.isEmpty()) {
			AbstractApp app = _appList.get(0);
			try {
				uninstallApp(app.getName());
			} catch (JDtnException e) {
				_logger.log(Level.SEVERE, "uninstallInternalApp(" + app.getName() + ")", e);
			} catch (InterruptedException e) {
				_logger.log(Level.SEVERE, "uninstallInternalApp(" + app.getName() + ")", e);
			}
		}
	}
	
	/**
	 * Install an App with the given name.  Create a new instance of the given
	 * AbstractApp class and install it.  The App class must have a no-args
	 * constructor.  The App is not started yet, a separate call to
	 * its startup() must be made.
	 * @param appName Name of App to install.  This must be unique among all
	 * other installed apps.
	 * @param appClass The Class of the App.
	 * @return The Application instance created
	 * @throws JDtnException if appName is already installed, or on a variety
	 * of Exceptions arising from trying to instantiate the no-args constructor.
	 */
	@SuppressWarnings("unchecked")
	public AbstractApp installApp(
			String appName, 
			Class<? extends AbstractApp> appClass,
			String[] args) 
	throws JDtnException {
		try {
			if (_appNameMap.get(appName) != null) {
				throw new JDtnException("Application " + appName
						+ " is already installed");
			}
			AbstractApp app = null;
			// Construct an instance of the App via redirection. Slight
			// complication because we want a non-trivial constructor that
			// accepts String[] as its single argument.
			Class[] argTypes = { String[].class };
			Constructor<? extends AbstractApp> constructor = appClass
					.getConstructor(argTypes);
			Object[] conArgs = { args };

			app = constructor.newInstance(conArgs);

			if (!app.getName().equals(appName)) {
				throw new JDtnException("AppName being installed " + appName
						+ " not same as AppName in the App Class");
			}

			_appList.add(app);
			_appNameMap.put(appName, app);
			return app;
		} catch (InstantiationException e) {
			throw new JDtnException(e);
		} catch (IllegalAccessException e) {
			throw new JDtnException(e);
		} catch (Exception e) {
			throw new JDtnException(e);
		}
	}
	
	/**
	 * Get the App with the given Name
	 * @param appName Given Name
	 * @return The App.
	 */
	public AbstractApp getApp(String appName) {
		return _appNameMap.get(appName);
	}
	
	/**
	 * Uninstall the App with the given Name
	 * @param appName
	 * @throws JDtnException
	 * @throws InterruptedException 
	 */
	public void uninstallApp(String appName) throws JDtnException, InterruptedException {
		AbstractApp app = _appNameMap.get(appName);
		if (app == null) {
			throw new JDtnException("Application " + appName + " is not installed");
		}
		_appNameMap.remove(appName);
		_appList.remove(app);
		app.stop();
	}
	
}
