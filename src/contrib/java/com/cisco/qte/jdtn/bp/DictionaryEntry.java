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
 * An entry in the Dictionary
 * A triple:
 * <ul>
 *   <li>Byte offset w/in original dictionary raw byte array
 *   <li>Logical offset w/in the logical array of Strings of the Dictionary
 *   <li>The String comprising the data in the Entry
 * </ul>
 */
public class DictionaryEntry {
	/** Byte offset in original raw dictionary byte array */
	public int rawOffset;
	/** Logical Offset - Index w/in array of Strings */
	public int logicalIndex;
	/** The String comprising the data in the Entry */
	public String str;
	
	/**
	 * Constructor
	 * @param aRawOffset rawOffset (-1 => unknown)
	 * @param aLogicalIndex Logical Offset (-1 => unknown)
	 * @param aStr The word data for the Entry
	 */
	public DictionaryEntry(int aRawOffset, int aLogicalIndex, String aStr) {
		this.rawOffset = aRawOffset;
		this.logicalIndex = aLogicalIndex;
		this.str = aStr;
	}

	/**
	 * Dump this object
	 * @param indent How much to indent
	 * @param detailed Whether detailed dump desired
	 * @return the dump
	 */
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "DictionaryEntry\n");
		sb.append(
				indent + 
				"  rawOffset=" + rawOffset + 
				" logicalIndex=" + logicalIndex + 
				" word=" + str + "\n");
		
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return dump("", false);
	}
	
}
