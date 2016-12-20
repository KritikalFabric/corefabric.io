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

import com.cisco.qte.jdtn.apps.MediaRepository;
import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.JDtnException;

/**
 * Payload for a Bundle containing a PayloadBundleBlock.  Can be used as is
 * for a generic uninterpreted Payload containing a stream of bytes in memory
 * or in a file, such as an Application Payload.
 * <p>
 * Can also be subclassed and used as a structured Payload, as in, e.g.,
 * AdministrativeRecord.
 * <p>
 * Used as is, encapsulates the generic payload as either:
 * <ul>
 *   <li>A File, in which the following members describe the payload:
 *   <ul>
 *     <li>BodyDataFile - File containing the Payload
 *     <li>BodyDataFileOffset - Starting offset into the BodyDataFile file
 *     <li>BodyDataFileLength - Number of bytes of payload
 *   </ul>
 *   <li>An in-memory buffer, in which the following members describe the payload:
 *   <ul>
 *     <li>BodyDataBuffer - Array of bytes containing the Payload
 *     <li>BodyDataMemOffset - Strating offset into the BodyDataBuffer
 *     <li>BodyDataMemLength - Number of bytes of payload
 *   </ul>
 * </ul>
 */
public class Payload extends Body {

	public Payload() {
		// Nothing
	}
	
	public Payload(DecodeState decodeState) throws JDtnException {
		super(decodeState);
	}
	
	public Payload(MediaRepository.File file, long fileOffset, long fileLength) {
		super(true, file, fileOffset, fileLength, null, 0, 0);
	}
	
	public Payload(byte[] memBuffer, int memOffset, int memLength) {
		super(false, null, 0L, 0L, memBuffer, memOffset, memLength);
	}
	
	@Override
	public void encode(java.sql.Connection con, EncodeState encodeState) throws JDtnException, InterruptedException {
		if (isBodyDataInFile()) {
			encodeState.append(
					con,
					getBodyDataFile(),
					getBodyDataFileOffset(),
					getBodyDataFileLength());
		} else {
			encodeState.append(
					getBodyDataBuffer(), 
					getBodyDataMemOffset(), 
					getBodyDataMemLength());
		}
	}

	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "Payload\n");
		sb.append(super.dump(indent + "  ", detailed));
		return sb.toString();
	}

}
