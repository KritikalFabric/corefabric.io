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

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.TimerTask;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.bp.EidScheme;
import com.cisco.qte.jdtn.events.JDTNEvent;
import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Link;

/**
 * Superclass for Inbound and Outbound Bundles.
 * A Bundle consists of a sequence of BundleBlocks.  It contains exactly one 
 * PrimaryBundleBlock at the beginning, followed by zero or
 * more SecondaryBundleBlocks.  It may contain zero or one PayloadBundleBlocks.
 */
public class Bundle extends JDTNEvent 
implements Iterable<BundleBlock> {
	/** Retention Constraint: Bundle retained pending dispatch */
	public static final int RETENTION_CONSTRAINT_DISPATCH_PENDING = 0x01;
	/** Retention Constraint: Bundle retained while forwarding in progress */
	public static final int RETENTION_CONSTRAINT_FORWARD_PENDING = 0x02;
	/** Retention Constraint: Bundle retained because we have accepted custody */
	public static final int RETENTION_CONSTRAINT_CUSTODY_ACCEPTED = 0x04;
	/** Retention Constraint: Bundle retained while reassembly in progress */
	public static final int RETENTION_CONSTRAINT_REASSEMBLY_PENDING = 0x08;
	/** Retention Constraing: Bundle held for delay period */
	public static final int RETENTION_CONSTRAINT_DELAY_HOLD = 0x10;
	
	private static final Logger _logger =
		Logger.getLogger(Bundle.class.getCanonicalName());
	
	/** The PrimaryBundleBlock (must be present) */
	protected PrimaryBundleBlock _primaryBundleBlock = null;
	/** The PayloadBundleBlock (zero or one present) */
	protected PayloadBundleBlock _payloadBundleBlock = null;
	/** Retention Constraint; IOR of RETENTION_CONSTRAINT_XXX */
	protected int _retentionConstraint = 0;
	
	/** Bundle Options (for outbound app generated Bundles) */
	protected BundleOptions _bundleOptions;
	// List of all BundleBlocks present
	protected ArrayList<BundleBlock> _blockList =
		new ArrayList<BundleBlock>();

	/** Bundle lifetime timer */
	protected TimerTask _bundleTimerTask;
	/** Custody transfer timer */
	protected TimerTask _custodyTransferTimerTask;
	
	// Whether this is an Inbound Bundle
	private boolean _inboundBundle;
	// Adaptation Layer Data
	private Object _adaptationLayerData = null;
	// The Link on which this Bundle arrived, or to which this Bundle has been sent
	private Link _link = null;
	
	/**
	 * Construct a Bundle for an Administrative Record with the given Payload.
	 * @param sourceEid Source of Administrative Record
	 * @param destEid Destination of Administrative Record
	 * @param payload Payload of Administrative Record
	 * @param options Bundle Options
	 * @return Newly constructed Administrative Record
	 * @throws BPException on errors
	 */
	public static Bundle constructAdminRecordBundle(
			EndPointId sourceEid,
			EndPointId destEid,
			Payload payload,
			BundleOptions options) throws BPException {
		options.isAdminRecord = true;
		return new Bundle(sourceEid, destEid, payload, options);
	}
	
	/**
	 * Construct a new outbound Bundle from the given parameters and payload
	 * @param sourceEid Source EID
	 * @param destEid Destination EID
	 * @param payload Payload
	 * @param options Bundle options - May be null for all default options
	 * @throws BPException on errors
	 */
	public Bundle(
			EndPointId sourceEid,
			EndPointId destEid,
			Payload payload,
			BundleOptions options) throws BPException {

		super(EventType.OUTBOUND_BUNDLE);
		if (options == null) {
			options = new BundleOptions();
		}
		setInboundBundle(false);
		setBundleOptions(options);
		addBundleBlock(new PrimaryBundleBlock(sourceEid, destEid, options));
		PayloadBundleBlock payloadBlock = 
			new PayloadBundleBlock(payload);
		payloadBlock.setLastBlock(true);
		addBundleBlock(payloadBlock);		
	}
	
	/**
	 * Construct a new inbound Bundle from the given DecodeState, representing a
	 * Bundle received off the wire.
	 * @param decodeState Given DecodeState, containing a buffer or File to
	 * decode from, a current offset, and a length.  The current offset is
	 * updated after the operation.
	 * @param eidScheme The EidScheme to be used to decode EIDs
	 * @throws JDtnException on various decoding errors
	 */
	public Bundle(DecodeState decodeState, EidScheme eidScheme) throws JDtnException {
		super(EventType.INBOUND_BUNDLE);
		// Must have a PrimaryBundleBlock
		addBundleBlock(new PrimaryBundleBlock(this, decodeState, eidScheme));
		setInboundBundle(true);
		while (decodeState.remainingLength() > 0) {
			int blockType = decodeState.getByte();
			if (blockType == SecondaryBundleBlock.BLOCK_TYPE_PAYLOAD) {
				addBundleBlock(new PayloadBundleBlock(this, decodeState));
				
			} else {
				addBundleBlock(new SecondaryBundleBlock(this, decodeState, blockType));
			}
		}
		
		_bundleOptions = new BundleOptions();
		
		// Consistency checks on the decoded Block
		/*
		 * 4.  Bundle Format
		 *  Each bundle shall be a concatenated sequence of at least two block
		 *  structures.  The first block in the sequence must be a primary bundle
		 *  block, and no bundle may have more than one primary bundle block.
		 *  Additional bundle protocol blocks of other types may follow the
		 *  primary block to support extensions to the bundle protocol, such as
		 *  the Bundle Security Protocol [BSP].  At most one of the blocks in the
		 *  sequence may be a payload block.  The last block in the sequence must
		 *  have the "last block" flag (in its block processing control flags)
		 *  set to 1; for every other block in the bundle after the primary
		 *  block, this flag must be set to zero.
		 */
		if (_blockList.size() < 2) {
			throw new BPException("Bundle must contain at least two bundle blocks");
		}
		BundleBlock block = _blockList.get(0);
		if (!(block instanceof PrimaryBundleBlock)) {
			throw new BPException("Bundle's first block must be a PrimaryBundleBlock");
		}
		int nPrimaries = 0;
		int nPayloads = 0;
		for (int ix = 0; ix < _blockList.size(); ix++) {
			block = _blockList.get(ix);
			if (block instanceof PrimaryBundleBlock) {
				nPrimaries++;
				
			} else if (block instanceof SecondaryBundleBlock) {
				SecondaryBundleBlock secondary = (SecondaryBundleBlock)block;
				if (secondary instanceof PayloadBundleBlock) {
					nPayloads++;
				}
				if (ix == _blockList.size() - 1) {
					if (!secondary.isLastBlock()) {
						throw new BPException("Bundle's last block doesn't have Last Flag set");
					}
				} else {
					if (secondary.isLastBlock()) {
						throw new BPException("Bundle block incorrectly has Last Flag set");
					}
				}
			} else {
				throw new BPException("Bundle has unrecognizable block");
			}
		}
		if (nPrimaries > 1) {
			throw new BPException("Bundle contains > 1 PrimaryBundleBlocks");
		}
		if (nPayloads > 1) {
			throw new BPException("Bundle contains > 1 PayloadBundleBlock");
		}
	}
	
	/**
	 * Get the Dictionary for this Bundle.  All Bundles have a Dictionary.
	 * @return What I said
	 */
	public Dictionary getDictionary() {
		if (_primaryBundleBlock != null) {
			return _primaryBundleBlock.getDictionary();
		}
		_logger.severe("We have a Bundle without a PayloadBundleBlock");
		return null;
	}
	
	/** The PrimaryBundleBlock */
	public PrimaryBundleBlock getPrimaryBundleBlock() {
		return _primaryBundleBlock;
	}

	/** The PayloadBundleBlock, if any */
	public PayloadBundleBlock getPayloadBundleBlock() {
		for (BundleBlock bundleBlock : _blockList) {
			if (bundleBlock instanceof PayloadBundleBlock) {
				return (PayloadBundleBlock)bundleBlock;
			}
		}
		return null;
	}
	
	/**
	 * Add a BundleBlock to this 
	 * Bundle.
	 * @param bundleBlock the BundleBlock to add
	 * @throws BPException if constraints on components aren't met
	 * */
	public void addBundleBlock(BundleBlock bundleBlock) 
	throws BPException {
		if (_blockList.isEmpty()) {
			// BundleBlock List is empty.  We can only add a PrimaryBundleBlock.
			if (!(bundleBlock instanceof PrimaryBundleBlock)) {
				throw new BPException("Can only add PrimaryBundleBlock to empty Bundle");
			}
			_primaryBundleBlock = (PrimaryBundleBlock)bundleBlock;
			
		} else if (bundleBlock instanceof SecondaryBundleBlock) {
			SecondaryBundleBlock secondaryBundleBlock = (SecondaryBundleBlock)bundleBlock;
			if (secondaryBundleBlock.getBlockType() == SecondaryBundleBlock.BLOCK_TYPE_PAYLOAD) {
				if (_payloadBundleBlock != null) {
					throw new BPException("Bundle already contains a PayloadBundleBlock");
				}
				if (!(secondaryBundleBlock instanceof PayloadBundleBlock)) {
					throw new BPException("Given Bundle has Block Type PAYLOAD " +
							"but is not instanceof PayloadBundleBlock");
				}
				_payloadBundleBlock = (PayloadBundleBlock)secondaryBundleBlock;
			}
		} else {
			throw new IllegalStateException("Can't add more than one PrimaryBundleBlock to Bundle");
		}
		_blockList.add(bundleBlock);
	}
	
	/**
	 * Get the BundleBlock at the specified Index.
	 * @param index Specified Index
	 * @return The BundleBlock at the specified Index
	 * @throws IndexOutOfBoundsException if index is out of range
	 */
	public BundleBlock getBundleBlock(int index) {
		return _blockList.get(index);
	}
	
	/**
	 * Add a BundleBlock at specified index in BundleBlock list.  Cannot be
	 * used to add a PrimaryBundleBlock nor a PayloadBundleBlock.  Used to
	 * clone blocks into fragment Bundles.
	 * @param bundleBlock
	 * @param index
	 * @throws BPException
	 */
	public void addBundleBlock(BundleBlock bundleBlock, int index)
	throws BPException {
		if (bundleBlock instanceof PrimaryBundleBlock ||
			bundleBlock instanceof PayloadBundleBlock) {
			throw new BPException(
					"Cannot use this method to insert Primary or Payload Blocks");
		}
		_blockList.add(index, bundleBlock);
	}
	
	/**
	 * Remove a BundleBlock from this Bundle.  Caution: this action might leave
	 * the Bundle in an inconsistent state; i.e., w/out a PrimaryBundleBlock,
	 * or removing a payload accidently.
	 * @param bundleBlock The BundleBlock to remove.
	 */
	public void removeBundleBlock(BundleBlock bundleBlock) {
		_blockList.remove(bundleBlock);
	}
	
	/**
	 * Called when processing is completed on this Bundle.  This gives us the
	 * opportunity to kill it's timers.
	 */
	public void close() {
		if (getBundleTimerTask() != null) {
			getBundleTimerTask().cancel();
		}
		if (getCustodyTransferTimerTask() != null) {
			getCustodyTransferTimerTask().cancel();
		}
	}
	
	/**
	 * Get an Iterator over the BundleBlocks comprising a Bundle, suitable
	 * for use in a for each loop.
	 */
	@Override
	public Iterator<BundleBlock> iterator() {
		return _blockList.iterator();
	}

	/**
	 * Encode this Bundle to the given EncodeState
	 * @param encodeState Given EncodeState
	 * @throws JDtnException On Encoding errors
	 * @throws InterruptedException 
	 */
	public void encode(EncodeState encodeState, EidScheme eidScheme)
	throws JDtnException, InterruptedException {
		for (BundleBlock bundleBlock : this) {
			bundleBlock.encode(encodeState, eidScheme);
		}
	}
	
	private static final long Y2K_SECS_SINCE_JAN_70 = 946684800;
	
	/**
	 * Get the Expiration date/time of this Bundle as a Date object
	 * @return What I said
	 */
	public Date getExpirationDate() {
		long creationSecsY2k = getPrimaryBundleBlock().getCreationTimestamp().getTimeSecsSinceY2K();
		long lifetimeSecs = getPrimaryBundleBlock().getLifetime();
		long expireSecsY2k = creationSecsY2k + lifetimeSecs;
		long expireSecsUTC = expireSecsY2k + Y2K_SECS_SINCE_JAN_70;
		long expireMSecsUTC = expireSecsUTC * 1000L;
		Date date = new Date(expireMSecsUTC);
//		_logger.info("getExpirationDate(): now=" + df.format(new Date()));
//		_logger.info("getExpirationDate(): exp=" + df.format(date));
		return date;
	}
	
	/**
	 * Determine if this Bundle contains an Administrative Record
	 * @return What I said
	 */
	public boolean isAdminRecord() {
		return _primaryBundleBlock.isAdminRecord();
	}
	
	public static String retentionConstraintsToString(int constraints) {
		StringBuffer result = new StringBuffer();
		if ((constraints & RETENTION_CONSTRAINT_CUSTODY_ACCEPTED) != 0) {
			result.append("InCustody ");
		}
		if ((constraints & RETENTION_CONSTRAINT_DISPATCH_PENDING) != 0) {
			result.append("DispatchPending ");
		}
		if ((constraints & RETENTION_CONSTRAINT_FORWARD_PENDING) != 0) {
			result.append("ForwardPending ");
		}
		if ((constraints & RETENTION_CONSTRAINT_REASSEMBLY_PENDING) != 0) {
			result.append("ReassemblyPending ");
		}
		if ((constraints & RETENTION_CONSTRAINT_DELAY_HOLD) != 0) {
			result.append("DelayHold ");
		}
		if (result.length() == 0) {
			result.append("None");
		}
		return result.toString();
	}
	
	private static final DateFormat df = DateFormat.getDateTimeInstance();
	
	/**
	 * Dump this object
	 * @param indent How much to indent
	 * @param detailed Whether detailed dump desired
	 * @return the dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "Bundle\n");
		sb.append(indent + "  RetentionConstraints = " + 
				retentionConstraintsToString(getRetentionConstraint()) + 
				"\n");
		if (_bundleTimerTask != null) {
			Date date = new Date(_bundleTimerTask.scheduledExecutionTime());
			sb.append(indent + "  Bundle Timer: " + 
					df.format(date) + "\n");
		}
		if (_custodyTransferTimerTask != null) {
			Date date = new Date(_custodyTransferTimerTask.scheduledExecutionTime());
			sb.append(indent + "  Custody Transfer Timer=" +
					df.format(date) + "\n");
		}
		if (detailed) {
			for (BundleBlock bundleBlock : _blockList) {
				sb.append(bundleBlock.dump(indent + "  ", detailed));
			}
		}
		if (_bundleOptions != null && detailed) {
			sb.append(_bundleOptions.dump(indent + "  ", detailed));
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		return dump("", false);
	}

	/** Bundle Options */
	protected BundleOptions getBundleOptions() {
		return _bundleOptions;
	}

	/** Bundle Options */
	protected void setBundleOptions(BundleOptions bundleOptions) {
		this._bundleOptions = bundleOptions;
	}

	/** Retention Constraint; IOR of RETENTION_CONSTRAINT_XXX */
	public int getRetentionConstraint() {
		return _retentionConstraint;
	}

	/** Retention Constraint; IOR of RETENTION_CONSTRAINT_XXX */
	public void setRetentionConstraint(int retentionConstraint) {
		this._retentionConstraint = retentionConstraint;
	}

	/**
	 * Add a Retention Constraint to this Bundle
	 * @param constraint one of RETENTION_CONSTRAINT_XXX
	 */
	public void addRetentionConstraint(int constraint) {
		_retentionConstraint |= constraint;
	}
	
	/**
	 * Remove a Retention Constraint from this Bundle
	 * @param constraint one of RETENTION_CONSTRAINT_XXX
	 */
	public void removeRetentionConstraint(int constraint) {
		_retentionConstraint &= ~constraint;
	}
	
	/**
	 * Get the BundleId for this Bundle -- a unique identifier for the Bundle
	 * among all Bundles.  Note - this kind of BundleId does not work to 
	 * distinguish among all Fragment Bundles arising from a single unfragmented
	 * Bundle.
	 * @return BundleId The BundleId for this Bundle
	 */
	public BundleId getBundleId() {
		BundleId bundleId = new BundleId(
				getPrimaryBundleBlock().getSourceEndpointId(),
				getPrimaryBundleBlock().getCreationTimestamp());
		return bundleId;
	}
	
	/**
	 * Get the ExtendedBundleId for this Bundle -- a unique identifier for the
	 * Bundle among all other Bundles, including any and all fragmentsa arising
	 * from a single, unfragmented Bundle.
	 * @return ExtendedBundleId The ExtendedBundleId for this Bundle.
	 */
	public ExtendedBundleId getExtendedBundleId() {
		ExtendedBundleId ebid = new ExtendedBundleId(
				getPrimaryBundleBlock().getSourceEndpointId(),
				getPrimaryBundleBlock().getCreationTimestamp(),
				getPrimaryBundleBlock().getFragmentOffset());
		return ebid;
	}
	
	/**
	 * Determine if a Retention Constraint is set
	 * @param constraint one of RETENTION_CONSTRAINT_XXX
	 * @return True if that constraint is set in this Bundle
	 */
	public boolean isRetentionConstraint(int constraint) {
		return (_retentionConstraint & constraint) != 0;
	}

	public boolean isInboundBundle() {
		return _inboundBundle;
	}
	
	public void setInboundBundle(boolean inboundBundle) {
		this._inboundBundle = inboundBundle;
	}

	/** Anonymous data used by adaptation layer */
	public Object getAdaptationLayerData() {
		return _adaptationLayerData;
	}

	/** Anonymous data used by adaptation layer */
	public void setAdaptationLayerData(Object adaptationLayerData) {
		this._adaptationLayerData = adaptationLayerData;
	}

	/** The Link on which this Bundle arrived, or to which this Bundle has been sent */
	public Link getLink() {
		return _link;
	}

	/** The Link on which this Bundle arrived, or to which this Bundle has been sent */
	public void setLink(Link link) {
		this._link = link;
	}

	public TimerTask getBundleTimerTask() {
		return _bundleTimerTask;
	}

	public void setBundleTimerTask(TimerTask bundleTimerTask) {
		this._bundleTimerTask = bundleTimerTask;
	}

	public TimerTask getCustodyTransferTimerTask() {
		return _custodyTransferTimerTask;
	}

	public void setCustodyTransferTimerTask(TimerTask custodyTransferTimerTask) {
		this._custodyTransferTimerTask = custodyTransferTimerTask;
	}
	
}
