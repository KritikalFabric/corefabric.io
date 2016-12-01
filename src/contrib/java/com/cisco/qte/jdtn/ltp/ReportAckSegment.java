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

import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.JDtnException;

/**
 * Report Acknowledge Segment.  Reports acknowledgement of receipt of a
 * prior Report Segment.  Contains reportSerialNumber of the Report Segment.
 */
public class ReportAckSegment extends Segment {

	protected ReportSerialNumber _reportSerialNumber = null;

	public ReportAckSegment(SegmentType segmentType) {
		super(segmentType);
	}
	
	public ReportAckSegment(
			SegmentType segmentType, 
			ReportSerialNumber reportSerialNumber,
			SessionId sessionId) {
		super(segmentType);
		setReportSerialNumber(reportSerialNumber);
		setSessionID(sessionId);
	}
	
	@Override
	protected void decodeContents(DecodeState decodeState) throws JDtnException {
		setReportSerialNumber(new ReportSerialNumber(decodeState));
	}

	@Override
	protected void encodeContents(EncodeState encodeState) throws JDtnException {
		getReportSerialNumber().encode(encodeState);
	}
	
	public ReportSerialNumber getReportSerialNumber() {
		return _reportSerialNumber;
	}

	public void setReportSerialNumber(ReportSerialNumber reportSerialNumber) {
		this._reportSerialNumber = reportSerialNumber;
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
		StringBuffer sb = new StringBuffer(indent + "ReportAckSegment\n");
		sb.append(super.dump(indent + "  ", detailed));
		sb.append(_reportSerialNumber.dump(indent + "  ", detailed));
		return sb.toString();
	}
	
}
