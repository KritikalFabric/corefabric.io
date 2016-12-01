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
package com.cisco.qte.jdtn.apps;

import com.cisco.qte.jdtn.bp.EndPointId;

/**
 * Callbacks from one of the ping apps to update the app on state of a ping
 * in progress.  
 */
public interface PingCallbacks {
	/**
	 * Notification that a Ping Request is about to be sent
	 * @param dest Destination EID
	 * @param transmitCount Number of Ping transmits
	 * @param count Total number of Ping transmits planned
	 */
	public void onPingRequest(EndPointId dest, int transmitCount, int count);
	
	/**
	 * Notification that a Ping Reply has been received
	 * @param rtt Route trip time, mSecs
	 * @param replyCount Number of Ping replies seen
	 */
	public void onPingReply(long rtt, int replyCount);
	
	/**
	 * Notification that a Timeout on a Ping Request has occurred
	 * @param timeoutCount Number of Ping reply timeouts
	 */
	public void onPingReplyTimeout(int timeoutCount);
	
	/**
	 * Notification that the ping process has completed
	 * @param transmitCount Number of Ping Requests sent
	 * @param receiveCount Number of Ping Replies received
	 * @param minTime Smallest amount of time between request and reply
	 * @param maxTime Largest amount of time between request and reply
	 * @param avgTime Average amount of time between request and reply
	 */
	public void onPingDone(
			int transmitCount, 
			int receiveCount, 
			long minTime, 
			long maxTime, 
			long avgTime);
	
	/**
	 * Notification that no ping replies were received
	 * @param dest EID we were pinging
	 */
	public void onNoPingReplies(EndPointId dest);
	
	/**
	 * Notification that an exception condition occurred during ping process
	 * @param e Exception which occurred.
	 */
	public void onPingException(Exception e);

}
