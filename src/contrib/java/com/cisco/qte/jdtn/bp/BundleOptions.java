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

import com.cisco.qte.jdtn.bp.EidScheme;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.ltp.ServiceId;

/**
 * Options for the handling of a Bundle to transmit.
 */
public class BundleOptions {
	/** The 'ReportTo EndPointId; default is "dtn:none" */
	public EndPointId reportToEndPointId = 
		(BPManagement.getInstance().getEidScheme() == EidScheme.DTN_EID_SCHEME) ?
				EndPointId.getDefaultEndpointId() :
				IpnEndpointId.nullIpnEndPointId;
	
	/** The 'Custodian' EndPointId; default is "dtn:none" */
	public EndPointId custodianEndPointId =
		(BPManagement.getInstance().getEidScheme() == EidScheme.DTN_EID_SCHEME) ?
				EndPointId.getDefaultEndpointId() :
				IpnEndpointId.nullIpnEndPointId;
	
	/** True if the referenced Bundle is a Fragment; default is false */
	public boolean isFragment = false;
	
	/** True if the referenced Bundle is an Admin Record; default is false */
	public boolean isAdminRecord = false;
	
	/** True if must not fragment referenced bundle; default is false */
	public boolean mustNotFragment = false;
	
	/** True if custody transfer requested; default is false */
	public boolean isCustodyXferRqstd = false;
	
	/** True if destination EndPoint is a Singleton; default is true */
	public boolean isDestEndPointSingleton = true;
	
	/** True if app is requesting App Acknowledge; default is false */
	public boolean isAppAckRequested = false;
	
	/** True if report of Bundle reception requested; default is false */
	public boolean isReportBundleReception = false;
	
	/** True if report Custody Acceptance requested; default is false */
	public boolean isReportCustodyAcceptance = false;
	
	/** True if report Bundle Forwarding requested; default is false */
	public boolean isReportBundleForwarding = false;
	
	/** True if report Bundle Delivery requested; default is false */
	public boolean isReportBundleDelivery = false;
	
	/** True if report Bundle Deletion requested; default is false */
	public boolean isReportBundleDeletion = false;
	
	/** Lifetime in seconds; default is BPManagement NetworkTimeSpread */
	public long lifetime = BPManagement.getInstance().getNetworkTimeSpread();
	
	/** Fragment Offset; valid if isFragment==true, default 0 */
	public long fragmentOffset = 0L;
	
	/** Total App Data Unit Length; valid if isFragment==true, default 0 */
	public long totalAppDataUnitLength = 0L;
	
	/** The class of service / priority of the Bundle; default is BULK */
	public PrimaryBundleBlock.BPClassOfServicePriority classOfServicePriority =
		PrimaryBundleBlock.BPClassOfServicePriority.BULK;
	
	/** The LTP Color to send the Bundle; default is GREEN (unreliable) */
	public BundleColor blockColor = BundleColor.GREEN;
	
	/** The LTP Service ID to which this Bundle is to be transmitted */
	public ServiceId serviceId =
		new ServiceId(Utils.intToByteUnsigned(BPManagement.getInstance().getBpServiceId()));
	
	/**
	 * Constructor which sets up all default options
	 */
	public BundleOptions() {
		// Nothing
	}
	
	/**
	 * Constructor to set block color
	 * @param aBlockColor Given Block color
	 */
	public BundleOptions(BundleColor aBlockColor) {
		this.blockColor = aBlockColor;
	}
	
	/**
	 * Constructor for BundleOptions which sets up LTP BlockOptions to transmit
	 * given Payload all reliable with checkpointing at end.  All other options
	 * are defaulted.
	 * @param payload Given Payload
	 */
	public BundleOptions(Payload payload) {
		blockColor = BundleColor.RED;
	}
	
	/**
	 * Dump this object
	 * @param indent How much to indent
	 * @param detailed Whether detailed dump desired
	 * @return the dump
	 */
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "BundleOptions\n");
		sb.append(indent + "  ReportToEndPointId\n");
		sb.append(reportToEndPointId.dump(indent + "  ", detailed));
		sb.append(indent + "  CustodianEndPointId\n");
		sb.append(custodianEndPointId.dump(indent + "  ", detailed));
		sb.append(indent + "  Lifetime=" + lifetime + " Secs\n");
		sb.append(indent + "  ClassOfServicePriority=" + classOfServicePriority + "\n");
		if (isAdminRecord) {
			sb.append(indent + "  IsAdminRecord\n");
		}
		if (isAppAckRequested) {
			sb.append(indent + "  IsAppAckRequested\n");
		}
		if (isCustodyXferRqstd) {
			sb.append(indent + "  IsCustodyXferRqstd\n");
		}
		if (isDestEndPointSingleton) {
			sb.append(indent + "  IsDestEndPointSingleton\n");
		}
		if (isReportBundleDeletion) {
			sb.append(indent + "  IsReportBundleDeletion\n");
		}
		if (isReportBundleDelivery) {
			sb.append(indent + "  IsReportBundleDelivery\n");
		}
		if (isReportBundleReception) {
			sb.append(indent + "  IsReportBundleReception\n");
		}
		if (isReportCustodyAcceptance) {
			sb.append(indent + "  IsReportCustodyAcceptance\n");
		}
		if (mustNotFragment) {
			sb.append(indent + "  MustNotFragment\n");
		}
		if (isFragment) {
			sb.append(indent + "  IsFragment\n");
			sb.append(indent + "  TotalAppDataUnitLength=" + totalAppDataUnitLength + "\n");
			sb.append(indent + "  FragmentOffset=" + fragmentOffset + "\n");
		}
		sb.append(indent + "  Block color=" + blockColor + "\n");
		
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return dump("", false);
	}
	
}
