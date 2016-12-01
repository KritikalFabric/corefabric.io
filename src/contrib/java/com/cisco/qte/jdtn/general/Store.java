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
import java.util.logging.Logger;

import com.cisco.qte.jdtn.component.AbstractStartableComponent;


/**
 * Manages DTN Storage
 * Original concept was that this would enforce BP
 * Retention constraints on amount of space used.  Instead,
 * BPProtocolAgent does that.  This still serves the useful purporse of
 * organizing BP storage and generation of filenames.
 */
public class Store extends AbstractStartableComponent {
	private static final Logger _logger =
		Logger.getLogger(Store.class.getCanonicalName());
	
	private static Store _instance = null;
	private static long _serialNumber = 0;
	private static final Object _syncObj = new Object();
	
	/**
	 * Get singleton instance
	 * @return Singleton instance
	 */
	public static Store getInstance() {
		if (_instance == null) {
			_instance = new Store();
		}
		return _instance;
	}
	
	/**
	 * Private constructor
	 */
	private Store() {
		super("Store");
	}
	
	@Override
	protected void startImpl() {
		// Nothing
	}
	
	@Override
	protected void stopImpl() {
		// Nothing
	}
	
	/**
	 * Clean the store -- 
	 * if the configured Storage Directory doesn't exist, create it
	 * delete all files in the configured Storage Directory
	 */
	public void clean() {
		File dir = new File(getStorageDirectory());
		if (dir.isDirectory()) {
			clean(dir);
		}
		if (!dir.exists() && !dir.mkdirs()) {
			_logger.severe("Cannot create Storage Directory: " + getStorageDirectory());
		}
	}
	
	private void clean(File dir) {
		// Don't clean the media repository if it happens to be a subdir of
		// the store.
		if (dir.equals(new File(GeneralManagement.getInstance().getMediaRepositoryPath()))) {
			return;
		}
		File[] files = dir.listFiles();
		for (File file : files) {
			if (file.isDirectory()) {
				clean(file);
			} else if (!file.delete()) {
				_logger.severe("Cannot delete Storage File: " + file.getAbsolutePath());
			}
		}
		dir.delete();
	}
	
	
	/**
	 * Get the configured Storage Directory.
	 * @return Configured Storage Directory.
	 */
	public String getStorageDirectory() {
		String result = GeneralManagement.getInstance().getStoragePath();
		File file = new File(result);
		
		// The Storage Directory might have been reconfigured on the fly.
		// So make sure it exists and create it if it doesn't.
		if (!file.exists()) {
			if (!file.mkdir()) {
				_logger.severe("Cannot create Storage directory");
			}
		}
		return result;
	}
	
	/**
	 * Create a File to hold Segment data
	 * @return The File
	 * @throws JDtnException if we've overrun quotas on Segment data files
	 */
	public File createNewSegmentFile() throws JDtnException {
		File file = new File(getStorageDirectory(), getUniqueFilename("Segment"));
		return file;
	}
	
	/**
	 * Create a File to hold Block data
	 * @return The File
	 * @throws JDtnException if we've overrun quotas on Block data files
	 */
	public File createNewBlockFile() throws JDtnException {
		File file = new File(getStorageDirectory(), getUniqueFilename("Block"));
		return file;
	}
	
	/**
	 * Create a File to hold Payload data
	 * @return The File
	 * @throws JDtnException if we've overrun quotas on Payload data files
	 */
	public File createNewPayloadFile() throws JDtnException {
		File file = new File(getStorageDirectory(), getUniqueFilename("Payload"));
		return file;
	}
	
	/**
	 * Create a temporary file
	 * @return The File
	 * @throws JDtnException if we've overrun quotas on Temporary data Files
	 */
	public File createNewTemporaryFile() throws JDtnException {
		File file = new File(getStorageDirectory(), getUniqueFilename("Tmp"));
		return file;
	}
	
	/**
	 * Create a File to hold Bundle data
	 * @return The File
	 * @throws JDtnException if we've overrun quotas on Bundle data files
	 */
	public File createBundleFile() throws JDtnException {
		File file = new File(getStorageDirectory(), getUniqueFilename("Bundle"));
		return file;
	}
	
	public String getUniqueFilename(String prefix) throws JDtnException {
		synchronized (_syncObj) {
			return prefix + _serialNumber++;
		}
	}
}
