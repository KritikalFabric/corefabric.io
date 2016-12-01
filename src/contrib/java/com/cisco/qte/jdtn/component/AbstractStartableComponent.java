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

public abstract class AbstractStartableComponent extends AbstractComponent
implements IStartableComponent {

	private boolean _started;
	/** Number of Start operations issued */
	public long nStarts = 0;
	/** Number of Stop operations issued */
	public long nStops = 0;
	
	/**
	 * Constructor
	 * @param name Name; not necessarily unique
	 */
	public AbstractStartableComponent(String name) {
		super(name);
		_started = false;
	}

	/**
	 * Start the component
	 */
	public void start() {
		if (!_started) {
			nStarts++;
			_started = true;
			startImpl();
		}
	}
	
	/**
	 * Subclass defined method called on start(); only if not already started
	 */
	protected abstract void startImpl();
	
	/**
	 * Stop the component (no-op if already stopped)
	 * @throws InterruptedException 
	 */
	public void stop() throws InterruptedException {
		if (_started) {
			nStops++;
			_started = false;
			stopImpl();
		}
	}
	
	/**
	 * Subclass defined method called on stop(); only if not already stopped
	 */
	protected abstract void stopImpl() throws InterruptedException;
	
	/** Started property */
	public boolean isStarted() {
		return _started;
	}

	/** 
	 * Clear Statistics 
	 * @see com.cisco.qte.jdtn.component.AbstractComponent#clearStatistics()
	 * */
	@Override
	public void clearStatistics() {
		nStarts = 0;
		nStops = 0;
	}
	
	/**
	 * @see com.cisco.qte.jdtn.component.AbstractComponent#dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "AbstractStartableComponent\n");
		sb.append(indent + "  Started=" + _started + "\n");
		if (detailed) {
			sb.append(indent + "  NStarts=" + nStarts + "\n");
			sb.append(indent + "  NStops=" + nStops + "\n");
		}
		sb.append(super.dump(indent + "  ", detailed));
		return sb.toString();
	}

	/**
	 * @see com.cisco.qte.jdtn.component.AbstractComponent#dump
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("AbstractStartableComponent{");
		sb.append("started=" + _started);
		sb.append("}");
		return sb.toString();
	}

}
