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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Store;
import com.cisco.qte.jdtn.general.Utils;

/**
 * An Inbound LTP Block - A Block received or in the process of being Received
 */
public class InboundBlock extends Block {
	private static final Logger _logger =
		Logger.getLogger(InboundBlock.class.getCanonicalName());
	
	/**
	 * LTP Receiver States -- state of an Inbound Block
	 * Corresponding to RFC5326 8.2 Receiver State Machine.
	 */
	public enum LtpReceiverState {
		/** Idle, closed, not active, cancelled */
		CLOSED,
		/** Receiving data segments, any color */
		DS_REC,
		/** Receiving Red data segments */
		RCV_RP,
		/** Receiving Green data segments */
		RCV_GP,
		/** Received EOB, awaiting full ack */
		WAIT_RP_REC,
		/** Send Cancel Request, awaiting Cancel Ack */
		CR_SENT
	}
	
	// State of the LTP Receiver Machine ala RFC5326 8.2
	private LtpReceiverState _ltpReceiverState = LtpReceiverState.CLOSED;
	
	// List of ReportSegments issued on this Block; We keep these around since
	// we need their information when we receive a ReportAckSegment so we
	// know what was acked.
	private ArrayList<ReportSegment> _reportSegmentsList =
		new ArrayList<ReportSegment>(100);
	
	// Map ReportSerialNumber to ReportSegment for faster lookup
	private HashMap<ReportSerialNumber, ReportSegment> _reportSegmentsMap =
		new HashMap<ReportSerialNumber, ReportSegment>();
		
	// Serial number reported in a ReportSegment for an InboundBlock.  Set to
	// Random when first Segment of Block received.  Incremented by 1 for each
	// Resend of a ReportSegment.
	private ReportSerialNumber _reportSerialNumber;
	
	// 
	private int numberReceptionProblems = 0;
	
	/**
	 * Constructor for an inbound Block whose first Segment is the given
	 * DataSegment.
	 * @param dataSegment Given DataSegment.  Note that this is not necessarily
	 * the DataSegment with offset 0.  It is merely the first DataSegment
	 * received.
	 * @throws JDtnException On various errors
	 */
	public InboundBlock(DataSegment dataSegment) throws JDtnException {
		super(dataSegment);
		_reportSerialNumber = new ReportSerialNumber();
	}

	/**
	 * Determine if this Inbound Block has been completely received; i.e.,
	 * <ul>
	 *   <li> We have received EOB.
	 *   <li> All Red Segments are contiguous with each other
	 *   <li> All Red Segments have been "Acked", i.e., we have sent report
	 *        segments covering all Red Segments and such report segments
	 *        have received corresponding Report Acks.
	 * </ul>
	 * @return True if Inbound Block completely received
	 */
	public boolean isInboundBlockComplete() {
		long expectedOffset = 0;
		boolean eobSeen = false;
		
		if (!_reportSegmentsList.isEmpty()) {
			// Not all outstanding ReportSegments have been acked
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Not Complete: Outstanding Report Segments");
			}
			return false;
		}
		
