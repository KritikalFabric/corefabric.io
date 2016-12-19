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

import java.io.*;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.apps.MediaRepository;
import com.cisco.qte.jdtn.ltp.LtpException;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;
import org.kritikal.fabric.contrib.jdtn.BlobAndBundleDatabase;

/**
 * Miscellaneous Utilities
 * <ul>
 *   <li>SDNV related utilities
 *   <li>Byte to Int related Utilities
 * </ul>
 */
public class Utils {

	public static final Logger _logger =
		Logger.getLogger(Utils.class.getCanonicalName());
	
	private static final int MAX_SDNVS_IN_LONG = Long.SIZE / 7 + 1;
	private static final int MAX_SDNVS_IN_INT = Integer.SIZE / 7 + 1;
	
	// Don't ever instantiate this thang
	private Utils() {
		// Nothing
	}
	
	/**
	 * Encode given int as an SDNV
	 * @param value Given int
	 * @param encodeState where we are encoding to
	 * @throws JDtnException on Encoding errors
	 */
	public static void sdnvEncodeInt(int value, EncodeState encodeState)
	throws JDtnException {
		if (value == 0) {
			encodeState.put(0);
		} else {
			boolean first = true;
			ArrayList<Byte> reverseBytes = new ArrayList<Byte>();
			while (value != 0) {
				byte bite = (byte)(value & 0x7f);
				value >>= 7;
				if (first) {
					first = false;
				} else {
					bite = (byte)(bite | 0x80);
				}
				reverseBytes.add(bite);
			}
			for (int ix = reverseBytes.size() - 1; ix >= 0; ix--) {
				encodeState.put(reverseBytes.get(ix).byteValue());
			}
		}
	}
	
	/**
	 * SDNV decode given byte array at given offset into an int
	 * @param decodeState - Supplies state of decoding:
	 * <ul>
	 *   <li>buffer Given byte array
	 *   <li> offset Current offset in buffer; updated after decode
	 *   <li> length Length of byte array
	 * </ul>
	 * @return Decoded long
	 * @throws JDtnException If SDNV too large to filt in int
	 */
	public static int sdnvDecodeInt(DecodeState decodeState) throws JDtnException {
		int n = 1;
		int result = 0;
		boolean endData = false;
		while (!endData) {
			if (decodeState.isAtEnd()) {
				throw new JDtnException("Reached end of buffer without end marker");
			}
			if (n > MAX_SDNVS_IN_INT) {
				/*
				 * 9.3.  Implementation Considerations
				 *  Byte ranges
				 *     Various report and other segments contain offset and length
				 *     fields.  Implementations MUST ensure that these are consistent and
				 *     sane.
				 */
				throw new JDtnException("SDNV too large to fit in int");
			}
			int bite = decodeState.getByte();
			endData = ((bite & 0x80) == 0);
			bite = bite & 0x7f;
			result <<= 7;
			result |= bite;
			n++;
		}
		return result;
	}
	
	/**
	 * Encode given long as an SDNV
	 * @param value Given long
	 * @param encodeState encodeState what we are encoding to
	 * @throws JDtnException on encoding errors
	 */
	public static void sdnvEncodeLong(long value, EncodeState encodeState)
	throws JDtnException {
		if (value == 0) {
			encodeState.put(0);
		} else {
			boolean first = true;
			ArrayList<Byte> reverseBytes = new ArrayList<Byte>();
			while (value != 0) {
				byte bite = (byte)(value & 0x7f);
				value >>= 7;
				if (first) {
					first = false;
				} else {
					bite = (byte)(bite | 0x80);
				}
				reverseBytes.add(bite);
			}
			for (int ix = reverseBytes.size() - 1; ix >= 0; ix--) {
				encodeState.put(reverseBytes.get(ix));
			}
		}
	}
	
