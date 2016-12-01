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
 * Listener interface for Links
 */
public interface LinkListener {

	/**
	 * Notification that the Operational State for the given Link has changed.
	 * @param link Affected Link
	 * @param linkOperational New Operational State
	 */
	public void onLinkOperationalStateChange(Link link, boolean linkOperational);
	
	/**
	 * Notification that the Operational State for the given Neighbor has changed.
	 * @param neighbor Affected Neighbor
	 * @param neighborState New Operational State; true==Up, false==Down
	 */
	public void onNeighborOperationalChange(Neighbor neighbor, boolean neighborState);
	
	/**
	 * Notification that the "Scheduled", or "Planned", state for the given
	 * Neighbor has changed.
	 * @param neighbor Affected Neighbor
	 * @param neighborState New Scheduled State; true=Up, False=Down
	 * @throws InterruptedException  if interrupted
	 */
	public void onNeighborScheduledStateChange(Neighbor neighbor, boolean neighborState) 
	throws InterruptedException;
	
	/**
	 * Notification that a Neighbor has been added to the given Link
	 * @param link Given Link
	 * @param neighbor Neighbor being added
	 */
	public void onNeighborAdded(Link link, Neighbor neighbor);
	
	/**
	 * Notification that a Neighbor has been removed from the given Link
	 * @param link Given Link
	 * @param neighbor Neighbor being removed
	 */
	public void onNeighborDeleted(Link link, Neighbor neighbor);
}
