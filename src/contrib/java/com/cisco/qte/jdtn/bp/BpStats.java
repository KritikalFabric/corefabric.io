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
package com.cisco.qte.jdtn.bp;

/**
 * BP Statistics
 */
public class BpStats {
	/** # Data Bundles sourced */
	public long nDataBundlesSourced = 0;
	/** # Data Bundles received */
	public long nDataBundlesReceived = 0;
	/** # Bundle Status Reports forwarded */
	public long nDataBundlesFwded = 0;
	/** # Bundles delivered */
	public long nDataBundlesDelivered = 0;
	/** # Bundle Status Reports sent */
	public long nStatusReportsSent = 0;
	/** # Bundle Status Reports received */
	public long nStatusReportsReceived = 0;
	/** # Custody Signals Sent */
	public long nCustodySignalsSent = 0;
	/** # Custody Signals Received */
	public long nCustodySignalsReceived = 0;
	/** # Expired Bundles */
	public long nBundlesExpired = 0;
	/** # Expired Custody Signals */
	public long nCustodySignalsExpired = 0;
	/** Amount retained data, bytes */
	public long nRetainedBytes = 0;
	/** Amt mSecs Encoding Bundles */
	public long nEncodingMSecs = 0;
	/** Amt mSecs Decoding Bundles */
	public long nDecodingMSecs = 0;
	
	public void clear() {
		nDataBundlesSourced = 0;
		nDataBundlesReceived = 0;
		nDataBundlesFwded = 0;
		nDataBundlesDelivered = 0;
		nStatusReportsSent = 0;
		nStatusReportsReceived = 0;
		nCustodySignalsSent = 0;
		nCustodySignalsReceived = 0;
		nBundlesExpired = 0;
		nCustodySignalsExpired = 0;
		nRetainedBytes = 0;
		nEncodingMSecs = 0;
		nDecodingMSecs = 0;
	}
	
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "BpStats\n");
		sb.append(indent + "  nDataBundlesSourced=" + nDataBundlesSourced + "\n");
		sb.append(indent + "  nDataBundlesFwded=" + nDataBundlesFwded + "\n");
		sb.append(indent + "  nDataBundlesReceived=" + nDataBundlesReceived + "\n");
		sb.append(indent + "  nDataBundlesDelivered=" + nDataBundlesDelivered + "\n");
		sb.append(indent + "  nStatusReportsSent=" + nStatusReportsSent + "\n");
		sb.append(indent + "  nStatusReportsReceived=" + nStatusReportsReceived + "\n");
		sb.append(indent + "  nCustodySignalsSent=" + nCustodySignalsSent + "\n");
		sb.append(indent + "  nCustodySignalsReceived=" + nCustodySignalsReceived + "\n");
		sb.append(indent + "  nBundlesExpired=" + nBundlesExpired + "\n");
		sb.append(indent + "  nCustodySignalsExpired=" + nCustodySignalsExpired + "\n");
		sb.append(indent + "  nRetainedBytes=" + nRetainedBytes + "\n");
		if (nDataBundlesSourced != 0) {
			sb.append(indent + "  Avg mSecs Encoding=" +
					nEncodingMSecs / nDataBundlesSourced + "\n");
		}
		if (nDataBundlesReceived != 0) {
			sb.append(indent + "  Avg mSecs Decoding=" +
					nDecodingMSecs / nDataBundlesReceived + "\n");
		}
		
		return sb.toString();
	}
	
}
