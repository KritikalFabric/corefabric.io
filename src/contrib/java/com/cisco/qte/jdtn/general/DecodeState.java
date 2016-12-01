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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;


/**
 * Keeps track of where we are when we're decoding data.  Works for both
 * in-memory data and in-file data.  For in-memory data, we use ints for
 * offset and length.  For in-file data, we use longs for offset and length.
 */
public class DecodeState {
	/** True => Get data from a file; False => get data from a buffer */
	public boolean _isInFile;
	/** isInFile==false: Buffer containing data to decode */
	public byte[] _memBuffer;
	/** isInFile==false: Current offset to next item to decode */
	public int _memOffset;
	/** isInFile==false: Length of the buffer */
	public int _memLength;
	
	/** isInFile==true; File path of file data */
	public File _filePath;
	/** isInFile==true; Current offset to next item to decode */
	public long _fileOffset;
	/** isInFile==true; Length of the File */
	public long _fileLength;
	/** isInFile==true; the open File */
	public InputStream _fis;
	
	/**
	 * Constructor which fills in all members from arguments for an
	 * in-memory buffer.
	 * @param aBuffer The Buffer containing data to decode
	 * @param aOffset Current ofset to next item to decode
	 * @param aLength Length of the buffer
	 */
	public DecodeState(byte[] aBuffer, int aOffset, int aLength) {
		storeInMemoryParameters(aBuffer, aOffset, aLength);
		this._isInFile = false;
	}

	/**
	 * Constructor which fills in all members from arguments for an
	 * in-file buffer.
	 * @param aFilePath Path of the file containining the data to decode
	 * @param aOffset Initial offset into File
	 * @param aLength Length of File to decode.  Must be known, we use a
	 * RandomAccessFile and there's no way to detect EOF.
	 * @throws IOException if cannot open the file or seek to initial Offset.
	 */
	public DecodeState(File aFilePath, long aOffset, long aLength) 
	throws JDtnException {
		storeInFileParameters(aFilePath, aOffset, aLength);
		this._isInFile = true;
		
		try {
			_fis = new FileInputStream(aFilePath);
			if (_fileOffset != 0) {
				_fis.skip(_fileOffset);
			}
		} catch (IOException e) {
			throw new JDtnException(e);
		}
	}

	/**
	 * Constructor which fills in all members from arguments for an
	 * InputStream not necessarily a File.
	 * @param inputStream The InputStream
	 */
	public DecodeState(InputStream inputStream) {
		_isInFile = true;
		_filePath = null;
		_fileOffset = 0L;
		_fileLength = Long.MAX_VALUE;
		_fis = inputStream;
	}
	
	/**
	 * Constructor which fills in members from arguments for either an in-file
	 * or in-memory buffer.
	 * @param aIsInFile Whether is in file or in memory
	 * @param aFilePath If isInFile, Path of the file containing the data to decode
	 * @param aFileOffset If isInFile, Initial offset into file
	 * @param aFileLength If isInFile, length of data to decode.  Must be known.
	 * @param aBuffer If !isInFile, buffer containing data to decode
	 * @param aOffset If !isInFile, initial offset into file
	 * @param aLength If !isInFile, length of the buffer
	 * @throws IOException If cannot open file or seek to initial offset.
	 */
	public DecodeState(
			boolean aIsInFile, 
			File aFilePath, 
			long aFileOffset, 
			long aFileLength, 
			byte[] aBuffer, 
			int aOffset, 
			int aLength) throws IOException {
		storeInMemoryParameters(aBuffer, aOffset, aLength);
		storeInFileParameters(aFilePath, aFileOffset, aFileLength);
		this._isInFile = aIsInFile;
		
		if (aIsInFile) {
			_fis = new FileInputStream(aFilePath);
			if (aFileOffset != 0) {
				_fis.skip(aFileOffset);
			}
		}
	}
	
	/**
	 * Flag the DecodeState done and close it out
	 */
	public void close() {
		// Make sure further attempts to decode fail
		_memOffset = _memLength;
		_fileOffset = _fileLength;
		
		// If data in file, close the RandomAccessFile we have open.
		if (_isInFile) {
			try {
				if (_fis != null) {
					_fis.close();
					_fis = null;
				}
			} catch (IOException e) {
				// Nothing
			}
		}
	}
	
	/**
	 * Close and if decoding from File, delete the File
	 */
	public void delete() {
		close();
		if (_isInFile) {
			_filePath.delete();
		}
	}
	
	/**
	 * Read the byte at the current offset, advance offset by 1.  Works for
	 * either in-memory or in-file decoding.
	 * @return Byte at the current offset
	 * @throws JDtnException if current offset beyond end of buffer or
	 * I/O error on accessing file buffer.
	 */
	public int getByte() throws JDtnException {
		if (_isInFile) {
			if (!isValidOffset(_fileOffset)) {
				throw new JDtnException("Trying to decode past end of file");
			}
			try {
				_fileOffset++;
				return _fis.read();
				
			} catch (IOException e) {
				throw new JDtnException(e);
			}
		} else {
			if (!isValidOffset(_memOffset)) {
				throw new JDtnException("Trying to decode past end of buffer");
			}
			return Utils.byteToIntUnsigned(_memBuffer[_memOffset++]);
		}
	}
	
	/**
	 * Determine if given offset is a valid offset for current DecodeState.
	 * @param offset Given offset
	 * @return True if given offset is valid
	 */
	public boolean isValidOffset(long offset) {
		if (_isInFile) {
			if (offset < 0 || offset >= _fileLength) {
				return false;
			}
			return true;
		} else {
			if (offset < 0 || offset >= _memLength) {
				return false;
			}
			return true;
		}
	}
	
