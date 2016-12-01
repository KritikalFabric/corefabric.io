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
import com.cisco.qte.jdtn.general.Store;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.ltp.Block;
import com.cisco.qte.jdtn.ltp.BlockOptions;
import com.cisco.qte.jdtn.ltp.CancelSegment;
import com.cisco.qte.jdtn.ltp.DataSegment;
import com.cisco.qte.jdtn.ltp.LtpApi;
import com.cisco.qte.jdtn.ltp.LtpException;
import com.cisco.qte.jdtn.ltp.LtpLink;
import com.cisco.qte.jdtn.ltp.LtpListener;
import com.cisco.qte.jdtn.ltp.LtpNeighbor;
import com.cisco.qte.jdtn.ltp.ServiceId;

/**
 * Adapter between BP layer and LTP layers
 */
public class BpLtpAdapter extends AbstractStartableComponent
implements LtpListener {

	private static final Logger _logger =
		Logger.getLogger(BpLtpAdapter.class.getCanonicalName());
	
	private static BpLtpAdapter _instance = null;
	
	private ServiceId _serviceId;
	
	/**
	 * Get singleton instance
	 * @return Singleton instance
	 */
	public static BpLtpAdapter getInstance() {
		if (_instance == null) {
			_instance = new BpLtpAdapter();
		}
		return _instance;
	}
	
	/**
	 * private constructor
	 */
	private BpLtpAdapter() {
		super("BpLtpAdapter");
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("BpLtpAdapter()");
		}
		_serviceId = new ServiceId(
				Utils.intToByteUnsigned(
						BPManagement.getInstance().getBpServiceId()));
	}
	
	/**
	 * Startup operation of the BpLtpAdapter
	 */
	@Override
	protected void startImpl() {
		LtpApi.getInstance().addLtpListener(this, _serviceId);
	}
	
	/**
	 * Shutdown operation of the BpLtpAdapter
	 */
	@Override
	protected void stopImpl() {
		ServiceId serviceId = _serviceId;
		LtpApi.getInstance().removeLtpListener(
				this, serviceId);
	}
		
	/**
	 * Transmit given Bundle
	 * @param bundle Given Bundle
	 * @param route Route for Transmission
	 * @param blockColor Color (RED or GREEN) to be used for transmission of Bundle.
	 * @throws JDtnException on errors
	 * @throws InterruptedException if interrupted while waiting
	 */
	public void transmitBundle(
			Bundle bundle, 
			Route route, 
			BundleColor blockColor)
	throws JDtnException, InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("transmitBundle()");
		}
		if (!isStarted()) {
			throw new BPException("BpLtpAdapter has not been started");
		}
		
		if (GeneralManagement.isDebugLogging()) {
			if (_logger.isLoggable(Level.FINER)) {
				_logger.finer("transmitBundle");
				_logger.finer(bundle.dump("", true));
			}
		}
		
		Link link = route.getLink();
		if (!(link instanceof LtpLink)) {
			throw new BPException("Route contains non LtpLink");
		}
		LtpLink ltpLink = (LtpLink)link;
		bundle.setLink(ltpLink);
		Neighbor neighbor = route.getNeighbor();
		if (!(neighbor instanceof LtpNeighbor)) {
			throw new BPException("Route contains non LtpNeighbor");
		}
		LtpNeighbor ltpNeighbor = (LtpNeighbor)neighbor;
		
		// Determine whether to encode to File or to memory
		EncodeState encodeState = null;
		PayloadBundleBlock payloadBlock = bundle.getPayloadBundleBlock();
		if (payloadBlock != null) {
			Payload payload = payloadBlock.getPayload();
			if (payload.isBodyDataInFile()) {
				encodeState = 
					new EncodeState(
							Store.getInstance().createBundleFile());
				
			} else if (payload.getBodyDataMemLength() > 
				BPManagement.getInstance().getBundleBlockFileThreshold()) {
				encodeState = 
					new EncodeState(
							Store.getInstance().createBundleFile());
				
			} else {
				encodeState = new EncodeState();
			}
		} else {
			encodeState = new EncodeState();
		}
		
		// Encode the Bundle
		long t1 = System.currentTimeMillis();
		bundle.encode(encodeState, ltpNeighbor.getEidScheme());
		encodeState.close();
		long t2 = System.currentTimeMillis();
		BPManagement.getInstance()._bpStats.nEncodingMSecs += (t2 - t1);
		
		if (GeneralManagement.isDebugLogging()) {
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(encodeState.dump("", true));
			}
		}
		
		// Determine LTP Block Options from Bundle Options
		BlockOptions blockOptions;
		if (blockColor == BundleColor.RED) {
			blockOptions = new BlockOptions(encodeState.getLength());
			blockOptions.checkpointOption = BlockOptions.CheckpointOption.CHECKPOINT_LAST_ONLY;
			
		} else {
			blockOptions = new BlockOptions();
		}
		blockOptions.serviceId = _serviceId;
		
		// Send the Encoded Bundle
		if (encodeState.isEncodingToFile) {
			bundle.setAdaptationLayerData(
					LtpApi.getInstance().send(
						bundle,
						ltpNeighbor.getEngineId(), 
						encodeState.file, 
						encodeState.fileLength, 
						blockOptions));
			
		} else {

			bundle.setAdaptationLayerData(
					LtpApi.getInstance().send(
						bundle,
						ltpNeighbor.getEngineId(), 
						encodeState.getByteBuffer(), 
						encodeState.getLength(), 
						blockOptions));	
		}
	}

	/**
	 * Cancel Transmission of a Bundle, if possible
	 * @param bundle
	 * @return true if Cancellation successfully initiated; false if
	 * Bundle has already been transmitted.
	 * @throws LtpException on LTP Errors
	 * @throws InterruptedException if interrupted on wait
	 */
	public boolean cancelBundleTransmit(Bundle bundle)
	throws LtpException, InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("cancelBundleTransmit()");
		}
		if (bundle.getAdaptationLayerData() == null) {
			// Already transmitted
			return false;
		}
		if (!(bundle.getAdaptationLayerData() instanceof Block)) {
			throw new LtpException("Adaptation Layer Data not instanceof Block");
		}
		Block block = (Block)bundle.getAdaptationLayerData();
		LtpApi.getInstance().cancelSentBlock(
				block, 
				CancelSegment.REASON_CODE_USER_CANCELLED);
		return true;
	}
	
	/**
	 * Called from LTP when a Block is received.  We turn it into a Bundle
	 * and give it to BPProtocolAgent.
	 * @param block Received block
	 */
	@Override
	public void onBlockReceived(Block block) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onBlockReceived()");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(block.dump("", true));
			}
		}
		try {
			long t1 = System.currentTimeMillis();
			DecodeState decodeState = new DecodeState(
					block.isDataInFile(), 
					block.getDataFile(), 
					0, 
					block.getDataLength(), 
					block.getDataBuffer(), 
					0, 
					(int)block.getDataLength());
			Bundle bundle = new Bundle(decodeState, block.getNeighbor().getEidScheme());
			bundle.setLink(block.getLink());
			decodeState.close();
			block.discardBlockData();
			long t2 = System.currentTimeMillis();
			BPManagement.getInstance()._bpStats.nDecodingMSecs += (t2 - t1);
			
			if (GeneralManagement.isDebugLogging()) {
				if (_logger.isLoggable(Level.FINER)) {
					_logger.finer("onBlockReceived()");
					_logger.finer(bundle.dump("", true));
				}
			}
			BPProtocolAgent.getInstance().onBundleReceived(bundle);
			
		} catch (Exception e) {
			_logger.log(Level.SEVERE, "onBlockReceived()", e);
			_logger.severe("Further information on LTP Block Received:");
			_logger.severe(block.dump("", true));
		}
		
	}
	
	/**
	 * Called from LTP when a Block is successfully transmitted.  We pass
	 * the news on to BPProtocolAgent.
	 * @throws InterruptedException if interrupted
	 */
	@Override
	public void onBlockSent(Block block) throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onBlockSent()");
		}
		if (!(block.getUserData() instanceof Bundle)) {
			_logger.severe("Transmitted Block doesn't have a Bundle as its User Data");
		}
		Bundle bundle = (Bundle)block.getUserData();
		bundle.setAdaptationLayerData(null);
		BPProtocolAgent.getInstance().onBundleTransmitComplete(bundle);
	}

	/**
	 * Called from LTP when a Block Transmit has been cancelled.  We pass
	 * the news on to BPProtocolAgent.
	 * @throws InterruptedException 
	 * @throws InterruptedException 
	 */
	@Override
	public void onBlockTransmitCanceled(Block block, byte reason)
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("onBlockTransmitCanceled()");
		}
		if (!(block.getUserData() instanceof Bundle)) {
			_logger.severe("Cancelled Block doesn't have a Bundle as its User Data");
		}
		Bundle bundle = (Bundle)block.getUserData();
		BPProtocolAgent.getInstance().onBundleTransmitCancelledByReceiver(
				bundle, reason);
	}

	// Other LtpApiListener required methods 
	@Override
	public void onBlockReceiveCanceled(Block block, byte reason) {
		_logger.severe("Block Receive Canceled");
	}

	@Override
	public void onGreenSegmentReceived(DataSegment dataSegment) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("Green Segment Received");
		}
	}

	@Override
	public void onRedPartReceived(Block block) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("Red part recevied");
		}
	}

	@Override
	public void onSegmentsTransmitted(Block block) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("Segments Transmitted");
		}
	}

	@Override
	public void onSessionStarted(Block block) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("Session Started");
		}
	}

	@Override
	public void onSystemError(String description, Throwable e) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.log(Level.SEVERE, "LTP error: " + description, e);
		}
	}
	
}
