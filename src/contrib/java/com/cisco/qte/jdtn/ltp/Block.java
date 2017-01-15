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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TimerTask;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.apps.MediaRepository;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.LinksList;
import com.cisco.qte.jdtn.general.Store;
import com.cisco.qte.jdtn.ltp.BlockOptions.CheckpointOption;

/**
 * An LTP Block - The aggregate unit of client data.  A Block
 * is composed of a series of DataSegments.  A Block also keeps Session state,
 * in particular it holds the SessionId of the Session.
 * This is the abstract superclass for:
 * <ul>
 *   <li>InboundBlock - A Block received or in the process of being received
 *   <li>OutboundBlock - A Block sent or in the process of being sent
 * </ul>
 */
public abstract class Block implements Iterable<DataSegment> {
	private static final Logger _logger =
		Logger.getLogger(Block.class.getCanonicalName());
	
	 // List of Segments comprising this Block.  The List is maintained in
	 // order of client data offset.
	private List<DataSegment> _segments = new ArrayList<DataSegment>(1200);
	
	// The Neighbor to which this Block is to be sent, or from which this
	// Block was received
	protected LtpNeighbor _neighbor;
	
	// The Link on which this Block is to be sent, or on which this Block
	// was received.
	protected LtpLink _link;
	
	// The SessionID for this Block
	protected SessionId _sessionId;
	
	// Whether data is in file or in memory
	protected boolean _dataInFile;
	
	// If data is in file, the path to the file
	protected MediaRepository.File _dataFile;
	
	// If data is in memory, the buffer containing the data
	protected byte[] _dataBuffer;
	
	// Length of data in memory buffer or in file
	protected long _dataLength;
	
	// Total length of Red Data in this Block
	protected long _redDataLength;
	
	// Options for this Block
	protected BlockOptions _blockOptions;
	
	private CancelSegment _outstandingCancelSegment = null;
	
	// CancelTimer; A timer started when a CancelSegment is sent; timing the
	// reply CancelAckSegment.
	protected TimerTask _cancelTimerTask;
	
	// Anonymous User data attached to the Block
	protected Object _userData = null;
	
	// True until discardBlockData() called
	protected boolean _valid = true;
	
	/**
	 * Constructor for an outbound Block with memory-based data
	 * @param neighbor Neighbor to which the Block should be sent
	 * @param link Link on which the Block should be sent
	 * @param buffer Buffer containing Data to be sent
	 * @param length Length of data to be sent
	 * @param options Options for transmission; if null, default BlockOptions
	 * will be used: all bytes red, checkpoint requested, no header or trailer
	 * options.
	 * @throws LtpException on Immediately detected errors
	 */
	public Block(
			LtpNeighbor neighbor, 
			LtpLink link, 
			byte[] buffer, 
			long length,
			BlockOptions options) throws LtpException {
		
		initThis(neighbor, link, false, null, buffer, length, null, options);
	}
	
	/**
	 * Constructor for an outbound Block with file-based data
	 * @param neighbor Neighbor to which the Block should be sent
	 * @param link Link on which the Block should be sent
	 * @param file File containing data to be sent
	 * @param length Length of data to be sent
	 * @param options Options for transmission; if null, default BlockOptions
	 * will be used: all bytes red, checkpoint requested, no header or trailer
	 * options.
	 * @throws LtpException on Immediately detected errors
	 */
	public Block(
			LtpNeighbor neighbor, 
			LtpLink link, 
			MediaRepository.File file,
			long length,
			BlockOptions options) throws LtpException {
		
		initThis(neighbor, link, true, file, null, length, null, options);
	}
	
	/**
	 * Constructor for an inbound Block whose first Segment is the given
	 * DataSegment.
	 * @param dataSegment Given DataSegment.  Note that this is not necessarily
	 * the DataSegment with offset 0.  It is merely the first DataSegment
	 * received.
	 * @throws JDtnException On various errors
	 */
	public Block(DataSegment dataSegment) throws JDtnException {
		BlockOptions options = new BlockOptions(dataSegment);
		options.serviceId = dataSegment.getClientServiceId();
		if (dataSegment.getClientDataLength() > LtpManagement.getInstance().getBlockLengthFileThreshold()) {
			initThis(dataSegment.getNeighbor(), dataSegment.getLink(),
					true, Store.getInstance().createNewBlockFile(),
					null, 0,
					dataSegment.getSessionID(), options);
		} else {
			initThis(dataSegment.getNeighbor(), dataSegment.getLink(),
					false, null,
					dataSegment.getClientData(), 0,
					dataSegment.getSessionID(), options);
		}
		if (dataSegment.isCheckpoint()) {
			setSessionId(dataSegment.getSessionID());
		}
		// Undo what initThis did to redDataLength, so can do it again in addInboundSegment
		_redDataLength = 0;
		addInboundSegment(dataSegment);
	}
	
