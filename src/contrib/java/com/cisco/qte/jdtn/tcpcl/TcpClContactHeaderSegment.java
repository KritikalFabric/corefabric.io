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
package com.cisco.qte.jdtn.tcpcl;

import com.cisco.qte.jdtn.bp.EndPointId;
import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.general.EncodeState;

/**
 * TCP Convergence Layer Contact Header Segment
 */
public class TcpClContactHeaderSegment extends TcpClSegment {
	public static final int VERSION_DEFAULT = 3;
	public static final int VERSION_MIN = 0;
	public static final int VERSION_MAX = 3;
	
	public static final byte[] HEADER = {
		(byte)0x64,
		(byte)0x74,
		(byte)0x6e,
		(byte)0x21
	};
	
	/** Value for 'flags' field indicating bundle acks enabled */
	public static final int FLAG_BUNDLE_ACKS = 0x01;
	/** Value for 'flag' field indicating reactive fragmentation enabled */
	public static final int FLAG_REACT_FRAG = 0x02;
	/** Value for 'flag' field indicating negative acknowledgements enabled */
	public static final int FLAG_NACKS = 0x04;
	
	/** Protocol version */
	private int _version = VERSION_DEFAULT;
	/** Protocol version */
	private int _flags = 0;
	/** Keepalive interval, seconds */
	private int _keepAliveIntervalSecs =
		TcpClNeighbor.KEEPALIVE_INTERVAL_SECS_DEFAULT;
	/** Local Endpoint ID */
	private EndPointId _endpointId = null;
	
	public TcpClContactHeaderSegment() {
		super(TcpClSegment.SegmentType.SEG_TYPE_CONTACT_HEADER);
	}

	/**
	 * Encode this for transmission on the wire
	 * @param encoded Where to encode to
	 * @throws JDtnException on errors, such as I/O errors
	 */
	@Override
	public void encode(EncodeState encoded) throws JDtnException {
		encoded.append(HEADER, 0, HEADER.length);
		encoded.put(Utils.intToByteUnsigned(getVersion()));
		encoded.put(Utils.intToByteUnsigned(getFlags()));
		byte[] intervalBytes = Utils.intToByteArray(getKeepAliveIntervalSecs(), 2);
		encoded.append(intervalBytes, 0, intervalBytes.length);
		String eidStr = _endpointId.getEndPointIdString();
		byte[] eidBytes = eidStr.getBytes();
		Utils.sdnvEncodeInt(eidBytes.length, encoded);
		encoded.append(eidBytes, 0, eidBytes.length);
	}
	
	/**
	 * Decode information from the wire about this Segment.  We have already
	 * read in the first byte of the Segment.
	 * @param byte0 First byte of segment
	 * @param decodeState Object governing decode of the remainder of the Segment
	 * @throws JDtnException on errors
	 */
	@Override
	public void decode(byte byte0, DecodeState decodeState)
	throws JDtnException {
		byte[] header = decodeState.getBytes(3);
		if (byte0 != HEADER[0] ||
			header[0] != HEADER[1] ||
			header[1] != HEADER[2] ||
			header[2] != HEADER[3]) {
			throw new JDtnException(
				String.format(
					"Invalid Contact Header 'magic' value: " +
					"0x%02x 0x%02x %02x %02x",
					byte0,
					header[0],
					header[1],
					header[2]));
		}
		int version = decodeState.getByte();
		if (version < VERSION_MIN || version > VERSION_MAX) {
			throw new JDtnException(
					"Invalid protocol version in Contact Header: " + version);
		}
		setVersion(version);
		int flags = decodeState.getByte();
		setFlags(flags);
		byte[] intervalBytes = decodeState.getBytes(2);
		int interval = Utils.byteArrayToInt(intervalBytes, 0, 2);
		setKeepAliveIntervalSecs(interval);
		int len = Utils.sdnvDecodeInt(decodeState);
		byte[] eidBytes = decodeState.getBytes(len);
		String eidStr = new String(eidBytes);
		EndPointId eid = EndPointId.createEndPointId(eidStr);
		setEndpointId(eid);
	}
	
	/**
	 * Dump this object
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "TcpClContactHeaderSegment\n");
		sb.append(super.dump(indent + "  ", detailed));
		sb.append(indent + "  Version=" + getVersion() + "\n");
		sb.append(indent + "  Flags=" + String.format("0x%02x\n", getFlags()));
		sb.append(indent + "  BundleAcks=" + isBundleAcks() + "\n");
		sb.append(indent + "  BundleNacks=" + isNacks() + "\n");
		sb.append(indent + "  ReactiveFrag=" + isReactiveFragmentation() + "\n");
		sb.append(indent + "  KeepAlive=" + getKeepAliveIntervalSecs() + " secs\n");
		sb.append(indent + "  Local EID=" + getEndpointId() + "\n");
		return sb.toString();
	}
	
	/** Protocol version */
	public int getVersion() {
		return _version;
	}

	/** Protocol version */
	public void setVersion(int version) {
		this._version = version;
	}

	/** Protocol version */
	public int getFlags() {
		return _flags;
	}

	/** Bundle Acks proposed */
	public boolean isBundleAcks() {
		return (_flags & FLAG_BUNDLE_ACKS) != 0;
	}
	
	/** Reactive fragmentation proposed */
	public boolean isReactiveFragmentation() {
		return (_flags & FLAG_REACT_FRAG) != 0;
	}
	
	/** Nacks proposed */
	public boolean isNacks() {
		return (_flags & FLAG_NACKS) != 0;
	}
	
	/** Protocol version */
	public void setFlags(int flags) {
		this._flags = flags;
	}

	/** Bundle Acks proposed */
	public void setBundleAcks(boolean bundleAcks) {
		if (bundleAcks) {
			_flags |= FLAG_BUNDLE_ACKS;
		} else {
			_flags &= ~FLAG_BUNDLE_ACKS;
		}
	}
	
	/** Reactive fragmentation proposed */
	public void setReactiveFragmentation(boolean reactFrag) {
		if (reactFrag) {
			_flags |= FLAG_REACT_FRAG;
		} else {
			_flags &= ~FLAG_REACT_FRAG;
		}
	}
	
	/** Nacks proposed */
	public void setNacks(boolean nacks) {
		if (nacks) {
			_flags |= FLAG_NACKS;
		} else {
			_flags &= ~FLAG_NACKS;
		}
	}
	
	/** Keepalive interval, seconds */
	public int getKeepAliveIntervalSecs() {
		return _keepAliveIntervalSecs;
	}

	/** Keepalive interval, seconds */
	public void setKeepAliveIntervalSecs(int keepAliveIntervalSecs) {
		this._keepAliveIntervalSecs = keepAliveIntervalSecs;
	}

	/** Local Endpoint ID */
	public EndPointId getEndpointId() {
		return _endpointId;
	}

	/** Local Endpoint ID */
	public void setEndpointId(EndPointId endpointId) {
		this._endpointId = endpointId;
	}

}
