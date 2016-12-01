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
package com.cisco.qte.jdtn.component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A component which broadcasts Events to a group of EventProcessors.  The
 * EventProcessors register to a "broadcast group" among a number of such
 * groups each identified by a unique group name.  This decouples EventProcessors
 * from detailed knowledge about the sources of events, and allows a single event
 * to stimulate multiple EventProcessors.  The EventBroadcaster is a singleton.
 */
public class EventBroadcaster extends AbstractStartableComponent {

	private static EventBroadcaster _instance = null;
	
	private HashMap<String, List<IEventProcessor>> _groupMap =
		new HashMap<String, List<IEventProcessor>>();
	/** Number of createGroup operations */
	public long nCreateGroups = 0;
	/** Number of deleteGroup operations */
	public long nDeleteGroups = 0;
	/** Number of register operations */
	public long nRegisters = 0;
	/** Number of unregister operations */
	public long nUnregisters = 0;
	/** Number of broadcast operations */
	public long nBroadcasts = 0;
	
	/**
	 * Get singleton instance
	 * @return Singleton instance
	 */
	public static EventBroadcaster getInstance() {
		if (_instance == null) {
			_instance = new EventBroadcaster();
		}
		return _instance;
	}
	
	/**
	 * Private constructor
	 */
	private EventBroadcaster() {
		super("EventBroadcaster");
	}

	@Override
	protected void startImpl() {
		// Nothing
	}

	@Override
	protected void stopImpl() throws InterruptedException {
		// Nothing
	}
	/**
	 * Create a broadcast group with the given name.  If group has already
	 * been created, then an IllegalArgumentException is thrown.
	 * @param groupName Name of broadcast group
	 * @throws IllegalArgumentException if group already created.
	 */
	public void createBroadcastGroup(String groupName) 
	throws IllegalArgumentException {
		nCreateGroups++;
		List<IEventProcessor> group = null;
		group = _groupMap.get(groupName);
		if (group != null) {
			throw new IllegalArgumentException("Group " + groupName + " already created");
		}
		group = new ArrayList<IEventProcessor>();
		_groupMap.put(groupName, group);
	}
	
	/**
	 * Delete the broadcast group with the given name.  If group has not
	 * previously been created, then an IllegalArgumentException is thrown.
	 * @param groupName Name of broadcast group.
	 * @throws IllegalArgumentException if group not previously created.
	 */
	public void deleteBroadcastGroup(String groupName)
	throws IllegalArgumentException {
		nDeleteGroups++;
		List<IEventProcessor> group = null;
		group = _groupMap.get(groupName);
		if (group == null) {
			throw new IllegalArgumentException(
					"Group " + groupName + " has not been created");
		}
		_groupMap.remove(groupName);
		group.clear();
	}
	
	/**
	 * Register given IEventProcessor to receive Events to given Group.  If
	 * group has not been created, then an IllegalArgumentException is thrown.
	 * @param groupName Group Name to register for
	 * @param eventProcessor IEventProcessor to register
	 * @throws IllegalArgumentException if group has not bee previously created.
	 */
	public void registerEventProcessor(
			String groupName, 
			IEventProcessor eventProcessor) 
	throws IllegalArgumentException {
		nRegisters++;
		List<IEventProcessor> group = null;
		group = _groupMap.get(groupName);
		if (group == null) {
			throw new IllegalArgumentException(
					"Group " + groupName + " has not been created");
		}
		group.add(eventProcessor);
	}
	
	/**
	 * Unregister given IEventProcessor from receiving Events to given Group.
	 * If group has not been created, then an IllegalArgumentException is thrown.
	 * @param groupName Group Name 
	 * @param eventProcessor IEventProcessor to unregister
	 * @throws IllegalArgumentException if group has not been previously created.
	 */
	public void unregisterEventProcessor(
		String groupName, 
		IEventProcessor eventProcessor) 
	throws IllegalArgumentException {
		nUnregisters++;
		List<IEventProcessor> group = null;
		group = _groupMap.get(groupName);
		if (group == null) {
			throw new IllegalArgumentException(
					"Group " + groupName + " has not been created");
		}
		group.remove(eventProcessor);
	}

	/**
	 * Broadcast given Event to the given Group.  If group has not been
	 * created, then an IllegalArgumentException is thrown.
	 * @param groupName Name of Group to broadcast to
	 * @param event Event to broadcast
	 * @throws IllegalArgumentException if group has not been previously created.
	 * @throws InterruptedException If interrupted during process
	 */
	public void broadcastEvent(String groupName, IEvent event)
	throws Exception {
		nBroadcasts++;
		List<IEventProcessor> group = null;
		group = _groupMap.get(groupName);
		if (group == null) {
			throw new IllegalArgumentException(
					"Group " + groupName + " has not been created");
		}
		List<IEventProcessor> tempGroup = new ArrayList<IEventProcessor>(group);
		for (IEventProcessor ep : tempGroup) {
			ep.processEvent(event);
		}
	}
	
	/**
	 * Clear statistics
	 */
	@Override
	public void clearStatistics() {
		nCreateGroups = 0;
		nDeleteGroups = 0;
		nRegisters = 0;
		nUnregisters = 0;
		nBroadcasts = 0;
		super.clearStatistics();
	}

	@Override
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "EventBroadcaster\n");
		if (detailed) {
			sb.append(indent + "  nCreateGroups=" + nCreateGroups + "\n");
			sb.append(indent + "  nDeleteGroups=" + nDeleteGroups + "\n");
			sb.append(indent + "  nRegisters=" + nRegisters + "\n");
			sb.append(indent + "  nUnregisters=" + nUnregisters + "\n");
			sb.append(indent + "  nBroadcasts=" + nBroadcasts + "\n");
			for (String groupName : _groupMap.keySet()) {
				sb.append(indent + "  Group=" + groupName + "\n");
				for (IEventProcessor evp : _groupMap.get(groupName)) {
					sb.append(indent + "   IEventProcessor=" + 
							evp.getClass().getSimpleName() + "(" + evp.getName() + ")\n");
				}
			}
		}
		sb.append(super.dump(indent + "  ", detailed));
		return sb.toString();
	}

}