		for (DataSegment dataSegment : this) {
			if (dataSegment.isEndOfBlock()) {
				eobSeen = true;
			}
			if (dataSegment.isRedData()) {
				if (!dataSegment.isAcked()) {
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("Not Complete: Report for this segment not acked");
					}
					return false;
				}
				if (dataSegment.getClientDataOffset() != expectedOffset) {
					if (GeneralManagement.isDebugLogging()) {
						_logger.fine("Not Complete: missing data");
					}
					return false;
				}
			}
			expectedOffset += dataSegment.getClientDataLength();
		}
		if (eobSeen) {
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Complete");
			}
			return true;
		} else {
			if (GeneralManagement.isDebugLogging()) {
				_logger.fine("Not Complete: EOB not seen");
			}
			return false;
		}
	}
	
	/**
	 * Determine if all Red Segments have been received.  I.e.:
	 * <ul>
	 *   <li> We have received EORP.
	 *   <li> All Red Segments are contiguous with each other
	 * </ul>
	 * @return True if Inbound Block all red segments received
	 */
	public boolean isAllRedDataReceived() {
		long expectedOffset = 0;
		boolean eorpSeen = false;
		
		for (DataSegment dataSegment : this) {
			if (dataSegment.isEndOfRedPart()) {
				eorpSeen = true;
			}
			if (dataSegment.isRedData()) {
				if (dataSegment.getClientDataOffset() != expectedOffset) {
					return false;
				}
			}
			expectedOffset += dataSegment.getClientDataLength();
		}
		if (eorpSeen) {
			return true;
		} else {
			return false;
		}		
	}
	
	/**
	 * Determine if all Green Segments have been received.  I.e.:
	 * <ul>
	 *   <li>We have received EOB
	 *   <li>All Green Segments are contiguous with each other
	 * </ul>
	 * @return True if all green segments received for this block
	 */
	public boolean isAllGreenDataReceived() {
		long expectedOffset = 0;
		boolean eobSeen = false;
		
		for (DataSegment dataSegment : this) {
			if (dataSegment.isEndOfBlock()) {
				eobSeen = true;
			}
			if (dataSegment.isGreenData()) {
				if (dataSegment.getClientDataOffset() != expectedOffset) {
					return false;
				}
			}
			expectedOffset += dataSegment.getClientDataLength();
		}
		if (eobSeen) {
			return true;
		} else {
			return false;
		}		
	}
	
	/**
	 * Complete this inbound Block by getting all of the Block's data in one
	 * place; either in a buffer if it's small enough, or in a File if its
	 * too large.
	 * @throws JDtnException on errors
	 */
	public void inboundBlockComplete() throws JDtnException {
		// If the Block data length is past a Threshold, spill all Segment
		//  data to a file.
		if (_dataLength > LtpManagement.getInstance().getBlockLengthFileThreshold()) {
			// Spill all DataSegments to this block's file
			_dataInFile = true;
			_dataFile = Store.getInstance().createNewBlockFile();
			spillSegmentDataToBlockFile();
			
		} else {
			// Else gather all Segment data to a buffer
			_dataInFile = false;
			_dataBuffer = new byte[(int)_dataLength];
			for (DataSegment segment : this) {
				gatherSegmentDataToBuffer(segment, _dataBuffer);
				segment.discardData();
			}
		}
	}
	
	/**
	 * Gather the data for the given DataSegment to the given buffer at the
	 * DataSegment's clienDataOffset, for length given by DataSegment's
	 * clientDataLength.
	 * @param dataSegment Given DataSegment
	 * @param buffer Buffer to gather into
	 * @throws LtpException On I/O errors if involves file operations
	 */
	private void gatherSegmentDataToBuffer(DataSegment dataSegment, byte[] buffer) 
	throws JDtnException {
		if (dataSegment.isClientDataInFile()) {
			// Gather from segment file to buffer[]
			FileInputStream fis = null;
			try {
				fis = new FileInputStream(dataSegment.getClientDataFile());
				int nRead = fis.read(buffer, (int)dataSegment.getClientDataOffset(), dataSegment.getClientDataLength());
				if (nRead != dataSegment.getClientDataLength()) {
					throw new JDtnException("nRead " + nRead + " != amount requested " + dataSegment.getClientDataLength());
				}
			} catch (IOException e) {
				throw new LtpException(e);
			} finally {
				try {
					if (fis != null) {
						fis.close();
					}
				} catch (IOException e) {
					// Nothing
				}
				fis = null;
				dataSegment.discardData();
			}
				
			
		} else {
			// Gather from segment buffer to buffer[]
			Utils.copyBytes(
					dataSegment.getClientData(), 
					0,
					buffer, 
					(int)dataSegment.getClientDataOffset(),
					dataSegment.getClientDataLength());
		}
	}
	
	/**
	 * Spill data from all DataSegments to this block's file.
	 * @throws LtpException on errors
	 */
	private void spillSegmentDataToBlockFile()
	throws LtpException {
		FileInputStream fis = null;
		FileOutputStream fos = null;
		
		if (_dataFile == null) {
			try {
				_dataFile = Store.getInstance().createNewBlockFile();
			} catch (JDtnException e) {
				throw new LtpException("spillSegmentDataToBlockFile()", e);
			}
		}

		try {
			fos = new FileOutputStream(_dataFile);
		} catch (FileNotFoundException e) {
			throw new LtpException(e);
		}
		
		try {
			byte[] buffer = new byte[4096];
			for (DataSegment dataSegment : this) {
				if (dataSegment.isClientDataInFile()) {
					// Spill segment file to block file
					// Make sure the file exists
					if (!dataSegment.getClientDataFile().exists()) {
						_logger.severe("InboundBlock contains Segment in file " +
								dataSegment.getClientDataFile().getAbsolutePath() +
								" but that file doesn't exist");
						_logger.severe(dataSegment.dump("", true));
						throw new IOException("File " +
								dataSegment.getClientDataFile().getAbsolutePath() +
								" does not exist");
					}
					fis = new FileInputStream(dataSegment.getClientDataFile());
					long remaining = dataSegment.getClientDataLength();
					while (remaining > 0) {
						int nRead = fis.read(buffer);
						if (nRead <= 0) {
							throw new LtpException("Read count returned " + nRead);
						}
						remaining -= nRead;
						fos.write(buffer, 0, nRead);
						dataSegment.discardData();
					}
					fis.close();
					fis = null;
				} else {
					// Spill segment buffer to block file
					fos.write(dataSegment.getClientData(), 0, dataSegment.getClientDataLength());
				}
			}
		} catch (IOException e) {
			throw new LtpException("Spilling block data", e);
		} finally {
			try {
				fos.close();
			} catch (IOException e) {
				// Nothing
			}
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
					// Nothing
				}
			}
		}
	}
	
	/**
	 * Called when this Block is closed, giving the Block an opportunity
	 * to clean up.  Clean up consists of:
	 * <ul>
	 *   <li> Remove outstanding report segments and kill their Checkpoint timers.
	 * </ul>
	 */
	@Override
	public void closeBlock() {
		super.closeBlock();
		removeOutstandingReportSegments();
	}

	/**
	 * Remove outstanding Report Segments and kill their RS Timers
	 */
	private void removeOutstandingReportSegments() {
		while (!_reportSegmentsList.isEmpty()) {
			ReportSegment reportSegment = _reportSegmentsList.remove(0);
			_reportSegmentsMap.remove(reportSegment.getReportSerialNumber());
			if (reportSegment.getRsTimerTask() != null) {
				reportSegment.getRsTimerTask().cancel();
				reportSegment.setRsTimerTask(null);
			}
		}		
	}
	
	/**
	 * Dump the state of this object
	 * @param indent Amount of indentation
	 * @param detailed True if want verbose details
	 * @return String containing dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "InboundBlock\n");
		sb.append(super.dump(indent + "  ", detailed));
		sb.append(indent + "  Ltp Receiver State=" + _ltpReceiverState + "\n");
		sb.append(indent + "  Block ReportSerialNumber\n");
		sb.append(getReportSerialNumber().dump(indent + "    ", detailed));
		if (detailed) {
			sb.append(indent + "  Outstanding Report Segments\n");
			for (ReportSegment reportSegment : _reportSegmentsList) {
				sb.append(reportSegment.dump(indent + "    ", detailed));
			}
		}
		return sb.toString();
	}
	
	/**
	 * State of the LTP Receiver Machine ala RFC5326 8.2
	 */
	public LtpReceiverState getLtpReceiverState() {
		return _ltpReceiverState;
	}

	/**
	 * State of the LTP Receiver Machine ala RFC5326 8.2
	 */
	public void setLtpReceiverState(LtpReceiverState ltpReceiverState) {
		this._ltpReceiverState = ltpReceiverState;
	}

	/**
	 * Get an Iterator over the list of outstanding ReportSegments issued by
	 * this InboundBlock.
	 * @return  What I said
	 */
	public Iterable<ReportSegment> iterateOutstandingReportSegments() {
		return _reportSegmentsList;
	}
	
	/**
	 * Get the outstanding Report Segment referenced by given ReportSerialNumber.
	 * @param reportSerialNumber Given ReportSerialNumber.
	 * @return What I said
	 */
	public ReportSegment getOutstandingReportSegment(ReportSerialNumber reportSerialNumber) {
		return _reportSegmentsMap.get(reportSerialNumber);
	}
	
	/**
	 * Add given ReportSegment to list of ReportSegments issued for this
	 * InboundBlock.  We need to keep them around because when a ReportAck
	 * comes in, we need to know what is being acked.
	 * @param reportSegment
	 */
	public void addReportSegment(ReportSegment reportSegment) {
		ReportSegment existingReportSegment = _reportSegmentsMap.get(
				reportSegment.getReportSerialNumber());
		if (existingReportSegment != null) {
			throw new IllegalArgumentException(
					"ReportSerialNumber " +
					reportSegment.getReportSerialNumber() +
					" is already in the block's outstanding list");
		}
		_reportSegmentsList.add(reportSegment);
		_reportSegmentsMap.put(reportSegment.getReportSerialNumber(), reportSegment);
	}
	
	/**
	 * Determine if given newly arrived DataSegment is miscolored.
	 * 6.21.  Handle Miscolored Segment
   	 * This procedure is triggered by the arrival of either (a) a red-part
   	 * data segment whose block offset begins at an offset higher than the
   	 * block offset of any green-part data segment previously received for
   	 * the same session or (b) a green-part data segment whose block offset
   	 * is lower than the block offset of any red-part data segment
   	 * previously received for the same session.  The arrival of a segment
   	 * matching either of the above checks is a violation of the protocol
   	 * requirement of having all red-part data as the block prefix and all
   	 * green-part data as the block suffix.
	 * @param block Block of which given Segment is a part
	 * @param dataSegment Given newly arrived DataSegment
	 * @return True if miscolored according to above definition
	 */
	public boolean isMiscolored(InboundBlock block, DataSegment dataSegment) {
		if (dataSegment.isRedData()) {
			// Given Segment is Red
			for (DataSegment otherSegment : block) {
				if (otherSegment.isGreenData()) {
					if (dataSegment.getClientDataOffset() > otherSegment.getClientDataOffset()) {
						// Found a Green Segment whose offset <= given Red Segment
						return true;
					}
				}						
			}
			
		} else {
			// Given Segment is Green
			for (DataSegment otherSegment : block) {
				if (otherSegment.isRedData()) {
					if (dataSegment.getClientDataOffset() < otherSegment.getClientDataOffset()) {
						// Found a Red Segment whose offset >= given Green Segment
						return true;
					}
				}
			}
		}
		
		return false;
	}
	
	/**
	 * Remove given Report Segment from lisxt of ReportSegments issued for this
	 * InboundBlock.
	 * @param reportSegment
	 */
	public void removeReportSegment(ReportSegment reportSegment) {
		_reportSegmentsMap.remove(reportSegment.getReportSerialNumber());
		_reportSegmentsList.remove(reportSegment);
	}

	/**
	 * Serial number reported in a ReportSegment for an InboundBlock.  Set to
	 * Random when first Segment of Block received.  Incremented by 1 for each
	 * Resend of a ReportSegment.  For an OutboundBlock, xxx
	 */
	public ReportSerialNumber getReportSerialNumber() {
		return _reportSerialNumber;
	}
	
	/**
	 * Serial number reported in a ReportSegment for an InboundBlock.  Set to
	 * Random when first Segment of Block received.  Incremented by 1 for each
	 * Resend of a ReportSegment.
	 */
	public ReportSerialNumber incrementReportSerialNumber() {
		_reportSerialNumber.incrementSerialNumber();
		return _reportSerialNumber;
	}

	/**
	 * Number of problems encountered in receipt of this inbound block
	 */
	public int getNumberReceptionProblems() {
		return numberReceptionProblems;
	}

	/**
	 * Increment Number of problems encountered in receipt of this inbound block
	 * @return incremented value
	 */
	public int incrementNumberReceptionProblems() {
		return ++numberReceptionProblems;
	}
	
	/**
	 * Determine if this session has encountered too many reception problems
	 * @return True if number of reception problems > system limit
	 */
	public boolean isTooManyReceptionProblems() {
		return numberReceptionProblems >= LtpManagement.getInstance().getSessionReceptionProblemsLimit();
	}
	
}
