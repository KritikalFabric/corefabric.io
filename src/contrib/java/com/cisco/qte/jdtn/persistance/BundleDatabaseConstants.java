/**
Copyright (c) 2011, Cisco Systems, Inc.
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
package com.cisco.qte.jdtn.persistance;

/**
 * Constants for the Bundles Database
 */
public interface BundleDatabaseConstants {
	// The bundles database table name
	public static final String TABLE_NAME = "node.cisco_jdtn__bundles";
	public static final String FILE_TABLE_NAME = "node.cisco_jdtn__files";
	
	// DB Column names.  The DB columns SOURCE_EID_COL, TIME_SECS_COL,
	// SEQUENCE_NO_COL, and FRAG_OFF_COL together constitute a unique key for
	// rows in the DB.
	public static final String SOURCE_EID_COL = "sourceEid";
	public static final String TIME_SECS_COL = "timeSecs";
	public static final String SEQUENCE_NO_COL = "sequenceNo";
	public static final String FRAG_OFFSET_COL = "fragOffset";
	public static final String PATH_COL = "path";
	public static final String STORAGETYPE_COL = "storageType";
	public static final String LENGTH_COL = "length";
	public static final String SOURCE_COL = "source";
	public static final String STATE_COL = "state";
	public static final String EID_SCHEME_COL = "eidScheme";
	public static final String LINK_NAME_COL = "linkName";
	public static final String IS_INBOUND_COL = "isInbound";
	public static final String RETENTION_CONSTRAINT_COL = "retentionConstraint";
	public static final String DATA_BLOB_COL = "dataBytes";
}
