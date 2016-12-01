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

/**
 * Callbacks to Registered Applications
 */
public interface BpListener {

	/**
	 * Notification that a Bundle in our custody had its Lifetime expired.
	 * @param bundleId BundleId of the expired Bundle
	 */
	public void onBundleLifetimeExpired(BundleId bundleId);
	
	/**
	 * Notification that a downstream node has received a Bundle
	 * @param bundleId BundleId of the Bundle
	 * @param reporter EndPointId of the node which reported the event
	 */
	public void onBundleReceivedDownstream(BundleId bundleId, EndPointId reporter);
	
	/**
	 * Notification that a downstream node has forwarded a Bundle
	 * @param bundleId BundleId of the Bundle
	 * @param reporter EndPointId of the node which reported the event
	 */
	public void onBundleForwardedDownstream(BundleId bundleId, EndPointId reporter);
	
	/**
	 * Notification that a downstream node has deleted a Bundle due to 
	 * anomalous events.
	 * @param bundleId BundleId of the Bundle
	 * @param reporter EndPointId of the node which reported the event
	 * @param reason Reason for deletion; one of BundleStatusReport.REASON_CODE_*
	 */
	public void onBundleDeletedDownstream(BundleId bundleId, EndPointId reporter, int reason);
	
	/**
	 * Notification that a downstream node has delivered a Bundle
	 * @param bundleId BundleId of the Bundle
	 * @param reporter EndPointId of the node which reported the event
	 */
	public void onBundleDeliveredDownstream(BundleId bundleId, EndPointId reporter);
	
	/**
	 * Notification that a downstream node has reported accepting custody of a Bundle
	 * @param bundleId BundleId of the Bundle
	 * @param reporter EndPointId of the node which reported the event
	 */
	public void onBundleCustodyAccepted(BundleId bundleId, EndPointId reporter);
	
	/**
	 * Notification that we relinquished custody of a Bundle
	 * @param bundleId BundleId of the Bundle
	 */
	public void onBundleCustodyReleased(BundleId bundleId);
	
	/**
	 * Notification that a Bundle enqueued for transmission was aborted
	 * because it would have exceeded our limit for bundle retention.
	 * @param bundle Affected Bundle
	 */
	public void onRetentionLimitExceeded(Bundle bundle);
	
	/**
	 * Notification that a Bundle transmission was canceled.
	 * @param bundle Bundle which was cancelled.
	 * @param reason Why cancelled, one of CancelSegment.REASON_CODE_*
	 */
	public void onBundleTransmissionCanceled(Bundle bundle, byte reason);
	
}
