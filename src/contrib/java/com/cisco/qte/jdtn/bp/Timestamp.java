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

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Utils;

/**
 * A Timestamp is a pair:
 * <ul>
 *   <li>A time, expressed in seconds since UTC 1/1/2000 00:00:00.0
 *   <li>A monotonically increasing serial number
 * </ul>
 */
public class Timestamp {
	private static long _lastSequenceNumber = 0;
	
	/** UTC Time, expressed in seconds since UTC 1/1/2000 00:00:00.0 */
	protected long _timeSecsSinceY2K;
	/** Monotonically increasing sequence number */
	protected long _sequenceNumber;
	
	private static final Calendar _y2kCalendar =
		Calendar.getInstance(TimeZone.getTimeZone("GMT+00:0"));
	private static Date _y2kDate;
	
	static {
		_y2kCalendar.set(Calendar.MONTH, 0);		// Javadocs say Jan == 0
		_y2kCalendar.set(Calendar.DATE, 1);			// Javadocs say first day of month  is 1
		_y2kCalendar.set(Calendar.YEAR, 2000);
		_y2kCalendar.set(Calendar.HOUR, 0);
		_y2kCalendar.set(Calendar.MINUTE, 0);
		_y2kCalendar.set(Calendar.SECOND, 0);
		_y2kCalendar.set(Calendar.AM_PM, Calendar.AM);
		_y2kCalendar.set(Calendar.MILLISECOND, 0);
		_y2kDate = _y2kCalendar.getTime();
	}
	
	/**
	 * Construct a Timestamp representing today's UTC date/time with a sequence
	 * number of zero.
	 */
	public Timestamp() {
		_timeSecsSinceY2K =
			(System.currentTimeMillis() - _y2kDate.getTime()) / 1000;
		_sequenceNumber = _lastSequenceNumber++;
	}
	
	/**
	 * Construct a Timestamp representing the given date and sequence number
	 * @param timeSecsSinceY2K Given date/time expressed as UTC Seconds since
	 * 1/1/2000 00:00:00.0
	 * @param sequenceNumber Given sequence number
	 */
	public Timestamp(long timeSecsSinceY2K, long sequenceNumber) {
		setTimeSecsSinceY2K(timeSecsSinceY2K);
		setSequenceNumber(sequenceNumber);
	}
	
	/**
	 * Copy constructor
	 * @param timestamp
	 */
	public Timestamp(Timestamp timestamp) {
		setTimeSecsSinceY2K(timestamp.getTimeSecsSinceY2K());
		setSequenceNumber(timestamp.getSequenceNumber());
	}
	
	/**
	 * Construct a Timestamp by decoding from given DecodeState.
	 * @param decodeState Given DecodeState
	 * @throws JDtnException On various decoding Errors
	 */
	public Timestamp(DecodeState decodeState) throws JDtnException {
		setTimeSecsSinceY2K(Utils.sdnvDecodeLong(decodeState));
		setSequenceNumber(Utils.sdnvDecodeLong(decodeState));
	}
	
	/**
	 * Encode this Object to the given EncodeState
	 * @param encodeState Given EncodeState
	 * @throws JDtnException On Encoding errors
	 */
	public void encode(EncodeState encodeState) throws JDtnException {
		Utils.sdnvEncodeLong(getTimeSecsSinceY2K(), encodeState);
		Utils.sdnvEncodeLong(getSequenceNumber(), encodeState);
	}
	
	/**
	 * Dump this object
	 * @param indent How much to indent
	 * @param detailed Whether detailed dump desired
	 * @return the dump
	 */
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "Timestamp\n");
		sb.append(indent + "  SecondsSinceY2K=" + getTimeSecsSinceY2K() + "\n");
		sb.append(indent + "  SequenceNumber=" + getSequenceNumber() + "\n");
		return sb.toString();
	}

	@Override
	public String toString() {
		return dump("", false);
	}
	
	@Override
	public boolean equals(Object otherObj) {
		if (otherObj == null || !(otherObj instanceof Timestamp)) {
			return false;
		}
		Timestamp other = (Timestamp)otherObj;
		return
			_timeSecsSinceY2K == other._timeSecsSinceY2K &&
			_sequenceNumber == other._sequenceNumber;
	}
	
	@Override
	public int hashCode() {
		return (int) (_timeSecsSinceY2K + _sequenceNumber);
	}
	
	/** UTC Time, expressed in seconds since UTC 1/1/2000 00:00:00.0 */
	public long getTimeSecsSinceY2K() {
		return _timeSecsSinceY2K;
	}

	/** UTC Time, expressed in seconds since UTC 1/1/2000 00:00:00.0 */
	public void setTimeSecsSinceY2K(long timeSecsSinceY2K) {
		this._timeSecsSinceY2K = timeSecsSinceY2K;
	}

	/** Monotonically increasing sequence number */
	public long getSequenceNumber() {
		return _sequenceNumber;
	}

	/** Monotonically increasing sequence number */
	public void setSequenceNumber(long sequenceNumber) {
		this._sequenceNumber = sequenceNumber;
	}
	
	/** Increment the sequence number */
	public long incrementSequenceNumber() {
		return ++_sequenceNumber;
	}
	
}
