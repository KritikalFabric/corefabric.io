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

import java.util.logging.Logger;

import com.cisco.qte.jdtn.bp.EidScheme;
import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Utils;

/**
 * The PrimaryBundleBlock is the first BundleBlock of any Bundle.  It contains
 * Bundle Processing Control Flags, Class of Service, and the Dictionary.
 */
public class PrimaryBundleBlock extends BundleBlock {
	private static final Logger _logger =
		Logger.getLogger(PrimaryBundleBlock.class.getCanonicalName());
	
	/** RFC5050 Protocol Version */
	public static final byte DEFAULT_PROTOCOL_VERSION = (byte)6;
	
	/** Minimum protocol version we will accept; This is so we can interop
	 *  with ou.edu code and Billy's Stack */
	public static final byte MIN_PROTOCOL_VERSION = (byte)4;
	
	/** Maximum protocol version we will accept */
	public static final byte MAX_PROTOCOL_VERSION = (byte)6;
	
	/** CBHE Scheme Number */
	public static final int CBHE_SCHEME_NUMBER = 1;
	
	/**
	 * BP Class of Service Priorities
	 */
	public enum BPClassOfServicePriority {
		BULK,
		NORMAL,
		EXPEDITED
	}
	
	/** Bundle Processing Control Flag Mask: is Fragment */
	public static final int BPCF_IS_FRAGMENT = 0x01;
	/** Bundle Processing Control Flag Mask: is Administrative Record */
	public static final int BPCF_IS_ADMIN_RECORD = 0x02;
	/** Bundle Processing Control Flag Mask: Must not fragment */
	public static final int BPCF_MUST_NOT_FRAGMENT = 0x04;
	/** Bundle Processing Control Flag Mask: Custody Transfer requested */
	public static final int BPCF_CUSTODY_XFER_RQSTED = 0x08;
	/** Bundle Processing Control Flag Mask: Destination endpoint is a singleton */
	public static final int BPCF_DEST_ENDPT_IS_SINGLETON = 0x10;
	/** Bundle Processing Control Flag Mask: Application ack requested */
	public static final int BPCF_APP_ACK_RQSTD = 0x20;
	/** Bundle Processing Control Flag Mask: Reserved */
	public static final int BPCF_RESERVED = 0x40;
	
	/** Primary Bundle Block Class of Service mask */
	public static final int BPCOS_MASK = 0x3f80;
	/** Primary Bundle Block Class of Service Priority: Bulk */
	public static final int BPCOS_PRIORITY_BULK = 0x0000;
	/** Primary Bundle Block Class of Service Priority: Normal */
	public static final int BPCOS_PRIORITY_NORMAL = 0x0080;
	/** Primary Bundle Block Class of Service Priority: Expedited */
	public static final int BPCOS_PRIORITY_EXPEDITED = 0x0100;
	
	/** Status Report Flag Mask: Request reporting of bundle reception */
	public static final int BPSTATUS_REPORT_BUNDLE_RECEPTION = 0x04000;
	/** Status Report Flag Mask: Request reporting of custody acceptance */
	public static final int BPSTATUS_REPORT_CUSTODY_ACCEPTANCE = 0x08000;
	/** Status Report Flag Mask: Request reporting of bundle forwarding */
	public static final int BPSTATUS_REPORT_BUNDLE_FORWARDING = 0x10000;
	/** Status Report Flag Mask: Request reporting of bundle delivery */
	public static final int BPSTATUS_REPORT_BUNDLE_DELIVERY = 0x20000;
	/** Status Report Flag Mask: Request reporting of bundle deletion */
	public static final int BPSTATUS_REPORT_BUNDLE_DELETION = 0x40000;
	
	/** The Protocol version */
	protected int _protocolVersion = BPManagement.getInstance().getOutboundProtocolVersion();

	/** If this Bundle is a Fragment */
	protected boolean _fragment = false;
	/** If Bundle Application Data Unit is an administrative record */
	protected boolean _adminRecord = false;
	/** If Bundle must not be fragmented */
	protected boolean _mustNotFragment = false;
	/** If Custody Transfer requested */
	protected boolean _custodyTransferRequested = false;
	/** If Destination EndPoint is a singleton */
	protected boolean _destEndPointSingleton = false;
	/** If application acknowledgement requested */
	protected boolean _appAckRequested = false;
	
	/** If request reporting of bundle reception */
	protected boolean _reportBundleReception = false;
	/** If request reporting of custody acceptance */
	protected boolean _reportCustodyAcceptance = false;
	/** If request reporting of bundle forwarding */
	protected boolean _reportBundleForwarding = false;
	/** If request reporting of bundle delivery */
	protected boolean _reportBundleDelivery = false;
	/** If request reporting of bundle deletion */
	protected boolean _reportBundleDeletion = false;
	
	/** Class of Service Priority */
	protected BPClassOfServicePriority _classOfServicePriority =
		BPClassOfServicePriority.BULK;
	
	/** The Block Length; excluding protocol version, SDNV encoded block 
	 * processing flags, and SDNV encoded Block Length */
	protected long _blockLength = 0;
	
