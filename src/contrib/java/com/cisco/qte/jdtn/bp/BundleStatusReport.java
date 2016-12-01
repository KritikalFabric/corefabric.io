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

import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Utils;

/**
 * Bundle Status Report - The Payload for a Bundle containing a Status Report
 * reporting a Bundle event.
 */
public class BundleStatusReport extends AdministrativeRecord {
	/** BundleStatusReport Flag Mask - Bundle Received */
	public static final int STATUS_FLAG_BUNDLE_RECEIVED = 0x01;
	/** BundleStatusReport Flag Mask - Custoday Transferred */
	public static final int STATUS_FLAG_CUSTODY_XFERRED = 0x02;
	/** BundleStatusReport Flag Mask - Bundle Forwarded */
	public static final int STATUS_FLAG_BUNDLE_FORWARDED = 0x04;
	/** BundleStatusReport Flag Mask - Bundle Delivered */
	public static final int STATUS_FLAG_BUNDLE_DELIVERED = 0x08;
	/** BundleStatusReport Flag Mask - Bundle Deleted */
	public static final int STATUS_FLAG_BUNDLE_DELETED = 0x10;
	
	/** Reason code - no additional information */
	public static final int REASON_CODE_NO_ADDL_INFO = 0x00;
	/** Reason code - Lifetime expired */
	public static final int REASON_CODE_EXPIRED = 0x01;
	/** Reason code - Forwarded over uni-directional link */
	public static final int REASON_CODE_FWDED_UNI_DIR_LINK = 0x02;
	/** Reason code - Transmission cancelled */
	public static final int REASON_CODE_TRANSMIT_CANCELED = 0x03;
	/** Reason code - Depleted storage */
	public static final int REASON_CODE_DEPLETED_STORAGE = 0x04;
	/** Reason code - Dest EID unintelligible */
	public static final int REASON_CODE_DEST_EID_UNKNOWN = 0x05;
	/** Reason code - No known route to destination from here */
	public static final int REASON_CODE_NO_ROUTE = 0x06;
	/** Reason code - No timely contact with next node on route */
	public static final int REASON_CODE_NO_TIMELY_CONTACT = 0x07;
	/** Reason code - Block unintelligible */
	public static final int REASON_CODE_BAD_BLOCK = 0x08;
	
	/** Report that bundle was received */
	protected boolean _reportBundleReceived = false;
	/** Report that bundle custody was accepted */
	protected boolean _reportCustodyAccepted = false;
	/** Report that bundle was forwarded */
	protected boolean _reportBundleForwarded = false;
	/** Report that bundle was delivered */
	protected boolean _reportBundleDelivered = false;
	/** Report that bundle was deleted */
	protected boolean _reportBundleDeleted = false;
	
	/** Reason code */
	protected int _reasonCode = REASON_CODE_NO_ADDL_INFO;
	/** Fragment offset (if isForFragment) */
	protected long _fragmentOffset = 0;
	/** Fragment length (if isForFragment) */
	protected long _fragmentLength = 0;
	
	/** Time at which Bundle Received (valid if isReportBundleReceived) */
	protected AdministrativeTimeStamp _receivedTime = null;
	/** Time at which Custody Acceped (valid if isReportCustodyAccepted */
	protected AdministrativeTimeStamp _custodyAcceptanceTime = null;
	/** Time at which Bundle Forwarded (valid if isReportBundleForwarded) */
	protected AdministrativeTimeStamp _forwardingTime = null;
	/** Time at which Bundle Delivered (valid if isReportBundleDelivered) */
	protected AdministrativeTimeStamp _deliveryTime = null;
	/** Time at which Bundle Deleted (valid if isReportBundleDeleted) */
	protected AdministrativeTimeStamp _deletionTime = null;
	
	/** Timestamp of referenced Bundle */
	protected Timestamp _bundleTimestamp = null;
	/** EndPointId of the source of the referenced Bundle */
	protected EndPointId _sourceEndPointId = null;
	
	// The BundleId of the referenced Bundle
	private BundleId _bundleId = null;
	
	/**
	 * Construct almost empty BundleStatusReport
	 * @param forFragment Whether its for a Fragment or not
	 */
	public BundleStatusReport(boolean forFragment) {
		super(AdministrativeRecord.ADMIN_RECORD_TYPE_STATUS_REPORT, forFragment);
	}
	
