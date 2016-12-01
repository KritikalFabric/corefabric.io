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

import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.JDtnException;

/**
 * The Generic Payload of a Bundle which carries administrative/control
 * information in its payload.  Has isAdminRecord true in its PrimaryBundleBlock.
 */
public abstract class AdministrativeRecord extends Payload {
	/** Mask for record type in type/flags field */
	public static final int ADMIN_RECORD_TYPE_MASK = 0xf0;
	/** Record type identifying AdminRecord as a Bundle Status Report */
	public static final int ADMIN_RECORD_TYPE_STATUS_REPORT = 0x10;
	/** Record type identifying AdminRecord as a Custody Signal */
	public static final int ADMIN_RECORD_TYPE_CUSTODY_SIGNAL = 0x20;
	/** Mask for flags in type/flags field */
	public static final int ADMIN_RECORD_FLAGS_MASK = 0x0f;
	/** Flag identifying AdminRecord as reporting on a Bundle Fragment */
	public static final int ADMIN_RECORD_FLAGS_FRAGMENT = 0x01;
	
	/** The AdministrativeRecord Type, one of ADMIN_RECORD_TYPE_* */
	protected int _recordType = 0;
	
	/** Whether AdministrativeRecord pertains to a Fragment */
	protected boolean _forFragment = false;

	/**
	 * Decode given Decode buffer representing the payload of an AdministrativeRecord
	 * and construct an implementation; BundleStatusReport or CustodaySignal
	 * @param decodeState Decode Buffer
	 * @return Constructed AdministrativeRecord implementation
	 * @throws JDtnException on decode errors
	 */
	public static AdministrativeRecord decodeAdministrativeRecord(DecodeState decodeState)
	throws JDtnException {
		int typeFlags = decodeState.getByte();
		int recordType = typeFlags & ADMIN_RECORD_TYPE_MASK;
		boolean forFragment = (typeFlags & ADMIN_RECORD_FLAGS_FRAGMENT) != 0;
		
		AdministrativeRecord result = null;
		switch (recordType) {
		case ADMIN_RECORD_TYPE_STATUS_REPORT:
			result = new BundleStatusReport(forFragment, decodeState);
			break;
		case ADMIN_RECORD_TYPE_CUSTODY_SIGNAL:
			result = new CustodySignal(forFragment, decodeState);
			break;
		default:
			throw new JDtnException("Unrecognized Admin Record Type: " + recordType);
		}
		return result;
	}
	
	/**
	 * Construct an AdministrativeRecord with the given RecordType and Fragment
	 * Flag
	 * @param recordType Record Type; one of ADMIN_RECORD_TYPE_*
	 * @param forFragment True if AdministrativeRecord applies to a fragment
	 */
	public AdministrativeRecord(int recordType, boolean forFragment) {
		setRecordType(recordType);
		setForFragment(forFragment);
	}
	
	/**
	 * Encode this AdministrativeRecord into the given buffer
	 * @param encodeState Given encoding buffer
	 * @throws JDtnException On encoding errors
	 */
	@Override
	public void encode(EncodeState encodeState) throws JDtnException {
		int typeFlags = getRecordType() & ADMIN_RECORD_TYPE_MASK;
		if (isForFragment()) {
			typeFlags |= ADMIN_RECORD_FLAGS_FRAGMENT;
		}
		encodeState.put(typeFlags);
	}
	
	/**
	 * Dump this object
	 * @param indent How much to indent
	 * @param detailed Whether detailed dump desired
	 * @return the dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "AdministrativeRecord\n");
		switch (getRecordType()) {
		case ADMIN_RECORD_TYPE_STATUS_REPORT:
			sb.append(indent + "  RecordType=Status Report\n");
			break;
		case ADMIN_RECORD_TYPE_CUSTODY_SIGNAL:
			sb.append(indent + "  RecordType=Custody Signal\n");
			break;
		default:
			sb.append(indent + "  RecordType=" + getRecordType() + "\n");
			break;
		}
		sb.append(indent + "  IsForFragment=" + isForFragment() + "\n");
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return dump("", false);
	}
	
	/** The AdministrativeRecord Type, one of ADMIN_RECORD_TYPE_* */
	public int getRecordType() {
		return _recordType;
	}

	/** The AdministrativeRecord Type, one of ADMIN_RECORD_TYPE_* */
	protected void setRecordType(int recordType) {
		this._recordType = recordType;
	}

	/** Whether AdministrativeRecord pertains to a Fragment */
	public boolean isForFragment() {
		return _forFragment;
	}

	/** Whether AdministrativeRecord pertains to a Fragment */
	public void setForFragment(boolean forFragment) {
		this._forFragment = forFragment;
	}
}