	/** The Destination EndPointId */
	protected EndPointId _destinationEndPointId = new EndPointId();
	/** The Source EndPointId */
	protected EndPointId _sourceEndpointId = new EndPointId();
	/** The Report-To EndPointId */
	protected EndPointId _reportToEndPointId = new EndPointId();
	/** The current Custodian EndPointId */
	protected EndPointId _custodianEndPointId = new EndPointId();
	/** Creation Timestamp */
	protected Timestamp _creationTimestamp = new Timestamp();
	/** Lifetime, seconds since Creation Timestamp */
	protected long _lifetime = 0;
	
	/** Dictionary Length; number of bytes in raw Dictionary */
	protected int _dictionaryLength = 0;
	/** Dictionary; set of Strings indexed by integer */
	protected Dictionary _dictionary = new Dictionary();
	/** Fragment Offset (present only if IsFragment property is true */
	protected long _fragmentOffset = 0;
	/** Total Application Data Unit length
	 * (present only if IsFragment property is true */
	protected long _totalAppDataUnitLength = 0;
	
	/**
	 * Construct PrimaryBundleBlock for OutboundBundle.
	 * @param sourceEid Source EndPointId
	 * @param destEid Destination EndPointId
	 * @param options Options
	 */
	public PrimaryBundleBlock(
			EndPointId sourceEid, 
			EndPointId destEid, 
			BundleOptions options) throws BPException {
		
		// Set up Bundle Processing flags from passed in options */
		setFragment(options.isFragment);
		setAdminRecord(options.isAdminRecord);
		setMustNotFragment(options.mustNotFragment);
		setFragment(options.isFragment);
		setCustodyTransferRequested(options.isCustodyXferRqstd);
		setDestEndPointSingleton(options.isDestEndPointSingleton);
		setAppAckRequested(options.isAppAckRequested);
		setReportBundleReception(options.isReportBundleReception);
		setReportCustodyAcceptance(options.isReportCustodyAcceptance);
		setReportBundleForwarding(options.isReportBundleForwarding);
		setReportBundleDelivery(options.isReportBundleDelivery);
		setReportBundleDeletion(options.isReportBundleDeletion);
		setClassOfServicePriority(options.classOfServicePriority);
		
		setCreationTimestamp(new Timestamp());
		setLifetime(options.lifetime);
		
		if (isFragment()) {
			setFragmentOffset(options.fragmentOffset);
			setTotalAppDataUnitLength(options.totalAppDataUnitLength);
		}
		
		// Record EndPointIds
		setSourceEndpointId(sourceEid);
		setDestinationEndPointId(destEid);
		setReportToEndPointId(options.reportToEndPointId);
		setCustodianEndPointId(options.custodianEndPointId);
	}
	
