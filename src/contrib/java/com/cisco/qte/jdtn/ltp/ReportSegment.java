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
import java.util.List;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Utils;

/**
 * Report Segment - sent by receiver of a block to provide info to sender
 * about what segments it has received.
 */
public class ReportSegment extends Segment {
	private static final Logger _logger =
		Logger.getLogger(ReportSegment.class.getCanonicalName());
	
	/**
	 * Uniquely ids report among all reports issued by receiver in session 
	 */
	protected ReportSerialNumber _reportSerialNumber;
	/** 
	 * Chkpt serial number of the checkpoint that requested the report.
	 * must be zero if the report is async; i.e., not issued in response to
	 * a checkpoint.
	 */
	protected CheckpointSerialNumber _checkpointSerialNumber;
	/** 
	 * Size of the block prefix to which the claims pertain. 
	 */
	protected long _upperBound;
	/**
	 * size of (interior) block prefix to which claims don't pertain
	 */
	protected long _lowerBound;
	/**
	 * Number of reception claims in this report
	 */
	protected int _receptionClaimCount;
	/**
	 * Set of reception claims in this report
	 */
	protected ReceptionClaim[] _receptionClaims;
	/**
	 * RS (ReportSegment) Timer; Started when a RS (Report Segment) is sent.
	 * Used to timeout on RA (Report Ack) coming back from that Report Segment.
	 * Managed by LtpInbound.
	 */
	protected TimerTask _rsTimerTask;
	
	/**
	 * Constructor: Used when decoding an inbound ReportSegment.
	 * @param segmentType Segment Type
	 */
	public ReportSegment(SegmentType segmentType) {
		super(segmentType);
	}
	
	/**
	 * Construct a "Primary" ReportSegment for the given Block.  The ReportSegment will
	 * report on each and every Segment in the given Block up to and including
	 * the scope of the given DataSegment.  We will report a LowerBound of 0
	 * regardless of what has been received.
	 * NOTE: this constructor can build a ReportSegment which is too large for
	 * the MTU of the Link on which the given Block originated.  Used by
	 * generateReceptionReport() to generate a set of ReportSegments, which
	 * together cover the desired range and each of which don't exceed the
	 * Link MTU.
	 * @param givenDataSegment Given DataSegment
	 * @param block Given Block
	 * @param checkpointSerialNumber The CheckpointSerialNumber to use in the
	 * ReportSegment.
	 * @param reportSerialNumber The ReportSerial Number to use in the
	 * ReportSegment.
	 * @throws LtpException If given Block contains no Segments
	 */
	private ReportSegment(
			DataSegment givenDataSegment,
			Block block,
			CheckpointSerialNumber checkpointSerialNumber,
			ReportSerialNumber reportSerialNumber)
	throws LtpException {
		super(SegmentType.REPORT_SEGMENT);
		setSessionID(block.getSessionId());
		setReportSerialNumber(new ReportSerialNumber(reportSerialNumber));
		setCheckpointSerialNumber(new CheckpointSerialNumber(checkpointSerialNumber));
		
		// First determine upper and lower bounds
		_lowerBound = 0L;
		_upperBound = 
			givenDataSegment.getClientDataOffset() + 
			givenDataSegment.getClientDataLength();
		
		// Now build Claims
		ReceptionClaim priorClaim = null;
		long priorSegmentOffset = 0;
		long priorSegmentLength = 0;
		ArrayList<ReceptionClaim> claimsList = new ArrayList<ReceptionClaim>();
		for (DataSegment dataSegment : block) {
			long dataSegmentOffset = dataSegment.getClientDataOffset();
			int dataSegmentLength = dataSegment.getClientDataLength();
			
			if (dataSegmentOffset + dataSegmentLength > _upperBound) {
				break;
			}
			if (priorClaim != null &&
				priorSegmentOffset + priorSegmentLength == dataSegmentOffset) {
				// This DataSegment is contiguous with prior DataSegment
				// so extend prior Claim
				priorClaim._length += dataSegmentLength;
			} else {
				// No prior claim or there's a "hole"
				// so create new Claim
				ReceptionClaim claim = new ReceptionClaim(
						dataSegmentOffset - _lowerBound,
						dataSegmentLength);
				claimsList.add(claim);
				priorClaim = claim;
			}
			priorSegmentOffset = dataSegmentOffset;
			priorSegmentLength = dataSegmentLength;
		}
		
		_receptionClaimCount = claimsList.size();
		if (_receptionClaimCount < 1) {
			_logger.severe("Built a ReportSegment with 0 claims!");
			_logger.severe(givenDataSegment.dump("", true));
			_logger.severe(block.dump("", true));
			throw new LtpException("Built a ReportSegment with 0 claims!");
		}
		_receptionClaims = new ReceptionClaim[_receptionClaimCount];
		_receptionClaims = claimsList.toArray(_receptionClaims);
	}
	
