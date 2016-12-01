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
package com.cisco.qte.jdtn.udpcl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Utils;

/**
 * UDP Convergence Layer API to upper layers
 */
public class UdpClAPI extends AbstractStartableComponent {
	private static final Logger _logger =
		Logger.getLogger(UdpClAPI.class.getCanonicalName());
	
	private static UdpClAPI _instance = null;
	
	private ArrayList<UdpClListener> _listeners = new ArrayList<UdpClListener>();
	
	/**
	 * Get singleton instance
	 * @return Singleton instance
	 */
	public static UdpClAPI getInstance() {
		if (_instance == null) {
			_instance = new UdpClAPI();
		}
		return _instance;
	}
	
	/**
	 * Protected access constructor
	 */
	protected UdpClAPI() {
		super("UdpClAPI");
	}
	
	/**
	 * Add upper layer listener for UDP CL events
	 * @param listener Listener to add
	 */
	public void addUdpClListener(UdpClListener listener) {
		synchronized (_listeners) {
			_listeners.add(listener);
		}
	}
	
	/**
	 * Remove upper layer listener for UDP CL events
	 * @param listener Listener to add
	 */
	public void removeUdpClListener(UdpClListener listener) {
		synchronized (_listeners) {
			_listeners.remove(listener);
		}
	}
	
	/**
	 * Start this component.
	 * This does nothing.
	 */
	@Override
	protected void startImpl() {
		// Nothing
	}
	
	/**
	 * Stop this component.
	 * This results in all listeners being deregistered
	 */
	@Override
	protected void stopImpl() {
		synchronized (_listeners) {
			_listeners.clear();
		}
	}
	
	/**
	 * Send a block of data to given Neighbor
	 * @param block Data block to send
	 * @param link Link on which to send
	 * @param neighbor Neighbor to send to
	 * @throws JDtnException on UdpCl errors
	 * @throws InterruptedException If interrupted during processing
	 */
	public void sendBlock(
			UdpClDataBlock block,
			UdpClLink link, 
			UdpClNeighbor neighbor)
	throws JDtnException, InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("sendBlock");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest("Link=" + link.getName());
				_logger.finest("Neighbor=" + neighbor.getName());
				_logger.finest(block.dump("", true));
			}
			neighbor.enqueueOutboundBlock(block);
		}
	}
	
	/**
	 * Send a block of data to given Neighbor
	 * @param data Data to send
	 * @param length Length of data to send
	 * @param link Link on which to send
	 * @param neighbor Neighbor to send to
	 * @param blockId An opaque identifier for the block being sent
	 * @throws JDtnException on UdpCl errors
	 * @throws InterruptedException If interrupted during processing
	 */
	public void sendBlock(
			byte[] data, 
			int length, 
			UdpClLink link, 
			UdpClNeighbor neighbor, 
			Object blockId)
	throws JDtnException, InterruptedException {
		
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("sendBlock");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest("Link=" + link.getName());
				_logger.finest("Neighbor=" + neighbor.getName());
				_logger.finest(Utils.dumpBytes(data, 0, length, true));
			}
		}
		
		UdpClDataBlock block = new UdpClDataBlock(
				data, length, link, neighbor, blockId);
		neighbor.enqueueOutboundBlock(block);
	}

	/**
	 * Send a file block of data to given Neighbor
	 * @param file File to send
	 * @param length Length of data tos end
	 * @param link Link on which to send
	 * @param neighbor Neighbor to send to
	 * @param blockId An opaque identifier for the block being sent
	 * @throws JDtnException on UdpCl errors
	 * @throws InterruptedException If interrupted during processing
	 * @throws IOException on I/O errors
	 */
	public void sendBlock(
			File file,
			int length,
			UdpClLink link,
			UdpClNeighbor neighbor,
			Object blockId)
	throws JDtnException, InterruptedException, IOException {
		
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("sendBlock(file=" + file.getAbsolutePath());
			_logger.finest("Link=" + link.getName());
			_logger.finest("Neighbor=" + neighbor.getName());
		}
		
		byte[] buffer = new byte[(int)length];
		InputStream ios = null;
		try {
			ios = new FileInputStream(file);
			int nRead = ios.read(buffer, 0, (int)length);
			if (nRead != length) {
				throw new JDtnException("Tried to read " + length + " but only read " + nRead);
			}
			sendBlock(buffer, length, link, neighbor, blockId);
			
		} finally {
			if (ios != null) {
				ios.close();
			}
		}
	}
	
	/**
	 * Notification from UdpCl that Block has been successfully transmitted.
	 * @param dataBlock Block transmitted
	 */
	public void blockTransmitted(UdpClDataBlock dataBlock) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("blockTransmitted()");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(dataBlock.dump("", true));
			}
		}
		ArrayList<UdpClListener> tempList = null;
		synchronized (_listeners) {
			tempList = new ArrayList<UdpClListener>(_listeners);
		}
		for (UdpClListener listener : tempList) {
			listener.notifyBlockTransmitComplete(dataBlock);
		}
	}
			
	/**
	 * Notification from lower layer that an inbound block has been received
	 * @param dataBlock Block received
	 * @param length Length of Block received
	 */
	public void notifyInboundBlock(UdpClDataBlock dataBlock, long length) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("notifyInboundBlock()");
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(dataBlock.dump("", true));
			}
		}
		
		ArrayList<UdpClListener> tempList = null;
		synchronized (_listeners) {
			tempList = new ArrayList<UdpClListener>(_listeners);
		}
		for (UdpClListener listener : tempList) {
			listener.notifyInboundBlock(dataBlock, length);
		}
	}
	
	/**
	 * Notification from lower layer that transmit of block failed.
	 * @param dataBlock The block which failed
	 * @param t Exception causing failure or null
	 */
	public void notifyOutboundBlockError(UdpClDataBlock dataBlock, Throwable t) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.log(Level.FINE, "Outbound Block Transmission Failed", t);
			if (_logger.isLoggable(Level.FINEST)) {
				_logger.finest(dataBlock.dump("", true));
			}
		}
		
		ArrayList<UdpClListener> tempList = null;
		synchronized (_listeners) {
			tempList = new ArrayList<UdpClListener>(_listeners);
		}
		for (UdpClListener listener : tempList) {
			listener.notifyOutboundBlockError(dataBlock, t);
		}
	}
	
}
