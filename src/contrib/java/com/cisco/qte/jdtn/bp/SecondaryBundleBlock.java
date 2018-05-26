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

import java.util.ArrayList;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.apps.MediaRepository;
import com.cisco.qte.jdtn.bp.EidScheme;
import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Store;
import com.cisco.qte.jdtn.general.Utils;

/**
 * All BundleBlocks which are not PrimaryBundleBlocks
 */
public class SecondaryBundleBlock extends BundleBlock {

	@SuppressWarnings("unused")
	private static final Logger _logger =
		Logger.getLogger(SecondaryBundleBlock.class.getCanonicalName());
	
	/** Block type field: bundle payload block; others are possible but not specified */
	public static final byte BLOCK_TYPE_PAYLOAD = 1;
	
	/** Block processing control flag: Block must be replicated in every fragment */
	public static final int BKPCF_MUST_REPLICATE = 0x01;
	/** Block processing control flag: transmit status report if block can't be processed */
	public static final int BKPCF_REPORT_IF_UNPROCESSABLE = 0x02;
	/** Block processing control flag: Delete bundle if block can't be processed */
	public static final int BKPCF_DELETE_BUNDLE_IF_UNPROCESSABLE = 0x04;
	/** Block processing control flag: Last Block */
	public static final int BKPCF_LAST_BLOCK = 0x08;
	/** Block processing control flag: Discard block if can't be processed */
	public static final int BKPCF_DISCARD_BLOCK_IF_UNPROCESSABLE = 0x10;
	/** Block processing control flag: Block was forwarded w/out processing */
	public static final int BKPCF_FWDED_WOUT_PROCESSING = 0x20;
	/** Block processing control flag: Block contains EID reference */
	public static final int BKPCF_CONTAINS_EID_REF = 0x40;

	/** Block type for this Block */
	protected int _blockType = 0;
	
	/** If replicate block on every fragment */
	protected boolean _replicateBlockEveryFragment = false;
	/** If Transmit status report if block can't be processed */
	protected boolean _reportStatusIfUnprocessable = false;
	/** If delete bundle if block can't be processed */
	protected boolean _deleteBundleIfUnprocessable = false;
	/** Last Block of bundle */
	protected boolean _lastBlock = false;
	/** Discard block if block can't be processed */
	protected boolean _discardBlockIfUnprocessable = false;
	/** Block was forwarded w/out being processed */
	protected boolean _forwardedWithoutProcessing = false;
	/** Block contains an EID reference */
	protected boolean _containsEidReference = false;
	
	/** Number of EID References to dictionary */
	protected int _nEidReferences = 0;
	/** References (logical indexes) to dictionary for EID schemes */
	protected ArrayList<Integer> _schemeReferences =
		new ArrayList<Integer>();
	/** References (logical indexes to dictionary for EID SSPs */
	protected ArrayList<Integer> _sspReferences =
		new ArrayList<Integer>();
	/** The Body of the Block */
	protected Body _body;
	
	/**
	 * Do nothing constructor
	 */
	public SecondaryBundleBlock() {
		// Nothing
	}
	
	public SecondaryBundleBlock(
			int blockType,
			Body body) {
		setBlockType(blockType);
		setBody(body);
	}
	
