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

/**
 * Event signifying the desire to be notified when this event is processed.
 */
public class SyncEvent extends AbstractEvent {

	private static final long serialVersionUID = 1L;

	private Object _syncObj = new Object();
	private boolean _success = true;
	private String _errorMessage = null;
	
	/**
	 * Constructor.
	 * @param name Name of the Event
	 */
	public SyncEvent(String name) {
		super(name);
	}

	/**
	 * Wait for receiver to process up to this sync event
	 * @throws InterruptedException if wait interrupted
	 */
	public void waitForSync() throws InterruptedException {
		synchronized (_syncObj) {
			_syncObj.wait();
		}
	}
	
	/**
	 * Wait for receiver to process up to this sync event with timeout
	 * @param timeMSecs Max mSecs to wait for sync event to be processed
	 * @throws InterruptedException if wait interrupted
	 */
	public void waitForSync(long timeMSecs) throws InterruptedException {
		synchronized (_syncObj) {
			_syncObj.wait(timeMSecs);
		}
	}
	
	/**
	 * Notify waiter that sync has been processed
	 */
	public void notifySyncProcessed() {
		synchronized (_syncObj) {
			_syncObj.notify();
		}
	}
	
	/**
	 * Set status of processing up to this point and notify waiter that sync
	 * has been processed
	 * @param success Whether prior event was successfully processed
	 * @param errorMessage If success == false, then this supplies a human
	 * oriented error message.
	 */
	public void notifySyncProcessed(boolean success, String errorMessage) {
		synchronized (_syncObj) {
			setSuccess(success);
			setErrorMessage(errorMessage);
			_syncObj.notify();
		}
	}
	
	/**
	 * @see com.cisco.qte.jdtn.component.AbstractComponent#dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "SyncEvent\n");
		sb.append(indent + "  Success=" + _success + "\n");
		sb.append(indent + "  ErrorMessage=" + _errorMessage + "\n");
		sb.append(super.dump(indent + "  ", detailed));
		return sb.toString();
	}

	/**
	 * @see com.cisco.qte.jdtn.component.AbstractComponent#toString
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("SyncEvent{");
		sb.append("Success=" + _success);
		sb.append(",  ErrorMessage=" + _errorMessage);
		sb.append(", " + super.toString());
		sb.append("}");
		return sb.toString();
	}

	/** Whether event prior to sync was successfully processed */
	public boolean isSuccess() {
		return _success;
	}

	/** Whether event prior to sync was successfully processed */
	public void setSuccess(boolean success) {
		this._success = success;
	}

	/** Error message associated with unsuccessful processing of event prior to sync */
	public String getErrorMessage() {
		return _errorMessage;
	}

	/** Error message associated with unsuccessful processing of event prior to sync */
	public void setErrorMessage(String errorMessage) {
		this._errorMessage = errorMessage;
	}

}