	/**
	 * SDNV decode given byte array at given offset into a long
	 * @param decodeState - Supplies state of decoding:
	 * <ul>
	 *   <li>buffer Given byte array
	 *   <li> offset Current offset in buffer; updated after decode
	 *   <li> length Length of byte array
	 * </ul>
	 * @return Decoded long
	 * @throws JDtnException If SDNV too large to filt in long
	 */
	public static long sdnvDecodeLong(DecodeState decodeState) throws JDtnException {
		int n = 1;
		long result = 0;
		boolean endData = false;
		while (!endData) {
			if (decodeState.isAtEnd()) {
				throw new JDtnException("Reached end of buffer without end marker");
			}
			if (n > MAX_SDNVS_IN_LONG) {
				/*
				 * 9.3.  Implementation Considerations
				 *  Byte ranges
				 *     Various report and other segments contain offset and length
				 *     fields.  Implementations MUST ensure that these are consistent and
				 *     sane.
				 */
				throw new JDtnException("SDNV too large to fit in long");
			}
			int bite = decodeState.getByte();
			endData = ((bite & 0x80) == 0);
			bite = bite & 0x7f;
			result <<= 7;
			result |= bite;
			n++;
		}
		return result;
	}
	
	// Used in sdnvEncodeBytes - Leading bit mask
	private static final int masks1[] = {
		0xfe,
		0xfc,
		0xf8,
		0xf0,
		0xe0,
		0xc0,
		0x80,
		0,
		0xfe,
		0xfc,
		0xf8,
		0xf0,
		0xe0,
		0xc0,
		0x80,
		0
	};
	
	// Used in sdnvEncodeBytes - Leading shift
	private static final int shifts1[] = {
		1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7, 0
	};
	
	// Used in sdnvEncodeBytes - Trailing bit mask
	private static final int masks2[] = {
		0x00,
		0x01,
		0x03,
		0x07,
		0x0f,
		0x1f,
		0x3f,
		0x7f,
		0,
		0x01,
		0x03,
		0x07,
		0x0f,
		0x1f,
		0x3f,
		0x7f
	};
	
	// Used in sdnvEncodeBytes - Trailing shift
	private static final int shifts2[] = {
		0, 6, 5, 4, 3, 2, 1, 0, 0, 6, 5, 4, 3, 2, 1, 0
	};
		
	/**
	 * Encode the given byte array as SDNV
	 * @param bytes Given byte array
	 * @param encodeState what we are encoded to
	 * @throws JDtnException on encoding errors
	 */
	public static void sdnvEncodeBytes(byte[] bytes, EncodeState encodeState)
	throws JDtnException {
		if (bytes.length == 0) {
			throw new IllegalArgumentException("Cannot encode 0-length byte array");
		}
		int length = bytes.length;
		int rem = length % 7;
		int maskIndex = 7 - rem;
		
		ArrayList<Byte> result = new ArrayList<Byte>();
		int index1 = 0;
		int index2 = -1;
		
		// Main Loop
		while (index2 < length) {
			int bite = 0;
			if (masks1[maskIndex] != 0) {
				if (index1 < length) {
					int byte1 = ((int)bytes[index1]) & 0xff;
					bite = (byte1 & masks1[maskIndex]) >> shifts1[maskIndex];
				}
				index1++;
			}
			if (masks2[maskIndex] != 0) {
				if (index2 >= 0) {
					int byte2 = ((int)bytes[index2]) & 0xff;
					bite |= (byte2 & masks2[maskIndex]) << shifts2[maskIndex];
				}
				index2++;
			}
			if (index2 < length) {
				bite |= 0x80;
			}
			result.add((byte)bite);
			if (++maskIndex >= masks1.length) {
				maskIndex = 0;
			}
		}
		
		// Remove leading zeroes
		while (!result.isEmpty() && result.get(0) == (byte)0x80) {
			result.remove(0);
		}
		if (result.isEmpty()) {
			result.add((byte)0);
		}
		encodeState.addAll(result);
	}
	
	private static int[] dmasks1 = {
			0x7f, 0x7e, 0x7c, 0x78, 0x70, 0x60, 0x40
	};
	
	private static int[] dshifts1 = {
			0, 1, 2, 3, 4, 5, 6
	};
	
	private static int[] dmasks2 = {
			0x01, 0x03, 0x07, 0x0f, 0x1f, 0x3f, 0x7f
	};
	
