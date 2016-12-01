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
/**
 * Licklider Transport Protocol (LTP) Classes.
 * <p/>
 * The interface between Upper Layers and LTP is encompassed by the following classes:
 * <ul>
 *   <li> LtpApi - API for down-calls from Upper Layers into LTP
 *   <li> LtpListener - Interface for callbacks from LTP back into Upper Layers
 *   <li> Block - Abstraction for a block of data passed from Upper Layers into
 *   LTP for transmission, and for a block of received data passed from LTP
 *   into Upper Layers.  A Block also encompasses the notion of a Session; a
 *   unidirectional flow of Blocks from one node to another.  All Session
 *   state is kept in the Block.  A Block contains a list of DataSegments
 *   comprising the Upper Layer data being transported.
 *   <li> BlockOptions - provides options that Upper-Layers can provide when
 *   requesting to send data over LTP.
 * </ul>
 * <p>
 * Although not apparent to Upper Layers, A Block may be one of two subclasses:
 * <ul>
 *   <li> InboundBlock - State associated with a received, or inbound, Block
 *   <li> OutboundBlock - State associated with a transmitted, or outbound Block
 * </ul>
 * <p>
 * The procedures of the LTP are embodied primarily in the following:
 * <ul>
 *   <li> LtpInbound - LTP Procedures and Session state for inbound Blocks.
 *   <li> LtpOutbound - LTP Procedures and Session state for outbound Blocks.
 * </ul>
 * <p>
 * The following comprise the management sublayer of LTP:
 * <ul>
 *   <li> Address - Abstraction of a Link address as a sequence of bytes
 *   <li> IPAddress - Specialization of Address for Internet Protocol addresses.
 *   <li> Link - Abstraction for a Datalink, or Network Interface.
 *   <li> LinkListener - Mechanism for providing callbacks on Link states.
 *   <li> LinksList - Collection of Links configured for use by LTP
 *   <li> LtpManagement - Primary LTP management interface
 *   <li> Neighbor - Abstraction for a directly reachable LTP node on a Link.
 * </ul>
 * The following classes model the protocol data units used by LTP, as defined
 * in RFC 5326:
 * <ul>
 *   <li>Segment - Abstraction for a LTP "frame"
 *   <li>CancelAckSegment - Segment which acknowledges receipt of a CancelSegment
 *   <li>CancelSegment - Segment which serves to Cancel transmission of a Block
 *   <li>DataSegment - Segment which carries Upper-Layer data between LTP nodes.
 *   <li>ReportAckSegment - Segment which acknowledges receipt of a ReportSegment
 *   <li>ReportSegment - Segment which summarizes receipt of DataSegments.
 * </ul>
 * <p>
 * These protocol data units are themselves composed of smaller pieces
 * embodied in the following classes:
 * <ul>
 *   <li>SerialNumber - A data item which serves as a serial-number-like identifier
 *   <li>CheckpointSerialNumber - A SerialNumber which identifies a "Checkpoint"
 *       DataSegment.
 *   <li>EngineId - A sequence of bytes which identifiers a particular LTP node
 *       to other LTP nodes.
 *   <li>ReceptionClaim - A data item which identies a particular subset of a
 *       DataSegment, and which asserts that said subset has been received.
 *   <li>ReportSerialNumber - A SerialNumber which identifies a particular
 *       ReportSegment.
 *   <li>SegmentExtension - An item used to extend LTP outside the scope of RFC 5326.
 *   <li>ServiceId - A sequence of bytes which identifies a particular Upper Layer.
 *   <li>SessionId - A data item which identifies an LTP Session.
 * </ul>
 */
package com.cisco.qte.jdtn.ltp;
