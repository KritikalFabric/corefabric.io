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
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.apps.AbstractApp;
import com.cisco.qte.jdtn.apps.AppManager;
import com.cisco.qte.jdtn.apps.BPSendFileApp;
import com.cisco.qte.jdtn.apps.CafAdapterApp;
import com.cisco.qte.jdtn.apps.Dtn2CpApp;
import com.cisco.qte.jdtn.apps.Dtn2PingApp;
import com.cisco.qte.jdtn.apps.IonSourceSinkApp;
import com.cisco.qte.jdtn.apps.MediaRepository;
import com.cisco.qte.jdtn.apps.PhotoApp;
import com.cisco.qte.jdtn.apps.RateEstimatorApp;
import com.cisco.qte.jdtn.apps.SafAdapterApp;
import com.cisco.qte.jdtn.apps.TextApp;
import com.cisco.qte.jdtn.apps.VideoApp;
import com.cisco.qte.jdtn.apps.VoiceApp;
import com.cisco.qte.jdtn.bp.BPException;
import com.cisco.qte.jdtn.bp.BPManagement;
import com.cisco.qte.jdtn.bp.BundleColor;
import com.cisco.qte.jdtn.bp.BundleId;
import com.cisco.qte.jdtn.bp.BundleOptions;
import com.cisco.qte.jdtn.bp.BundleStatusReport;
import com.cisco.qte.jdtn.bp.EidMap;
import com.cisco.qte.jdtn.bp.EidScheme;
import com.cisco.qte.jdtn.bp.EndPointId;
import com.cisco.qte.jdtn.bp.RouteTable;
import com.cisco.qte.jdtn.bp.PrimaryBundleBlock.BPClassOfServicePriority;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Link;
import com.cisco.qte.jdtn.general.LinkAddress;
import com.cisco.qte.jdtn.general.LinksList;
import com.cisco.qte.jdtn.general.Management;
import com.cisco.qte.jdtn.general.Neighbor;
import com.cisco.qte.jdtn.general.NeighborsList;
import com.cisco.qte.jdtn.general.Store;
import com.cisco.qte.jdtn.ltp.EngineId;
import com.cisco.qte.jdtn.ltp.IPAddress;
import com.cisco.qte.jdtn.ltp.LtpException;
import com.cisco.qte.jdtn.ltp.LtpLink;
import com.cisco.qte.jdtn.ltp.LtpManagement;
import com.cisco.qte.jdtn.ltp.LtpNeighbor;
import com.cisco.qte.jdtn.ltp.udp.LtpUDPLink;
import com.cisco.qte.jdtn.ltp.udp.LtpUDPNeighbor;
import com.cisco.qte.jdtn.tcpcl.TcpClLink;
import com.cisco.qte.jdtn.tcpcl.TcpClManagement;
import com.cisco.qte.jdtn.tcpcl.TcpClNeighbor;
import com.cisco.qte.jdtn.udpcl.UdpClLink;
import com.cisco.qte.jdtn.udpcl.UdpClManagement;
import com.cisco.qte.jdtn.udpcl.UdpClNeighbor;
import com.cisco.saf.Service;

/**
 * A command line interface to the JDTN Stack.  Mostly managment and config.
 * Some bundling.
 */
public class Shell {

	private static final Logger _logger =
		Logger.getLogger(Shell.class.getCanonicalName());
	
	private TestApp _testApp = null;
	
	/**
	 * Main program
	 * @param args
	 */
	public static void main(String[] args) {
		Shell shell;
		try {
			shell = new Shell();
		} catch (BPException e) {
			_logger.log(Level.SEVERE, "Shell construction", e);
			System.exit(1);
			return;
		}
		try {
			shell.cmdLoop();
		} catch (Exception e) {
			_logger.log(Level.SEVERE, "CmdLoop", e);
		}
		shell.terminate();
		System.exit(0);
	}

	/**
	 * Constructor; starts up JDTN stack, launches Bundle receiver thread
	 * @throws BPException On startup errors
	 */
	public Shell() throws BPException {
		Management.getInstance().start();
		BPManagement.getInstance().requestBundleRestore();
		
		try {
			_testApp = (TestApp)
				AppManager.getInstance().installApp("Test", TestApp.class, null);
			_testApp.start();
		} catch (JDtnException e) {
			throw new BPException(e);
		}
	}
	