	/**
	 * Internal Initialization
	 * @param neighbor Neighbor to which the Block should be sent
	 * @param link Link on which the Block should be sent
	 * @param dataInFile True => data to send is in a File
	 * @param dataFile if dataInFile is true, File containing data to be sent
	 * @param dataBuffer If dataInFile is false, buffer containing data to send
	 * @param dataLength Length of data to be sent
	 * @param options Options for transmission; if null, default BlockOptions
	 * will be used: all bytes red, checkpoint requested, no header or trailer
	 * options.
	 * @throws LtpException 
	 */
	private void initThis(
			LtpNeighbor neighbor, 
			LtpLink link, 
			boolean dataInFile,
			MediaRepository.File dataFile,
			byte[] dataBuffer,
			long dataLength,
			SessionId sessionId,
			BlockOptions options) throws LtpException {
		
		// Do some error checking
		if (!LinksList.getInstance().contains(link)) {
			throw new LtpException("Given Link not configured: Link=" + link);
		}
		
		this._dataInFile = dataInFile;
		this._dataFile = dataFile;
		this._dataBuffer = dataBuffer;
		this._dataLength = dataLength;
		
		setNeighbor(neighbor);
		setLink(link);
		if (options == null) {
			options = new BlockOptions();
		}
		setBlockOptions(options);
		_redDataLength = options.redLength;
		
		// If caller specified redData, but no checkpoint options specifed, then
		// make it green instead.  This is because the protocol can't handle
		// a Block with Red segments but no Checkpoints.
		if (_redDataLength > 0 && options.checkpointOption == CheckpointOption.NO_CHECKPOINTING) {
			_redDataLength = 0;
		}
		if (sessionId == null) {
			createSession();
		} else {
			setSessionId(sessionId);
		}
	}
	
	/**
	 * Create a Session for this Block.  Creates the SessionId and populates
	 * property SessionId.
	 */
	private void createSession() {
		SessionId sessionId = new SessionId();
		setSessionId(sessionId);
	}
	
	/**
	 * Called when this Block is closed, giving the Block an opportunity
	 * to clean up. 
	 * Clean up consists of:
	 * <ul>
	 *   <li> Remove outstanding cancel segment and kill its Cancel timer.
	 * </ul>
	 */
	public void closeBlock(java.sql.Connection con) {
		_outstandingCancelSegment = null;
		if (_cancelTimerTask != null) {
			_cancelTimerTask.cancel();
			_cancelTimerTask = null;
		}
	}

	/**
	 * Discard this Block; remove all outstanding storage behind it
	 */
	public void discardBlockData(java.sql.Connection con) {
		if (_valid) {
			for (DataSegment segment : this) {
				segment.discardData(con);
			}
			if (isDataInFile()) {
				if (getDataFile().exists(con) && !getDataFile().delete(con)) {
					_logger.warning("Cannot delete Block storage: " +
							getDataFile().getAbsolutePath());
				}
			}
		}
		_valid = false;
	}
	
	/**
	 * Append given DataSegment to end of list of DataSegments comprising this
	 * Block.  It is assumed that caller knows that the Segment belongs to the
	 * end of the list, rather than in the interior, thus keeping the ordering
	 * of the list by ClientDataOffset.
	 * @param segment
	 */
	protected void appendSegment(DataSegment segment) {
		_segments.add(segment);
		segment.setBlock(this);
	}
	
