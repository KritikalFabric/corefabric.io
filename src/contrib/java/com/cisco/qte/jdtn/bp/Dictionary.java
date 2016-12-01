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
import java.util.HashMap;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.bp.EidScheme;
import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Utils;

/**
 * Dictionary of Strings for a Bundle (actually, attached to the PrimaryBundleBlock).
 * This is an abstraction of the idea of a Bundle Dictionary.
 * It is a collection of Strings with two indexes:
 * <ul>
 *   <li>Raw Index by "raw offset".  This is an offset into the original raw byte
 *   array comprising the Dictionary as it comes in off the wire.
 *   <li>Logical Index - An index in which all keys are constant.  Once placed
 *   into the Logical Index, then a word is always at that index.
 * </ul>
 */
public class Dictionary {
	private static final Logger _logger =
		Logger.getLogger(Dictionary.class.getCanonicalName());
	
	/** Reserved logical index for Destination Scheme */
	public static final int DEST_SCHEME_INDEX = 0;
	/** Reserved logical index for Destination SSP */
	public static final int DEST_SSP_INDEX = 1;
	/** Reserved logical index for Source Scheme */
	public static final int SOURCE_SCHEME_INDEX = 2;
	/** Reserved logical index for Source SSP */
	public static final int SOURCE_SSP_INDEX = 3;
	/** Reserved logical index for ReportTo Scheme */
	public static final int REPORT_TO_SCHEME_INDEX = 4;
	/** Reserved logical index for ReportTo SSP */
	public static final int REPORT_TO_SSP_INDEX = 5;
	/** Reserved logical index for Custodian Scheme */
	public static final int CUSTODIAN_SCHEME_INDEX = 6;
	/** Reserved logical index for Custodian SSP */
	public static final int CUSTODIAN_SSP_INDEX = 7;
	/** Reserved logical index for Destination Scheme */
	public static final int N_FIXED_INDEXES = 8;
	
	/** The set of entries in the Dictionary */
	protected ArrayList<DictionaryEntry> _entries =
		new ArrayList<DictionaryEntry>();
	/** For lookup by raw byte index */
	protected HashMap<Integer, DictionaryEntry> _rawMap =
		new HashMap<Integer, DictionaryEntry>();
	
	/**
	 * Do-nothing null constructor; used for construction of Outbound Bundle
	 */
	public Dictionary() {
		
		// Add dummy entries for all reserved entries
		int rawIndex = 0;
		for (int logicalIndex = 0; logicalIndex < N_FIXED_INDEXES; logicalIndex++) {
			setEntry(logicalIndex, rawIndex, "");
			rawIndex++;
		}
	}
	
	/**
	 * Add or replace entry in the dictionary
	 * @param logicalIndex The logical index into the Dictionary
	 * @param rawIndex The Raw Index into the Dictionary (or -1 if unknown)
	 * @param str The Dictionary entry String
	 */
	public void setEntry(int logicalIndex, int rawIndex, String str) {
		DictionaryEntry entry = new DictionaryEntry(rawIndex, logicalIndex, str);
		_rawMap.put(rawIndex, entry);
		if (logicalIndex < _entries.size()) {
			_entries.set(logicalIndex, entry);
		} else {
			for (int ix = _entries.size(); ix < logicalIndex; ix++) {
				DictionaryEntry dummyEntry = new DictionaryEntry(-1, ix, "");
				_entries.add(dummyEntry);
			}
			_entries.add(entry);
		}
	}
	
	/**
	 * Construct a new Dictionary by decoding from the given DecodeState.
	 * Used for construction of Inbound Bundle.
	 * We decode the following PrimaryBundleBlock fields:
	 * <ul>
	 *   <li>Dictionary Length
	 *   <li>Dictionary
	 * </ul>
	 * @param decodeState Given DecodeState
	 * @throws JDtnException on various decoding errors
	 */
	public Dictionary(DecodeState decodeState) throws JDtnException {
		_rawMap.clear();
		_entries.clear();
		
		// Read in raw dictionary
		int dictionaryByteLength = Utils.sdnvDecodeInt(decodeState);
		if (dictionaryByteLength > 0) {
			byte[] rawDict = decodeState.getBytes(dictionaryByteLength);
			
			// Find each null-terminated byte subsequence from raw dictionary and
			// form a String from it, add it to our collection.
			int rawOffset = 0;
			int logicalIndex = 0;
			for (int ix = 0; ix < rawDict.length; ix++) {
				if (rawDict[ix] == 0) {
					String str = new String(rawDict, rawOffset, ix - rawOffset);
					DictionaryEntry entry = new DictionaryEntry(rawOffset, logicalIndex, str);
					_entries.add(entry);
					_rawMap.put(rawOffset, entry);
					rawOffset = ix + 1;
					logicalIndex++;
				}
			}
		}
	}
	
	/**
	 * Encode this Dictionary into the given Buffer.  Call should call
	 * recomputeRawIndices() beforehand to make sure the raw offsets are correct.
	 * We encode the following PrimaryBundleBlock fields:
	 * <ul>
	 *   <li>Dictionary Length
	 *   <li>Dictionary
	 * </ul>
	 * @param encodeState Buffer to encode to
	 * @throws JDtnException  on encode errors
	 */
	public void encode(EncodeState encodeState, EidScheme eidScheme)
	throws JDtnException {
		switch (eidScheme) {
		case IPN_EID_SCHEME:
			// IPN Scheme; encode using CBHE rules:
			// Encode # entries in the Dictionary = 0
			Utils.sdnvEncodeInt(0, encodeState);
			// Dictionary itself is not encoded
			break;
			
		case DTN_EID_SCHEME:
			// Fall thru
		default:
			// DTN Scheme; encode using RFC 5050 rules:
			ArrayList<Byte> rawBytes = new ArrayList<Byte>();
			for (int logicalIndex = 0; logicalIndex < _entries.size(); logicalIndex++) {
				DictionaryEntry entry = _entries.get(logicalIndex);
				byte[] entryBytes = entry.str.getBytes();
				for (byte bite : entryBytes) {
					rawBytes.add(bite);
				}
				rawBytes.add((byte)0);
			}
			Utils.sdnvEncodeInt(rawBytes.size(), encodeState);
			encodeState.addAll(rawBytes);
		}
	}