	/**
	 * Construct a PrimaryBundleBlock from the given encoded state.
	 * @param bundle Bundle of which this Block is a part
	 * @param decodeState Provides state on decoding, whether decoding from a
	 * File or from memory.  The 'offset' portion of the Decode State is
	 * updated after this operation.
	 * @param eidScheme The EidScheme to use to encode the block
	 * @throws JDtnException On various decode errors
	 */
	public PrimaryBundleBlock(Bundle bundle, DecodeState decodeState, EidScheme eidScheme) 
	throws JDtnException {
		super(bundle, decodeState);
		setProtocolVersion(Utils.decodeByte(decodeState));
		if (getProtocolVersion() < MIN_PROTOCOL_VERSION ||
			getProtocolVersion() > MAX_PROTOCOL_VERSION) {
			// Bug ID 3090545: produce more debug info
			_logger.severe("Bad Protocol Version in Primary Bundle Block: " +
					getProtocolVersion());
			_logger.severe("Decoded Bundle to this point:");
			_logger.severe(decodeState.dump("", true));
			throw new BPException("Bad Protocol Version in Primary Bundle Block: " +
					getProtocolVersion());
		}
		setBundleProcessingControlFlags(Utils.sdnvDecodeInt(decodeState));
		setBlockLength(Utils.sdnvDecodeLong(decodeState));
		
		// Get scheme and SSP offsets but wait to do anything more until we
		// get to the raw dictionary
		int destSchemeOffset = Utils.sdnvDecodeInt(decodeState);
		int destSSPOffset = Utils.sdnvDecodeInt(decodeState);
		int sourceSchemeOffset = Utils.sdnvDecodeInt(decodeState);
		int sourceSSPOffset = Utils.sdnvDecodeInt(decodeState);
		int reportToSchemeOffset = Utils.sdnvDecodeInt(decodeState);
		int reportToSSPOffset = Utils.sdnvDecodeInt(decodeState);
		int custodianSchemeOffset = Utils.sdnvDecodeInt(decodeState);
		int custodianSSPOffset = Utils.sdnvDecodeInt(decodeState);
		
		setCreationTimestamp(new Timestamp(decodeState));
		setLifetime(Utils.sdnvDecodeLong(decodeState));
		
		// Read in raw dictionary
		_dictionary = new Dictionary(decodeState);
		
		if (eidScheme == EidScheme.IPN_EID_SCHEME) {
			
			// IPN Scheme
			// The PrimaryBundleBlock uses Compressed Bundle Header Encoding, in
			// which the destination offsets, source offsets, etc do not point
			// into the dictionary, and instead encode the EIDs directly.
			IpnEndpointId iEid = null;
			if (BPManagement.getInstance().isCBHECompatibility()) {
				// ION 1.0 R203 Compatibility
				int scheme = destSchemeOffset;
				if (scheme != CBHE_SCHEME_NUMBER) {
					throw new BPException("Decoded Scheme Number was " + scheme + " but expected " + CBHE_SCHEME_NUMBER);
				}
				iEid = new IpnEndpointId(destSSPOffset, sourceSchemeOffset);
				// DTN - IPN Gateway Feature: we are decoding a CBHE encoded
				// Bundle.  Translate the destination EID into a DTN scheme Eid.
				EndPointId mappedEid = EidMap.getInstance().getDtnEid(iEid);
				if (mappedEid != null) {
					setDestinationEndPointId(mappedEid);
				} else {
					setDestinationEndPointId(iEid);
				}

				iEid = new IpnEndpointId(sourceSSPOffset, reportToSchemeOffset);
				setSourceEndpointId(iEid);

				iEid = new IpnEndpointId(reportToSSPOffset, custodianSchemeOffset);
				mappedEid = EidMap.getInstance().getDtnEid(iEid);
				if (mappedEid != null) {
					setReportToEndPointId(mappedEid);
				} else {
					setReportToEndPointId(iEid);
				}
				iEid = new IpnEndpointId(custodianSSPOffset, 0);
				mappedEid = EidMap.getInstance().getDtnEid(iEid);
				if (mappedEid != null) {
					setCustodianEndPointId(mappedEid);
				} else {
					setCustodianEndPointId(iEid);
				}
			} else {
				// Strict CBHE-03 compliance
				iEid = new IpnEndpointId(destSchemeOffset, destSSPOffset);
				setDestinationEndPointId(iEid);
				iEid = new IpnEndpointId(sourceSchemeOffset, sourceSSPOffset);
				setSourceEndpointId(iEid);
				iEid = new IpnEndpointId(reportToSchemeOffset, reportToSSPOffset);
				setReportToEndPointId(iEid);
				iEid = new IpnEndpointId(custodianSchemeOffset, custodianSSPOffset);
				setCustodianEndPointId(iEid);
			}
			
		} else {
			// DTN Scheme: Standard EID encoding and Dictionary Encoding
			// Make sure the Dictionary included all reserved entries
			if (_dictionary.getRaw(destSchemeOffset) == null) {
				throw new BPException("Inbound Block didn't contain Destination Scheme in Dictionary");
			}
			if (_dictionary.getRaw(destSSPOffset) == null) {
				throw new BPException("Inbound Block didn't contain Destination SSP in Dictionary");
			}
			if (_dictionary.getRaw(sourceSchemeOffset) == null) {
				throw new BPException("Inbound Block didn't contain Source Scheme in Dictionary");
			}
			if (_dictionary.getRaw(sourceSSPOffset) == null) {
				throw new BPException("Inbound Block didn't contain Source SSP in Dictionary");
			}
			if (_dictionary.getRaw(reportToSchemeOffset) == null) {
				throw new BPException("Inbound Block didn't contain Report To Scheme in Dictionary");
			}
			if (_dictionary.getRaw(reportToSSPOffset) == null) {
				throw new BPException("Inbound Block didn't contain Report To SSP in Dictionary");
			}
			if (_dictionary.getRaw(custodianSchemeOffset) == null) {
				throw new BPException("Inbound Block didn't contain Custodian Scheme in Dictionary");
			}
			if (_dictionary.getRaw(custodianSSPOffset) == null) {
				throw new BPException("Inbound Block didn't contain Custodian SSP in Dictionary");
			}
			
			// Build EIDs from Dictionary
			String scheme = _dictionary.getRaw(destSchemeOffset).str;
			String ssp = _dictionary.getRaw(destSSPOffset).str;
			_destinationEndPointId = EndPointId.createEndPointId(scheme, ssp);
			
			scheme = _dictionary.getRaw(sourceSchemeOffset).str;
			ssp = _dictionary.getRaw(sourceSSPOffset).str;
			_sourceEndpointId = EndPointId.createEndPointId(scheme, ssp);
			
			scheme = _dictionary.getRaw(reportToSchemeOffset).str;
			ssp = _dictionary.getRaw(reportToSSPOffset).str;
			_reportToEndPointId = EndPointId.createEndPointId(scheme, ssp);
			
			scheme = _dictionary.getRaw(custodianSchemeOffset).str;
			ssp = _dictionary.getRaw(custodianSSPOffset).str;
			_custodianEndPointId = EndPointId.createEndPointId(scheme, ssp);
		}
		
		
		if (isFragment()) {
			setFragmentOffset(Utils.sdnvDecodeLong(decodeState));
			setTotalAppDataUnitLength(Utils.sdnvDecodeLong(decodeState));
		}
	}
	
