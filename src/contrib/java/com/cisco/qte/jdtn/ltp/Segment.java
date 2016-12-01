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

import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.LeakyBucketQueueElement;
import com.cisco.qte.jdtn.general.Utils;

/**
 * LTP Segment.  Abstract super-class for all LTP Segment classes.  Encompasses
 * common data and behavior for all Segments.  Provides a starting point for
 * encoding and decoding of Segment classes onto/from the wire.
 */
public abstract class Segment implements LeakyBucketQueueElement {
	/** Value to put in protocol version field of segment */
	public static final int LTP_CURRENT_VERSION = 0;
	
	// Bit masks for seg type flags field of segment
	/** Segment Type Flags - CTRL bit */
	protected static final int SEG_TYPE_FLAG_CTRL = 0x8;
	/** Segment Type Flags - EXC bit */
	protected static final int SEG_TYPE_FLAG_EXC = 0x4;
	/** Segment Type Flags - FLAG1 bit */
	protected static final int SEG_TYPE_FLAG_FLAG1 = 0x2;
	/** Segment Type Flags - FLAG0 bit */
	protected static final int SEG_TYPE_FLAG_FLAG0 = 0x1;
	
	// Seg type flags field type codes
	/** Segment Type Code - Regular Red Data Segment */
	protected static final int SEG_TYPE_RED_NOTCP_NOT_EORP_NOT_EOB = 0;
	/** Segment Type Code - Red Data Segment + Checkpoint */
	protected static final int SEG_TYPE_RED_CP_NOT_EORP_NOT_EOB = 1;
	/** Segment Type Code - Red Data Segment + Checkpoint + End of Red Part */
	protected static final int SEG_TYPE_RED_CP_EORP_NOT_EOB = 2;
	/** Segment Type Code - Red Data Segment + Checkpoint + End of Red Part + End of Block */
	protected static final int SEG_TYPE_RED_CP_EORP_EOB = 3;
	
	/** Segment Type Code - Regular Green Data Segment */
	protected static final int SEG_TYPE_GREEN_NOT_EOB = 4;
	/** Segment Type Code - Green Data Segment + End of Block */
	protected static final int SEG_TYPE_GREEN_EOB = 7;
	
	/** Segment Type Code - Report Segment */
	protected static final int SEG_TYPE_REPORT_SEGMENT = 8;
	/** Segment Type Code - Report Ack Segment */
	protected static final int SEG_TYPE_REPORT_ACK_SEGMENT = 9;
	/** Segment Type Code - Cancel Segment from Sender */
	protected static final int SEG_TYPE_CANCEL_SEGMENT_FROM_SENDER = 12;
	/** Segment Type Code - Cancel Segment Ack to Sender */
	protected static final int SEG_TYPE_CANCEL_SEGMENT_ACK_TO_SENDER = 13;
	/** Segment Type Code -  Cancel Segment from Receiver */
	protected static final int SEG_TYPE_CANCEL_SEGMENT_FROM_RECEIVER = 14;
	/** Segment Type Code - Cancel Segment Ack to Receiver */
	protected static final int SEG_TYPE_CANCEL_SEGMENT_ACK_TO_RECEIVER = 15;
	