	private static int[] dshifts2 = {
			7, 6, 5, 4, 3, 2, 1
	};
	
	/**
	 * Decode given SDNV as a byte array
	 * @param decodeState - Supplies & returns state of decoding:
	 * <ul>
	 *   <li>buffer Given byte array
	 *   <li> offset Current offset; updated after decode.
	 *   <li> length Length of byte array
	 * </ul>
	 * @return Decoded Byte array
	 */
	public static byte[] sdnvDecodeBytes(DecodeState decodeState)
	throws JDtnException {
		ArrayList<Byte> sdnv = gatherSdnv(decodeState);
		int index1 = sdnv.size() - 1;
		int index2 = index1 - 1;
		int maskIndex = 0;
		
		// Build result in reversed order from lsbits to msbits
		ArrayList<Byte> reversedResult = new ArrayList<Byte>();
		while (index1 >= 0 || index2 >= 0) {
			int bite = 0;
			if (index1 >= 0) {
				bite = (byteToIntUnsigned(sdnv.get(index1)) & dmasks1[maskIndex]) >> dshifts1[maskIndex];
			}
			
			if (index2 >= 0) {
				bite |= (byteToIntUnsigned(sdnv.get(index2)) & dmasks2[maskIndex]) << dshifts2[maskIndex];
				
			}
			index1--;
			index2--;
			
			reversedResult.add(intToByteUnsigned(bite));
			maskIndex++;
			if (maskIndex >= dmasks1.length) {
				// Rollover mask index.  Indexes need to be decremented one extra.
				maskIndex = 0;
				index1--;
				index2--;
			}
		}
		
		// Remove leading zeroes (which are last in the reversedResult)
		while (reversedResult.size() > 1 &&
				reversedResult.get(reversedResult.size() - 1) == 0) {
			reversedResult.remove(reversedResult.size() - 1);
		}
		
		// Copy to result array in reverse order
		byte[] result = new byte[reversedResult.size()];
		int jx = 0;
		for (int ix = reversedResult.size() - 1; ix >= 0; ix--) {
			result[jx++] = reversedResult.get(ix);
		}
		return result;
	}

	/**
	 * Extract the bytes of a SDNV from the given decode buffer.
	 * @param decodeState Given decode buffer.
	 * @return Bytes comprising the SDNV
	 * @throws JDtnException On decode errors, such as falling off the end of
	 * the decode buffer.
	 */
	private static ArrayList<Byte> gatherSdnv(DecodeState decodeState)
	throws JDtnException {
		ArrayList<Byte> result = new ArrayList<Byte>();
		while (true) {
			int bite = decodeState.getByte();
			result.add(intToByteUnsigned(bite));
			if ((bite & 0x80) == 0) {
				break;
			}
		}
		return result;
	}
	
	/**
	 * Convert given ArrayList<Byte> to byte[] array.
	 * @param byteArrayList Given ArrayList<Byte>
	 * @return Resulting byte[] array
	 */
	public static byte[] arrayListToByteArray(ArrayList<Byte> byteArrayList) {
		Byte[] bigByteArray = new Byte[byteArrayList.size()];
		bigByteArray = byteArrayList.toArray(bigByteArray);
		
		// I wish there was an efficient way to convert Byte[] to byte[],
		// but I don't see any way to do it except the hard way.
		byte[] result = new byte[bigByteArray.length];
		for (int ix = 0; ix < bigByteArray.length; ix++) {
			result[ix] = bigByteArray[ix];
		}
		return result;
	}
	
	/**
	 * "Decode" a single byte out of the decoding process.  I.e, just pull
	 * a single byte out of the decode buffer and increment decode offset.
	 * Includes checking to make sure that the decode buffer actually
	 * *contains* the byte.
	 * @param decodeState - Supplies & returns state of decoding:
	 * <ul>
	 *   <li>buffer Given byte array
	 *   <li> offset Current offset; updated after decode.
	 *   <li> length Length of byte array
	 * </ul>
	 * @return The byte pulled
	 * @throws JDtnException if the buffer is empty
	 */
	public static byte decodeByte(DecodeState decodeState) throws JDtnException {
		return Utils.intToByteUnsigned(decodeState.getByte());
	}
	
