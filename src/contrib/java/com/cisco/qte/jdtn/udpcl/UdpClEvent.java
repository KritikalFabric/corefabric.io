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
package com.cisco.qte.jdtn.udpcl;

import com.cisco.qte.jdtn.events.JDTNEvent;

/**
 * Events for UdpCl components
 */
public class UdpClEvent extends JDTNEvent {

	public enum UdpClSubEvents {
		/** Start issued by mgmt */
		START_EVENT,
		/** Stop issued by mgmt */
		STOP_EVENT,
		/** Socket successfully opened event */
		SOCKET_OPENED_EVENT,
		/** Socket no longer usable */
		SOCKET_CLOSED_EVENT
	}
	
	/** The SubEvent code describing the event */
	private UdpClSubEvents _subEvent;
	
	/**
	 * Constructor
	 * @param subEvent Sub-Event type
	 */
	public UdpClEvent(UdpClSubEvents subEvent) {
		super(JDTNEvent.EventType.UDPCL_EVENT);
		setSubEvent(subEvent);
	}

	/** The SubEvent code describing the event */
	public UdpClSubEvents getSubEvent() {
		return _subEvent;
	}

	/** The SubEvent code describing the event */
	public void setSubEvent(UdpClSubEvents subEvent) {
		this._subEvent = subEvent;
	}

}
