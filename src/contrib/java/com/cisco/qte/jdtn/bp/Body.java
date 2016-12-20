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

import java.sql.SQLException;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.apps.MediaRepository;
import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Utils;
import org.kritikal.fabric.contrib.jdtn.BlobAndBundleDatabase;

/**
 * The Body of a SecondaryBundleBlock.
 * Encapsulates the generic body as either:
 * <ul>
 *   <li>A File, in which the following members describe the payload:
 *   <ul>
 *     <li>BodyDataFile - File containing the Payload
 *     <li>BodyDataFileOffset - Starting offset into the BodyDataFile file
 *     <li>BodyDataFileLength - Number of bytes of payload
 *   </ul>
 *   <li>An in-memory buffer, in which the following members describe the payload:
 *   <ul>
 *     <li>BodyDataBuffer - Array of bytes containing the Payload
 *     <li>BodyDataMemOffset - Strating offset into the BodyDataBuffer
 *     <li>BodyDataMemLength - Number of bytes of payload
 *   </ul>
 * </ul>
 */
public abstract class Body {

	private static Logger _logger =
		Logger.getLogger(Body.class.getCanonicalName());
	
	/** True if block body data is in file */
	protected boolean _bodyDataInFile = false;
	
	/** If block body data is in file, this is the file it's in */
	protected MediaRepository.File _bodyDataFile = null;
	
	/** If block body data is in file, this is the starting offset w/in File */
	protected long _bodyDataFileOffset = 0L;
	
	/** If block body data is in file, this is the length of body in File */
	protected long _bodyDataFileLength = -1L;
	
	/** If block body data is not in file, this is the buffer it's in */
	protected byte[] _bodyDataBuffer;
	
	/** If block body data is not in file, this is the starting offset w/in buffer */
	protected int _bodyDataMemOffset = 0;
	
	/** If block body data is not in file, this is the length w/in buffer */
	protected int _bodyDataMemLength = -1;
	
	/**
	 * Decode and construct a Body from the given Decode buffer
	 * @param secondaryBundleBlock The SecondaryBundleBlock of which this will
	 * be the Body.  We will construct either an AdministrativeRecord type
	 * body (if secondaryBundleBlock.isAdminRecord()) or a generic
	 * Payload type body otherwise.
	 * @param decodeState Given Decode Buffer
	 * @return The constructed Body
	 * @throws JDtnException on Decoding errors
	 */
	public static Body decodeBody(
			SecondaryBundleBlock secondaryBundleBlock, 
			DecodeState decodeState) throws JDtnException {
		if (secondaryBundleBlock.getBundle().isAdminRecord()) {
			return AdministrativeRecord.decodeAdministrativeRecord(decodeState);
		} else {
			return new Payload(decodeState);
		}
	}
	
	/**
	 * Do-little constructor.  Caller must fill in the details.
	 */
	public Body() {
		setBodyDataInFile(false);
	}
	
	public Body(
			boolean inFile,
			MediaRepository.File file,
			long fileOffset,
			long fileLength,
			byte[] memBuffer,
			int memOffset,
			int memLength
			) {
		setBodyDataInFile(inFile);
		setBodyDataFile(file);
		setBodyDataFileOffset(fileOffset);
		setBodyDataFileLength(fileLength);
		setBodyDataBuffer(memBuffer);
		setBodyDataMemOffset(memOffset);
		setBodyDataMemLength(memLength);
	}
	
	public Body(DecodeState decodeState) {
		setBodyDataInFile(decodeState._isInFile);
		setBodyDataFile(decodeState._filePath);
		setBodyDataFileOffset(decodeState._fileOffset);
		setBodyDataFileLength(decodeState._fileLength);
		setBodyDataBuffer(decodeState._memBuffer);
		setBodyDataMemOffset(decodeState._memOffset);
		setBodyDataMemLength(decodeState._memLength);
	}
	