	/**
	 * Constructor from given DecodeState.  It is assumed that the Block Type
	 * field has already been extracted.  We decode up to and including the
	 * 'block body data' field.
	 * @param bundle Bundle of which this Block is a part
	 * @param decodeState decoded block type
	 * @param blockType The BlockType of this SecondaryBundleBlock
	 * @throws JDtnException on various decode errors
	 */
	public SecondaryBundleBlock(
			Bundle bundle, 
			DecodeState decodeState, 
			int blockType)
	throws JDtnException {
		
		super(bundle, decodeState);

		final java.sql.Connection con = decodeState.con;

		setBlockType(blockType);
		decodeBlockProcessingFlags(Utils.sdnvDecodeInt(decodeState));
		if (containsEidReference()) {
			setNEidReferences(Utils.sdnvDecodeInt(decodeState));
			for (int ix = 0; ix < getNEidReferences(); ix++) {
				int rawSchemeKey = Utils.sdnvDecodeInt(decodeState);
				DictionaryEntry entry = getBundle().getDictionary().getRaw(rawSchemeKey);
				
				int logicalSchemeKey = entry.logicalIndex;
				_schemeReferences.add(logicalSchemeKey);
				
				int rawSSPKey = Utils.sdnvDecodeInt(decodeState);
				entry = getBundle().getDictionary().getRaw(rawSSPKey);
				int logicalSSPKey = entry.logicalIndex;
				_sspReferences.add(logicalSSPKey);
			}
		}
		
		long blockLength = Utils.sdnvDecodeLong(decodeState);
		
		// Now deal with the body.  We save it to a File if it is large enough,
		// else we keep it in a buffer.
		if (blockLength > BPManagement.getInstance().getBundleBlockFileThreshold()) {
			MediaRepository.File file = Store.getInstance().createNewPayloadFile();
			decodeState.spillToFile(file, blockLength);
			DecodeState decodeState2 =
				new DecodeState(
						con,
						file, 
						0, 
						blockLength);
			_body = Body.decodeBody(this, decodeState2);
			decodeState2.close();
			decodeState.close();
			
		} else {
			DecodeState decodeState2 =
				new DecodeState(
						decodeState.getBytes((int)blockLength), 
						0, 
						(int)blockLength);
			_body = Body.decodeBody(this, decodeState2);
			decodeState.close();
		}
	}
	
	/**
	 * Encode this Bundle to the given EncodeState
	 * @param encodeState Given EncodeState
	 * @param eidScheme not used
	 * @throws JDtnException On Encoding errors
	 * @throws InterruptedException 
	 */
	@Override
	public void encode(java.sql.Connection con, EncodeState encodeState, EidScheme eidScheme)
	throws JDtnException, InterruptedException {
		encodeState.put(getBlockType());
		Utils.sdnvEncodeInt(getBlockProcessingControlFlags(), encodeState);
		if (containsEidReference()) {
			Utils.sdnvEncodeInt(getNEidReferences(), encodeState);
			for (int ix = 0; ix < getNEidReferences(); ix++) {
				int logicalIndex = _schemeReferences.get(ix);
				int rawOffset = getBundle().getDictionary().getRawOffset(logicalIndex);
				Utils.sdnvEncodeInt(rawOffset, encodeState);
				
				logicalIndex = _sspReferences.get(ix);
				rawOffset = getBundle().getDictionary().getRawOffset(logicalIndex);
				Utils.sdnvEncodeInt(logicalIndex, encodeState);
			}
		}
		
		// Now we need to encode the Body Length, but we don't know that until
		// we encode the Body.  So first encode body to temporary buffer;
		// and encode length of that temporary buffer.
		EncodeState encodeState2 = null;
		if (encodeState.isEncodingToFile) {
			encodeState2 = 
				new EncodeState(con, Store.getInstance().createNewTemporaryFile());
		} else {
			encodeState2 = new EncodeState();
		}
		_body.encode(con, encodeState2);
		encodeState2.close();
		Utils.sdnvEncodeLong(encodeState2.getLength(), encodeState);

		// Append Body encoding
		encodeState.append(encodeState2);
		
		encodeState2.delete();
	}
	