	/**
	 * Possible segment types
	 */
	public enum SegmentType {
		/** Red (Reliable) DataSegment */
		RED_NOTCP_NOT_EORP_NOT_EOB,
		/** Red (Reliable) DataSegment, Checkpoint */
		RED_CP_NOT_EORP_NOT_EOB,
		/** Red (Reliable) DataSegment, Checkpoint, End of Red Part */
		RED_CP_EORP_NOT_EOB,
		/** Red (Reliable) DataSegment, Checkpoint, End of Red Part, End of Block */
		RED_CP_EORP_EOB,
		/** Green (Unreliable) DataSegment, End of Block */
		GREEN_NOT_EOB,
		/** Green (Unreliable) DataSegment */
		GREEN_EOB,
		/** Report Segment (RS) */
		REPORT_SEGMENT,
		/** Report Acknowledge Segment (RAS) */
		REPORT_ACK_SEGMENT,
		/** Cancel Request from Block Sender (CRS) */
		CANCEL_SEGMENT_FROM_SENDER,
		/** Cancel Acknowledgement to Block Sender (CAS) */
		CANCEL_SEGMENT_ACK_TO_SENDER,
		/** Cancel Request from Block Receiver (CRR) */
		CANCEL_SEGMENT_FROM_RECEIVER,
		/** Cancel Acknowledgement to Block Receiver (CAR) */
		CANCEL_SEGMENT_ACK_TO_RECEIVER,
	}
	
	/**
	 * Segment type
	 */
	protected SegmentType _segmentType;
	
	/**
	 * Protocol version number of segment
	 */
	protected int _versionNumber = LTP_CURRENT_VERSION;
	
	/**
	 * Uniquely identifies the session among all transmissions between sender
	 * and receiver.
	 */
	protected SessionId _sessionID;
	
	/**
	 * Number of header extensions
	 */
	protected int _nHeaderExtensions = 0;
	
	/**
	 * Header extensions
	 */
	protected SegmentExtension[] _headerExtensions = null;
	
	/**
	 * Number of trailer extensions
	 */
	protected int _nTrailerExtensions = 0;
	
	/**
	 * Trailer extensions.
	 */
	protected SegmentExtension[] _trailerExtensions = null;
	
	/**
	 * Link on which Segment is outbound, or Link on which Segment arrived
	 */
	protected LtpLink _link;
	
	/**
	 * Neighbor to send Segment to or Neighbor from which Segment received
	 */
	protected LtpNeighbor _neighbor = null;
	
	/**
	 * Number of times this DataSegment has been enqueued for retransmit.
	 */
	protected int _nTransmitEnqueues = 0;
	
	/**
	 * Whom to callback when Segment is successfully transmitted
	 */
	protected SegmentTransmitCallback _segmentTransmitCallback = null;
	
	/** Whether segment is valid; always true for Segment; DataSegment can set false */
	protected boolean _valid = true;
	
	/**
	 * Default constructor invisible to outside world
	 */
	@SuppressWarnings("unused")
	private Segment() {
		// Nothing
	}
	
	/**
	 * Constructor with given SegmentType.  Used when decoding a Segment.
	 * @param segmentType Given SegmentType
	 */
	protected Segment(SegmentType segmentType) {
		setSegmentType(segmentType);
	}
	
	/**
	 * Copy constructor
	 * @param segment Segment to copy from.  Copies only Segment Properties.
	 */
	public Segment(Segment segment) {
		setSegmentType(segment.getSegmentType());
		setSessionID(segment.getSessionID());
		setVersionNumber(segment.getVersionNumber());
		setNHeaderExtensions(segment.getNHeaderExtensions());
		setHeaderExtensions(segment.getHeaderExtensions());
		setLink(segment.getLink());
		setNeighbor(segment.getNeighbor());
		setNTrailerExtensions(segment.getNTrailerExtensions());
		setTrailerExtensions(segment.getTrailerExtensions());
		setNTransmitEnqueues(segment.getNTransmitEnqueues());
	}
	
