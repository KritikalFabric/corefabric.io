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
package com.cisco.qte.jdtn.ltp;

import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.JDtnException;

/**
 * CheckpointSerialNumber - A SerialNumber which uniquely identifies a
 * Checkpoint among all Checkpoints in a Session.
 */
public class CheckpointSerialNumber extends SerialNumber {

	/**
	 * Constructor: sets random serial number
	 */
	public CheckpointSerialNumber() {
		super();
	}
	
	/**
	 * Constructor: sets 32-bit SerialNumber from bytes of given long value.
	 * @param intVal
	 */
	public CheckpointSerialNumber(int intVal) {
		super(intVal);
	}
	
	/**
	 * Constructor: sets serial number to given byte array
	 * @param bytes Given byte array
	 */
	public CheckpointSerialNumber(byte[] bytes) {
		super(bytes);
	}
	
	/**
	 * Constructor: sets serial number to that of given CheckpointSerialNumber
	 * @param checkpointSerialNumber Given CheckpointSerialNumber
	 */
	public CheckpointSerialNumber(CheckpointSerialNumber checkpointSerialNumber) {
		super(checkpointSerialNumber);
	}
	
	/**
	 * Constructor: sets serial number by decoding given DecodeState
	 * @param decodeState Contains buffer, offset, and length of decode. After
	 * this operation, offset is updated.
	 * @throws JDtnException On decode error
	 */
	public CheckpointSerialNumber(DecodeState decodeState) throws JDtnException {
		super(decodeState);
	}
	
	@Override
	public String toString() {
		return "CheckpointSerialNumber(" + super.toString() + ")";
	}
	
	/**
	 * Dump the state of this object
	 * @param indent Amount of indentation
	 * @param detailed True if want verbose details
	 * @return String containing dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "CheckpointSerialNumber\n");
		sb.append(super.dump(indent + "  ", detailed));
		return sb.toString();
	}
	
}