	/**
	 * Encode this Object to the given EncodeState
	 * @param encodeState Given EncodeState
	 * @throws JDtnException On Encoding errors
	 * @throws InterruptedException 
	 */
	@Override
	public void encode(EncodeState encodeState, EidScheme eidScheme) 
	throws JDtnException, InterruptedException {
		// We cannot fully encode the Block until we know a couple things:
		// BlockLength - This is the length of the Encoded Block from the offsets
		// fields to the end.  We don't know this because we haven't encoded it
		// yet.
		// Raw Offsets into Dictionary - We don't know these until we encode the
		// Dictionary.
		// So we have to do the encoding in stages.  First, we'll skip encoding
		// the first few fields and encode all fields from the offsets to the
		// end into tempEncodeState1.  In order to do that, we need to encode
		// the dictionary into tempEncodeState2.  At the end, we'll put it all
		// together.
		
		// First we encode the Dictionary into tempEncodeState1
		EncodeState tempEncodeState1 = new EncodeState();
		_dictionary.rebuildDictionary(
				_destinationEndPointId, 
				_sourceEndpointId, 
				_reportToEndPointId, 
				_custodianEndPointId);
		_dictionary.encode(tempEncodeState1, eidScheme);
		tempEncodeState1.close();
		
		// Now encode offsets fields thru end into tempEncodeState2
		// Encode standard dictionary entries raw offsets
		EncodeState tempEncodeState2 = new EncodeState();
		if (eidScheme == EidScheme.IPN_EID_SCHEME) {
			
			// IPN Scheme
			if (!(getDestinationEndPointId() instanceof IpnEndpointId)) {
				throw new JDtnException("Destination EndPointId is not an IPN EID: " +
						getDestinationEndPointId().getEndPointIdString());
			}
			IpnEndpointId ipnDest = (IpnEndpointId)getDestinationEndPointId();
			
			IpnEndpointId ipnSrc = null;
			if (!(getSourceEndpointId() instanceof IpnEndpointId)) {
				// DTN - IPN Gateway Feature.  We are encoding a CBHE encoded
				// bundle.  The source EID is not an IPN EID.  See if we can
				// map it to an IPN EID.
				ipnSrc = EidMap.getInstance().getIpnEid(getSourceEndpointId());
				if (ipnSrc == null) {
					throw new JDtnException("Source EndPointId is not an IPN EID: " +
							getSourceEndpointId().getEndPointIdString());
				}
			} else {
				ipnSrc = (IpnEndpointId)getSourceEndpointId();
			}
			
			IpnEndpointId ipnReportTo = null;
			if (!(getReportToEndPointId() instanceof IpnEndpointId)) {
				// DTN - IPN Gateway Feature.  We are encoding a CBHE encoded
				// bundle.  The ReportTo EID is not an IPN EID.  See if we can
				// map it to an IPN EID.
				ipnReportTo = EidMap.getInstance().getIpnEid(getReportToEndPointId());
				if (ipnReportTo == null) {
					throw new JDtnException("ReportTo EndPointId is not an IPN EID: " +
							getReportToEndPointId().getEndPointIdString());
				}
			} else {
				ipnReportTo = (IpnEndpointId)getReportToEndPointId();
			}
			
			IpnEndpointId ipnCust = null;
			if (!(getCustodianEndPointId() instanceof IpnEndpointId)) {
				// DTN - IPN Gateway Feature.  We are encoding a CBHE encoded
				// bundle.  The Custodian EID is not an IPN EID.  See if we can
				// map it to an IPN EID.
				ipnCust = EidMap.getInstance().getIpnEid(getCustodianEndPointId());
				if (ipnCust == null) {
					throw new JDtnException("Custodian EndPointId is not an IPN EID: " +
							getCustodianEndPointId().getEndPointIdString());
				}
			} else {
				ipnCust = (IpnEndpointId)getCustodianEndPointId();
			}
			
			if (BPManagement.getInstance().isCBHECompatibility()) {
				// ION 1.0 R203 compatibility mode
				// 0 = SCHE_SCHEME_NUMBER (01)
				// 1 = Dest node number
				// 2 = Dest svc number
				// 3 = Src node number
				// 4 = Src svc number
				// 5 = Report node number
				// 6 = Report svc number
				// 7 = Custodian node number
				// svc number 0 is implied for custodian
				Utils.sdnvEncodeInt(CBHE_SCHEME_NUMBER, tempEncodeState2);
				Utils.sdnvEncodeInt(ipnDest.getHostNodeNumber(), tempEncodeState2);
				Utils.sdnvEncodeInt(ipnDest.getServiceNumber(), tempEncodeState2);
				Utils.sdnvEncodeInt(ipnSrc.getHostNodeNumber(), tempEncodeState2);
				Utils.sdnvEncodeInt(ipnSrc.getServiceNumber(), tempEncodeState2);
				Utils.sdnvEncodeInt(ipnReportTo.getHostNodeNumber(), tempEncodeState2);
				Utils.sdnvEncodeInt(ipnReportTo.getServiceNumber(), tempEncodeState2);
				Utils.sdnvEncodeInt(ipnCust.getHostNodeNumber(), tempEncodeState2);
				
			} else {
				// Strict CBHE_03 compliance
				// 0 = Dest node number
				// 1 = Dest svc number
				// 2 = Src node number
				// 3 = Src svc number
				// 4 = Report To node number
				// 5 = Report To svc number
				// 6 = Custodian node number
				// 7 = Custodian svc number
				Utils.sdnvEncodeInt(ipnDest.getHostNodeNumber(), tempEncodeState2);
				Utils.sdnvEncodeInt(ipnDest.getServiceNumber(), tempEncodeState2);
				Utils.sdnvEncodeInt(ipnSrc.getHostNodeNumber(), tempEncodeState2);
				Utils.sdnvEncodeInt(ipnSrc.getServiceNumber(), tempEncodeState2);
				Utils.sdnvEncodeInt(ipnReportTo.getHostNodeNumber(), tempEncodeState2);
				Utils.sdnvEncodeInt(ipnReportTo.getServiceNumber(), tempEncodeState2);
				Utils.sdnvEncodeInt(ipnCust.getHostNodeNumber(), tempEncodeState2);
				Utils.sdnvEncodeInt(ipnCust.getServiceNumber(), tempEncodeState2);
			}
						
		} else {
			
			// DTN Scheme
			// 0 = Src scheme dict offset
			// 1 = Src ssp dict offset
			// 2 = Dest scheme dict offset
			// 3 = Dest ssp dict offset
			// 4 = Report To scheme dict offset
			// 5 = Report To ssp dict offset
			// 6 = Custodian scheme dict offset
			// 7 = Custodian ssp dict offset
			Utils.sdnvEncodeInt(_dictionary.getRawOffset(Dictionary.DEST_SCHEME_INDEX), tempEncodeState2);
			Utils.sdnvEncodeInt(_dictionary.getRawOffset(Dictionary.DEST_SSP_INDEX), tempEncodeState2);
			Utils.sdnvEncodeInt(_dictionary.getRawOffset(Dictionary.SOURCE_SCHEME_INDEX), tempEncodeState2);
			Utils.sdnvEncodeInt(_dictionary.getRawOffset(Dictionary.SOURCE_SSP_INDEX), tempEncodeState2);
			Utils.sdnvEncodeInt(_dictionary.getRawOffset(Dictionary.REPORT_TO_SCHEME_INDEX), tempEncodeState2);
			Utils.sdnvEncodeInt(_dictionary.getRawOffset(Dictionary.REPORT_TO_SSP_INDEX), tempEncodeState2);
			Utils.sdnvEncodeInt(_dictionary.getRawOffset(Dictionary.CUSTODIAN_SCHEME_INDEX), tempEncodeState2);
			Utils.sdnvEncodeInt(_dictionary.getRawOffset(Dictionary.CUSTODIAN_SSP_INDEX), tempEncodeState2);
		}
		
		getCreationTimestamp().encode(tempEncodeState2);
		Utils.sdnvEncodeLong(getLifetime(), tempEncodeState2);
		
		// Append the temporary Dictionary encoding in tempEncodeState1 into
		// tempEncodeState2
		tempEncodeState2.append(tempEncodeState1);
		
		if (isFragment()) {
			Utils.sdnvEncodeLong(getFragmentOffset(), tempEncodeState2);
			Utils.sdnvEncodeLong(getTotalAppDataUnitLength(), tempEncodeState2);
		}
		tempEncodeState2.close();
		
		// tempEncodeState2 now encodes all fields which the BlockLength field
		// covers, so we now know the BlockLength.
		setBlockLength(tempEncodeState2.getLength());
		
		// Now we can fully encode the block
		encodeState.put(getProtocolVersion());
		Utils.sdnvEncodeInt(getBundleProcessingControlFlags(), encodeState);
		Utils.sdnvEncodeLong(getBlockLength(), encodeState);
		encodeState.append(tempEncodeState2);
		
	}
	
