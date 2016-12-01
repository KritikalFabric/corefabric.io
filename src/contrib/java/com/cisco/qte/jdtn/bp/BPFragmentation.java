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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Store;
import com.cisco.qte.jdtn.general.Utils;

/**
 * Bundle Protocol Fragmentation processor.
 * All of this runs in the context of the BPProtocolAgent Thread.
 * So there is no synchronization necessary.
 * XXX Note: the BP spec says: "Note that the payloads of fragments
 * resulting from different fragmentation episodes, in different parts of the
 * network, may be overlapping subsets of the original bundle's payload."  I
 * don't see how overlapping fragments can result from this process.  So I'm
 * ignoring that issue until somebody tells me how it could happen. 
 */
public class BPFragmentation {
	private static final Logger _logger =
		Logger.getLogger(BPFragmentation.class.getCanonicalName());
	
	private static BPFragmentation _instance = null;
	
	private Map<BundleId, List<Bundle>> _reassemblyQueue =
		new HashMap<BundleId, List<Bundle>>();
	private boolean _started = false;
	
	/**
	 * Get Singleton Instance
	 * @return Singleton instance
	 */
	public static BPFragmentation getInstance() {
		if (_instance == null) {
			_instance = new BPFragmentation();
		}
		return _instance;
	}
	
	/**
	 * Private constructor
	 */
	private BPFragmentation() {
		// Nothing
	}
	