	/**
	 * Safely transform byte to int; assumes byte is unsigned
	 * @param bite Input byte
	 * @return Resulting int
	 */
	public static int byteToIntUnsigned(byte bite) {
		int result = (int)bite & 0xff;
		return result;
	}
	
	/**
	 * Safely transform int to byte; assumes int is unsigned
	 * @param ent Input int
	 * @return Resulting int
	 */
	public static byte intToByteUnsigned(int ent) {
		byte result = (byte)(ent & 0xff);
		return result;
	}
	
	/**
	 * Get the next 'signficant' event from the pull parser.  I.e., Skip over
	 * all except the actual XML Markup. I.e., we find the next Start Element,
	 * End Element, Start Document, or End Document.
	 * @param parser XML Parser
	 * @return Next 'significant' event
	 * @throws XmlRdParserException errors from the parser
	 * @throws IOException errors from the parser
	 */
	public static XmlRDParser.EventType nextNonTextEvent(XmlRDParser parser)
	throws IOException, XmlRdParserException {
		return parser.next();
	}
	
	/**
	 * Get value for optional String-valued attribute from the parser
	 * @param parser The parser
	 * @param attributeStr The name of the attribute
	 * @return String value if attribute specified, null if attribute not specified.
	 */
	public static String getStringAttribute(
			XmlRDParser parser,
			String attributeStr) {
		try {
			String value = parser.getAttributeValue(attributeStr);
			if (value != null && value.length() > 0) {
				return value;
			}
			return null;
		} catch (XmlRdParserException e) {
			_logger.log(Level.SEVERE, "getStringAttribute()", e);
			return null;
		}
	}
	
	/**
	 * Get value for optional, integer valued attribute from the parser.
	 * @param parser The parser
	 * @param attributeStr The name of the attribute
	 * @param minValue Minimum value for the attribute
	 * @param maxValue Maximum value for the attribute
	 * @return Integer value if attribute specifed, null if attribute not specified.
	 * @throws LtpException On number format error
	 */
	public static Integer getIntegerAttribute(
			XmlRDParser parser, 
			String attributeStr, 
			int minValue, 
			int maxValue)
	throws LtpException {
		try {
			String value = parser.getAttributeValue(attributeStr);
			if (value != null && value.length() > 0) {
				Integer intValue = new Integer(value);
				if (intValue.intValue() < minValue
						|| intValue.intValue() > maxValue) {
					throw new LtpException("Value for " + attributeStr
							+ " is out of range [" + minValue + ", " + maxValue
							+ "]");
				}
				return intValue;

			}
		} catch (NumberFormatException e) {
			throw new LtpException("Value for " + attributeStr
					+ " is not valid integer");
		} catch (XmlRdParserException e) {
			throw new LtpException(e);
		}
		return null;
	}
	
	/**
	 * Get value for optional, long valued attribute from the parser.
	 * @param parser The parser
	 * @param attributeStr The name of the attribute
	 * @param minValue Minimum value for the attribute
	 * @param maxValue Maximum value for the attribute
	 * @return Long value if attribute specifed, null if attribute not specified.
	 * @throws LtpException On number format error
	 */
	public static Long getLongAttribute(
			XmlRDParser parser, 
			String attributeStr, 
			long minValue, 
			long maxValue)
	throws LtpException {
		try {
			String value = parser.getAttributeValue(attributeStr);
			if (value != null && value.length() > 0) {
				Long longValue = new Long(value);
				if (longValue.longValue() < minValue
						|| longValue.longValue() > maxValue) {
					throw new LtpException("Value for " + attributeStr
							+ " is out of range [" + minValue + ", " + maxValue
							+ "]");
				}
				return longValue;

			}
		} catch (NumberFormatException e) {
			throw new LtpException("Value for " + attributeStr
					+ " is not valid long");
		} catch (XmlRdParserException e) {
			throw new LtpException(e);
		}
		return null;
	}