	/**
	 * Decode the Block Processing Flags
	 * @param blockProcessingFlags after SDNV decoding
	 */
	protected void decodeBlockProcessingFlags(int blockProcessingFlags) {
		if ((blockProcessingFlags & BKPCF_MUST_REPLICATE) != 0) {
			setReplicateBlockEveryFragment(true);
		}
		if ((blockProcessingFlags & BKPCF_REPORT_IF_UNPROCESSABLE) != 0) {
			setReportStatusIfUnprocessable(true);
		}
		if ((blockProcessingFlags & BKPCF_DELETE_BUNDLE_IF_UNPROCESSABLE) != 0) {
			setDeleteBundleIfUnprocessable(true);
		}
		if ((blockProcessingFlags & BKPCF_LAST_BLOCK) != 0) {
			setLastBlock(true);
		}
		if ((blockProcessingFlags & BKPCF_DISCARD_BLOCK_IF_UNPROCESSABLE) != 0) {
			setDiscardBlockIfUnprocessable(true);
		}
		if ((blockProcessingFlags & BKPCF_FWDED_WOUT_PROCESSING) != 0) {
			setForwardedWithoutProcessing(true);
		}
		if ((blockProcessingFlags & BKPCF_CONTAINS_EID_REF) != 0) {
			setContainsEidReference(true);
		}
	}
	
	/** Block processing control flags; after SDNV decoding */
	public int getBlockProcessingControlFlags() {
		int result = 0;
		if (isReplicateBlockEveryFragment()) {
			result |= BKPCF_MUST_REPLICATE;
		}
		if (isReportStatusIfUnprocessable()) {
			result |= BKPCF_REPORT_IF_UNPROCESSABLE;
		}
		if (isDeleteBundleIfUnprocessable()) {
			result |= BKPCF_DELETE_BUNDLE_IF_UNPROCESSABLE;
		}
		if (isLastBlock()) {
			result |= BKPCF_LAST_BLOCK;
		}
		if (isDiscardBlockIfUnprocessable()) {
			result |= BKPCF_DISCARD_BLOCK_IF_UNPROCESSABLE;
		}
		if (isForwardedWithoutProcessing()) {
			result |= BKPCF_FWDED_WOUT_PROCESSING;
		}
		if (containsEidReference()) {
			result |= BKPCF_CONTAINS_EID_REF;
		}
		return result;
	}
	