	/**
	 * Construct BundleStatusReport by decoding from given decode buffer
	 * @param decodeState Given Decode Buffer
	 * @throws JDtnException On Decoding errors
	 */
	public BundleStatusReport(boolean forFragment, DecodeState decodeState) throws JDtnException {
		super(AdministrativeRecord.ADMIN_RECORD_TYPE_STATUS_REPORT, forFragment);
		int flags = decodeState.getByte();
		setReportBundleReceived((flags & STATUS_FLAG_BUNDLE_RECEIVED) != 0);
		setReportCustodyAccepted((flags & STATUS_FLAG_CUSTODY_XFERRED) != 0);
		setReportBundleForwarded((flags & STATUS_FLAG_BUNDLE_FORWARDED) != 0);
		setReportBundleDelivered((flags & STATUS_FLAG_BUNDLE_DELIVERED) != 0);
		setReportBundleDeleted((flags & STATUS_FLAG_BUNDLE_DELETED) != 0);
		setReasonCode(decodeState.getByte());
		
		if (isForFragment()) {
			setFragmentOffset(Utils.sdnvDecodeLong(decodeState));
			setFragmentLength(Utils.sdnvDecodeLong(decodeState));
		}
		
		if (isReportBundleReceived()) {
			setReceivedTime(new AdministrativeTimeStamp(decodeState));
		}
		if (isReportCustodyAccepted()) {
			setCustodyAcceptanceTime(new AdministrativeTimeStamp(decodeState));
		}
		if (isReportBundleForwarded()) {
			setForwardingTime(new AdministrativeTimeStamp(decodeState));
		}
		if (isReportBundleDelivered()) {
			setDeliveryTime(new AdministrativeTimeStamp(decodeState));
		}
		if (isReportBundleDeleted()) {
			setDeletionTime(new AdministrativeTimeStamp(decodeState));
		}
		
		setBundleTimestamp(new Timestamp(decodeState));
		
		int eidLen = Utils.sdnvDecodeInt(decodeState);
		byte[] eidBytes = decodeState.getBytes(eidLen);
		String eidStr = new String(eidBytes, 0, eidLen);
		setSourceEndPointId(EndPointId.createEndPointId(eidStr));
		
	}

	/**
	 * Encode this BundleStatusReport into the given buffer
	 * @param encodeState Given encoding buffer
	 * @throws JDtnException On encoding errors
	 */
	@Override
	public void encode(EncodeState encodeState) throws JDtnException {
		super.encode(encodeState);
		int flags = 0;
		if (isReportBundleReceived()) {
			flags |= STATUS_FLAG_BUNDLE_RECEIVED;
		}
		if (isReportCustodyAccepted()) {
			flags |= STATUS_FLAG_CUSTODY_XFERRED;
		}
		if (isReportBundleForwarded()) {
			flags |= STATUS_FLAG_BUNDLE_FORWARDED;
		}
		if (isReportBundleDelivered()) {
			flags |= STATUS_FLAG_BUNDLE_DELIVERED;
		}
		if (isReportBundleDeleted()) {
			flags |= STATUS_FLAG_BUNDLE_DELETED;
		}
		encodeState.put(flags);
		encodeState.put(getReasonCode());
		if (isForFragment()) {
			Utils.sdnvEncodeLong(getFragmentOffset(), encodeState);
			Utils.sdnvEncodeLong(getFragmentLength(), encodeState);
		}
		if (isReportBundleReceived()) {
			getReceivedTime().encode(encodeState);
		}
		if (isReportCustodyAccepted()) {
			getCustodyAcceptanceTime().encode(encodeState);
		}
		if (isReportBundleForwarded()) {
			getForwardingTime().encode(encodeState);
		}
		if (isReportBundleDelivered()) {
			getDeliveryTime().encode(encodeState);
		}
		if (isReportBundleDeleted()) {
			getDeletionTime().encode(encodeState);
		}
		getBundleTimestamp().encode(encodeState);
		
		byte[] eidBytes = getSourceEndPointId().getEndPointIdString().getBytes();
		Utils.sdnvEncodeInt(eidBytes.length, encodeState);
		encodeState.append(eidBytes, 0, eidBytes.length);
	}
	
