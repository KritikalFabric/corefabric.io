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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.TimerTask;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.apps.MediaRepository;
import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Store;
import com.cisco.qte.jdtn.general.Utils;
import org.kritikal.fabric.contrib.jdtn.BlobAndBundleDatabase;

/**
 * DataSegment - A Segment which transports user data.
 */
public class DataSegment extends Segment {
	private static final Logger _logger =
		Logger.getLogger(DataSegment.class.getCanonicalName());
	
	/** Identifies upper-layer service to which segment is to be delivered */
	protected ServiceId _clientServiceId;

	/** Offset of client data w/in session's transmitted block */
	protected long _clientDataOffset;
	
	/** Length of client service data, bytes */
	protected int _clientDataLength;

	/** If this DataSegment is a checkpoint, then this is a serial number
	 * uniquely identifying the checkpoint among all checkpoints issued by
	 * sender for this session.
	 */
	protected CheckpointSerialNumber _checkpointSerialNumber;

	/** If this DataSegment was trasmitted in response to a ReportSegment,
	 * then this is the report serial number value from the triggering
	 * ReportSegment
	 */
	protected ReportSerialNumber _reportSerialNumber;
	
	/** Whether client service data is in a file (or in memory) */
	protected boolean _clientDataInFile;

	/** If clientDataInFile is true, then this is the file it is stored in */
	protected MediaRepository.File _clientDataFile;

	/** If clientDataInFile is false, then this is the data buffer */
	protected byte[] _clientData;

	/** Block of which this DataSegment is a part */
	protected Block _block;
	
	/**
	 * For outbound DataSegment, True if a claim has been received claiming
	 * receipt of this segment.
	 * For inbound DataSegment, True if a ReportAck has been received which
	 * itself claimed receipt of this Segment.
	 */
	protected boolean _acked = false;
	
	/** True if this DataSegment has been sent at least once */
	protected boolean _sent = false;
	
	/**
	 * Checkpoint timer; Started when send a DataSegment.
	 * Used to timeout on RS (Report Segment) coming
	 * back from that DataSegment.  Managed by LtpOutbound.
	 */
	protected TimerTask _checkpointTimerTask;
	
	/** mSecs spent encoding payload */
	protected long _mSecsEncodingPayload = 0;
	
	/**
	 * Constructor with given SegmentType
	 */
	public DataSegment(SegmentType segmentType) {
		super(segmentType);
	}
	
	/**
	 * Called from super to decode the contents of the segment.  Super has
	 * already decoded the segment header.  This decodes the segment type
	 * specific body of the segment.  Here, we are primarily concerned with
	 * decoding the client service payload.
	 */
	@Override
	protected void decodeContents(java.sql.Connection con, DecodeState decodeState) throws JDtnException {
		// Decode payload descriptive fields
		setClientServiceId(new ServiceId(decodeState));
		setClientDataOffset(Utils.sdnvDecodeLong(decodeState));
		setClientDataLength(Utils.sdnvDecodeInt(decodeState));
		
		if (isCheckpoint()) {
			setCheckpointSerialNumber(new CheckpointSerialNumber(decodeState));
			setReportSerialNumber(new ReportSerialNumber(decodeState));
		}
		
		// Make sure the payload description is consistent with the data length
		long remainingLength = decodeState.remainingLength();
		if (_clientDataLength > remainingLength) {
			throw new JDtnException(
					"Data Segment length (" + 
					_clientDataLength + 
					") too large for input buffer remaining length (" +
					remainingLength + ")");
		}
		
		// Decide what to do with the payload.  If the payload length is beyond
		// a certain point, store the payload to a file.  Otherwise, keep it
		// in a buffer.  The segment may be part of a larger block (a bundle)
		// and we would like to keep the entire block in a single file.
		// However, we don't know the size of the entire block, and at this
		// point we don't even know other segments in the same block as this.
		// So all we can do at this point is store to a file.
		// XXX Think we can use SessionId to relate all segment payloads
		// for a single block.  Later.
		// Code doesn't account for fact that decodeState can hold file data.
		// In practice, inbound DataSegments never are stored in file.  Let's
		// just make sure of that
		if (decodeState._isInFile) {
			throw new JDtnException(
					"Incoming encoded DataSegment is stored in file," +
					" and we aren't set up to handle that situation");
		}
		if (_clientDataLength > LtpManagement.getInstance().getSegmentLengthFileThreshold()) {
			
			// Store payload to a file
			_clientDataInFile = true;
			_clientDataFile = Store.getInstance().createNewSegmentFile();
			ByteBuffer buffer = ByteBuffer.wrap(
					decodeState._memBuffer,
					decodeState._memOffset,
					_clientDataLength);
			BlobAndBundleDatabase.getInstance().copyByteBufferToFile(con, buffer, _clientDataFile);
			
		} else {
			// Save payload to a buffer
			_clientDataInFile = false;
			_clientData = new byte[_clientDataLength];
			System.arraycopy(decodeState._memBuffer, decodeState._memOffset, _clientData, 0, _clientDataLength);
		}
	}
	
