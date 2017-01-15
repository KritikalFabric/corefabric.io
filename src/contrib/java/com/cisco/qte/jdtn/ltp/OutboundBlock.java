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

import com.cisco.qte.jdtn.apps.MediaRepository;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.ltp.BlockOptions.CheckpointOption;
import com.cisco.qte.jdtn.ltp.Segment.SegmentType;

/**
 * An Outbound LTP Block - A Block sent or in the process of being Sent
 */
public class OutboundBlock extends Block {

	/**
	 * LTP Sender States -- state of an outgoing Block
	 * Corresponding to RFC5326 8.1 Sender State Machine, except that we don't
	 * implement CHK_RPT nor RP_RXMT, which aren't really states.
	 */
	public enum LtpSenderState {
		/** Idle, closed, not active, cancelled */
		CLOSED,
		/** Transmitting Green Data Segments in Green only Block */
		FG_XMIT,
		/** Transmitting Red Data Segments */
		RP_XMIT,
		/** Transmitting Green Data Segments in mixed color Block */
		GP_XMIT,
		/** Primary transmission complete, Awaiting Report Segment */
		WAIT_RP_ACK,
		/** Cancel sent, awaiting Cancel Ack */
		CS_SENT,
		/** Report Ack sent */
		CHK_RPT,
		/** Resending missing Red Data Segments */
		RP_RXMT
	}
	
	private LtpSenderState _ltpSenderState = LtpSenderState.CLOSED;
	
	 // If checkpointing is requested, this is the CheckpointSerialNumber of
	 // the first Segment
	private CheckpointSerialNumber _firstCheckpointSerialNumber;
	
	 // If checkpoint is requested, this is the CheckpointSerialNumber of
	 // subsequent Segments, constructed from predecessor by incrementing
	 // by 1.
	private CheckpointSerialNumber _subsequentSerialNumber;
	