	/**
	 * Internal constructor used by generateReceptionReport below.  The idea
	 * here is to construct a ReportSegment that contains a subset of the
	 * claims in the given ReportSegment.
	 * @param original Given ReportSegment which we are subsetting
	 * @param lowerIndex Index into original's claims of first claim to include.
	 * @param upperIndex Index into original's claims of last claim to include.
	 */
	private ReportSegment(
			InboundBlock block,
			ReportSegment original, 
			int lowerIndex, 
			int upperIndex) {
		super(Segment.SegmentType.REPORT_SEGMENT);
		
		setSessionID(block.getSessionId());
		_receptionClaimCount = upperIndex - lowerIndex + 1;
		_lowerBound = original._lowerBound + original._receptionClaims[lowerIndex]._offset;
		_upperBound = original._lowerBound + original._receptionClaims[upperIndex]._offset +
			original._receptionClaims[upperIndex]._length;
		_receptionClaims = new ReceptionClaim[_receptionClaimCount];
		int jx = lowerIndex;
		for (int ix = 0; ix < _receptionClaimCount; ix++, jx++) {
			_receptionClaims[ix] = new ReceptionClaim();
			_receptionClaims[ix]._offset =
				original._receptionClaims[jx]._offset +
					original._lowerBound - 
					_lowerBound;
			_receptionClaims[ix]._length =
				original._receptionClaims[jx]._length;
		}
		_checkpointSerialNumber = new CheckpointSerialNumber(
				original._checkpointSerialNumber);
		_reportSerialNumber = new ReportSerialNumber(
				block.incrementReportSerialNumber());
	}
	
	/**
	 * Generate a List of ReportSegments comprising a "Primary" Reception Report
	 * for the given Block.  The List of ReportSegments will, collectively,
	 * report on each and every Segment in the given Block up to and including
	 * the scope of the given DataSegment.  We will report a LowerBound of 0
	 * regardless of what has been received.
	 * NOTE: this constructor can build a ReportSegment which is too large for
	 * the MTU of the Link on which the given Block originated.  Use
	 * generateReceptionReport() to generate a set of ReportSegments, which
	 * together cover the desired range and each of which don't exceed the
	 * Link MTU.
	 * @param givenDataSegment Given DataSegment
	 * @param block InboundBlock of which given DataSegment is a part
	 * @param checkpointSerialNumber CheckpointSerialNumber of the Checkpoint
	 * DataSegment trigger this report.
	 * @param reportSerialNumber ReportSerialNumber for this Report
	 * @return List of ReportSegments generated
	 * @throws LtpException on various errors
	 */
	public static List<ReportSegment> generateReceptionReport(
			DataSegment givenDataSegment,
			InboundBlock block,
			CheckpointSerialNumber checkpointSerialNumber,
			ReportSerialNumber reportSerialNumber) 
		throws LtpException {
		
		// Max # claims allowed on given Block's Link.
		int maxClaims = block.getLink().getMaxClaimCountPerReport();
		
		// Construct a single ReportSegment which covers the entire claim.
		// However, that might be too large for the MTU of the Link from
		// which the given Block arrived.
		ArrayList<ReportSegment> result = new ArrayList<ReportSegment>();
		ReportSegment reportSegment = new ReportSegment(
				givenDataSegment, 
				block, 
				checkpointSerialNumber, 
				reportSerialNumber);
		int totalClaims = reportSegment.getReceptionClaimCount();
		if (totalClaims <= maxClaims) {
			// Not too large, just return the single ReportSegment
			result.add(reportSegment);
			return result;
		}
		
		// A single ReportSegment is too large.  We're going to have to carve
		// it up.  We carve it up into several ReportSegments, each of which
		// contains 'maxClaims' claims.
		int jx = maxClaims - 1;
		for (int ix = 0; ix < totalClaims; ix += maxClaims, jx += maxClaims) {
			if (jx >= totalClaims) {
				jx = totalClaims - 1;
			}
			ReportSegment newReportSegment = new ReportSegment(block, reportSegment, ix, jx);
			result.add(newReportSegment);
		}
		return result;
	}
	
