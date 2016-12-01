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

/**
 * TCP Convergence Layer Statistics
 */
public class TcpClStats {
	/** Number of blocks sent */
	public long nBlocksSent = 0;
	/** Number of blocks enqueued for transmission but failed due to errors */
	public long nBlocksSentErrors = 0;
	/** Number of blocks received */
	public long nBlocksRcvd = 0;
	/** Number of TcpCl segments sent */
	public long nSegmentsSent = 0;
	/** Number of TcpCl segments received */
	public long nSegmentsRcvd = 0;
	/** Number of TcpCl Data Segments sent */
	public long nDataSegmentsSent = 0;
	/** Number of TcpCl Data Segment received */
	public long nDataSegmentsRcvd = 0;
	/** Number of Ack Segments sent */
	public long nAckSegmentsSent = 0;
	/** Number of Ack Segments received */
	public long nAckSegmentsRcvd = 0;
	/** Number of Nack Segments sent */
	public long nNackSegmentsSent = 0;
	/** Number of Nack Segments received */
	public long nNackSegmentsRcvd = 0;
	/** Number of Contact Headers sent */
	public long nContactHdrsSent = 0;
	/** Number of Contact Headers received */
	public long nContactHdrsRcvd = 0;
	/** Number of KeepAlive Segments sent */
	public long nKeepAlivesSent = 0;
	/** Number of KeepAlive Segments received */
	public long nKeepAlivesRcvd = 0;
	/** Number of Shutdown segments sent */
	public long nShutdownsSent = 0;
	/** Number of Shutdown segments received */
	public long nShutdownsRcvd = 0;
	/** Number of Keepalive Timer Expirations */
	public long nKeepaliveExpires = 0;
	/** Number of Idle Timer expirations */
	public long nIdleExpires = 0;
	/** Number of Reconnect Timer expirations */
	public long nReconnectExpires = 0;
	/** Number of TCP Connects */
	public long nConnects = 0;
	/** Number of TCP Accepts */
	public long nAccepts = 0;
	/** Number of TCP disconnects */
	public long nDisconnects = 0;
	
	/**
	 * Clear TcpClStatistics
	 */
	public void clear() {
		nBlocksSent = 0;
		nBlocksSentErrors = 0;
		nBlocksRcvd = 0;
		nSegmentsSent = 0;
		nSegmentsRcvd = 0;
		nDataSegmentsSent = 0;
		nDataSegmentsRcvd = 0;
		nAckSegmentsSent = 0;
		nAckSegmentsRcvd = 0;
		nNackSegmentsSent = 0;
		nNackSegmentsRcvd = 0;
		nContactHdrsSent = 0;
		nContactHdrsRcvd = 0;
		nKeepAlivesSent = 0;
		nKeepAlivesRcvd = 0;
		nShutdownsSent = 0;
		nShutdownsRcvd = 0;
		nKeepaliveExpires = 0;
		nIdleExpires = 0;
		nReconnectExpires = 0;
		nConnects = 0;
		nAccepts = 0;
		nDisconnects = 0;
	}
	
	/**
	 * Dump Statistics
	 * @param indent How much to indent
	 * @param detailed Not used
	 */
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "TcpCl Statistics\n");
		sb.append(indent + "    nBlocksSent=" + nBlocksSent + "\n");
		sb.append(indent + "    nBlocksSentErrors=" + nBlocksSentErrors + "\n");
		sb.append(indent + "    nBlocksRcvd=" + nBlocksRcvd + "\n");
		sb.append(indent + "    nSegmentsSent=" + nSegmentsSent + "\n");
		sb.append(indent + "    nSegmentsRcvd=" + nSegmentsRcvd + "\n");
		sb.append(indent + "    nDataSegmentsSent=" + nDataSegmentsSent + "\n");
		sb.append(indent + "    nDataSegmentsRcvd=" + nDataSegmentsRcvd + "\n");
		sb.append(indent + "    nAckSegmentsSent=" + nAckSegmentsSent + "\n");
		sb.append(indent + "    nAckSegmentsRcvd=" + nAckSegmentsRcvd + "\n");
		sb.append(indent + "    nNackSegmentsSent=" + nNackSegmentsSent + "\n");
		sb.append(indent + "    nNackSegmentsRcvd=" + nNackSegmentsRcvd + "\n");
		sb.append(indent + "    nContactHdrsSent=" + nContactHdrsSent + "\n");
		sb.append(indent + "    nContactHdrsRcvd=" + nContactHdrsRcvd + "\n");
		sb.append(indent + "    nKeepAlivesSent=" + nKeepAlivesSent + "\n");
		sb.append(indent + "    nKeepAlivesRcvd=" + nKeepAlivesRcvd + "\n");
		sb.append(indent + "    nShutdownsSent=" + nShutdownsSent + "\n");
		sb.append(indent + "    nShutdownsRcvd=" + nShutdownsRcvd + "\n");
		sb.append(indent + "    nKeepaliveExpires=" + nKeepaliveExpires + "\n");
		sb.append(indent + "    nIdleExpires=" + nIdleExpires + "\n");
		sb.append(indent + "    nReconnectExpires=" + nReconnectExpires + "\n");
		sb.append(indent + "    nConnects=" + nConnects + "\n");
		sb.append(indent + "    nAccepts=" + nAccepts + "\n");
		sb.append(indent + "    nDisconnects=" + nDisconnects + "\n");
		return sb.toString();
	}
	
}
