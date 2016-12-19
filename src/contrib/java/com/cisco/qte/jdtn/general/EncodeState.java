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


import com.cisco.qte.jdtn.apps.MediaRepository;
import org.kritikal.fabric.contrib.jdtn.BlobAndBundleDatabase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ClosedByInterruptException;
import java.util.ArrayList;
import java.util.logging.Logger;


/**
 * A collection of encoded data.
 * Keeps track of Encoding process; for encoding either to a memory buffer or
 * to a File.
 */
public class EncodeState {
	/**
	 * 
	 */
	private static final int APPEND_BUFFER_LENGTH = 4096;

	@SuppressWarnings("unused")
	private static final Logger _logger =
		Logger.getLogger(EncodeState.class.getCanonicalName());
	
	/** True if encoding to a file */
	public boolean isEncodingToFile = false;
	/** If encoding to file, the File we're encoding to */
	public MediaRepository.File file = null;
	/** If encoding to file, the number of bytes encoded */
	public long fileLength = 0;
	
	/** If not encoding to file, the buffer we're encoding to */
	public GrowableArrayOfBytes memList = null;

	BlobAndBundleDatabase blobAndBundleDatabase = null;


	/**
	 * Constructor for encoding to memory
	 */
	public EncodeState() {
		isEncodingToFile = false;
		memList = new GrowableArrayOfBytes();
	}
	
	/**
	 * Constructor for encoding to a file
	 * @param aFile File to encode to
	 * @throws JDtnException If cannot open File
	 */
	public EncodeState(MediaRepository.File aFile) throws JDtnException {
		isEncodingToFile = true;
		this.file = aFile;
		blobAndBundleDatabase = BlobAndBundleDatabase.getInstance();
		// We must delete the file if it exists, otherwise subsequent
		// channel operations hang.  Go figure!
		if (aFile.exists()) {
			aFile.delete();
		}
	}
	
	/**
	 * Constructor for encoding to either a file or to memory
	 * @param aIsEncodingToFile True => encode to File
	 * @param aFile File to encode to, if isEncodingToFile
	 * @throws JDtnException If cannot open File
	 */
	public EncodeState(boolean aIsEncodingToFile, MediaRepository.File aFile) throws JDtnException {
		this.isEncodingToFile = aIsEncodingToFile;
		if (aIsEncodingToFile) {
			this.file = aFile;
			blobAndBundleDatabase = BlobAndBundleDatabase.getInstance();
			// We must delete the file if it exists, otherwise subsequent
			// channel operations hang.  Go figure!
			if (aFile.exists()) {
				aFile.delete();
			}
		}
		else {
			memList = new GrowableArrayOfBytes();
		}
	}
	
	/**
	 * Output a single encoded byte 
	 * @param bite the byte
	 * @throws JDtnException on I/O errors
	 */
	public void put(byte bite) throws JDtnException {
		if (isEncodingToFile) {
			byte[] bytes = new byte[1];
			bytes[0] = bite;
			blobAndBundleDatabase.appendByteArrayToFile(bytes, 0, bytes.length, file);
			fileLength++;
		} else {
			memList.add(bite);
		}
	}
	
	/**
	 * Output a single encoded byte 
	 * @param intBite the byte
	 * @throws JDtnException on I/O errors
	 */
	public void put(int intBite) throws JDtnException {
		if (isEncodingToFile) {
			byte[] bytes = new byte[1];
			bytes[0] = (byte)intBite;
			blobAndBundleDatabase.appendByteArrayToFile(bytes, 0, bytes.length, file);
			fileLength++;
		} else {
			memList.add(Utils.intToByteUnsigned(intBite));
		}
	}
	
	/**
	 * Get number of bytes encoded
	 * @return What I said
	 */
	public long getLength() {
		if (isEncodingToFile) {
			return file.length();
		} else {
			return memList.length();
		}
	}
	
	/**
	 * Get a byte[] array containing encoded bytes.  This works only for
	 * memory-encoding.
	 * @throws JDtnException If this isEncodingToFile
	 */
	public byte[] getByteBuffer() throws JDtnException {
		if (isEncodingToFile) {
			return blobAndBundleDatabase.mediaGetBodyData(file);
		}
		return memList.gather();
	}
	
	/**
	 * Append the given ArrayList<Byte>
	 * @param bites Given ArrayList<Byte>
	 * @throws JDtnException on I/O errors
	 */
	public void addAll(ArrayList<Byte> bites) throws JDtnException {
		if (isEncodingToFile) {
			byte[] biteArray = Utils.arrayListToByteArray(bites);
			try {
				blobAndBundleDatabase.appendByteArrayToFile(biteArray, 0, biteArray.length, file);
			} catch (Exception e) {
				throw new JDtnException(e);
			}
			fileLength += biteArray.length;
		} else {
			memList.addAll(bites);
		}
	}
	
	/**
	 * Append given EncodeState to this EncodeState.
	 * For now, works only when given EncodeState is in memory.
	 * @param encodeState Given EncodeState
	 * @throws JDtnException on encoding errors
	 * @throws InterruptedException 
	 */
	public void append(EncodeState encodeState) throws JDtnException, InterruptedException {
		if (encodeState.isEncodingToFile) {
			// StorageType is a File
			append(encodeState.file, 0, encodeState.getLength());
		} else {
			// StorageType is a buffer
			if (isEncodingToFile) {
				// Dest (this) is a file; source is a buffer
				append(encodeState.getByteBuffer(), 0, (int)encodeState.getLength());
			} else {
				// Dest (this) is a buffer; source is a buffer
				memList.addAll(encodeState.memList);
			}
		}
	}
	
