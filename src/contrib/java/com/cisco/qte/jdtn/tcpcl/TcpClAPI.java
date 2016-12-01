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
package com.cisco.qte.jdtn.tcpcl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.general.JDtnException;

/**
 * Upper Layer's API to Tcp Convergence Layer
 */
public class TcpClAPI extends AbstractStartableComponent {
	private static final Logger _logger =
		Logger.getLogger(TcpClAPI.class.getCanonicalName());
	
	private static TcpClAPI _instance = null;
	
	private ArrayList<TcpClListener> _listeners = new ArrayList<TcpClListener>();
	
	/**
	 * Get singleton instance
	 * @return Singleton instance
	 */
	public static TcpClAPI getInstance() {
		if (_instance == null) {
			_instance = new TcpClAPI();
		}
		return _instance;
	}
	
	/**
	 * Do nothing constructor
	 */
	protected TcpClAPI() {
		super("TcpClAPI");
	}
	
	@Override
	protected void startImpl() {
		// Nothing
	}
	
	@Override
	protected void stopImpl() {
		synchronized (_listeners) {
			_listeners.clear();
		}
	}
	
	/**
	 * Send a block of data to given Neighbor
	 * @param data Data to send
	 * @param length Length of data to send
	 * @param link Link on which to send
	 * @param neighbor Neighbor to send to
	 * @param blockId An opaque identifier for the block being sent
	 * @throws JDtnException on TcpCl errors
	 * @throws InterruptedException If interrupted during processing
	 */
	public void sendBlock(
			byte[] data, 
			long length, 
			TcpClLink link, 
			TcpClNeighbor neighbor, 
			Object blockId)
	throws JDtnException, InterruptedException {
		if (!(neighbor instanceof TcpClNeighbor)) {
			throw new JDtnException("Neighbor not instanceof TcpClNeighbor");
		}
		TcpClDataBlock dataBlock = new TcpClDataBlock(
				data, 
				length, 
				link, 
				neighbor, 
				blockId);
		TcpClNeighbor tcpClNeighbor = (TcpClNeighbor)neighbor;
		tcpClNeighbor.enqueueOutboundBlock(dataBlock);
	}
	
	/**
	 * Send a file block of data to given Neighbor
	 * @param file File to send
	 * @param length Length of data tos end
	 * @param link Link on which to send
	 * @param neighbor Neighbor to send to
	 * @param blockId An opaque identifier for the block being sent
	 * @throws JDtnException on TcpCl errors
	 * @throws InterruptedException If interrupted during processing
	 * @throws IOException on I/O errors
	 */
	public void sendBlock(
			File file,
			long length,
			TcpClLink link,
			TcpClNeighbor neighbor,
			Object blockId)
	throws JDtnException, InterruptedException, IOException {
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
	 * Notification from TcpCl that Block has been successfully transmitted.
	 * @param dataBlock Block transmitted
	 */
	public void blockTransmitted(TcpClDataBlock dataBlock) {
		ArrayList<TcpClListener> tempList = null;
		synchronized (_listeners) {
			tempList = new ArrayList<TcpClListener>(_listeners);
		}
		for (TcpClListener listener : tempList) {
			listener.notifyBlockTransmitComplete(dataBlock);
		}
	}
			
	/**
	 * Notification from lower layer that an inbound block has been received
	 * @param dataBlock Block received
	 * @param length Length of Block received
	 */
	public void notifyInboundBlock(TcpClDataBlock dataBlock, long length) {
		_logger.finer("notifyInboundBlock()");
		if (_logger.isLoggable(Level.FINEST)) {
			_logger.finest(dataBlock.dump("", true));
		}
		
		ArrayList<TcpClListener> tempList = null;
		synchronized (_listeners) {
			tempList = new ArrayList<TcpClListener>(_listeners);
		}
		for (TcpClListener listener : tempList) {
			listener.notifyInboundBlock(dataBlock, length);
		}
	}
	
	/**
	 * Notification from lower layer that transmit of block failed.
	 * @param dataBlock The block which failed
	 * @param t Exception causing failure or null
	 */
	public void notifyOutboundBlockError(TcpClDataBlock dataBlock, Throwable t) {
		_logger.log(Level.SEVERE, "OutboundBundleError", t);
		if (_logger.isLoggable(Level.FINEST)) {
			_logger.finest(dataBlock.dump("", true));
		}
		
		ArrayList<TcpClListener> tempList = null;
		synchronized (_listeners) {
			tempList = new ArrayList<TcpClListener>(_listeners);
		}
		for (TcpClListener listener : tempList) {
			listener.notifyOutboundBlockError(dataBlock, t);
		}
	}
	
	/**
	 * Add listener for TcpClAPI events
	 * @param listener Given Listener
	 */
	public void addTcpClListener(TcpClListener listener) {
		synchronized(_listeners) {
			_listeners.add(listener);
		}
	}
	
	/**
	 * Remove listener for TcpClAPI events
	 * @param listener Given Listener
	 */
	public void removeTcpClListener(TcpClListener listener) {
		synchronized(_listeners) {
			_listeners.remove(listener);
		}
	}
	
}