	/**
	 * Get value for optional, double valued attribute from the parser.
	 * @param parser The parser
	 * @param attributeStr The name of the attribute
	 * @param minValue Minimum value for the attribute
	 * @param maxValue Maximum value for the attribute
	 * @return Long value if attribute specifed, null if attribute not specified.
	 * @throws LtpException On number format error
	 */
	public static Double getDoubleAttribute(
			XmlRDParser parser, 
			String attributeStr, 
			double minValue, 
			double maxValue)
	throws LtpException {
		try {
			String value = parser.getAttributeValue(attributeStr);
			if (value != null && value.length() > 0) {
				Double doubleValue = new Double(value);
				if (doubleValue.longValue() < minValue
						|| doubleValue.longValue() > maxValue) {
					throw new LtpException("Value for " + attributeStr
							+ " is out of range [" + minValue + ", " + maxValue
							+ "]");
				}
				return doubleValue;

			}
		} catch (NumberFormatException e) {
			throw new LtpException("Value for " + attributeStr
					+ " is not valid Double");
		} catch (XmlRdParserException e) {
			throw new LtpException(e);
		}
		return null;
	}

	/**
	 * Get value for optional, boolean valued attribute from the parser.
	 * <ul>
	 *   <li>  If the attribute value is specified,
	 *   <ul>
	 *     <li> If it contains the value "up" or "true",
	 *     then the return value is Boolean.true.  
	 *     <li> Else the return value is Boolean.false.
	 *   </ul>
	 *   <li> Else the return value is null.
	 * </ul>
	 * @param parser The parser
	 * @param attributeStr The name of the boolean valued attribute.
	 * @return what I said
	 */
	public static Boolean getBooleanAttribute(
			XmlRDParser parser,
			String attributeStr) {
		try {
			String value = parser.getAttributeValue(attributeStr);
			if (value != null && value.length() > 0) {
				Boolean boolValue = null;
				if (value.equalsIgnoreCase("up")) {
					boolValue = new Boolean(true);
				} else {
					boolValue = new Boolean(Boolean.parseBoolean(value));
				}
				return boolValue;
			}
		} catch (XmlRdParserException e) {
			_logger.log(Level.SEVERE, "getBooleanAttribute()", e);
		}
		return null;
	}
	
	/**
	 * Convert given InetAddress to a parseable String form.  The behavior of
	 * InetAddress.toString() is tricky, it returns "hostname/numeric" or
	 * "/numeric" if address is unresolved.  We want just the "numeric"
	 * part so that it can be parsed later on.
	 * @param address Given address
	 * @return Parseable address string
	 */
	public static String inetAddressString(InetAddress address) {
		String addrStr = address.toString();
		String[] words = addrStr.split("/");
		if (words.length == 2) {
			// hostname / numeric_address_str
			return words[1];
		} else {
			return addrStr;
		}
	}
	
	/**
	 * Hex Dump an array of bytes
	 * @param bytes Given array of bytes
	 * @param addNewLines whether to do multi-line dump
	 * @return String containing dump
	 */
	public static String dumpBytes(byte[] bytes, boolean addNewLines) {
		return dumpBytes("", bytes, 0, bytes.length, addNewLines);
	}
	
	/**
	 * Hex Dump an array of bytes
	 * @param bytes Given array of bytes
	 * @param offset Starting offset
	 * @param length Ending offset - 1
	 * @param addNewLines whether to do multi-line dump
	 * @return String containing dump
	 */
	public static String dumpBytes(byte[] bytes, int offset, int length, boolean addNewLines) {
		return dumpBytes("", bytes, offset, length, addNewLines);
	}
	
	/**
	 * Multiline Hex Dump an array of bytes
	 * @param indent Indentation characters
	 * @param bytes Given array of bytes
	 * @param offset Starting offset
	 * @param length Ending offset - 1
	 * @return String containing dump
	 */
	public static String dumpBytes(String indent, byte[] bytes, int offset, int length) {
		return dumpBytes(indent, bytes, offset, length, true);
	}
	
