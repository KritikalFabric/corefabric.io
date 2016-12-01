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
package org.kritikal.fabric.dtn.jdtn;

import com.cisco.qte.jdtn.apps.*;
import com.cisco.qte.jdtn.bp.*;
import com.cisco.qte.jdtn.bp.PrimaryBundleBlock.BPClassOfServicePriority;
import com.cisco.qte.jdtn.general.*;
import com.cisco.qte.jdtn.ltp.*;
import com.cisco.qte.jdtn.ltp.udp.LtpUDPLink;
import com.cisco.qte.jdtn.ltp.udp.LtpUDPNeighbor;
import com.cisco.qte.jdtn.tcpcl.TcpClLink;
import com.cisco.qte.jdtn.tcpcl.TcpClManagement;
import com.cisco.qte.jdtn.tcpcl.TcpClNeighbor;
import com.cisco.qte.jdtn.udpcl.UdpClLink;
import com.cisco.qte.jdtn.udpcl.UdpClManagement;
import com.cisco.qte.jdtn.udpcl.UdpClNeighbor;
import com.cisco.saf.Service;
import io.vertx.core.*;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.shell.ShellService;
import io.vertx.ext.shell.ShellServiceOptions;
import io.vertx.ext.shell.command.CommandBuilder;
import io.vertx.ext.shell.command.CommandProcess;
import io.vertx.ext.shell.command.CommandRegistry;
import io.vertx.ext.shell.term.HttpTermOptions;
import io.hawt.web.plugin.HawtioPlugin;

import java.io.File;
import java.net.UnknownHostException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A command line interface to the JDTN Stack.  Mostly managment and config.
 * Some bundling.
 */
public class JDTNHTTPShell extends AbstractVerticle{

	private static final Logger _logger =
		Logger.getLogger(JDTNHTTPShell.class.getCanonicalName());

	private TestApp _testApp = null;



	@Override
	public void start(Future<Void> startFuture) throws Exception {
		ShellService service = ShellService.create(vertx, new ShellServiceOptions().
				setHttpOptions(
						new HttpTermOptions().
								setPort(8080)));
		service.start(ar -> {
			if (ar.succeeded()) {
				registerVertxShellCommands(startFuture); //register vertx shell commands
			} else {
				startFuture.fail(ar.cause());
			}
		});
	}

	@Override
	public void stop(Future<Void> stopFuture) throws Exception {
		this.terminate();
		stopFuture.complete();
	}



	/**
	 * Main program
	 * @param args
	 */
	public static void main(String[] args) {
		JDTNHTTPShell shell;
		try {
			shell = new JDTNHTTPShell();
		} catch (BPException e) {
			_logger.log(Level.SEVERE, "Shell construction", e);
			System.exit(1);
			return;
		}

		//Expose Metrics to JMX for Jolokia to expose over REST and hawtIO to render
		Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(
				new DropwizardMetricsOptions()
						.setEnabled(true)
						.setJmxEnabled(true)
						.setJmxDomain("vertx-metrics")));

		//Deploy shell verticle
		vertx.deployVerticle(shell);