	/**
	 * Dump this object
	 * @param indent How much to indent
	 * @param detailed Whether detailed dump desired
	 * @return the dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "SecondaryBundleBlock\n");
		sb.append(super.dump(indent + "  ", detailed));
		switch (getBlockType()) {
		case BLOCK_TYPE_PAYLOAD:
			sb.append(indent + "  BlockType=PAYLOAD\n");
			break;
		default:
			sb.append(indent + "  BlockType=" + getBlockType() + "\n");
			break;
		}
		sb.append(indent + "  ReplicateInEveryFragment=" + isReplicateBlockEveryFragment() + "\n");
		sb.append(indent + "  ReportIfCantProcess=" + isReportStatusIfUnprocessable() + "\n");
		sb.append(indent + "  DeleteBundleIfCantProcess=" + isDeleteBundleIfUnprocessable() + "\n");
		sb.append(indent + "  LastBlock=" + isLastBlock() + "\n");
		sb.append(indent + "  DiscardBlockIfCantProcess=" + isDiscardBlockIfUnprocessable() + "\n");
		sb.append(indent + "  FwdedWoutProcessing=" + isForwardedWithoutProcessing() + "\n");
		sb.append(indent + "  ContainsEIDReference=" + _containsEidReference + "\n");
		if (containsEidReference() && detailed) {
			for (int ix = 0; ix < getNEidReferences(); ix++) {
				int schemeLogicalIndex = getSchemeReferences().get(ix);
				DictionaryEntry schemeEntry = getBundle().getDictionary().get(schemeLogicalIndex);
				String scheme = schemeEntry.str;
				
				int sspLogicalIndex = getSspReferences().get(ix);
				DictionaryEntry sspEntry = getBundle().getDictionary().get(sspLogicalIndex);
				String ssp = sspEntry.str;
				sb.append(indent + "    EID Reference " + scheme + ":" + ssp + "\n");
			}
		}
		sb.append(_body.dump(indent + "  ", detailed));
		return sb.toString();
	}

	@Override
	public String toString() {
		return dump("", false);
	}
	
	/** If replicate block on every fragment */
	public boolean isReplicateBlockEveryFragment() {
		return _replicateBlockEveryFragment;
	}
	/** If replicate block on every fragment */
	public void setReplicateBlockEveryFragment(boolean replicateBlockEveryFragment) {
		this._replicateBlockEveryFragment = replicateBlockEveryFragment;
	}
	/** If Transmit status report if block can't be processed */
	public boolean isReportStatusIfUnprocessable() {
		return _reportStatusIfUnprocessable;
	}
	/** If Transmit status report if block can't be processed */
	public void setReportStatusIfUnprocessable(boolean reportStatusIfUnprocessable) {
		this._reportStatusIfUnprocessable = reportStatusIfUnprocessable;
	}
	/** If delete bundle if block can't be processed */
	public boolean isDeleteBundleIfUnprocessable() {
		return _deleteBundleIfUnprocessable;
	}
	/** If delete bundle if block can't be processed */
	public void setDeleteBundleIfUnprocessable(boolean deleteBundleIfUnprocessable) {
		this._deleteBundleIfUnprocessable = deleteBundleIfUnprocessable;
	}
	/** Last Block of bundle */
	public boolean isLastBlock() {
		return _lastBlock;
	}
	/** Last Block of bundle */
	public void setLastBlock(boolean lastBlock) {
		this._lastBlock = lastBlock;
	}
	/** Discard block if block can't be processed */
	public boolean isDiscardBlockIfUnprocessable() {
		return _discardBlockIfUnprocessable;
	}
	/** Discard block if block can't be processed */
	public void setDiscardBlockIfUnprocessable(boolean discardBlockIfUnprocessable) {
		this._discardBlockIfUnprocessable = discardBlockIfUnprocessable;
	}
	/** Block was forwarded w/out being processed */
	public boolean isForwardedWithoutProcessing() {
		return _forwardedWithoutProcessing;
	}
	/** Block was forwarded w/out being processed */
	public void setForwardedWithoutProcessing(boolean forwardedWithoutProcessing) {
		this._forwardedWithoutProcessing = forwardedWithoutProcessing;
	}
	/** Block contains an EID reference */
	public boolean containsEidReference() {
		return _containsEidReference;
	}
	/** Block contains an EID reference */
	public void setContainsEidReference(boolean containsEidReference) {
		this._containsEidReference = containsEidReference;
	}

	/** Block type for this Block */
	public int getBlockType() {
		return _blockType;
	}

	/** Block type for this Block */
	public void setBlockType(int blockType) {
		this._blockType = blockType;
	}

	/** Number of EID References to dictionary */
	public int getNEidReferences() {
		return _nEidReferences;
	}

	/** Number of EID References to dictionary */
	public void setNEidReferences(int nEidReferences) {
		this._nEidReferences = nEidReferences;
	}

	/** References (logical indexes) to dictionary for EID schemes */
	public ArrayList<Integer> getSchemeReferences() {
		return _schemeReferences;
	}

	/** References (logical indexes) to dictionary for EID SSPs */
	public ArrayList<Integer> getSspReferences() {
		return _sspReferences;
	}

	public int getnEidReferences() {
		return _nEidReferences;
	}

	protected void setnEidReferences(int nEidReferences) {
		this._nEidReferences = nEidReferences;
	}

	protected void setSchemeReferences(ArrayList<Integer> schemeReferences) {
		this._schemeReferences = schemeReferences;
	}

	protected void setSspReferences(ArrayList<Integer> sspReferences) {
		this._sspReferences = sspReferences;
	}

	public Body getBody() {
		return _body;
	}
	
	protected void setBody(Body body) {
		this._body = body;
	}
	
}
