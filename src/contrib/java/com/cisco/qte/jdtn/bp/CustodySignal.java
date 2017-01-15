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
 * A CustodySignal - Payload for a Bundle containing an AdministrativeRecord
 * which reports on Custody transfers.
 */
public class CustodySignal extends AdministrativeRecord {
	/** Status Field: Bit indicating custody transfer succeeded */
	public static final int CUSTODY_TRANSFER_SUCCEEDED = 0x80;
	
	/** Reason Field Mask in Status byte */
	public static final int REASON_MASK = 0x7f;
	/** Reason Field: No additional info */
	public static final int REASON_NO_FURTHER_INFO = 0x00;
	/** Reason Field: Redundant reception */
	public static final int REASON_REDUNDANT_RECEPTION = 0x03;
	/** Reason Field: Depleted storage */
	public static final int REASON_DEPLETED_STORAGE = 0x04;
	/** Reason Field: Destination endpoint id unintelligible */
	public static final int REASON_BAD_DEST_EID = 0x05;
	/** Reason Field: No known route to destination from here */
	public static final int REASON_NO_ROUTE = 0x06;
	/** Reason Field: No timely contact with next node on route */
	public static final int REASON_NO_TIMELY_CONTACT = 0x07;
	/** Reason Field: Block unintelligible */
	public static final int REASON_BAD_BLOCK = 0x08;
	
	/** Whether custody transfer succeeded */
	protected boolean _custodyXferSucceeded = false;
	/** Reason for custody transfer success or failure */
	protected int _reason = REASON_NO_FURTHER_INFO;
	/** Fragment Offset (if isForFragment) */
	protected long _fragmentOffset = 0L;
	/** Fragment Length (valid if IsForFragment) */
	protected long _fragmentLength = 0L;
	/** Time of Custody Signal */
	protected AdministrativeTimeStamp _timeStamp = new AdministrativeTimeStamp();
	/** Timestamp of referenced Bundle */
	protected Timestamp _bundleTimeStamp = new Timestamp();
	/** Referenced Bundle Source EndPointId */
	protected EndPointId _bundleSourceEndPointId = EndPointId.getDefaultEndpointId();
	
	private BundleId _referencedBundleId = null;
	
	/**
	 * Construct an almost-empty CustodySignal
	 * @param forFragment Whether CustodySignal is for a Fragment
	 */
	public CustodySignal(boolean forFragment) {
		super(AdministrativeRecord.ADMIN_RECORD_TYPE_CUSTODY_SIGNAL, forFragment);
	}
	
	/**
	 * Construct a CustodySignal by decoding from given Decode buffer
	 * @param decodeState Given Decode buffer
	 * @throws JDtnException on decoding errors
	 */
	public CustodySignal(boolean forFragment, DecodeState decodeState) throws JDtnException {
		super(AdministrativeRecord.ADMIN_RECORD_TYPE_CUSTODY_SIGNAL, forFragment);
		int status = decodeState.getByte();
		setCustodyXferSucceeded((status & CUSTODY_TRANSFER_SUCCEEDED) != 0);
		setReason(status & REASON_MASK);
		if (isForFragment()) {
			setFragmentOffset(Utils.sdnvDecodeLong(decodeState));
			setFragmentLength(Utils.sdnvDecodeLong(decodeState));
		}
		setTimeStamp(new AdministrativeTimeStamp(decodeState));
		setBundleTimeStamp(new Timestamp(decodeState));
		
		int eidLen = Utils.sdnvDecodeInt(decodeState);
		byte[] eidBytes = decodeState.getBytes(eidLen);
		String eidStr = new String(eidBytes, 0, eidLen);
		setBundleSourceEndPointId(EndPointId.createEndPointId(eidStr));
	}

	/**
	 * Encode this CustodySignal into the given Encode buffer
	 * @param encodeState Given Encode buffer
	 * @throws JDtnException on Encoding errors
	 */
	@Override
	public void encode(java.sql.Connection con, EncodeState encodeState) throws JDtnException {
		super.encode(con, encodeState);
		int status = 0;
		if (isCustodyXferSucceeded()) {
			status |= CUSTODY_TRANSFER_SUCCEEDED;
		}
		status |= getReason();
		encodeState.put(status);
		if (isForFragment()) {
			Utils.sdnvEncodeLong(getFragmentOffset(), encodeState);
			Utils.sdnvEncodeLong(getFragmentLength(), encodeState);
		}
		getTimeStamp().encode(encodeState);
		getBundleTimeStamp().encode(encodeState);
		
		String eidStr = getBundleSourceEndPointId().getEndPointIdString();
		byte[] bytes = eidStr.getBytes();
		Utils.sdnvEncodeInt(bytes.length, encodeState);
		encodeState.append(bytes, 0, bytes.length);
	}
	
