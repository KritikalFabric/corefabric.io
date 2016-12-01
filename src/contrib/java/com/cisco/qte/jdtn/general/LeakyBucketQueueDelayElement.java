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
package com.cisco.qte.jdtn.general;


/**
 * A type of element in a LeakyBucketQueue designed as a marker to signal the
 * need to "freeze" a LeakyBucketQueue for a specified amount of time.
 */
public class LeakyBucketQueueDelayElement implements LeakyBucketQueueElement {
	public long delayMSecs = 0;
	
	/**
	 * Construct a LeakyBucketQueueDelayElement for specified timer interval.
	 * @param aDelayMSecs Time interval to "freeze" the LeakyBucketQueue it
	 * is enqueued to, in units of mSecs.
	 */
	public LeakyBucketQueueDelayElement(long aDelayMSecs) {
		delayMSecs = aDelayMSecs;
	}
	
	/**
	 * Get the Delay property for this LeakyBucketQueueDelayElement
	 * @return Delay property, units of mSecs.
	 */
	public long getDelayMSecs() {
		return delayMSecs;
	}
	
	/**
	 * Dump this LeakyBucketQueueDelayElement
	 * @param indent Amount of indentation
	 * @param detailed whether want verbose dump
	 * @return The dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		return indent + "  LeakyBucketQueueDelayElement(delayMSecs=" + delayMSecs + ")\n";
	}
	
	@Override
	public String toString() {
		return "LeakyBucketQueueDelayElement(delayMSecs=" + delayMSecs + ")";
	}
}