		//shell.terminate();
		//System.exit(0);
	}

	/**
	 * Constructor; starts up JDTN stack, launches Bundle receiver thread
	 * @throws BPException On startup errors
	 */
	public JDTNHTTPShell() throws BPException {
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
	

	private void registerVertxShellCommand(String commandname){
		CommandBuilder builder = CommandBuilder.command(commandname);
		builder.processHandler(process -> {

			try {
				switch (commandname) {

					case "dtn-config":
						config(process);
						break;
					case "dtn-show":
						show(process);
						break;
					case "dtn-set":
						set(process);
						break;
					case "dtn-add":
						add(process);
						break;
					case "dtn-remove":
						remove(process);
						break;
					case "dtn-ping":
						ping(process);
						break;
					case "dtn-text":
						text(process);
						break;
					case "dtn-photo":
						photo(process);
						break;
					case "dtn-video":
						video(process);
						break;
					case "dtn-rateEstimator":
						rateEstimator(process);
						break;
					case "dtn-clear":
						clear(process);
						break;
					case "dtn-ion":
						ion(process);
						break;
					case "dtn-start":
						start(process);
						break;
					case "dtn-stop":
						stop(process);
						break;
					case "dtn-sendFile":
						doSend(process);
						break;
					case "dtn-clean":
						clean(process);
						break;
					case "dtn-help":
						help(process);
						break;


				}
			}
			catch (Exception e){
				e.printStackTrace(); //TODO: how to handle this better
			}
			// End the process
			process.end();
		});

		// Register the command
		CommandRegistry registry = CommandRegistry.getShared(vertx);
		registry.registerCommand(builder.build(vertx));
	}

	private void registerVertxShellCommands(Future<Void> startFuture){
		registerVertxShellCommand("dtn-config");
		registerVertxShellCommand("dtn-show");
		registerVertxShellCommand("dtn-set");
		registerVertxShellCommand("dtn-add");
		registerVertxShellCommand("dtn-remove");
		registerVertxShellCommand("dtn-ping");
		registerVertxShellCommand("dtn-text");
		registerVertxShellCommand("dtn-photo");
		registerVertxShellCommand("dtn-video");
		registerVertxShellCommand("dtn-rateEstimator");
		registerVertxShellCommand("dtn-clear");
		registerVertxShellCommand("dtn-ion");
		registerVertxShellCommand("dtn-start");
		registerVertxShellCommand("dtn-stop");
		registerVertxShellCommand("dtn-sendFile");
		registerVertxShellCommand("dtn-clean");
		registerVertxShellCommand("dtn-help");
		startFuture.complete();
	}
	

	
	/**
	 * Help command processing; can display overall help if no arguments or
	 * dispatch to lower level help.
	 * @param process shellprocess
	 */
	private void help(CommandProcess process) {
		List<String> words=process.args();
		String subject = null;
		if (words.size() >= 1) {
			subject = getMatchAmongAlts(process,
					words.get(0),
					"dtn-config dtn-show dtn-set dtn-add dtn-remove dtn-ping dtn-text dtn-photo dtn-video " +
					"dtn-voice dtn-clean dtn-ion dtn-clear dtn-rateEstimator dtn-start dtn-stop dtn-sendFile");
		}
		if (subject == null) {
			topLevelHelp(process);
		
		} else if (subject.equalsIgnoreCase("dtn-config")) {
			helpConfig(process);
		} else if (subject.equalsIgnoreCase("dtn-show")) {
			helpShow(process);
		} else if (subject.equalsIgnoreCase("dtn-set")) {
			helpSet(process);
		} else if (subject.equalsIgnoreCase("dtn-add")) {
			helpAdd(process);
		} else if (subject.equalsIgnoreCase("dtn-remove")) {
			helpRemove(process);
		} else if (subject.equalsIgnoreCase("dtn-ping")) {
			helpPing(process);
		} else if (subject.equalsIgnoreCase("dtn-text")) {
			helpText(process);
		} else if (subject.equalsIgnoreCase("dtn-photo")) {
			helpPhoto(process);
		} else if (subject.equalsIgnoreCase("dtn-video")) {
			helpVideo(process);
		} else if (subject.equalsIgnoreCase("dtn-voice")) {
			helpVoice(process);
		} else if (subject.equalsIgnoreCase("dtn-rateEstimator")) {
			helpRateEstimator(process);
		} else if (subject.equalsIgnoreCase("dtn-clear")) {
			helpClear(process);
		} else if (subject.equalsIgnoreCase("dtn-ion")) {
			helpIon(process);
		} else if (subject.equalsIgnoreCase("dtn-start")) {
			helpStart(process);
		} else if (subject.equalsIgnoreCase("dtn-stop")) {
			helpStop(process);
		} else if (subject.equalsIgnoreCase("dtn-sendFile")) {
			helpSend(process);
		} else if (subject.equalsIgnoreCase("dtn-clean")) {
			helpClean(process);
			
		} else {
			topLevelHelp(process);
		}
	}

	/**
	 * Display an overall help summary
	 */
	private void topLevelHelp(CommandProcess process) {
		process.write("dtn Help\n");
		process.write("  dtn-add ...                      Add Stuff\n");
		process.write("  dtn-clean ...                    Clean Stuff\n");
		process.write("  dtn-clear                        Clear statistics\n");
		process.write("  dtn-config ...                   Configuration related commands\n");
		process.write("  dtn-exit                         Get outta here\n");
		process.write("  dtn-ion                          ION Interop Testing\n");
		process.write("  dtn-photo ...                    Send Photo Note\n");
		process.write("  dtn-ping ...                     Ping things (DTN2 'dtnping' comptaible)\n");
		process.write("  dtn-rateEstimator ...            Estimate Segment Rate Limit\n");
		process.write("  dtn-remove ...                   Remove Stuff\n");
		process.write("  dtn-sendFile ...                 Send file (DTN2 'dtncp' compatible)\n");
		process.write("  dtn-set ...                      Set Stuff\n");
		process.write("  dtn-show ...                     Show stuff\n");
		process.write("  dtn-start ...                    Start things\n");
		process.write("  dtn-stop ...                     Stop things\n");
		process.write("  dtn-text ...                     Send Text Note\n");
		process.write("  dtn-video ...                    Send Video Note\n");
		process.write("  dtn-voice ...                    Send Voice Note\n");
		process.write("  dtn-help <command>               Get further help on these commands\n\n");
	}
	
	/**
	 * Display help on 'config' command
	 * @param process shellprocess
	 */
	private void helpConfig(CommandProcess process) {
		process.write(" dtn-config save                    Save\n");
		process.write(" dtn-config restore                 Restore saved configuration\n");
		process.write(" dtn-config defaults                Revert to default configuration\n");
	}
	
	/**
	 * Execute 'config' command; save, restore, or revert to default configuration.
	 * @param process arguments
	 */
	private void config(CommandProcess process) {
		List<String> words=process.args();

		if (words.size() < 1) {
			process.write("Incomplete 'dtn-config' command\n");
			process.end();
			return;
		}
		String command = getMatchAmongAlts(process,words.get(0), "save restore defaults\n");
		if (command.equalsIgnoreCase("save")) {
			process.write("Saving Confg\n");
			Management.getInstance().saveConfig();
			
		} else if (command.equalsIgnoreCase("restore")) {
			process.write("Restoring saved config\n");
			Management.getInstance().setDefaults();
			Management.getInstance().loadConfig();
			
		} else if (command.equalsIgnoreCase("defaults")) {
			process.write("Setting default config\n");
			Management.getInstance().setDefaults();
		
		} else {
			process.write("Unrecognized 'config' option: '" + command + "'\n");
		}
		process.end();
	}
	
	/**
	 * Display help for 'show' command; show properties of various subsystems
	 * @param process arguments
	 */
	private void helpShow(CommandProcess process) {
		//List<String> words=process.args();

		process.write("  dtn-show all                                 Show all\n");
		process.write("  dtn-show all links                           Show all links\n");
		process.write("  dtn-show all neighbors                       Show all neighbors\n");
		process.write("  dtn-show general                             Show general configuration\n");
		process.write("  dtn-show ltp                                 Show LTP properties and stats\n");
		process.write("  dtn-show ltp statistics                      Show LTP Statistics\n");
		process.write("  dtn-show ltp links                           Show all Links\n");
		process.write("  dtn-show ltp blocks {-verbose}               Show LTP Block queues\n");
		process.write("  dtn-show bp                                  Show BP properties and stats\n");
		process.write("  dtn-show bp statistics                       Show BP Statistics\n");
		process.write("  dtn-show bp routes                           Show BP Routes\n");
		process.write("  dtn-show bp bundles {-verbose}               Show BP Bundle Retention Queue\n");
		process.write("  dtn-show link <linkName>                     Show link\n");
		process.write("  dtn-show neighbor <neighborName>             Show neighbor\n");
		process.write("  dtn-show saf                                 Show Service Advertisement Framework\n");
		process.write("  dtn-show caf                                 Show Connected Apps Framework\n");
		process.write("  dtn-show app <appName>                       Show info about given Application\n");
		process.write("  dtn-show tcpcl                               Show TcpCl properties and stats\n");
		process.write("  dtn-show tcpcl neighbors                     Show all TcpCl Neighbors\n");
		process.write("  dtn-show tcpcl links                         Show all TcpCl Links\n");
		process.write("  dtn-show tcpcl statistics                    Show TcpCl Statistics\n");
		process.write("  dtn-show udpcl                               Show UdpCl properties and stats\n");
		process.write("  dtn-show udpcl neighbors                     Show all UdpCl Neighbors\n");
		process.write("  dtn-show ucpdl links                         Show all UdpCl Links\n");
		process.write("  dtn-show udpcl statistics                    Show UdpCl Statistics\n");
	}
	
	/**
	 * Execute 'show' command; dispatches to lower level
	 * @param process arguments
	 */
	private void show(CommandProcess process) {
		List<String> words=process.args();
		if (words.size() < 1) {
			process.write(Management.getInstance().dump("", true)+"\n");
			process.end();
			return;
		}
		
		String command = getMatchAmongAlts(process,
				words.get(0),
				"all general ltp bp link neighbor saf caf app tcpcl udpcl");
		if (command.equalsIgnoreCase("all")) {
			if (words.size() >= 2) {
				String whichAll = getMatchAmongAlts(process,words.get(1), "links neighbors");
				if (whichAll.equalsIgnoreCase("links")) {
					showLinks(process);
				} else if (whichAll.equalsIgnoreCase("neighbors")) {
					showNeighbors(process);
				} else {
					process.write("Unrecognized 'dtn-show all' option: " + words.get(1)+"\n");
				}
				
			} else {
				process.write(Management.getInstance().dump("", true)+"\n");
			}
			
		} else if (command.equalsIgnoreCase("general")) {
			process.write(GeneralManagement.getInstance().dump("", true)+"\n");
			
		} else if (command.equalsIgnoreCase("ltp")) {
			if (words.size() >= 2) {
				String showWhat = getMatchAmongAlts(process,words.get(1), "statistics links blocks");
				if (showWhat.equalsIgnoreCase("statistics")) {
					process.write(LtpManagement.getInstance().getLtpStats().dump("", true)+"\n");
				} else if (showWhat.equalsIgnoreCase("links")) {
					process.write(LinksList.getInstance().dump("", true)+"\n");
				} else if (showWhat.equalsIgnoreCase("blocks")) {
					boolean verbose = false;
					if (words.size() >= 3) {
						String option = getMatchAmongAlts(process,words.get(2), "-verbose");
						if (option.equalsIgnoreCase("-verbose")) {
							verbose = true;
						} else {
							process.write("Unrecognized option: " + option+"\n");
							process.end();
							return;
						}
					}
					process.write(LtpManagement.getInstance().dumpBlocks("", verbose)+"\n");
				} else {
					process.write("I don't understand 'show ltp " + showWhat + "'\n");
				}
			} else {
				process.write(LtpManagement.getInstance().dump("", true)+"\n");
			}
			
		} else if (command.equalsIgnoreCase("bp")) {
			if (words.size() >= 2) {
				String showWhat = getMatchAmongAlts(process,words.get(1), "statistics routes bundles");
				if (showWhat.equalsIgnoreCase("statistics")) {
					process.write(BPManagement.getInstance().getBpStats().dump("", true)+"\n");
				} else if (showWhat.equalsIgnoreCase("routes")) {
					process.write(RouteTable.getInstance().dump("", true)+"\n");
				} else if (showWhat.equalsIgnoreCase("bundles")) {
					boolean verbose = false;
					if (words.size() >= 3) {
						String option = getMatchAmongAlts(process,words.get(2), "-verbose");
						if (option.equalsIgnoreCase("-verbose")) {
							verbose = true;
						} else {
							process.write("Unrecognized option: " + option+"\n");
							process.end();
							return;
						}
					}
					process.write(BPManagement.getInstance().dumpBundles("", verbose)+"\n");
				} else {
					process.write("I don't understand 'show bp " + showWhat + "'\n");
				}
			} else {
				process.write(BPManagement.getInstance().dump("", true)+"\n");
			}
			
		} else if (command.equalsIgnoreCase("link")) {
			showLink(process);
			
		} else if (command.equalsIgnoreCase("neighbor")) {
			showNeighbor(process);
			
		} else if (command.equalsIgnoreCase("caf")) {
			showCaf(process);
			
		} else if (command.equalsIgnoreCase("saf")) {
			showSaf(process);
			
		} else if (command.equalsIgnoreCase("app")) {
			showApp(process);
			
		} else if (command.equalsIgnoreCase("tcpcl")) {
			showTcpCl(process);
			
		} else if (command.equalsIgnoreCase("udpcl")) {
			showUdpCl(process);
			
		} else {
			process.write("Unrecognized 'dtnshow' option '" + command + "'\n");
		}
		process.end();
	}
	
	/**
	 * Execute 'show all links' command; show properties of all links
	 * @param process not used
	 */
	private void showLinks(CommandProcess process) {
		List<String> words=process.args();

		boolean detailed = false;
		if (words.size() > 3) {
			String option = getMatchAmongAlts(process,words.get(2), "-verbose");
			if (!option.equalsIgnoreCase("-verbose")) {
				process.write("Invalid 'show all links' option: " + words.get(2)+"\n");
				return;
			}
		}
		process.write(LinksList.getInstance().dump("", detailed)+"\n");
	}
	
	/**
	 * Execute 'show link" command; show properties of a particular Link
	 * @param process arguments
	 */
	private void showLink(CommandProcess process) {
		List<String> words=process.args();

		// show link <linkName>
		if (words.size() < 3) {
			process.write("Missing <linkName> argument\n");
			return;
		}
		String linkName = words.get(2);
		Link link = LinksList.getInstance().findLinkByName(linkName);
		if (link == null) {
			process.write("No such Link: '" + linkName + "'\n");
			return;
		}
		process.write(link.dump("", true)+"\n");
	}
	
	/**
	 * Execute 'show all neighbors' command; show properties of all neighbors
	 * @param process not used
	 */
	private void showNeighbors(CommandProcess process) {
		List<String> words=process.args();

		boolean detailed = false;
		if (words.size() > 3) {
			String option = getMatchAmongAlts(process,words.get(2), "-verbose");
			if (!option.equalsIgnoreCase("-verbose")) {
				process.write("Invalid 'show all neighbors' option: " + words.get(2)+"\n");
				return;
			}
		}
		process.write("All Neighbors\n");
		for (Neighbor neighbor : NeighborsList.getInstance()) {
			process.write(neighbor.dump("", detailed)+"\n");
		}
	}
	
	/**
	 * Execute 'show neighbor" command; show properties of a particular Neighbor
	 * @param process arguments
	 */
	private void showNeighbor(CommandProcess process) {
		List<String> words=process.args();

		// show neighbor <neighborName>
		// 0    1         2
		if (words.size() < 3) {
			process.write("Incomplete 'dtn-show neighbor <neighborName>' command\n");
			return;
		}
		String neighborName = words.get(2);
		Neighbor neighbor = NeighborsList.getInstance().findNeighborByName(neighborName);
		if (neighbor == null) {
			process.write("No such neighbor: '" + neighborName + "'\n");
			return;
		}
		process.write(neighbor.dump("", true)+"\n");
	}
	
	/**
	 * Execute 'show saf' command; show info about Service Advertisement Framework.
	 * @param process arguments
	 */
	private void showSaf(CommandProcess process) {
		List<String> words=process.args();

		SafAdapterApp app = (SafAdapterApp)AppManager.getInstance().getApp(SafAdapterApp.APP_NAME);
		if (app == null) {
			process.write("SAF app is not installed\n");
			return;
		}
		
		process.write("Discovered Neighbors\n");
		for (Neighbor neighbor : NeighborsList.getInstance()) {
			if (neighbor.isTemporary()) {
				process.write("  " + neighbor.getEndPointIdStem().getEndPointIdString()+"\n");
			}
		}
		
		process.write("Discovered Services\n");
		Service subscription = app.getSubscription();
		List<Service> services = subscription.getMatchingServices();
		for (Service service : services) {
			process.write(service.toString()+"\n");
		}
	}
	
	private void showCaf(CommandProcess process) {
		List<String> words=process.args();

		CafAdapterApp app = (CafAdapterApp)AppManager.getInstance().getApp(CafAdapterApp.APP_NAME);
		if (app == null) {
			process.write("CAF app is not installed\n");
			return;
		}
		
		process.write("Discovered Neighbors\n");
		for (Neighbor neighbor : NeighborsList.getInstance()) {
			if (neighbor.isTemporary()) {
				process.write("  " + neighbor.getEndPointIdStem().getEndPointIdString()+"\n");
			}
		}
		
		process.write("Discovered Services\n");
		com.cisco.caf.xmcp.Service subscription = app.getSubscription();
		List<com.cisco.caf.xmcp.Service> services = subscription.getMatchingServices();
		for (com.cisco.caf.xmcp.Service service : services) {
			process.write(service.toString()+"\n");
		}
	}
	
	/**
	 * Execute 'show app' command; show info about Application identified by
	 * given application name.
	 * @param process arguments
	 */
	private void showApp(CommandProcess process) {
		List<String> words=process.args();

		// show app <appName>
		// 0    1   2
		if (words.size() < 3) {
			process.write("Incomplete 'dtn-show app <appName>' command\n");
			return;
		}
		String appName = words.get(2);
		AbstractApp app = AppManager.getInstance().getApp(appName);
		if (app == null) {
			process.write("No application named '" + appName + "' is installed\n");
			return;
		}
		process.write(app.dump("", true)+"\n");
	}
	
	/**
	 * Execute 'show tcpcl' command
	 * @param process arguments
	 */
	private void showTcpCl(CommandProcess process) {
		List<String> words=process.args();

		// as a result of getting to this point, words.size() >= 2
		if (words.size() == 1 || words.size() == 2) {
			if (words.size() == 1) {
				// show tcpcl
				process.write(TcpClManagement.getInstance().dump("", true)+"\n");
			} else {
				String selector = getMatchAmongAlts(process,words.get(1), "neighbors links statistics");
				if (selector.equalsIgnoreCase("neighbors")) {
					// show tcpcl neighbors
					process.write(TcpClManagement.getInstance().dumpNeighbors("", true)+"\n");
					
				} else if (selector.equalsIgnoreCase("links")) {
					// show tcpcl links
					process.write(TcpClManagement.getInstance().dumpLinks("", true)+"\n");
					
				} else if (selector.equalsIgnoreCase("statistics")) {
					// show tcpcl statistics
					process.write(TcpClManagement.getInstance().getStatistics().dump("", true)+"\n");
					
				} else {
					process.write("I don't understand 'dtn-show tcpcl " + words.get(1)+"\n");
				}
			}
		} else {
			process.write("Extraneous argument after 'dtn-show tcpcl " + words.get(2) + "'");
		}
	}
	
	/**
	 * Execute 'show udpcl' command
	 * @param process arguments
	 */
	private void showUdpCl(CommandProcess process) {
		List<String> words=process.args();

		// as a result of getting to this point, words.size() >= 2
		if (words.size() == 1 || words.size() == 2) {
			if (words.size() == 1) {
				// show tcpcl
				process.write(UdpClManagement.getInstance().dump("", true)+"\n");
			} else {
				String selector = getMatchAmongAlts(process,words.get(1), "neighbors links statistics");
				if (selector.equalsIgnoreCase("neighbors")) {
					// show udpcl neighbors
					process.write(UdpClManagement.getInstance().dumpNeighbors("", true)+"\n");
					
				} else if (selector.equalsIgnoreCase("links")) {
					// show tcpcl links
					process.write(UdpClManagement.getInstance().dumpLinks("", true)+"\n");
					
				} else if (selector.equalsIgnoreCase("statistics")) {
					// show tcpcl statistics
					process.write(UdpClManagement.getStatistics().dump("", true)+"\n");
					
				} else {
					process.write("I don't understand 'dtn-show udpcl " + words.get(1)+"\n");
				}
			}
		} else {
			process.write("Extraneous argument after 'dtn0show udpcl " + words.get(2) + "'\n");
		}
	}
	
	/**
	 * Show help on 'set' command.  Dispatches to lower leve.
	 * @param process shellprocess
	 */
	private void helpSet(CommandProcess process) {
		List<String> words=process.args();
		if (words.size() > 1) {
			String which = getMatchAmongAlts(process,
				words.get(1),
				"general ltp bp link neighbor tcpcl udpcl");
			if (which.equalsIgnoreCase("general")) {
				helpSetGeneral(process);
				
			} else if (which.equalsIgnoreCase("ltp")) {
				helpSetLtp(process);
				
			} else if (which.equalsIgnoreCase("bp")) {
				helpSetBp(process);
				
			} else if (which.equalsIgnoreCase("link")) {
				helpSetLink(process);
				
			} else if (which.equalsIgnoreCase("neighbor")) {
				helpSetNeighbor(process);
				
			} else if (which.equalsIgnoreCase("tcpcl")) {
				helpSetTcpCl(process);
				
			} else if (which.equalsIgnoreCase("udpcl")) {
				helpSetUdpCl(process);
				
			} else {
				process.write("I don't understand 'help set " + which+"\n");
			}
		} else {
			process.write("dtn-set general ...                         Set General Properties\n");
			process.write("dtn-set ltp ...                             Set LTP Properties\n");
			process.write("dtn-set bp ...                              Set BP Properties\n");
			process.write("dtn-set link ...                            Set Link Properties\n");
			process.write("dtn-set neighbor ...                        Set Neighbor Properties\n");
			process.write("dtn-set tcpcl ...                           Set TCP Convergence Layer Properties\n");
			process.write("dtn-set udpcl ...                          set UCP Convergence Layer Properties\n");
			process.write("dtn-help dtn-set <topic> for more specific info\n");
		}
	}
	
	/**
	 * Execute 'set' command.  Dispatches to lower level
	 * @param process shellprocess
	 * @throws InterruptedException if interrupted
	 */
	private void set(CommandProcess process) throws InterruptedException {
		List<String> words=process.args();
		if (words.size() < 2) {
			process.write("Yeah, but set what?\n");
			return;
		}
		
		String command = getMatchAmongAlts(process,
				words.get(1),
				"general ltp bp link neighbor tcpcl udpcl");
		if (command.equalsIgnoreCase("general")) {
			setGeneral(process);
			
		} else if (command.equalsIgnoreCase("ltp")) {
			setLtp(process);
			
		} else if (command.equalsIgnoreCase("bp")) {
			setBp(process);
			
		} else if (command.equalsIgnoreCase("link")) {
			setLink(process);
			
		} else if (command.equalsIgnoreCase("neighbor")) {
			setNeighbor(process);
			
		} else if (command.equalsIgnoreCase("tcpcl")) {
			setTcpCl(process);
			
		} else if (command.equalsIgnoreCase("udpcl")) {
			setUdpCl(process);
			
		} else {
			process.write("Does 'dtn-set " + command + "' make sense to you?\n");
			process.write("No, me neither\n");
		}
	}
	
	/**
	 * Provide help for 'set general' command
	 * @param process shellprocess
	 */
	private void helpSetGeneral(CommandProcess process) {
		process.write(" dtn-set general storagePath <path>\n");
		process.write(" dtn-set general mediaRepository <path>\n");
		process.write(" dtn-set general debugLogging <true|false>\n");
	}
	
	/**
	 * Execute 'set general' command; set general configuration property
	 * @param process shellprocess
	 */
	private void setGeneral(CommandProcess process) {
		List<String> words=process.args();
		if (words.size() < 2) {
			process.write("Missing property name\n");
			return;
		}
		if (words.size() < 3) {
			process.write("Missing property value\n");
			return;
		}
		String propName = getMatchAmongAlts(process,words.get(1), "storagePath mediaRepository debugLogging");
		if (propName.equalsIgnoreCase("storagePath")) {
			String path = words.get(2);
			File file = new File(path);
			if (!file.exists()) {
				process.write("Path '" + path + "' doesn't exist\n");
				return;
			}
			if (!file.isDirectory()) {
				process.write("Path '" + path + "' is not a directory\n");
				return;
			}
			process.write("Setting storagePath=" + file.getAbsolutePath()+"\n");
			GeneralManagement.getInstance().setStoragePath(file.getAbsolutePath());
			
		} else if (propName.equalsIgnoreCase("mediaRepository")) {
			String path = words.get(2);
			File file = new File(path);
			if (!file.exists()) {
				process.write("Path '" + path + "' doesn't exist\n");
				return;
			}
			if (!file.isDirectory()) {
				process.write("Path '" + path + "' is not a directory\n");
				return;
			}
			process.write("Setting mediaRepository=" + file.getAbsolutePath()+"\n");
			GeneralManagement.getInstance().setMediaRepositoryPath(file.getAbsolutePath());
			
		} else if (propName.equalsIgnoreCase("debugLogging")) {
			String boolValue = getMatchAmongAlts(process,words.get(2), "true false");
			if (boolValue.equalsIgnoreCase("true")) {
				GeneralManagement.setDebugLogging(true);
			} else if (boolValue.equalsIgnoreCase("false")) {
				GeneralManagement.setDebugLogging(false);
			} else {
				process.write("Invalid boolean value: " + boolValue+"\n");
			}
			
		} else if (propName.equalsIgnoreCase("mySegmentRateLimit")) {
			double mySegRateLimit = 0.0;
			try {
				mySegRateLimit = Double.parseDouble(words.get(2));
			} catch (NumberFormatException e) {
				process.write("Invalid double value for 'mySegmentRateLimit'\n");
				return;
			}
			process.write("Setting mySegmentRateLimit=" + mySegRateLimit+"\n");
			GeneralManagement.getInstance().setMySegmentRateLimit(mySegRateLimit);
			
		} else if (propName.equalsIgnoreCase("myBurstSize")) {
			long myBurstSize = 0;
			try {
				myBurstSize = Long.parseLong(words.get(2));
			} catch (NumberFormatException e) {
				process.write("Invalid long value for 'myBurstSize'\n");
				return;
			}
			process.write("Setting myBurstSize=" + myBurstSize+"\n");
			GeneralManagement.getInstance().setMyBurstSize(myBurstSize);
			
		} else {
			process.write("Bad property name: " + propName+"\n");
		}
	}
	
	/**
	 * Provide help for 'set ltp' command
	 * @param process shellprocess
	 */
	private void helpSetLtp(CommandProcess process) {
		process.write(" dtn-set ltp engineId <engineId>        : Engine ID for this LTP instance\n");
		process.write(" dtn-set ltp maxRetransmits <n>         : Max number of retransmissions for Checkpoints\n");
		process.write(" dtn-set ltp maxReportRetransmits <n>   : Max number of retransmissions for Report Segments\n");
		process.write(" dtn-set ltp udpPort <n>                : UDP Port number for LTP over UDP\n");
		process.write(" dtn-set ltp receiveBuffer <n>          : UDP Receive buffer size\n");
		process.write(" dtn-set ltp testInterface <interface>  : Interface name to be used in unit testing\n");
		process.write(" dtn-set ltp blockFileThreshold <n>     : Threshold to decide whether Block should be in memory or in a File\n");
		process.write(" dtn-set ltp segmentFileThreshold <n>   : Threshold to decide whether Segment should be in memory or in a File\n");
		process.write(" dtn-set ltp loglinkOperStateChanges <t/f> : Log (or not) link operational state changes\n");
	}
	
	/**
	 * Execute 'set ltp' command; set LTP property
	 * @param process shellprocess
	 */
	private void setLtp(CommandProcess process) {
		List<String> words=process.args();
		if (words.size() < 2) {
			process.write("Missing property name\n");
			return;
		}
		if (words.size() < 3) {
			process.write("Missing property value\n");
			return;
		}
		String propName = getMatchAmongAlts(process,
				words.get(1),
				"engineId maxRetransmits maxReportRetransmits udpPort " +
				"receiveBuffer testInterface blockFileThreshold " +
				"segmentFileThreshold logLinkOperStateChanges");
		String propValue = words.get(2);
		if (propName.equalsIgnoreCase("engineId")) {
			EngineId engineId = null;
			try {
				engineId = new EngineId(propValue);
			} catch (LtpException e) {
				process.write(e.getMessage()+"\n");
				return;
			}
			process.write("Setting EngineId=" + propValue+"\n");
			LtpManagement.getInstance().setEngineId(engineId);
			
		} else if (propName.equalsIgnoreCase("maxRetransmits")) {
			try {
				int iVal = Integer.parseInt(propValue);
				process.write("Setting LtpMaxRetransmits=" + iVal+"\n");
				LtpManagement.getInstance().setLtpMaxRetransmits(iVal);
				
			} catch (NumberFormatException e) {
				process.write("Invalid integer: " + propValue+"\n");
			} catch (LtpException e) {
				process.write(e.getMessage()+"\n");
			}
			
		} else if (propName.equalsIgnoreCase("maxReportRetransmits")) {
			try {
				int iVal = Integer.parseInt(propValue);
				process.write("Setting maxReportRetransmits=" + iVal+"\n");
				LtpManagement.getInstance().setLtpMaxReportRetransmits(iVal);
				
			} catch (NumberFormatException e) {
				process.write("Invalid integer: " + propValue+"\n");
			} catch (LtpException e) {
				process.write(e.getMessage()+"\n");
			}
			
			
		} else if (propName.equalsIgnoreCase("udpPort")) {
			try {
				int iVal = Integer.parseInt(propValue);
				process.write("Setting udpPort=" + iVal+"\n");
				LtpManagement.getInstance().setLtpUdpPort(iVal);
				
			} catch (NumberFormatException e) {
				process.write("Invalid integer: " + propValue+"\n");
			} catch (LtpException e) {
				process.write(e.getMessage()+"\n");
			}
			
		} else if (propName.equalsIgnoreCase("receiveBuffer")) {
			try {
				int iVal = Integer.parseInt(propValue);
				process.write("Setting LtpUdpRecvBuffer=" + iVal+"\n");
				LtpManagement.getInstance().setLtpUdpRecvBufferSize(iVal);
				
			} catch (NumberFormatException e) {
				process.write("Invalid integer: " + propValue+"\n");
			} catch (LtpException e) {
				process.write(e.getMessage()+"\n");
			}
			
			
		} else if (propName.equalsIgnoreCase("testInterface")) {
			process.write("Setting testInterface=" + propValue+"\n");
			LtpManagement.getInstance().setTestInterface(propValue);
			
		} else if (propName.equalsIgnoreCase("blockFileThreshold")) {
			try {
				int iVal = Integer.parseInt(propValue);
				process.write("Setting blockLengthFileThreshold=" + iVal+"\n");
				LtpManagement.getInstance().setBlockLengthFileThreshold(iVal);
				
			} catch (NumberFormatException e) {
				process.write("Invalid integer: " + propValue+"\n");
			} catch (LtpException e) {
				process.write(e.getMessage()+"\n");
			}
			
			
		} else if (propName.equalsIgnoreCase("segmentFileThreshold")) {
			try {
				int iVal = Integer.parseInt(propValue);
				process.write("Setting segmentLengthFileThreshold=" + iVal+"\n");
				LtpManagement.getInstance().setSegmentLengthFileThreshold(iVal);
				
			} catch (NumberFormatException e) {
				process.write("Invalid integer: " + propValue+"\n");
			} catch (LtpException e) {
				process.write(e.getMessage()+"\n");
			}
			
		} else if (propName.equalsIgnoreCase("logLinkOperStateChanges")) {
			boolean bVal = Boolean.parseBoolean(propValue);
			process.write("Setting logLinkOperStateChanges=" + bVal+"\n");
			LtpManagement.getInstance().setLogLinkOperStateChanges(bVal);
			
		} else {
			process.write("Invalid property name: " + propName+"\n");
		}
	}
	
	/**
	 * Provide help for 'set bp' command
	 * @param process shellprocess
	 */
	private void helpSetBp(CommandProcess process) {
		process.write(" dtn-set bp bundleFileThreshold <n>     : Threshold above which  block body stored in file rather than in memory\n");
		process.write(" dtn-set bp eid <eid>                   : EndPointId stem for all traffic to this BP Node\n");
		process.write(" dtn-set bp statusReportsLength <n>     : Max number of items in StatusReports List \n");
		process.write(" dtn-set bp bulkColor <red|green>       : Color for Forwarded Bulk Bundles\n");
		process.write(" dtn-set bp normalColor <red|green>     : Color for Forwarded Normal Bundles\n");
		process.write(" dtn-set bp expeditedColor <red|green>  : Color for Forwarded Expedited Bundles\n");
		process.write(" dtn-set bp scheme {dtn|ipn}            : Global Endpoint ID Scheme\n");
		process.write(" dtn-set bp serviceId <n>               : Set the LTP Service access Point for BP\n");
		process.write(" dtn-set bp version <n>                 : Set the BP version for outgoing bundles\n");
		process.write(" dtn-set bp maxRetainedBytes <n>        : Set Max # retained bytes\n");
		process.write(" dtn-set bp holdBundleIfNoRoute <t/f>   : Hold bundle if no route found; false => reject Bundle\n");
	}
	
	/**
	 * Execute 'set bp' command; set BP property
	 * @param process shellprocess
	 */
	private void setBp(CommandProcess process) {
		List<String> words=process.args();

		if (words.size() < 2) {
			process.write("Missing property name\n");
			return;
		}
		if (words.size() < 3) {
			process.write("Missing property value\n");
			return;
		}
		String propName = getMatchAmongAlts(process,
				words.get(1),
				"bundleFileThreshold eid statusReportsLength bulkColor " + 
				"normalColor expeditedColor scheme serviceId version " +
				"maxRetainedBytes holdBundleIfNoRoute");
		String propValue = words.get(2);
		
		if (propName.equalsIgnoreCase("bundleFileThreshold")) {
			try {
				int iVal = Integer.parseInt(propValue);
				process.write("Setting BundleBlockFileThreshold=" + iVal+"\n");
				BPManagement.getInstance().setBundleBlockFileThreshold(iVal);
				
			} catch (NumberFormatException e) {
				process.write("Invalid integer: " + propValue+"\n");
			}			
			
		} else if (propName.equalsIgnoreCase("eid")) {
			try {
				EndPointId eid = EndPointId.createEndPointId(propValue);
				process.write("Setting EndPointIdStem=" + propValue+"\n");
				BPManagement.getInstance().setEndPointIdStem(eid);
				
			} catch (BPException e) {
				process.write(e.getMessage()+"\n");
			}
			
		} else if (propName.equalsIgnoreCase("statusReportsLength")) {
			try {
				int iVal = Integer.parseInt(propValue);
				process.write("Setting BundleStatusReportsListLength=" + iVal+"\n");
				BPManagement.getInstance().setBundleStatusReportsListLength(iVal);
				
			} catch (NumberFormatException e) {
				process.write("Invalid integer: " + propValue+"\n");
			}			
			
		} else if (propName.equalsIgnoreCase("bulkColor")) {
			propValue = getMatchAmongAlts(process,propValue, "red green");
			process.write("Setting bulkColor=" + propValue+"\n");
			if (propValue.equalsIgnoreCase("red")) {
				BPManagement.getInstance().setBulkBlockColor(BundleColor.RED);
			} else if (propValue.equalsIgnoreCase("green")) {
				BPManagement.getInstance().setBulkBlockColor(BundleColor.GREEN);
			} else {
				process.write("Invalid color: " + propValue+"\n");
			}
			
		} else if (propName.equalsIgnoreCase("normalColor")) {
			propValue = getMatchAmongAlts(process,propValue, "red green");
			process.write("Setting normalColor=" + propValue+"\n");
			if (propValue.equalsIgnoreCase("red")) {
				BPManagement.getInstance().setNormalBlockColor(BundleColor.RED);
			} else if (propValue.equalsIgnoreCase("green")) {
				BPManagement.getInstance().setNormalBlockColor(BundleColor.GREEN);
			} else {
				process.write("Invalid color: " + propValue+"\n");
			}

		} else if (propName.equalsIgnoreCase("expeditedColor")) {
			propValue = getMatchAmongAlts(process,propValue, "red green");
			process.write("Setting expeditedColor=" + propValue+"\n");
			if (propValue.equalsIgnoreCase("red")) {
				BPManagement.getInstance().setExpeditedBlockColor(BundleColor.RED);
			} else if (propValue.equalsIgnoreCase("green")) {
				BPManagement.getInstance().setExpeditedBlockColor(BundleColor.GREEN);
			} else {
				process.write("Invalid color: " + propValue+"\n");
			}
		
		} else if (propName.equalsIgnoreCase("scheme")) {
			propValue = getMatchAmongAlts(process,propValue, "dtn ipn");
			process.write("Setting scheme=" + propValue+"\n");
			process.write("Note that this configuration property is obsolete\n");
			process.write("Now, you should configure EID Schemes on the individual Neighbors\n");
			if (propValue.equalsIgnoreCase("dtn")) {
				BPManagement.getInstance().setEidScheme(EidScheme.DTN_EID_SCHEME);
			} else if (propValue.equalsIgnoreCase("ipn")) {
				BPManagement.getInstance().setEidScheme(EidScheme.IPN_EID_SCHEME);
			} else {
				process.write("Invalid scheme: " + propValue+"\n");
			}
			
		} else if (propName.equalsIgnoreCase("serviceId")) {
			try {
				int iVal = Integer.parseInt(propValue);
				process.write("Setting serviceId=" + propValue+"\n");
				BPManagement.getInstance().setBpServiceId(iVal);
				
			} catch (NumberFormatException e) {
				process.write("Invalid integer value: " + propValue+"\n");
			}
			
		} else if (propName.equalsIgnoreCase("version")) {
			try {
				int iVal = Integer.parseInt(propValue);
				process.write("Setting version=" + propValue+"\n");
				BPManagement.getInstance().setOutboundProtocolVersion(iVal);
				
			} catch (NumberFormatException e) {
				process.write("Invalid integer value: " + propValue+"\n");
			}			
			
		} else if (propName.equalsIgnoreCase("maxRetainedBytes")) {
			try {
				long lVal = Long.parseLong(propValue);
				process.write("Setting maxRetainedBytes=" + lVal+"\n");
				BPManagement.getInstance().setMaxRetainedBytes(lVal);
			} catch (NumberFormatException e) {
				process.write("Invalid long value: " + propValue+"\n");
			}
			
		} else if (propName.equalsIgnoreCase("holdBundleIfNoRoute")) {
			boolean bVal = Boolean.parseBoolean(propValue);
			process.write("Setting holdBundleIfNoRoute=" + bVal+"\n");
			BPManagement.getInstance().setHoldBundleIfNoRoute(bVal);
			
		} else {
			process.write("Invalid property name: " + propName+"\n");
		}
	}
	
	/**
	 * Provide help for 'set link' command
	 * @param process shellprocess
	 */
	private void helpSetLink(CommandProcess process) {
		process.write(" dtn-set link <linkName> adminState up|down\n");
		process.write(" dtn-set link <linkName> maxClaimCount <n>\n");
		process.write(" dtn-set link <linkName> maxFrameSize <n>\n");
		process.write(" dtn-set link <linkName> reportTimeout <n>\n");
		process.write(" dtn-set link <linkName> cancelTimeout <n>\n");
		process.write(" dtn-set link <linkName> checkpointTimeout <n>\n");
	}
	
	/**
	 * Execute 'set link' command; set properties on a particular link
	 * @param process shellprocess
	 */
	private void setLink(CommandProcess process) {
		List<String> words=process.args();

		if (words.size() < 4) {
			process.write("Incomplete 'set link' command\n");
			return;
		}
		String linkName = words.get(1);
		LtpLink link = LtpManagement.getInstance().findLtpLink(linkName);
		if (link == null) {
			process.write("No such link: " + linkName+"\n");
			return;
		}
		String which = getMatchAmongAlts(process,
				words.get(2),
				"adminState maxClaimCount maxFrameSize reportTimeout checkpointTimeout");
		String value = words.get(3);
		if (which.equalsIgnoreCase("adminState")) {
			String stateStr = getMatchAmongAlts(process,value, "up down");
			if (stateStr.equalsIgnoreCase("up")) {
				link.setLinkAdminUp(true);
				
			} else if (stateStr.equalsIgnoreCase("down")) {
				link.setLinkAdminUp(false);
				
			} else {
				process.write("Unrecognized adminState value: " + stateStr+"\n");
				return;
			}
			
		} else if (which.equalsIgnoreCase("maxClaimCount")) {
			int claimCount = 0;
			try {
				claimCount = Integer.parseInt(value);
				link.setMaxClaimCountPerReport(claimCount);
			} catch (NumberFormatException e) {
				process.write("Invalid integer value: " + words.get(3)+"\n");
				return;
			}
			
		} else if (which.equals("maxFrameSize")) {
			if (!(link instanceof LtpUDPLink)) {
				process.write("'maxFrameSize' can only be configured for UDPLinks\n");
				return;
			}
			LtpUDPLink udpLink = (LtpUDPLink)link;
			int maxFrameSize = 0;
			try {
				maxFrameSize = Integer.parseInt(value);
				udpLink.setMaxFrameSize(maxFrameSize);
				
			} catch (NumberFormatException e) {
				process.write("Invalid integer value for 'maxFrameSize': " + value+"\n");
				return;
			}
			
		} else if (which.equals("reportTimeout")) {
			int reportTimeout = 0;
			try {
				reportTimeout = Integer.parseInt(value);
				link.setReportTimeout(reportTimeout);

			} catch (NumberFormatException e) {
				process.write("Invalid integer value for 'reportTimeout': " + value+"\n");
				return;
			}
			
		} else if (which.equals("cancelTimeout")) {
			int cancelTimeout = 0;
			try {
				cancelTimeout = Integer.parseInt(value);
				link.setCancelTimeout(cancelTimeout);

			} catch (NumberFormatException e) {
				process.write("Invalid integer value for 'cancelTimeout': " + value+"\n");
				return;
			}
			
		} else if (which.equals("checkpointTimeout")) {
			int checkpointTimeout = 0;
			try {
				checkpointTimeout = Integer.parseInt(value);
				link.setCancelTimeout(checkpointTimeout);

			} catch (NumberFormatException e) {
				process.write("Invalid integer value for 'checkpointTimeout': " + value+"\n");
				return;
			}
			
		} else {
			process.write("Unrecognized 'dtn-set link' option: " + which+"\n");
			return;
		}
	}
	
	/**
	 * Provide help for 'dtn-set neighbor' command
	 * @param process shellprocess
	 */
	private void helpSetNeighbor(CommandProcess process) {
		process.write(" dtn-set neighbor <neighborName> adminState up|down\n");
		process.write(" dtn-set neighbor <neighborName> engineId <engineId>\n");
		process.write(" dtn-set neighbor <neighborName> scheduledState up|down\n");
		process.write(" dtn-set neighbor <neighborName> lightSeconds <lightSecs>\n");
		process.write(" dtn-set neighbor <neighborName> roundTripSlop <roundTripSlopMSecs>\n");
		process.write(" dtn-set neighbor <neighborName> eid <eid>\n");
		process.write(" dtn-set neighbor <neighborName> segmentXmitRateLimit <limit_segs_per_sec>\n");
		process.write(" dtn-set neighbor <neighborName> burstSize <segmentBurstSize>\n");
		process.write(" dtn-set neighbor <neighborName> scheme <dtn_or_ipn>\n");
	}
	
	/**
	 * Execute 'dtn-set neighbor' command; set property for a particular Neighbor
	 * @param process
	 * @throws InterruptedException if interrupted
	 */
	private void setNeighbor(CommandProcess process) throws InterruptedException {
		List<String> words=process.args();

		// set neighbor <neighborName> <paramName> <paramValue>
		// 0   1         2              3           4
		if (words.size() < 4) {
			process.write("Incomplete 'dtn-set neighbor' command\n");
			return;
		}
		String neighborName = words.get(1);
		String which = getMatchAmongAlts(process,
				words.get(2),
				"adminState engineId scheduledState lightSeconds roundTripSlop " +
				"eid scheme segmentXmitRateLimit burstSize");
		String value = words.get(3);
		
		Neighbor neighbor = NeighborsList.getInstance().findNeighborByName(neighborName);
		if (neighbor == null) {
			process.write("No such neighbor: '" + neighborName + "'\n");
			return;
		}
		
		if (which.equalsIgnoreCase("adminState")) {
			String stateStr = getMatchAmongAlts(process,value, "up down");
			if (stateStr.equalsIgnoreCase("up")) {
				neighbor.setNeighborAdminUp(true);
				
			} else if (stateStr.equalsIgnoreCase("down")) {
				neighbor.setNeighborAdminUp(false);
				
			} else {
				process.write("Unrecognized adminState value: " + stateStr+"\n");
				return;
			}
			
		} else if (which.equalsIgnoreCase("engineId")) {
			if (!(neighbor instanceof LtpNeighbor)) {
				process.write("Named neighbor is not a Ltp Neighbor\n");
				return;
			}
			LtpNeighbor ltpNeighbor = (LtpNeighbor)neighbor;
			try {
				EngineId engineId = new EngineId(value);
				ltpNeighbor.setEngineId(engineId);
				
			} catch (LtpException e) {
				process.write(e.getMessage()+"\n");
				return;
			}
			
		} else if (which.equalsIgnoreCase("scheduledState")) {
			String stateStr = getMatchAmongAlts(process,value, "up down");
			if (stateStr.equalsIgnoreCase("up")) {
				neighbor.setNeighborScheduledUp(true);
				
			} else if (stateStr.equalsIgnoreCase("down")) {
				neighbor.setNeighborScheduledUp(false);
				
			} else {
				process.write("Unrecognized scheduledState value: " + stateStr+"\n");
				return;
			}
			
		} else if (which.equalsIgnoreCase("lightSeconds")) {
			float lightSecs = 0.0f;
			try {
				lightSecs = Float.parseFloat(value);
				neighbor.setLightDistanceSecs(lightSecs);
				
			} catch (NumberFormatException e) {
				process.write("Invalid float value for lightSeconds: " + value+"\n");
				return;
			}
			
		} else if (which.equalsIgnoreCase("roundTripSlop")) {
			int slop = 0;
			try {
				slop = Integer.parseInt(value);
				neighbor.setRoundTripSlopMSecs(slop);
				
			} catch (NumberFormatException e) {
				process.write("Invalid integer value for roundTripSlop: " + value+"\n");
				return;
			}
			
		} else if (which.equalsIgnoreCase("eid")) {
			EndPointId eid = null;
			try {
				eid = EndPointId.createEndPointId(value);
			} catch (BPException e) {
				process.write(e.getMessage()+"\n");
				return;
			}
			neighbor.setEndPointIdStem(eid);
			
		} else if (which.equalsIgnoreCase("segmentXmitRateLimit")) {
			if (!(neighbor instanceof LtpNeighbor)) {
				process.write("Named neighbor is not a Ltp Neighbor\n");
				return;
			}
			LtpNeighbor ltpNeighbor = (LtpNeighbor)neighbor;
			double rateLimit = 0.0d;
			try {
				rateLimit = Double.parseDouble(value);
				ltpNeighbor.setSegmentXmitRateLimit(rateLimit);
			} catch (NumberFormatException ex) {
				process.write("Invalid double value for segmentXmitRateLimit: " + value+"\n");
				return;
			}
			
		} else if (which.equalsIgnoreCase("burstSize")) {
			if (!(neighbor instanceof LtpNeighbor)) {
				process.write("Named neighbor is not a Ltp Neighbor\n");
				return;
			}
			LtpNeighbor ltpNeighbor = (LtpNeighbor)neighbor;
			long burstSize = 0L;
			try {
				burstSize = Long.parseLong(value);
				ltpNeighbor.setBurstSize(burstSize);
			} catch (NumberFormatException ex) {
				process.write("Invalid long value for burstSize: " + value+"\n");
				return;
			}
				
		} else if (which.equalsIgnoreCase("scheme")) {
			try {
				EidScheme scheme = EidScheme.parseEidScheme(value);
				neighbor.setEidScheme(scheme);
				
			} catch (BPException e) {
				process.write(e.getMessage()+"\n");
			}
			
		} else {
			process.write("Unrecognized 'set neighbor' option: " + words.get(4)+"\n");
			return;
		}
	}
	
	/**
	 * Help for 'set tcpcl' command
	 * @param process args
	 */
	private void helpSetTcpCl(CommandProcess process) {
		process.write(" dtn-set tcpcl link <linkName> maxSegmentSize <n>\n");
		process.write(" dtn-set tcpcl link <linkName> tcpPort <n>\n");
		process.write(" dtn-set tcpcl neighbor <neighborName> acks <true|false>\n");
		process.write(" dtn-set tcpcl neighbor <neighborName> keepAlives <true|false>\n");
		process.write(" dtn-set tcpcl neighbor <neighborName> keepAliveSecs <n>\n");
		process.write(" dtn-set tcpcl neighbor <neighborName> delayBeforeReconnect <true|false>\n");
		process.write(" dtn-set tcpcl neighbor <neighborName> reconnectDelaySecs <n>\n");
		process.write(" dtn-set tcpcl neighbor <neighborName> idleShutdown <true|false>\n");
		process.write(" dtn-set tcpcl neighbor <neighborName> idleDelaySecs <n>\n");
	}
	
	/**
	 * Execute 'set tcpcl' command
	 * @param process args
	 * @throws InterruptedException 
	 */
	private void setTcpCl(CommandProcess process) throws InterruptedException {
		List<String> words=process.args();

		// set tcpcl ...
		if (words.size() >= 2) {
			String discrim = getMatchAmongAlts(process,words.get(1), "link neighbor");
			if (discrim.equalsIgnoreCase("link")) {
				setTcpClLink(process);
			} else if (discrim.equalsIgnoreCase("neighbor")) {
				setTcpClNeighbor(process);
			} else {
				process.write("I don't understand 'set tcpcl " + words.get(1) + "'\n");
			}
		} else {
			process.write("Missing arguments on 'set tcpcl' command\n");
		}
	}
	
	/**
	 * Execute 'set tcpcl link' command
	 * @param process args
	 * @throws InterruptedException 
	 */
	private void setTcpClLink(CommandProcess process) throws InterruptedException {
		List<String> words=process.args();

		// set tcpcl link <linkName> <paramName> <paramValue>
		// 0   1     2     3         4           5
		if (words.size() != 5) {
			process.write("Incorrect number of arguments for 'set tcpcl link ...\n");
			return;
		}
		String linkName = words.get(2);
		Link link = LinksList.getInstance().findLinkByName(linkName);
		if (link == null) {
			process.write("No Link named '" + linkName + "'\n");
			return;
		}
		if (!(link instanceof TcpClLink)) {
			process.write("Link named '" + linkName + "' is not a TcpCl Link\n");
			return;
		}
		TcpClLink tcpClLink = (TcpClLink)link;
		String param = getMatchAmongAlts(process,words.get(3), "maxSegmentSize tcpPort");
		if (param.equalsIgnoreCase("maxSegmentSize")) {
			// set tcpcl link <linkName> maxSegmentSize <n>");
			// 0   1     2    3          4              5
			try {
				int n = Integer.parseInt(words.get(4));
				tcpClLink.setMaxSegmentSize(n);
			} catch (NumberFormatException e) {
				process.write("Invalid integer argument: " + words.get(4)+"\n");
				return;
			} catch (IllegalArgumentException e) {
				process.write(e.getMessage()+"\n");
			}
			
		} else if (param.equalsIgnoreCase("tcpPort")) {
			// set tcpcl link <linkName> tcpPort <n>
			// 0   1     2    3          4       5
			try {
				int n = Integer.parseInt(words.get(4));
				tcpClLink.setTcpPort(n);
			} catch (NumberFormatException e) {
				process.write("Invalid integer argument: " + words.get(4)+"\n");
				return;
			} catch (IllegalArgumentException e) {
				process.write(e.getMessage()+"\n");
			}
			
		} else {
			process.write("I don't understand 'set tcpcl link " + linkName + words.get(3) + "'\n");
		}
			
	}
	
	/**
	 * Execute 'set tcpcl neighbor' command
	 * @param process args
	 * @throws InterruptedException 
	 */
	private void setTcpClNeighbor(CommandProcess process) throws InterruptedException {
		List<String> words=process.args();

		// set tcpcl neighbor <neighborName> <paramName> <paramValue>
		// 0   1     2         3              4          5
		if (words.size() != 5) {
			process.write("Incorrect number of arguments for 'set tcpcl neighbor'\n");
			return;
		}
		String neighborName = words.get(2);
		Neighbor neighbor = NeighborsList.getInstance().findNeighborByName(neighborName);
		if (neighbor == null) {
			process.write("No neighbor named '" + neighborName + "'\n");
			return;
		}
		if (!(neighbor instanceof TcpClNeighbor)) {
			process.write("Neighbor named '" + neighborName + "' is not a TcpCl Neighbor\n");
			return;
		}
		TcpClNeighbor tcpClNeighbor = (TcpClNeighbor)neighbor;
		String paramName = getMatchAmongAlts(process,words.get(3),
				"acks keepAlives " +
				"keepAliveSecs delayBeforeReconnect reconnectDelaySecs " +
				"idleShutdown idleDelaySecs");
			
		if (paramName.equalsIgnoreCase("acks")) {
			boolean paramValue = Boolean.parseBoolean(words.get(4));
			tcpClNeighbor.setAckDataSegments(paramValue);
			
		} else if (paramName.equalsIgnoreCase("keepAlives")) {
			boolean paramValue = Boolean.parseBoolean(words.get(4));
			tcpClNeighbor.setKeepAlive(paramValue);
			
		} else if (paramName.equalsIgnoreCase("keepAliveSecs")) {
			try {
				int n = Integer.parseInt(words.get(4));
				tcpClNeighbor.setKeepAliveIntervalSecs(n);
			} catch (NumberFormatException e) {
				process.write("Invalid keepAliveIntervalSecs: " + words.get(4)+"\n");
			} catch (IllegalArgumentException e) {
				process.write(e.getMessage()+"\n");
			}
			
		} else if (paramName.equalsIgnoreCase("delayBeforeReconnect")) {
			boolean paramValue = Boolean.parseBoolean(words.get(4));
			tcpClNeighbor.setDelayBeforeReconnection(paramValue);
			
		} else if (paramName.equalsIgnoreCase("reconnectDelaySecs")) {
			try {
				int n = Integer.parseInt(words.get(5));
				tcpClNeighbor.setReconnectionDelaySecs(n);
			} catch (NumberFormatException e) {
				process.write("Invalid value for ReconnectDelay: " + words.get(4)+"\n");
			} catch (IllegalArgumentException e) {
				process.write(e.getMessage()+"\n");
			}
			
		} else if (paramName.equalsIgnoreCase("idleShutdown")) {
			boolean paramValue = Boolean.parseBoolean(words.get(4));
			tcpClNeighbor.setIdleConnectionShutdown(paramValue);
			
		} else if (paramName.equalsIgnoreCase("idleDelaySecs")) {
			try {
				int n = Integer.parseInt(words.get(4));
				tcpClNeighbor.setIdleConnectionShutdownDelaySecs(n);
			} catch (NumberFormatException e) {
				process.write("Invalid value for 'idleDelaySecs': " + words.get(4)+"\n");
			} catch (IllegalArgumentException e) {
				process.write(e.getMessage()+"\n");
			}
			
		} else {
			process.write("I don't understand 'set tcpcl neighbor " +
					words.get(3) + " ...\n");
		}
		 				
	}
	
	/**
	 * Provide help for 'set udpcl' command
	 * @param process shellprocess
	 */
	private void helpSetUdpCl(CommandProcess process) {
		process.write(" dtn-set udpcl link <linkName> udpPort <n>\n");
		process.write(" dtn-set udpcl neighbor <neighborName> segmentRateLimit <n>\n");
		process.write(" dtn-set udpcl neighbor <neighborName> burstSize <n>\n");
	}
	
	/**
	 * Execute the 'set udpcl' command.  Dispatches for variants.
	 * @param process shellprocess
	 */
	private void setUdpCl(CommandProcess process) {
		List<String> words=process.args();

		// set udpcl ...
		if (words.size() >= 2) {
			String discrim = getMatchAmongAlts(process,words.get(1), "link neighbor");
			if (discrim.equalsIgnoreCase("link")) {
				setUdpClLink(process);
			} else if (discrim.equalsIgnoreCase("neighbor")) {
				setUdpClNeighbor(process);
			} else {
				process.write("I don't understand 'set udpcl " + words.get(1) + "'\n");
			}
		} else {
			process.write("Missing arguments on 'set udpcl' command\n");
		}
	}
	
	/**
	 * Execute the 'set udpcl link' command
	 * @param process shellprocess
	 */
	private void setUdpClLink(CommandProcess process) {
		List<String> words=process.args();

		// set udpcl link <linkName> <paramName> <paramValue>
		// 0   1     2     3         4           5
		if (words.size() != 5) {
			process.write("Incorrect number of arguments for 'set udpcl link ...\n");
			return;
		}
		String linkName = words.get(2);
		Link link = LinksList.getInstance().findLinkByName(linkName);
		if (link == null) {
			process.write("No Link named '" + linkName + "'\n");
			return;
		}
		if (!(link instanceof UdpClLink)) {
			process.write("Link named '" + linkName + "' is not a UdpCl Link\n");
			return;
		}
		UdpClLink udpClLink = (UdpClLink)link;
		String param = getMatchAmongAlts(process,words.get(3), "udpPort");
		if (param.equalsIgnoreCase("udpPort")) {
			// set udpcl link <linkName> udpPort <n>"
			try {
				int udpPort = Integer.parseInt(words.get(4));
				udpClLink.setUdpPort(udpPort);
			} catch (NumberFormatException e) {
				process.write(
						"Invalid integer udpPort specified: " + 
						words.get(4)+"\n");
				return;
			}
			
		} else {
			process.write("Unrecognized option to set udpcl link command: " + 
					words.get(3)+"\n");
			return;
		}
	}
	
	/**
	 * Execute 'set udpcl neighbor' command
	 * @param process shellprocess
	 */
	private void setUdpClNeighbor(CommandProcess process) {
		List<String> words=process.args();

		// set udpcl neighbor <neighborName> <paramname> <paramValue>
		// 0   1     2        3              4           5
		if (words.size() != 5) {
			process.write("Incorrect number of arguments for 'dtn0set udpcl neighbor'\n");
			return;
		}
		String neighborName = words.get(2);
		Neighbor neighbor = NeighborsList.getInstance().findNeighborByName(neighborName);
		if (neighbor == null) {
			process.write("No neighbor named '" + neighborName + "'\n");
			return;
		}
		if (!(neighbor instanceof UdpClNeighbor)) {
			process.write("Neighbor named '" + neighborName + "' is not a UdpCl Neighbor\n");
			return;
		}
		UdpClNeighbor udpClNeighbor = (UdpClNeighbor)neighbor;
		String paramName = getMatchAmongAlts(process,
				words.get(3),
				"segmentRateLimit burstSize");
			
		if (paramName.equalsIgnoreCase("segmentRateLimit")) {
			// set udpcl neighbor <neighborName> segmentRateLimit <n>
			// 0   1     2        3              4                5
			try {
				double segRate = Double.parseDouble(words.get(4));
				udpClNeighbor.setSegmentXmitRateLimit(segRate);
			} catch (NumberFormatException e) {
				process.write(
						"Invalid double value for 'segmentRateLimit': " +
						words.get(4)+"\n");
			}
			
		} else if (paramName.equalsIgnoreCase("burstSize")) {
			// set udpcl neighbor <neighborName> burstSize <n>
			// 0   1     2        3              4         5
			try {
				long burstSize = Long.parseLong(words.get(4));
				udpClNeighbor.setBurstSize(burstSize);
			} catch (NumberFormatException e) {
				process.write(
						"Invalid long value for 'burstSize'" +
						words.get(4)+"\n");
			}
		}
	}
	
	/**
	 * Provide help for 'clean' command
	 * @param process Not used
	 */
	private void helpClean(CommandProcess process) {
		process.write(" dtn-clean                     Clean media repository and bundles\n");
		process.write(" dtn-clean media               Clean media repository\n");
		process.write(" dtn-clean bundles             Clean bundles\n");
	}
	
	/**
	 * Execute the 'clean' command; Clean the Media Repository and/or Bundles
	 * @param process shellprocess
	 */
	private void clean(CommandProcess process) {
		List<String> words=process.args();

		if (words.size() <= 1) {
			// clean
			process.write("Cleaning media repository\n");
			MediaRepository.getInstance().clean();
			process.write("Cleaning Bundles\n");
			Store.getInstance().clean();
			return;
		}
		String which = getMatchAmongAlts(process,words.get(1), "media bundles");
		if (which.equalsIgnoreCase("media")) {
			process.write("Cleaning media repository\n");
			MediaRepository.getInstance().clean();
		} else if (which.equalsIgnoreCase("bundles")) {
			process.write("Cleaning Bundles\n");
			Store.getInstance().clean();
		} else {
			process.write("I don't understand 'clean " + words.get(1) + "'\n");
		}
	}
	
	/**
	 * Provide help for 'clear' command'
	 * @param process not used
	 */
	private void helpClear(CommandProcess process) {
		process.write(" dtn-clear statistics            Clear LTP and BP statistics\n");
		process.write(" dtn-clear ltp statistics        Clear LTP statistics\n");
		process.write(" dtn-clear bp statistics         Clear BP statistics\n");
		process.write(" dtn-clear tcpcl statistics      Clear TcpCl statistics\n");
		process.write(" dtn-clear bp statusReports      Clear logged status reports\n");
		process.write(" dtn-clear link <linkName>       Clear link statistics\n");
	}
	
	/**
	 * Execute the 'clear' command; Clear statistics
	 * @param process shellprocess
	 */
	private void clear(CommandProcess process) {
		List<String> words=process.args();

		if (words.size() < 1) {
			process.write("Yeah, but clear what?\n");
			return;
		}
		String which = getMatchAmongAlts(process,
				words.get(0),
				"statistics ltp bp link udpcl tcpcl");
		if (which.equalsIgnoreCase("statistics")) {
			LtpManagement.getInstance().getLtpStats().clear();
			BPManagement.getInstance().getBpStats().clear();
		} else if (which.equalsIgnoreCase("ltp")) {
			clearLtp(process);
		} else if (which.equalsIgnoreCase("bp")) {
			clearBp(process);
		} else if (which.equalsIgnoreCase("link")) {
			clearLink(process);
		} else if (which.equalsIgnoreCase("tcpcl")) {
			clearTcpCl(process);
		} else if (which.equalsIgnoreCase("udpcl")) {
			clearUdpCl(process);
		} else {
			process.write("Invalid 'dtn-clear' option\n");
		}
	}
	
	/**
	 * Execute 'clear ltp' command
	 * @param process shellprocess
	 */
	private void clearLtp(CommandProcess process) {
		List<String> words=process.args();

		if (words.size() < 2) {
			process.write("Missing option from 'dtn-clear ltp' command\n");
			return;
		}
		String which = getMatchAmongAlts(process,words.get(1), "statistics");
		if (which.equalsIgnoreCase("statistics")) {
			LtpManagement.getInstance().getLtpStats().clear();
		} else {
			process.write("Invalid 'dtn-clear ltp' option: " + which+"\n");
		}
	}
	
	/**
	 * Execute 'clear bp' command
	 * @param process shellprocess
	 */
	private void clearBp(CommandProcess process) {
		List<String> words=process.args();

		if (words.size() < 2) {
			process.write("Missing option from 'dtn-clear bp' command\n");
			return;
		}
		String which = getMatchAmongAlts(process,words.get(1), "statistics statusReports");
		if (which.equalsIgnoreCase("statistics")) {
			BPManagement.getInstance().getBpStats().clear();
		} else if (which.equalsIgnoreCase("statusReports")) {
			BPManagement.getInstance().clearBundleStatusReportsList();
		} else {
			process.write("Invalid 'dtn-clear bp' option: " + which+"\n");
		}
	}
	
	/**
	 * Execute 'clear ltpcl' command
	 * @param process shellprocess
	 */
	private void clearTcpCl(CommandProcess process) {
		List<String> words=process.args();

		if (words.size() != 2) {
			process.write("Incorrect number of arguments to 'dtn-clear tcpcl' command\n");
			return;
		}
		String which = getMatchAmongAlts(process,words.get(1), "statistics");
		if (which.equalsIgnoreCase("statistics")) {
			TcpClManagement.getInstance().clearStatistics();
		} else {
			process.write("Invalid 'dtn-clear tcpcl' option: " + which+"\n");
		}
	}
	
	/**
	 * Execute 'clear udpcl' command
	 * @param process shellprocess
	 */
	private void clearUdpCl(CommandProcess process) {
		List<String> words=process.args();

		if (words.size() != 2) {
			process.write("Incorrect number of arguments to 'dtn-clear udpcl' command\n");
			return;
		}
		String which = getMatchAmongAlts(process,words.get(1), "statistics");
		if (which.equalsIgnoreCase("statistics")) {
			UdpClManagement.getInstance().clearStatistics();
		} else {
			process.write("Invalid 'dtn-clear udpcl' option: " + which+"\n");
		}
	}
		
	/**
	 * Execute 'clear link' command
	 * @param process shellprocess
	 */
	private void clearLink(CommandProcess process) {
		List<String> words=process.args();

		// clear link <linkName>
		if (words.size() < 3) {
			process.write("Missing <linkName> from 'dtn-clear link' command\n");
			return;
		}
		String linkName = words.get(2);
		LtpLink link = LtpManagement.getInstance().findLtpLink(linkName);
		if (link == null) {
			process.write("No such link: '" + linkName + "'\n");
			return;
		}
		link.clearStatistics();
	}
	
	/**
	 * Provide help for 'add' command
	 * @param process shellprocess
	 */
	private void helpAdd(CommandProcess process) {
		process.write(" dtn-add link <linkName> <ifName> {ipv6}                     : Add LTP Link\n");
		process.write(" dtn-add neighbor <nName> {<EngineId>}                       : Add LTP Neighbor\n");
		process.write(" dtn-add neighbor <nName> -link <lName> <ipAddress>          : Add Link and IPAddress to Neighbor\n");
		process.write(" dtn-add route <eidPattern> <name> <linkName> <neighborName> : Add BP Route\n");
		process.write(" dtn-add defaultRoute <name> <linkName> <neighborName>       : Add BP Default Route\n");
		process.write(" dtn-add application <name> <className> <appArg>...          : Add Application\n");
		process.write(" dtn-add tcpcl link <lName> <ifName> {ipv6}                  : Add TcpCl Link\n");
		process.write(" dtn-add tcpcl neighbor <nName> <EndPointId>                 : Add TcpCl Neighbor\n");
		process.write(" dtn-add tcpcl neighbor <nName> -link <lName> <ipAddress>    : Add Link and IPAddress to Neighbor\n");
		process.write(" dtn-add udpcl link <lName> <ifName> {ipv6}                  : Add UdpCl Link\n");
		process.write(" dtn-add udpcl neighbor <nName> <EndPointId>                 : Add UdpCl Neighbor\n");
		process.write(" dtn-add udpcl neighbor <nName> -link <lName> <ipAddress>    : Add Link and IPAddress to Neighbor\n");
		process.write(" dtn-add eidMap <dtnEid> <ipnEid>                            : Add ipn <=> dtn EndPointId Mapping\n");
	}
	
	/**
	 * Execute the 'add' command; dispatches to lower level
	 * @param process shellprocess
	 */
	private void add(CommandProcess process) {
		List<String> words=process.args();

		if (words.size() < 1) {
			process.write("Missing {link|neighbor|route|application|etc\n");
			return;
		}
		String which = getMatchAmongAlts(process,
				words.get(0),
				"link neighbor route defaultRoute application tcpcl udpcl eidMap");
		
		if (which.equalsIgnoreCase("link")) {
			addLink(process);
			
		} else if (which.equalsIgnoreCase("neighbor")) {
			addNeighborVariations(process);
			
		} else if (which.equalsIgnoreCase("route")) {
			addRoute(process);
			
		} else if (which.equalsIgnoreCase("defaultRoute")) {
			addDefaultRoute(process);
			
		} else if (which.equalsIgnoreCase("application")) {
			addApplication(process);
		
		} else if (which.equalsIgnoreCase("tcpcl")) {
			addTcpCl(process);
			
		} else if (which.equalsIgnoreCase("udpcl")) {
			addUdpCl(process);
			
		} else if (which.equalsIgnoreCase("eidMap")) {
			addEidMap(process);
			
		} else {
			process.write("I'm not sure what you want to add: '" + which + "' is not in my vocabulary");
		}
	}

	/**
	 * Execute the 'add application' command; add a particular application
	 * @param process shellprocess
	 */
	private void addApplication(CommandProcess process) {
		List<String> words=process.args();

		// add application <name> <className> <arg>...
		if (words.size() < 4) {
			process.write("Missing arguments in 'dtn-add application' command\n");
			return;
		}
		String appName = words.get(2);
		String appClassName = words.get(3);
		int nArgs = words.size() - 4;
		String[] args = null;
		if (nArgs > 0) {
			args = new String[nArgs];
			for (int ix = 4; ix < words.size(); ix++) {
				args[ix - 4] = words.get(ix);
			}
		}
		try {
			GeneralManagement.getInstance().addApplication(appName, appClassName, args);
		} catch (JDtnException e) {
			process.write(e.getMessage()+"\n");
			e.printStackTrace();
			return;
		}
	}

	/**
	 * Execute the 'add defaultRoute' command; add the default route
	 * @param process shellprocess
	 */
	private void addDefaultRoute(CommandProcess process) {
		List<String> words=process.args();

		// add defaultRoute <name> <linkName> <neighborName>
		if (words.size() < 5) {
			process.write("Missing arguments in 'add defaultRoute' command\n");
			return;
		}
		String routeName = words.get(2);
		String linkName = words.get(3);
		String neighborName = words.get(4);

		try {
			process.write("Adding DefaultRoute" +
					" routeName=" + routeName +
					", linkName=" + linkName +
					", neighborName=" + neighborName+"\n");
			BPManagement.getInstance().setDefaultRoute(routeName, linkName, neighborName);
		} catch (BPException e) {
			process.write(e.getMessage()+"\n");
			return;
		}
	}

	/**
	 * Execute the 'add route' command; add a new route
	 * @param process shellprocess
	 */
	private void addRoute(CommandProcess process) {
		List<String> words=process.args();

		// add route <eidPattern> <name> <linkName> <neighborName>
		if (words.size() < 6) {
			process.write("Missing arguments in 'dtn-add route' command\n");
			return;
		}
		String eidPattern = words.get(2);
		String routeName = words.get(3);
		String linkName = words.get(4);
		String neighborName = words.get(5);

		try {
			process.write("Adding Route" +
					" routeName=" + routeName +
					", eidPattern=" + eidPattern +
					", linkName=" + linkName +
					", neighborName=" + neighborName+"\n");
			BPManagement.getInstance().addRoute(routeName, eidPattern, linkName, neighborName);

		} catch (BPException e) {
			process.write(e.getMessage()+"\n");
			return;
		}
	}

	/**
	 * Parses and dispatches variations on 'add neighbor' command
	 * @param process command arguments
	 */
	private void addNeighborVariations(CommandProcess process) {
		List<String> words=process.args();

		// add neighbor <nName> <ipAddress> {<engineId>}
		// 0   1         2       3           4
		// or
		// add neighbor <nName> -link <lName> <ipAddress>
		// 0   1         2       3     4       5
		if (words.size() < 4) {
			process.write("Missing arguments from 'dtn-add neighbor' command\n");
			return;
		}
		String option = getMatchAmongAlts(process,words.get(3), "-link", true);
		if (option.equalsIgnoreCase("-link")) {
			addLinkToNeighbor(process);
		} else {
			addNeighbor(process);
		}

	}

	/**
	 * Execute the 'add neighbor' command; add a new neighbor
	 * @param process shellprocess
	 */
	private void addNeighbor(CommandProcess process) {
		List<String> words=process.args();

		// add neighbor <nName> {<EngineId>}
		// 0   1         2        3
		if (words.size() < 3) {
			process.write("Missing arguments from 'dtn-add neighbor' command\n");
			return;
		}
		String neighborName = words.get(2);
		EngineId engineId = null;
		if (words.size() >= 4) {
			try {
				engineId = new EngineId(words.get(3));
			} catch (LtpException e) {
				process.write(e.getMessage()+"\n");
				return;
			}
		} else {
			engineId = new EngineId();
		}
		try {
			process.write("Adding Neighbor" +
					", engineId=" + engineId.getEngineIdString() +
					", neighborName=" + neighborName+"\n");
			LtpManagement.getInstance().addUDPNeighbor(engineId, neighborName);
		} catch (Exception e) {
			process.write(e.getMessage()+"\n");
		}
	}

	/**
	 * Execute 'add neighbor <nName> -link' command.  Add Link to Neighbor.
	 * @param process shellprocess
	 */
	private void addLinkToNeighbor(CommandProcess process) {
		List<String> words=process.args();

		// add neighbor <nName> -link <lName> <ipAddress>
		// 0   1         2       3     4       5
		if (words.size() < 6) {
			process.write("Incomplete 'dtn-add neighbor <nName> -link command\n");
			return;
		}
		String nName = words.get(2);
		String lName = words.get(4);
		String ipAddrStr = words.get(5);

		IPAddress ipAddr = null;
		try {
			ipAddr = new IPAddress(ipAddrStr);
		} catch (UnknownHostException e) {
			process.write("Invalid IP Address: " + e.getMessage()+"\n");
			return;
		}

		LtpNeighbor neighbor = LtpManagement.getInstance().findNeighbor(nName);
		if (neighbor == null) {
			process.write("No such neighbor: " + nName+"\n");
			return;
		}
		LtpLink link = LtpManagement.getInstance().findLtpLink(lName);
		if (link == null) {
			process.write("No such link: " + lName+"\n");
			return;
		}
		LinkAddress linkAddress = new LinkAddress(link, ipAddr);
		neighbor.addLinkAddress(linkAddress);
	}

	/**
	 * Execute the 'add link' command; add a new Link
	 * @param process shellprocess
	 */
	private void addLink(CommandProcess process) {
		List<String> words=process.args();

		// add link <linkName> <ifName> {ipv6}
		if (words.size() < 4) {
			process.write("Missing arguments from 'dtn-add link' command\n");
			return;
		}
		String linkName = words.get(2);
		String ifName = words.get(3);
		boolean wantIpv6 = false;
		if (words.size() >= 5) {
			if (words.get(4).equalsIgnoreCase("ipv6")) {
				wantIpv6 = true;
			} else {
				process.write("Expected 'ipv6': " + words.get(4)+"\n");
				return;
			}
		}
		try {
			process.write(
					"Adding Link linkName=" + linkName +
							", ifName=" + ifName +
							", wantIpv6=" + wantIpv6+"\n");
			LtpManagement.getInstance().addUDPLink(linkName, ifName, wantIpv6);
		} catch (LtpException e) {
			process.write(e.getMessage()+"\n");
			return;
		}
	}

	/**
	 * Execute the 'add tcpcl ..." command
	 * @param process shellprocess
	 */
	private void addTcpCl(CommandProcess process) {
		List<String> words=process.args();

		// add tcpcl link ...
		// OR
		// add tcpcl neighbor ...
		if (words.size() >= 2) {
			String which = getMatchAmongAlts(process,words.get(1), "link neighbor");
			if (which.equalsIgnoreCase("link")) {
				addTcpClLink(process);
			} else if (which.equalsIgnoreCase("neighbor")) {
				addTcpClNeighborVariations(process);

			} else {
				process.write("I don't understand 'dtn-add tcpcl " + words.get(1) + "'\n");
			}

		} else {
			process.write("Insufficient number of arguments for 'dtn-add tcpcl' command\n");
		}
	}

	/**
	 * Execute the 'add tcpcl link ..." command
	 * @param process shellprocess
	 */
	private void addTcpClLink(CommandProcess process) {
		List<String> words=process.args();

		// add tcpcl link <lName> <ifName> {ipv6}
		// 0   1     2    3       4        5
		if (words.size() == 4 || words.size() == 5) {
			String linkName = words.get(3);
			if (LinksList.getInstance().findLinkByName(linkName) != null) {
				process.write("Already a Link named '" + linkName + "'\n");
				return;
			}
			String ifName = words.get(4);
			boolean wantIpv6 = false;
			if (words.size() == 6) {
				if (words.get(5).equalsIgnoreCase("ipv6")) {
					wantIpv6 = true;
				} else {
					process.write("Expected 'ipv6': " + words.get(5)+"\n");
					return;
				}
			}
			try {
				process.write(
						"Adding Link linkName=" + linkName +
								", ifName=" + ifName +
								", wantIpv6=" + wantIpv6+"\n");
				TcpClManagement.getInstance().addLink(linkName, ifName, wantIpv6);
			} catch (JDtnException e) {
				process.write(e.getMessage()+"\n");
				return;
			}

		} else {
			process.write("Wrong number of arguments for 'dtn-add tcpcl link' command\n");
		}
	}

	/**
	 * Execute the 'add tcpcl neighbor ..." command
	 * @param process shellprocess
	 */
	private void addTcpClNeighborVariations(CommandProcess process) {
		List<String> words=process.args();

		// At this point, we're guaranteed length >= 3
		// add tcpcl neighbor <nName> <eid>
		// 0   1     2        3       4
		// OR
		// add tcpcl neighbor <nName> -link <lName> <ipAddress>
		// 0   1     2        3       4     5        6
		if (words.size() < 5) {
			process.write("Missing arguments from 'dtn-add tcpcl neighbor' command\n");
			return;
		}
		String neighborName = words.get(3);
		if (!words.get(4).equalsIgnoreCase("-link")) {
			// add tcpcl neighbor <nName> <eid>
			// 0   1     2        3       4
			if (words.size() != 5) {
				process.write(
						"Incorrect number of arguments for 'dtn-add tcpcl neighbor " +
						"<nName> <eid>\n");
			}
			try {
				EndPointId eid = EndPointId.createEndPointId(words.get(4));
				process.write(
						"Adding Neighbor neighborName=" + neighborName +
								" eid=" + eid+"\n");
				TcpClManagement.getInstance().addNeighbor(neighborName, eid);
			} catch (BPException e) {
				process.write(e.getMessage()+"\n");
			} catch (JDtnException e) {
				process.write(e.getMessage()+"\n");
			}
		} else if (words.size() == 7) {
			// add tcpcl neighbor <nName> -link <lName> <ipAddress>
			// 0   1     2        3       4     5        6
			String linkName = words.get(5);
			Link link = LinksList.getInstance().findLinkByName(linkName);
			if (link == null) {
				process.write("No Link named '" + linkName + "'\n");
				return;
			}
			IPAddress ipAddress = null;
			try {
				ipAddress = new IPAddress(words.get(6));
			} catch (UnknownHostException e) {
				process.write("Unknown host or bad IPAddress: " + words.get(6)+"\n");
				return;
			}
			Neighbor neighbor =
				NeighborsList.getInstance().findNeighborByName(neighborName);
			if (neighbor == null) {
				process.write("No Neighbor named '" + neighborName + "'\n");
				return;
			}
			LinkAddress linkAddress = new LinkAddress(link, ipAddress);
			process.write(
					"Adding LinkAddress link=" + linkName +
							" address=" + ipAddress +
							" to Neighbor " + neighborName+"\n");
			neighbor.addLinkAddress(linkAddress);
		}

	}

	/**
	 * Execute the 'add udpcl' command.  Dispatch for variations.
	 * @param process shellprocess
	 */
	private void addUdpCl(CommandProcess process) {
		List<String> words=process.args();

		// add udpcl link ...
		// OR
		// add udpcl neighbor ...
		if (words.size() >= 2) {
			String which = getMatchAmongAlts(process,words.get(1), "link neighbor");
			if (which.equalsIgnoreCase("link")) {
				addUdpClLink(process);
			} else if (which.equalsIgnoreCase("neighbor")) {
				addUdpClNeighborVariations(process);

			} else {
				process.write("I don't understand 'dtn-add udpcl " + words.get(1) + "'\n");
			}

		} else {
			process.write("Insufficient number of arguments for 'dtn-add udpcl' command\n");
		}
	}

	/**
	 * Execute the 'add udpcl link' command.
	 * @param process shellprocess
	 */
	private void addUdpClLink(CommandProcess process) {
		List<String> words=process.args();

		// add udpcl link <lName> <ifName> {ipv6}
		// 0   1     2    3       4        5
		if (words.size() == 4 || words.size() == 5) {
			String linkName = words.get(3);
			if (LinksList.getInstance().findLinkByName(linkName) != null) {
				process.write("Already a Link named '" + linkName + "'\n");
				return;
			}
			String ifName = words.get(4);
			boolean wantIpv6 = false;
			if (words.size() == 6) {
				if (words.get(5).equalsIgnoreCase("ipv6")) {
					wantIpv6 = true;
				} else {
					process.write("Expected 'ipv6': " + words.get(5)+"\n");
					return;
				}
			}
			try {
				process.write(
						"Adding Link linkName=" + linkName +
								", ifName=" + ifName +
								", wantIpv6=" + wantIpv6+"\n");
				UdpClManagement.getInstance().addLink(linkName, ifName, wantIpv6);
			} catch (JDtnException e) {
				process.write(e.getMessage()+"\n");
				return;
			}

		} else {
			process.write("Wrong number of arguments for 'dtn-add tcpcl link' command\n");
		}
	}

	/**
	 * Execute the 'add udpcl neighbor' command
	 * @param process shellprocess
	 */
	private void addUdpClNeighborVariations(CommandProcess process) {
		List<String> words=process.args();

		// At this point, we're guaranteed length >= 3
		// add udpcl neighbor <nName> <eid>
		// 0   1     2        3       4
		// OR
		// add udpcl neighbor <nName> -link <lName> <ipAddress>
		// 0   1     2        3       4     5        6
		if (words.size() < 5) {
			process.write("Missing arguments from 'dtn-add udpcl neighbor' command\n");
			return;
		}
		String neighborName = words.get(3);
		if (neighborName.equals("-link")) {
			process.write("You forgot to include Neighbor Name before '-link' modifier\n");
			return;
		}
		if (!words.get(4).equalsIgnoreCase("-link")) {
			// add udpcl neighbor <nName> <eid>
			// 0   1     2        3       4
			if (words.size() != 5) {
				process.write(
						"Incorrect number of arguments for 'dtn-add udpcl neighbor " +
						"<nName> <eid>\n");
				return;
			}
			try {
				EndPointId eid = EndPointId.createEndPointId(words.get(4));
				process.write(
						"Adding Neighbor neighborName=" + neighborName +
								" eid=" + eid+"\n");
				UdpClManagement.getInstance().addNeighbor(neighborName, eid);
			} catch (BPException e) {
				process.write(e.getMessage()+"\n");
			} catch (JDtnException e) {
				process.write(e.getMessage()+"\n");
			}
		} else if (words.size() == 7) {
			// add udpcl neighbor <nName> -link <lName> <ipAddress>
			// 0   1     2        3       4     5        6
			String linkName = words.get(5);
			Link link = LinksList.getInstance().findLinkByName(linkName);
			if (link == null) {
				process.write("No Link named '" + linkName + "'\n");
				return;
			}
			IPAddress ipAddress = null;
			try {
				ipAddress = new IPAddress(words.get(6));
			} catch (UnknownHostException e) {
				process.write("Unknown host or bad IPAddress: " + words.get(6)+"\n");
				return;
			}
			Neighbor neighbor =
				NeighborsList.getInstance().findNeighborByName(neighborName);
			if (neighbor == null) {
				process.write("No Neighbor named '" + neighborName + "'\n");
				return;
			}
			LinkAddress linkAddress = new LinkAddress(link, ipAddress);
			process.write(
					"Adding LinkAddress link=" + linkName +
							" address=" + ipAddress +
							" to Neighbor " + neighborName+"\n");
			neighbor.addLinkAddress(linkAddress);
		}
	}

	/**
	 * Execute 'add eidMap' command
	 * @param process shellprocess
	 */
	private void addEidMap(CommandProcess process) {
		List<String> words=process.args();

		// add eidMap <dtnEid> <ipnEid>
		// 0   1      2        3
		if (words.size() != 4) {
			process.write("Incorrect number of arguments\n");
			return;
		}
		try {
			EidMap.getInstance().addMapping(words.get(2), words.get(3));
		} catch (BPException e) {
			process.write(e.getMessage()+"\n");
		}
	}

	/**
	 * Provide help for 'remove' command
	 * @param process shellprocess
	 */
	private void helpRemove(CommandProcess process) {
		process.write(" dtn-remove link <linkName>                      : Remove LTP Link\n");
		process.write(" dtn-remove neighbor <neighborName>              : Remove LTP Neighbor\n");
		process.write(" dtn-remove route <routeName>                    : Remove BP Route\n");
		process.write(" dtn-remove defaultRoute                         : Remove BP Default Route\n");
		process.write(" dtn-remove application <name>                   : Remove application\n");
		process.write(" dtn-remove ltpcl link <linkName>                : Remove TcpCl Link\n");
		process.write(" dtn-remove ltpcl neighbor <neighborName>        : Remove TcpCl Neighbor\n");
		process.write(" dtn-remove eidMap <dtnEid>                      : Remove dtn <=> ipn EndPointId Mapping\n");
	}

	/**
	 * Execute the 'remove' command; dispatch to lower level
	 * @param process shellprocess
	 * @throws InterruptedException
	 */
	private void remove(CommandProcess process) throws InterruptedException {
		List<String> words=process.args();

		if (words.size() < 1) {
			process.write("Yeah, but what do you want to delete?\n");
			return;
		}
		String which = getMatchAmongAlts(process,words.get(0),
				"link neighbor route defaultRoute application tcpcl udpcl eidMap");

		if (which.equalsIgnoreCase("link")) {
			removeLink(process);

		} else if (which.equalsIgnoreCase("neighbor")) {
			removeNeighbor(process);

		} else if (which.equalsIgnoreCase("route")) {
			removeRoute(process);

		} else if (which.equalsIgnoreCase("defaultRoute")) {
			process.write("Removing BP Default Route\n");
			BPManagement.getInstance().removeDefaultRoute();

		} else if (which.equalsIgnoreCase("application")) {
			removeApplication(process);

		} else if (which.equalsIgnoreCase("tcpcl")) {
			removeTcpCl(process);

		} else if (which.equalsIgnoreCase("udpcl")) {
			removeUdpCl(process);

		} else if (which.equalsIgnoreCase("eidMap")) {
			removeEidMap(process);

		} else {
			process.write("remove '" +	which + "' is not in my vocabulary\n");
		}

	}

	/**
	 * Execute the 'remove application' command; remove a previously installed
	 * application.
	 * @param process shellprocess
	 * @throws InterruptedException
	 */
	private void removeApplication(CommandProcess process) throws InterruptedException {
		List<String> words=process.args();

		// remove application <name>
		if (words.size() < 3) {
			process.write("Missing '<name>' argument\n");
			return;
		}
		String appName = words.get(2);
		try {
			GeneralManagement.getInstance().removeApplication(appName);
		} catch (JDtnException e) {
			process.write(e.getMessage()+"\n");
			return;
		}
	}

	/**
	 * Execute the 'remove route' command; remove a previously installed
	 * route.
	 * @param process shellprocess
	 */
	private void removeRoute(CommandProcess process) {
		List<String> words=process.args();

		// remove route <routeName>
		if (words.size() < 3) {
			process.write("Missing arguments from 'dtn-remove route' command\n");
			return;
		}
		String routeName = words.get(2);
		try {
			process.write("Removing route routeName=" + routeName+"\n");
			BPManagement.getInstance().removeRoute(routeName);
		} catch (BPException e) {
			process.write(e.getMessage()+"\n");
		}
	}

	/**
	 * Execute the 'remove neighbor' command; remove a previously installed
	 * neighbor
	 * @param process shellprocess
	 */
	private void removeNeighbor(CommandProcess process) {
		List<String> words=process.args();

		// remove neighbor <neighborName>
		// 0      1         2
		if (words.size() < 3) {
			process.write("Missing arguments from 'dtn-remove neighbor' command\n");
			return;
		}
		String neighborName = words.get(2);
		LtpNeighbor neighbor = LtpManagement.getInstance().findNeighbor(neighborName);
		if (neighbor == null || !(neighbor instanceof LtpUDPNeighbor)) {
			process.write("No such neighbor:  " + neighborName+"\n");
			return;
		}
		try {
			process.write("Removing Neighbor" +
					" neighborName=" + neighbor.getName()+"\n");
			LtpManagement.getInstance().removeUDPNeighbor((LtpUDPNeighbor)neighbor);
		} catch (JDtnException e) {
			process.write(e.getMessage()+"\n");
		}
	}

	/**
	 * Execute the 'remove link' command; remove a previously installed Link.
	 * @param process
	 * @throws InterruptedException
	 */
	private void removeLink(CommandProcess process) throws InterruptedException {
		List<String> words=process.args();

		// remove link <linkName>
		if (words.size() < 3) {
			process.write("Missing arguments from 'dtn-remove link' command\n");
			return;
		}
		String linkName = words.get(2);
		LtpLink link = LtpManagement.getInstance().findLtpLink(linkName);
		if (link == null || !(link instanceof LtpUDPLink)) {
			process.write("No such Link: " + linkName+"\n");
			return;
		}
		try {
			process.write("Removing Link linkName=" + linkName+"\n");
			LtpManagement.getInstance().removeLink(link);
		} catch (JDtnException e) {
			process.write(e.getMessage()+"\n");
			return;
		}
	}

	/**
	 * Execute the 'remove tcpcl' command
	 * @param process shellprocess
	 * @throws InterruptedException
	 */
	private void removeTcpCl(CommandProcess process) throws InterruptedException {
		List<String> words=process.args();

		// remove tcpcl neighbor <neighborName>
		// remove tcpcl link     <linkName>
		// 0      1     2        3
		if (words.size() != 3) {
			process.write("Incorrect number of arguments for 'dtn-remove tcpcl' command\n");
			return;
		}
		String which = getMatchAmongAlts(process,words.get(1), "neighbor link");
		if (which.equalsIgnoreCase("link")) {
			String linkName = words.get(2);
			Link link = LinksList.getInstance().findLinkByName(linkName);
			if (link == null) {
				process.write("No Link named '" + linkName + "'\n");
				return;
			}
			try {
				TcpClManagement.getInstance().removeLink(linkName);
			} catch (JDtnException e) {
				process.write(e.getMessage()+"\n");
			}

		} else if (which.equalsIgnoreCase("neighbor")) {
			String neighborName = words.get(2);
			Neighbor neighbor =
				NeighborsList.getInstance().findNeighborByName(neighborName);
			if (neighbor == null) {
				process.write("No Neighbor named '" + neighborName + "'\n");
				return;
			}
			try {
				TcpClManagement.getInstance().removeNeighbor(neighborName);
			} catch (JDtnException e) {
				process.write(e.getMessage()+"\n");
			}
		}
	}

	/**
	 * Execute the 'remove udpcl' command
	 * @param process shellprocess
	 * @throws InterruptedException
	 */
	private void removeUdpCl(CommandProcess process) throws InterruptedException {
		List<String> words=process.args();

		// remove udpcl neighbor <neighborName>
		// remove udpcl link     <linkName>
		// 0      1     2        3
		if (words.size() != 3) {
			process.write("Incorrect number of arguments for 'dtn-remove udpcl' command\n");
			return;
		}
		String which = getMatchAmongAlts(process,words.get(1), "neighbor link");
		if (which.equalsIgnoreCase("link")) {
			String linkName = words.get(2);
			Link link = LinksList.getInstance().findLinkByName(linkName);
			if (link == null) {
				process.write("No Link named '" + linkName + "'\n");
				return;
			}
			try {
				UdpClManagement.getInstance().removeLink(linkName);
			} catch (JDtnException e) {
				process.write(e.getMessage()+"\n");
			}

		} else if (which.equalsIgnoreCase("neighbor")) {
			String neighborName = words.get(2);
			Neighbor neighbor =
				NeighborsList.getInstance().findNeighborByName(neighborName);
			if (neighbor == null) {
				process.write("No Neighbor named '" + neighborName + "'\n");
				return;
			}
			try {
				UdpClManagement.getInstance().removeNeighbor(neighborName);
			} catch (JDtnException e) {
				process.write(e.getMessage()+"\n");
			}
		}
	}

	/**
	 * Execute 'remove eidMap' command
	 * @param process shellprocess
	 */
	private void removeEidMap(CommandProcess process) {
		List<String> words=process.args();

		// remove eidMap <dtnEid>
		// 0      1      2
		if (words.size() != 3) {
			process.write("Incorrect number of arguments\n");
			return;
		}
		try {
			EidMap.getInstance().removeMapping(words.get(2));
		} catch (BPException e) {
			process.write(e.getMessage()+"\n");
		}
	}

	/**
	 * Display help for 'start' command
	 * @param process not used
	 */
	private void helpStart(CommandProcess process) {
		process.write("dtn-start tcpcl link <linkName>\n");
		process.write("dtn-start tcpcl neighbor <neighborName>\n");
		process.write("dtn-start udpcl link <linkName>\n");
		process.write("dtn-start udpcl neighbor <neighborName>\n");
	}

	/**
	 * Execute 'start' command
	 * @param process shellprocess
	 */
	private void start(CommandProcess process) {
		List<String> words=process.args();

		// start tcpcl link     <linkName>
		// start tcpcl neighbor <neighborName>
		// start udpcl link     <linkName>
		// start udpcl neighbor <neighborName>
		// 0     1     2        3
		if (words.size() != 3) {
			process.write("Incorrect number of options for 'dtn-start' command\n");
			return;
		}
		String which = getMatchAmongAlts(process,words.get(0), "tcpcl udpcl", true);
		if (which.equalsIgnoreCase("tcpcl")) {
			which = getMatchAmongAlts(process,words.get(1), "link neighbor");
			if (which.equalsIgnoreCase("link")) {
				String linkName = words.get(2);
				Link link = LinksList.getInstance().findLinkByName(linkName);
				if (link == null) {
					process.write("No Link named '" + linkName + "'\n");
					return;
				}
				if (!(link instanceof TcpClLink)) {
					process.write("Link '" + linkName + "' is not a TcpCl link\n");
					return;
				}
				TcpClLink tcpClLink = (TcpClLink)link;
				tcpClLink.start();

			} else if (which.equalsIgnoreCase("neighbor")) {
				String neighborName = words.get(2);
				Neighbor neighbor = NeighborsList.getInstance().findNeighborByName(neighborName);
				if (neighbor == null) {
					process.write("No Neighbor named '" + neighborName + "'\n");
					return;
				}
				if (!(neighbor instanceof TcpClNeighbor)) {
					process.write("Neighbor '" + neighborName + "' is not a TcpCl Neighbor\n");
					return;
				}
				TcpClNeighbor tcpClNeighbor = (TcpClNeighbor)neighbor;
				tcpClNeighbor.start();
			}
		} else if (which.equalsIgnoreCase("udpcl")) {
			which = getMatchAmongAlts(process,words.get(1), "link neighbor");
			if (which.equalsIgnoreCase("link")) {
				String linkName = words.get(2);
				Link link = LinksList.getInstance().findLinkByName(linkName);
				if (link == null) {
					process.write("No Link named '" + linkName + "'\n");
					return;
				}
				if (!(link instanceof UdpClLink)) {
					process.write("Link '" + linkName + "' is not a UdpCl link\n");
					return;
				}
				UdpClLink udpClLink = (UdpClLink)link;
				udpClLink.start();

			} else if (which.equalsIgnoreCase("neighbor")) {
				String neighborName = words.get(2);
				Neighbor neighbor = NeighborsList.getInstance().findNeighborByName(neighborName);
				if (neighbor == null) {
					process.write("No Neighbor named '" + neighborName + "'\n");
					return;
				}
				if (!(neighbor instanceof UdpClNeighbor)) {
					process.write("Neighbor '" + neighborName + "' is not a UdpCl Neighbor\n");
					return;
				}
				UdpClNeighbor udpClNeighbor = (UdpClNeighbor)neighbor;
				udpClNeighbor.start();
			}

		} else {
			process.write("Expected 'tcpcl' or 'udpcl' as second word of command\n");
			return;
		}

	}

	/**
	 * Display help for 'stop' command
	 * @param process not used
	 */
	private void helpStop(CommandProcess process) {
		process.write("dtn-stop tcpcl link <linkName>\n");
		process.write("dtn-stop tcpcl neighbor <neighborName>\n");
		process.write("dtn-stop udpcl link <linkName>\n");
		process.write("dtn-stop udpcl neighbor <neighborName>\n");
	}

	/**
	 * Execute 'stop' command
	 * @param process shellprocess
	 * @throws InterruptedException
	 */
	private void stop(CommandProcess process) throws InterruptedException {
		List<String> words=process.args();

		// stop tcpcl link     <linkName>
		// stop tcpcl neighbor <neighborName>
		// stop udpcl link     <linkName>
		// stop udpcl neighbor <neighborName>
		// 0     1     2        3
		if (words.size() != 3) {
			process.write("Incorrect number of options for 'dtn-stop' command\n");
			return;
		}
		String which = getMatchAmongAlts(process,words.get(0), "tcpcl udpcl", true);
		if (which.equalsIgnoreCase("tcpcl")) {
			which = getMatchAmongAlts(process,words.get(1), "link neighbor");
			if (which.equalsIgnoreCase("link")) {
				String linkName = words.get(2);
				Link link = LinksList.getInstance().findLinkByName(linkName);
				if (link == null) {
					process.write("No Link named '" + linkName + "'\n");
					return;
				}
				if (!(link instanceof TcpClLink)) {
					process.write("Link '" + linkName + "' is not a TcpCl link\n");
					return;
				}
				TcpClLink tcpClLink = (TcpClLink)link;
				tcpClLink.stop();

			} else if (which.equalsIgnoreCase("neighbor")) {
				String neighborName = words.get(2);
				Neighbor neighbor = NeighborsList.getInstance().findNeighborByName(neighborName);
				if (neighbor == null) {
					process.write("No Neighbor named '" + neighborName + "'\n");
					return;
				}
				if (!(neighbor instanceof UdpClNeighbor)) {
					process.write("Neighbor '" + neighborName + "' is not a TcpCl Neighbor\n");
					return;
				}
				UdpClNeighbor udpClNeighbor = (UdpClNeighbor)neighbor;
				udpClNeighbor.stop();

			} else {
				process.write("Invalid 'stop' command option: " + words.get(2)+"\n");
			}

		} else if (which.equalsIgnoreCase("udpcl")) {
			which = getMatchAmongAlts(process,words.get(1), "link neighbor");
			if (which.equalsIgnoreCase("link")) {
				String linkName = words.get(2);
				Link link = LinksList.getInstance().findLinkByName(linkName);
				if (link == null) {
					process.write("No Link named '" + linkName + "'\n");
					return;
				}
				if (!(link instanceof UdpClLink)) {
					process.write("Link '" + linkName + "' is not a TcpCl link\n");
					return;
				}
				UdpClLink tcpClLink = (UdpClLink)link;
				tcpClLink.stop();

			} else if (which.equalsIgnoreCase("neighbor")) {
				String neighborName = words.get(2);
				Neighbor neighbor = NeighborsList.getInstance().findNeighborByName(neighborName);
				if (neighbor == null) {
					process.write("No Neighbor named '" + neighborName + "'\n");
					return;
				}
				if (!(neighbor instanceof UdpClNeighbor)) {
					process.write("Neighbor '" + neighborName + "' is not a TcpCl Neighbor\n");
					return;
				}
				UdpClNeighbor udpClNeighbor = (UdpClNeighbor)neighbor;
				udpClNeighbor.stop();

			} else {
				process.write("Invalid 'stop' command option: " + words.get(1)+"\n");
			}

		} else {
			process.write("Expected 'tcpcl' as second word of command\n");
			return;
		}

	}

	/**
	 * Provide help for eid and bundling options used in various commands which
	 * send Bundles.
	 */
	private void helpOptions(CommandProcess process) {
		process.write("         <eid> = destination EndPointId\n");
		process.write("         Options:\n");
		process.write("         -red => Send Bundle Red (Reliable)\n");
		process.write("         -transferCustody => request custody transfer\n");
		process.write("         -custodyReport => Report when custody accepted\n");
		process.write("         -receiveReport => Report when Bundle received\n");
		process.write("         -forwardReport => Report when Bundle forwarded\n");
		process.write("         -deliverReport => Report when Bundle delivered\n");
		process.write("         -deleteReport => Report when Bundle deleted due to anomalous conditions\n");
		process.write("         -lifetime <n> => Set Bundle lifetime to 'n' seconds\n");
		process.write("         -bulk => Bulk class of service/priority\n");
		process.write("         -normal => Normal class of service/priority\n");
		process.write("         -expedited => Expedited class of service/priority\n");
	}

	/**
	 * Decipher command options for 'text' command, and fill things out
	 * appropriately.
	 * @param startCmdIndex First command argument to start processing
	 * @param appName Application name (e.g. 'Text')
	 * @param process command arguments
	 * @param options Populated based on command arguments
	 * @param text Populated based on the text to send
	 * @return The Destination EID specified in the command.
	 */
	private EndPointId decipherSendOrTextOptions(
			int startCmdIndex,
			String appName,
			CommandProcess process,
			BundleOptions options,
			StringBuffer text) {
		List<String> words=process.args();

		EndPointId destEid = null;
		boolean processingOptions = true;
		for (int ix = startCmdIndex; ix < words.size(); ix++) {
			if (processingOptions) {
				// Processing options phase
				ix = decipherBundleOptions(ix, process, options);
				if (ix < 0) {
					return null;
				} else {
					// Doesn't start with "-"; end of processing options
					// this word should be eid
					// then go to processing text phase
					try {
						if (appName == null) {
							destEid = EndPointId.createEndPointId(words.get(ix));
						} else {
							destEid = EndPointId.createEndPointId(words.get(ix) + "/" + appName);
						}
					} catch (BPException e) {
						process.write(e.getMessage()+"\n");
						return null;
					}
					processingOptions = false;
				}
			} else {
				// Processing text phase; append this word onto text string to send
				text.append(words.get(ix) + " ");
			}
		}
		if (destEid == null) {
			process.write("No <eid> specified\n");
			return null;
		}
		if (text.length() == 0) {
			process.write("No <words...> specified\n");
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
	 * @param process Command line arguments
	 * @param options Filled out based on command arguments concerning bundling
	 * options.
	 * @return Next index into words array beyond bundling options
	 */
	private int decipherBundleOptions(int ix, CommandProcess process, BundleOptions options) {
		List<String> words=process.args();

		for (; ix < words.size(); ix++) {
			if (words.get(ix).startsWith("-")) {
				// Starts with "-"; is an option
				String option = getMatchAmongAlts(process,
						words.get(ix),
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
					if (ix >= words.size()) {
						process.write("Missing <n> after -lifetime\n");
						return -1;
					}
					try {
						options.lifetime = Long.parseLong(words.get(ix));
					} catch (NumberFormatException e) {
						process.write("Invalid long int specified for lifetime\n");
						return -1;
					}

				} else {
					process.write("Invalid option: " + option+"\n");
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
	 * @param process shellprocess
	 */
	private void helpSend(CommandProcess process) {
		process.write("     Send file to given 'eid'\n");
		process.write("       sendFile {options} <eid> filePath\n");
		helpOptions(process);
	}

	/**
	 * Execute the 'send' command
	 * @param process shellprocess
	 * @throws InterruptedException if interrupted during process
	 */
	private void doSend(CommandProcess process) throws InterruptedException {
		List<String> words=process.args();

		// send {options} <eid> filePath
		if (words.size() < 2) {
			process.write("Incomplete 'dtn-send' command\n");
			return;
		}

		// Set up default bundling options
		BundleOptions options = new BundleOptions();	// Bundle Options
		options.classOfServicePriority = BPClassOfServicePriority.BULK;
		options.lifetime = 3600;	// Default lifetime 1 hour

		// Figure out command line bundling options
		int ix = decipherBundleOptions(1, process, options);
		if (ix < 0) {
			return;
		}

		// Command line <eid>
		if (ix >= words.size()) {
			process.write("<eid> omitted\n");
			return;
		}
		String eidStr = words.get(ix);
		EndPointId destEid;
		try {
			destEid = EndPointId.createEndPointId(eidStr);
		} catch (BPException e1) {
			process.write(e1.getMessage()+"\n");
			return;
		}

		// Command line <filePath>
		if (++ix >= words.size()) {
			process.write("<filePath> omitted\n");
			return;
		}
		String filePath = words.get(ix);

		// Send the File
		process.write("Sending " +
				" eid=" + destEid.getEndPointIdString() +
				" path=" + filePath +
				" options=" + options+"\n");
		Dtn2CpApp app = (Dtn2CpApp)AppManager.getInstance().getApp(Dtn2CpApp.APP_NAME);
		if (app == null) {
			process.write("'Dtn2Cp' application is not installed\n");
			return;
		}
		try {
			app.sendFile(new File(filePath), destEid, options);
		} catch (JDtnException e) {
			process.write(e.getMessage()+"\n");
		}
	}

	/**
	 * Provide help for 'text' command
	 * @param process shellprocess
	 */
	private void helpText(CommandProcess process) {
		process.write("     Send text note containing 'words...' to given 'eid'\n");
		process.write("       text {options} <eid> <words...> \n");
		process.write("         <words...> = Text to send in payload of Bundle\n");
		helpOptions(process);
	}

	/**
	 * Execute the 'text' command; send a Text Note to another node.
	 * @param process shellprocess
	 * @throws InterruptedException if interrupted during a wait
	 */
	private void text(CommandProcess process) throws InterruptedException {
		List<String> words=process.args();

		// text {options} <eid> <words...>
		if (words.size() < 2) {
			process.write("Incomplete 'text' command\n");
			return;
		}

		BundleOptions options = new BundleOptions();	// Bundle Options

		// Figure out command line
		StringBuffer text = new StringBuffer();			// Text to send
		EndPointId destEid = decipherSendOrTextOptions(1, null, process, options, text);
		if (destEid == null) {
			return;
		}

		// Send the Text Note
		process.write("Sending " +
				" eid=" + destEid.getEndPointIdString() +
				" text=" + text +
				" options=" + options+"\n");
		TextApp textApp = (TextApp)AppManager.getInstance().getApp(TextApp.APP_NAME);
		if (textApp == null) {
			process.write("'Text' application is not installed\n");
			return;
		}
		try {
			textApp.sendText(
					destEid.getEndPointIdString(),
					text.toString(),
					options);
		} catch (JDtnException e) {
			process.write(e.getMessage()+"\n");
		}
	}

	/**
	 * Provide help for 'photo' command
	 * @param process shellprocess
	 */
	public void helpPhoto(CommandProcess process) {
		process.write("     Send a photo note to given 'eid'\n");
		process.write("       photo {options} <eid> <photoFile> \n");
		process.write("         <photoFile - Path to a .jpg image\n");
		helpOptions(process);
	}

	/**
	 * Execute 'photo' command; send a Photo Note to a node.
	 * @param process shellprocess
	 * @throws InterruptedException if interrupted druing wait
	 */
	public void photo(CommandProcess process) throws InterruptedException {
		List<String> words=process.args();

		// photo {options} <eid> <photoFile>
		if (words.size() < 2) {
			process.write("Incomplete 'photo' command\n");
			return;
		}

		// Set up default bundling options
		BundleOptions options = new BundleOptions();	// Bundle Options
		options.classOfServicePriority = BPClassOfServicePriority.BULK;

		// Figure out command line bundling options
		int ix = decipherBundleOptions(1, process, options);
		if (ix < 0) {
			return;
		}

		// Command line <eid>
		if (ix >= words.size()) {
			process.write("<eid> omitted\n");
			return;
		}
		String eidStr = words.get(ix);
		EndPointId destEid;
		try {
			destEid = EndPointId.createEndPointId(eidStr);
		} catch (BPException e1) {
			process.write(e1.getMessage()+"\n");
			return;
		}

		// Command line <photoFile>
		if (++ix >= words.size()) {
			process.write("<photoFile> omitted\n");
			return;
		}
		String filePath = words.get(ix);

		// Send the Photo Note
		process.write("Sending " +
				" eid=" + destEid.getEndPointIdString() +
				" path=" + filePath +
				" options=" + options+"\n");
		PhotoApp photoApp = (PhotoApp)AppManager.getInstance().getApp(PhotoApp.APP_NAME);
		if (photoApp == null) {
			process.write("'Photo' application is not installed\n");
			return;
		}
		try {
			photoApp.sendPhoto(destEid.getEndPointIdString(), new File(filePath), options);
		} catch (JDtnException e) {
			process.write(e.getMessage()+"\n");
		}
	}

	/**
	 * Provide help for 'video' command
	 * @param process shellprocess
	 */
	public void helpVideo(CommandProcess process) {
		process.write("     Send a video note to given 'eid'\n");
		process.write("       video {options} <eid> <videoFile> \n");
		process.write("         <videoFile - Path to a .3gp video file\n");
		helpOptions(process);
	}

	/**
	 * Execute 'video' command; send a Video Note to a node.
	 * @param process shellprocess
	 * @throws InterruptedException if interrupted during wait
	 */
	public void video(CommandProcess process) throws InterruptedException {
		List<String> words=process.args();

		// video {options} <eid> <videoFile>
		if (words.size() < 2) {
			process.write("Incomplete 'video' command\n");
			return;
		}

		// Set up default bundling options
		BundleOptions options = new BundleOptions();	// Bundle Options
		options.classOfServicePriority = BPClassOfServicePriority.BULK;

		// Figure out command line bundling options
		int ix = decipherBundleOptions(1, process, options);
		if (ix < 0) {
			return;
		}

		// Command line <eid>
		if (ix >= words.size()) {
			process.write("<eid> omitted\n");
			return;
		}
		String eidStr = words.get(ix);
		EndPointId destEid;
		try {
			destEid = EndPointId.createEndPointId(eidStr);
		} catch (BPException e1) {
			process.write(e1.getMessage()+"\n");
			return;
		}

		// Command line <videoFile>
		if (++ix >= words.size()) {
			process.write("<videoFile> omitted\n");
			return;
		}
		String filePath = words.get(ix);

		// Send the Photo Note
		process.write("Sending " +
				" eid=" + destEid.getEndPointIdString() +
				" path=" + filePath +
				" options=" + options+"\n");
		VideoApp videoApp = (VideoApp)AppManager.getInstance().getApp(VideoApp.APP_NAME);
		if (videoApp == null) {
			process.write("'Video' application is not installed\n");
			return;
		}
		try {
			videoApp.sendVideo(destEid.getEndPointIdString(), new File(filePath), options);
		} catch (JDtnException e) {
			process.write(e.getMessage()+"\n");
		}
	}

	/**
	 * Provide help for 'voice' command
	 * @param process shellprocess
	 */
	public void helpVoice(CommandProcess process) {
		process.write("     Send a voice note to given 'eid'\n");
		process.write("       voice {options} <eid> <voiceFile> \n");
		process.write("         <voiceFile - Path to a .3gp audio file\n");
		helpOptions(process);
	}

	/**
	 * Execute 'voice' command; send a Voice Note to a node
	 * @param process shellprocess
	 * @throws InterruptedException if interrupted during wait
	 */
	public void voice(CommandProcess process) throws InterruptedException {
		List<String> words=process.args();

		// voice {options} <eid> <voiceFile>
		if (words.size() < 2) {
			process.write("Incomplete 'voice' command\n");
			return;
		}

		// Set up default bundling options
		BundleOptions options = new BundleOptions();	// Bundle Options
		options.classOfServicePriority = BPClassOfServicePriority.BULK;

		// Figure out command line bundling options
		int ix = decipherBundleOptions(1, process, options);
		if (ix < 0) {
			return;
		}

		// Command line <eid>
		if (ix >= words.size()) {
			process.write("<eid> omitted\n");
			return;
		}
		String eidStr = words.get(ix);
		EndPointId destEid;
		try {
			destEid = EndPointId.createEndPointId(eidStr);
		} catch (BPException e1) {
			process.write(e1.getMessage()+"\n");
			return;
		}

		// Command line <voiceFile>
		if (++ix >= words.size()) {
			process.write("<voiceFile> omitted\n");
			return;
		}
		String filePath = words.get(ix);

		// Send the Voice Note
		process.write("Sending " +
				" eid=" + destEid.getEndPointIdString() +
				" path=" + filePath +
				" options=" + options+"\n");
		VoiceApp voiceApp = (VoiceApp)AppManager.getInstance().getApp(VoiceApp.APP_NAME);
		if (voiceApp == null) {
			process.write("'Voice' application is not installed\n");
			return;
		}
		try {
			voiceApp.sendVoiceNote(destEid.getEndPointIdString(), new File(filePath), options);
		} catch (JDtnException e) {
			process.write(e.getMessage()+"\n");
		}
	}

	/**
	 * Provide help for 'ping' command
	 * @param process shellprocess
	 */
	private void helpPing(CommandProcess process) {
		process.write("     Send echo request(s); receive echo reply(s)\n");
		process.write("       ping destEid {count {lifetimeSecs}}\n");
	}

	/**
	 * Execute 'ping' command; send a ping request, receive a ping reply.
	 * @param process shellprocess
	 */
	private void ping(CommandProcess process) {
		List<String> words=process.args();

		// ping destEid {count {lifetimeSecs}}
		// 0    1       2       3
		if (words.size() < 2) {
			process.write("Missing 'destEid' argument to dtn-ping command\n");
			return;
		}
		String eidStr = words.get(1);
		EndPointId eid;
		try {
			eid = EndPointId.createEndPointId(eidStr);
		} catch (BPException e) {
			process.write(e.getMessage()+"\n");
			return;
		}
		int count;
		if (words.size() < 3) {
			count = 1;
		} else {
			try {
				count = Integer.parseInt(words.get(2));
				if (count < 1) {
					process.write("Invalid value for 'count' argument: " + words.get(2)+"\n");
					return;
				}
			} catch (NumberFormatException e) {
				process.write("Invalid integer for 'count' argument: " + words.get(2)+"\n");
				return;
			}
		}

		long lifetimeSecs;
		if (words.size() < 4) {
			lifetimeSecs = Dtn2PingApp.PING_LIFETIME_SECS;
		} else {
			try {
				lifetimeSecs = Long.parseLong(words.get(3));
			} catch (NumberFormatException e) {
				process.write("invalid Long for 'lifetimeSecs' argument: " + words.get(3)+"\n");
				return;
			}
		}

		Dtn2PingApp pinger = (Dtn2PingApp)AppManager.getInstance().getApp(Dtn2PingApp.APP_NAME);
		if (pinger == null) {
			process.write("Ping app is not installed\n");
			return;
		}
		pinger.doPing(eid.getEndPointIdString(), count, lifetimeSecs);
	}

	/**
	 * Provide help for 'rateEstimator' command
	 * @param process not used
	 */
	private void helpRateEstimator(CommandProcess process) {
		process.write("    Rate Estimator for specified Neighbor\n");
		process.write("      rateEstimator <linkName> <neighborName> <filename>\n");
	}

	/**
	 * Execute 'rateEstimator' command
	 * @param process command arguments
	 */
	private void rateEstimator(CommandProcess process) {
		List<String> words=process.args();

		// rateEstimator <linkName> <neighborName> <filename>
		if (words.size() != 4) {
			process.write("Incomplete 'dtn-rateEstimator' command\n");
			return;
		}

		String linkName = words.get(1);
		String neighborName = words.get(2);
		String filename = words.get(3);

		LtpLink link = LtpManagement.getInstance().findLtpLink(linkName);
		if (link == null) {
			process.write("No such Link: " + linkName+"\n");
			return;
		}
		Neighbor neighbor = NeighborsList.getInstance().findNeighborByName(neighborName);
		if (neighbor == null) {
			process.write("No such Neighbor: " + neighborName+"\n");
			return;
		}
		if (!(neighbor instanceof LtpNeighbor)) {
			process.write("Named neighbor is not a Ltp Neighbor\n");
			return;
		}
		LtpNeighbor ltpNeighbor = (LtpNeighbor)neighbor;
		File file = new File(filename);
		if (!file.exists()) {
			process.write(" File " + filename + " does not exist\n");
			return;
		}

		RateEstimatorApp estimator =
			(RateEstimatorApp)AppManager.getInstance().getApp(
					RateEstimatorApp.APP_NAME);
		if (estimator == null) {
			process.write("RateEstimatorApp is not installed\n");
			return;
		}

		process.write(
				"Starting RateEstimator for Link " + linkName +
						" Neighbor " + neighborName+"\n");
		try {
			estimator.estimateRateLimit(ltpNeighbor, file);
		} catch (JDtnException e) {
			process.write(e.getMessage()+"\n");
			return;
		}
	}

	private void helpIon(CommandProcess process) {
		process.write("    ION interoperability test\n");
		process.write("      ion source {options} <eid> {word} ,..        Source text bundle to <eid>\n");
		process.write("         <words...> = Text to send in payload of Bundle\n");
		process.write("      ion sendFile {options} <eid> <filePath>      Send file(BPSendFile) to <eid>\n");
		helpOptions(process);
	}

	private void ion(CommandProcess process) throws InterruptedException {
		List<String> words=process.args();

		if (words.size() < 1) {
			process.write("Incomplete 'dtn-ion' command\n");
			return;
		}
		String cmd = getMatchAmongAlts(process,words.get(0), "source sendFile");
		if (cmd.equalsIgnoreCase("source")) {
			ionSource(process);
		} else if (cmd.equalsIgnoreCase("sendFile")) {
			ionSendFile(process);
		} else {
			process.write("Unrecognized 'dtn-ion' sub-command: " + words.get(0)+"\n");
		}
	}

	private void ionSource(CommandProcess process) {
		List<String> words=process.args();

		// ion source {options} <eid> <words...>
		if (words.size() < 3) {
			process.write("Incomplete 'source' command\n");
			return;
		}

		BundleOptions options = new BundleOptions();	// Bundle Options

		// Figure out command line
		StringBuffer text = new StringBuffer();			// Text to send
		EndPointId destEid = decipherSendOrTextOptions(2, null, process, options, text);
		if (destEid == null) {
			return;
		}

		// Send the Text
		process.write("Sourcing " +
				" eid=" + destEid.getEndPointIdString() +
				" text=" + text +
				" options=" + options+"\n");
		IonSourceSinkApp ionSourceApp = (IonSourceSinkApp)AppManager.getInstance().getApp(IonSourceSinkApp.APP_NAME);
		if (ionSourceApp == null) {
			process.write("'IonSourceApp' application is not installed\n");
			return;
		}
		try {
			ionSourceApp.source(destEid, options, text.toString());
		} catch (Exception e) {
			process.write(e.getMessage()+"\n");
		}
	}

	private void ionSendFile(CommandProcess process) throws InterruptedException {
		List<String> words=process.args();

		// ion sendFile {options} <eid> <filePath>
		// 0   1        2         3     4
		if (words.size() < 5) {
			process.write("Incorrect number of arguments\n");
			return;
		}

		BundleOptions options = new BundleOptions();	// Bundle Options

		// Figure out command line
		StringBuffer text = new StringBuffer();			// Cmd line remainder after options
		EndPointId destEid = decipherSendOrTextOptions(2, null, process, options, text);
		if (destEid == null) {
			return;
		}

		File file = new File(text.toString());
		BPSendFileApp app = (BPSendFileApp)AppManager.getInstance().getApp(BPSendFileApp.APP_NAME);
		if (app == null) {
			process.write("BPSendFileApp is not installed\n");
			return;
		}
		process.write(
				"Sending " + file.getAbsolutePath() + " to " +
						destEid.getEndPointIdString()+"\n");
		try {
			app.sendFile(file, destEid, options);
		} catch (JDtnException e) {
			process.write(e.getMessage()+"\n");
		}
	}
	
	/**
	 * Determine which one of the given alternative words which the given input
	 * String is a unique prefix of.
	 * @param input Given input String
	 * @param alternatives Alternative words
	 * @return Matching alternative word, or original input if not match
	 */
	public String getMatchAmongAlts(CommandProcess process, String input, String alternatives) {
		return getMatchAmongAlts(process, input, alternatives, false);
	}
	
	public String getMatchAmongAlts(CommandProcess process, String input, String alternatives, boolean quiet) {
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
				process.write("Ambiguous input; try typing more letters\n");
			}
			return input;
			
		} else if (nMatches == 1) {
			return words[matchingIndex];
		}
		if (!quiet) {
			process.write("No match among alternatives: " + alternatives+"\n");
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