	/**
	 * Convert given CustodySignal reason code to a human readable String
	 * @param reasonCode Given CustodySignal reason code
	 * @return Human readable String
	 */
	public static String reasonCodeToString(int reasonCode) {
		switch (reasonCode) {
		case REASON_BAD_BLOCK:
			return("BAD BLOCK");
		case REASON_BAD_DEST_EID:
			return("BAD DEST EID");
		case REASON_DEPLETED_STORAGE:
			return("DEPLETED STORAGE");
		case REASON_NO_FURTHER_INFO:
			return("No further info");
		case REASON_NO_ROUTE:
			return("NO ROUTE");
		case REASON_NO_TIMELY_CONTACT:
			return("NO TIMELY CONTACT");
		default:
			return(Integer.toString(reasonCode));
		}
	}
	
	/**
	 * Get the BundleId of the Bundle referenced by this CustodySignal
	 * @return What I said
	 */
	public BundleId getReferencedBundleId() {
		if (_referencedBundleId == null) {
			_referencedBundleId = new BundleId(getBundleSourceEndPointId(), getBundleTimeStamp());
		}
		return _referencedBundleId;
	}
	
	/**
	 * Dump this object
	 * @param indent How much to indent
	 * @param detailed Whether detailed dump desired
	 * @return the dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "CustodySignal\n");
		sb.append(super.dump(indent + "  ", detailed));
		sb.append(indent + "  Custody Xfer Succeeded=" + isCustodyXferSucceeded() + "\n");
		sb.append(indent + "  Reason=" + reasonCodeToString(getReason()) + "\n");
		if (isForFragment()) {
			sb.append(indent + "  Fragment Offset=" + getFragmentOffset() + "\n");
			sb.append(indent + "  Fragment Length=" + getFragmentLength() + "\n");
		}
		sb.append(indent + "  Time of Custody Signal=\n");
		sb.append(getTimeStamp().dump(indent + "    ", detailed));
		sb.append(indent + "  Bundle Timestamp=\n");
		sb.append(getBundleTimeStamp().dump(indent + "    ", detailed));
		sb.append(getBundleSourceEndPointId().dump(indent + "  Bundle Source EID=", detailed));
		
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return dump("", false);
	}
	
	/** Whether custody transfer succeeded */
	public boolean isCustodyXferSucceeded() {
		return _custodyXferSucceeded;
	}

	/** Whether custody transfer succeeded */
	public void setCustodyXferSucceeded(boolean custodyXferSucceeded) {
		this._custodyXferSucceeded = custodyXferSucceeded;
	}

	/** Reason for custody transfer success or failure */
	public int getReason() {
		return _reason;
	}

	/** Reason for custody transfer success or failure */
	public void setReason(int reason) {
		this._reason = reason;
	}

	/** Fragment Offset (if isForFragment) */
	public long getFragmentOffset() {
		return _fragmentOffset;
	}

	/** Fragment Offset (if isForFragment) */
	public void setFragmentOffset(long fragmentOffset) {
		this._fragmentOffset = fragmentOffset;
	}

	/** Fragment Length (valid if IsForFragment) */
	public long getFragmentLength() {
		return _fragmentLength;
	}

	/** Fragment Length (valid if IsForFragment) */
	public void setFragmentLength(long fragmentLength) {
		this._fragmentLength = fragmentLength;
	}

	/** Time of Custody Signal */
	public AdministrativeTimeStamp getTimeStamp() {
		return _timeStamp;
	}

	/** Time of Custody Signal */
	public void setTimeStamp(AdministrativeTimeStamp timeStamp) {
		this._timeStamp = timeStamp;
	}

	/** Timestamp of referenced Bundle */
	public Timestamp getBundleTimeStamp() {
		return _bundleTimeStamp;
	}

	/** Timestamp of referenced Bundle */
	public void setBundleTimeStamp(Timestamp bundleTimeStamp) {
		this._bundleTimeStamp = bundleTimeStamp;
	}

	/** Referenced Bundle Source EndPointId */
	public EndPointId getBundleSourceEndPointId() {
		return _bundleSourceEndPointId;
	}

	/** Referenced Bundle Source EndPointId */
	public void setBundleSourceEndPointId(EndPointId bundleSourceEndPointId) {
		this._bundleSourceEndPointId = bundleSourceEndPointId;
	}

}