	/**
	 * Build the Bundle Processing Flags field for this PrimaryBundleBlock
	 * from members.
	 * @return Bundle Processing Flags field
	 */
	protected int getBundleProcessingControlFlags() {
		int result = 0;
		if (isFragment()) {
			result |= BPCF_IS_FRAGMENT;
		}
		if (isAdminRecord()) {
			result |= BPCF_IS_ADMIN_RECORD;
		}
		if (isMustNotFragment()) {
			result |= BPCF_MUST_NOT_FRAGMENT;
		}
		if (isCustodyTransferRequested()) {
			result |= BPCF_CUSTODY_XFER_RQSTED;
		}
		if (isDestEndPointSingleton()) {
			result |= BPCF_DEST_ENDPT_IS_SINGLETON;
		}
		if (isAppAckRequested()) {
			result |= BPCF_APP_ACK_RQSTD;
		}
		
		switch (_classOfServicePriority) {
		case BULK:
			result |= BPCOS_PRIORITY_BULK;
			break;
		case NORMAL:
			result |= BPCOS_PRIORITY_NORMAL;
			break;
		case EXPEDITED:
			result |= BPCOS_PRIORITY_EXPEDITED;
			break;
		}
		
		if (isReportBundleReception()) {
			result |= BPSTATUS_REPORT_BUNDLE_RECEPTION;
		}
		if (isReportCustodyAcceptance()) {
			result |= BPSTATUS_REPORT_CUSTODY_ACCEPTANCE;
		}
		if (isReportBundleForwarding()) {
			result |= BPSTATUS_REPORT_BUNDLE_FORWARDING;
		}
		if (isReportBundleDelivery()) {
			result |= BPSTATUS_REPORT_BUNDLE_DELIVERY;
		}
		if (isReportBundleDeletion()) {
			result |= BPSTATUS_REPORT_BUNDLE_DELETION;
		}
		
		return result;
	}

