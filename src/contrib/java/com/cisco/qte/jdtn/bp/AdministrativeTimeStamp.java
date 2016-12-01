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
 * Timestamp used in all AdministrativeRecords, consisting of:
 * <ul>
 *   <li>UTC Seconds since UTC 01/01/2000 00:00:00
 *   <li>Nanoseconds within second
 * </ul>
 */
public class AdministrativeTimeStamp {
	private static final long MSECS_PER_SEC = 1000L;
	private static final int NSECS_PER_MSEC = 1000 * 1000;
	
	/** UTC Seconds since UTC 01/01/2000 00:00:00 */
	protected long _utcSecsSinceY2K = 0;
	
	/** Nanoseconds within second */
	protected int _nanoSeconds = 0;

	private static final Calendar _y2kCalendar =
		Calendar.getInstance(TimeZone.getTimeZone("GMT+00:0"));
	private static Date _y2kDate;
	
	static {
		_y2kCalendar.set(Calendar.DAY_OF_MONTH, 1);
		_y2kCalendar.set(Calendar.DATE, 1);
		_y2kCalendar.set(Calendar.YEAR, 2000);
		_y2kCalendar.set(Calendar.HOUR, 0);
		_y2kCalendar.set(Calendar.MINUTE, 0);
		_y2kCalendar.set(Calendar.SECOND, 0);
		_y2kCalendar.set(Calendar.AM_PM, Calendar.AM);
		_y2kCalendar.set(Calendar.MILLISECOND, 0);
		_y2kDate = _y2kCalendar.getTime();
	}
	
	/**
	 * Construct AdministrativeTimeStamp representing current time
	 */
	public AdministrativeTimeStamp() {
		long mSecs = System.currentTimeMillis() - _y2kDate.getTime();
		_utcSecsSinceY2K = mSecs / MSECS_PER_SEC;
		mSecs = (mSecs % MSECS_PER_SEC);
		_nanoSeconds = (int)(mSecs * NSECS_PER_MSEC);
	}
	
	/**
	 * Construct AdministrativeTimeStamp by decoding from given buffer
	 * @param decodeState Given decode buffer
	 * @throws JDtnException on decoding errors
	 */
	public AdministrativeTimeStamp(DecodeState decodeState) 
	throws JDtnException {
		setUtcSecsSinceY2K(Utils.sdnvDecodeLong(decodeState));
		setNanoSeconds(Utils.sdnvDecodeInt(decodeState));
	}
	
	/**
	 * Construct AdministrativeTimeStamp representing given components
	 * @param secs UTC Seconds since UTC 01/01/2000 00:00:00
	 * @param nSecs NanoSecs within Second
	 */
	public AdministrativeTimeStamp(long secs, int nSecs) {
		setUtcSecsSinceY2K(secs);
		setNanoSeconds(nSecs);
	}
	
	/**
	 * Encode AdministrativeTimeStamp to given encoding buffer
	 * @param encodeState Given encoding buffer
	 * @throws JDtnException on encoding errors
	 */
	public void encode(EncodeState encodeState) throws JDtnException {
		Utils.sdnvEncodeLong(getUtcSecsSinceY2K(), encodeState);
		Utils.sdnvEncodeInt(getNanoSeconds(), encodeState);
	}
	
	/**
	 * Dump this object
	 * @param indent How much to indent
	 * @param detailed Whether detailed dump desired
	 * @return the dump
	 */
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "AdministrativeTimeStamp\n");
		sb.append(indent + "  SecsSinceY2K=" + getUtcSecsSinceY2K() + "\n");
		sb.append(indent + "  NanoSecs=" + getNanoSeconds() + "\n");
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return dump("", false);
	}
	
	@Override
	public boolean equals(Object thatObj) {
		if (thatObj == null || !(thatObj instanceof AdministrativeTimeStamp)) {
			return false;
		}
		AdministrativeTimeStamp that = (AdministrativeTimeStamp)thatObj;
		return 
			this._utcSecsSinceY2K == that._utcSecsSinceY2K &&
			this._nanoSeconds == that._nanoSeconds;
	}
	
	@Override
	public int hashCode() {
		return 
			new Long(_utcSecsSinceY2K).hashCode() +
			new Integer(_nanoSeconds).hashCode();
	}
	
	/** UTC Seconds since UTC 01/01/2000 00:00:00 */
	public long getUtcSecsSinceY2K() {
		return _utcSecsSinceY2K;
	}

	/** UTC Seconds since UTC 01/01/2000 00:00:00 */
	protected void setUtcSecsSinceY2K(long utcSecsSinceY2K) {
		this._utcSecsSinceY2K = utcSecsSinceY2K;
	}

	/** Nanoseconds within second */
	public int getNanoSeconds() {
		return _nanoSeconds;
	}

	/** Nanoseconds within second */
	protected void setNanoSeconds(int nanoSeconds) {
		this._nanoSeconds = nanoSeconds;
	}
	
	
}
