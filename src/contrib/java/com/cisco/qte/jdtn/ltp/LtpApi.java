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
package com.cisco.qte.jdtn.ltp;

import java.util.HashMap;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.apps.MediaRepository;
import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Link;
import com.cisco.qte.jdtn.general.Utils;

/**
 * Upper layer API to the LTP layer.  Provides API to:
 * <ul>
 *   <li>Send data as an outbound block
 *   <li>Cancel a Block that is in the process of being sent
 *   <li>Register a listener to respond to various events, including:
 *   <ul>
 *     <li>Receipt of an inbound Block
 *     <li>Cancellation of a Block, either inbound or outbound
 *     <li>Notification that a Block has been successfully sent
 *     <li>Notification that a LTP Session has been started (in-or-out-bound)
 *     <li>Notification that the Red part of an inbound Block has been received
 *     <li>Notification that a Green segment of an inbound Block has been received
 *   </ul>
 * </ul>
 */
public class LtpApi extends AbstractStartableComponent {
	
	private static final Logger _logger =
		Logger.getLogger(LtpApi.class.getCanonicalName());
	
	private static LtpApi _instance = null;
	private final HashMap<ServiceId, LtpListener> _listenerMap =
		new HashMap<ServiceId, LtpListener>();
	
	/**
	 * Get singleton instance of LtpApi
	 * @return Singleton instance
	 */
	public static LtpApi getInstance() {
		if (_instance == null) {
			_instance = new LtpApi();
		}
		return _instance;
	}
	
	private LtpApi() {
		super("LtpApi");
	}
	
	/**
	 * Startup this component
	 */
	@Override
	protected void startImpl() {
		// Nothing
	}
	
	/**
	 * Shutdown this component
	 */
	@Override
	protected void stopImpl() {
		_listenerMap.clear();
	}
	
	/**
	 * Send given data buffer to Neighbor with given EngineId
	 * @param engineId Given EngineId
	 * @param buffer Given data
	 * @param length Length of given data
	 * @param blockOptions Block send Options; null => default BlockOptions
	 * all green; no checkpointing; no header or trailer extensions
	 * @return The Block created
	 * @throws InterruptedException if get Interrupted while trying to enqueue
	 * @throws JDtnException on immediately detected errors
	 */
	public Block send(
			EngineId engineId, 
			byte[] buffer, 
			long length,
			BlockOptions blockOptions) 
	throws InterruptedException, JDtnException {
		return send(null, engineId, buffer, length, blockOptions);
	}
	
