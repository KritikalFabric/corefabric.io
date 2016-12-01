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
package com.cisco.qte.jdtn.tcpcl;

import com.cisco.qte.jdtn.events.JDTNEvent;

/**
 * Superclass for all TcpCl events
 */
public class TcpClEvent extends JDTNEvent {

	public enum SubEventTypes {
		/** Start connection initiation or acceptance */
		START_EVENT,
		
		/** Stop all network operations */
		STOP_EVENT,
		
		/** We have initiated a TCP Connection to a Neighbor */
		SOCKET_CONNECTED_EVENT,
		
		/** We have accepted a TCP Connection from a Neighbor */
		SOCKET_ACCEPTED_EVENT,
		
		/** Connection Closed by remote host */
		CONNECTION_CLOSED_REMOTE,
		
		/** Inbound Segment received */
		INBOUND_SEGMENT_EVENT,
		
		/** Keepalive timer expired */
		KEEPALIVE_TIMER_EVENT,
		
		/** Reconnect Delay Timer expired */
		RECONNECT_TIMER_EVENT,
		
		/** Idle Timer expired */
		IDLE_TIMER_EVENT,
		
		/** Outbound Block enqueued */
		OUTBOUND_BLOCK_EVENT,
		
		/** Demand Service event */
		DEMAND_SERVICE_EVENT,
		
		/** Mgmt set parameter */
		SET_PARAMETER_EVENT,
		
		/** Timeout waiting for Data Segment Ack */
		ACK_TIMER_EVENT
	}
	
	private SubEventTypes _subEventType;
	
	/**
	 * Constructor
	 * @param subEventType Sub event type
	 */
	public TcpClEvent(SubEventTypes subEventType) {
		super(JDTNEvent.EventType.TCPCL_EVENT);
		setSubEventType(subEventType);
	}

	public SubEventTypes getSubEventType() {
		return _subEventType;
	}

	public void setSubEventType(SubEventTypes subEventType) {
		this._subEventType = subEventType;
	}

}