	/**
	 * Determine if decode is at end of decode data; i.e., current offset at
	 * or beyond length of decode data.
	 * @return True if at end of decode data.
	 */
	public boolean isAtEnd() {
		if (_isInFile) {
			return _fileOffset >= _fileLength;
		} else {
			return _memOffset >= _memLength;
		}
	}
	
	/**
	 * Get current offset as a long
	 * @return What I said
	 */
	public long getLongOffset() {
		if (_isInFile) {
			return _fileOffset;
		} else {
			return _memOffset;
		}
	}
	
	/**
	 * Set the offset.  NOTE: no error checking
	 * @param offset Given offset
	 */
	public void setLongOffset(long offset) {
		if (_isInFile) {
			_fileOffset = offset;
		} else {
			this._memOffset = (int)offset;
		}
	}
	
	/**
	 * Increment the current decode offset by given amount
	 * @param incrAmount Given amount to increment
	 */
	public void incrementOffsetBy(int incrAmount) {
		if (_isInFile) {
			_fileOffset += incrAmount;
		} else {
			_memOffset += incrAmount;
		}
	}
	
	/**
	 * Get the remaining length of the Decode data (i.e., length - offset)
	 * @return Remaining Length
	 */
	public long remainingLength() {
		if (_isInFile) {
			return _fileLength - _fileOffset;
		} else {
			return _memLength - _memOffset;
		}
	}
	
	/**
	 * Get a sub-sequence of raw bytes from the DecodeState to an in-memory
	 * buffer starting at current offset.  Current offset is advanced.
	 * @param length Number of raw bytes to get.
	 * @return in-memory buffer containing raw bytes.
	 * @throws JDtnException on various decoding errors
	 */
	public byte[] getBytes(int length) throws JDtnException {
		byte[] result = new byte[length];
		if (_isInFile) {
			if (!isValidOffset(_fileOffset + length - 1) ){
				throw new JDtnException("Operation would read past end of file");
			}
			try {
				int offset = 0;
				int remainingLength = length;
				while (remainingLength > 0) {
					int nRead = _fis.read(result, offset, remainingLength);
					if (nRead < 0) {
						throw new JDtnException("EOF detected while decoding");
						
					} else if (nRead == 0) {
						throw new JDtnException("0 bytes read");
						
					} else {
						remainingLength -= nRead;
						offset += nRead;
					}
				}
			} catch (IOException e) {
				throw new JDtnException(e);
			}
			_fileOffset += length;
			
		} else {
			if (!isValidOffset(_memOffset + length - 1)) {
				throw new JDtnException("Operation would scan past end of buffer");
			}
			Utils.copyBytes(_memBuffer, _memOffset, result, 0, length);
			_memOffset += length;
		}
		return result;
	}
	
	/**
	 * Spill a sub-sequence of raw bytes from the DecodeState to given File,
	 * starting at current offset.  Current offset is advanced.
	 * @param file File to spill to
	 * @param length Number of raw bytes to spill
	 * @throws JDtnException on various errors
	 */
	public void spillToFile(File file, long length) throws JDtnException {
		FileOutputStream fos = null;
		byte[] buffer = new byte[4096];
		try {
			if (_isInFile) {
				// Spill from this File to given File
				fos = new FileOutputStream(file);
				while (length > 0) {
					int nRead = _fis.read(buffer);
					if (nRead > 0) {
						fos.write(buffer, 0, nRead);
						length -= nRead;
					} else {
						throw new JDtnException("Asked for 4096 bytes but read returned " + nRead);
					}
				}
				
			} else {
				// Spill from in-memory buffer to File
				fos = new FileOutputStream(file);
				fos.write(_memBuffer, _memOffset, (int)length);
			}
			
		} catch (IOException e) {
			throw new JDtnException("spillToFile " + file.getAbsolutePath());
			
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					// Ignore
				}
			}
		}
		
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
		StringBuffer sb = new StringBuffer(indent + "DecodeState\n");
		if (_isInFile) {
			sb.append(indent + "  IsInFile\n");
			sb.append(indent + "  File=" + _filePath.getAbsolutePath() + "\n");
			sb.append(indent + "  Offset=" + _fileOffset + "\n");
			sb.append(indent + "  Length=" + _fileLength + "\n");
		} else {
			sb.append(indent + "  IsInMemory\n");
			sb.append(indent + "  Offset=" + _memOffset + "\n");
			sb.append(indent + "  Length=" + _memLength + "\n");
			if (detailed) {
				int lengthToDump = _memLength;
				if (_memLength > _memBuffer.length) {
					lengthToDump = _memBuffer.length;
					sb.append(indent + "  Inconsistent _memLength & _memBuffer.length\n");
					sb.append(indent + "  _memBuffer.length=" + _memBuffer.length + "\n");
				}
				sb.append(indent + "  Buffer=" + "\n");
				sb.append(Utils.dumpBytes(indent + "  ", _memBuffer, 0, lengthToDump));
			}
		}
		return sb.toString();
	}
	
	private void storeInMemoryParameters(byte[] buffer, int offset, int length) {
		this._memBuffer = buffer;
		this._memOffset = offset;
		this._memLength = length;
	}
	
	private void storeInFileParameters(File aFilePath, long offset, long length) {
		this._filePath = aFilePath;
		this._fileOffset = offset;
		this._fileLength = length;
	}
	
}