	/**
	 * Get the BundleId of the Bundle referenced by this report
	 * @return what I said
	 */
	public BundleId getReferencedBundleId() {
		if (_bundleId == null) {
			_bundleId = new BundleId(getSourceEndPointId(), getBundleTimestamp());
		}
		return _bundleId;
	}

	/**
	 * Convert given Bundle Status Report reason code to a String for display
	 * @param reasonCode Given reason code
	 * @return Human readable String
	 */
	public static String reasonCodeToString(int reasonCode) {
		switch (reasonCode) {
		case REASON_CODE_BAD_BLOCK:
			return("BAD BLOCK");
		case REASON_CODE_DEPLETED_STORAGE:
			return("DEPLETED STORAGE");
		case REASON_CODE_DEST_EID_UNKNOWN:
			return("BAD DEST EID");
		case REASON_CODE_EXPIRED:
			return("EXPIRED");
		case REASON_CODE_FWDED_UNI_DIR_LINK:
			return("FWDED UNI-DIR LINK");
		case REASON_CODE_NO_ADDL_INFO:
			return("No further info");
		case REASON_CODE_NO_ROUTE:
			return("NO ROUTE");
		case REASON_CODE_NO_TIMELY_CONTACT:
			return("NO TIMELY CONTACT");
		case REASON_CODE_TRANSMIT_CANCELED:
			return("XMIT CANCELED");
		default:
			return(Integer.toString(reasonCode));
		}
	}
	
