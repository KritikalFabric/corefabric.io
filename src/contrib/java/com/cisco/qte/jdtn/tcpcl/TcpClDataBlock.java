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
package com.cisco.qte.jdtn.tcpcl;

import com.cisco.qte.jdtn.general.Utils;

/**
 * A small record summarizing a TCP Convergence Layer Data Block
 */
public class TcpClDataBlock {
	/** Buffer containing data in the Block */
	public byte[] buffer;
	/** Length of data in the Block */
	public long length;
	/** The Link on which the Block is to be xmitted or rcvd */
	public TcpClLink link;
	/** The Neighbor to/from which the Block is forwarded */
	public TcpClNeighbor neighbor;
	/** Upper layer opaque client data */
	public Object dataBlockId;
	
	/**
	 * Constructor
	 * @param aBuffer Buffer containing data in the block
	 * @param aLength Length of data in the Block
	 * @param aLink Link on which the block to to be xmitted or rcvd
	 * @param aNeighbor Neighbor to/from which the Block is forwarded
	 * @param aDataBlockId Upper layer opaque client data
	 */
	public TcpClDataBlock(
			byte[] aBuffer,
			long aLength,
			TcpClLink aLink,
			TcpClNeighbor aNeighbor,
			Object aDataBlockId) {
		buffer = aBuffer;
		length = aLength;
		link = aLink;
		neighbor = aNeighbor;
		dataBlockId = aDataBlockId;
	}
	
	/**
	 * Dump this object
	 * @param indent Amount of indentation
	 * @param detailed Whether detailed dump desired
	 * @return String containing the Dump
	 */
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "TcpClDataBlock\n");
		sb.append(indent + "  Length=" + length + "\n");
		sb.append(indent + "  Link=" + link.getName() + "\n");
		sb.append(indent + "  Neighbor=" + neighbor.getName() + "\n");
		sb.append(indent + "  BlockId=" + dataBlockId + "\n");
		if (detailed) {
			Utils.dumpBytes(indent + "  ", buffer, 0, (int)length);
		}
		return sb.toString();
	}
}