	/**
	 * Encode the payload into the given buffer
	 * @param encodeState Buffer to encode into
	 * @throws InterruptedException 
	 * @throws LtpException on errors
	 */
	@Override
	protected void encodeContents(java.sql.Connection con, EncodeState encodeState)
	throws JDtnException, InterruptedException {
		// Encode payload descriptive information
		_clientServiceId.encode(encodeState);
		Utils.sdnvEncodeLong(_clientDataOffset, encodeState);
		Utils.sdnvEncodeInt(_clientDataLength, encodeState);
		
		if (isCheckpoint()) {
			_checkpointSerialNumber.encode(encodeState);
			_reportSerialNumber.encode(encodeState);
		}
		
		long t1 = System.currentTimeMillis();
		if (_clientDataInFile) {
			// Payload in file; read from file and append to buffer
			encodeState.append(con, _clientDataFile, _clientDataOffset, _clientDataLength);
			
		} else {
			// Payload in buffer; append to output buffer
			encodeState.append(_clientData, 0, _clientDataLength);
		}
		long t2 = System.currentTimeMillis();
		_mSecsEncodingPayload = t2 - t1;
		
	}
	
	/**
	 * Estimates the header length of this DataSegment.  The estimate will be
	 * a few bytes >= the true header length.  The problem is that the length
	 * of client data has not been determined yet, so we can't estimate
	 * exactly.
	 * @return Estimate of header length
	 * @throws InterruptedException 
	 * @throws LtpException If Segment Type is incorrect or other logical errors
	 */
	public int estimateHeaderLength() throws JDtnException, InterruptedException {
		java.sql.Connection con = BlobAndBundleDatabase.getInstance().getInterface().createConnection();
		try {
			EncodeState encodeState = new EncodeState();
			// Encode Segment envelope
			super.encode(con, encodeState, true);
			// Encode payload descriptive information
			_clientServiceId.encode(encodeState);
			Utils.sdnvEncodeLong(_clientDataOffset, encodeState);
			// Encode maximum Client Data Length; which results in max sized SDNV
			// So we get safe estimate of header length
			Utils.sdnvEncodeInt(Integer.MAX_VALUE, encodeState);

			// For safe estimate, assume the Segment is a Checkpoint.  In our
			// implementation, we use Longs as CheckpointSerialNumbers and
			// ReportSerialNumbers.  So, use Long.MAX_VALUE for each.
			CheckpointSerialNumber sn1 = new CheckpointSerialNumber(Integer.MAX_VALUE);
			sn1.encode(encodeState);
			ReportSerialNumber sn2 = new ReportSerialNumber(Integer.MAX_VALUE);
			sn2.encode(encodeState);
			encodeState.close();

			try { con.rollback(); } catch (SQLException ignore) { }

			return (int)encodeState.getLength();
		}
		finally {
			try { con.close(); } catch (SQLException e) { _logger.warning(e.getMessage()); }
		}
	}
	