	/**
	 * Rebuild the Dictionary.  This is in preparation for encoding.  We make
	 * sure there is a unique entry for each fixed entry in the dictionary,
	 * and preserve any entries added beyond the fixed entries.
	 * @param dest Destination EID
	 * @param src Source EID
	 * @param reportTo Report To EID
	 * @param custodian Custodian EID
	 */
	public void rebuildDictionary(
			EndPointId dest,
			EndPointId src,
			EndPointId reportTo,
			EndPointId custodian) {
		ArrayList<DictionaryEntry> newEntries = new ArrayList<DictionaryEntry>();
		
		DictionaryEntry entry = 
			new DictionaryEntry(-1, DEST_SCHEME_INDEX, dest.getScheme());
		newEntries.add(entry);
		entry =
			new DictionaryEntry(-1, DEST_SSP_INDEX, dest.getSchemeSpecificPart());
		newEntries.add(entry);
		
		entry = 
			new DictionaryEntry(-1, SOURCE_SCHEME_INDEX, src.getScheme());
		newEntries.add(entry);
		entry =
			new DictionaryEntry(-1, SOURCE_SSP_INDEX, src.getSchemeSpecificPart());
		newEntries.add(entry);
		
		entry = 
			new DictionaryEntry(-1, REPORT_TO_SCHEME_INDEX, reportTo.getScheme());
		newEntries.add(entry);
		entry =
			new DictionaryEntry(-1, REPORT_TO_SSP_INDEX, reportTo.getSchemeSpecificPart());
		newEntries.add(entry);
		
		entry = 
			new DictionaryEntry(-1, CUSTODIAN_SCHEME_INDEX, custodian.getScheme());
		newEntries.add(entry);
		entry =
			new DictionaryEntry(-1, CUSTODIAN_SSP_INDEX, custodian.getSchemeSpecificPart());
		newEntries.add(entry);
		
		// Preserve any entries added beyond the initial fixed set of 8 entries
		for (int ix = N_FIXED_INDEXES; ix < _entries.size(); ix++) {
			newEntries.add(_entries.get(ix));
		}
		
		_entries = newEntries;
		recomputeRawIndices();
	}
	
	/**
	 * In preparation for encoding, recompute all raw indices
	 */
	private void recomputeRawIndices() {
		_rawMap.clear();
		int rawIndex = 0;
		for (int logicalIndex = 0; logicalIndex < _entries.size(); logicalIndex++) {
			DictionaryEntry entry = _entries.get(logicalIndex);
			entry.rawOffset = rawIndex;
			_rawMap.put(rawIndex, entry);
			
			byte[] rawBytes = entry.str.getBytes();
			rawIndex += rawBytes.length + 1;	// + 1 for null terminator in raw dict
		}
	}
	
	/**
	 * Get the Dictionary entry tagged at the given raw offset.
	 * @param rawOffset Raw offset
	 * @return Dictionary word or null if none at that offset.
	 */
	public DictionaryEntry getRaw(int rawOffset) {
		DictionaryEntry entry = _rawMap.get(rawOffset);
		if (entry != null) {
			return entry;
		}
		_logger.severe("No entry for Raw Offset " + rawOffset);
		return null;
	}
	
	/**
	 * Get the Dictionary entry tagged at the given logical index.
	 * @param logicalIndex Given logical index
	 * @return Dictionary word found or null if none at that index.
	 */
	public DictionaryEntry get(int logicalIndex) {
		DictionaryEntry entry = _entries.get(logicalIndex);
		if (entry != null) {
			return entry;
		}
		_logger.severe("No entry for Logical Index " + logicalIndex);
		return null;
	}
	
	/**
	 * Get the Raw Offset for the word corresponding to the given logical index
	 * @param logicalIndex Given logical index
	 * @return Raw Offset
	 * @throws BPException If there is no raw index for given logical index
	 */
	public int getRawOffset(int logicalIndex) throws BPException {
		if (logicalIndex < 0 || logicalIndex >= _entries.size()) {
			_logger.info(dump("", true));
			throw new BPException(
					"Bad logical Index (" + logicalIndex + 
					") for dictionary size " + _entries.size());
		}
		DictionaryEntry entry = _entries.get(logicalIndex);
		if (entry == null) {
			throw new BPException("No entry for logical index: " + logicalIndex);
		}
		return entry.rawOffset;
	}
	
	/**
	 * Dump this object
	 * @param indent How much to indent
	 * @param detailed Whether detailed dump desired
	 * @return the dump
	 */
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "Dictionary\n");
		for (DictionaryEntry entry : _entries) {
			sb.append(entry.dump(indent + "  ", detailed));
		}
		if (detailed) {
			sb.append(indent + "  Raw Offsets: ");
			for (Integer key : _rawMap.keySet()) {
				sb.append(key + " ");
			}
			sb.append("\n");
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		return dump("", false);
	}
	
}
