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

import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.bp.EndPointId;

/**
 * Default Ping callbacks; just outputs logging messages reporting significant
 * events in the ping process.  Suitable for shell, but not for Android.
 */
public class PingDefaultCallbacks implements PingCallbacks {
	private static final Logger _logger =
		Logger.getLogger(PingDefaultCallbacks.class.getCanonicalName());

	@Override
	public void onPingRequest(EndPointId dest, int transmitCount, int count) {
           _logger.info("PING Request:" +
            		" dest=" + dest.getEndPointIdString() + "; " + 
            		(transmitCount + 1) + "/" + count);						
	}
	
	@Override
	public void onPingReply(long rtt, int replyCount) {
        _logger.info("Ping Reply: " + rtt + " ms");			
	}

	@Override
	public void onPingReplyTimeout(int timeoutCount) {
        _logger.severe(" Reply Timeout.");			
	}

	@Override
	public void onPingDone(
			int transmitCount, 
			int receiveCount, 
			long minTime, 
			long maxTime, 
			long sumTimes) {
        _logger.info(
        		transmitCount + " pings transmitted " + 
        		receiveCount + " ping reply's received");
        _logger.info
        ("rtt min/avg/max = " + minTime + "/" + 
        		(sumTimes / receiveCount) + "/" + maxTime + " ms \n");			
	}

	@Override
	public void onNoPingReplies(EndPointId dest) {
        _logger.info("Host " + dest.getEndPointIdString() + " is unreachable");
	}

	@Override
	public void onPingException(Exception e) {
		_logger.log(Level.SEVERE, "Ping", e);
		_logger.severe("Error ignored");
	}

}
