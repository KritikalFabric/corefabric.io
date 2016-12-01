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

/**
 * LTP Statistics
 */
public class LtpStats {
	/** Number of Blocks sent */
	public long nBlocksSent = 0;
	/** Number of Blocks received */
	public long nBlocksReceived = 0;
	/** Number of Data Segments sent */
	public long nDataSegmentsSent = 0;
	/** Number of Data Segment Resends */
	public long nDataSegmentResends = 0;
	/** Number of Data Segments Received */
	public long nDataSegmentsReceived = 0;
	/** Number of Reports sent */
	public long nReportSegmentsSent = 0;
	/** Number of Reports received */
	public long nReportSegmentsReceived = 0;
	/** Number of Report Acks Sent */
	public long nReportAcksSent = 0;
	/** Number of Report Acks Received */
	public long nReportAcksReceived = 0;
	/** Number of Cancel Segments Sent */
	public long nCancelsSent = 0;
	/** Number of Cancel Segments Received */
	public long nCancelsReceived = 0;
	/** Number of Cancel Acks Sent */
	public long nCancelAcksSent = 0;
	/** Number of Cancel Acks Received */
	public long nCancelAcksReceived = 0;
	/** Number of Cancel Timer Expirations */
	public long nCancelExpirations = 0;
	/** Number of Checkpoint Timer Expirations */
	public long nCheckpointExpirations = 0;
	/** Number of Report Timer Expirations */
	public long nReportExpirations = 0;
	/** Number of Checkpoint Timer Starts */
	public long nCkPtTimerStarts = 0;
	/** Number of Checkpoint Timer Stops */
	public long nCkPtTimerStops = 0;
	/** Amt mSecs spent Encoding DataSegments */
	public long nEncodeMSecs = 0;
	/** Amt mSecs spent Decoding DataSegments */
	public long nDecodeMSecs = 0;
	/** Amt mSecs spent Encoding DataSegment payload */
	public long nPayloadEncodeMSecs = 0;
	
	public void clear() {
		nDataSegmentsSent = 0;
		nDataSegmentResends = 0;
		nDataSegmentsReceived = 0;
		nBlocksSent = 0;
		nBlocksReceived = 0;
		nReportSegmentsSent = 0;
		nReportSegmentsReceived = 0;
		nReportAcksSent = 0;
		nReportAcksReceived = 0;
		nCancelsSent = 0;
		nCancelsReceived = 0;
		nCancelAcksSent = 0;
		nCancelAcksReceived = 0;
		nCancelExpirations = 0;
		nCheckpointExpirations = 0;
		nReportExpirations = 0;
		nCkPtTimerStarts = 0;
		nCkPtTimerStops = 0;
		nEncodeMSecs = 0;
		nDecodeMSecs = 0;
		nPayloadEncodeMSecs = 0;
	}
	
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "LtpStats\n");
		sb.append(indent + "  nBlocksSent=" + nBlocksSent + "\n");
		sb.append(indent + "  nBlocksReceived=" + nBlocksReceived + "\n");
		sb.append(indent + "  nDataSegmentsSent=" + nDataSegmentsSent + "\n");
		sb.append(indent + "  nDataSegmentsResends=" + nDataSegmentResends + "\n");
		sb.append(indent + "  nDataSegmentsReceived=" + nDataSegmentsReceived + "\n");
		sb.append(indent + "  nReportSegmentsSent=" + nReportSegmentsSent + "\n");
		sb.append(indent + "  nReportSegmentsReceived=" + nReportSegmentsReceived + "\n");
		sb.append(indent + "  nReportAcksSent=" + nReportAcksSent + "\n");
		sb.append(indent + "  nReportAcksReceived=" + nReportAcksReceived + "\n");
		sb.append(indent + "  nCancelsSent=" + nCancelsSent + "\n");
		sb.append(indent + "  nCancelsReceived=" + nCancelsReceived + "\n");
		sb.append(indent + "  nCancelAcksSent=" + nCancelAcksSent + "\n");
		sb.append(indent + "  nCancelAcksReceived=" + nCancelAcksReceived + "\n");
		sb.append(indent + "  nCancelExpirations=" + nCancelExpirations + "\n");
		sb.append(indent + "  nCheckpointExpirations=" + nCheckpointExpirations + "\n");
		sb.append(indent + "  nReportExpirations=" + nReportExpirations + "\n");
		sb.append(indent + "  nCkPtTimerStarts=" + nCkPtTimerStarts + "\n");
		sb.append(indent + "  nCkptTimerStops=" + nCkPtTimerStops + "\n");
		if (nDataSegmentsSent != 0) {
			sb.append(indent + "  Encode mSecs (avg)=" +
					nEncodeMSecs / nDataSegmentsSent + "\n");
			sb.append(indent + "  Payload Encode mSecs (avg)=" +
					nPayloadEncodeMSecs / nDataSegmentsSent + "\n");
		}
		if (nDataSegmentsReceived != 0) {
			sb.append(indent + "  Decode mSecs (avg)=" +
					nDecodeMSecs / nDataSegmentsReceived + "\n");
		}
		
		return sb.toString();
	}
}
