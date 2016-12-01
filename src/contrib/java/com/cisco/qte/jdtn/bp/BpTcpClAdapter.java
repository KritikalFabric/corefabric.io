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

import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Link;
import com.cisco.qte.jdtn.general.Neighbor;
import com.cisco.qte.jdtn.tcpcl.TcpClAPI;
import com.cisco.qte.jdtn.tcpcl.TcpClDataBlock;
import com.cisco.qte.jdtn.tcpcl.TcpClLink;
import com.cisco.qte.jdtn.tcpcl.TcpClListener;
import com.cisco.qte.jdtn.tcpcl.TcpClNeighbor;

/**
 * Convergence Layer adapter for BP - TCP Convergence Layer
 */
public class BpTcpClAdapter extends AbstractStartableComponent
implements TcpClListener {
	private static final Logger _logger =
		Logger.getLogger(BpTcpClAdapter.class.getCanonicalName());
	
	private static BpTcpClAdapter _instance = null;
	
	/**
	 * Get singleton instance
	 * @return Singleton instance
	 */
	public static BpTcpClAdapter getInstance() {
		if (_instance == null) {
			_instance = new BpTcpClAdapter();
		}
		return _instance;
	}
	
	/**
	 * Do nothing constructor
	 */
	protected BpTcpClAdapter() {
		super("BpTcpClAdapter");
	}
	
	/**
	 * Start operation of this component
	 */
	@Override
	protected void startImpl() {
		TcpClAPI.getInstance().addTcpClListener(this);
	}
	
	/**
	 * Stop operation of this component
	 */
	@Override
	protected void stopImpl() {
		TcpClAPI.getInstance().removeTcpClListener(this);
	}

	/**
	 * Transmit the given Bundle via the given Route
	 * @param bundle Given Bundle to transmit
	 * @param route Route dictating Link and Neighbor
	 * @param blockColor Not used
	 * @throws JDtnException On errors
	 * @throws InterruptedException If interrupted during operation
	 */
	public void transmitBundle(
			Bundle bundle, 
			Route route, 
			BundleColor blockColor)
	throws JDtnException, InterruptedException {
		if (GeneralManagement.isDebugLogging() &&
			_logger.isLoggable(Level.FINEST)) {
			_logger.finest("transmitBundle");
			_logger.finest(bundle.dump("", true));
		}
		Link link = route.getLink();
		if (!(link instanceof TcpClLink)) {
			throw new JDtnException("Route Link not instance of TcpClLink");
		}
		Neighbor neighbor = route.getNeighbor();
		if (!(neighbor instanceof TcpClNeighbor)) {
			throw new JDtnException("Route neighbor not instance of TcpClNeighbor");
		}
		EncodeState encodeState = new EncodeState();
		bundle.encode(encodeState, neighbor.getEidScheme());
		byte[] buffer = encodeState.getByteBuffer();
		TcpClAPI.getInstance().sendBlock(
				buffer, 
				buffer.length, 
				(TcpClLink)route.getLink(), 
				(TcpClNeighbor)route.getNeighbor(), 
				bundle);
		encodeState.close();
	}
	
	/**
	 * Notification from TcpCl about receipt of a Block encoding a Bundle.
	 * @see com.cisco.qte.jdtn.tcpcl.TcpClListener#notifyInboundBlock(com.cisco.qte.jdtn.tcpcl.TcpClDataBlock, long)
	 */
	@Override
	public void notifyInboundBlock(TcpClDataBlock dataBlock, long length) {
		DecodeState decodeState = new DecodeState(
				dataBlock.buffer, 
				0, 
				(int)dataBlock.length);
		try {
			Bundle bundle = new Bundle(decodeState, dataBlock.neighbor.getEidScheme());
			if (GeneralManagement.isDebugLogging() &&
					_logger.isLoggable(Level.FINEST)) {
					_logger.finest("notifyInboundBundle");
					_logger.finest(bundle.dump("", true));
			}
			bundle.setLink(dataBlock.link);
			BPProtocolAgent.getInstance().onBundleReceived(bundle);
			
		} catch (JDtnException e) {
			_logger.log(Level.SEVERE, "decode inbound block", e);
		} catch (InterruptedException e) {
			// Nothing
		}
	}

	/**
	 * Notification from TcpCl that an error occurred during the transmission of
	 * a Block.
	 * @see com.cisco.qte.jdtn.tcpcl.TcpClListener#notifyOutboundBlockError(com.cisco.qte.jdtn.tcpcl.TcpClDataBlock, java.lang.Throwable)
	 */
	@Override
	public void notifyOutboundBlockError(TcpClDataBlock dataBlock, Throwable t) {
		if (t == null) {
			_logger.severe(
					"Outbound Block Error; block Id " + 
					dataBlock.dataBlockId);
		} else {
			_logger.log(
					Level.SEVERE, 
					"Outbound Block Error; block Id " + dataBlock.dataBlockId, 
					t);
		}
		Bundle bundle = (Bundle)dataBlock.dataBlockId;
		try {
			BPProtocolAgent.getInstance().onBundleTransmitFailed(bundle);
		} catch (InterruptedException e) {
			// Ignore
		}
	}

	/**
	 * Notification from TcpCl that the given block has been completely and
	 * successfully transmitted.
	 * @see com.cisco.qte.jdtn.tcpcl.TcpClListener#notifyBlockTransmitComplete(com.cisco.qte.jdtn.tcpcl.TcpClDataBlock)
	 */
	@Override
	public void notifyBlockTransmitComplete(TcpClDataBlock dataBlock) {
		Bundle bundle = (Bundle)dataBlock.dataBlockId;
		try {
			BPProtocolAgent.getInstance().onBundleTransmitComplete(bundle);
		} catch (InterruptedException e) {
			// Ignore
		}
	}
	
}