	/**
	 * Decode the segment from the given buffer, offset, and length representing
	 * the Segment as received on the wire.  This decodes the common generic
	 * portions of the Segment, then calls out to implementation subclass to
	 * do subclass specific decoding.
	 * @param buffer Buffer containing data to be decoded.
	 * @param offset Offset into buffer to start of data to decode.
	 * @param buflen Length of buffer
	 * @return The Decoded Segment
	 * @throws JDtnException on decoding errors
	 */
	public static Segment decode(byte[] buffer, int offset, int buflen) 
	throws JDtnException {
		DecodeState decodeState = new DecodeState(buffer, offset, buflen);
		
		// Decode Protocol version
		int bite = decodeState.getByte();
		int version = (bite >> 4) & 0xf;
		if (version > LTP_CURRENT_VERSION) {
			throw new LtpException("Unsupported version in LTP Segment header: " + version);
		}
		
		// Decode Seg Type Flags; which determine type of segment and some attributes
		int segTypeFlags = bite & 0x0f;
		SegmentType segmentType = segTypeFlagsToSegmentType(segTypeFlags);
		Segment segment = null;
		switch (segmentType) {
		case RED_CP_EORP_EOB:
			// Fall thru
		case RED_CP_EORP_NOT_EOB:
			// Fall thru
		case RED_CP_NOT_EORP_NOT_EOB:
			// Fall thru
		case RED_NOTCP_NOT_EORP_NOT_EOB:
			// Fall thru
		case GREEN_EOB:
			// Fall thru
		case GREEN_NOT_EOB:
			segment = new DataSegment(segmentType);
			break;
		case CANCEL_SEGMENT_ACK_TO_RECEIVER:
			segment = new CancelSegment(segmentType);
			break;
		case CANCEL_SEGMENT_ACK_TO_SENDER:
			segment = new CancelAckSegment(segmentType);
			break;
		case CANCEL_SEGMENT_FROM_RECEIVER:
			segment = new CancelSegment(segmentType);
			break;
		case CANCEL_SEGMENT_FROM_SENDER:
			segment = new CancelSegment(segmentType);
			break;
		case REPORT_ACK_SEGMENT:
			segment = new ReportAckSegment(segmentType);
			break;
		case REPORT_SEGMENT:
			segment = new ReportSegment(segmentType);
			break;
		default:
			throw new LtpException(
					"Unrecognized Segment Type in Segment Type Flags: " + 
					segTypeFlags);
		}
		
		// Session ID
		segment.setSessionID(new SessionId(decodeState));
		
		// Header Extension Cnt / Trailer Extension Cnt
		int extensionCounts = Utils.decodeByte(decodeState);
		segment.setNHeaderExtensions((extensionCounts >> 4) & 0x0f);
		segment.setNTrailerExtensions(extensionCounts & 0x0f);
		
		// Header Extensions
		SegmentExtension[] extensions = new SegmentExtension[segment.getNHeaderExtensions()];
		segment.setHeaderExtensions(extensions);
		for (int ix = 0; ix < segment.getNHeaderExtensions(); ix++) {
			extensions[ix] = new SegmentExtension(decodeState);
		}
		
		// Segment Content; callout to subclass
		segment.decodeContents(decodeState);
		
		// Trailer Extensions
		extensions = new SegmentExtension[segment.getNTrailerExtensions()];
		segment.setTrailerExtensions(extensions);
		for (int ix = 0; ix < segment.getNTrailerExtensions(); ix++) {
			extensions[ix] = new SegmentExtension(decodeState);
		}
		decodeState.close();
		return segment;
	}
	
	/**
	 * Subclass provided method to do further segment-type specific decoding.
	 * @param decodeState State of Decode
	 * @throws JDtnException on decoding errors
	 */
	protected abstract void decodeContents(DecodeState decodeState)
	throws JDtnException;
	
	/**
	 * Encode the full Segment into given Buffer
	 * @param encodeState Given Buffer
	 * @throws InterruptedException 
	 * @throws LtpException on errors
	 */
	public void encode(EncodeState encodeState) throws JDtnException, InterruptedException {
		encode(encodeState, false);
	}
	
