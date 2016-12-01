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
package com.cisco.qte.jdtn.bp;

import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Neighbor;

/**
 * Multiplexer between BP and various convergence layers
 */
public class ConvergenceLayerMux extends AbstractStartableComponent {

	private static ConvergenceLayerMux _instance = null;
	
	public static ConvergenceLayerMux getInstance() {
		if (_instance == null) {
			_instance = new ConvergenceLayerMux();
		}
		return _instance;
	}
	
	protected ConvergenceLayerMux() {
		super("ConvergenceLayerMux");
	}
	
	@Override
	protected void startImpl() {
		// Nothing
	}
	
	@Override
	protected void stopImpl() {
		// Nothing
	}
	
	/**
	 * Called from BPProtocolAgent to trasmit a Bundle.  Chooses the
	 * convergence layer to pass it on to based on the type of Neighbor
	 * specified in the given Route.
	 * @param bundle Bundle to Transmit
	 * @param route Route governing how to forward the Bundle
	 * @param blockColor Block Color (for LTP convergence layer only)
	 * @throws JDtnException On errors
	 * @throws InterruptedException if interrupts during process
	 */
	public void transmitBundle(
			Bundle bundle, 
			Route route, 
			BundleColor blockColor)
	throws JDtnException, InterruptedException {
		Neighbor neighbor = route.getNeighbor();
		switch (neighbor.getType()) {
		case NEIGHBOR_TYPE_LTP_UDP:
			BpLtpAdapter.getInstance().transmitBundle(bundle, route, blockColor);
			break;
		case NEIGHBOR_TYPE_TCPCL:
			BpTcpClAdapter.getInstance().transmitBundle(bundle, route, blockColor);
			break;
		case NEIGHBOR_TYPE_UDPCL:
			BpUdpClAdapter.getInstance().transmitBundle(bundle, route, blockColor);
			break;
		}
	}
	
	
}