	/**
	 * Hex Dump an array of bytes
	 * @param indent Indentation characters
	 * @param bytes Given array of bytes
	 * @param offset starting offset
	 * @param length length to dump
	 * @param addNewLines whether to do multi-line dump
	 * @return String containing dump
	 */
	public static String dumpBytes(String indent, byte[] bytes, int offset, int length, boolean addNewLines) {
		StringBuffer sb = new StringBuffer();
		for (int ix = offset; ix < length; ix += 16) {
			for (int jx = 0; jx < 16; jx++) {
				if (ix + jx >= length) {
					break;
				}
				if (jx == 0) {
					sb.append(indent);
				}
				if (ix + jx == length - 1) {
					sb.append(String.format("%02x", bytes[ix + jx]));
				} else {
					sb.append(String.format("%02x.", bytes[ix + jx]));
				}
			}
			if (addNewLines) {
				sb.append("\n");
			}
		}
		return sb.toString();
	}
	
	public static final int N_BYTES_LONG = Long.SIZE / 8;
	
	/**
	 * Convert given long to a byte array by extracting individual bytes from
	 * the long.  This is done in Big-Endian byte order.
	 * @param inLong Given long
	 * @return Resulting byte array
	 */
	public static byte[] longToByteArray(long inLong) {
		byte[] result = new byte[N_BYTES_LONG];
		for (int ix = N_BYTES_LONG - 1; ix >= 0; ix--) {
			result[ix] = (byte)(inLong & 0xff);
			inLong >>= 8;
		}
		return result;
	}
	
	/**
	 * Convert bytes from given byte array to long
	 * @param bytes Given byte array
	 * @param offset Starting offset into byte array
	 * @return Resulting long
	 */
	public static long byteArrayToLong(byte[] bytes, int offset) {
		long result = 0;
		for (int ix = 0; ix < N_BYTES_LONG; ix++) {
			result <<= 8;
			result |= (long)(bytes[ix + offset] & 0xff);
		}
		return result;
	}
	
	public static final int N_BYTES_INT = Integer.SIZE / 8;
	
	/**
	 * Convert given int to a byte array by extracting individual bytes from
	 * the int.  This is done in Big-Endian byte order.
	 * @param inInt Given int
	 * @return Resulting byte array
	 */
	public static byte[] intToByteArray(int inInt) {
		return intToByteArray(inInt, N_BYTES_INT);
	}
	
	/**
	 * Convert given int to a byte array by extracting individual bytes from
	 * the int.  This is done in Big-Endian byte order.  This allows you to
	 * specify the exact number of bytes in the encoding.
	 * @param inInt Given int
	 * @param nBytes Number of bytes in the encoding.
	 * @return Resulting byte array
	 */
	public static byte[] intToByteArray(int inInt, int nBytes) {
		byte[] result = new byte[nBytes];
		for (int ix = nBytes - 1; ix >= 0; ix--) {
			result[ix] = intToByteUnsigned(inInt & 0xff);
			inInt >>= 8;
		}
		return result;
	}
	
	/**
	 * Convert bytes from given byte array to int
	 * @param bytes Given byte array
	 * @param offset Starting offset into byte array
	 * @return Resulting int
	 */
	public static int byteArrayToInt(byte[] bytes, int offset) {
		return byteArrayToInt(bytes, offset, N_BYTES_INT);
	}
	
	/**
	 * Convert bytes from given byte array to int
	 * @param bytes Given byte array
	 * @param offset Starting offset into byte array
	 * @param length Number of bytes in given byte array to convert
	 * @return Resulting int
	 */
	public static int byteArrayToInt(byte[] bytes, int offset, int length) {
		int result = 0;
		for (int ix = 0; ix < length; ix++) {
			result <<= 8;
			result |= (int)(bytes[ix + offset] & 0xff);
		}
		return result;
	}
	