	/**
	 * Startup operation of the fragmenter
	 */
	public void startup() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("startup()");
		}
		if (!_started) {
			_started = true;
		}
	}
	
	/**
	 * Shutdown operation of the fragmenter
	 */
	public void shutdown() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("shutdown()");
		}
		if (_started) {
			_started = false;
			_reassemblyQueue.clear();
		}
	}
	
	/**
	 * Determine if given Bundle is a fragment of a Bundle in the process
	 * of reassembly.
	 * @param bundleId Subject bundle ID
	 * @return True if in process of reassembly
	 */
	public boolean isBundleInReassembly(BundleId bundleId) {
		return _reassemblyQueue.containsKey(bundleId);
	}
	
	/**
	 * Remove given Bundle from reassembly queue.
	 * Remove its reassembly constraint.
	 * @param bundleId Bundle ID to be removed
	 * @throws JDtnException 
	 * @throws SQLException 
	 */
	public void removeBundleInReassembly(BundleId bundleId) throws SQLException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("removeBundleInReassembly()");
			_logger.finer(bundleId.dump("", true));
		}
		List<Bundle> fragList = _reassemblyQueue.get(bundleId);
		if (fragList != null) {
			_reassemblyQueue.remove(bundleId);
			discardFragList(fragList);
		}
	}
	
	/**
	 * Fragment the given Bundle according to the given maximum payload
	 * length.
	 * @param origBundle The given Bundle to be fragmented
	 * @param maxPayloadLen Maximum payload length
	 * @return A List<Bundle> containing the fragments.  Returns null if
	 * given Bundle payload length doesn't exceed maxPayloadLen.
	 * @throws JDtnException on various errors
	 */
	public List<Bundle> fragmentBundle(Bundle origBundle, int maxPayloadLen) 
	throws JDtnException {
		PrimaryBundleBlock origPrimary = origBundle.getPrimaryBundleBlock();
		if (origPrimary.isMustNotFragment()) {
			throw new BPException(
				"Asked to fragment Bundle but 'must not Fragment' flag is set");
		}
		List<Bundle> fragList = new ArrayList<Bundle>();
		Payload origPayload = origBundle.getPayloadBundleBlock().getPayload();
		long origPayloadLen = origPayload.getLength();
		if (origPayloadLen <= maxPayloadLen) {
			return null;
		}
		FileInputStream fis = null;
		if (origPayload.isBodyDataInFile()) {
			try {
				fis = new FileInputStream(origPayload.getBodyDataFile());
			} catch (FileNotFoundException e) {
				throw new BPException("Opening Bundle file", e);
			}
		}
		for (long offset = 0; offset < origPayloadLen; offset += maxPayloadLen) {
			int fragPayloadLen = maxPayloadLen;
			long remainingPayload = origPayloadLen - offset;
			if (remainingPayload < maxPayloadLen) {
				fragPayloadLen = (int)(origPayloadLen - offset);
			}
			byte[] fragPayloadBuf = new byte[fragPayloadLen];
			if (fis != null) {
				// Original Payload is in a file; Copy frag portion to buffer
				try {
					int nRead = fis.read(fragPayloadBuf, 0, fragPayloadLen);
					if (nRead != fragPayloadLen) {
						throw new BPException(
								"Attempt to read " + fragPayloadLen + 
								" at offset " + offset + " failed");
					}
				} catch (IOException e) {
					try {
						fis.close();
					} catch (IOException e1) {
						// Ignore
					}
					throw new BPException("Reading Bundle file", e);
				}
				
			} else {
				// Original Payload is in buffer; Copy frag portion to buffer
				Utils.copyBytes(
						origPayload.getBodyDataBuffer(), 
						(int)offset, 
						fragPayloadBuf, 
						0, 
						fragPayloadLen);
			}
			Payload payload = new Payload(fragPayloadBuf, 0, fragPayloadLen);
			Bundle bundle = deriveBundle(
					origBundle, 
					payload, 
					offset, 
					true, 
					false, 
					origPayloadLen);
			fragList.add(bundle);
		}
		if (fis != null) {
			try {
				fis.close();
			} catch (IOException e) {
				// Ignore
			}
		}

		// RFC 5050 Section 5.8
		// "All blocks that precede the payload block must be replicated in the
		//  fragment with the lowest offset.
		//  All blocks that follow the payload block must be replicated in the
		//  fragment with the highest offset.
		//  If the 'Block must be replicated in every fragment' bit is set to
		//  1, then the block must be replicated in every fragment.
		//  If the 'Block must be replicated in every fragment' bit is set to
		//  0, then the block should be replicated in only one fragment.
		//  The relative order of all blocks that are present in a fragment
		//  must be the same as in the bundle prior to fragmentation.
		
		// Replicate blocks from original Block according to above.
		Bundle firstFragBundle = fragList.get(0);
		Bundle lastFragBundle = fragList.get(fragList.size() - 1);
		// deriveBundle creates a Bundle with two BundleBlocks: PrimaryBundleBlock
		// and PayloadBundleBlock.  So the insertion point for 'preceding'
		// Blocks is 1: after PrimaryBundleBlock but before SecondaryBundleBlock.
		int insertIndex = 1;
		boolean seenPayload = false;
		for (BundleBlock origBlock : origBundle) {
			if ((origBlock instanceof SecondaryBundleBlock)) {
				SecondaryBundleBlock origSecondary = (SecondaryBundleBlock)origBlock;
				if (origSecondary instanceof PayloadBundleBlock) {
					seenPayload = true;
					continue;
				}
				if (origSecondary.isReplicateBlockEveryFragment()) {
					//  If the 'Block must be replicated in every fragment' bit is set to
					//  1, then the block must be replicated in every fragment.
					for (Bundle fragBundle : fragList) {
						if (!seenPayload) {
							boolean inserted = false;
							int ix = 0;
							for (BundleBlock fragBlock : fragBundle) {
								if (fragBlock instanceof PayloadBundleBlock) {
									inserted = true;
									fragBundle.addBundleBlock(origSecondary, ix);
									break;
								}
								ix++;
							}
							if (!inserted) {
								fragBundle.addBundleBlock(origSecondary);
							}
						} else {
							fragBundle.addBundleBlock(origSecondary);
						}
					}
				}
				else if (!seenPayload) {
					// "All blocks that precede the payload block must be replicated in the
					//  fragment with the lowest offset.
					firstFragBundle.addBundleBlock(origBlock, insertIndex++);
				} else {
					//  All blocks that follow the payload block must be replicated in the
					//  fragment with the highest offset.
					lastFragBundle.addBundleBlock(origBlock);
				}
			}
		}
		
		return fragList;
	}
	
	/**
	 * Processing of an incoming Bundle fragment.  We add given Fragment to
	 * reassembly queue.  If the incoming fragments for this BundleId
	 * are now completely received, then reassemble and return the
	 * reassembled Bundle.
	 * @param bundle Incoming Bundle fragment
	 * @return Reassembled bundle or null if not completely reassembled
	 * @throws JDtnException on various errors
	 * @throws SQLException 
	 */
	public Bundle onIncomingFragmentedBundle(Bundle bundle)
	throws JDtnException, SQLException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("onIncomingFragmentedBundle(" + bundle.getBundleId() + ")");
			_logger.finer("Offset=" +
					bundle.getPrimaryBundleBlock().getFragmentOffset() +
					" Len=" +
					bundle.getPayloadBundleBlock().getBody().getLength());
			_logger.finest(bundle.dump("", true));
		}
		PrimaryBundleBlock primary = bundle.getPrimaryBundleBlock();
		if (!primary.isFragment()) {
			throw new BPException("Bundle doesn't have 'isFragment' bit set");
		}
		BundleId bundleId = bundle.getBundleId();
		List<Bundle> fragList = _reassemblyQueue.get(bundleId);
		if (fragList == null) {
			// New fragmented Bundle
			if (GeneralManagement.isDebugLogging()) {
				_logger.finer("New fragmented bundle");
			}
			fragList = new ArrayList<Bundle>();
			_reassemblyQueue.put(bundleId, fragList);
		}
		addFragment(fragList, bundle);
		if (isBundleFullyReassembled(fragList)) {
			return reassembleBundle(bundleId, fragList);
		}
		return null;
	}
	
	/**
	 * Add the given Bundle fragment to the given reassembly list.  We
	 * maintain the reassembly list in order of increasing fragment
	 * offset.
	 * @param fragList The reassembly list
	 * @param bundle The Bundle Fragment.
	 */
	private void addFragment(List<Bundle> fragList, Bundle bundle) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("addFragment()");
			_logger.finest(bundle.dump("", true));
		}
		PrimaryBundleBlock primary = bundle.getPrimaryBundleBlock();
		PayloadBundleBlock payloadBlock = bundle.getPayloadBundleBlock();
		Body body = payloadBlock.getBody();
		long fragOffset = primary.getFragmentOffset();
		long fragLen = body.getLength();
		int index = 0;
		boolean found = false;
		for (Bundle otherBundle : fragList) {
			PrimaryBundleBlock otherPrimary = otherBundle.getPrimaryBundleBlock();
			PayloadBundleBlock otherPayload = otherBundle.getPayloadBundleBlock();
			Body otherBody = otherPayload.getBody();
			long otherFragOffset = otherPrimary.getFragmentOffset();
			long otherFragLen = otherBody.getLength();
			if (fragOffset + fragLen < otherFragOffset + otherFragLen) {
				_logger.finer(
						"addFragment: index=" + index +
						" fragOffset = " + fragOffset +
						" fregLen= " + fragLen +
						" otherFragOffset=" + otherFragOffset +
						" otherFragLen=" + otherFragLen);
				fragList.add(index, bundle);
				found = true;
				break;
			}
			index++;
		}
		if (!found) {
			_logger.finer("addFragment: add to end");
			fragList.add(bundle);
		}
		bundle.addRetentionConstraint(
				Bundle.RETENTION_CONSTRAINT_REASSEMBLY_PENDING);
	}
	
	/**
	 * Determine if we have received all fragments for the bundle list.  All
	 * fragments in the list must be contiguous, and the accumulated length
	 * of all fragments must match the total payload length.
	 * @param fragList Given Bundle List
	 * @return True if have received all fragments.
	 */
	private boolean isBundleFullyReassembled(List<Bundle> fragList) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("isBundleFullyReassembled()");
			_logger.finest(fragList.get(0).dump("", true));
		}
		long expectedOffset = 0;
		for (Bundle bundle : fragList) {
			PrimaryBundleBlock primary = bundle.getPrimaryBundleBlock();
			PayloadBundleBlock payloadBlock = bundle.getPayloadBundleBlock();
			Body body = payloadBlock.getBody();
			long fragOffset = primary.getFragmentOffset();
			long fragLen = body.getLength();
			
			if (fragOffset != expectedOffset) {
				_logger.finer("isBundleFullyReassembled: fragOffset " + 
						fragOffset + " != expectedOffset " + expectedOffset +
						" fragLen=" + fragLen);
				return false;
			}
			expectedOffset += fragLen;
		}
		long totLen = expectedOffset;
		Bundle bundle = fragList.get(0);
		if (bundle == null) {
			_logger.finer("isBundleFullyReassembled: no bundles in fragList");
			return false;
		}
		PrimaryBundleBlock primary = bundle.getPrimaryBundleBlock();
		if (totLen != primary.getTotalAppDataUnitLength()) {
			_logger.finer("isBundleFullyReassembled: expectedOffset " + 
					expectedOffset + " != TotalAppDataUnit " + 
					primary.getTotalAppDataUnitLength());
			return false;
		}
		_logger.finer("isBundleFullyReassembled: true");
		return true;
	}
	
	/**
	 * Reassemble the given fragment list into a complete Bundle and return it.
	 * In the process, remove the fragment list from the reassembly
	 * queue and discard all fragments.
	 * @param bundleId BundleId of the fragment list
	 * @param fragList Fragment list
	 * @return Reassembled Bundle
	 * @throws JDtnException
	 * @throws SQLException 
	 */
	private Bundle reassembleBundle(BundleId bundleId, List<Bundle> fragList)
	throws JDtnException, SQLException {
		Bundle bundle = fragList.get(0);
		if (bundle == null) {
			_logger.warning("No fragments in reassembly for Bundle ID");
			_logger.warning(bundleId.dump("", true));
			return null;
		}
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("reassembleBundle()");
			_logger.finest(bundle.dump("", true));
		}
		
		_reassemblyQueue.remove(bundleId);

		File file = Store.getInstance().createBundleFile();
		Payload payload = new Payload(file, 0, 0);
		
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(file);
		} catch (IOException e) {
			discardFragList(fragList);
			throw new BPException(
					"Opening reassembly file " + file.getAbsolutePath(), e);
		}
		
		long totLen = 0;
		try {
			for (Bundle fragBundle : fragList) {
				PayloadBundleBlock fragPayload = fragBundle.getPayloadBundleBlock();
				Body body = fragPayload.getBody();
				totLen += body.getLength();
				if (body.isBodyDataInFile()) {
					Utils.copyFileToOutputStream(body.getBodyDataFile(), fos);
				} else {
					fos.write(
							body.getBodyDataBuffer(), 
							0, 
							body.getBodyDataMemLength());
				}
			}
			payload.setBodyDataFileLength(totLen);
			
		} catch (IOException e) {
			throw new BPException("Writing frag data to reassembled payload", e);
		} finally {
			discardFragList(fragList);
			try {
				fos.close();
			} catch (IOException e) {
				// Ignore
			}
		}
		
		Bundle reassembledBundle = 
			deriveBundle(bundle, payload, 0, false, true, totLen);
		
		// RFC 5050 Section 5.8
		// "All blocks that precede the payload block must be replicated in the
		//  fragment with the lowest offset.
		//  All blocks that follow the payload block must be replicated in the
		//  fragment with the highest offset.
		//  If the 'Block must be replicated in every fragment' bit is set to
		//  1, then the block must be replicated in every fragment.
		//  If the 'Block must be replicated in every fragment' bit is set to
		//  0, then the block should be replicated in only one fragment.
		//  The relative order of all blocks that are present in a fragment
		//  must be the same as in the bundle prior to fragmentation.
		// Copy all SecondaryBundleBlocks from first fragment up to Paylaod
		Bundle firstFragment = fragList.get(0);
		int index = 0;
		for (BundleBlock block : firstFragment) {
			if (block instanceof SecondaryBundleBlock) {
				SecondaryBundleBlock fragSecondary = (SecondaryBundleBlock)block;
				if (fragSecondary instanceof PayloadBundleBlock) {
					break;
				}
				reassembledBundle.addBundleBlock(fragSecondary, index);
			}
			index++;
		}
		
		// Copy all SecondaryBundleBlocks from last fragment after payload
		Bundle lastFragment = fragList.get(fragList.size() - 1);
		boolean seenPayload = false;
		for (BundleBlock block : lastFragment) {
			if (block instanceof SecondaryBundleBlock) {
				if (block instanceof PayloadBundleBlock) {
					seenPayload = true;
				} else if (seenPayload) {
					reassembledBundle.addBundleBlock(block);
				}
			}
		}
		
		return reassembledBundle;
	}

	/**
	 * Construct a new Bundle describing the given Payload.  Copy Bundle options
	 * from given Bundle.
	 * @param origPrimary Original bundle to copy options from
	 * @param newPayload Payload of the new Bundle
	 * @param offset Fragment offset
	 * @param isFragment True if new Bundle is to be a Fragment
	 * @param isInbound True if new Bundle is an Inbound Bundle
	 * @param totLen Total payload length
	 * @return
	 * @throws BPException
	 */
	private Bundle deriveBundle(
			Bundle origBundle,
			Payload newPayload, 
			long offset, 
			boolean isFragment,
			boolean isInbound,
			long totLen) throws BPException {
		PrimaryBundleBlock origPrimary = origBundle.getPrimaryBundleBlock();
		BundleOptions options = new BundleOptions(newPayload);
		options.classOfServicePriority = origPrimary.getClassOfServicePriority();
		options.custodianEndPointId = origPrimary.getCustodianEndPointId();
		options.isAdminRecord = origPrimary.isAdminRecord();
		options.isAppAckRequested = origPrimary.isAppAckRequested();
		options.isCustodyXferRqstd = origPrimary.isCustodyTransferRequested();
		options.isDestEndPointSingleton = origPrimary.isDestEndPointSingleton();
		options.isReportBundleDeletion = origPrimary.isReportBundleDeletion();
		options.isReportBundleDelivery = origPrimary.isReportBundleDelivery();
		options.isReportBundleForwarding = origPrimary.isReportBundleForwarding();
		options.isReportBundleReception = origPrimary.isReportBundleReception();
		options.isReportCustodyAcceptance = origPrimary.isReportCustodyAcceptance();
		options.mustNotFragment = origPrimary.isMustNotFragment();
		options.isFragment = isFragment;
		options.lifetime = origPrimary.getLifetime();
		options.fragmentOffset = offset;
		options.reportToEndPointId = origPrimary.getReportToEndPointId();
		options.totalAppDataUnitLength = totLen;
		options.fragmentOffset = offset;
		
		Bundle newBundle = new Bundle(
				origPrimary.getSourceEndpointId(),
				origPrimary.getDestinationEndPointId(),
				newPayload,
				options);
		newBundle.setInboundBundle(isInbound);
		newBundle.getPrimaryBundleBlock().setTotalAppDataUnitLength(totLen);
		newBundle.getPrimaryBundleBlock().setFragmentOffset(offset);
		newBundle.getPrimaryBundleBlock().setCreationTimestamp(
				origBundle.getPrimaryBundleBlock().getCreationTimestamp());

		return newBundle;
	}

	/**
	 * Discard all Bundles in the given fragmentation list
	 * @param fragList Given fragmentation list
	 * @throws JDtnException 
	 * @throws SQLException 
	 */
	private void discardFragList(List<Bundle> fragList) throws SQLException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("removeBundleInReassembly()");
			_logger.finest(fragList.get(0).dump("", true));
		}
		for (Bundle fragBundle : fragList) {
			discardBundle(fragBundle);
		}
	}

	/**
	 * Discard given Bundle
	 * @param fragBundle Given Bundle
	 * @throws JDtnException 
	 * @throws SQLException 
	 */
	private void discardBundle(Bundle fragBundle) throws SQLException, JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("removeBundleInReassembly()");
			_logger.finest(fragBundle.dump("", true));
		}
		fragBundle.removeRetentionConstraint(
				Bundle.RETENTION_CONSTRAINT_REASSEMBLY_PENDING);
		BPProtocolAgent.getInstance().discardBundleIfNoLongerConstrained(
				fragBundle);
	}
	
	/**
	 * Dump the state of this object
	 * @param indent Amount of indentation
	 * @param detailed Whether detailed dump desired
	 * @return String containing dump
	 */
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "BPFragmentation\n");
		sb.append(indent + "  Reassembly Queue: Len=" + _reassemblyQueue.size() + "\n");
		for (BundleId bundleId : _reassemblyQueue.keySet()) {
			sb.append(bundleId.dump(indent + "    ", detailed));
			List<Bundle> fragList = _reassemblyQueue.get(bundleId);
			for (Bundle bundle : fragList) {
				sb.append(bundle.dump(indent + "    ", detailed));
			}
		}
		return sb.toString();
	}
	
}