	/**
	 * Append raw data from given File to this EncodeState
	 * @param sourceFile Given File
	 * @param offset Starting offset into given File 
	 * @param length Amount of data to append
	 * @throws JDtnException on encoding errors
	 * @throws InterruptedException 
	 */
	public void append(MediaRepository.File sourceFile, long offset, long length)
	throws JDtnException, InterruptedException {
		if (isEncodingToFile) {
			// append given File to this' File
			InputStream raf2 = null;
			try {
				// Open the source file
				raf2 = sourceFile.inputStream();
				
				// Position the source file to the specified offset
				if (offset != 0) {
					long nSkipped = raf2.skip(offset);
					if (nSkipped != offset) {
						throw new JDtnException("Skip " + offset + " failed");
					}
				}
				
				// Copy the data as a series of reads from the source file
				// and writes to the destination file
				byte[] buffer = new byte[APPEND_BUFFER_LENGTH];
				long remainingBytes = length;
				while (remainingBytes > 0) {
					int nRead = raf2.read(buffer);
					if (nRead > 0) {
						blobAndBundleDatabase.appendByteArrayToFile(buffer, 0, nRead, file);
						remainingBytes -= nRead;
						fileLength += nRead;
					} else {
						throw new JDtnException("Number of bytes read <= 0; remainingBytes to read=" + remainingBytes);
					}
				}
				
			} catch (IOException e) {
				throw new JDtnException(e);
			} finally {
				if (raf2 != null) {
					try {
						raf2.close();
					} catch (IOException e) {
						// Nothing
					}
				}
			}
		} else {
			// Append given File to in-memory buffer
			byte[] buf2 = new byte[(int)length];
			InputStream raf2 = null;
			try {
				// Open source file
				raf2 = sourceFile.inputStream();
				
				// Position source file
				if (offset != 0) {
					long nSkipped = raf2.skip(offset);
					if (nSkipped != offset) {
						throw new JDtnException("Skip " + offset + " failed");
					}
				}
				
				// Read source file
				int nRead = raf2.read(buf2);
				if (nRead != length) {
					throw new JDtnException("Tried to read " + length + " bytes but only read " + nRead);
				}
				
				// Append to in-memory buffer
				memList.add(buf2);
								
			} catch (ClosedByInterruptException e) {
				throw new InterruptedException();
				
			} catch (IOException e) {
				throw new JDtnException(e);
			} finally {
				try {
					if (raf2 != null) {
						raf2.close();
					}
				} catch (IOException e) {
					// Ignore
				}
			}
		}
	}
	
	/**
	 * Append raw data from given buffer to this EncodeState
	 * @param buffer Given buffer
	 * @param offset Starting offset into buffer
	 * @param length Amount of data to append
	 * @throws JDtnException
	 */
	public void append(byte[] buffer, int offset, int length)
	throws JDtnException {
		if (isEncodingToFile) {
			blobAndBundleDatabase.appendByteArrayToFile(buffer, 0, length, file);
			fileLength += length;
		} else {
			for (int ix = 0; ix < length; ix++) {
				memList.add(buffer[offset + ix]);
			}			
		}
	}
	
	/**
	 * Clear the Encoding; Note, in a File encoding, you'll have to recontruct.
	 */
	public void clear() {
		if (isEncodingToFile) {
			close();
			fileLength = 0;
		} else {
			memList.discardData();
		}
	}
	
	/**
	 * Close the encoding process
	 */
	public void close() {
		if (isEncodingToFile) {
			try {
				blobAndBundleDatabase.getInterface().commit();
			} catch (Exception e) {
				// Ignore
			}
		} else {
			memList.close();
		}
	}
	
	/**
	 * Delete the backing store behind the encoding state
	 */
	public void delete() {
		close();
		if (isEncodingToFile) {
			file.delete();
		} else {
			memList.discardData();
		}
	}
	
	/**
	 * Dump this object
	 * @param indent amount of indentation
	 * @param detailed Verbose
	 * @return Dump
	 */
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "EncodeState\n");
		sb.append(indent + "  IsEncodingToFile=" + isEncodingToFile + "\n");
		if (isEncodingToFile) {
			sb.append(indent + "  File=" + file.getAbsolutePath() + "\n");
			sb.append(indent + "  Length=" + fileLength + "\n");
		} else {
			if (detailed) {
				byte[] bytes = memList.gather();
				sb.append(Utils.dumpBytes(indent, bytes, 0, bytes.length, true));
			}
			sb.append(indent + "  Length=" + memList.length());
		}
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object thatObj) {
		if (thatObj == null || !(thatObj instanceof EncodeState)) {
			return false;
		}
		EncodeState that = (EncodeState)thatObj;
		
		if (that.isEncodingToFile != this.isEncodingToFile) {
			return false;
		}
		if (isEncodingToFile) {
			if (!this.file.equals(that.file)) {
				return false;
			}
			if (this.fileLength != that.fileLength) {
				return false;
			}
			return true;
			
		} else {
			return this.memList.equals(that.memList);
		}
		
	}
	
	@Override
	public int hashCode() {
		if (isEncodingToFile) {
			return file.hashCode() + (int)fileLength;
		} else {
			return memList.hashCode();
		}
	}
}