	/**
	 * Compare individual bytes in two arrays
	 * @param b1 First array
	 * @param b2 Second array
	 * @return True if both arrays have same size and all bytes equal
	 */
	public static boolean compareByteArrays(byte[] b1, byte[] b2) {
		if (b1.length != b2.length) {
			return false;
		}
		for (int ix = 0; ix < b1.length; ix++) {
			if (b1[ix] != b2[ix]) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Compare for equality two byte arrays that were originally decoded from
	 * SDNVs, or that are generally encoded into SDNVs when sent elsewhere.
	 * Because leading zeroes of such byte arrays are not significant, its
	 * best if we compare them by encoding each to an SDNV and then comparing
	 * the encoded forms, which is what we do. 
	 * @param b1 First byte array
	 * @param b2 Second byte array
	 * @return True if b1 compares equal to b2
	 */
	public static boolean compareSdnvDecodedArrays(byte[] b1, byte[] b2) {
		try {
			EncodeState es1 = new EncodeState();
			sdnvEncodeBytes(b1, es1);
			es1.close();
			EncodeState es2 = new EncodeState();
			sdnvEncodeBytes(b2, es2);
			es2.close();
			return es1.equals(es2);
		} catch (JDtnException e) {
			// Encoding errors don't happen on in-memory encoding.
			_logger.severe("FIX THIS!");
			return false;
		}
	}
	
	/**
	 * Generate hashCode for a byte array that is an SDNV.
	 * @param bites Given byte array
	 * @return Hash Code
	 */
	public static int sdnvDecodedArrayHashCode(byte[] bites) {
		try {
			EncodeState encodeState = new EncodeState();
			sdnvEncodeBytes(bites, encodeState);
			return encodeState.hashCode();
		} catch (JDtnException e) {
			// Encoding errors don't happen on in-memory encoding.
			_logger.log(Level.SEVERE, "FIX THIS!", e);
			return 0;
		}
	}
	
	/**
	 * Compute hash code for a byte array
	 * @param b Given byte array
	 * @return Hash code
	 */
	public static int byteArrayHashCode(byte[] b) {
		int result = 0;
		for (int ix = 0; ix < b.length; ix++) {
			result += b[ix];
		}
		return result;
	}
	
	/**
	 * Copy bytes from source byte array to dest byte array
	 * @param src Source byte array
	 * @param srcOffset Source byte array offset
	 * @param dest Destination byte array
	 * @param destOffset Destination byte array offset
	 * @param length Number of bytes to copy
	 * @throws JDtnException 
	 */
	public static void copyBytes(
			byte[] src, 
			int srcOffset, 
			byte[] dest, 
			int destOffset, 
			int length) throws JDtnException {
		
		if (srcOffset < 0 ||
			srcOffset + length > src.length) {
			throw new JDtnException(
					"Invalid srcOffset=" + srcOffset +
					" Length=" + length +
					" src.length=" + src.length);
		}
		if (destOffset < 0 ||
			destOffset + length > dest.length) {
			throw new JDtnException(
					"Invalid destOffset=" + destOffset +
					" Length=" + length +
					" dest.length=" + dest.length);
		}
		System.arraycopy(src, srcOffset, dest, destOffset, length);
	}
	
	/** Sizeof buffer used in copyFileToOutputStream */
	public static final int BUFFER_LEN = 1000;
	
	/**
	 * Copy contents of given File to given OutputStream
	 * @param file Given File
	 * @param fileOutput Given OutputStream
	 * @throws IOException on I/O errors
	 */
	public static void copyFileToOutputStream(MediaRepository.File file, MediaRepository.File fileOutput)
	throws IOException {
		InputStream fis = file.inputStream();
		byte[] buffer = new byte[BUFFER_LEN];
		
		int len = (int)file.length();
		try {
			while (len > 0) {
				int readLen = BUFFER_LEN;
				if (len < BUFFER_LEN) {
					readLen = len;
				}
				int nRead = fis.read(buffer, 0, readLen);
				if (nRead == -1) {
					// EOF
					break;
				}
				BlobAndBundleDatabase.getInstance().appendByteArrayToFile(buffer,0,nRead,fileOutput);
				len -= nRead;
			}
			fileOutput.getConnection().commit();
		} catch (Exception e) {
			_logger.severe(
					"Copying file " + file.getAbsolutePath() + ": " + 
					e.getMessage());
			throw new Error("", e);
		} finally {
			fis.close();
		}
	}
	
}