	/**
	 * Encode this Segment into the given buffer
	 * @param encodeState Given buffer
	 * @param envelopeOnly Only encode the Segment envelope, not entire Segment.
	 * This should be true only when we're estimating header length of
	 * DataSegment.
	 * @throws InterruptedException 
	 */
	public void encode(EncodeState encodeState, boolean envelopeOnly) throws JDtnException, InterruptedException {
		// Encode version segment type
		int segTypeFlags = 0;
		switch (getSegmentType()) {
		case GREEN_EOB:
			segTypeFlags = SEG_TYPE_GREEN_EOB;
			break;
		case GREEN_NOT_EOB:
			segTypeFlags = SEG_TYPE_GREEN_NOT_EOB;
			break;
		case RED_NOTCP_NOT_EORP_NOT_EOB:
			segTypeFlags = SEG_TYPE_RED_NOTCP_NOT_EORP_NOT_EOB;
			break;
		case RED_CP_NOT_EORP_NOT_EOB:
			segTypeFlags = SEG_TYPE_RED_CP_NOT_EORP_NOT_EOB;
			break;
		case RED_CP_EORP_NOT_EOB:
			segTypeFlags = SEG_TYPE_RED_CP_EORP_NOT_EOB;
			break;
		case RED_CP_EORP_EOB:
			segTypeFlags = SEG_TYPE_RED_CP_EORP_EOB;
			break;
		case REPORT_SEGMENT:
			segTypeFlags = SEG_TYPE_REPORT_SEGMENT;
			break;
		case REPORT_ACK_SEGMENT:
			segTypeFlags = SEG_TYPE_REPORT_ACK_SEGMENT;
			break;
		case CANCEL_SEGMENT_FROM_SENDER:
			segTypeFlags = SEG_TYPE_CANCEL_SEGMENT_FROM_SENDER;
			break;
		case CANCEL_SEGMENT_ACK_TO_RECEIVER:
			segTypeFlags = SEG_TYPE_CANCEL_SEGMENT_ACK_TO_RECEIVER;
			break;
		case CANCEL_SEGMENT_FROM_RECEIVER:
			segTypeFlags = SEG_TYPE_CANCEL_SEGMENT_FROM_RECEIVER;
			break;
		case CANCEL_SEGMENT_ACK_TO_SENDER:
			segTypeFlags = SEG_TYPE_CANCEL_SEGMENT_ACK_TO_SENDER;
			break;
		}
		segTypeFlags |= (LTP_CURRENT_VERSION << 4);
		encodeState.put(Utils.intToByteUnsigned(segTypeFlags));
		
		// Session ID
		_sessionID.encode(encodeState);
		
		// Header and Trailer Extension Counts
		int counts = (_nHeaderExtensions << 4) | _nTrailerExtensions;
		encodeState.put(counts);
		
		// Header Extensions
		for (int ix = 0; ix < _nHeaderExtensions; ix++) {
			_headerExtensions[ix].encode(encodeState);
		}
		
		// Segment Contents
		if (!envelopeOnly) {
			encodeContents(encodeState);
		}
		
		// Trailer Extensions
		for (int ix = 0; ix < _nTrailerExtensions; ix++) {
			_trailerExtensions[ix].encode(encodeState);
		}
	}
	
