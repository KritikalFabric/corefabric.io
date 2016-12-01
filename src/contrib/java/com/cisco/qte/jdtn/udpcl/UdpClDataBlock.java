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
package com.cisco.qte.jdtn.udpcl;

import com.cisco.qte.jdtn.general.LeakyBucketQueueElement;
import com.cisco.qte.jdtn.general.Utils;

/**
 * Service Data Unit for Udp Cl API.  This serves as the basic object for
 * passing data to be transferred to and from the Udp Convergenece Layer.
 * Basically wraps a buffer and a length, plus
 * some related info.
 */
public class UdpClDataBlock implements LeakyBucketQueueElement {
	/** Buffer containing data in the Block */
	public byte[] buffer;
	/** Length of data in the Block */
	public int length;
	/** The Link on which the Block is to be xmitted or rcvd */
	public UdpClLink link;
	/** The Neighbor to/from which the Block is forwarded */
	public UdpClNeighbor neighbor;
	/** Upper layer opaque client data */
	public Object dataBlockId;
	/** Whether block is a fragment of an original bundle */
	public boolean isFragment;
	/** Whether block is last fragment of a fragmented bundle */
	public boolean isLastFragment;
	
	/**
	 * Construct a UdpClDataBlock.
	 * @param aBuffer Buffer containing data to be transferred
	 * @param aLength Length of data to transfer
	 * @param aLink link over which to transfer data
	 * @param aNeighbor Neighbor to transfer data to
	 * @param aDataBlockId Opaque data which will be carried in the UdpClDataBlock.
	 */
	public UdpClDataBlock(
			byte[] aBuffer,
			int aLength,
			UdpClLink aLink,
			UdpClNeighbor aNeighbor,
			Object aDataBlockId) {
		buffer = new byte[aLength];
		System.arraycopy(aBuffer, 0, buffer, 0, aLength);
		length = aLength;
		neighbor = aNeighbor;
		link = aLink;
		dataBlockId = aDataBlockId;
		isFragment = false;
		isLastFragment = false;
	}

	/**
	 * Dump this object
	 * @param indent Amount of indentation
	 * @param detailed Whether detailed dump desired
	 * @return String containing the Dump
	 */
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "UdpClDataBlock\n");
		sb.append(indent + "  Length=" + length + "\n");
		sb.append(indent + "  Link=" + link.getName() + "\n");
		sb.append(indent + "  Neighbor=" + neighbor.getName() + "\n");
		sb.append(indent + "  BlockId=" + dataBlockId + "\n");
		sb.append(indent + "  isFragment=" + isFragment + "\n");
		sb.append(indent + "  isLastFragment=" + isLastFragment + "\n");
		if (detailed) {
			sb.append(Utils.dumpBytes(indent + "  ", buffer, 0, (int)length));
		}
		return sb.toString();
	}
}
