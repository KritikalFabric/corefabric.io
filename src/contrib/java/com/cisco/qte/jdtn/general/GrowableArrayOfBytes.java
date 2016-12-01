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

import java.util.ArrayList;
//import java.util.logging.Logger;

/**
 * Store for Encoding into memory buffers; buffers are a list of byte[].
 * Optimized for avoiding per-byte copies where ever possible.  We previously
 * used ArrayList<Byte> for this purpose, and found that extremely inefficient
 * on Android.
 * <p>
 * We only grow, never shrink.  We don't need to enumerate directly.  There is
 * a gather operation which gathers the data structure down to a flat byte[],
 * and this operation is intended to be used only sparingly at the end of an
 * Encode.
 */
public class GrowableArrayOfBytes {

//	private static final Logger _logger =
//		Logger.getLogger(GrowableArrayOfBytes.class.getCanonicalName());
	
	// Size of byte[] used when we're appending individual bytes.
	public static final int DEFAULT_ARRAY_SIZE = 256;
	// The list of byte[]'s
	private ArrayList<ByteArray> arrayList = new ArrayList<ByteArray>();
	// Most recent ByteArray, not yet added to arrayList.
	private ByteArray curByteArray = null;
	// Total number of bytes accumulated.
	private int totalLen = 0;
	
	/**
	 * Constructor
	 */
	public GrowableArrayOfBytes() {
		// Nothing
	}
	
	/**
	 * Add a single byte
	 * @param bite Single byte
	 */
	public void add(byte bite) {
//		_logger.info("Add byte, totalLen=" + totalLen);
		if (curByteArray == null) {
			curByteArray = new ByteArray(DEFAULT_ARRAY_SIZE);
		}
		if (curByteArray.curLen >= curByteArray.byteArray.length) {
			arrayList.add(curByteArray);
			curByteArray = new ByteArray(DEFAULT_ARRAY_SIZE);
		}
		curByteArray.add(bite);
		totalLen++;
	}
	
	/**
	 * Add given byte[]
	 * @param bites Given byte[]
	 */
	public void add(byte[] bites) {
//		_logger.info("Add byte array, arrayLen=" + bites.length + " totalLen=" + totalLen);
		if (bites.length <= DEFAULT_ARRAY_SIZE) {
			if (curByteArray == null) {
				curByteArray = new ByteArray(bites);
			} else if (bites.length <= curByteArray.remainingLen()) {
				curByteArray.add(bites);
			} else {
				arrayList.add(curByteArray);
				curByteArray = new ByteArray(bites);
			}
		} else {
			if (curByteArray != null) {
				arrayList.add(curByteArray);
			}
			curByteArray = new ByteArray(bites);
		}
		totalLen += bites.length;
	}
	
	/**
	 * Add given ByteArray
	 * @param byteArray Given ByteArray
	 */
	private void add(ByteArray byteArray) {
//		_logger.info("Add ByteArray, ByteArrayLen=" + byteArray.curLen + " totalLen=" + totalLen);
		if (curByteArray != null) {
			arrayList.add(curByteArray);
		}
		curByteArray = byteArray;
		totalLen += byteArray.curLen;
	}
	
	/**
	 * Add given ArrayList<Byte>.  This is intended to be an occasional
	 * operation on small blocks of data.  It is done byte-by-byte, and
	 * is extremely slow.  Unfortunately, I don't know of another way to
	 * perform this.  Fortunately, it is only used occasionally and never
	 * when encoding for transmission.
	 * @param bites
	 */
	public void addAll(ArrayList<Byte> bites) {
//		_logger.info("addAll(ArrayList<Byte>");
		for (Byte bite : bites) {
			add(bite.byteValue());
		}
	}
	
	/**
	 * Append given GrowableArrayOfBytes to this GrowableArrayOfBytes.
	 * @param that Given GrowableArrayOfBytes.
	 */
	public void addAll(GrowableArrayOfBytes that) {
//		_logger.info("addAll(GrowableArrayOfBytes)");
		for (ByteArray byteArray : that.arrayList) {
			add(byteArray);
		}
		if (that.curByteArray != null) {
			add(that.curByteArray);
		}
	}
	