	/**
	 * Shell command loop; reads commands and dispatches them until 'exit' command.
	 * @throws Exception On I/O errors reading stdin
	 */
	public void cmdLoop() throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		while (true) {
			
			System.out.println("> ");
			System.out.flush();
			String cmdLine = reader.readLine();
			if (cmdLine == null) {
				break;
			}
			if (cmdLine.length() == 0) {
				continue;
			}
			
			String[] words = cmdLine.split("\\s+");
			
			try {
				boolean doExit = dispatchCommand(cmdLine, words);
				if (doExit) {
					break;
				}
			} catch (Exception e) {
				System.err.println("Error executing command:");
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Terminate the shell; kills the bundle receiver shell, shuts down JDTN
	 * stack.
	 */
	public void terminate() {
		System.out.println("Terminating Shell...");
		try {
			AppManager.getInstance().uninstallApp(_testApp.getName());
		} catch (JDtnException e) {
			_logger.log(Level.SEVERE, "uninstallApp testApp()", e);
		} catch (InterruptedException e) {
			_logger.log(Level.SEVERE, "uninstallApp testApp()", e);
		}
		try {
			Management.getInstance().stop();
		} catch (InterruptedException e) {
			_logger.log(Level.SEVERE, "Management.stop()", e);
		}
		System.out.println("Done");
	}
	
	private String lastCommand = null;
	
	/**
	 * Dispatch user input command line
	 * @param cmdLine User command line
	 * @param words Command line split into words
	 * @return True if exit command entered
	 * @throws Exception
	 */
	private boolean dispatchCommand(String cmdLine, String[] words) throws Exception {
		String command = getMatchAmongAlts(
				words[0], 
				"exit help show config add remove set ping text photo video " +
				"voice clean clear ion rateEstimator start stop sendFile !!");
		if (command.equalsIgnoreCase("help")) {
			help(words);
			
		} else if (command.equalsIgnoreCase("exit")) {
			return true;
			
		} else if (command.equalsIgnoreCase("show")) {
			show(words);
			
		} else if (command.equalsIgnoreCase("config")) {
			config(words);
			
		} else if (command.equalsIgnoreCase("add")) {
			add(words);
			
		} else if (command.equalsIgnoreCase("remove")) {
			remove(words);
			
		} else if (command.equalsIgnoreCase("start")) {
			start(words);
			
		} else if (command.equalsIgnoreCase("stop")) {
			stop(words);
			
		} else if (command.equalsIgnoreCase("set")) {
			set(words);
			
		} else if (command.equalsIgnoreCase("ping")) {
			ping(words);
			
		} else if (command.equalsIgnoreCase("text")) {
			text(words);
			
		} else if (command.equalsIgnoreCase("photo")) {
			photo(words);
			
		} else if (command.equalsIgnoreCase("video")) {
			video(words);
			
		} else if (command.equalsIgnoreCase("voice")) {
			voice(words);
		
		} else if (command.equalsIgnoreCase("rateEstimator")) {
			rateEstimator(words);
			
		} else if (command.equalsIgnoreCase("clean")) {
			clean(words);
			
		} else if (command.equalsIgnoreCase("clear")) {
			clear(words);
			
		} else if (command.equalsIgnoreCase("ion")) {
			ion(words);
			
		} else if (command.equalsIgnoreCase("sendFile")) {
			doSend(words);
			
		} else if (command.equals("!!")) {
			if (lastCommand != null) {
				System.out.println(lastCommand);
				if (dispatchCommand(lastCommand, lastCommand.split(" "))) {
					return true;
				}
			} else {
				System.err.println("No last command line to recall");
			}
			
		} else {
			System.err.println("Unrecognized command: " + command);
		}
		if (!command.equals("!!")) {
			lastCommand = cmdLine;
		}
		return false;
	}
	
	/**
	 * Help command processing; can display overall help if no arguments or
	 * dispatch to lower level help.
	 * @param words arguments
	 */
	private void help(String[] words) {
		String subject = null;
		if (words.length >= 2) {
			subject = getMatchAmongAlts(
					words[1], 
					"config show set add remove ping text photo video " +
					"voice clean ion clear rateEstimator start stop sendFile !!");
		}
		if (subject == null) {
			topLevelHelp();
		
		} else if (subject.equalsIgnoreCase("config")) {
			helpConfig(words);
		} else if (subject.equalsIgnoreCase("show")) {
			helpShow(words);
		} else if (subject.equalsIgnoreCase("set")) {
			helpSet(words);
		} else if (subject.equalsIgnoreCase("add")) {
			helpAdd(words);
		} else if (subject.equalsIgnoreCase("remove")) {
			helpRemove(words);
		} else if (subject.equalsIgnoreCase("ping")) {
			helpPing(words);
		} else if (subject.equalsIgnoreCase("text")) {
			helpText(words);
		} else if (subject.equalsIgnoreCase("photo")) {
			helpPhoto(words);
		} else if (subject.equalsIgnoreCase("video")) {
			helpVideo(words);
		} else if (subject.equalsIgnoreCase("voice")) {
			helpVoice(words);
		} else if (subject.equalsIgnoreCase("rateEstimator")) {
			helpRateEstimator(words);
		} else if (subject.equalsIgnoreCase("clear")) {
			helpClear(words);
		} else if (subject.equalsIgnoreCase("ion")) {
			helpIon(words);
		} else if (subject.equalsIgnoreCase("start")) {
			helpStart(words);
		} else if (subject.equalsIgnoreCase("stop")) {
			helpStop(words);
		} else if (subject.equalsIgnoreCase("sendFile")) {
			helpSend(words);
		} else if (subject.equalsIgnoreCase("clean")) {
			helpClean(words);
			
		} else {
			topLevelHelp();
		}
	}

	/**
	 * Display an overall help summary
	 */
	private void topLevelHelp() {
		System.out.println("Help");
		System.out.println("  add ...                      Add Stuff");
		System.out.println("  clean ...                    Clean Stuff");
		System.out.println("  clear                        Clear statistics");
		System.out.println("  config ...                   Configuration related commands");
		System.out.println("  exit                         Get outta here");
		System.out.println("  ion                          ION Interop Testing");
		System.out.println("  photo ...                    Send Photo Note");
		System.out.println("  ping ...                     Ping things (DTN2 'dtnping' comptaible)");
		System.out.println("  rateEstimator ...            Estimate Segment Rate Limit");
		System.out.println("  remove ...                   Remove Stuff");
		System.out.println("  sendFile ...                 Send file (DTN2 'dtncp' compatible)");
		System.out.println("  set ...                      Set Stuff");
		System.out.println("  show ...                     Show stuff");
		System.out.println("  start ...                    Start things");
		System.out.println("  stop ...                     Stop things");
		System.out.println("  text ...                     Send Text Note");
		System.out.println("  video ...                    Send Video Note");
		System.out.println("  voice ...                    Send Voice Note");
		System.out.println("  !!                           Repeat last command");
		System.out.println("  help <command>               Get further help on these commands");
	}
	
	/**
	 * Display help on 'config' command
	 * @param words arguments
	 */
	private void helpConfig(String[] words) {
		System.out.println(" config save                    Save");
		System.out.println(" config restore                 Restore saved configuration");
		System.out.println(" config defaults                Revert to default configuration");
	}
	
	/**
	 * Execute 'config' command; save, restore, or revert to default configuration.
	 * @param words arguments
	 */
	private void config(String[] words) {
		if (words.length < 2) {
			System.err.println("Incomplete 'config' command");
			return;
		}
		String command = getMatchAmongAlts(words[1], "save restore defaults");
		if (command.equalsIgnoreCase("save")) {
			System.out.println("Saving Confg");
			Management.getInstance().saveConfig();
			
		} else if (command.equalsIgnoreCase("restore")) {
			System.out.println("Restoring saved config");
			Management.getInstance().setDefaults();
			Management.getInstance().loadConfig();
			
		} else if (command.equalsIgnoreCase("defaults")) {
			System.out.println("Setting default config");
			Management.getInstance().setDefaults();
		
		} else {
			System.err.println("Unrecognized 'config' option: '" + command + "'");
		}
	}
	
	/**
	 * Display help for 'show' command; show properties of various subsystems
	 * @param words arguments
	 */
	private void helpShow(String[] words) {
		System.out.println("  show all                                 Show all");
		System.out.println("  show all links                           Show all links");
		System.out.println("  show all neighbors                       Show all neighbors");
		System.out.println("  show general                             Show general configuration");
		System.out.println("  show ltp                                 Show LTP properties and stats");
		System.out.println("  show ltp statistics                      Show LTP Statistics");
		System.out.println("  show ltp links                           Show all Links");
		System.out.println("  show ltp blocks {-verbose}               Show LTP Block queues");
		System.out.println("  show bp                                  Show BP properties and stats");
		System.out.println("  show bp statistics                       Show BP Statistics");
		System.out.println("  show bp routes                           Show BP Routes");
		System.out.println("  show bp bundles {-verbose}               Show BP Bundle Retention Queue");
		System.out.println("  show link <linkName>                     Show link");
		System.out.println("  show neighbor <neighborName>             Show neighbor");
		System.out.println("  show saf                                 Show Service Advertisement Framework");
		System.out.println("  show caf                                 Show Connected Apps Framework");
		System.out.println("  show app <appName>                       Show info about given Application");
		System.out.println("  show tcpcl                               Show TcpCl properties and stats");
		System.out.println("  show tcpcl neighbors                     Show all TcpCl Neighbors");
		System.out.println("  show tcpcl links                         Show all TcpCl Links");
		System.out.println("  show tcpcl statistics                    Show TcpCl Statistics");
		System.out.println("  show udpcl                               Show UdpCl properties and stats");
		System.out.println("  show udpcl neighbors                     Show all UdpCl Neighbors");
		System.out.println("  show ucpdl links                         Show all UdpCl Links");
		System.out.println("  show udpcl statistics                    Show UdpCl Statistics");
	}
	
	/**
	 * Execute 'show' command; dispatches to lower level
	 * @param words arguments
	 */
	private void show(String[] words) {
		if (words.length < 2) {
			System.out.println(Management.getInstance().dump("", true));
			return;
		}
		
		String command = getMatchAmongAlts(
				words[1], 
				"all general ltp bp link neighbor saf caf app tcpcl udpcl");
		if (command.equalsIgnoreCase("all")) {
			if (words.length >= 3) {
				String whichAll = getMatchAmongAlts(words[2], "links neighbors");
				if (whichAll.equalsIgnoreCase("links")) {
					showLinks(words);
				} else if (whichAll.equalsIgnoreCase("neighbors")) {
					showNeighbors(words);
				} else {
					System.err.println("Unrecognized 'show all' option: " + words[2]);
				}
				
			} else {
				System.out.println(Management.getInstance().dump("", true));
			}
			
		} else if (command.equalsIgnoreCase("general")) {
			System.out.println(GeneralManagement.getInstance().dump("", true));
			
		} else if (command.equalsIgnoreCase("ltp")) {
			if (words.length >= 3) {
				String showWhat = getMatchAmongAlts(words[2], "statistics links blocks");
				if (showWhat.equalsIgnoreCase("statistics")) {
					System.out.println(LtpManagement.getInstance().getLtpStats().dump("", true));
				} else if (showWhat.equalsIgnoreCase("links")) {
					System.out.println(LinksList.getInstance().dump("", true));
				} else if (showWhat.equalsIgnoreCase("blocks")) {
					boolean verbose = false;
					if (words.length >= 4) {
						String option = getMatchAmongAlts(words[3], "-verbose");
						if (option.equalsIgnoreCase("-verbose")) {
							verbose = true;
						} else {
							System.err.println("Unrecognized option: " + option);
							return;
						}
					}
					System.out.println(LtpManagement.getInstance().dumpBlocks("", verbose));
				} else {
					System.err.println("I don't understand 'show ltp " + showWhat + "'");
				}
			} else {				
				System.out.println(LtpManagement.getInstance().dump("", true));
			}
			
		} else if (command.equalsIgnoreCase("bp")) {
			if (words.length >= 3) {
				String showWhat = getMatchAmongAlts(words[2], "statistics routes bundles");
				if (showWhat.equalsIgnoreCase("statistics")) {
					System.out.println(BPManagement.getInstance().getBpStats().dump("", true));
				} else if (showWhat.equalsIgnoreCase("routes")) {
					System.out.println(RouteTable.getInstance().dump("", true));
				} else if (showWhat.equalsIgnoreCase("bundles")) {
					boolean verbose = false;
					if (words.length >= 4) {
						String option = getMatchAmongAlts(words[3], "-verbose");
						if (option.equalsIgnoreCase("-verbose")) {
							verbose = true;
						} else {
							System.err.println("Unrecognized option: " + option);
							return;
						}
					}
					System.out.println(BPManagement.getInstance().dumpBundles("", verbose));
				} else {
					System.err.println("I don't understand 'show bp " + showWhat + "'");
				}
			} else {
				System.out.println(BPManagement.getInstance().dump("", true));
			}
			
		} else if (command.equalsIgnoreCase("link")) {
			showLink(words);
			
		} else if (command.equalsIgnoreCase("neighbor")) {
			showNeighbor(words);
			
		} else if (command.equalsIgnoreCase("caf")) {
			showCaf(words);
			
		} else if (command.equalsIgnoreCase("saf")) {
			showSaf(words);
			
		} else if (command.equalsIgnoreCase("app")) {
			showApp(words);
			
		} else if (command.equalsIgnoreCase("tcpcl")) {
			showTcpCl(words);
			
		} else if (command.equalsIgnoreCase("udpcl")) {
			showUdpCl(words);
			
		} else {
			System.out.println("Unrecognized 'show' option '" + command + "'");
		}
	}
	
	/**
	 * Execute 'show all links' command; show properties of all links
	 * @param words not used
	 */
	private void showLinks(String[] words) {
		boolean detailed = false;
		if (words.length > 4) {
			String option = getMatchAmongAlts(words[3], "-verbose");
			if (!option.equalsIgnoreCase("-verbose")) {
				System.err.println("Invalid 'show all links' option: " + words[3]);
				return;
			}
		}
		System.out.println(LinksList.getInstance().dump("", detailed));
	}
	
	/**
	 * Execute 'show link" command; show properties of a particular Link
	 * @param words arguments
	 */
	private void showLink(String[] words) {
		// show link <linkName>
		if (words.length < 3) {
			System.err.println("Missing <linkName> argument");
			return;
		}
		String linkName = words[2];
		Link link = LinksList.getInstance().findLinkByName(linkName);
		if (link == null) {
			System.err.println("No such Link: '" + linkName + "'");
			return;
		}
		System.out.println(link.dump("", true));
	}
	
	/**
	 * Execute 'show all neighbors' command; show properties of all neighbors
	 * @param words not used
	 */
	private void showNeighbors(String[] words) {
		boolean detailed = false;
		if (words.length > 4) {
			String option = getMatchAmongAlts(words[3], "-verbose");
			if (!option.equalsIgnoreCase("-verbose")) {
				System.err.println("Invalid 'show all neighbors' option: " + words[3]);
				return;
			}
		}
		System.out.println("All Neighbors");
		for (Neighbor neighbor : NeighborsList.getInstance()) {
			System.out.println(neighbor.dump("", detailed));
		}
	}
	
	/**
	 * Execute 'show neighbor" command; show properties of a particular Neighbor
	 * @param words arguments
	 */
	private void showNeighbor(String[] words) {
		// show neighbor <neighborName>
		// 0    1         2
		if (words.length < 3) {
			System.err.println("Incomplete 'show neighbor <neighborName>' command");
			return;
		}
		String neighborName = words[2];
		Neighbor neighbor = NeighborsList.getInstance().findNeighborByName(neighborName);
		if (neighbor == null) {
			System.err.println("No such neighbor: '" + neighborName + "'");
			return;
		}
		System.out.println(neighbor.dump("", true));
	}
	
	/**
	 * Execute 'show saf' command; show info about Service Advertisement Framework.
	 * @param words arguments
	 */
	private void showSaf(String[] words) {
		SafAdapterApp app = (SafAdapterApp)AppManager.getInstance().getApp(SafAdapterApp.APP_NAME);
		if (app == null) {
			System.err.println("SAF app is not installed");
			return;
		}
		
		System.out.println("Discovered Neighbors");
		for (Neighbor neighbor : NeighborsList.getInstance()) {
			if (neighbor.isTemporary()) {
				System.out.println("  " + neighbor.getEndPointIdStem().getEndPointIdString());
			}
		}
		
		System.out.println("Discovered Services");
		Service subscription = app.getSubscription();
		List<Service> services = subscription.getMatchingServices();
		for (Service service : services) {
			System.out.println(service.toString());
		}
	}
	
	private void showCaf(String[] words) {
		CafAdapterApp app = (CafAdapterApp)AppManager.getInstance().getApp(CafAdapterApp.APP_NAME);
		if (app == null) {
			System.err.println("CAF app is not installed");
			return;
		}
		
		System.out.println("Discovered Neighbors");
		for (Neighbor neighbor : NeighborsList.getInstance()) {
			if (neighbor.isTemporary()) {
				System.out.println("  " + neighbor.getEndPointIdStem().getEndPointIdString());
			}
		}
		
		System.out.println("Discovered Services");
		com.cisco.caf.xmcp.Service subscription = app.getSubscription();
		List<com.cisco.caf.xmcp.Service> services = subscription.getMatchingServices();
		for (com.cisco.caf.xmcp.Service service : services) {
			System.out.println(service.toString());
		}
	}
	
	/**
	 * Execute 'show app' command; show info about Application identified by
	 * given application name.
	 * @param words arguments
	 */
	private void showApp(String[] words) {
		// show app <appName>
		// 0    1   2
		if (words.length < 3) {
			System.err.println("Incomplete 'show app <appName>' command");
			return;
		}
		String appName = words[2];
		AbstractApp app = AppManager.getInstance().getApp(appName);
		if (app == null) {
			System.err.println("No application named '" + appName + "' is installed");
			return;
		}
		System.out.println(app.dump("", true));
	}
	
	/**
	 * Execute 'show tcpcl' command
	 * @param words arguments
	 */
	private void showTcpCl(String[] words) {
		// as a result of getting to this point, words.length >= 2
		if (words.length == 2 || words.length == 3) {
			if (words.length == 2) {
				// show tcpcl
				System.out.println(TcpClManagement.getInstance().dump("", true));
			} else {
				String selector = getMatchAmongAlts(words[2], "neighbors links statistics");
				if (selector.equalsIgnoreCase("neighbors")) {
					// show tcpcl neighbors
					System.out.println(TcpClManagement.getInstance().dumpNeighbors("", true));
					
				} else if (selector.equalsIgnoreCase("links")) {
					// show tcpcl links
					System.out.println(TcpClManagement.getInstance().dumpLinks("", true));
					
				} else if (selector.equalsIgnoreCase("statistics")) {
					// show tcpcl statistics
					System.out.println(TcpClManagement.getInstance().getStatistics().dump("", true));
					
				} else {
					System.err.println("I don't understand 'show tcpcl " + words[2]);
				}
			}
		} else {
			System.err.println("Extraneous argument after 'show tcpcl " + words[3] + "'");
		}
	}
	
	/**
	 * Execute 'show udpcl' command
	 * @param words arguments
	 */
	private void showUdpCl(String[] words) {
		// as a result of getting to this point, words.length >= 2
		if (words.length == 2 || words.length == 3) {
			if (words.length == 2) {
				// show tcpcl
				System.out.println(UdpClManagement.getInstance().dump("", true));
			} else {
				String selector = getMatchAmongAlts(words[2], "neighbors links statistics");
				if (selector.equalsIgnoreCase("neighbors")) {
					// show udpcl neighbors
					System.out.println(UdpClManagement.getInstance().dumpNeighbors("", true));
					
				} else if (selector.equalsIgnoreCase("links")) {
					// show tcpcl links
					System.out.println(UdpClManagement.getInstance().dumpLinks("", true));
					
				} else if (selector.equalsIgnoreCase("statistics")) {
					// show tcpcl statistics
					System.out.println(UdpClManagement.getStatistics().dump("", true));
					
				} else {
					System.err.println("I don't understand 'show udpcl " + words[2]);
				}
			}
		} else {
			System.err.println("Extraneous argument after 'show udpcl " + words[3] + "'");
		}
	}
	
	/**
	 * Show help on 'set' command.  Dispatches to lower leve.
	 * @param words arguments
	 */
	private void helpSet(String[] words) {
		if (words.length > 2) {
			String which = getMatchAmongAlts(
				words[2], 
				"general ltp bp link neighbor tcpcl udpcl");
			if (which.equalsIgnoreCase("general")) {
				helpSetGeneral(words);
				
			} else if (which.equalsIgnoreCase("ltp")) {
				helpSetLtp(words);
				
			} else if (which.equalsIgnoreCase("bp")) {
				helpSetBp(words);
				
			} else if (which.equalsIgnoreCase("link")) {
				helpSetLink(words);
				
			} else if (which.equalsIgnoreCase("neighbor")) {
				helpSetNeighbor(words);
				
			} else if (which.equalsIgnoreCase("tcpcl")) {
				helpSetTcpCl(words);
				
			} else if (which.equalsIgnoreCase("udpcl")) {
				helpSetUdpCl(words);
				
			} else {
				System.err.println("I don't understand 'help set " + which);
			}
		} else {
			System.out.println("set general ...                         Set General Properties");
			System.out.println("set ltp ...                             Set LTP Properties");
			System.out.println("set bp ...                              Set BP Properties");
			System.out.println("set link ...                            Set Link Properties");
			System.out.println("set neighbor ...                        Set Neighbor Properties");
			System.out.println("set tcpcl ...                           Set TCP Convergence Layer Properties");
			System.out.println("set udpcl ...                          set UCP Convergence Layer Properties");
			System.out.println("help set <topic> for more specific info");
		}
	}
	
	/**
	 * Execute 'set' command.  Dispatches to lower level
	 * @param words arguments
	 * @throws InterruptedException if interrupted
	 */
	private void set(String[] words) throws InterruptedException {
		if (words.length < 2) {
			System.err.println("Yeah, but set what?");
			return;
		}
		
		String command = getMatchAmongAlts(
				words[1], 
				"general ltp bp link neighbor tcpcl udpcl");
		if (command.equalsIgnoreCase("general")) {
			setGeneral(words);
			
		} else if (command.equalsIgnoreCase("ltp")) {
			setLtp(words);
			
		} else if (command.equalsIgnoreCase("bp")) {
			setBp(words);
			
		} else if (command.equalsIgnoreCase("link")) {
			setLink(words);
			
		} else if (command.equalsIgnoreCase("neighbor")) {
			setNeighbor(words);
			
		} else if (command.equalsIgnoreCase("tcpcl")) {
			setTcpCl(words);
			
		} else if (command.equalsIgnoreCase("udpcl")) {
			setUdpCl(words);
			
		} else {
			System.out.println("Does 'set " + command + "' make sense to you?");
			System.out.println("No, mean neither");
		}
	}
	
	/**
	 * Provide help for 'set general' command
	 * @param words arguments
	 */
	private void helpSetGeneral(String[] words) {
		System.out.println(" set general storagePath <path>");
		System.out.println(" set general mediaRepository <path>");
		System.out.println(" set general debugLogging <true|false>");
	}
	
	/**
	 * Execute 'set general' command; set general configuration property
	 * @param words arguments
	 */
	private void setGeneral(String[] words) {
		if (words.length < 3) {
			System.out.println("Missing property name");
			return;
		}
		if (words.length < 4) {
			System.out.println("Missing property value");
			return;
		}
		String propName = getMatchAmongAlts(words[2], "storagePath mediaRepository debugLogging");
		if (propName.equalsIgnoreCase("storagePath")) {
			String path = words[3];
			File file = new File(path);
			if (!file.exists()) {
				System.out.println("Path '" + path + "' doesn't exist");
				return;
			}
			if (!file.isDirectory()) {
				System.out.println("Path '" + path + "' is not a directory");
				return;
			}
			System.out.println("Setting storagePath=" + file.getAbsolutePath());
			GeneralManagement.getInstance().setStoragePath(file.getAbsolutePath());
			
		} else if (propName.equalsIgnoreCase("mediaRepository")) {
			String path = words[3];
			File file = new File(path);
			if (!file.exists()) {
				System.out.println("Path '" + path + "' doesn't exist");
				return;
			}
			if (!file.isDirectory()) {
				System.out.println("Path '" + path + "' is not a directory");
				return;
			}
			System.out.println("Setting mediaRepository=" + file.getAbsolutePath());
			GeneralManagement.getInstance().setMediaRepositoryPath(file.getAbsolutePath());
			
		} else if (propName.equalsIgnoreCase("debugLogging")) {
			String boolValue = getMatchAmongAlts(words[3], "true false");
			if (boolValue.equalsIgnoreCase("true")) {
				GeneralManagement.setDebugLogging(true);
			} else if (boolValue.equalsIgnoreCase("false")) {
				GeneralManagement.setDebugLogging(false);
			} else {
				System.err.println("Invalid boolean value: " + boolValue);
			}
			
		} else if (propName.equalsIgnoreCase("mySegmentRateLimit")) {
			double mySegRateLimit = 0.0;
			try {
				mySegRateLimit = Double.parseDouble(words[3]);
			} catch (NumberFormatException e) {
				System.out.println("Invalid double value for 'mySegmentRateLimit'");
				return;
			}
			System.out.println("Setting mySegmentRateLimit=" + mySegRateLimit);
			GeneralManagement.getInstance().setMySegmentRateLimit(mySegRateLimit);
			
		} else if (propName.equalsIgnoreCase("myBurstSize")) {
			long myBurstSize = 0;
			try {
				myBurstSize = Long.parseLong(words[3]);
			} catch (NumberFormatException e) {
				System.out.println("Invalid long value for 'myBurstSize'");
				return;
			}
			System.out.println("Setting myBurstSize=" + myBurstSize);
			GeneralManagement.getInstance().setMyBurstSize(myBurstSize);
			
		} else {
			System.err.println("Bad property name: " + propName);
		}
	}
	
	/**
	 * Provide help for 'set ltp' command
	 * @param words arguments
	 */
	private void helpSetLtp(String[] words) {
		System.out.println(" set ltp engineId <engineId>        : Engine ID for this LTP instance");
		System.out.println(" set ltp maxRetransmits <n>         : Max number of retransmissions for Checkpoints");
		System.out.println(" set ltp maxReportRetransmits <n>   : Max number of retransmissions for Report Segments");
		System.out.println(" set ltp udpPort <n>                : UDP Port number for LTP over UDP");
		System.out.println(" set ltp receiveBuffer <n>          : UDP Receive buffer size");
		System.out.println(" set ltp testInterface <interface>  : Interface name to be used in unit testing");
		System.out.println(" set ltp blockFileThreshold <n>     : Threshold to decide whether Block should be in memory or in a File");
		System.out.println(" set ltp segmentFileThreshold <n>   : Threshold to decide whether Segment should be in memory or in a File");
		System.out.println(" set ltp loglinkOperStateChanges <t/f> : Log (or not) link operational state changes");
	}
	
	/**
	 * Execute 'set ltp' command; set LTP property
	 * @param words arguments
	 */
	private void setLtp(String[] words) {
		if (words.length < 3) {
			System.out.println("Missing property name");
			return;
		}
		if (words.length < 4) {
			System.out.println("Missing property value");
			return;
		}
		String propName = getMatchAmongAlts(
				words[2], 
				"engineId maxRetransmits maxReportRetransmits udpPort " +
				"receiveBuffer testInterface blockFileThreshold " +
				"segmentFileThreshold logLinkOperStateChanges");
		String propValue = words[3];
		if (propName.equalsIgnoreCase("engineId")) {
			EngineId engineId = null;
			try {
				engineId = new EngineId(propValue);
			} catch (LtpException e) {
				System.err.println(e.getMessage());
				return;
			}
			System.out.println("Setting EngineId=" + propValue);
			LtpManagement.getInstance().setEngineId(engineId);
			
		} else if (propName.equalsIgnoreCase("maxRetransmits")) {
			try {
				int iVal = Integer.parseInt(propValue);
				System.out.println("Setting LtpMaxRetransmits=" + iVal);
				LtpManagement.getInstance().setLtpMaxRetransmits(iVal);
				
			} catch (NumberFormatException e) {
				System.err.println("Invalid integer: " + propValue);
			} catch (LtpException e) {
				System.err.println(e.getMessage());
			}
			
		} else if (propName.equalsIgnoreCase("maxReportRetransmits")) {
			try {
				int iVal = Integer.parseInt(propValue);
				System.out.println("Setting maxReportRetransmits=" + iVal);
				LtpManagement.getInstance().setLtpMaxReportRetransmits(iVal);
				
			} catch (NumberFormatException e) {
				System.err.println("Invalid integer: " + propValue);
			} catch (LtpException e) {
				System.err.println(e.getMessage());
			}
			
			
		} else if (propName.equalsIgnoreCase("udpPort")) {
			try {
				int iVal = Integer.parseInt(propValue);
				System.out.println("Setting udpPort=" + iVal);
				LtpManagement.getInstance().setLtpUdpPort(iVal);
				
			} catch (NumberFormatException e) {
				System.err.println("Invalid integer: " + propValue);
			} catch (LtpException e) {
				System.err.println(e.getMessage());
			}
			
		} else if (propName.equalsIgnoreCase("receiveBuffer")) {
			try {
				int iVal = Integer.parseInt(propValue);
				System.out.println("Setting LtpUdpRecvBuffer=" + iVal);
				LtpManagement.getInstance().setLtpUdpRecvBufferSize(iVal);
				
			} catch (NumberFormatException e) {
				System.err.println("Invalid integer: " + propValue);
			} catch (LtpException e) {
				System.err.println(e.getMessage());
			}
			
			
		} else if (propName.equalsIgnoreCase("testInterface")) {
			System.out.println("Setting testInterface=" + propValue);
			LtpManagement.getInstance().setTestInterface(propValue);
			
		} else if (propName.equalsIgnoreCase("blockFileThreshold")) {
			try {
				int iVal = Integer.parseInt(propValue);
				System.out.println("Setting blockLengthFileThreshold=" + iVal);
				LtpManagement.getInstance().setBlockLengthFileThreshold(iVal);
				
			} catch (NumberFormatException e) {
				System.err.println("Invalid integer: " + propValue);
			} catch (LtpException e) {
				System.err.println(e.getMessage());
			}
			
			
		} else if (propName.equalsIgnoreCase("segmentFileThreshold")) {
			try {
				int iVal = Integer.parseInt(propValue);
				System.out.println("Setting segmentLengthFileThreshold=" + iVal);
				LtpManagement.getInstance().setSegmentLengthFileThreshold(iVal);
				
			} catch (NumberFormatException e) {
				System.err.println("Invalid integer: " + propValue);
			} catch (LtpException e) {
				System.err.println(e.getMessage());
			}
			
		} else if (propName.equalsIgnoreCase("logLinkOperStateChanges")) {
			boolean bVal = Boolean.parseBoolean(propValue);
			System.out.println("Setting logLinkOperStateChanges=" + bVal);
			LtpManagement.getInstance().setLogLinkOperStateChanges(bVal);
			
		} else {
			System.err.println("Invalid property name: " + propName);
		}
	}
	
	/**
	 * Provide help for 'set bp' command
	 * @param words arguments
	 */
	private void helpSetBp(String[] words) {
		System.out.println(" set bp bundleFileThreshold <n>     : Threshold above which  block body stored in file rather than in memory");
		System.out.println(" set bp eid <eid>                   : EndPointId stem for all traffic to this BP Node");
		System.out.println(" set bp statusReportsLength <n>     : Max number of items in StatusReports List ");
		System.out.println(" set bp bulkColor <red|green>       : Color for Forwarded Bulk Bundles");
		System.out.println(" set bp normalColor <red|green>     : Color for Forwarded Normal Bundles");
		System.out.println(" set bp expeditedColor <red|green>  : Color for Forwarded Expedited Bundles");
		System.out.println(" set bp scheme {dtn|ipn}            : Global Endpoint ID Scheme");
		System.out.println(" set bp serviceId <n>               : Set the LTP Service access Point for BP");
		System.out.println(" set bp version <n>                 : Set the BP version for outgoing bundles");
		System.out.println(" set bp maxRetainedBytes <n>        : Set Max # retained bytes");
		System.out.println(" set bp holdBundleIfNoRoute <t/f>   : Hold bundle if no route found; false => reject Bundle");
	}
	
	/**
	 * Execute 'set bp' command; set BP property
	 * @param words arguments
	 */
	private void setBp(String[] words) {
		if (words.length < 3) {
			System.out.println("Missing property name");
			return;
		}
		if (words.length < 4) {
			System.out.println("Missing property value");
			return;
		}
		String propName = getMatchAmongAlts(
				words[2], 
				"bundleFileThreshold eid statusReportsLength bulkColor " + 
				"normalColor expeditedColor scheme serviceId version " +
				"maxRetainedBytes holdBundleIfNoRoute");
		String propValue = words[3];
		
		if (propName.equalsIgnoreCase("bundleFileThreshold")) {
			try {
				int iVal = Integer.parseInt(propValue);
				System.out.println("Setting BundleBlockFileThreshold=" + iVal);
				BPManagement.getInstance().setBundleBlockFileThreshold(iVal);
				
			} catch (NumberFormatException e) {
				System.err.println("Invalid integer: " + propValue);
			}			
			
		} else if (propName.equalsIgnoreCase("eid")) {
			try {
				EndPointId eid = EndPointId.createEndPointId(propValue);
				System.out.println("Setting EndPointIdStem=" + propValue);
				BPManagement.getInstance().setEndPointIdStem(eid);
				
			} catch (BPException e) {
				System.err.println(e.getMessage());
			}
			
		} else if (propName.equalsIgnoreCase("statusReportsLength")) {
			try {
				int iVal = Integer.parseInt(propValue);
				System.out.println("Setting BundleStatusReportsListLength=" + iVal);
				BPManagement.getInstance().setBundleStatusReportsListLength(iVal);
				
			} catch (NumberFormatException e) {
				System.err.println("Invalid integer: " + propValue);
			}			
			
		} else if (propName.equalsIgnoreCase("bulkColor")) {
			propValue = getMatchAmongAlts(propValue, "red green");
			System.out.println("Setting bulkColor=" + propValue);
			if (propValue.equalsIgnoreCase("red")) {
				BPManagement.getInstance().setBulkBlockColor(BundleColor.RED);
			} else if (propValue.equalsIgnoreCase("green")) {
				BPManagement.getInstance().setBulkBlockColor(BundleColor.GREEN);
			} else {
				System.err.println("Invalid color: " + propValue);
			}
			
		} else if (propName.equalsIgnoreCase("normalColor")) {
			propValue = getMatchAmongAlts(propValue, "red green");
			System.out.println("Setting normalColor=" + propValue);
			if (propValue.equalsIgnoreCase("red")) {
				BPManagement.getInstance().setNormalBlockColor(BundleColor.RED);
			} else if (propValue.equalsIgnoreCase("green")) {
				BPManagement.getInstance().setNormalBlockColor(BundleColor.GREEN);
			} else {
				System.err.println("Invalid color: " + propValue);
			}

		} else if (propName.equalsIgnoreCase("expeditedColor")) {
			propValue = getMatchAmongAlts(propValue, "red green");
			System.out.println("Setting expeditedColor=" + propValue);
			if (propValue.equalsIgnoreCase("red")) {
				BPManagement.getInstance().setExpeditedBlockColor(BundleColor.RED);
			} else if (propValue.equalsIgnoreCase("green")) {
				BPManagement.getInstance().setExpeditedBlockColor(BundleColor.GREEN);
			} else {
				System.err.println("Invalid color: " + propValue);
			}
		
		} else if (propName.equalsIgnoreCase("scheme")) {
			propValue = getMatchAmongAlts(propValue, "dtn ipn");
			System.out.println("Setting scheme=" + propValue);
			System.out.println("Note that this configuration property is obsolete");
			System.out.println("Now, you should configure EID Schemes on the individual Neighbors");
			if (propValue.equalsIgnoreCase("dtn")) {
				BPManagement.getInstance().setEidScheme(EidScheme.DTN_EID_SCHEME);
			} else if (propValue.equalsIgnoreCase("ipn")) {
				BPManagement.getInstance().setEidScheme(EidScheme.IPN_EID_SCHEME);
			} else {
				System.err.println("Invalid scheme: " + propValue);
			}
			
		} else if (propName.equalsIgnoreCase("serviceId")) {
			try {
				int iVal = Integer.parseInt(propValue);
				System.out.println("Setting serviceId=" + propValue);
				BPManagement.getInstance().setBpServiceId(iVal);
				
			} catch (NumberFormatException e) {
				System.err.println("Invalid integer value: " + propValue);
			}
			
		} else if (propName.equalsIgnoreCase("version")) {
			try {
				int iVal = Integer.parseInt(propValue);
				System.out.println("Setting version=" + propValue);
				BPManagement.getInstance().setOutboundProtocolVersion(iVal);
				
			} catch (NumberFormatException e) {
				System.err.println("Invalid integer value: " + propValue);
			}			
			
		} else if (propName.equalsIgnoreCase("maxRetainedBytes")) {
			try {
				long lVal = Long.parseLong(propValue);
				System.out.println("Setting maxRetainedBytes=" + lVal);
				BPManagement.getInstance().setMaxRetainedBytes(lVal);
			} catch (NumberFormatException e) {
				System.err.println("Invalid long value: " + propValue);
			}
			
		} else if (propName.equalsIgnoreCase("holdBundleIfNoRoute")) {
			boolean bVal = Boolean.parseBoolean(propValue);
			System.out.println("Setting holdBundleIfNoRoute=" + bVal);
			BPManagement.getInstance().setHoldBundleIfNoRoute(bVal);
			
		} else {
			System.err.println("Invalid property name: " + propName);
		}
	}
	
	/**
	 * Provide help for 'set link' command
	 * @param words arguments
	 */
	private void helpSetLink(String[] words) {
		System.out.println(" set link <linkName> adminState up|down");
		System.out.println(" set link <linkName> maxClaimCount <n>");
		System.out.println(" set link <linkName> maxFrameSize <n>");
		System.out.println(" set link <linkName> reportTimeout <n>");
		System.out.println(" set link <linkName> cancelTimeout <n>");
		System.out.println(" set link <linkName> checkpointTimeout <n>");
	}
	
	/**
	 * Execute 'set link' command; set properties on a particular link
	 * @param words arguments
	 */
	private void setLink(String[] words) {
		if (words.length < 5) {
			System.err.println("Incomplete 'set link' command");
			return;
		}
		String linkName = words[2];
		LtpLink link = LtpManagement.getInstance().findLtpLink(linkName);
		if (link == null) {
			System.err.println("No such link: " + linkName);
			return;
		}
		String which = getMatchAmongAlts(
				words[3], 
				"adminState maxClaimCount maxFrameSize reportTimeout checkpointTimeout");
		String value = words[4];
		if (which.equalsIgnoreCase("adminState")) {
			String stateStr = getMatchAmongAlts(value, "up down");
			if (stateStr.equalsIgnoreCase("up")) {
				link.setLinkAdminUp(true);
				
			} else if (stateStr.equalsIgnoreCase("down")) {
				link.setLinkAdminUp(false);
				
			} else {
				System.err.println("Unrecognized adminState value: " + stateStr);
				return;
			}
			
		} else if (which.equalsIgnoreCase("maxClaimCount")) {
			int claimCount = 0;
			try {
				claimCount = Integer.parseInt(value);
				link.setMaxClaimCountPerReport(claimCount);
			} catch (NumberFormatException e) {
				System.err.println("Invalid integer value: " + words[4]);
				return;
			}
			
		} else if (which.equals("maxFrameSize")) {
			if (!(link instanceof LtpUDPLink)) {
				System.err.println("'maxFrameSize' can only be configured for UDPLinks");
				return;
			}
			LtpUDPLink udpLink = (LtpUDPLink)link;
			int maxFrameSize = 0;
			try {
				maxFrameSize = Integer.parseInt(value);
				udpLink.setMaxFrameSize(maxFrameSize);
				
			} catch (NumberFormatException e) {
				System.err.println("Invalid integer value for 'maxFrameSize': " + value);
				return;
			}
			
		} else if (which.equals("reportTimeout")) {
			int reportTimeout = 0;
			try {
				reportTimeout = Integer.parseInt(value);
				link.setReportTimeout(reportTimeout);

			} catch (NumberFormatException e) {
				System.err.println("Invalid integer value for 'reportTimeout': " + value);
				return;
			}
			
		} else if (which.equals("cancelTimeout")) {
			int cancelTimeout = 0;
			try {
				cancelTimeout = Integer.parseInt(value);
				link.setCancelTimeout(cancelTimeout);

			} catch (NumberFormatException e) {
				System.err.println("Invalid integer value for 'cancelTimeout': " + value);
				return;
			}
			
		} else if (which.equals("checkpointTimeout")) {
			int checkpointTimeout = 0;
			try {
				checkpointTimeout = Integer.parseInt(value);
				link.setCancelTimeout(checkpointTimeout);

			} catch (NumberFormatException e) {
				System.err.println("Invalid integer value for 'checkpointTimeout': " + value);
				return;
			}
			
		} else {
			System.err.println("Unrecognized 'set link' option: " + which);
			return;
		}
	}
	
	/**
	 * Provide help for 'set neighbor' command
	 * @param words arguments
	 */
	private void helpSetNeighbor(String[] words) {
		System.out.println(" set neighbor <neighborName> adminState up|down");
		System.out.println(" set neighbor <neighborName> engineId <engineId>");
		System.out.println(" set neighbor <neighborName> scheduledState up|down");
		System.out.println(" set neighbor <neighborName> lightSeconds <lightSecs>");
		System.out.println(" set neighbor <neighborName> roundTripSlop <roundTripSlopMSecs>");
		System.out.println(" set neighbor <neighborName> eid <eid>");
		System.out.println(" set neighbor <neighborName> segmentXmitRateLimit <limit_segs_per_sec>");
		System.out.println(" set neighbor <neighborName> burstSize <segmentBurstSize>");
		System.out.println(" set neighbor <neighborName> scheme <dtn_or_ipn>");
	}
	
	/**
	 * Execute 'set neighbor' command; set property for a particular Neighbor
	 * @param words
	 * @throws InterruptedException if interrupted
	 */
	private void setNeighbor(String[] words) throws InterruptedException {
		// set neighbor <neighborName> <paramName> <paramValue>
		// 0   1         2              3           4
		if (words.length < 5) {
			System.err.println("Incomplete 'set neighbor' command");
			return;
		}
		String neighborName = words[2];
		String which = getMatchAmongAlts(
				words[3], 
				"adminState engineId scheduledState lightSeconds roundTripSlop " +
				"eid scheme segmentXmitRateLimit burstSize");
		String value = words[4];
		
		Neighbor neighbor = NeighborsList.getInstance().findNeighborByName(neighborName);
		if (neighbor == null) {
			System.err.println("No such neighbor: '" + neighborName + "'");
			return;
		}
		
		if (which.equalsIgnoreCase("adminState")) {
			String stateStr = getMatchAmongAlts(value, "up down");
			if (stateStr.equalsIgnoreCase("up")) {
				neighbor.setNeighborAdminUp(true);
				
			} else if (stateStr.equalsIgnoreCase("down")) {
				neighbor.setNeighborAdminUp(false);
				
			} else {
				System.err.println("Unrecognized adminState value: " + stateStr);
				return;
			}
			
		} else if (which.equalsIgnoreCase("engineId")) {
			if (!(neighbor instanceof LtpNeighbor)) {
				System.err.println("Named neighbor is not a Ltp Neighbor");
				return;
			}
			LtpNeighbor ltpNeighbor = (LtpNeighbor)neighbor;
			try {
				EngineId engineId = new EngineId(value);
				ltpNeighbor.setEngineId(engineId);
				
			} catch (LtpException e) {
				System.err.println(e.getMessage());
				return;
			}
			
		} else if (which.equalsIgnoreCase("scheduledState")) {
			String stateStr = getMatchAmongAlts(value, "up down");
			if (stateStr.equalsIgnoreCase("up")) {
				neighbor.setNeighborScheduledUp(true);
				
			} else if (stateStr.equalsIgnoreCase("down")) {
				neighbor.setNeighborScheduledUp(false);
				
			} else {
				System.err.println("Unrecognized scheduledState value: " + stateStr);
				return;
			}
			
		} else if (which.equalsIgnoreCase("lightSeconds")) {
			float lightSecs = 0.0f;
			try {
				lightSecs = Float.parseFloat(value);
				neighbor.setLightDistanceSecs(lightSecs);
				
			} catch (NumberFormatException e) {
				System.err.println("Invalid float value for lightSeconds: " + value);
				return;
			}
			
		} else if (which.equalsIgnoreCase("roundTripSlop")) {
			int slop = 0;
			try {
				slop = Integer.parseInt(value);
				neighbor.setRoundTripSlopMSecs(slop);
				
			} catch (NumberFormatException e) {
				System.err.println("Invalid integer value for roundTripSlop: " + value);
				return;
			}
			
		} else if (which.equalsIgnoreCase("eid")) {
			EndPointId eid = null;
			try {
				eid = EndPointId.createEndPointId(value);
			} catch (BPException e) {
				System.err.println(e.getMessage());
				return;
			}
			neighbor.setEndPointIdStem(eid);
			
		} else if (which.equalsIgnoreCase("segmentXmitRateLimit")) {
			if (!(neighbor instanceof LtpNeighbor)) {
				System.err.println("Named neighbor is not a Ltp Neighbor");
				return;
			}
			LtpNeighbor ltpNeighbor = (LtpNeighbor)neighbor;
			double rateLimit = 0.0d;
			try {
				rateLimit = Double.parseDouble(value);
				ltpNeighbor.setSegmentXmitRateLimit(rateLimit);
			} catch (NumberFormatException ex) {
				System.err.println("Invalid double value for segmentXmitRateLimit: " + value);
				return;
			}
			
		} else if (which.equalsIgnoreCase("burstSize")) {
			if (!(neighbor instanceof LtpNeighbor)) {
				System.err.println("Named neighbor is not a Ltp Neighbor");
				return;
			}
			LtpNeighbor ltpNeighbor = (LtpNeighbor)neighbor;
			long burstSize = 0L;
			try {
				burstSize = Long.parseLong(value);
				ltpNeighbor.setBurstSize(burstSize);
			} catch (NumberFormatException ex) {
				System.err.println("Invalid long value for burstSize: " + value);
				return;
			}
				
		} else if (which.equalsIgnoreCase("scheme")) {
			try {
				EidScheme scheme = EidScheme.parseEidScheme(value);
				neighbor.setEidScheme(scheme);
				
			} catch (BPException e) {
				System.err.println(e.getMessage());
			}
			
		} else {
			System.err.println("Unrecognized 'set neighbor' option: " + words[4]);
			return;
		}
	}
	
	/**
	 * Help for 'set tcpcl' command
	 * @param words args
	 */
	private void helpSetTcpCl(String[] words) {
		System.out.println(" set tcpcl link <linkName> maxSegmentSize <n>");
		System.out.println(" set tcpcl link <linkName> tcpPort <n>");
		System.out.println(" set tcpcl neighbor <neighborName> acks <true|false>");
		System.out.println(" set tcpcl neighbor <neighborName> keepAlives <true|false>");
		System.out.println(" set tcpcl neighbor <neighborName> keepAliveSecs <n>");
		System.out.println(" set tcpcl neighbor <neighborName> delayBeforeReconnect <true|false>");
		System.out.println(" set tcpcl neighbor <neighborName> reconnectDelaySecs <n>");
		System.out.println(" set tcpcl neighbor <neighborName> idleShutdown <true|false>");
		System.out.println(" set tcpcl neighbor <neighborName> idleDelaySecs <n>");
	}
	
	/**
	 * Execute 'set tcpcl' command
	 * @param words args
	 * @throws InterruptedException 
	 */
	private void setTcpCl(String[] words) throws InterruptedException {
		// set tcpcl ...
		if (words.length >= 3) {
			String discrim = getMatchAmongAlts(words[2], "link neighbor");
			if (discrim.equalsIgnoreCase("link")) {
				setTcpClLink(words);
			} else if (discrim.equalsIgnoreCase("neighbor")) {
				setTcpClNeighbor(words);
			} else {
				System.err.println("I don't understand 'set tcpcl " + words[2] + "'");
			}
		} else {
			System.err.println("Missing arguments on 'set tcpcl' command");
		}
	}
	
	/**
	 * Execute 'set tcpcl link' command
	 * @param words args
	 * @throws InterruptedException 
	 */
	private void setTcpClLink(String[] words) throws InterruptedException {
		// set tcpcl link <linkName> <paramName> <paramValue>
		// 0   1     2     3         4           5
		if (words.length != 6) {
			System.err.println("Incorrect number of arguments for 'set tcpcl link ...");
			return;
		}
		String linkName = words[3];
		Link link = LinksList.getInstance().findLinkByName(linkName);
		if (link == null) {
			System.err.println("No Link named '" + linkName + "'");
			return;
		}
		if (!(link instanceof TcpClLink)) {
			System.err.println("Link named '" + linkName + "' is not a TcpCl Link");
			return;
		}
		TcpClLink tcpClLink = (TcpClLink)link;
		String param = getMatchAmongAlts(words[4], "maxSegmentSize tcpPort");
		if (param.equalsIgnoreCase("maxSegmentSize")) {
			// set tcpcl link <linkName> maxSegmentSize <n>");
			// 0   1     2    3          4              5
			try {
				int n = Integer.parseInt(words[5]);
				tcpClLink.setMaxSegmentSize(n);
			} catch (NumberFormatException e) {
				System.err.println("Invalid integer argument: " + words[5]);
				return;
			} catch (IllegalArgumentException e) {
				System.err.println(e.getMessage());
			}
			
		} else if (param.equalsIgnoreCase("tcpPort")) {
			// set tcpcl link <linkName> tcpPort <n>
			// 0   1     2    3          4       5
			try {
				int n = Integer.parseInt(words[5]);
				tcpClLink.setTcpPort(n);
			} catch (NumberFormatException e) {
				System.err.println("Invalid integer argument: " + words[5]);
				return;
			} catch (IllegalArgumentException e) {
				System.err.println(e.getMessage());
			}
			
		} else {
			System.err.println("I don't understand 'set tcpcl link " + linkName + words[4] + "'");
		}
			
	}
	
	/**
	 * Execute 'set tcpcl neighbor' command
	 * @param words args
	 * @throws InterruptedException 
	 */
	private void setTcpClNeighbor(String[] words) throws InterruptedException {
		// set tcpcl neighbor <neighborName> <paramName> <paramValue>
		// 0   1     2         3              4          5
		if (words.length != 6) {
			System.err.println("Incorrect number of arguments for 'set tcpcl neighbor'");
			return;
		}
		String neighborName = words[3];
		Neighbor neighbor = NeighborsList.getInstance().findNeighborByName(neighborName);
		if (neighbor == null) {
			System.err.println("No neighbor named '" + neighborName + "'");
			return;
		}
		if (!(neighbor instanceof TcpClNeighbor)) {
			System.err.println("Neighbor named '" + neighborName + "' is not a TcpCl Neighbor");
			return;
		}
		TcpClNeighbor tcpClNeighbor = (TcpClNeighbor)neighbor;
		String paramName = getMatchAmongAlts(words[4], 
				"acks keepAlives " +
				"keepAliveSecs delayBeforeReconnect reconnectDelaySecs " +
				"idleShutdown idleDelaySecs");
			
		if (paramName.equalsIgnoreCase("acks")) {
			boolean paramValue = Boolean.parseBoolean(words[5]);
			tcpClNeighbor.setAckDataSegments(paramValue);
			
		} else if (paramName.equalsIgnoreCase("keepAlives")) {
			boolean paramValue = Boolean.parseBoolean(words[5]);
			tcpClNeighbor.setKeepAlive(paramValue);
			
		} else if (paramName.equalsIgnoreCase("keepAliveSecs")) {
			try {
				int n = Integer.parseInt(words[5]);
				tcpClNeighbor.setKeepAliveIntervalSecs(n);
			} catch (NumberFormatException e) {
				System.err.println("Invalid keepAliveIntervalSecs: " + words[5]);
			} catch (IllegalArgumentException e) {
				System.err.println(e.getMessage());
			}
			
		} else if (paramName.equalsIgnoreCase("delayBeforeReconnect")) {
			boolean paramValue = Boolean.parseBoolean(words[5]);
			tcpClNeighbor.setDelayBeforeReconnection(paramValue);
			
		} else if (paramName.equalsIgnoreCase("reconnectDelaySecs")) {
			try {
				int n = Integer.parseInt(words[5]);
				tcpClNeighbor.setReconnectionDelaySecs(n);
			} catch (NumberFormatException e) {
				System.err.println("Invalid value for ReconnectDelay: " + words[5]);
			} catch (IllegalArgumentException e) {
				System.err.println(e.getMessage());
			}
			
		} else if (paramName.equalsIgnoreCase("idleShutdown")) {
			boolean paramValue = Boolean.parseBoolean(words[5]);
			tcpClNeighbor.setIdleConnectionShutdown(paramValue);
			
		} else if (paramName.equalsIgnoreCase("idleDelaySecs")) {
			try {
				int n = Integer.parseInt(words[5]);
				tcpClNeighbor.setIdleConnectionShutdownDelaySecs(n);
			} catch (NumberFormatException e) {
				System.err.println("Invalid value for 'idleDelaySecs': " + words[5]);
			} catch (IllegalArgumentException e) {
				System.err.println(e.getMessage());
			}
			
		} else {
			System.err.println("I don't understand 'set tcpcl neighbor " +
					words[4] + " ...");
		}
		 				
	}
	
	/**
	 * Provide help for 'set udpcl' command
	 * @param words arguments
	 */
	private void helpSetUdpCl(String[] words) {
		System.out.println(" set udpcl link <linkName> udpPort <n>");
		System.out.println(" set udpcl neighbor <neighborName> segmentRateLimit <n>");
		System.out.println(" set udpcl neighbor <neighborName> burstSize <n>");
	}
	
	/**
	 * Execute the 'set udpcl' command.  Dispatches for variants.
	 * @param words arguments
	 */
	private void setUdpCl(String[] words) {
		// set udpcl ...
		if (words.length >= 3) {
			String discrim = getMatchAmongAlts(words[2], "link neighbor");
			if (discrim.equalsIgnoreCase("link")) {
				setUdpClLink(words);
			} else if (discrim.equalsIgnoreCase("neighbor")) {
				setUdpClNeighbor(words);
			} else {
				System.err.println("I don't understand 'set udpcl " + words[2] + "'");
			}
		} else {
			System.err.println("Missing arguments on 'set udpcl' command");
		}
	}
	
	/**
	 * Execute the 'set udpcl link' command
	 * @param words arguments
	 */
	private void setUdpClLink(String[] words) {
		// set udpcl link <linkName> <paramName> <paramValue>
		// 0   1     2     3         4           5
		if (words.length != 6) {
			System.err.println("Incorrect number of arguments for 'set udpcl link ...");
			return;
		}
		String linkName = words[3];
		Link link = LinksList.getInstance().findLinkByName(linkName);
		if (link == null) {
			System.err.println("No Link named '" + linkName + "'");
			return;
		}
		if (!(link instanceof UdpClLink)) {
			System.err.println("Link named '" + linkName + "' is not a UdpCl Link");
			return;
		}
		UdpClLink udpClLink = (UdpClLink)link;
		String param = getMatchAmongAlts(words[4], "udpPort");
		if (param.equalsIgnoreCase("udpPort")) {
			// set udpcl link <linkName> udpPort <n>"
			try {
				int udpPort = Integer.parseInt(words[5]);
				udpClLink.setUdpPort(udpPort);
			} catch (NumberFormatException e) {
				System.err.println(
						"Invalid integer udpPort specified: " + 
						words[5]);
				return;
			}
			
		} else {
			System.err.println("Unrecognized option to set udpcl link command: " + 
					words[4]);
			return;
		}
	}
	
	/**
	 * Execute 'set udpcl neighbor' command
	 * @param words arguments
	 */
	private void setUdpClNeighbor(String[] words) {
		// set udpcl neighbor <neighborName> <paramname> <paramValue>
		// 0   1     2        3              4           5
		if (words.length != 6) {
			System.err.println("Incorrect number of arguments for 'set udpcl neighbor'");
			return;
		}
		String neighborName = words[3];
		Neighbor neighbor = NeighborsList.getInstance().findNeighborByName(neighborName);
		if (neighbor == null) {
			System.err.println("No neighbor named '" + neighborName + "'");
			return;
		}
		if (!(neighbor instanceof UdpClNeighbor)) {
			System.err.println("Neighbor named '" + neighborName + "' is not a UdpCl Neighbor");
			return;
		}
		UdpClNeighbor udpClNeighbor = (UdpClNeighbor)neighbor;
		String paramName = getMatchAmongAlts(
				words[4], 
				"segmentRateLimit burstSize");
			
		if (paramName.equalsIgnoreCase("segmentRateLimit")) {
			// set udpcl neighbor <neighborName> segmentRateLimit <n>
			// 0   1     2        3              4                5
			try {
				double segRate = Double.parseDouble(words[5]);
				udpClNeighbor.setSegmentXmitRateLimit(segRate);
			} catch (NumberFormatException e) {
				System.err.println(
						"Invalid double value for 'segmentRateLimit': " +
						words[5]);
			}
			
		} else if (paramName.equalsIgnoreCase("burstSize")) {
			// set udpcl neighbor <neighborName> burstSize <n>
			// 0   1     2        3              4         5
			try {
				long burstSize = Long.parseLong(words[5]);
				udpClNeighbor.setBurstSize(burstSize);
			} catch (NumberFormatException e) {
				System.err.println(
						"Invalid long value for 'burstSize'" +
						words[5]);
			}
		}
	}
	
	/**
	 * Provide help for 'clean' command
	 * @param words Not used
	 */
	private void helpClean(String[] words) {
		System.out.println(" clean                     Clean media repository and bundles");
		System.out.println(" clean media               Clean media repository");
		System.out.println(" clean bundles             Clean bundles");
	}
	
	/**
	 * Execute the 'clean' command; Clean the Media Repository and/or Bundles
	 * @param words arguments
	 */
	private void clean(String[] words) {
		if (words.length <= 1) {
			// clean
			System.out.println("Cleaning media repository");
			MediaRepository.getInstance().clean();
			System.out.println("Cleaning Bundles");
			Store.getInstance().clean();
			return;
		}
		String which = getMatchAmongAlts(words[1], "media bundles");
		if (which.equalsIgnoreCase("media")) {
			System.out.println("Cleaning media repository");
			MediaRepository.getInstance().clean();
		} else if (which.equalsIgnoreCase("bundles")) {
			System.out.println("Cleaning Bundles");
			Store.getInstance().clean();
		} else {
			System.err.println("I don't understand 'clean " + words[1] + "'");
		}
	}
	
	/**
	 * Provide help for 'clear' command'
	 * @param words not used
	 */
	private void helpClear(String[] words) {
		System.out.println(" clear statistics            Clear LTP and BP statistics");
		System.out.println(" clear ltp statistics        Clear LTP statistics");
		System.out.println(" clear bp statistics         Clear BP statistics");
		System.out.println(" clear tcpcl statistics      Clear TcpCl statistics");
		System.out.println(" clear bp statusReports      Clear logged status reports");
		System.out.println(" clear link <linkName>       Clear link statistics");
	}
	
	/**
	 * Execute the 'clear' command; Clear statistics
	 * @param words arguments
	 */
	private void clear(String[] words) {
		if (words.length < 2) {
			System.err.println("Yeah, but clear what?");
			return;
		}
		String which = getMatchAmongAlts(
				words[1], 
				"statistics ltp bp link udpcl tcpcl");
		if (which.equalsIgnoreCase("statistics")) {
			LtpManagement.getInstance().getLtpStats().clear();
			BPManagement.getInstance().getBpStats().clear();
		} else if (which.equalsIgnoreCase("ltp")) {
			clearLtp(words);
		} else if (which.equalsIgnoreCase("bp")) {
			clearBp(words);
		} else if (which.equalsIgnoreCase("link")) {
			clearLink(words);
		} else if (which.equalsIgnoreCase("tcpcl")) {
			clearTcpCl(words);
		} else if (which.equalsIgnoreCase("udpcl")) {
			clearUdpCl(words);
		} else {
			System.err.println("Invalid 'clear' option");
		}
	}
	
	/**
	 * Execute 'clear ltp' command
	 * @param words arguments
	 */
	private void clearLtp(String[] words) {
		if (words.length < 3) {
			System.err.println("Missing option from 'clear ltp' command");
			return;
		}
		String which = getMatchAmongAlts(words[2], "statistics");
		if (which.equalsIgnoreCase("statistics")) {
			LtpManagement.getInstance().getLtpStats().clear();
		} else {
			System.err.println("Invalid 'clear ltp' option: " + which);
		}
	}
	
	/**
	 * Execute 'clear bp' command
	 * @param words arguments
	 */
	private void clearBp(String[] words) {
		if (words.length < 3) {
			System.err.println("Missing option from 'clear bp' command");
			return;
		}
		String which = getMatchAmongAlts(words[2], "statistics statusReports");
		if (which.equalsIgnoreCase("statistics")) {
			BPManagement.getInstance().getBpStats().clear();
		} else if (which.equalsIgnoreCase("statusReports")) {
			BPManagement.getInstance().clearBundleStatusReportsList();
		} else {
			System.err.println("Invalid 'clear bp' option: " + which);
		}
	}
	
	/**
	 * Execute 'clear ltpcl' command
	 * @param words arguments
	 */
	private void clearTcpCl(String[] words) {
		if (words.length != 3) {
			System.err.println("Incorrect number of arguments to 'clear tcpcl' command");
			return;
		}
		String which = getMatchAmongAlts(words[2], "statistics");
		if (which.equalsIgnoreCase("statistics")) {
			TcpClManagement.getInstance().clearStatistics();
		} else {
			System.err.println("Invalid 'clear tcpcl' option: " + which);
		}
	}
	
	/**
	 * Execute 'clear udpcl' command
	 * @param words arguments
	 */
	private void clearUdpCl(String[] words) {
		if (words.length != 3) {
			System.err.println("Incorrect number of arguments to 'clear udpcl' command");
			return;
		}
		String which = getMatchAmongAlts(words[2], "statistics");
		if (which.equalsIgnoreCase("statistics")) {
			UdpClManagement.getInstance().clearStatistics();
		} else {
			System.err.println("Invalid 'clear udpcl' option: " + which);
		}
	}
		
	/**
	 * Execute 'clear link' command
	 * @param words arguments
	 */
	private void clearLink(String[] words) {
		// clear link <linkName>
		if (words.length < 3) {
			System.err.println("Missing <linkName> from 'clear link' command");
			return;
		}
		String linkName = words[2];
		LtpLink link = LtpManagement.getInstance().findLtpLink(linkName);
		if (link == null) {
			System.err.println("No such link: '" + linkName + "'");
			return;
		}
		link.clearStatistics();
	}
	
	/**
	 * Provide help for 'add' command
	 * @param words arguments
	 */
	private void helpAdd(String[] words) {
		System.out.println(" add link <linkName> <ifName> {ipv6}                     : Add LTP Link");
		System.out.println(" add neighbor <nName> {<EngineId>}                       : Add LTP Neighbor");
		System.out.println(" add neighbor <nName> -link <lName> <ipAddress>          : Add Link and IPAddress to Neighbor");
		System.out.println(" add route <eidPattern> <name> <linkName> <neighborName> : Add BP Route");
		System.out.println(" add defaultRoute <name> <linkName> <neighborName>       : Add BP Default Route");
		System.out.println(" add application <name> <className> <appArg>...          : Add Application");
		System.out.println(" add tcpcl link <lName> <ifName> {ipv6}                  : Add TcpCl Link");
		System.out.println(" add tcpcl neighbor <nName> <EndPointId>                 : Add TcpCl Neighbor");
		System.out.println(" add tcpcl neighbor <nName> -link <lName> <ipAddress>    : Add Link and IPAddress to Neighbor");
		System.out.println(" add udpcl link <lName> <ifName> {ipv6}                  : Add UdpCl Link");
		System.out.println(" add udpcl neighbor <nName> <EndPointId>                 : Add UdpCl Neighbor");
		System.out.println(" add udpcl neighbor <nName> -link <lName> <ipAddress>    : Add Link and IPAddress to Neighbor");
		System.out.println(" add eidMap <dtnEid> <ipnEid>                            : Add ipn <=> dtn EndPointId Mapping");
	}
	
	/**
	 * Execute the 'add' command; dispatches to lower level
	 * @param words arguments
	 */
	private void add(String[] words) {
		if (words.length < 2) {
			System.err.println("Missing {link|neighbor|route|application|etc");
			return;
		}
		String which = getMatchAmongAlts(
				words[1], 
				"link neighbor route defaultRoute application tcpcl udpcl eidMap");
		
		if (which.equalsIgnoreCase("link")) {
			addLink(words);
			
		} else if (which.equalsIgnoreCase("neighbor")) {
			addNeighborVariations(words);
			
		} else if (which.equalsIgnoreCase("route")) {
			addRoute(words);
			
		} else if (which.equalsIgnoreCase("defaultRoute")) {
			addDefaultRoute(words);
			
		} else if (which.equalsIgnoreCase("application")) {
			addApplication(words);
		
		} else if (which.equalsIgnoreCase("tcpcl")) {
			addTcpCl(words);
			
		} else if (which.equalsIgnoreCase("udpcl")) {
			addUdpCl(words);
			
		} else if (which.equalsIgnoreCase("eidMap")) {
			addEidMap(words);
			
		} else {
			System.err.println("I'm not sure what you want to add: '" + which + "' is not in my vocabulary");
		}
	}

	/**
	 * Execute the 'add application' command; add a particular application
	 * @param words arguments
	 */
	private void addApplication(String[] words) {
		// add application <name> <className> <arg>...
		if (words.length < 4) {
			System.err.println("Missing arguments in 'add application' command");
			return;
		}
		String appName = words[2];
		String appClassName = words[3];
		int nArgs = words.length - 4;
		String[] args = null;
		if (nArgs > 0) {
			args = new String[nArgs];
			for (int ix = 4; ix < words.length; ix++) {
				args[ix - 4] = words[ix];
			}
		}
		try {
			GeneralManagement.getInstance().addApplication(appName, appClassName, args);
		} catch (JDtnException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
			return;
		}
	}

	/**
	 * Execute the 'add defaultRoute' command; add the default route
	 * @param words arguments
	 */
	private void addDefaultRoute(String[] words) {
		// add defaultRoute <name> <linkName> <neighborName>
		if (words.length < 5) {
			System.err.println("Missing arguments in 'add defaultRoute' command");
			return;
		}
		String routeName = words[2];
		String linkName = words[3];
		String neighborName = words[4];
		
		try {
			System.out.println("Adding DefaultRoute" +
						" routeName=" + routeName +
						", linkName=" + linkName +
						", neighborName=" + neighborName);
			BPManagement.getInstance().setDefaultRoute(routeName, linkName, neighborName);
		} catch (BPException e) {
			System.err.println(e.getMessage());
			return;
		}
	}

	/**
	 * Execute the 'add route' command; add a new route
	 * @param words arguments
	 */
	private void addRoute(String[] words) {
		// add route <eidPattern> <name> <linkName> <neighborName>
		if (words.length < 6) {
			System.err.println("Missing arguments in 'add route' command");
			return;
		}
		String eidPattern = words[2];
		String routeName = words[3];
		String linkName = words[4];
		String neighborName = words[5];
		
		try {
			System.out.println("Adding Route" +
					" routeName=" + routeName +
					", eidPattern=" + eidPattern +
					", linkName=" + linkName +
					", neighborName=" + neighborName);
			BPManagement.getInstance().addRoute(routeName, eidPattern, linkName, neighborName);

		} catch (BPException e) {
			System.err.println(e.getMessage());
			return;
		}
	}

	/**
	 * Parses and dispatches variations on 'add neighbor' command
	 * @param words command arguments
	 */
	private void addNeighborVariations(String[] words) {
		// add neighbor <nName> <ipAddress> {<engineId>}
		// 0   1         2       3           4
		// or
		// add neighbor <nName> -link <lName> <ipAddress>
		// 0   1         2       3     4       5
		if (words.length < 4) {
			System.err.println("Missing arguments from 'add neighbor' command");
			return;
		}
		String option = getMatchAmongAlts(words[3], "-link", true);
		if (option.equalsIgnoreCase("-link")) {
			addLinkToNeighbor(words);
		} else {
			addNeighbor(words);
		}
		
	}

	/**
	 * Execute the 'add neighbor' command; add a new neighbor
	 * @param words arguments
	 */
	private void addNeighbor(String[] words) {
		// add neighbor <nName> {<EngineId>}
		// 0   1         2        3
		if (words.length < 3) {
			System.err.println("Missing arguments from 'add neighbor' command");
			return;
		}
		String neighborName = words[2];
		EngineId engineId = null;
		if (words.length >= 4) {
			try {
				engineId = new EngineId(words[3]);
			} catch (LtpException e) {
				System.err.println(e.getMessage());
				return;
			}
		} else {
			engineId = new EngineId();
		}
		try {
			System.out.println("Adding Neighbor" +
					", engineId=" + engineId.getEngineIdString() +
					", neighborName=" + neighborName);
			LtpManagement.getInstance().addUDPNeighbor(engineId, neighborName);
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
	}
	
	/**
	 * Execute 'add neighbor <nName> -link' command.  Add Link to Neighbor.
	 * @param words arguments
	 */
	private void addLinkToNeighbor(String[] words) {
		// add neighbor <nName> -link <lName> <ipAddress>
		// 0   1         2       3     4       5
		if (words.length < 6) {
			System.err.println("Incomplete 'add neighbor <nName> -link command");
			return;
		}
		String nName = words[2];
		String lName = words[4];
		String ipAddrStr = words[5];
		
		IPAddress ipAddr = null;
		try {
			ipAddr = new IPAddress(ipAddrStr);
		} catch (UnknownHostException e) {
			System.err.println("Invalid IP Address: " + e.getMessage());
			return;
		}
		
		LtpNeighbor neighbor = LtpManagement.getInstance().findNeighbor(nName);
		if (neighbor == null) {
			System.err.println("No such neighbor: " + nName);
			return;
		}
		LtpLink link = LtpManagement.getInstance().findLtpLink(lName);
		if (link == null) {
			System.err.println("No such link: " + lName);
			return;
		}
		LinkAddress linkAddress = new LinkAddress(link, ipAddr);
		neighbor.addLinkAddress(linkAddress);
	}
	
	/**
	 * Execute the 'add link' command; add a new Link
	 * @param words arguments
	 */
	private void addLink(String[] words) {
		// add link <linkName> <ifName> {ipv6}
		if (words.length < 4) {
			System.err.println("Missing arguments from 'add link' command");
			return;
		}
		String linkName = words[2];
		String ifName = words[3];
		boolean wantIpv6 = false;
		if (words.length >= 5) {
			if (words[4].equalsIgnoreCase("ipv6")) {
				wantIpv6 = true;
			} else {
				System.err.println("Expected 'ipv6': " + words[4]);
				return;
			}
		}
		try {
			System.out.println(
					"Adding Link linkName=" + linkName + 
					", ifName=" + ifName + 
					", wantIpv6=" + wantIpv6);
			LtpManagement.getInstance().addUDPLink(linkName, ifName, wantIpv6);
		} catch (LtpException e) {
			System.err.println(e.getMessage());
			return;
		}
	}
	
	/**
	 * Execute the 'add tcpcl ..." command
	 * @param words arguments
	 */
	private void addTcpCl(String[] words) {
		// add tcpcl link ...
		// OR
		// add tcpcl neighbor ...
		if (words.length >= 3) {
			String which = getMatchAmongAlts(words[2], "link neighbor");
			if (which.equalsIgnoreCase("link")) {
				addTcpClLink(words);
			} else if (which.equalsIgnoreCase("neighbor")) {
				addTcpClNeighborVariations(words);
				
			} else {
				System.err.println("I don't understand 'add tcpcl " + words[2] + "'");
			}
					
		} else {
			System.err.println("Insufficient number of arguments for 'add tcpcl' command");
		}
	}
	
	/**
	 * Execute the 'add tcpcl link ..." command
	 * @param words arguments
	 */
	private void addTcpClLink(String[] words) {
		// add tcpcl link <lName> <ifName> {ipv6}
		// 0   1     2    3       4        5
		if (words.length == 4 || words.length == 5) {
			String linkName = words[3];
			if (LinksList.getInstance().findLinkByName(linkName) != null) {
				System.err.println("Already a Link named '" + linkName + "'");
				return;
			}
			String ifName = words[4];
			boolean wantIpv6 = false;
			if (words.length == 6) {
				if (words[5].equalsIgnoreCase("ipv6")) {
					wantIpv6 = true;
				} else {
					System.err.println("Expected 'ipv6': " + words[5]);
					return;
				}
			}
			try {
				System.out.println(
						"Adding Link linkName=" + linkName + 
						", ifName=" + ifName + 
						", wantIpv6=" + wantIpv6);
				TcpClManagement.getInstance().addLink(linkName, ifName, wantIpv6);
			} catch (JDtnException e) {
				System.err.println(e.getMessage());
				return;
			}
			
		} else {
			System.err.println("Wrong number of arguments for 'add tcpcl link' command");
		}
	}
	
	/**
	 * Execute the 'add tcpcl neighbor ..." command
	 * @param words arguments
	 */
	private void addTcpClNeighborVariations(String[] words) {
		// At this point, we're guaranteed length >= 3
		// add tcpcl neighbor <nName> <eid>
		// 0   1     2        3       4
		// OR
		// add tcpcl neighbor <nName> -link <lName> <ipAddress>
		// 0   1     2        3       4     5        6
		if (words.length < 5) {
			System.err.println("Missing arguments from 'add tcpcl neighbor' command");
			return;
		}
		String neighborName = words[3];
		if (!words[4].equalsIgnoreCase("-link")) {
			// add tcpcl neighbor <nName> <eid>
			// 0   1     2        3       4
			if (words.length != 5) {
				System.err.println(
						"Incorrect number of arguments for 'add tcpcl neighbor " +
						"<nName> <eid>");
			}
			try {
				EndPointId eid = EndPointId.createEndPointId(words[4]);
				System.out.println(
						"Adding Neighbor neighborName=" + neighborName + 
						" eid=" + eid);
				TcpClManagement.getInstance().addNeighbor(neighborName, eid);
			} catch (BPException e) {
				System.err.println(e.getMessage());
			} catch (JDtnException e) {
				System.err.println(e.getMessage());
			}
		} else if (words.length == 7) {
			// add tcpcl neighbor <nName> -link <lName> <ipAddress>
			// 0   1     2        3       4     5        6
			String linkName = words[5];
			Link link = LinksList.getInstance().findLinkByName(linkName);
			if (link == null) {
				System.err.println("No Link named '" + linkName + "'");
				return;
			}
			IPAddress ipAddress = null;
			try {
				ipAddress = new IPAddress(words[6]);
			} catch (UnknownHostException e) {
				System.err.println("Unknown host or bad IPAddress: " + words[6]);
				return;
			}
			Neighbor neighbor =
				NeighborsList.getInstance().findNeighborByName(neighborName);
			if (neighbor == null) {
				System.err.println("No Neighbor named '" + neighborName + "'");
				return;
			}
			LinkAddress linkAddress = new LinkAddress(link, ipAddress);
			System.out.println(
					"Adding LinkAddress link=" + linkName + 
					" address=" + ipAddress + 
					" to Neighbor " + neighborName);
			neighbor.addLinkAddress(linkAddress);
		}
			
	}
	
	/**
	 * Execute the 'add udpcl' command.  Dispatch for variations.
	 * @param words arguments
	 */
	private void addUdpCl(String[] words) {
		// add udpcl link ...
		// OR
		// add udpcl neighbor ...
		if (words.length >= 3) {
			String which = getMatchAmongAlts(words[2], "link neighbor");
			if (which.equalsIgnoreCase("link")) {
				addUdpClLink(words);
			} else if (which.equalsIgnoreCase("neighbor")) {
				addUdpClNeighborVariations(words);
				
			} else {
				System.err.println("I don't understand 'add udpcl " + words[2] + "'");
			}
					
		} else {
			System.err.println("Insufficient number of arguments for 'add udpcl' command");
		}
	}
	
	/**
	 * Execute the 'add udpcl link' command.
	 * @param words arguments
	 */
	private void addUdpClLink(String[] words) {
		// add udpcl link <lName> <ifName> {ipv6}
		// 0   1     2    3       4        5
		if (words.length == 4 || words.length == 5) {
			String linkName = words[3];
			if (LinksList.getInstance().findLinkByName(linkName) != null) {
				System.err.println("Already a Link named '" + linkName + "'");
				return;
			}
			String ifName = words[4];
			boolean wantIpv6 = false;
			if (words.length == 6) {
				if (words[5].equalsIgnoreCase("ipv6")) {
					wantIpv6 = true;
				} else {
					System.err.println("Expected 'ipv6': " + words[5]);
					return;
				}
			}
			try {
				System.out.println(
						"Adding Link linkName=" + linkName + 
						", ifName=" + ifName + 
						", wantIpv6=" + wantIpv6);
				UdpClManagement.getInstance().addLink(linkName, ifName, wantIpv6);
			} catch (JDtnException e) {
				System.err.println(e.getMessage());
				return;
			}
			
		} else {
			System.err.println("Wrong number of arguments for 'add tcpcl link' command");
		}
	}
	
	/**
	 * Execute the 'add udpcl neighbor' command
	 * @param words arguments
	 */
	private void addUdpClNeighborVariations(String[] words) {
		// At this point, we're guaranteed length >= 3
		// add udpcl neighbor <nName> <eid>
		// 0   1     2        3       4
		// OR
		// add udpcl neighbor <nName> -link <lName> <ipAddress>
		// 0   1     2        3       4     5        6
		if (words.length < 5) {
			System.err.println("Missing arguments from 'add udpcl neighbor' command");
			return;
		}
		String neighborName = words[3];
		if (neighborName.equals("-link")) {
			System.err.println("You forgot to include Neighbor Name before '-link' modifier");
			return;
		}
		if (!words[4].equalsIgnoreCase("-link")) {
			// add udpcl neighbor <nName> <eid>
			// 0   1     2        3       4
			if (words.length != 5) {
				System.err.println(
						"Incorrect number of arguments for 'add udpcl neighbor " +
						"<nName> <eid>");
				return;
			}
			try {
				EndPointId eid = EndPointId.createEndPointId(words[4]);
				System.out.println(
						"Adding Neighbor neighborName=" + neighborName + 
						" eid=" + eid);
				UdpClManagement.getInstance().addNeighbor(neighborName, eid);
			} catch (BPException e) {
				System.err.println(e.getMessage());
			} catch (JDtnException e) {
				System.err.println(e.getMessage());
			}
		} else if (words.length == 7) {
			// add udpcl neighbor <nName> -link <lName> <ipAddress>
			// 0   1     2        3       4     5        6
			String linkName = words[5];
			Link link = LinksList.getInstance().findLinkByName(linkName);
			if (link == null) {
				System.err.println("No Link named '" + linkName + "'");
				return;
			}
			IPAddress ipAddress = null;
			try {
				ipAddress = new IPAddress(words[6]);
			} catch (UnknownHostException e) {
				System.err.println("Unknown host or bad IPAddress: " + words[6]);
				return;
			}
			Neighbor neighbor =
				NeighborsList.getInstance().findNeighborByName(neighborName);
			if (neighbor == null) {
				System.err.println("No Neighbor named '" + neighborName + "'");
				return;
			}
			LinkAddress linkAddress = new LinkAddress(link, ipAddress);
			System.out.println(
					"Adding LinkAddress link=" + linkName + 
					" address=" + ipAddress + 
					" to Neighbor " + neighborName);
			neighbor.addLinkAddress(linkAddress);
		}
	}
	
	/**
	 * Execute 'add eidMap' command
	 * @param words arguments
	 */
	private void addEidMap(String[] words) {
		// add eidMap <dtnEid> <ipnEid>
		// 0   1      2        3
		if (words.length != 4) {
			System.err.println("Incorrect number of arguments");
			return;
		}
		try {
			EidMap.getInstance().addMapping(words[2], words[3]);
		} catch (BPException e) {
			System.err.println(e.getMessage());
		}
	}
	
	/**
	 * Provide help for 'remove' command
	 * @param words arguments
	 */
	private void helpRemove(String[] words) {
		System.out.println(" remove link <linkName>                      : Remove LTP Link");
		System.out.println(" remove neighbor <neighborName>              : Remove LTP Neighbor");
		System.out.println(" remove route <routeName>                    : Remove BP Route");
		System.out.println(" remove defaultRoute                         : Remove BP Default Route");
		System.out.println(" remove application <name>                   : Remove application");
		System.out.println(" remove ltpcl link <linkName>                : Remove TcpCl Link");
		System.out.println(" remove ltpcl neighbor <neighborName>        : Remove TcpCl Neighbor");
		System.out.println(" remove eidMap <dtnEid>                      : Remove dtn <=> ipn EndPointId Mapping");
	}
	
	/**
	 * Execute the 'remove' command; dispatch to lower level
	 * @param words arguments
	 * @throws InterruptedException 
	 */
	private void remove(String[] words) throws InterruptedException {
		if (words.length < 2) {
			System.err.println("Yeah, but what do you want to delete?");
			return;
		}
		String which = getMatchAmongAlts(words[1], 
				"link neighbor route defaultRoute application tcpcl udpcl eidMap");
		
		if (which.equalsIgnoreCase("link")) {
			removeLink(words);
			
		} else if (which.equalsIgnoreCase("neighbor")) {
			removeNeighbor(words);
			
		} else if (which.equalsIgnoreCase("route")) {
			removeRoute(words);
			
		} else if (which.equalsIgnoreCase("defaultRoute")) {
			System.out.println("Removing BP Default Route");
			BPManagement.getInstance().removeDefaultRoute();
			
		} else if (which.equalsIgnoreCase("application")) {
			removeApplication(words);
			
		} else if (which.equalsIgnoreCase("tcpcl")) {
			removeTcpCl(words);
			
		} else if (which.equalsIgnoreCase("udpcl")) {
			removeUdpCl(words);
			
		} else if (which.equalsIgnoreCase("eidMap")) {
			removeEidMap(words);
			
		} else {
			System.err.println("remove '" +	which + "' is not in my vocabulary");
		}
			
	}

	/**
	 * Execute the 'remove application' command; remove a previously installed
	 * application.
	 * @param words arguments
	 * @throws InterruptedException 
	 */
	private void removeApplication(String[] words) throws InterruptedException {
		// remove application <name>
		if (words.length < 3) {
			System.err.println("Missing '<name>' argument");
			return;
		}
		String appName = words[2];
		try {
			GeneralManagement.getInstance().removeApplication(appName);
		} catch (JDtnException e) {
			System.err.println(e.getMessage());
			return;
		}
	}

	/**
	 * Execute the 'remove route' command; remove a previously installed
	 * route.
	 * @param words arguments
	 */
	private void removeRoute(String[] words) {
		// remove route <routeName>
		if (words.length < 3) {
			System.err.println("Missing arguments from 'remove route' command");
			return;
		}
		String routeName = words[2];
		try {
			System.out.println("Removing route routeName=" + routeName);
			BPManagement.getInstance().removeRoute(routeName);
		} catch (BPException e) {
			System.err.println(e.getMessage());
		}
	}

	/**
	 * Execute the 'remove neighbor' command; remove a previously installed
	 * neighbor
	 * @param words arguments
	 */
	private void removeNeighbor(String[] words) {
		// remove neighbor <neighborName>
		// 0      1         2
		if (words.length < 3) {
			System.err.println("Missing arguments from 'remove neighbor' command");
			return;
		}
		String neighborName = words[2];
		LtpNeighbor neighbor = LtpManagement.getInstance().findNeighbor(neighborName);
		if (neighbor == null || !(neighbor instanceof LtpUDPNeighbor)) {
			System.err.println("No such neighbor:  " + neighborName);
			return;
		}
		try {
			System.out.println("Removing Neighbor" +
					" neighborName=" + neighbor.getName());
			LtpManagement.getInstance().removeUDPNeighbor((LtpUDPNeighbor)neighbor);
		} catch (JDtnException e) {
			System.err.println(e.getMessage());
		}
	}

	/**
	 * Execute the 'remove link' command; remove a previously installed Link.
	 * @param words
	 * @throws InterruptedException 
	 */
	private void removeLink(String[] words) throws InterruptedException {
		// remove link <linkName>
		if (words.length < 3) {
			System.err.println("Missing arguments from 'remove link' command");
			return;
		}
		String linkName = words[2];
		LtpLink link = LtpManagement.getInstance().findLtpLink(linkName);
		if (link == null || !(link instanceof LtpUDPLink)) {
			System.err.println("No such Link: " + linkName);
			return;
		}
		try {
			System.out.println("Removing Link linkName=" + linkName);
			LtpManagement.getInstance().removeLink(link);
		} catch (JDtnException e) {
			System.err.println(e.getMessage());
			return;
		}
	}
	
	/**
	 * Execute the 'remove tcpcl' command
	 * @param words arguments
	 * @throws InterruptedException 
	 */
	private void removeTcpCl(String[] words) throws InterruptedException {
		// remove tcpcl neighbor <neighborName>
		// remove tcpcl link     <linkName>
		// 0      1     2        3
		if (words.length != 4) {
			System.err.println("Incorrect number of arguments for 'remove tcpcl' command");
			return;
		}
		String which = getMatchAmongAlts(words[2], "neighbor link");
		if (which.equalsIgnoreCase("link")) {
			String linkName = words[3];
			Link link = LinksList.getInstance().findLinkByName(linkName);
			if (link == null) {
				System.err.println("No Link named '" + linkName + "'");
				return;
			}
			try {
				TcpClManagement.getInstance().removeLink(linkName);
			} catch (JDtnException e) {
				System.err.println(e.getMessage());
			}
			
		} else if (which.equalsIgnoreCase("neighbor")) {
			String neighborName = words[3];
			Neighbor neighbor = 
				NeighborsList.getInstance().findNeighborByName(neighborName);
			if (neighbor == null) {
				System.err.println("No Neighbor named '" + neighborName + "'");
				return;
			}
			try {
				TcpClManagement.getInstance().removeNeighbor(neighborName);
			} catch (JDtnException e) {
				System.err.println(e.getMessage());
			}
		}
	}
	
	/**
	 * Execute the 'remove udpcl' command
	 * @param words arguments
	 * @throws InterruptedException 
	 */
	private void removeUdpCl(String[] words) throws InterruptedException {
		// remove udpcl neighbor <neighborName>
		// remove udpcl link     <linkName>
		// 0      1     2        3
		if (words.length != 4) {
			System.err.println("Incorrect number of arguments for 'remove udpcl' command");
			return;
		}
		String which = getMatchAmongAlts(words[2], "neighbor link");
		if (which.equalsIgnoreCase("link")) {
			String linkName = words[3];
			Link link = LinksList.getInstance().findLinkByName(linkName);
			if (link == null) {
				System.err.println("No Link named '" + linkName + "'");
				return;
			}
			try {
				UdpClManagement.getInstance().removeLink(linkName);
			} catch (JDtnException e) {
				System.err.println(e.getMessage());
			}
			
		} else if (which.equalsIgnoreCase("neighbor")) {
			String neighborName = words[3];
			Neighbor neighbor = 
				NeighborsList.getInstance().findNeighborByName(neighborName);
			if (neighbor == null) {
				System.err.println("No Neighbor named '" + neighborName + "'");
				return;
			}
			try {
				UdpClManagement.getInstance().removeNeighbor(neighborName);
			} catch (JDtnException e) {
				System.err.println(e.getMessage());
			}
		}
	}
	
	/**
	 * Execute 'remove eidMap' command
	 * @param words arguments
	 */
	private void removeEidMap(String[] words) {
		// remove eidMap <dtnEid>
		// 0      1      2
		if (words.length != 3) {
			System.err.println("Incorrect number of arguments");
			return;
		}
		try {
			EidMap.getInstance().removeMapping(words[2]);
		} catch (BPException e) {
			System.err.println(e.getMessage());
		}
	}
	
	/**
	 * Display help for 'start' command
	 * @param words not used
	 */
	private void helpStart(String[] words) {
		System.out.println("start tcpcl link <linkName>");
		System.out.println("start tcpcl neighbor <neighborName>");
		System.out.println("start udpcl link <linkName>");
		System.out.println("start udpcl neighbor <neighborName>");
	}
	
	/**
	 * Execute 'start' command
	 * @param words arguments
	 */
	private void start(String[] words) {
		// start tcpcl link     <linkName>
		// start tcpcl neighbor <neighborName>
		// start udpcl link     <linkName>
		// start udpcl neighbor <neighborName>
		// 0     1     2        3
		if (words.length != 4) {
			System.err.println("Incorrect number of options for 'start' command");
			return;
		}
		String which = getMatchAmongAlts(words[1], "tcpcl udpcl", true);
		if (which.equalsIgnoreCase("tcpcl")) {
			which = getMatchAmongAlts(words[2], "link neighbor");
			if (which.equalsIgnoreCase("link")) {
				String linkName = words[3];
				Link link = LinksList.getInstance().findLinkByName(linkName);
				if (link == null) {
					System.err.println("No Link named '" + linkName + "'");
					return;
				}
				if (!(link instanceof TcpClLink)) {
					System.err.println("Link '" + linkName + "' is not a TcpCl link");
					return;
				}
				TcpClLink tcpClLink = (TcpClLink)link;
				tcpClLink.start();
				
			} else if (which.equalsIgnoreCase("neighbor")) {
				String neighborName = words[3];
				Neighbor neighbor = NeighborsList.getInstance().findNeighborByName(neighborName);
				if (neighbor == null) {
					System.err.println("No Neighbor named '" + neighborName + "'");
					return;
				}
				if (!(neighbor instanceof TcpClNeighbor)) {
					System.err.println("Neighbor '" + neighborName + "' is not a TcpCl Neighbor");
					return;
				}
				TcpClNeighbor tcpClNeighbor = (TcpClNeighbor)neighbor;
				tcpClNeighbor.start();
			}
		} else if (which.equalsIgnoreCase("udpcl")) {
			which = getMatchAmongAlts(words[2], "link neighbor");
			if (which.equalsIgnoreCase("link")) {
				String linkName = words[3];
				Link link = LinksList.getInstance().findLinkByName(linkName);
				if (link == null) {
					System.err.println("No Link named '" + linkName + "'");
					return;
				}
				if (!(link instanceof UdpClLink)) {
					System.err.println("Link '" + linkName + "' is not a UdpCl link");
					return;
				}
				UdpClLink udpClLink = (UdpClLink)link;
				udpClLink.start();
				
			} else if (which.equalsIgnoreCase("neighbor")) {
				String neighborName = words[3];
				Neighbor neighbor = NeighborsList.getInstance().findNeighborByName(neighborName);
				if (neighbor == null) {
					System.err.println("No Neighbor named '" + neighborName + "'");
					return;
				}
				if (!(neighbor instanceof UdpClNeighbor)) {
					System.err.println("Neighbor '" + neighborName + "' is not a UdpCl Neighbor");
					return;
				}
				UdpClNeighbor udpClNeighbor = (UdpClNeighbor)neighbor;
				udpClNeighbor.start();
			}
			
		} else {
			System.err.println("Expected 'tcpcl' or 'udpcl' as second word of command");
			return;
		}

	}
	
	/**
	 * Display help for 'stop' command
	 * @param words not used
	 */
	private void helpStop(String[] words) {
		System.out.println("stop tcpcl link <linkName>");
		System.out.println("stop tcpcl neighbor <neighborName>");
		System.out.println("stop udpcl link <linkName>");
		System.out.println("stop udpcl neighbor <neighborName>");
	}
	
	/**
	 * Execute 'stop' command
	 * @param words arguments
	 * @throws InterruptedException 
	 */
	private void stop(String[] words) throws InterruptedException {
		// stop tcpcl link     <linkName>
		// stop tcpcl neighbor <neighborName>
		// stop udpcl link     <linkName>
		// stop udpcl neighbor <neighborName>
		// 0     1     2        3
		if (words.length != 4) {
			System.err.println("Incorrect number of options for 'stop' command");
			return;
		}
		String which = getMatchAmongAlts(words[1], "tcpcl udpcl", true);
		if (which.equalsIgnoreCase("tcpcl")) {
			which = getMatchAmongAlts(words[2], "link neighbor");
			if (which.equalsIgnoreCase("link")) {
				String linkName = words[3];
				Link link = LinksList.getInstance().findLinkByName(linkName);
				if (link == null) {
					System.err.println("No Link named '" + linkName + "'");
					return;
				}
				if (!(link instanceof TcpClLink)) {
					System.err.println("Link '" + linkName + "' is not a TcpCl link");
					return;
				}
				TcpClLink tcpClLink = (TcpClLink)link;
				tcpClLink.stop();
				
			} else if (which.equalsIgnoreCase("neighbor")) {
				String neighborName = words[3];
				Neighbor neighbor = NeighborsList.getInstance().findNeighborByName(neighborName);
				if (neighbor == null) {
					System.err.println("No Neighbor named '" + neighborName + "'");
					return;
				}
				if (!(neighbor instanceof UdpClNeighbor)) {
					System.err.println("Neighbor '" + neighborName + "' is not a TcpCl Neighbor");
					return;
				}
				UdpClNeighbor udpClNeighbor = (UdpClNeighbor)neighbor;
				udpClNeighbor.stop();
				
			} else {
				System.err.println("Invalid 'stop' command option: " + words[2]);
			}
			
		} else if (which.equalsIgnoreCase("udpcl")) {
			which = getMatchAmongAlts(words[2], "link neighbor");
			if (which.equalsIgnoreCase("link")) {
				String linkName = words[3];
				Link link = LinksList.getInstance().findLinkByName(linkName);
				if (link == null) {
					System.err.println("No Link named '" + linkName + "'");
					return;
				}
				if (!(link instanceof UdpClLink)) {
					System.err.println("Link '" + linkName + "' is not a TcpCl link");
					return;
				}
				UdpClLink tcpClLink = (UdpClLink)link;
				tcpClLink.stop();
				
			} else if (which.equalsIgnoreCase("neighbor")) {
				String neighborName = words[3];
				Neighbor neighbor = NeighborsList.getInstance().findNeighborByName(neighborName);
				if (neighbor == null) {
					System.err.println("No Neighbor named '" + neighborName + "'");
					return;
				}
				if (!(neighbor instanceof UdpClNeighbor)) {
					System.err.println("Neighbor '" + neighborName + "' is not a TcpCl Neighbor");
					return;
				}
				UdpClNeighbor udpClNeighbor = (UdpClNeighbor)neighbor;
				udpClNeighbor.stop();
				
			} else {
				System.err.println("Invalid 'stop' command option: " + words[2]);
			}
			
		} else {
			System.err.println("Expected 'tcpcl' as second word of command");
			return;
		}

	}
	
	/**
	 * Provide help for eid and bundling options used in various commands which
	 * send Bundles.
	 */
	private void helpOptions() {
		System.out.println("         <eid> = destination EndPointId");
		System.out.println("         Options:");
		System.out.println("         -red => Send Bundle Red (Reliable)");
		System.out.println("         -transferCustody => request custody transfer");
		System.out.println("         -custodyReport => Report when custody accepted");
		System.out.println("         -receiveReport => Report when Bundle received");
		System.out.println("         -forwardReport => Report when Bundle forwarded");
		System.out.println("         -deliverReport => Report when Bundle delivered");
		System.out.println("         -deleteReport => Report when Bundle deleted due to anomalous conditions");
		System.out.println("         -lifetime <n> => Set Bundle lifetime to 'n' seconds");
		System.out.println("         -bulk => Bulk class of service/priority");
		System.out.println("         -normal => Normal class of service/priority");
		System.out.println("         -expedited => Expedited class of service/priority");
	}
	
	/**
	 * Decipher command options for 'text' command, and fill things out
	 * appropriately.
	 * @param startCmdIndex First command argument to start processing
	 * @param appName Application name (e.g. 'Text')
	 * @param words command arguments
	 * @param options Populated based on command arguments
	 * @param text Populated based on the text to send
	 * @return The Destination EID specified in the command.
	 */
	private EndPointId decipherSendOrTextOptions(
			int startCmdIndex,
			String appName, 
			String[] words, 
			BundleOptions options, 
			StringBuffer text) {
		EndPointId destEid = null;
		boolean processingOptions = true;
		for (int ix = startCmdIndex; ix < words.length; ix++) {
			if (processingOptions) {
				// Processing options phase
				ix = decipherBundleOptions(ix, words, options);
				if (ix < 0) {
					return null;
				} else {
					// Doesn't start with "-"; end of processing options
					// this word should be eid
					// then go to processing text phase
					try {
						if (appName == null) {
							destEid = EndPointId.createEndPointId(words[ix]);
						} else {
							destEid = EndPointId.createEndPointId(words[ix] + "/" + appName);
						}
					} catch (BPException e) {
						System.err.println(e.getMessage());
						return null;
					}
					processingOptions = false;
				}
			} else {
				// Processing text phase; append this word onto text string to send
				text.append(words[ix] + " ");
			}
		}
		if (destEid == null) {
			System.err.println("No <eid> specified");
			return null;
		}
		if (text.length() == 0) {
			System.err.println("No <words...> specified");
			return null;
		}
		if (text.charAt(text.length() - 1) == ' ') {
			text.deleteCharAt(text.length() - 1);
		}
		return destEid;
	}
	
	/**
	 * Decipher the command line options having to do with Bundling Options.
	 * @param ix Beginning index into words
	 * @param words Command line arguments
	 * @param options Filled out based on command arguments concerning bundling
	 * options.
	 * @return Next index into words array beyond bundling options
	 */
	private int decipherBundleOptions(int ix, String[] words, BundleOptions options) {
		for (; ix < words.length; ix++) {
			if (words[ix].startsWith("-")) {
				// Starts with "-"; is an option
				String option = getMatchAmongAlts(
						words[ix], 
						"-transferCustody -custodyReport -receiveReport" +
						" -forwardReport -deliverReport -deleteReport" +
						" -lifetime -red -bulk -normal -expedited");
				if (option.equalsIgnoreCase("-transferCustody")) {
					options.isCustodyXferRqstd = true;
					options.custodianEndPointId = BPManagement.getInstance().getEndPointIdStem();
				} else if (option.equalsIgnoreCase("-custodyReport")) {
					options.isReportCustodyAcceptance = true;
					options.reportToEndPointId = BPManagement.getInstance().getEndPointIdStem();
				} else if (option.equalsIgnoreCase("-receiveReport")) {
					options.isReportBundleReception = true;
					options.reportToEndPointId = BPManagement.getInstance().getEndPointIdStem();
				} else if (option.equalsIgnoreCase("-forwardReport")) {
					options.isReportBundleForwarding = true;
					options.reportToEndPointId = BPManagement.getInstance().getEndPointIdStem();
				} else if (option.equalsIgnoreCase("-deliverReport")) {
					options.isReportBundleDelivery = true;
					options.reportToEndPointId = BPManagement.getInstance().getEndPointIdStem();
				} else if (option.equalsIgnoreCase("-deleteReport")) {
					options.isReportBundleDeletion = true;
					options.reportToEndPointId = BPManagement.getInstance().getEndPointIdStem();
				} else if (option.equalsIgnoreCase("-red")) {
					options.blockColor = BundleColor.RED;
				} else if (option.equalsIgnoreCase("-bulk")) {
					options.classOfServicePriority = BPClassOfServicePriority.BULK;
				} else if (option.equalsIgnoreCase("-normal")) {
					options.classOfServicePriority = BPClassOfServicePriority.NORMAL;
				} else if (option.equalsIgnoreCase("-expedited")) {
					options.classOfServicePriority = BPClassOfServicePriority.EXPEDITED;
				} else if (option.equalsIgnoreCase("-lifetime")) {
					ix++;
					if (ix >= words.length) {
						System.err.println("Missing <n> after -lifetime");
						return -1;
					}
					try {
						options.lifetime = Long.parseLong(words[ix]);
					} catch (NumberFormatException e) {
						System.err.println("Invalid long int specified for lifetime");
						return -1;
					}
					
				} else {
					System.err.println("Invalid option: " + option);
					return -1;
				}
			} else {
				return ix;
			}
		}
		return ix;
	}
	
	/**
	 * Provide help for 'sendFile' command
	 * @param words arguments
	 */
	private void helpSend(String[] words) {
		System.out.println("     Send file to given 'eid'");
		System.out.println("       sendFile {options} <eid> filePath");
		helpOptions();
	}
	
	/**
	 * Execute the 'send' command
	 * @param words arguments
	 * @throws InterruptedException if interrupted during process
	 */
	private void doSend(String[] words) throws InterruptedException {
		// send {options} <eid> filePath
		if (words.length < 2) {
			System.err.println("Incomplete 'send' command");
			return;
		}
		
		// Set up default bundling options
		BundleOptions options = new BundleOptions();	// Bundle Options
		options.classOfServicePriority = BPClassOfServicePriority.BULK;
		options.lifetime = 3600;	// Default lifetime 1 hour

		// Figure out command line bundling options
		int ix = decipherBundleOptions(1, words, options);
		if (ix < 0) {
			return;
		}
		
		// Command line <eid>
		if (ix >= words.length) {
			System.err.println("<eid> omitted");
			return;
		}
		String eidStr = words[ix];
		EndPointId destEid;
		try {
			destEid = EndPointId.createEndPointId(eidStr);
		} catch (BPException e1) {
			System.err.println(e1.getMessage());
			return;
		}
		
		// Command line <filePath>
		if (++ix >= words.length) {
			System.err.println("<filePath> omitted");
			return;
		}
		String filePath = words[ix];
		
		// Send the File
		System.out.println("Sending " +
				" eid=" + destEid.getEndPointIdString() +
				" path=" + filePath +
				" options=" + options);
		Dtn2CpApp app = (Dtn2CpApp)AppManager.getInstance().getApp(Dtn2CpApp.APP_NAME);
		if (app == null) {
			System.err.println("'Dtn2Cp' application is not installed");
			return;
		}
		try {
			app.sendFile(new File(filePath), destEid, options);
		} catch (JDtnException e) {
			System.err.println(e.getMessage());
		}
	}
	
	/**
	 * Provide help for 'text' command
	 * @param words arguments
	 */
	private void helpText(String[] words) {
		System.out.println("     Send text note containing 'words...' to given 'eid'");
		System.out.println("       text {options} <eid> <words...> ");
		System.out.println("         <words...> = Text to send in payload of Bundle");
		helpOptions();
	}
	
	/**
	 * Execute the 'text' command; send a Text Note to another node.
	 * @param words arguments
	 * @throws InterruptedException if interrupted during a wait
	 */
	private void text(String[] words) throws InterruptedException {
		// text {options} <eid> <words...> 
		if (words.length < 2) {
			System.err.println("Incomplete 'text' command");
			return;
		}
		
		BundleOptions options = new BundleOptions();	// Bundle Options

		// Figure out command line
		StringBuffer text = new StringBuffer();			// Text to send
		EndPointId destEid = decipherSendOrTextOptions(1, null, words, options, text);
		if (destEid == null) {
			return;
		}
		
		// Send the Text Note
		System.out.println("Sending " +
				" eid=" + destEid.getEndPointIdString() +
				" text=" + text +
				" options=" + options);
		TextApp textApp = (TextApp)AppManager.getInstance().getApp(TextApp.APP_NAME);
		if (textApp == null) {
			System.err.println("'Text' application is not installed");
			return;
		}
		try {
			textApp.sendText(
					destEid.getEndPointIdString(), 
					text.toString(), 
					options);
		} catch (JDtnException e) {
			System.err.println(e.getMessage());
		}
	}
	
	/**
	 * Provide help for 'photo' command
	 * @param words arguments
	 */
	public void helpPhoto(String[] words) {
		System.out.println("     Send a photo note to given 'eid'");
		System.out.println("       photo {options} <eid> <photoFile> ");
		System.out.println("         <photoFile - Path to a .jpg image");
		helpOptions();
	}
	
	/**
	 * Execute 'photo' command; send a Photo Note to a node.
	 * @param words arguments
	 * @throws InterruptedException if interrupted druing wait
	 */
	public void photo(String[] words) throws InterruptedException {
		// photo {options} <eid> <photoFile> 
		if (words.length < 2) {
			System.err.println("Incomplete 'photo' command");
			return;
		}
		
		// Set up default bundling options
		BundleOptions options = new BundleOptions();	// Bundle Options
		options.classOfServicePriority = BPClassOfServicePriority.BULK;

		// Figure out command line bundling options
		int ix = decipherBundleOptions(1, words, options);
		if (ix < 0) {
			return;
		}
		
		// Command line <eid>
		if (ix >= words.length) {
			System.err.println("<eid> omitted");
			return;
		}
		String eidStr = words[ix];
		EndPointId destEid;
		try {
			destEid = EndPointId.createEndPointId(eidStr);
		} catch (BPException e1) {
			System.err.println(e1.getMessage());
			return;
		}
		
		// Command line <photoFile>
		if (++ix >= words.length) {
			System.err.println("<photoFile> omitted");
			return;
		}
		String filePath = words[ix];
		
		// Send the Photo Note
		System.out.println("Sending " +
				" eid=" + destEid.getEndPointIdString() +
				" path=" + filePath +
				" options=" + options);
		PhotoApp photoApp = (PhotoApp)AppManager.getInstance().getApp(PhotoApp.APP_NAME);
		if (photoApp == null) {
			System.err.println("'Photo' application is not installed");
			return;
		}
		try {
			photoApp.sendPhoto(destEid.getEndPointIdString(), new File(filePath), options);
		} catch (JDtnException e) {
			System.err.println(e.getMessage());
		}
	}
	
	/**
	 * Provide help for 'video' command
	 * @param words arguments
	 */
	public void helpVideo(String[] words) {
		System.out.println("     Send a video note to given 'eid'");
		System.out.println("       video {options} <eid> <videoFile> ");
		System.out.println("         <videoFile - Path to a .3gp video file");
		helpOptions();
	}
	
	/**
	 * Execute 'video' command; send a Video Note to a node.
	 * @param words arguments
	 * @throws InterruptedException if interrupted during wait
	 */
	public void video(String[] words) throws InterruptedException {
		// video {options} <eid> <videoFile> 
		if (words.length < 2) {
			System.err.println("Incomplete 'video' command");
			return;
		}
		
		// Set up default bundling options
		BundleOptions options = new BundleOptions();	// Bundle Options
		options.classOfServicePriority = BPClassOfServicePriority.BULK;

		// Figure out command line bundling options
		int ix = decipherBundleOptions(1, words, options);
		if (ix < 0) {
			return;
		}
		
		// Command line <eid>
		if (ix >= words.length) {
			System.err.println("<eid> omitted");
			return;
		}
		String eidStr = words[ix];
		EndPointId destEid;
		try {
			destEid = EndPointId.createEndPointId(eidStr);
		} catch (BPException e1) {
			System.err.println(e1.getMessage());
			return;
		}
		
		// Command line <videoFile>
		if (++ix >= words.length) {
			System.err.println("<videoFile> omitted");
			return;
		}
		String filePath = words[ix];
		
		// Send the Photo Note
		System.out.println("Sending " +
				" eid=" + destEid.getEndPointIdString() +
				" path=" + filePath +
				" options=" + options);
		VideoApp videoApp = (VideoApp)AppManager.getInstance().getApp(VideoApp.APP_NAME);
		if (videoApp == null) {
			System.err.println("'Video' application is not installed");
			return;
		}
		try {
			videoApp.sendVideo(destEid.getEndPointIdString(), new File(filePath), options);
		} catch (JDtnException e) {
			System.err.println(e.getMessage());
		}
	}
	
	/**
	 * Provide help for 'voice' command
	 * @param words arguments
	 */
	public void helpVoice(String[] words) {
		System.out.println("     Send a voice note to given 'eid'");
		System.out.println("       voice {options} <eid> <voiceFile> ");
		System.out.println("         <voiceFile - Path to a .3gp audio file");
		helpOptions();
	}
	
	/**
	 * Execute 'voice' command; send a Voice Note to a node
	 * @param words arguments
	 * @throws InterruptedException if interrupted during wait
	 */
	public void voice(String[] words) throws InterruptedException {
		// voice {options} <eid> <voiceFile> 
		if (words.length < 2) {
			System.err.println("Incomplete 'voice' command");
			return;
		}
		
		// Set up default bundling options
		BundleOptions options = new BundleOptions();	// Bundle Options
		options.classOfServicePriority = BPClassOfServicePriority.BULK;

		// Figure out command line bundling options
		int ix = decipherBundleOptions(1, words, options);
		if (ix < 0) {
			return;
		}
		
		// Command line <eid>
		if (ix >= words.length) {
			System.err.println("<eid> omitted");
			return;
		}
		String eidStr = words[ix];
		EndPointId destEid;
		try {
			destEid = EndPointId.createEndPointId(eidStr);
		} catch (BPException e1) {
			System.err.println(e1.getMessage());
			return;
		}
		
		// Command line <voiceFile>
		if (++ix >= words.length) {
			System.err.println("<voiceFile> omitted");
			return;
		}
		String filePath = words[ix];
		
		// Send the Voice Note
		System.out.println("Sending " +
				" eid=" + destEid.getEndPointIdString() +
				" path=" + filePath +
				" options=" + options);
		VoiceApp voiceApp = (VoiceApp)AppManager.getInstance().getApp(VoiceApp.APP_NAME);
		if (voiceApp == null) {
			System.err.println("'Voice' application is not installed");
			return;
		}
		try {
			voiceApp.sendVoiceNote(destEid.getEndPointIdString(), new File(filePath), options);
		} catch (JDtnException e) {
			System.err.println(e.getMessage());
		}
	}
	
	/**
	 * Provide help for 'ping' command
	 * @param words arguments
	 */
	private void helpPing(String[] words) {
		System.out.println("     Send echo request(s); receive echo reply(s)");
		System.out.println("       ping destEid {count {lifetimeSecs}}");
	}
	
	/**
	 * Execute 'ping' command; send a ping request, receive a ping reply.
	 * @param words arguments
	 */
	private void ping(String[] words) {
		// ping destEid {count {lifetimeSecs}}
		// 0    1       2       3
		if (words.length < 2) {
			System.err.println("Missing 'destEid' argument to ping command");
			return;
		}
		String eidStr = words[1];
		EndPointId eid;
		try {
			eid = EndPointId.createEndPointId(eidStr);
		} catch (BPException e) {
			System.err.println(e.getMessage());
			return;
		}
		int count;
		if (words.length < 3) {
			count = 1;
		} else {
			try {
				count = Integer.parseInt(words[2]);
				if (count < 1) {
					System.err.println("Invalid value for 'count' argument: " + words[2]);
					return;
				}
			} catch (NumberFormatException e) {
				System.err.println("Invalid integer for 'count' argument: " + words[2]);
				return;
			}
		}
		
		long lifetimeSecs;
		if (words.length < 4) {
			lifetimeSecs = Dtn2PingApp.PING_LIFETIME_SECS;
		} else {
			try {
				lifetimeSecs = Long.parseLong(words[3]);
			} catch (NumberFormatException e) {
				System.err.println("invalid Long for 'lifetimeSecs' argument: " + words[3]);
				return;
			}
		}
		
		Dtn2PingApp pinger = (Dtn2PingApp)AppManager.getInstance().getApp(Dtn2PingApp.APP_NAME);
		if (pinger == null) {
			System.err.println("Ping app is not installed");
			return;
		}
		pinger.doPing(eid.getEndPointIdString(), count, lifetimeSecs);
	}
	
	/**
	 * Provide help for 'rateEstimator' command
	 * @param words not used
	 */
	private void helpRateEstimator(String[] words) {
		System.out.println("    Rate Estimator for specified Neighbor");
		System.out.println("      rateEstimator <linkName> <neighborName> <filename>");
	}
	
	/**
	 * Execute 'rateEstimator' command
	 * @param words command arguments
	 */
	private void rateEstimator(String[] words) {
		// rateEstimator <linkName> <neighborName> <filename>
		if (words.length != 4) {
			System.err.println("Incomplete 'rateEstimator' command");
			return;
		}
		
		String linkName = words[1];
		String neighborName = words[2];
		String filename = words[3];
		
		LtpLink link = LtpManagement.getInstance().findLtpLink(linkName);
		if (link == null) {
			System.err.println("No such Link: " + linkName);
			return;
		}
		Neighbor neighbor = NeighborsList.getInstance().findNeighborByName(neighborName);
		if (neighbor == null) {
			System.err.println("No such Neighbor: " + neighborName);
			return;
		}
		if (!(neighbor instanceof LtpNeighbor)) {
			System.err.println("Named neighbor is not a Ltp Neighbor");
			return;
		}
		LtpNeighbor ltpNeighbor = (LtpNeighbor)neighbor;
		File file = new File(filename);
		if (!file.exists()) {
			System.err.println(" File " + filename + " does not exist");
			return;
		}
		
		RateEstimatorApp estimator =
			(RateEstimatorApp)AppManager.getInstance().getApp(
					RateEstimatorApp.APP_NAME);
		if (estimator == null) {
			System.err.println("RateEstimatorApp is not installed");
			return;
		}
		
		System.out.println(
				"Starting RateEstimator for Link " + linkName + 
				" Neighbor " + neighborName);
		try {
			estimator.estimateRateLimit(ltpNeighbor, file);
		} catch (JDtnException e) {
			System.err.println(e.getMessage());
			return;
		}
	}
	
	private void helpIon(String[] words) {
		System.out.println("    ION interoperability test");
		System.out.println("      ion source {options} <eid> {word} ,..        Source text bundle to <eid>");
		System.out.println("         <words...> = Text to send in payload of Bundle");
		System.out.println("      ion sendFile {options} <eid> <filePath>      Send file(BPSendFile) to <eid>");
		helpOptions();
	}
	
	private void ion(String[] words) throws InterruptedException {
		if (words.length < 2) {
			System.err.println("Incomplete 'ion' command");
			return;
		}
		String cmd = getMatchAmongAlts(words[1], "source sendFile");
		if (cmd.equalsIgnoreCase("source")) {
			ionSource(words);
		} else if (cmd.equalsIgnoreCase("sendFile")) {
			ionSendFile(words);
		} else {
			System.err.println("Unrecognized 'ion' sub-command: " + words[1]);
		}
	}
	
	private void ionSource(String[] words) {
		// ion source {options} <eid> <words...> 
		if (words.length < 3) {
			System.err.println("Incomplete 'source' command");
			return;
		}
		
		BundleOptions options = new BundleOptions();	// Bundle Options

		// Figure out command line
		StringBuffer text = new StringBuffer();			// Text to send
		EndPointId destEid = decipherSendOrTextOptions(2, null, words, options, text);
		if (destEid == null) {
			return;
		}
		
		// Send the Text
		System.out.println("Sourcing " +
				" eid=" + destEid.getEndPointIdString() +
				" text=" + text +
				" options=" + options);
		IonSourceSinkApp ionSourceApp = (IonSourceSinkApp)AppManager.getInstance().getApp(IonSourceSinkApp.APP_NAME);
		if (ionSourceApp == null) {
			System.err.println("'IonSourceApp' application is not installed");
			return;
		}
		try {
			ionSourceApp.source(destEid, options, text.toString());
		} catch (Exception e) {
			System.err.println(e.getMessage());
		}
	}
	
	private void ionSendFile(String[] words) throws InterruptedException {
		// ion sendFile {options} <eid> <filePath>
		// 0   1        2         3     4
		if (words.length < 5) {
			System.err.println("Incorrect number of arguments");
			return;
		}
		
		BundleOptions options = new BundleOptions();	// Bundle Options

		// Figure out command line
		StringBuffer text = new StringBuffer();			// Cmd line remainder after options
		EndPointId destEid = decipherSendOrTextOptions(2, null, words, options, text);
		if (destEid == null) {
			return;
		}
		
		File file = new File(text.toString());
		BPSendFileApp app = (BPSendFileApp)AppManager.getInstance().getApp(BPSendFileApp.APP_NAME);
		if (app == null) {
			System.err.println("BPSendFileApp is not installed");
			return;
		}
		System.out.println(
				"Sending " + file.getAbsolutePath() + " to " + 
				destEid.getEndPointIdString());
		try {
			app.sendFile(file, destEid, options);
		} catch (JDtnException e) {
			System.err.println(e.getMessage());
		}
	}
	
	/**
	 * Determine which one of the given alternative words which the given input
	 * String is a unique prefix of.
	 * @param input Given input String
	 * @param alternatives Alternative words
	 * @return Matching alternative word, or original input if not match
	 */
	public String getMatchAmongAlts(String input, String alternatives) {
		return getMatchAmongAlts(input, alternatives, false);
	}
	
	public String getMatchAmongAlts(String input, String alternatives, boolean quiet) {
		String lcInput = input.toLowerCase();
		String[] words = alternatives.split(" ");
		int matchingIndex = -1;
		int nMatches = 0;
		
		for (int ix = 0; ix < words.length; ix++) {
			String word = words[ix];
			if (word.toLowerCase().startsWith(lcInput)) {
				matchingIndex = ix;
				nMatches++;
			}
		}
		if (nMatches > 1) {
			if (!quiet) {
				System.err.println("Ambiguous input; try typing more letters");
			}
			return input;
			
		} else if (nMatches == 1) {
			return words[matchingIndex];
		}
		if (!quiet) {
			System.err.println("No match among alternatives: " + alternatives);
		}
		return input;
	}
	
	/**
	 * A Test Application we install in order to
	 * inform user upon event notifications from
	 * BpApi.
	 */
	public static class TestApp extends AbstractApp {
		
		public TestApp(String[] args) throws BPException {
			super("Test", "test" + BPManagement.ALL_SUBCOMPONENTS_EID_PATTERN, 10, null);
			// Doesn't need to be written into config since we start it programmatically
			setSaveInConfig(false);
		}

		@Override
		public void shutdownImpl() {
			// Nothing
		}

		@Override
		public void startupImpl() {
			// Nothing
		}

		@Override
		public void threadImpl() throws Exception {
			Thread.sleep(60000L);
		}
		
		@Override
		public void onBundleCustodyAccepted(
				BundleId bundleId,
				EndPointId reporter) {
			System.out.println("Bundle Custody Accepted Downstream");
			System.out.print(bundleId.dump("  ", false));
			System.out.println("  Reported By: " + reporter.getEndPointIdString());
		}

		@Override
		public void onBundleDeletedDownstream(
				BundleId bundleId, 
				EndPointId reporter,
				int reason) {
			System.out.println("Bundle Deleted Downstream due to anomalous conditions");
			System.out.print(bundleId.dump("  ", false));
			System.out.println("  Reported By: " + reporter.getEndPointIdString());
			System.out.println("  Reason: " + BundleStatusReport.reasonCodeToString(reason));
		}

		@Override
		public void onBundleDeliveredDownstream(
				BundleId bundleId,
				EndPointId reporter) {
			System.out.println("Bundle Delivered Downstream");
			System.out.print(bundleId.dump("  ", false));
			System.out.println("  Reported By: " + reporter.getEndPointIdString());
		}

		@Override
		public void onBundleForwardedDownstream(
				BundleId bundleId,
				EndPointId reporter) {
			System.out.println("Bundle Forwarded Downstream");
			System.out.print(bundleId.dump("  ", false));
			System.out.println("  Reported By: " + reporter.getEndPointIdString());
		}

		@Override
		public void onBundleReceivedDownstream(
				BundleId bundleId,
				EndPointId reporter) {
			System.out.println("Bundle Received Downstream");
			System.out.print(bundleId.dump("  ", false));
			System.out.println("  Reported By: " + reporter.getEndPointIdString());
		}

		@Override
		public void onBundleLifetimeExpired(BundleId bundleId) {
			System.out.println("Bundle Lifetime Expired");
			System.out.print(bundleId.dump("  ", false));
		}
		
		@Override
		public void onBundleCustodyReleased(BundleId bundleId) {
			System.out.println("Bundle released from custody");
			System.out.print(bundleId.dump("  ", false));
		}

	}
	
}