	/**
	 * Test the Bundle Processing Flags field for this PrimaryBundleBlock and
	 * set various members accordingly.
	 * @param bundleProcessingControlFlags The Bundle Processing Control Flags
	 * after decoding from SDNV.
	 * @throws BPException If illegal combination of flags detected
	 * */
	protected void setBundleProcessingControlFlags(int bundleProcessingControlFlags) 
	throws BPException {
		setFragment((bundleProcessingControlFlags & BPCF_IS_FRAGMENT) != 0);
		setAdminRecord((bundleProcessingControlFlags & BPCF_IS_ADMIN_RECORD) != 0);
		setMustNotFragment((bundleProcessingControlFlags & BPCF_MUST_NOT_FRAGMENT) != 0);
		setCustodyTransferRequested((bundleProcessingControlFlags & BPCF_CUSTODY_XFER_RQSTED) != 0);
		setDestEndPointSingleton((bundleProcessingControlFlags & BPCF_DEST_ENDPT_IS_SINGLETON) != 0);
		setAppAckRequested((bundleProcessingControlFlags & BPCF_APP_ACK_RQSTD) != 0);
		
		int bpCos = bundleProcessingControlFlags & BPCOS_MASK;
		switch (bpCos) {
		case BPCOS_PRIORITY_BULK:
			setClassOfServicePriority(BPClassOfServicePriority.BULK);
			break;
		case BPCOS_PRIORITY_NORMAL:
			setClassOfServicePriority(BPClassOfServicePriority.NORMAL);
			break;
		case BPCOS_PRIORITY_EXPEDITED:
			setClassOfServicePriority(BPClassOfServicePriority.EXPEDITED);
			break;
		default:
			throw new BPException("Invalid value specified in Class of Service field: 0x" +
					Integer.toHexString(bpCos));
		}
		
		setReportBundleReception((bundleProcessingControlFlags & BPSTATUS_REPORT_BUNDLE_RECEPTION) != 0);
		setReportCustodyAcceptance((bundleProcessingControlFlags & BPSTATUS_REPORT_CUSTODY_ACCEPTANCE) != 0);
		setReportBundleForwarding((bundleProcessingControlFlags & BPSTATUS_REPORT_BUNDLE_FORWARDING) != 0);
		setReportBundleDelivery((bundleProcessingControlFlags & BPSTATUS_REPORT_BUNDLE_DELIVERY) != 0);
		setReportBundleDeletion((bundleProcessingControlFlags & BPSTATUS_REPORT_BUNDLE_DELETION) != 0);
		
		if (isAdminRecord()) {
			/*
			 * 4.2 Bundle Processing Control Flags
			 *    If the bundle processing control flags indicate that the bundle's
			 *  application data unit is an administrative record, then the custody
			 *  transfer requested flag must be zero and all status report request
			 *  flags must be zero.  
			 */
			if (isCustodyTransferRequested()) {
				throw new BPException("Cannot request Custody Transfer in Admin Record");
			}
			if (isReportBundleReception() || isReportCustodyAcceptance() ||
				isReportBundleForwarding() || isReportBundleDelivery() ||
				isReportBundleDeletion()) {
				throw new BPException("Cannot request Reporting in Admin Record");
			}
		}
	}

	/**
	 * Dump this object
	 * @param indent How much to indent
	 * @param detailed Whether detailed dump desired
	 * @return the dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "PrimaryBundleBlock\n");
		sb.append(indent + "  Version=0x" + Integer.toHexString(getProtocolVersion()) + "\n");
		sb.append(indent + "  IsFragment=" + isFragment() + "\n");
		sb.append(indent + "  IsAdminRecord=" + isAdminRecord() + "\n");
		sb.append(indent + "  MustNotFragment=" + isMustNotFragment() + "\n");
		sb.append(indent + "  CustodyXferRqstd=" + isCustodyTransferRequested() + "\n");
		sb.append(indent + "  DestIsSingleton=" + isDestEndPointSingleton() + "\n");
		sb.append(indent + "  AppAckRqstd=" + isAppAckRequested() + "\n");
		
		sb.append(indent + "  ClassOfServicePriority=" + getClassOfServicePriority() + "\n");
		
		sb.append(indent + "  ReportBundleReception=" + isReportBundleReception() + "\n");
		sb.append(indent + "  ReportCustodyTransfer=" + isReportCustodyAcceptance() + "\n");
		sb.append(indent + "  ReportBundleFwding=" + isReportBundleForwarding() + "\n");
		sb.append(indent + "  ReportBundleDelivery=" + isReportBundleDelivery() + "\n");
		sb.append(indent + "  ReportBundleDeletion=" + isReportBundleDeletion() + "\n");
		
		sb.append(indent + "  Destination EID=\n");
		sb.append(getDestinationEndPointId().dump(indent + "  ", detailed));
		sb.append(indent + "  Source EID=\n");
		sb.append(getSourceEndpointId().dump(indent + "  ", detailed));
		sb.append(indent + "  ReportTo EID=\n");
		sb.append(getReportToEndPointId().dump(indent + "  ", detailed));
		sb.append(indent + "  Custodian EID=\n");
		sb.append(getCustodianEndPointId().dump(indent + "  ", detailed));
		
		sb.append(indent + "  Creation Timestamp\n");
		sb.append(_creationTimestamp.dump(indent + "    ", detailed));
		sb.append(indent + "  Lifetime (Seconds) = " + getLifetime() + "\n");
		
		if (detailed) {
			sb.append(_dictionary.dump(indent + "  ", detailed));
		}
		if (isFragment()) {
			sb.append(indent + "  Fragment Offset=" + getFragmentOffset() + "\n");
			sb.append(indent + "  Total Length=" + getTotalAppDataUnitLength() + "\n");
		}
		
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return dump("", false);
	}
	
	/** The Protocol version */
	public int getProtocolVersion() {
		return _protocolVersion;
	}