	/**
	 * Send given data buffer to Neighbor with given EngineId
	 * @param userData Anonymous user data to attach to the Block for callbacks.
	 * @param engineId Given EngineId
	 * @param buffer Given data
	 * @param length Length of given data
	 * @param blockOptions Block send Options; null => default BlockOptions
	 * all green; no checkpointing; no header or trailer extensions
	 * @return The Block created
	 * @throws InterruptedException if get Interrupted while trying to enqueue
	 * @throws JDtnException on immediately detected errors
	 */
	public Block send(
			Object userData,
			EngineId engineId, 
			byte[] buffer, 
			long length,
			BlockOptions blockOptions) 
	throws InterruptedException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("send(in-memory, length=" + length + ")");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(engineId.dump("  ", true));
				_logger.finest(Utils.dumpBytes("  ", buffer, 0, (int)length));
				if (blockOptions != null) {
					_logger.finest(blockOptions.dump("  ", true));
				}
			}
		}
		
		// Make sure we're alive
		if (!isStarted()) {
			throw new LtpException("LtpApi has not been started up");
		}
		
		// Make sure length is representable as an int
		if (length > Integer.MAX_VALUE) {
			throw new LtpException("Invalid length; must be repesentable as int");
		}
		
		// Get Neighbor with given EngineId
		LtpNeighbor neighbor = LtpNeighbor.findNeighborByEngineId(engineId);
		if (neighbor == null) {
			throw new LtpException("No neighbor found with EngineId: " + engineId);
		}
		// Get Link thru which Neighbor is reachable
		Link link = neighbor.findOperationalLink();
		if (link == null || !(link instanceof LtpLink)) {
			throw new LtpException("Neighbor associated with EngineId " + engineId +
					" has no associated Link");
		}
		// Assemble a LTP Block for given data
		OutboundBlock block = new OutboundBlock(
				neighbor, 
				(LtpLink)link, 
				buffer, 
				length, 
				blockOptions);
		block.setUserData(userData);
		
		// Enqueue the Block for transmit
		LtpOutbound.getInstance().enqueueOutboundBlock(block);
		
		return block;
	}
	
	/**
	 * Send given data File to Neighbor with given EngineId
	 * @param engineId Given EngineId
	 * @param file Given file
	 * @param length Length of File data
	 * @param blockOptions Block send Options; null => default BlockOptions
	 * all green; no checkpointing; no header or trailer extensions
	 * @return The Block created
	 * @throws InterruptedException on immediately detected exceptions
	 * @throws JDtnException on immediately detected exceptions
	 */
	public Block send(
			EngineId engineId, 
			MediaRepository.File file,
			long length, 
			BlockOptions blockOptions)
	throws InterruptedException, JDtnException {
		
		return send(null, engineId, file, length, blockOptions);
	}
	
	/**
	 * Send given data File to Neighbor with given EngineId
	 * @param userData Anonymous data to attach to Block sent for callbacks
	 * @param engineId Given EngineId
	 * @param file Given file
	 * @param length Length of File data
	 * @param blockOptions Block send Options; null => default BlockOptions
	 * all green; no checkpointing; no header or trailer extensions
	 * @return The Block created
	 * @throws InterruptedException on immediately detected exceptions
	 * @throws JDtnException on immediately detected exceptions
	 */
	public Block send(
			Object userData,
			EngineId engineId, 
			MediaRepository.File file,
			long length, 
			BlockOptions blockOptions)
	throws InterruptedException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.fine("send(in-file, length=" + length + ")");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(engineId.dump("  ", true));
				_logger.finest("  File=" + file.getAbsolutePath());
				if (blockOptions != null) {
					_logger.finest(blockOptions.dump("  ", true));
				}
			}
		}
		
		// Make sure we're alive
		if (!isStarted()) {
			throw new LtpException("LtpApi has not been started up");
		}
		
		// Get Neighbor with given EngineId
		LtpNeighbor neighbor = LtpNeighbor.findNeighborByEngineId(engineId);
		if (neighbor == null) {
			throw new LtpException("No neighbor found with EngineId: " + engineId);
		}
		// Get Link thru which Neighbor is reachable
		Link link = neighbor.findOperationalLink();
		if (link == null || !(link instanceof LtpLink)) {
			throw new LtpException("Neighbor associated with EngineId " + engineId +
					" has no associated Link");
		}
		OutboundBlock block = new OutboundBlock(
				neighbor, 
				(LtpLink)link, 
				file, 
				length, 
				blockOptions);
		block.setUserData(userData);
		
		// Enqueue the Block for transmit
		LtpOutbound.getInstance().enqueueOutboundBlock(block);
		return block;
	}
	
	/**
	 * Cancel a Block which we have previously enqueued for sending
	 * @param block Block previously enqueued
	 * @param reason Reason code
	 * @throws InterruptedException if interrupted waiting for queue space
	 * @throws LtpException if neighbor is down and its queue has been full
	 * for some time.
	 */
	public void cancelSentBlock(Block block, byte reason) 
	throws InterruptedException, LtpException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("cancelSentBlock(reason=" + reason + ")");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(block.dump("  ", true));
			}
		}
		
		if (!(block instanceof OutboundBlock)) {
			throw new LtpException("Given Block is not an OutboundBlock");
		}
		
		// Make sure we're alive
		if (!isStarted()) {
			throw new LtpException("LtpApi has not been started up");
		}
		
		LtpOutbound.getInstance().cancelBlock((OutboundBlock)block, reason);
	}
	
	/**
	 * Called when a Transmit Block has been successfully Canceled.  We notify any
	 * Listener on this Block.
	 * @param block Block which was canceled.
	 * @param reason Reason for cancellation
	 * @throws InterruptedException  on interrupt
	 */
	public void onBlockTransmitCancelled(Block block, byte reason)
	throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("onBlockCancelled()");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finer(block.dump("  ", true));
			}
		}
		
		// Notify any Listener for this block
		ServiceId serviceId = block.getBlockOptions().serviceId;
		LtpListener listener = null;
		synchronized (_listenerMap) {
			listener = _listenerMap.get(serviceId);
		}
		if (listener != null) {
			listener.onBlockTransmitCanceled(block, reason);
		}
	}
	
	/**
	 * Notification that all Segments of the given Block have been initially
	 * transmitted (but not necessarily reported on and re-sent
	 * @param block Given Block
	 */
	public void onSegmentsTransmitted(Block block) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("onSegmentsTransmitted()");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finer(block.dump("  ", true));
			}
		}
		
		// Notify any Listener for this block
		ServiceId serviceId = block.getBlockOptions().serviceId;
		LtpListener listener = null;
		synchronized (_listenerMap) {
			listener = _listenerMap.get(serviceId);
		}
		if (listener != null) {
			listener.onSegmentsTransmitted(block);
		}
	}
	
	/**
	 * Called when a Receive Block has been successfully Canceled.  We notify any
	 * Listener on this Block.
	 * @param block Block which was canceled.
	 * @param reason Reason for cancellation
	 */
	public void onBlockReceiveCancelled(Block block, byte reason) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("onBlockCancelled()");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finer(block.dump("  ", true));
			}
		}
		
		// Notify any Listener for this block
		ServiceId serviceId = block.getBlockOptions().serviceId;
		LtpListener listener = null;
		synchronized (_listenerMap) {
			listener = _listenerMap.get(serviceId);
		}
		if (listener != null) {
			listener.onBlockReceiveCanceled(block, reason);
		}
	}
	
	/**
	 * Called when a Block has been successfully Received.  We notify any
	 * Listener on this Block.
	 * @param block Block which was canceled.
	 */
	public void onBlockReceived(java.sql.Connection con, Block block) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("onBlockReceived()");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finer(block.dump("  ", true));
			}
		}
		
		// Notify any Listener for this block
		ServiceId serviceId = block.getBlockOptions().serviceId;
		LtpListener listener = null;
		synchronized (_listenerMap) {
			listener = _listenerMap.get(serviceId);
		}
		if (listener != null) {
			listener.onBlockReceived(con, block);
		}
	}
	
	/**
	 * Called when a Block has been successfully Sent.  We notify any
	 * Listener on this Block.
	 * @param block Block which was canceled.
	 * @throws InterruptedException if interrupted
	 */
	public void onBlockSent(Block block) throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("onBlockSent()");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finer(block.dump("  ", true));
			}
		}
		
		// Notify any Listener for this block
		ServiceId serviceId = block.getBlockOptions().serviceId;
		LtpListener listener = null;
		synchronized (_listenerMap) {
			listener = _listenerMap.get(serviceId);
		}
		if (listener != null) {
			listener.onBlockSent(block);
		}
	}
	
	/**
	 * Called when a LTP Session has been started for transmission of the
	 * given Block.  We notify any Listener on this Block.
	 * @param block Given Block
	 */
	public void onSessionStarted(Block block) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("onSessionStarted()");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finer(block.dump("  ", true));
			}
		}
		
		// Notify any Listener for this block
		ServiceId serviceId = block.getBlockOptions().serviceId;
		LtpListener listener = null;
		synchronized (_listenerMap) {
			listener = _listenerMap.get(serviceId);
		}
		if (listener != null) {
			listener.onSessionStarted(block);
		}
	}
	
	/**
	 * Called when a checkpoint segment is received when it is known that
	 * EORP has already or coincidentally received.  We notify listeners.
	 * @param block Affected Block
	 */
	public void onRedPartReceived(Block block) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("onRedPartReceived()");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finer(block.dump("  ", true));
			}
		}
		
		// Notify any Listener for this block
		ServiceId serviceId = block.getBlockOptions().serviceId;
		LtpListener listener = null;
		synchronized (_listenerMap) {
			listener = _listenerMap.get(serviceId);
		}
		if (listener != null) {
			listener.onRedPartReceived(block);
		}
	}
	
	/**
	 * Called when a Green DataSegment is received
	 * @param segment Received Green DataSegment
	 */
	public void onGreenSegmentReceived(DataSegment segment) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("onGreenSegmentReceived()");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finer(segment.dump("  ", true));
			}
		}
		
		// Notify any Listener for this block
		ServiceId serviceId = segment.getClientServiceId();
		LtpListener listener = null;
		synchronized (_listenerMap) {
			listener = _listenerMap.get(serviceId);
		}
		if (listener != null) {
			listener.onGreenSegmentReceived(segment);
		}
	}
	
	/**
	 * Called when an unrecoverable error occurs
	 * @param description Description of the error
	 * @param e Possible Exception thrown (may be null)
	 */
	public void onSystemError(String description, Throwable e) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("onSystemError()");
		}
		
		// Notify all Listeners
		Set<ServiceId> keys = _listenerMap.keySet();
		for (ServiceId serviceId : keys) {
			LtpListener listener = _listenerMap.get(serviceId);
			if (listener != null) {
				listener.onSystemError(description, e);
			} else {
				_logger.severe("Inconsistent listener map and keyset");
				_logger.log(Level.SEVERE, description, e);
			}
		}
		
	}
	
	/**
	 * Determine if there is a client registered to receive given ServiceId
	 * @param serviceId Given ServiceId
	 * @return True if there is such a client registered
	 */
	public boolean isClientRegisteredForServiceId(ServiceId serviceId) {
		return _listenerMap.containsKey(serviceId);
	}
	
	/**
	 * Register given LtpListener for events on given ServiceId
	 * @param listener Given LtpListener
	 * @param serviceId Given ServiceId
	 */
	public void addLtpListener(LtpListener listener, ServiceId serviceId) {
		synchronized (_listenerMap) {
			_listenerMap.put(serviceId, listener);
		}
	}
	
	/**
	 * Unregister given LtpListener from events on given ServiceId
	 * @param listener Given LtpListener
	 * @param serviceId Given ServiceId
	 */
	public void removeLtpListener(LtpListener listener, ServiceId serviceId) {
		synchronized (_listenerMap) {
			_listenerMap.remove(serviceId);
//			_listenerMap.put(serviceId, listener);
		}
	}

	@Override
	public String toString() {
		return dump("", false);
	}
	
	/**
	 * Dump the state of this object
	 * @param indent Amount of indentation
	 * @param detailed True if want verbose details
	 * @return String containing dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "LtpApi\n");
		sb.append(super.dump(indent + "  ", detailed));
		return sb.toString();
	}
	
}