	public abstract void encode(java.sql.Connection con, EncodeState encodeState) throws JDtnException, InterruptedException;
	
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "Body\n");
		if (isBodyDataInFile()) {
			sb.append(indent + "  BodyDataFileOffset=" + getBodyDataFileOffset() + "\n");
			sb.append(indent + "  BodyDataFileLength=" + getBodyDataFileLength() + "\n");
			sb.append(indent + "  BodyDataFile=" + getBodyDataFile().getAbsolutePath() + "\n");
		} else {
			sb.append(indent + "  BodyDataMemOffset=" + getBodyDataMemOffset() + "\n");
			sb.append(indent + "  BodyDataMemLength=" + getBodyDataMemLength() + "\n");
			sb.append(indent + "  BlockBodyDataBuffer:\n");
			sb.append(Utils.dumpBytes(indent + "    ", getBodyDataBuffer(), 0, getBodyDataMemLength()));
		}
		return sb.toString();
	}
	
	/**
	 * Delete the backing storage behind this
	 */
	public void delete() {
		if (_bodyDataInFile) {
			java.sql.Connection con = BlobAndBundleDatabase.getInstance().getInterface().createConnection();
			boolean success = false;
			try {
				success = _bodyDataFile.delete(con);
				if (success) { try { con.commit(); } catch (SQLException ignore) { } }
				else { try { con.rollback(); } catch (SQLException ignore) { } }
				if (!success) {
					_logger.warning(("Cannot delete Payload backing store: " +
							_bodyDataFile.getAbsolutePath()));
				}
			}
			finally {
				try { con.close(); } catch (SQLException ignore) { }
			}
			_bodyDataFileLength = 0;
			_bodyDataFileOffset = 0;
			
		} else {
			_bodyDataBuffer = null;
			_bodyDataMemLength = 0;
			_bodyDataMemOffset = 0;
		}
	}
	
	/**
	 * Get the length of the Body, regardless of whether it's in a File or
	 * in memory.
	 * @return Length of Body, bytes
	 */
	public long getLength() {
		if (isBodyDataInFile()) {
			return getBodyDataFileLength();
		} else {
			return getBodyDataMemLength();
		}
	}
	
	/**
	 * Get the offset of the Body, regardless of whether it's in a File or
	 * in memory.
	 * @return Body data offset
	 */
	public long getOffset() {
		if (isBodyDataInFile()) {
			return getBodyDataFileOffset();
		} else {
			return getBodyDataMemOffset();
		}
	}
	
	/** True if block body data is in file */
	public boolean isBodyDataInFile() {
		return _bodyDataInFile;
	}

	/** True if block body data is in file */
	public void setBodyDataInFile(boolean bodyDataInFile) {
		this._bodyDataInFile = bodyDataInFile;
	}

	/** If block body data is in file, this is the file it's in */
	public MediaRepository.File getBodyDataFile() {
		return _bodyDataFile;
	}

	/** If block body data is in file, this is the file it's in */
	public void setBodyDataFile(MediaRepository.File bodyDataFile) {
		this._bodyDataFile = bodyDataFile;
	}

	/** If block body data is not in file, this is the buffer it's in */
	public byte[] getBodyDataBuffer() {
		return _bodyDataBuffer;
	}

	/** If block body data is not in file, this is the buffer it's in */
	public void setBodyDataBuffer(byte[] bodyDataBuffer) {
		this._bodyDataBuffer = bodyDataBuffer;
	}

	public long getBodyDataFileOffset() {
		return _bodyDataFileOffset;
	}

	protected void setBodyDataFileOffset(long bodyDataFileOffset) {
		this._bodyDataFileOffset = bodyDataFileOffset;
	}

	public long getBodyDataFileLength() {
		return _bodyDataFileLength;
	}

	protected void setBodyDataFileLength(long bodyDataFileLength) {
		this._bodyDataFileLength = bodyDataFileLength;
	}

	public int getBodyDataMemOffset() {
		return _bodyDataMemOffset;
	}

	protected void setBodyDataMemOffset(int bodyDataMemOffset) {
		this._bodyDataMemOffset = bodyDataMemOffset;
	}

	public int getBodyDataMemLength() {
		return _bodyDataMemLength;
	}

	protected void setBodyDataMemLength(int bodyDataMemLength) {
		this._bodyDataMemLength = bodyDataMemLength;
	}
	

}