	/**
	 * Discard the data behind this DataSegment
	 */
	public void discardData(java.sql.Connection con) {
		if (isValid()) {
			setValid(false);
			if (isClientDataInFile()) {
				if (getClientDataFile().exists(con) && !getClientDataFile().delete(con)) {
					_logger.warning("Cannot delete Segment Storage: " +
							getClientDataFile());
				}
			}
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
		StringBuffer sb = new StringBuffer(indent + "DataSegment\n");
		sb.append(super.dump(indent + "  ", detailed));
		sb.append(_clientServiceId.dump(indent + "  ", detailed));
		if (isCheckpoint()) {
			sb.append(_checkpointSerialNumber.dump(indent + "  ", detailed));
			sb.append(_reportSerialNumber.dump(indent + "  ", detailed));
		}
		sb.append(indent + "  ClientDataLength=" + _clientDataLength + "\n");
		sb.append(indent + "  ClientDataOffset=" + _clientDataOffset + "\n");
		sb.append(indent + "  ClientDataInFile=" + _clientDataInFile + "\n");
		if (_clientDataInFile) {
			sb.append(indent + "  ClientDataFile=" + _clientDataFile.getAbsolutePath() + "\n");
		} else {
			if (detailed) {
				sb.append(indent + "  ClientData=\n");
				sb.append(Utils.dumpBytes(indent + "  ", _clientData, 0, _clientData.length));
			}
		}
		return sb.toString();
	}
	
	/** Identifies upper-layer service to which segment is to be delivered */
	public ServiceId getClientServiceId() {
		return _clientServiceId;
	}
	/** Identifies upper-layer service to which segment is to be delivered */
	public void setClientServiceId(ServiceId clientServiceId) {
		this._clientServiceId = clientServiceId;
	}
	/** Length of client service data, bytes */
	public int getClientDataLength() {
		return _clientDataLength;
	}
	/** Length of client service data, bytes */
	public void setClientDataLength(int clientDataLength) {
		this._clientDataLength = clientDataLength;
	}
	/** If clientDataInFile is false, then this is the data buffer */
	public byte[] getClientData() {
		return _clientData;
	}
	/** If clientDataInFile is false, then this is the data buffer */
	public void setClientData(byte[] clientData) {
		this._clientData = clientData;
	}
	/** If this DataSegment is a checkpoint, then this is a serial number
	 * uniquely identifying the checkpoint among all checkpoints issued by
	 * sender for this session.
	 */
	public CheckpointSerialNumber getCheckpointSerialNumber() {
		return _checkpointSerialNumber;
	}
	/** If this DataSegment is a checkpoint, then this is a serial number
	 * uniquely identifying the checkpoint among all checkpoints issued by
	 * sender for this session.
	 */
	public void setCheckpointSerialNumber(CheckpointSerialNumber checkpointSerialNumber) {
		this._checkpointSerialNumber = new CheckpointSerialNumber(checkpointSerialNumber);
	}
	/** If this DataSegment was trasmitted in response to a ReportSegment,
	 * then this is the report serial number value from the triggering
	 * ReportSegment
	 */
	public ReportSerialNumber getReportSerialNumber() {
		return _reportSerialNumber;
	}
	/** If this DataSegment was trasmitted in response to a ReportSegment,
	 * then this is the report serial number value from the triggering
	 * ReportSegment
	 */
	public void setReportSerialNumber(ReportSerialNumber reportSerialNumber) {
		this._reportSerialNumber = new ReportSerialNumber(reportSerialNumber);
	}
	/** Whether client service data is in a file (or in memory) */
	public boolean isClientDataInFile() {
		return _clientDataInFile;
	}
	/** Whether client service data is in a file (or in memory) */
	public void setClientDataInFile(boolean clientDataInFile) {
		this._clientDataInFile = clientDataInFile;
	}
	/** If clientDataInFile is true, then this is the file it is stored in */
	public MediaRepository.File getClientDataFile() {
		return _clientDataFile;
	}
	/** If clientDataInFile is true, then this is the file it is stored in */
	public void setClientDataFile(MediaRepository.File clientDataFile) {
		this._clientDataFile = clientDataFile;
	}
	public long getClientDataOffset() {
		return _clientDataOffset;
	}
	public void setClientDataOffset(long clientDataOffset) {
		this._clientDataOffset = clientDataOffset;
	}

	/** Block of which this DataSegment is a part */
	public Block getBlock() {
		return _block;
	}

	/** Block of which this DataSegment is a part */
	public void setBlock(Block block) {
		this._block = block;
	}

	/**
	 * For outbound DataSegment, True if a claim has been received claiming
	 * receipt of this segment.
	 * For inbound DataSegment, True if a ReportAck has been received which
	 * itself claimed receipt of this Segment.
	 */
	public boolean isAcked() {
		return _acked;
	}

	/**
	 * For outbound DataSegment, True if a claim has been received claiming
	 * receipt of this segment.
	 * For inbound DataSegment, True if a ReportAck has been received which
	 * itself claimed receipt of this Segment.
	 */
	public void setAcked(boolean acked) {
		this._acked = acked;
	}

	/**
	 * For an inbound DataSegment, determines if this DataSegment is a resend
	 * i.e., it is a checkpoint and it has a non-zero ReportSerialNumber.
	 * @return What I said
	 */
	public boolean isResend() {
		
		if (isCheckpoint() && !getReportSerialNumber().isZero()) {
			return true;
		}
		return false;
	}

	/** True if this DataSegment has been sent at least once */
	public boolean isSent() {
		return _sent;
	}

	/** True if this DataSegment has been sent at least once */
	public void setSent(boolean sent) {
		this._sent = sent;
	}

	/**
	 * Checkpoint timer; Started when send a DataSegment.
	 * Used to timeout on RS (Report Segment) coming
	 * back from that DataSegment.  Managed by LtpOutbound.
	 */
	public TimerTask getCheckpointTimerTask() {
		return _checkpointTimerTask;
	}

	/**
	 * Checkpoint timer; Started when send a DataSegment.
	 * Used to timeout on RS (Report Segment) coming
	 * back from that DataSegment.  Managed by LtpOutbound.
	 */
	public void setCheckpointTimerTask(TimerTask checkpointTimerTask) {
		this._checkpointTimerTask = checkpointTimerTask;
	}

	/** mSecs spent encoding payload */
	public long getmSecsEncodingPayload() {
		return _mSecsEncodingPayload;
	}

	/** mSecs spent encoding payload */
	public void setmSecsEncodingPayload(long mSecsEncodingPayload) {
		this._mSecsEncodingPayload = mSecsEncodingPayload;
	}

}