	/** The Protocol version */
	public void setProtocolVersion(int protocolVersion) {
		this._protocolVersion = protocolVersion;
	}
	
	/** If this Bundle is a Fragment */
	public boolean isFragment() {
		return _fragment;
	}

	/** If this Bundle is a Fragment */
	public void setFragment(boolean fragment) {
		this._fragment = fragment;
	}

	/** If Bundle Application Data Unit is an administrative record */
	public boolean isAdminRecord() {
		return _adminRecord;
	}

	/** If Bundle Application Data Unit is an administrative record */
	public void setAdminRecord(boolean adminRecord) {
		this._adminRecord = adminRecord;
	}

	/** If Bundle must not be fragmented */
	public boolean isMustNotFragment() {
		return _mustNotFragment;
	}

	/** If Bundle must not be fragmented */
	public void setMustNotFragment(boolean mustNotFragment) {
		this._mustNotFragment = mustNotFragment;
	}

	/** If Custody Transfer requested */
	public boolean isCustodyTransferRequested() {
		return _custodyTransferRequested;
	}

	/** If Custody Transfer requested */
	public void setCustodyTransferRequested(boolean custodyTransferRequested) {
		this._custodyTransferRequested = custodyTransferRequested;
	}

	/** If Destination EndPoint is a singleton */
	public boolean isDestEndPointSingleton() {
		return _destEndPointSingleton;
	}

	/** If Destination EndPoint is a singleton */
	public void setDestEndPointSingleton(boolean destEndPointSingleton) {
		this._destEndPointSingleton = destEndPointSingleton;
	}

	/** If application acknowledgement requested */
	public boolean isAppAckRequested() {
		return _appAckRequested;
	}

	/** If application acknowledgement requested */
	public void setAppAckRequested(boolean appAckRequested) {
		this._appAckRequested = appAckRequested;
	}

	/** Class of Service Priority */
	public BPClassOfServicePriority getClassOfServicePriority() {
		return _classOfServicePriority;
	}

	/** Class of Service Priority */
	public void setClassOfServicePriority(
			BPClassOfServicePriority classOfServicePriority) {
		this._classOfServicePriority = classOfServicePriority;
	}

	/** If request reporting of bundle reception */
	public boolean isReportBundleReception() {
		return _reportBundleReception;
	}

	/** If request reporting of bundle reception */
	public void setReportBundleReception(boolean reportBundleReception) {
		this._reportBundleReception = reportBundleReception;
	}

	/** If request reporting of custody acceptance */
	public boolean isReportCustodyAcceptance() {
		return _reportCustodyAcceptance;
	}

	/** If request reporting of custody acceptance */
	public void setReportCustodyAcceptance(boolean reportCustodyAcceptance) {
		this._reportCustodyAcceptance = reportCustodyAcceptance;
	}

	/** If request reporting of bundle forwarding */
	public boolean isReportBundleForwarding() {
		return _reportBundleForwarding;
	}

	/** If request reporting of bundle forwarding */
	public void setReportBundleForwarding(boolean reportBundleForwarding) {
		this._reportBundleForwarding = reportBundleForwarding;
	}

	/** If request reporting of bundle delivery */
	public boolean isReportBundleDelivery() {
		return _reportBundleDelivery;
	}

	/** If request reporting of bundle delivery */
	public void setReportBundleDelivery(boolean reportBundleDelivery) {
		this._reportBundleDelivery = reportBundleDelivery;
	}

	/** If request reporting of bundle deletion */
	public boolean isReportBundleDeletion() {
		return _reportBundleDeletion;
	}

	/** If request reporting of bundle deletion */
	public void setReportBundleDeletion(boolean reportBundleDeletion) {
		this._reportBundleDeletion = reportBundleDeletion;
	}

