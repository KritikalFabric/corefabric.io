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
package com.cisco.qte.jdtn.events;

/**
 * Notification that CafAdapterApp is about to publish service data for this
 * Node.  Gives listeners a chance to modify the service data, such as adding
 * location info.
 */
public class CafPublishEvent extends JDTNEvent {

	private StringBuilder _serviceData;
	
	/**
	 * Constructor
	 * @param serviceData Service Data about to be published by CAF.  At the
	 * time this is called, the ServiceData consists of a &lt; JDTN &gt; xml element,
	 * but without the &lt; /JDTN &gt; terminator.  Listeners can add to this
	 * service data by appending full XML elements.
	 */
	public CafPublishEvent(StringBuilder serviceData) {
		super(JDTNEvent.EventType.CAF_PUBLISH_EVENT);
		setServiceData(serviceData);
	}

	/** Service Data property */
	public StringBuilder getServiceData() {
		return _serviceData;
	}

	/** Service Data Property */
	public void setServiceData(StringBuilder serviceData) {
		this._serviceData = serviceData;
	}
}