	/**
	 * Get a single byte[] containing the accumulated data.  This is intended
	 * to be a not-so-frequent operation; done once encoding has been completed.
	 * System.arrayCopy is used to gather byte[]'s together.
	 * @return Single byte[] containing the accumulated data.
	 */
	public byte[] gather() {
//		_logger.info("gather; totalLen=" + totalLen);
		byte[] result = new byte[totalLen];
		int index = 0;
		for (ByteArray byteArray : arrayList) {
//			_logger.info("gather loop: byteArray.curLen=" + byteArray.curLen);
//			if (index + byteArray.curLen > totalLen) {
//				_logger.severe("Inconsistent totalLen=" + totalLen + " index=" + index + " byteArray.curLen=" + byteArray.curLen);
//			}
			System.arraycopy(byteArray.byteArray, 0, result, index, byteArray.curLen);
			index += byteArray.curLen;
		}
		if (curByteArray != null) {
			System.arraycopy(curByteArray.byteArray, 0, result, index, curByteArray.curLen);
		}
		return result;
	}
	
	/**
	 * Get the number of bytes accumulated
	 * @return Ditto
	 */
	public int length() {
		return totalLen;
	}
	
	/**
	 * Close this out in preparation for transmission.
	 */
	public void close() {
//		_logger.info("close()");
		if (curByteArray != null) {
			arrayList.add(curByteArray);
			curByteArray = null;
		}
	}
	
	/**
	 * Discard all of the accumulated data.  This becomes empty.
	 */
	public void discardData() {
//		_logger.info("clear()");
		close();
		arrayList.clear();
		curByteArray = null;
		totalLen = 0;
	}
	
	/**
	 * A little helper class; basically a wrapper around a byte[] and a length.
	 */
	public class ByteArray {
		public byte[] byteArray;
		public int curLen;

		public ByteArray(int len) {
			byteArray = new byte[len];
			curLen = 0;
		}
		
		public ByteArray(byte[] bites) {
			byteArray = bites;
			curLen = bites.length;
		}
		
		public int remainingLen() {
			return byteArray.length - curLen;
		}
		
		public void add(byte bite) throws ArrayIndexOutOfBoundsException {
			if (curLen >= byteArray.length) {
				throw new ArrayIndexOutOfBoundsException();
			}
			byteArray[curLen++] = bite;
		}
		
		public void add(byte[] bites) throws ArrayIndexOutOfBoundsException {
			if (curLen + bites.length > byteArray.length) {
				throw new ArrayIndexOutOfBoundsException();
			}
			System.arraycopy(bites, 0, byteArray, curLen, bites.length);
			curLen += bites.length;
		}
	}
	
	/**
	 * Not very efficient, we do a gather and a byte-by-byte compare.  Only
	 * used for small accumulations, never for encoding for transmission.
	 */
	@Override
	public boolean equals(Object thatObj) {
		if (thatObj == null || !(thatObj instanceof GrowableArrayOfBytes)) {
			return false;
		}
		GrowableArrayOfBytes that = (GrowableArrayOfBytes)thatObj;
		if (that.totalLen != totalLen) {
			return false;
		}
		byte[] thisBites = gather();
		byte[] thatBites = that.gather();
		
		for (int ix = 0; ix < thisBites.length; ix++) {
			if (thisBites[ix] != thatBites[ix]) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Not very efficient, we do a gather and a byte-by-byte addition.  Only
	 * used for small accumulations, never for encoding for transmission.
	 */
	@Override
	public int hashCode() {
		byte[] bites = gather();
		int result = 0;
		for (int ix = 0; ix < bites.length; ix++) {
			result += bites[ix];
		}
		return result;
	}
	
	/**
	 * A quick little test program.
	 * @param args command line args not used
	 */
	public static void main(String[] args) {
		GrowableArrayOfBytes gaob = new GrowableArrayOfBytes();
		
		gaob.add((byte)0);
		gaob.add((byte)1);
		gaob.add((byte)2);
		gaob.add((byte)3);
		
		byte[] small1 = {
				(byte)4, (byte)5, (byte)6, (byte)7, (byte)8
		};
		gaob.add(small1);
		
		byte[] large = new byte[1400];
		for (int ix = 0; ix < 1400; ix++) {
			large[ix] = (byte)(ix + 9);
		}
		gaob.add(large);
		
		gaob.add((byte)1409);
		gaob.add((byte)1410);
		
		byte[] result = gaob.gather();
		if (gaob.totalLen != 1411) {
			System.out.println("Total Len exp=1409, act=" + gaob.length());
		}
		for (int ix = 0; ix < 1411; ix++) {
			if (result[ix] != (byte)ix) {
				System.out.println("Index " + ix + " exp=" + (byte)ix + " act=" + result[ix]);
			}
		}
		System.out.println("Done");
	}
	
	
}