	public String shortDump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "BundleStatusReport Summary\n");
		if (isReportBundleDeleted()) {
			sb.append(indent + "  Report Deleted\n");
			sb.append(indent + "  DeletedTime=\n");
			sb.append(getDeletionTime().dump(indent + "    ", detailed));
		}
		if (isReportBundleDelivered()) {
			sb.append(indent + "  Report Delivered\n");
			sb.append(indent + "  DeliveredTime=\n");
			sb.append(getDeliveryTime().dump(indent + "    ", detailed));
		}
		if (isReportBundleForwarded()) {
			sb.append(indent + "  Report Forwarded\n");
			sb.append(indent + "  ForwardedTime=\n");
			sb.append(getForwardingTime().dump(indent + "    ", detailed));
		}
		if (isReportBundleReceived()) {
			sb.append(indent + "  Report Received\n");
			sb.append(indent + "  BundleReceivedTime=\n");
			sb.append(getReceivedTime().dump(indent + "    ", detailed));
		}
		if (isReportCustodyAccepted()) {
			sb.append(indent + "  Report Custody Accepted\n");
			sb.append(indent + "  CustodyAcceptedTime=\n");
			sb.append(getCustodyAcceptanceTime().dump(indent + "    ", detailed));
		}
		sb.append(indent + "  Referenced Bundle Id\n");
		sb.append(getReferencedBundleId().dump(indent + "  ", detailed));
		if (isForFragment()) {
			sb.append(indent + "  FragmentOffset=" + getFragmentOffset() + "\n");
			sb.append(indent + "  FragmentLength=" + getFragmentLength() + "\n");
		}
		return sb.toString();
	}
	
	/**
	 * Dump this object
	 * @param indent How much to indent
	 * @param detailed Whether detailed dump desired
	 * @return the dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "BundleStatusReport\n");
		sb.append(super.dump(indent + "  ", detailed));
		sb.append(shortDump(indent + "  ", detailed));
		sb.append(indent + "  Reason=" + reasonCodeToString(getReasonCode()) + "\n");
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return dump("", false);
	}
	
	/** Report that bundle was received */
	public boolean isReportBundleReceived() {
		return _reportBundleReceived;
	}

	/** Report that bundle was received */
	public void setReportBundleReceived(boolean reportBundleReceived) {
		this._reportBundleReceived = reportBundleReceived;
	}

	/** Report that bundle custody was accepted */
	public boolean isReportCustodyAccepted() {
		return _reportCustodyAccepted;
	}

	/** Report that bundle custody was accepted */
	public void setReportCustodyAccepted(boolean reportCustodyAccepted) {
		this._reportCustodyAccepted = reportCustodyAccepted;
	}

	/** Report that bundle was forwarded */
	public boolean isReportBundleForwarded() {
		return _reportBundleForwarded;
	}

	/** Report that bundle was forwarded */
	public void setReportBundleForwarded(boolean reportBundleForwarded) {
		this._reportBundleForwarded = reportBundleForwarded;
	}

	/** Report that bundle was delivered */
	public boolean isReportBundleDelivered() {
		return _reportBundleDelivered;
	}

	/** Report that bundle was delivered */
	public void setReportBundleDelivered(boolean reportBundleDelivered) {
		this._reportBundleDelivered = reportBundleDelivered;
	}

	/** Report that bundle was deleted */
	public boolean isReportBundleDeleted() {
		return _reportBundleDeleted;
	}

	/** Report that bundle was deleted */
	public void setReportBundleDeleted(boolean reportBundleDeleted) {
		this._reportBundleDeleted = reportBundleDeleted;
	}

	/** Reason code */
	public int getReasonCode() {
		return _reasonCode;
	}

	/** Reason code */
	public void setReasonCode(int reasonCode) {
		this._reasonCode = reasonCode;
	}

	/** Fragment offset (if isForFragment) */
	public long getFragmentOffset() {
		return _fragmentOffset;
	}

	/** Fragment offset (if isForFragment) */
	public void setFragmentOffset(long fragmentOffset) {
		this._fragmentOffset = fragmentOffset;
	}

	/** Fragment length (if isForFragment) */
	public long getFragmentLength() {
		return _fragmentLength;
	}

	/** Fragment length (if isForFragment) */
	public void setFragmentLength(long fragmentLength) {
		this._fragmentLength = fragmentLength;
	}

	/** Time at which Bundle Received (valid if isReportBundleReceived) */
	public AdministrativeTimeStamp getReceivedTime() {
		return _receivedTime;
	}

	/** Time at which Bundle Received (valid if isReportBundleReceived) */
	public void setReceivedTime(AdministrativeTimeStamp receivedTime) {
		this._receivedTime = receivedTime;
	}

	/** Time at which Custody Accepted (valid if isReportCustodyAccepted) */
	public AdministrativeTimeStamp getCustodyAcceptanceTime() {
		return _custodyAcceptanceTime;
	}

	/** Time at which Custody Accepted (valid if isReportCustodyAccepted) */
	public void setCustodyAcceptanceTime(
			AdministrativeTimeStamp custodyAcceptanceTime) {
		this._custodyAcceptanceTime = custodyAcceptanceTime;
	}

	/** Time at which Bundle Forwarded (valid if isReportBundleForwarded) */
	public AdministrativeTimeStamp getForwardingTime() {
		return _forwardingTime;
	}

	/** Time at which Bundle Forwarded (valid if isReportBundleForwarded) */
	public void setForwardingTime(AdministrativeTimeStamp forwardingTime) {
		this._forwardingTime = forwardingTime;
	}

	/** Time at which Bundle Delivered (valid if isReportBundleDelivered) */
	public AdministrativeTimeStamp getDeliveryTime() {
		return _deliveryTime;
	}

	/** Time at which Bundle Delivered (valid if isReportBundleDelivered) */
	public void setDeliveryTime(AdministrativeTimeStamp deliveryTime) {
		this._deliveryTime = deliveryTime;
	}

	/** Time at which Bundle Deleted (valid if isReportBundleDeleted) */
	public AdministrativeTimeStamp getDeletionTime() {
		return _deletionTime;
	}

	/** Time at which Bundle Deleted (valid if isReportBundleDeleted) */
	public void setDeletionTime(AdministrativeTimeStamp deletionTime) {
		this._deletionTime = deletionTime;
	}

	/** Timestamp of referenced Bundle */
	public Timestamp getBundleTimestamp() {
		return _bundleTimestamp;
	}

	/** Timestamp of referenced Bundle */
	public void setBundleTimestamp(Timestamp bundleTimestamp) {
		this._bundleTimestamp = bundleTimestamp;
	}

	/** EndPointId of the source of the referenced Bundle */
	public EndPointId getSourceEndPointId() {
		return _sourceEndPointId;
	}

	/** EndPointId of the source of the referenced Bundle */
	public void setSourceEndPointId(EndPointId sourceEndPointId) {
		this._sourceEndPointId = sourceEndPointId;
	}

}