	/**
	 * Called from super to decode the contents of the segment.  Super has
	 * already decoded the segment header.  This decodes the segment type
	 * specific body of the segment.
	 */
	@Override
	protected void decodeContents(DecodeState decodeState) throws JDtnException {
		setReportSerialNumber(new ReportSerialNumber(decodeState));
		setCheckpointSerialNumber(new CheckpointSerialNumber(decodeState));
		setUpperBound(Utils.sdnvDecodeLong(decodeState));
		setLowerBound(Utils.sdnvDecodeLong(decodeState));
		setReceptionClaimCount(Utils.sdnvDecodeInt(decodeState));
		
		_receptionClaims = new ReceptionClaim[_receptionClaimCount];
		for (int ix = 0; ix < _receptionClaimCount; ix++) {
			_receptionClaims[ix] = new ReceptionClaim(decodeState);
		}
		
		// Check constraints on ReportSegment (RFC5326 3.2.2)
		for (int ix = 0; ix < _receptionClaimCount; ix++) {
			ReceptionClaim claim = _receptionClaims[ix];
			
			// A reception claim's length shall never be less than 1 and shall
	        // never exceed the difference between the upper and lower bounds
	        // of the report segment.
			if (claim._length < 1 || 
				claim._length > _upperBound - _lowerBound) {
				if (GeneralManagement.isDebugLogging()) {
					if (_logger.isLoggable(Level.FINEST)) {
						_logger.finest(
								"Invalid Claim Length: " +
								claim._length +
								"; UpperBound=" + _upperBound +
								"; LowerBound=" + _lowerBound);
						_logger.finest(dump("", true));
					}
				}
				throw new LtpException(
						"Invalid Claim Length: " +
						claim._length +
						"; UpperBound=" + _upperBound +
						"; LowerBound=" + _lowerBound);
			}
			
			// The offset of a reception claim shall always be greater than
	        // the sum of the offset and length of the prior claim, if any.
			// Note: I think the intent was >=, rather than >.
			if (ix > 0) {
				if (claim._offset < _receptionClaims[ix - 1]._offset + _receptionClaims[ix - 1]._length) {
					if (GeneralManagement.isDebugLogging()) {
						if (_logger.isLoggable(Level.FINEST)) {
							_logger.finest(
									"Claim offset " + claim._offset +
									" <= prior claim offset " + _receptionClaims[ix - 1]._offset +
									"  + prior claim length " + _receptionClaims[ix - 1]._length);
							_logger.finest(dump("", true));
						}
					}
					throw new LtpException(
							"Claim offset " + claim._offset +
							" <= prior claim offset " + _receptionClaims[ix - 1]._offset +
							"  + prior claim length " + _receptionClaims[ix - 1]._length);
				}
			}
			
			// The sum of a reception claim's offset and length and the lower
	        // bound of the report segment shall never exceed the upper bound
	        // of the report segment.
			if (claim._offset + claim._length + _lowerBound > _upperBound) {
				if (GeneralManagement.isDebugLogging()) {
					if (_logger.isLoggable(Level.FINEST)) {
						_logger.finest(
								"Claim offset " + claim._offset +
								" + claim length " + claim._length +
								" + lowerBound " + _lowerBound +
								" > upperBound " + _upperBound);
						_logger.finest(dump("", true));
					}
				}
				throw new LtpException(
						"Claim offset " + claim._offset +
						" + claim length " + claim._length +
						" + lowerBound " + _lowerBound +
						" > upperBound " + _upperBound);
			}
		}

	}

	/**
	 * Encode the ReportSegment into the given buffer
	 * @param encodeState Buffer to encode into
	 * @throws JDtnException on errors
	 */
	@Override
	protected void encodeContents(EncodeState encodeState) throws JDtnException {
		_reportSerialNumber.encode(encodeState);
		_checkpointSerialNumber.encode(encodeState);
		Utils.sdnvEncodeLong(_upperBound, encodeState);
		Utils.sdnvEncodeLong(_lowerBound, encodeState);
		Utils.sdnvEncodeInt(_receptionClaimCount, encodeState);
		for (int ix = 0; ix < _receptionClaimCount; ix++) {
			_receptionClaims[ix].encode(encodeState);
		}		
	}