	// Mapping from Segment Type Flags field of Segment to SegmentType enum.
	private static final HashMap<Integer, SegmentType> segTypeFlagsToSegmentTypeMap =
		new HashMap<Integer, SegmentType>();
	static {
		segTypeFlagsToSegmentTypeMap.put(
				SEG_TYPE_RED_NOTCP_NOT_EORP_NOT_EOB,
				SegmentType.RED_NOTCP_NOT_EORP_NOT_EOB);
		segTypeFlagsToSegmentTypeMap.put(
				SEG_TYPE_RED_CP_NOT_EORP_NOT_EOB,
				SegmentType.RED_CP_NOT_EORP_NOT_EOB);
		segTypeFlagsToSegmentTypeMap.put
		(SEG_TYPE_RED_CP_EORP_NOT_EOB,
				SegmentType.RED_CP_EORP_NOT_EOB);
		segTypeFlagsToSegmentTypeMap.put(
				SEG_TYPE_RED_CP_EORP_EOB,
				SegmentType.RED_CP_EORP_EOB);
		segTypeFlagsToSegmentTypeMap.put(
				SEG_TYPE_GREEN_NOT_EOB,
				SegmentType.GREEN_NOT_EOB);
		segTypeFlagsToSegmentTypeMap.put(
				SEG_TYPE_GREEN_EOB,
				SegmentType.GREEN_EOB);
		segTypeFlagsToSegmentTypeMap.put(
				SEG_TYPE_REPORT_SEGMENT,
				SegmentType.REPORT_SEGMENT);
		segTypeFlagsToSegmentTypeMap.put(
				SEG_TYPE_REPORT_ACK_SEGMENT,
				SegmentType.REPORT_ACK_SEGMENT);
		segTypeFlagsToSegmentTypeMap.put(
				SEG_TYPE_CANCEL_SEGMENT_FROM_SENDER,
				SegmentType.CANCEL_SEGMENT_FROM_SENDER);
		segTypeFlagsToSegmentTypeMap.put(
				SEG_TYPE_CANCEL_SEGMENT_ACK_TO_SENDER,
				SegmentType.CANCEL_SEGMENT_ACK_TO_SENDER);
		segTypeFlagsToSegmentTypeMap.put(
				SEG_TYPE_CANCEL_SEGMENT_FROM_RECEIVER,
				SegmentType.CANCEL_SEGMENT_FROM_RECEIVER);
		segTypeFlagsToSegmentTypeMap.put(
				SEG_TYPE_CANCEL_SEGMENT_ACK_TO_RECEIVER,
				SegmentType.CANCEL_SEGMENT_ACK_TO_RECEIVER);
	}
	/**
	 * Convert segment type flags field to SegmentType
	 * @param segTypeFlags segment type flags field
	 * @return Corresponding SegmentType
	 * @throws LtpException if invalid segment type flags field
	 */
	protected static SegmentType segTypeFlagsToSegmentType(int segTypeFlags)
	throws LtpException {
		SegmentType result = segTypeFlagsToSegmentTypeMap.get(segTypeFlags);
		if (result == null) {
			throw new LtpException("Invalid value for Segment Type Flags: 0x" +
					Integer.toHexString(segTypeFlags));
		}
		return result;
	}
	
