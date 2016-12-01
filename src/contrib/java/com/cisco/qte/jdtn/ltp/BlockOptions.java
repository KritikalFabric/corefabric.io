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
 * Options passed to the LtpApi.send() method, indicating options that the
 * caller wants when sending a Block.
 */
public class BlockOptions {

	/**
	 * The ServiceId to which the Block is to be delivered
	 */
	public ServiceId serviceId;
	
	/**
	 * Number of Red bytes; This is the number of prefix bytes of the Block
	 * which will be transmitted reliably.  Remainder of bytes of Block are
	 * green, meaning they will be transmitted unreliably.  Can be zero,
	 * meaning all bytes are green.
	 */
	public long redLength;
	
	/**
	 * Checkpointing Options for the Block
	 */
	public enum CheckpointOption {
		/** For Outbound DataSegments, No Checkpointing at all */
		NO_CHECKPOINTING,
		/** For Outbound DataSegments, Checkpoint last red segment only */
		CHECKPOINT_LAST_ONLY,
		/** For Outbound DataSegments, Checkpoint all red segments */
		CHECKPOINT_ALL,
		/** Checkpointing determined by Inbound DataSegment type */
		DETERMINED_BY_SEGMENT_TYPE
	}
	
	/**
	 * Checkpointing option
	 */
	public CheckpointOption checkpointOption;
	
	/**
	 * Array of SegmentExtensions to be placed in segment headers.  Null or
	 * 0 length implies no header extensions desired.
	 */
	public SegmentExtension[] headerExtensions;
	
	/**
	 * Array of SegmentExtensions to be placed in segment trailers.  Null or
	 * 0 length implies no trailer extensions required.
	 */
	public SegmentExtension[] trailerExtensions;
	
	/**
	 * Default Constructor, BlockOptions consist of:
	 * <ul>
	 *   <li>ServiceId = default, meaning deliver to BP
	 *   <li>All bytes in Block Green
	 *   <li>No Checkpointing requested
	 *   <li>No Header Extensions
	 *   <li>No Trailer Extensions
	 * </ul>
	 */
	public BlockOptions() {
		serviceId = ServiceId.getDefaultServiceId();
		redLength = 0;
		checkpointOption = CheckpointOption.NO_CHECKPOINTING;
		headerExtensions = null;
		trailerExtensions = null;
	}
	
	/**
	 * Constructor for BlockOptions consisting of:
	 * <ul>
	 *   <li>ServiceId = default, meaning deliver to BP
	 *   <li>All bytes in Block Red
	 *   <li>Checkpoint last block only
	 *   <li>No Header Extensions
	 *   <li>No Trailer Extensions
	 * </ul>
	 * @param blockLength
	 */
	public BlockOptions(long blockLength) {
		serviceId = ServiceId.getDefaultServiceId();
		redLength = blockLength;
		checkpointOption = CheckpointOption.CHECKPOINT_LAST_ONLY;
		headerExtensions = null;
		trailerExtensions = null;
	}
	
	/**
	 * Constructor for BlockOptions from given Inbound DataSegment
	 * @param dataSegment Given DataSegment, from which BlockOptions
	 * HeaderExtensions and TrailerExtensions.  CheckpointOption is set to
	 * DETERMINED_BY_SEGMENT_TYPE.
	 */
	public BlockOptions(DataSegment dataSegment) {
		serviceId = dataSegment.getClientServiceId();
		if (dataSegment.isRedData()) {
			redLength = dataSegment.getClientDataLength();
		} else {
			redLength = 0;
		}
		checkpointOption = CheckpointOption.DETERMINED_BY_SEGMENT_TYPE;
		headerExtensions = dataSegment.getHeaderExtensions();
		trailerExtensions = dataSegment.getTrailerExtensions();
	}
	
	@Override
	public String toString() {
		return dump("", false);
	}
	
	/**
	 * Dump the state of this object
	 * @param indent Amount of indentation
	 * @param detailed True if want verbose details
	 * @return String containing dump
	 */
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "BlockOptions\n");
		sb.append(serviceId.dump(indent + "  ", detailed));
		sb.append(indent + "  RedLength=" + redLength + "\n");
		sb.append(indent + "  CheckpointOption=" + checkpointOption + "\n");
		if (headerExtensions != null) {
			sb.append(indent + "  HeaderExtensions\n");
			for (int ix = 0; ix < headerExtensions.length; ix++) {
				sb.append(headerExtensions[ix].dump(indent + "  ", detailed));
			}
		}
		if (trailerExtensions != null) {
			sb.append(indent + "  TrailerExtensions\n");
			for (int ix = 0; ix < trailerExtensions.length; ix++) {
				sb.append(trailerExtensions[ix].dump(indent + "  ", detailed));
			}
		}
		return sb.toString();
	}
	
}