	/**
	 * Determine if this Report covers the given DataSegment; i.e., if this
	 * report contains a claim that the given DataSegment has been received.
	 * @param dataSegment Given DataSegment
	 * @return True if this claim covers given DataSegment.
	 */
	public boolean covers(DataSegment dataSegment) {
		long dataSegmentOffset = dataSegment.getClientDataOffset();
		long dataSegmentLength = dataSegment.getClientDataLength();
		if (dataSegmentOffset >= _lowerBound &&
			dataSegmentOffset + dataSegmentLength <= _upperBound) {
			
			for (ReceptionClaim claim : _receptionClaims) {
				long claimOffset = claim._offset + _lowerBound;
				long claimUpper = claimOffset + claim._length;
				if (dataSegmentOffset >= claimOffset &&
					dataSegmentOffset + dataSegmentLength <= claimUpper) {
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Determine if this Report implies that the given DataSegment needs to be
	 * resent.  i.e., True if:
	 * <ul>
	 *   <li>DataSegment is red
	 *   <li>DataSegment has not been acknowledged by a prior ReportSegment.
	 *   <li>DataSegment is in scope of the ReportSegment
	 * </ul>
	 * @param dataSegment Given DataSegment
	 * @return True if resend required
	 */
	public boolean isResendRequired(DataSegment dataSegment) {
		long segmentLowerBound = dataSegment.getClientDataOffset();
		long segmentUpperBound = segmentLowerBound + dataSegment.getClientDataLength();
		if (dataSegment.isRedData() &&
			!dataSegment.isAcked() &&
			segmentLowerBound >= getLowerBound() &&
			segmentUpperBound <= getUpperBound()) {
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Uniquely ids report among all reports issued by receiver in session 
	 */
	public ReportSerialNumber getReportSerialNumber() {
		return _reportSerialNumber;
	}

	/**
	 * Uniquely ids report among all reports issued by receiver in session 
	 */
	public void setReportSerialNumber(ReportSerialNumber reportSerialNumber) {
		this._reportSerialNumber = reportSerialNumber;
	}

	/** 
	 * Chkpt serial number of the checkpoint that requested the report.
	 * must be zero if the report is async; i.e., not issued in response to
	 * a checkpoint.
	 */
	public CheckpointSerialNumber getCheckpointSerialNumber() {
		return _checkpointSerialNumber;
	}

	/** 
	 * Chkpt serial number of the checkpoint that requested the report.
	 * must be zero if the report is async; i.e., not issued in response to
	 * a checkpoint.
	 */
	public void setCheckpointSerialNumber(CheckpointSerialNumber checkpointSerialNumber) {
		this._checkpointSerialNumber = checkpointSerialNumber;
	}

	/** 
	 * Size of the block prefix to which the claims pertain. 
	 * */
	public long getUpperBound() {
		return _upperBound;
	}

	/** 
	 * Size of the block prefix to which the claims pertain. 
	 * */
	public void setUpperBound(long upperBound) {
		this._upperBound = upperBound;
	}

	/**
	 * size of (interior) block prefix to which claims don't pertain
	 */
	public long getLowerBound() {
		return _lowerBound;
	}

	/**
	 * size of (interior) block prefix to which claims don't pertain
	 */
	public void setLowerBound(long lowerBound) {
		this._lowerBound = lowerBound;
	}

	/**
	 * Number of reception claims in this report
	 */
	public int getReceptionClaimCount() {
		return _receptionClaimCount;
	}

	/**
	 * Number of reception claims in this report
	 */
	public void setReceptionClaimCount(int receptionClaimCount) {
		this._receptionClaimCount = receptionClaimCount;
	}

	/**
	 * Set of reception claims in this report
	 */
	public ReceptionClaim[] getReceptionClaims() {
		return _receptionClaims;
	}

	/**
	 * Set of reception claims in this report
	 */
	public void setReceptionClaims(ReceptionClaim[] receptionClaims) {
		this._receptionClaims = receptionClaims;
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
		StringBuffer sb = new StringBuffer(indent + "ReportSegment\n");
		sb.append(super.dump(indent + "  ", detailed));
		sb.append(_checkpointSerialNumber.dump(indent + "  ", detailed));
		sb.append(_reportSerialNumber.dump(indent + "  ", detailed));
		sb.append(indent + "  LowerBound=" + _lowerBound + "\n");
		sb.append(indent + "  UpperBound=" + _upperBound + "\n");
		sb.append(indent + "  ReceptionClaimCount=" + _receptionClaimCount + "\n");
		if (detailed) {
			for (ReceptionClaim claim : _receptionClaims) {
				sb.append(claim.dump(indent + "  ", detailed));
			}
		}
		return sb.toString();
	}

	/**
	 * RS (ReportSegment) Timer; Started when a RS (Report Segment) is sent.
	 * Used to timeout on RA (Report Ack) coming back from that Report Segment.
	 * Managed by LtpInbound.
	 */
	public TimerTask getRsTimerTask() {
		return _rsTimerTask;
	}

	/**
	 * RS (ReportSegment) Timer; Started when a RS (Report Segment) is sent.
	 * Used to timeout on RA (Report Ack) coming back from that Report Segment.
	 * Managed by LtpInbound.
	 */
	public void setRsTimerTask(TimerTask rsTimerTask) {
		this._rsTimerTask = rsTimerTask;
	}

}
