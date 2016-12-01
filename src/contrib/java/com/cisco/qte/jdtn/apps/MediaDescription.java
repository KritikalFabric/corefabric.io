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
package com.cisco.qte.jdtn.apps;

import java.io.Serializable;

/**
 * @author jimbo
 * A description of a file in the Media Repository
 */
public class MediaDescription implements Serializable {
	private static final long serialVersionUID = 1L;

	/** App Name: one of TextApp.APP_NAME, etc */
	public String appName;
	/** File path of the media file */
	public String mediaFilePath;
	
	/**
	 * Default constructor, leaves all fields null
	 */
	public MediaDescription() {
		// Nothing
	}
	
	/**
	 * Constructor; fills fields from arguments
	 * @param aAppName App Name; one of TextApp.APP_NAME, etc 
	 * @param aMediaFilePath File path of the media file
	 */
	public MediaDescription(String aAppName, String aMediaFilePath) {
		appName = aAppName;
		mediaFilePath = aMediaFilePath;
	}
	
	/**
	 * Get the Date Received as a String from the given Media Description
	 * @return Date Received as a String
	 */
	public String getDateReceived() {
		// First, split off path info
		String[] words = mediaFilePath.split("/");
		// <mediaPath>/<appName>/yyyy-mmm-dd-hh-mm-ss-SSS-from.extension
		//     0...   /  n - 1  /     n
		if (words == null || words.length < 3) {
			return "Unknown-Date";
		}
		int ix = words.length - 1;
		String[] words2 = words[ix].split("-");
		
		// yyyy-mmm-dd-hh-mm-ss-SSS-from.extension
		//  0  - 1 - 2-3 -4 -5 - 6 -   7
		if (words2 == null || words2.length < 7) {
			return "Unknown-Date";
		}
		return words2[0] + '-' + words2[1] + '-' + words2[2] + '-' + 
			words2[3] + '-' +  words2[4] + '-' + words2[5] + '-' +
			words2[6];
	}
	
	/**
	 * Get the 'from' node name, i.e., the node which sourced the media,
	 * from the given Media Description
	 * @return 'from' node name
	 */
	public String getFrom() {
		// First, split off path info
		String[] words = mediaFilePath.split("/");
		// <mediaPath>/<appName>/yyyy-mmm-dd-hh-mm-ss-SSS-from.extension
		//     0...   /  n - 1  /     n
		if (words == null || words.length < 3) {
			return "Unknown-Node";
		}
		int ix = words.length - 1;		
		String[] words2 = words[ix].split("-");
		// yyyy-mmm-dd-hh-mm-ss-SSS-from.extension
		//  0    1   2 3  4  5   6 - 7
		if (words2 == null || words2.length < 8) {
			return "Unknown-Node";
		}
		String[] words3 = words2[7].split("\\.");
		// from.extension
		//   0      1
		if (words3 == null || words3.length < 2) {
			return "Unknown-Node";
		}
		return words3[0];
	}
	
	@Override
	public String toString() {
		return "From: " + getFrom() + "; Date: " + getDateReceived();
	}
	
}
