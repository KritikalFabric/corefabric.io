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
package com.cisco.qte.jdtn.events;

import com.cisco.qte.jdtn.component.AbstractEvent;

/**
 * Abstract superclass for any object which can be in an Event Queue
 */
public abstract class JDTNEvent extends AbstractEvent {
	/** Possible Event Types */
	public enum EventType {
		OUTBOUND_BUNDLE,
		INBOUND_BUNDLE,
		BUNDLE_CANCEL,
		BUNDLE_CANCEL_BY_RECEIVER,
		BUNDLE_TRANSMIT_COMPLETE,
		BUNDLE_TIMER_EXPIRED,
		BUNDLE_CUSTODY_TIMER_EXPIRED,
		OUTBOUND_BLOCK,
		INBOUND_BLOCK,
		BLOCK_CANCEL,
		REPORT_SEGMENT,
		CANCEL_SEGMENT,
		CANCEL_ACK_SEGMENT,
		NEIGHBOR_SCHEDULED_STATE_CHANGE,
		SEGMENT_TRANSMIT_STARTED,
		CHECKPOINT_TIMER_EXPIRED,
		CANCEL_TIMER_EXPIRED,
		INBOUND_SEGMENT,
		REPORT_SEGMENT_TIMER_EXPIRED,
		POST_BUNDLE_HOLD,
		BUNDLE_HOLD_CANCELLED,
		TCPCL_EVENT,
		BUNDLE_TRANSMIT_FAILED,
		UDPCL_EVENT,
		LINKS_EVENT,
		MANAGEMENT_PROPERTY_CHANGE_EVENT,
		RESTORE_BUNDLES_EVENT,
		CAF_NEIGHBOR_ADDED_EVENT,
		CAF_NEIGHBOR_REMOVED_EVENT,
		CAF_PUBLISH_EVENT
	}
	/** Event type for this event */
	private EventType _eventType;
	
	/**
	 * Construct a new EventQueueElement with specified EventType
	 * @param eventType Specified EventType
	 */
	public JDTNEvent(EventType eventType) {
		super(eventType.toString());
		setEventType(eventType);
	}

	/** Event type for this event */
	public EventType getEventType() {
		return _eventType;
	}

	/** Event type for this event */
	public void setEventType(EventType eventType) {
		this._eventType = eventType;
	}

}
