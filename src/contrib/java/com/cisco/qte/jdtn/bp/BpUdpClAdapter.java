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

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Link;
import com.cisco.qte.jdtn.general.Neighbor;
import com.cisco.qte.jdtn.udpcl.UdpClAPI;
import com.cisco.qte.jdtn.udpcl.UdpClDataBlock;
import com.cisco.qte.jdtn.udpcl.UdpClLink;
import com.cisco.qte.jdtn.udpcl.UdpClListener;
import com.cisco.qte.jdtn.udpcl.UdpClNeighbor;
import org.kritikal.fabric.contrib.jdtn.BlobAndBundleDatabase;

/**
 * BP Convergence Adapter for UDP Convergence Layer
 */
public class BpUdpClAdapter extends AbstractStartableComponent
implements UdpClListener {
	private static final Logger _logger =
		Logger.getLogger(BpUdpClAdapter.class.getCanonicalName());
	
	public static final int FRAGMENTATION_SLOP = 200;
	public static final int FRAGMENTATION_LIMIT = 
		UdpClLink.MAX_PACKET_LEN - FRAGMENTATION_SLOP;
	private static BpUdpClAdapter _instance = null;
	
	/**
	 * Get singleton instance
	 * @return Singleton instance
	 */
	public static BpUdpClAdapter getInstance() {
		if (_instance == null) {
			_instance = new BpUdpClAdapter();
		}
		return _instance;
	}
	
	/**
	 * Do nothing constructor
	 */
	protected BpUdpClAdapter() {
		super("BpUdpClAdapter");
	}
	
	/**
	 * Start operation of this component
	 */
	@Override
	protected void startImpl() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("startImpl");
		}
		UdpClAPI.getInstance().addUdpClListener(this);
	}
	
	/**
	 * Stop operation of this component
	 */
	@Override
	protected void stopImpl() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("stopImpl");
		}
		UdpClAPI.getInstance().removeUdpClListener(this);
	}

	/**
	 * Transmit the given Bundle via the given Route.  We also fragment the
	 * Bundle using BP Fragmentation if necessary.
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
		if (!(link instanceof UdpClLink)) {
			throw new JDtnException("Route Link not instance of UdpClLink");
		}
		UdpClLink udpClLink = (UdpClLink)link;
		Neighbor neighbor = route.getNeighbor();
		if (!(neighbor instanceof UdpClNeighbor)) {
			throw new JDtnException("Route neighbor not instance of UdpClNeighbor");
		}
		UdpClNeighbor udpClNeighbor = (UdpClNeighbor)neighbor;

		java.sql.Connection con = BlobAndBundleDatabase.getInstance().getInterface().createConnection();
		try {

			// Encode the original Bundle
			EncodeState encodeState = new EncodeState();
			bundle.encode(con, encodeState, neighbor.getEidScheme());
			encodeState.close();

			if (encodeState.getLength() >= FRAGMENTATION_LIMIT) {
				// Fragmentation Required, fragment original Bundle
				List<Bundle> fragments =
						BPFragmentation.getInstance().fragmentBundle(
								con,
								bundle,
								FRAGMENTATION_LIMIT);
				for (Bundle fragmentBundle : fragments) {
					encodeState = new EncodeState();
					fragmentBundle.encode(con, encodeState, neighbor.getEidScheme());
					byte[] buffer = encodeState.getByteBuffer();

					UdpClDataBlock block = new UdpClDataBlock(
							buffer,
							buffer.length,
							udpClLink,
							udpClNeighbor,
							bundle);
					block.isFragment = true;
					if (fragmentBundle.getPrimaryBundleBlock().getFragmentOffset() +
							fragmentBundle.getPayloadBundleBlock().getBody().getLength() >=
							fragmentBundle.getPrimaryBundleBlock().getTotalAppDataUnitLength()) {
						block.isLastFragment = true;
					}

					// Send Fragment
					UdpClAPI.getInstance().sendBlock(
							block,
							udpClLink,
							udpClNeighbor);
				}
			} else {
				// Fragmentation not required; Send the entire Bundle
				byte[] buffer = encodeState.getByteBuffer();
				UdpClAPI.getInstance().sendBlock(
						buffer,
						buffer.length,
						(UdpClLink) route.getLink(),
						(UdpClNeighbor) route.getNeighbor(),
						bundle);
			}
			try { con.commit(); } catch (SQLException e) { _logger.warning(e.getMessage()); }
		}
		finally {
			try { con.close(); } catch (SQLException e) { _logger.warning(e.getMessage()); }
		}
	}
	
	/**
	 * Notification from UdpCl that the given block has been completely and
	 * successfully transmitted.
	 * @see com.cisco.qte.jdtn.udpcl.UdpClListener#notifyBlockTransmitComplete(com.cisco.qte.jdtn.udpcl.UdpClDataBlock)
	 */
	@Override
	public void notifyBlockTransmitComplete(UdpClDataBlock dataBlock) {
		Bundle bundle = (Bundle)dataBlock.dataBlockId;
		try {
			if (dataBlock.isFragment && dataBlock.isLastFragment) {
				if (GeneralManagement.isDebugLogging()) {
					_logger.fine("Last fragment transmitted, signalling full " +
							"bundle xmitted to BPProtocolAgent");
				}
				BPProtocolAgent.getInstance().onBundleTransmitComplete(bundle);
				
			} else if (!dataBlock.isFragment) {
				BPProtocolAgent.getInstance().onBundleTransmitComplete(bundle);
			}
		} catch (InterruptedException e) {
			// Ignore
		}
	}

	/**
	 * Notification from UdpCl about receipt of a Block encoding a Bundle.
	 * @see com.cisco.qte.jdtn.udpcl.UdpClListener#notifyInboundBlock(com.cisco.qte.jdtn.udpcl.UdpClDataBlock, long)
	 */
	@Override
	public void notifyInboundBlock(UdpClDataBlock dataBlock, long length) {
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
	 * Notification from UdpCl that an error occurred during the transmission of
	 * a Block.
	 * @see com.cisco.qte.jdtn.udpcl.UdpClListener#notifyOutboundBlockError(com.cisco.qte.jdtn.udpcl.UdpClDataBlock, java.lang.Throwable)
	 */
	@Override
	public void notifyOutboundBlockError(UdpClDataBlock dataBlock, Throwable t) {
		Bundle bundle = (Bundle)dataBlock.dataBlockId;
		if (t == null) {
			_logger.severe(
					" Outbound Block Transmission Failed; Bundle Id " + 
					bundle.getBundleId());
		} else {
			_logger.log(
					Level.SEVERE, 
					" Outbound Block Transmission Failed; Bundle Id " + 
					bundle.getBundleId(),
					t);
		}
		try {
			BPProtocolAgent.getInstance().onBundleTransmitFailed(bundle);
		} catch (InterruptedException e) {
			// Ignore
		}
	}

	
}
