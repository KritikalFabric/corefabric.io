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
 * A way we use to uniquely identify Bundles.  This is derived from two
 * pieces of information about a Bundle:
 * <ul>
 *   <li>SourceEndPointId - The Source EndPointId of the Bundle - Uniquely
 *   identifiers the source of the Bundle.
 *   <li>Timestamp - Uniquely identifies the Bundle among all from the Source
 *   EndPoint.
 * </ul>
 */
public class BundleId {
	/** The Source EndPointId of the Bundle */
	public EndPointId sourceEndPointId;
	/** The Timestamp of the Bundle */
	public Timestamp timestamp;
	
	/**
	 * Constructor - from arguments
	 * @param source Source EndPointId
	 * @param time Bundle Timestamp
	 */
	public BundleId(EndPointId source, Timestamp time) {
		this.sourceEndPointId = source;
		this.timestamp = time;
	}
	
	/**
	 * Dump this object.
	 * @param indent Amount of indent
	 * @param detailed True if want details
	 * @return Dump of object.
	 */
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "BundleId\n");
		sb.append(sourceEndPointId.dump(indent + "  Source ", detailed));
		sb.append(timestamp.dump(indent + "  ", detailed));
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return dump("", false);
	}
	
	@Override
	public boolean equals(Object thatObj) {
		if (thatObj == null || !(thatObj instanceof BundleId)) {
			return false;
		}
		BundleId that = (BundleId)thatObj;
		
		return
			this.sourceEndPointId.equals(that.sourceEndPointId) &&
			this.timestamp.equals(that.timestamp);
	}
	
	@Override
	public int hashCode() {
		return
			sourceEndPointId.hashCode() +
			timestamp.hashCode();
	}
	
}