	/**
	 * Subclass provided method to encode type-specific contents
	 * @param encodeState Buffer to encode into
	 */
	protected abstract void encodeContents(EncodeState encodeState) throws JDtnException, InterruptedException;
	
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
		StringBuffer sb = new StringBuffer(indent + "Segment\n");
		sb.append(indent + "  Version=" + _versionNumber + "\n");
		sb.append(indent + "  Segment Type=" + _segmentType + "\n");
		sb.append(_sessionID.dump(indent + "  ", detailed));
		if (_headerExtensions != null) {
			sb.append(indent + "  Header Extensions\n");
			for (SegmentExtension extension : _headerExtensions) {
				sb.append(extension.dump(indent + "  ", detailed));
			}
		}
		if (_trailerExtensions != null) {
			sb.append(indent + "  Trailer Extensions\n");
			for (SegmentExtension extension : _trailerExtensions) {
				sb.append(extension.dump(indent + "  ", detailed));
			}
		}
		return sb.toString();
	}
	
	/**
	 * @return True if Segment type implies End of Block
	 */
	public boolean isEndOfBlock() {
		switch (_segmentType) {
		case GREEN_EOB:
			return true;
		case RED_CP_EORP_EOB:
			return true;
		default:
			return false;
		}
	}

	/**
	 * @return True if Segment type implies Checkpoinjt
	 */
	public boolean isCheckpoint() {
		switch (_segmentType) {
		case RED_CP_EORP_EOB:
			return true;
		case RED_CP_NOT_EORP_NOT_EOB:
			return true;
		case RED_CP_EORP_NOT_EOB:
			return true;
		default:
			return false;
		}
	}
	
	/**
	 * @return True if Segment type implies End of Red Part
	 */
	public boolean isEndOfRedPart() {
		switch (_segmentType) {
		case RED_CP_EORP_EOB:
			return true;
		case RED_CP_EORP_NOT_EOB:
			return true;
		default:
			return false;
		}
	}
	
	/**
	 * @return True if Segment type implies any form of Red DataSegment
	 */
	public boolean isRedData() {
		switch (_segmentType) {
		case RED_CP_EORP_EOB:
			return true;
		case RED_CP_EORP_NOT_EOB:
			return true;
		case RED_CP_NOT_EORP_NOT_EOB:
			return true;
		case RED_NOTCP_NOT_EORP_NOT_EOB:
			return true;
		default:
			return false;
		}
	}

	/**
	 * @return True if Segment type implies any form of Green DataSegment
	 */
	public boolean isGreenData() {
		switch (_segmentType) {
		case GREEN_EOB:
			return true;
		case GREEN_NOT_EOB:
			return true;
		default:
			return false;
		}
	}

	/**
	 * The protocol version number
	 */
	public int getVersionNumber() {
		return _versionNumber;
	}

	/**
	 * The protocol version number
	 */
	public void setVersionNumber(int versionNumber) {
		this._versionNumber = versionNumber;
	}

	/**
	 * Session Id for this Segment
	 */
	public SessionId getSessionID() {
		return _sessionID;
	}

	/**
	 * Session Id for this Segment
	 */
	public void setSessionID(SessionId sessionID) {
		this._sessionID = sessionID;
	}

	/**
	 * Header extensions for this Segment
	 */
	public SegmentExtension[] getHeaderExtensions() {
		return _headerExtensions;
	}

	/**
	 * Header extensions for this Segment
	 */
	public void setHeaderExtensions(SegmentExtension[] headerExtensions) {
		this._headerExtensions = headerExtensions;
		if (headerExtensions == null) {
			setNHeaderExtensions(0);
		} else {
			setNHeaderExtensions(headerExtensions.length);
		}
	}

	/**
	 * Trailer extensions for this Segment
	 */
	public SegmentExtension[] getTrailerExtensions() {
		return _trailerExtensions;
	}

	/**
	 * Trailer extensions for this Segment
	 */
	public void setTrailerExtensions(SegmentExtension[] trailerExtensions) {
		this._trailerExtensions = trailerExtensions;
		if (trailerExtensions == null) {
			setNTrailerExtensions(0);
		} else {
			setNTrailerExtensions(trailerExtensions.length);
		}
	}

	/**
	 * Number of Header extensions for this Segment
	 */
	public int getNHeaderExtensions() {
		return _nHeaderExtensions;
	}

	/**
	 * Number of Header extensions for this Segment
	 */
	public void setNHeaderExtensions(int nHeaderExtensions) {
		this._nHeaderExtensions = nHeaderExtensions;
	}

	/**
	 * Number of Trailer extensions for this Segment
	 */
	public int getNTrailerExtensions() {
		return _nTrailerExtensions;
	}

	/**
	 * Number of Trailer extensions for this Segment
	 */
	public void setNTrailerExtensions(int nTrailerExtensions) {
		this._nTrailerExtensions = nTrailerExtensions;
	}

	/**
	 * @return True if segment type implies any form of DataSegment
	 */
	public boolean isData() {
		switch (_segmentType) {
		case RED_CP_EORP_EOB:
			return true;
		case RED_CP_EORP_NOT_EOB:
			return true;
		case RED_CP_NOT_EORP_NOT_EOB:
			return true;
		case RED_NOTCP_NOT_EORP_NOT_EOB:
			return true;
		case GREEN_EOB:
			return true;
		case GREEN_NOT_EOB:
			return true;
		default:
			return false;
		}
	}

	/**
	 * @return True if segment type implies a ReportSegment
	 */
	public boolean isReportSegment() {
		switch (_segmentType) {
		case REPORT_SEGMENT:
			return true;
		default:
			return false;
		}
	}

	/**
	 * @return True if segment type implies a ReportAckSegment
	 */
	public boolean isReportAckSegment() {
		switch (_segmentType) {
		case REPORT_ACK_SEGMENT:
			return true;
		default:
			return false;
		}
	}

	/**
	 * @return True if segment type implies a CancelSegmentFromSender
	 */
	public boolean isCancelSegmentFromSender() {
		switch (_segmentType) {
		case CANCEL_SEGMENT_FROM_SENDER:
			return true;
		default:
			return false;
		}
	}

	/**
	 * @return True if segment type implies a CancelAckSegmentToSender
	 */
	public boolean isCancelAckSegmentToSender() {
		switch (_segmentType) {
		case CANCEL_SEGMENT_ACK_TO_SENDER:
			return true;
		default:
			return false;
		}
	}

	/**
	 * @return True if segment type implies a CancelSegmentFromReceiver
	 */
	public boolean isCancelSegmentFromReceiver() {
		switch (_segmentType) {
		case CANCEL_SEGMENT_FROM_RECEIVER:
			return true;
		default:
			return false;
		}
	}

	/**
	 * @return True if segment type implies a CancelAckSegmentToReceiver
	 */
	public boolean isCancelAckSegmentToReceiver() {
		switch (_segmentType) {
		case CANCEL_SEGMENT_ACK_TO_RECEIVER:
			return true;
		default:
			return false;
		}
	}

	/**
	 * Neighbor to/from which this Segment transmitted/received
	 */
	public LtpNeighbor getNeighbor() {
		return _neighbor;
	}

	/**
	 * Neighbor to/from which this Segment transmitted/received
	 */
	public void setNeighbor(LtpNeighbor neighbor) {
		this._neighbor = neighbor;
	}

	/**
	 * Link to/from which this Segment transmitted/received
	 */
	public LtpLink getLink() {
		return _link;
	}

	/**
	 * Link to/from which this Segment transmitted/received
	 */
	public void setLink(LtpLink link) {
		this._link = link;
	}

	/**
	 * Number of times this Segment has been enqueued for Transmission
	 */
	public int getNTransmitEnqueues() {
		return _nTransmitEnqueues;
	}

	/**
	 * Number of times this Segment has been enqueued for Transmission
	 */
	public void setNTransmitEnqueues(int nTransmitEnqueues) {
		this._nTransmitEnqueues = nTransmitEnqueues;
	}

	/**
	 * Increment the number of times this Segment has been enqueued for Transmission
	 */
	public int incrementNTransmitEnqueues() {
		return ++_nTransmitEnqueues;
	}
	
	/**
	 * @return True if number of times this Segment has been enqueued for
	 * transmit exceeds the LtpManagement Max Retransmits parameter.
	 */
	public boolean isTooManyTransmitEnqueues() {
		if (_nTransmitEnqueues > LtpManagement.getInstance().getLtpMaxRetransmits()) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * The Segment Type
	 */
	public SegmentType getSegmentType() {
		return _segmentType;
	}

	/**
	 * The Segment Type
	 */
	public void setSegmentType(SegmentType segmentType) {
		this._segmentType = segmentType;
	}

	/**
	 * Callback for after the Segment has been transmitted.
	 */
	public SegmentTransmitCallback getSegmentTransmitCallback() {
		return _segmentTransmitCallback;
	}

	/**
	 * Callback for after the Segment has been transmitted.
	 */
	public void setSegmentTransmitCallback(
			SegmentTransmitCallback segmentTransmitCallback) {
		this._segmentTransmitCallback = segmentTransmitCallback;
	}

	/** Whether segment is valid */
	public boolean isValid() {
		return _valid;
	}

	/** Whether segment is valid */
	public void setValid(boolean valid) {
		this._valid = valid;
	}
	
}
