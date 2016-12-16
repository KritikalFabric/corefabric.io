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

/**
 * LtpApi Listener Interface.  These are callbacks from LtpApi back to
 * interested listeners.  In most cases, these callbacks are directed to a
 * particular ServiceId on which the listener registers.  The only exception
 * is onSystemError() which is called back to all registered listeners,
 * because we don't necessarily know a ServiceId in that case.
 */
public interface LtpListener {

	/**
	 * 7.1.  Session Start
     * The Session Start notice returns the session ID identifying a newly
   	 * created session.
   	 * At the sender, the session start notice informs the client service of
   	 * the initiation of the transmission session.  On receiving this notice
   	 * the client service may, for example, release resources of its own
   	 * that are allocated to the block being transmitted, or remember the
   	 * session ID so that the session can be canceled in the future if
   	 * necessary.  At the receiver, this notice indicates the beginning of a
   	 * new reception session, and is delivered upon arrival of the first
   	 * data segment carrying a new session ID.
	 * Notification that a session was started for the given Block.  This is
	 * either a Send Session or a Receive Session.
	 * @param block Given Block
	 */
	public void onSessionStarted(Block block);
	
	/**
	 * 7.5.  Transmission-Session Cancellation
	 *  The parameters provided by the LTP engine when a transmission-session
	 *  cancellation notice is delivered are:
	 *     Session ID of the transmission session.
	 *     The reason-code sent or received in the Cx segment that initiated
	 *     the cancellation sequence.
	 *  A transmission-session cancellation notice informs the client service
	 *  that the indicated session was terminated, either by the receiver or
	 *  else due to an error or a resource quench condition in the local LTP
	 *  engine.  There is no assurance that the destination client service
	 *  instance received any portion of the data block.
	 * Notification that the given Block was cancelled.  This block was
	 * previously enqueued for transmission.
	 * @param block Given Block
	 * @param reason Reason for cancellation
	 * @throws InterruptedException if interrupted
	 */
	public void onBlockTransmitCanceled(Block block, byte reason)
	throws InterruptedException;
	
	/**
	 * 7.4.  Transmission-Session Completion
	 *  The sole parameter provided by the LTP engine when a transmission-
	 *  session completion notice is delivered is the session ID of the
	 *  transmission session.
	 *  A transmission-session completion notice informs the client service
	 *  that all bytes of the indicated data block have been transmitted and
	 *  that the receiver has received the red-part of the block.
	 * Notification that the given Block was successfully transmitted.
	 * @param block Transmitted block
	 * @throws InterruptedException if interrupted
	 */
	public void onBlockSent(Block block) throws InterruptedException;
	
	/**
	 * 7.7.  Initial-Transmission Completion
	 *  The session ID of the transmission session is included with the
	 *  initial-transmission completion notice.
	 *  This notice informs the client service that all segments of a block
	 *  (both red-part and green-part) have been transmitted.  This notice
	 *  only indicates that original transmission is complete; retransmission
	 *  of any lost red-part data segments may still be necessary.
	 * Notification that all Segments of the given Block have been initially
	 * transmitted (but not necessarily reported on and re-sent
	 * @param block Given Block
	 */
	public void onSegmentsTransmitted(Block block);

	/**
	 * 7.6.  Reception-Session Cancellation
	 *  The parameters provided by the LTP engine when a reception
	 *  cancellation notice is delivered are:
	 *     Session ID of the transmission session.
	 *     The reason-code explaining the cancellation.
	 *  A reception-session cancellation notice informs the client service
	 *  that the indicated session was terminated, either by the sender or
	 *  else due to an error or a resource quench condition in the local LTP
	 *  engine.  No subsequent delivery notices will be issued for this
	 *  session.
	 * Notification that the given Block was cancelled.  This block was in 
	 * the process of being received
	 * @param block Given Block
	 * @param reason Reason for Cancellation
	 */
	public void onBlockReceiveCanceled(Block block, byte reason);
	
	/**
	 * Notification that the given Block was received.
	 * @param block Received Block
	 */
	public void onBlockReceived(Block block);
	
	/**
	 * 7.3.  Red-Part Reception
	 * The following parameters are provided by the LTP engine when a red-
	 *  part reception notice is delivered:
	 *     Session ID of the transmission session.
	 *     Array of client service data bytes that constitute the red-part of
	 *     the block.
	 *     Length of the red-part of the block.
	 *     Indication as to whether or not the last byte of the red-part is
	 *     also the end of the block.
	 *     StorageType LTP engine ID.
	 * Called when a checkpoint segment is received when it is known that
	 * EORP has already or coincidentally received.  We notify listeners.
	 * @param block Affected Block
	 */
	public void onRedPartReceived(Block block);

	/**
	 * 7.2.  Green-Part Segment Arrival
   	 * The following parameters are provided by the LTP engine when a green-
   	 * part segment arrival notice is delivered:
     * Session ID of the transmission session.
     * Array of client service data bytes contained in the data segment.
     * Offset of the data segment's content from the start of the block.
     * Length of the data segment's content.
     * Indication as to whether or not the last byte of this data
     * segment's content is also the end of the block.
     * StorageType LTP engine ID.
	 * Called when a Green DataSegment is received
	 * @param dataSegment Received Green DataSegment
	 */
	public void onGreenSegmentReceived(DataSegment dataSegment);
	
	/**
	 * 6.22.  Handling System Error Conditions
	 * Called when an unrecoverable error occurs.  All possible recovery actions
	 * such as cancellation of the session, have been already performed.
	 * @param description Description of the error
	 * @param e Possible Exception thrown (may be null)
	 */
	public void onSystemError(String description, Throwable e);
}