	/** The Block Length; excluding protocol version, SDNV encoded block 
	 * processing flags, and SDNV encoded Block Length */
	public long getBlockLength() {
		return _blockLength;
	}

	/** The Block Length; excluding protocol version, SDNV encoded block 
	 * processing flags, and SDNV encoded Block Length */
	public void setBlockLength(long blockLength) {
		this._blockLength = blockLength;
	}

	/** The Destination EndPointId */
	public EndPointId getDestinationEndPointId() {
		return _destinationEndPointId;
	}

	/** The Destination EndPointId 
	 * @throws BPException if EndPointId invalid */
	public void setDestinationEndPointId(EndPointId destinationEndPointId) 
	throws BPException {
		_destinationEndPointId = 
			EndPointId.createEndPointId(destinationEndPointId.getEndPointIdString());
		_dictionary.setEntry(
				Dictionary.DEST_SCHEME_INDEX, 
				-1, 
				_destinationEndPointId.getScheme());
		_dictionary.setEntry(
				Dictionary.DEST_SSP_INDEX,
				-1,
				_destinationEndPointId.getSchemeSpecificPart());
	}

	/** The Source EndPointId */
	public EndPointId getSourceEndpointId() {
		return _sourceEndpointId;
	}

	/** The Source EndPointId
	 * @throws BPException if EndPointId invalid */
	public void setSourceEndpointId(EndPointId sourceEndpointId)
	throws BPException {
		this._sourceEndpointId =
			EndPointId.createEndPointId(sourceEndpointId.getEndPointIdString());
		_dictionary.setEntry(
				Dictionary.SOURCE_SCHEME_INDEX, 
				-1,
				this._sourceEndpointId.getScheme());
		_dictionary.setEntry(
				Dictionary.SOURCE_SSP_INDEX, 
				-1,
				this._sourceEndpointId.getSchemeSpecificPart());
	}

	/** The Report-To EndPointId */
	public EndPointId getReportToEndPointId() {
		return _reportToEndPointId;
	}

	/** The Report-To EndPointId 
	 * @throws BPException if EndPointId invalid */
	public void setReportToEndPointId(EndPointId reportToEndPointId)
	throws BPException {
		this._reportToEndPointId = 
			EndPointId.createEndPointId(reportToEndPointId.getEndPointIdString());
		_dictionary.setEntry(
				Dictionary.REPORT_TO_SCHEME_INDEX, 
				-1,
				this._reportToEndPointId.getScheme());
		_dictionary.setEntry(
				Dictionary.REPORT_TO_SSP_INDEX, 
				-1,
				this._reportToEndPointId.getSchemeSpecificPart());
	}

	/** The current Custodian EndPointId */
	public EndPointId getCustodianEndPointId() {
		return _custodianEndPointId;
	}

	/** The current Custodian EndPointId 
	 * @throws BPException If EndPointId invalid */
	public void setCustodianEndPointId(EndPointId custodianEndPointId) throws BPException {
		this._custodianEndPointId =
			EndPointId.createEndPointId(custodianEndPointId.getEndPointIdString());
		_dictionary.setEntry(
				Dictionary.CUSTODIAN_SCHEME_INDEX, 
				-1,
				this._custodianEndPointId.getScheme());
		_dictionary.setEntry(
				Dictionary.CUSTODIAN_SSP_INDEX, 
				-1,
				this._custodianEndPointId.getSchemeSpecificPart());
	}

	/** Creation Timestamp */
	public Timestamp getCreationTimestamp() {
		return _creationTimestamp;
	}

	/** Creation Timestamp */
	public void setCreationTimestamp(Timestamp creationTimestamp) {
		this._creationTimestamp = creationTimestamp;
	}

	/** Lifetime, seconds since Creation Timestamp */
	public long getLifetime() {
		return _lifetime;
	}

	/** Lifetime, seconds since Creation Timestamp */
	public void setLifetime(long lifetime) {
		this._lifetime = lifetime;
	}

	/** Dictionary Length; number of bytes in raw dictionary byte array */
	public int getDictionaryLength() {
		return _dictionaryLength;
	}

	/** Dictionary Length; number of bytes in raw dictionary byte array */
	public void setDictionaryLength(int dictionaryLength) {
		this._dictionaryLength = dictionaryLength;
	}

	/** Fragment Offset (present only if IsFragment property is true) */
	public long getFragmentOffset() {
		return _fragmentOffset;
	}

	/** Fragment Offset (present only if IsFragment property is true) */
	public void setFragmentOffset(long fragmentOffset) {
		this._fragmentOffset = fragmentOffset;
	}

	/** Total Application Data Unit length
	 * (present only if IsFragment property is true) */
	public long getTotalAppDataUnitLength() {
		return _totalAppDataUnitLength;
	}

	/** Total Application Data Unit length
	 * (present only if IsFragment property is true) */
	public void setTotalAppDataUnitLength(long totalAppDataUnitLength) {
		this._totalAppDataUnitLength = totalAppDataUnitLength;
	}

	/** The Dictionary */
	public Dictionary getDictionary() {
		return _dictionary;
	}

	/** The Dictionary */
	public void setDictionary(Dictionary dictionary) {
		this._dictionary = dictionary;
	}

}
