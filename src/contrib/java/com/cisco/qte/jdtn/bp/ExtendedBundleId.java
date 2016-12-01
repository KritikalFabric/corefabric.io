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
 * An 'extended' BundleId which includes a 'fragment Offset' field in addition to
 * the fields defined for BundleId.  This is used to uniquely distinguish
 * Bundle fragments, which share a common BundleId.
 */
public class ExtendedBundleId extends BundleId {
	/** The fragment Offset portion of the ExtendedBundleId */
	public long fragOffset = 0L;
	
	/**
	 * Constructor - From Arguments - fragOffset field set to zero.
	 * @param aSource source EndPointId of Bundle
	 * @param aTime Timestamp of Bundle
	 */
	public ExtendedBundleId(EndPointId aSource, Timestamp aTime) {
		super(aSource, aTime);
		fragOffset = 0;
	}
	
	/**
	 * Constructor - From Arguments
	 * @param aSource source EndPointId of Bundle
	 * @param aTime Timestamp of Bundle
	 * @param aFragOffset - Fragment Offset 
	 */
	public ExtendedBundleId(EndPointId aSource, Timestamp aTime, long aFragOffset) {
		super(aSource, aTime);
		fragOffset = aFragOffset;
	}

	/**
	 * Get the 'regular' BundleId of which this is an extension
	 * @return What I said
	 */
	public BundleId getBundleId() {
		return new BundleId(super.sourceEndPointId, super.timestamp);
	}
	
	/**
	 * Get the 'fragment offset' portion of the extended bundle ID.
	 * @return Fragment offset
	 */
	public long getFragOffset() {
		return fragOffset;
	}
	
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "ExtendedBundleId\n");
		sb.append(super.dump(indent + "  ", detailed));
		sb.append(indent + "  fragOffset=" + fragOffset + "\n");
		return sb.toString();
	}

	@Override
	public boolean equals(Object thatObj) {
		if (super.equals(thatObj) && thatObj instanceof ExtendedBundleId) {
			// super.equals ensures thatObj != null
			ExtendedBundleId that = (ExtendedBundleId)thatObj;
			return fragOffset ==  that.fragOffset;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return super.hashCode() + (int)fragOffset;
	}

	@Override
	public String toString() {
		return dump("", false);
	}

}