	/**
	 * Constructor for an outbound Block with memory-based data
	 * @param neighbor Neighbor to which the Block should be sent
	 * @param link Link on which the Block should be sent
	 * @param buffer Buffer containing Data to be sent
	 * @param length Length of data to be sent
	 * @param options Options for transmission; if null, default BlockOptions
	 * will be used: all bytes red, checkpoint requested, no header or trailer
	 * options.
	 * @throws JDtnException  on immediately detected errors
	 * @throws InterruptedException 
	 */
	public OutboundBlock(LtpNeighbor neighbor, LtpLink link, byte[] buffer,
			long length, BlockOptions options) throws JDtnException, InterruptedException {
		super(neighbor, link, buffer, length, options);
		segmentData();
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
	 * @throws JDtnException on immediately detected errors
	 * @throws InterruptedException 
	 */
	public OutboundBlock(LtpNeighbor neighbor, LtpLink link, MediaRepository.File file, long length,
						 BlockOptions options) throws JDtnException, InterruptedException {
		super(neighbor, link, file, length, options);
		segmentData();
	}

	/**
	 * Segment the data; Construct a series of DataSegments which are small
	 * enough to send on the designated Link.
	 * @throws JDtnException on encoding errors
	 * @throws InterruptedException 
	 */
	private void segmentData() throws JDtnException, InterruptedException {

		long runningDataOffset = 0L;
		long redLength = _blockOptions.redLength;
		long greenLength = _dataLength - redLength;
		int runningBufferOffset = 0;
		int segmentLength;
		
		// Construct first Segment; Can be red or green depending
		segmentLength = constructFirstSegment(redLength, greenLength);
		runningDataOffset += segmentLength;
		runningBufferOffset += segmentLength;			
	
		if (redLength > 0) {
			redLength -= segmentLength;
		} else {
			greenLength -= segmentLength;
		}
		
		// Construct remaining red segments
		while (redLength > 0) {
			segmentLength = constructRedSegment(
					redLength, greenLength, runningDataOffset, 
					runningBufferOffset);
			runningDataOffset += segmentLength;
			runningBufferOffset += segmentLength;	
			redLength -= segmentLength;
		}
		
		// Now do remaining green segments
		while (greenLength > 0) {
			segmentLength = constructGreenSegment(greenLength, runningDataOffset, runningBufferOffset);
			runningDataOffset += segmentLength;
			runningBufferOffset += segmentLength;	
			greenLength -= segmentLength;
		}
		
	}
	
	/**
	 * Construct the first segment of the block
	 * @param redLength Amount of Red data in block
	 * @param greenLength Amount of Green data in block
	 * @return Length in bytes of data in first block
	 * @throws JDtnException on encoding errors
	 * @throws InterruptedException 
	 */
	private int constructFirstSegment(
			long redLength, 
			long greenLength) throws JDtnException, InterruptedException {
		
		// Start construction of the DataSegment.  Can be Red or Green.
		// Assume not EOB for now.  Will correct that a little later.
		DataSegment segment = null;
		if (redLength > 0) {
			segment = new DataSegment(SegmentType.RED_NOTCP_NOT_EORP_NOT_EOB);
		} else {
			segment = new DataSegment(SegmentType.GREEN_NOT_EOB);
		}
		segment.setSessionID(getSessionId());
		segment.setClientServiceId(_blockOptions.serviceId);
		segment.setClientDataOffset(0L);
		segment.setClientDataInFile(_dataInFile);
		segment.setHeaderExtensions(_blockOptions.headerExtensions);
		segment.setTrailerExtensions(_blockOptions.trailerExtensions);
		segment.setNeighbor(_neighbor);
		segment.setLink(_link);
		
		switch (_blockOptions.checkpointOption) {
		case CHECKPOINT_ALL:
			// Fall thru
		case CHECKPOINT_LAST_ONLY:
			// Green Segments are never checkpoints.
			// Red Segments are checkpoints if CHECKPOINT_ALL requested, or if
			// CHECKPOINT_LAST_ONLY requested and this segment is END OF RED PART.
			// I can't determine yet whether it is END OF RED PART because I don't
			// know the segment length, and can't until I fill out the DataSegment
			// header and know how big the header is.  So I'm going to be
			// conservative, assume that it is END OF RED PART and put in a
			// CheckpointSerialNumber and ReportSerialNumber.  Later on, I'll
			// fix that assumption if it proves false, at the expense of a
			// slightly shorter segment than optimum.
			if (segment.isRedData()) {
				segment.setSegmentType(SegmentType.RED_CP_EORP_EOB);
				setNextCheckpointSerialNumber(segment);
				segment.setReportSerialNumber(new ReportSerialNumber(0));
			}
			break;
			
		default:
			// No action necessary
		}
		
		// Determine the length of data in the segment.
		int headerLength = segment.estimateHeaderLength();
		int availableSpace = _link.getMaxFrameSize() - headerLength;
		int segmentLength;
		
		if (redLength > 0) {
			if (redLength <= availableSpace) {				
				segmentLength = (int)redLength;
			} else {
				segmentLength = availableSpace;
			}
		} else {
			if (greenLength <= availableSpace) {
				segmentLength = (int)greenLength;
			} else {
				segmentLength = availableSpace;
			}
		}			
		segment.setClientDataLength(segmentLength);
		
		// If data not in file, then copy data to a buffer for the segment.
		if (!_dataInFile) {
			byte[] segBuffer = new byte[segmentLength];
			System.arraycopy(_dataBuffer, 0, segBuffer, 0, segmentLength);
			segment.setClientData(segBuffer);
		} else {
			segment.setClientDataFile(_dataFile);
		}
		
		// Figure out what the final SegmentType should be.  This will end up
		// fixing the assumption about EORP made above.
		if (segment.isRedData()) {
			// Segment is Red
			redLength -= segmentLength;
			if (redLength <= 0) {
				// No more RedData to add; EORP
				if (greenLength <= 0) {
					// No GreenData to add; EORP + EOB
					// EORP + EOB is always a Checkpoint
					segment.setSegmentType(SegmentType.RED_CP_EORP_EOB);
				} else {
					// GreenData to add; EORP
					segment.setSegmentType(SegmentType.RED_CP_EORP_NOT_EOB);
				}
			} else {
				// More RedData to add; not EORP
				if (_blockOptions.checkpointOption == CheckpointOption.CHECKPOINT_ALL) {
					// Is Checkoint: CP, not EORP not EOB
					segment.setSegmentType(SegmentType.RED_CP_NOT_EORP_NOT_EOB);
				} else {
					// No CP, Not EORP, not EOB
					segment.setSegmentType(SegmentType.RED_NOTCP_NOT_EORP_NOT_EOB);
				}
			}
		} else {
			// Segment is Green
			greenLength -= segmentLength;
			if (greenLength <= 0) {
				// No more GreenData to add; EOB
				segment.setSegmentType(SegmentType.GREEN_EOB);
			} else {
				// More GreenData to add; Not EOB
				segment.setSegmentType(SegmentType.GREEN_NOT_EOB);
			}
		}
		
		// Add segment to Block's segment list
		appendSegment(segment);

		return segmentLength;
	}
	
	/**
	 * Construct a Red Segment
	 * @param redLength Amount of remaining Red data
	 * @param greenLength Amount of remaining Green data
	 * @param runningDataOffset Running offset into block data
	 * @param runningBufferOffset Running offset into user's buffer
	 * @return Length in bytes of data in new Red Segment
	 * @throws JDtnException on immediately detected errors
	 * @throws InterruptedException 
	 */
	private int constructRedSegment(
			long redLength,
			long greenLength,
			long runningDataOffset,
			int runningBufferOffset) throws JDtnException, InterruptedException {

		// Start constructing the Segment.  It is always Red.  Assume not EORP nor EOB.
		DataSegment segment = new DataSegment(SegmentType.RED_NOTCP_NOT_EORP_NOT_EOB);
		segment.setSessionID(getSessionId());
		segment.setClientServiceId(_blockOptions.serviceId);
		segment.setClientDataOffset(runningDataOffset);
		segment.setClientDataInFile(_dataInFile);
		segment.setHeaderExtensions(_blockOptions.headerExtensions);
		segment.setTrailerExtensions(_blockOptions.trailerExtensions);
		segment.setNeighbor(_neighbor);
		segment.setLink(_link);
		
		switch (_blockOptions.checkpointOption) {
		case CHECKPOINT_ALL:
			// Fall thru
		case CHECKPOINT_LAST_ONLY:
			// Red Segments are checkpoints if CHECKPOINT_ALL requested, or if
			// CHECKPOINT_LAST_ONLY requested and this segment is END OF RED PART.
			// I can't determine yet whether it is END OF RED PART because I don't
			// know the segment length, and can't until I fill out the DataSegment
			// header and know how big the header is.  So I'm going to be
			// conservative, assume that it is END OF RED PART and put in a
			// CheckpointSerialNumber and ReportSerialNumber.  Later on, I'll
			// fix that assumption if it proves false, at the expense of a
			// slightly shorter segment than optimum.
			segment.setSegmentType(SegmentType.RED_CP_EORP_EOB);
			setNextCheckpointSerialNumber(segment);
			segment.setReportSerialNumber(new ReportSerialNumber(0));
			break;
			
		default:
			// No action necessary
		}
		
		// Determine the length of data in the segment.
		int headerLength = segment.estimateHeaderLength();
		int availableSpace = _link.getMaxFrameSize() - headerLength;
		int segmentLength;
		
		if (redLength <= availableSpace) {				
			segmentLength = (int)redLength;
		} else {
			segmentLength = availableSpace;
		}			
		segment.setClientDataLength(segmentLength);
		
		// If data not in file, then copy data to a buffer for the segment.
		if (!_dataInFile) {
			byte[] segBuffer = new byte[segmentLength];
			System.arraycopy(_dataBuffer, runningBufferOffset, segBuffer, 0, segmentLength);
			segment.setClientData(segBuffer);
		} else {
			segment.setClientDataFile(_dataFile);
		}
		
		// Determine final value of SegmentType.  This fixes the assumption
		// made earlier about EOB
		redLength -= segmentLength;
		if (redLength <= 0) {
			// No more Red Data to add; EORP + CP
			if (greenLength <= 0) {
				// No Green Data to add; EORP + CP + EOB
				segment.setSegmentType(SegmentType.RED_CP_EORP_EOB);
			} else {
				// Green Data to add; EORP + CP + not EOB
				segment.setSegmentType(SegmentType.RED_CP_EORP_NOT_EOB);
			}
		} else {
			// More Red Data to add; Not EORP + Not EOB
			if (_blockOptions.checkpointOption == CheckpointOption.CHECKPOINT_ALL) {
				// CP + Not EORP + Not EOB
				segment.setSegmentType(SegmentType.RED_CP_NOT_EORP_NOT_EOB);
			} else {
				// Not CP + Not EORP + Not EOB
				segment.setSegmentType(SegmentType.RED_NOTCP_NOT_EORP_NOT_EOB);
			}
		}
		
		// Add segment to Block's segment list
		appendSegment(segment);

		return segmentLength;
	}

	/**
	 * Construct a Green segment
	 * @param greenLength Amount of remaining Green data
	 * @param runningDataOffset Running offset into block data
	 * @param runningBufferOffset Running offset into user's buffer
	 * @return Length in bytes of data in Green segment
	 * @throws JDtnException on immediately detected errors
	 * @throws InterruptedException 
	 */
	private int constructGreenSegment(
			long greenLength,
			long runningDataOffset,
			int runningBufferOffset) throws JDtnException, InterruptedException {
		
		DataSegment segment = new DataSegment(SegmentType.GREEN_NOT_EOB);
		segment.setSessionID(getSessionId());

		segment.setClientServiceId(_blockOptions.serviceId);
		segment.setClientDataOffset(runningDataOffset);
		segment.setClientDataInFile(_dataInFile);
		segment.setHeaderExtensions(_blockOptions.headerExtensions);
		segment.setTrailerExtensions(_blockOptions.trailerExtensions);
		segment.setNeighbor(_neighbor);
		segment.setLink(_link);
		
		// Determine the length of data in the segment.
		int headerLength = segment.estimateHeaderLength();
		int availableSpace = _link.getMaxFrameSize() - headerLength;
		int segmentLength;
		
		if (greenLength <= availableSpace) {
			segmentLength = (int)greenLength;
		} else {
			segmentLength = availableSpace;
		}
			
		segment.setClientDataLength(segmentLength);
		
		// If data not in file, then copy data to a buffer for the segment.
		if (!_dataInFile) {
			byte[] segBuffer = new byte[segmentLength];
			System.arraycopy(_dataBuffer, runningBufferOffset, segBuffer, 0, segmentLength);
			segment.setClientData(segBuffer);
		} else {
			segment.setClientDataFile(_dataFile);
		}
		
		// Determine final value of SegmentType
		greenLength -= segmentLength;
		if (greenLength <= 0) {
			// No more Green data; EOB
			segment.setSegmentType(SegmentType.GREEN_EOB);
		} else {
			// More Green data to add; not EOB
			segment.setSegmentType(SegmentType.GREEN_NOT_EOB);
		}
		
		// Add segment to Block's segment list
		appendSegment(segment);

		return segmentLength;
	}
	
	/**
	 * Determine if this Outbound Block has been completely acknowledged; i.e.,
	 * All Red Segments have been claimed received.
	 * All Green Segments have at least been sent.
	 * @return True if Outbound Block has been completely acknowledged.
	 */
	public boolean isOutboundBlockComplete() {
		for (DataSegment dataSegment : this) {
			if (dataSegment.isRedData()) {
				if (!dataSegment.isAcked()) {
					return false;
				}
			} else {
				if (!dataSegment.isSent()) {
					return false;
				}
			}
		}
		return true;
	}
	
	/**
	 * Called when this Block is closed, giving the Block an opportunity
	 * to clean up.
	 */
	@Override
	public void closeBlock(java.sql.Connection con) {
		for (DataSegment dataSegment : this) {
			if (dataSegment.getCheckpointTimerTask() != null) {
				LtpManagement.getInstance().getLtpStats().nCkPtTimerStops++;
				dataSegment.getCheckpointTimerTask().cancel();
				dataSegment.setCheckpointTimerTask(null);
			}
		}
		discardBlockData(con);
		super.closeBlock(con);
	}
	
	/**
	 * Assign a CheckpointSerialNumber to the given DataSegment based on
	 * CheckpointSerialNumbers assigned in the past.
	 * @param segment The DataSegment to receive the CheckpointSerialNumber
	 */
	public void setNextCheckpointSerialNumber(DataSegment segment) {
		if (_firstCheckpointSerialNumber == null) {
			_firstCheckpointSerialNumber = new CheckpointSerialNumber();
			_subsequentSerialNumber = new CheckpointSerialNumber(_firstCheckpointSerialNumber);
			_subsequentSerialNumber.incrementSerialNumber();
			segment.setCheckpointSerialNumber(_firstCheckpointSerialNumber);
		} else {
			segment.setCheckpointSerialNumber(_subsequentSerialNumber);
			_subsequentSerialNumber = new CheckpointSerialNumber(_subsequentSerialNumber);
			_subsequentSerialNumber.incrementSerialNumber();
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
		StringBuffer sb = new StringBuffer(indent + "OutboundBlock\n");
		sb.append(super.dump(indent + "  ", detailed));
		sb.append(indent + "  Ltp Sender State=" + _ltpSenderState + "\n");
		return sb.toString();
	}
	
	/**
	 * State of the LTP Sender Machine ala RFC5326 8.1
	 */
	public LtpSenderState getLtpSenderState() {
		return _ltpSenderState;
	}
	
	/**
	 * State of the LTP Sender Machine ala RFC5326 8.1
	 */
	public void setLtpSenderState(LtpSenderState state) {
		_ltpSenderState = state;
	}

}