	/**
	 * Add given DataSegment to the list of DataSegments comprising this Block.
	 * It is assume that the DataSegment and Block are inbound, and are being
	 * assembled on the fly.
	 * The list of DataSegments is maintained in order by DataSegment
	 * ClientDataOffset.
	 * We also update the member _dataLength.
	 * @param segment DataSegment to add.
	 * @throws LtpException on errors spilling Segment Data to a file
	 */
	public void addInboundSegment(DataSegment segment) throws LtpException {
		segment.setBlock(this);
		for (int ix = 0; ix < _segments.size(); ix++) {
			DataSegment otherSegment = _segments.get(ix);
			if (segment.getClientDataOffset() == otherSegment.getClientDataOffset() &&
				segment.getClientDataLength() == otherSegment.getClientDataLength()) {
				// Exact match on an existing Segment in the Block.  Replace.
				_segments.set(ix, segment);
				return;
			}
			if (segment.getClientDataOffset() < otherSegment.getClientDataOffset()) {
				// Found right place to insert to keep it in offset order.
				_dataLength += segment.getClientDataLength();
				if (segment.isRedData()) {
					_redDataLength += segment.getClientDataLength();
				}
				_segments.add(ix, segment);
				return;
			}
		}
		// Add it to the end
		_dataLength += segment.getClientDataLength();
		if (segment.isRedData()) {
			_redDataLength += segment.getClientDataLength();
		}
		_segments.add(segment);
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
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "Block\n");
		sb.append(indent + "  DataLength=" + _dataLength + "\n");
		sb.append(indent + "  DataInFile=" + _dataInFile + "\n");
		if (_dataInFile) {
			sb.append(indent + "  DataFile=" + _dataFile.getAbsolutePath() + "\n");
		} else {
			if (_dataBuffer != null) {
				sb.append(indent + "  DataBuffer.length=" + _dataBuffer.length + "\n");
				if (_dataBuffer.length != _dataLength) {
					sb.append(indent + "  DataBuffer.length & DataLength inconsistent\n");
				}
			}
		}
		sb.append(indent + "  Neighbor=" + _neighbor.getName() + "\n");
		sb.append(indent + "  Link=" + _link.getName() + "\n");
		sb.append(_sessionId.dump(indent + "  ", detailed));
		sb.append(_blockOptions.dump(indent + "  ", detailed));
		if (detailed) {
			sb.append(indent + "  Data Segments\n");
			for (DataSegment dataSegment : _segments) {
				sb.append(dataSegment.dump(indent + "    ", detailed));
			}
		}
		return sb.toString();
	}

	/**
	 * Get an Iterator over all DataSegments comprising the Block, suitable
	 * for use in a For Each loop.
	 */
	@Override
	public Iterator<DataSegment> iterator() {
		return _segments.iterator();
	}

	/**
	 * Get length of DataSegments list.
	 * @return Length
	 */
	public int getDataSegmentsSize() {
		return _segments.size();
	}
	
	/**
	 * Get the n'th DataSegment of the list of DataSegments comprising this
	 * Block.
	 * @param index Index of DataSegment to get; 0-based.
	 * @return DataSegment
	 * @throws ArrayIndexOutOfBoundsException if index out of bounds.
	 */
	public DataSegment getDataSegment(int index) {
		return _segments.get(index);
	}
	
	/**
	 * The Neighbor to which this Block is to be sent, or from which this
	 * Block was received
	 */
	public LtpNeighbor getNeighbor() {
		return _neighbor;
	}

	/**
	 * The Neighbor to which this Block is to be sent, or from which this
	 * Block was received
	 */
	public void setNeighbor(LtpNeighbor neighbor) {
		this._neighbor = neighbor;
	}

	/**
	 * The Link on which this Block is to be sent, or on which this Block
	 * was received.
	 */
	public LtpLink getLink() {
		return _link;
	}

	/**
	 * The Link on which this Block is to be sent, or on which this Block
	 * was received.
	 */
	public void setLink(LtpLink link) {
		this._link = link;
	}

	/**
	 * The SessionID for this Block
	 */
	public SessionId getSessionId() {
		return _sessionId;
	}

	/**
	 * The SessionID for this Block
	 */
	public void setSessionId(SessionId sessionId) {
		this._sessionId = sessionId;
	}

	/**
	 * Options for this Block
	 */
	public BlockOptions getBlockOptions() {
		return _blockOptions;
	}

	/**
	 * Options for this Block
	 */
	public void setBlockOptions(BlockOptions blockOptions) {
		this._blockOptions = blockOptions;
	}

	/**
	 * Whether data is in file or in memory
	 */
	public boolean isDataInFile() {
		return _dataInFile;
	}

	/**
	 * If data is in file, the path to the file
	 */
	public MediaRepository.File getDataFile() {
		return _dataFile;
	}

	/**
	 * If data is in memory, the buffer containing the data
	 */
	public byte[] getDataBuffer() {
		return _dataBuffer;
	}

	/**
	 * Length of data in memory buffer or in file
	 */
	public long getDataLength() {
		return _dataLength;
	}

	/**
	 * Total length of Red Data in this Block
	 */
	public long getRedDataLength() {
		return _redDataLength;
	}

	/**
	 * Cancel Segment issued on this Block
	 */
	public CancelSegment getOutstandingCancelSegment() {
		return _outstandingCancelSegment;
	}

	/**
	 * Cancel Segment issued on this Block
	 */
	public void setOutstandingCancelSegment(CancelSegment outstandingCancelSegment) {
		this._outstandingCancelSegment = outstandingCancelSegment;
	}

	/** Anonymous user data attached to the Block */
	public Object getUserData() {
		return _userData;
	}

	/** Anonymous user data attached to the Block */
	public void setUserData(Object userData) {
		this._userData = userData;
	}

	/** True until discardBlockData() called */
	public boolean isValid() {
		return _valid;
	}

	/**
	 * CancelTimer; A timer started when a CancelSegment is sent; timing the
	 * reply CancelAckSegment.
	 * */
	public TimerTask getCancelTimerTask() {
		return _cancelTimerTask;
	}

	/**
	 * CancelTimer; A timer started when a CancelSegment is sent; timing the
	 * reply CancelAckSegment.
	 * */
	public void setCancelTimerTask(TimerTask cancelTimerTask) {
		this._cancelTimerTask = cancelTimerTask;
	}
	
	/**
	 * Perform a simple consistency check on the block's data just before
	 * delivery of the completed block.
	 * @throws LtpException if inconsistency detected
	 */
	public void consistencyCheck() throws LtpException {
		if (!_dataInFile && _dataBuffer != null) {
			if (_dataLength != _dataBuffer.length) {
				_logger.severe("Inconsistent Block _dataLength (" +
						_dataLength +
						") and _dataBuffer.length (" +
						_dataBuffer.length +
						")");
				_logger.severe(dump("", true));
				throw new LtpException("Inconsistent _dataLength (" +
						_dataLength +
						") and _dataBuffer.length (" +
						_dataBuffer.length +
						")");
			}
		}
	}
	
}
