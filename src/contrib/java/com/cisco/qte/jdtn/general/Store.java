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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.apps.MediaRepository;
import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import org.kritikal.fabric.contrib.jdtn.BlobAndBundleDatabase;


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
	private static final AtomicLong _serialNumber = new AtomicLong(0l);
	private static final String _serialUUID = UUID.randomUUID().toString();

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
		BlobAndBundleDatabase.getInstance().cleanMediaRepository(BlobAndBundleDatabase.StorageType.STORE);
	}
	
	private void clean(MediaRepository.File dir) {
		BlobAndBundleDatabase.getInstance().cleanMediaRepository(BlobAndBundleDatabase.StorageType.STORE, dir);
	}
	
	
	/**
	 * Get the configured Storage Directory.
	 * @return Configured Storage Directory.
	 */
	public String getStorageDirectory() {
		return "$store";
	}
	
	/**
	 * Create a File to hold Segment data
	 * @return The File
	 * @throws JDtnException if we've overrun quotas on Segment data files
	 */
	public MediaRepository.File createNewSegmentFile() throws JDtnException {
		MediaRepository.File file = new MediaRepository.File(BlobAndBundleDatabase.StorageType.STORE, getUniqueFilename("Segment"));
		return file;
	}
	
	/**
	 * Create a File to hold Block data
	 * @return The File
	 * @throws JDtnException if we've overrun quotas on Block data files
	 */
	public MediaRepository.File createNewBlockFile() throws JDtnException {
		MediaRepository.File file = new MediaRepository.File(BlobAndBundleDatabase.StorageType.STORE, getUniqueFilename("Block"));
		return file;
	}
	
	/**
	 * Create a File to hold Payload data
	 * @return The File
	 * @throws JDtnException if we've overrun quotas on Payload data files
	 */
	public MediaRepository.File createNewPayloadFile() throws JDtnException {
		MediaRepository.File file = new MediaRepository.File(BlobAndBundleDatabase.StorageType.STORE, getUniqueFilename("Payload"));
		return file;
	}
	
	/**
	 * Create a temporary file
	 * @return The File
	 * @throws JDtnException if we've overrun quotas on Temporary data Files
	 */
	public MediaRepository.File createNewTemporaryFile() throws JDtnException {
		MediaRepository.File file = new MediaRepository.File(BlobAndBundleDatabase.StorageType.STORE, getUniqueFilename("Tmp"));
		return file;
	}
	
	/**
	 * Create a File to hold Bundle data
	 * @return The File
	 * @throws JDtnException if we've overrun quotas on Bundle data files
	 */
	public MediaRepository.File createBundleFile() throws JDtnException {
		MediaRepository.File file = new MediaRepository.File(BlobAndBundleDatabase.StorageType.STORE, getUniqueFilename(
				"Bundle"));
		return file;
	}
	
	public String getUniqueFilename(String prefix) throws JDtnException {
		StringBuilder sb = new StringBuilder();
		sb.append(prefix).append('+');
		sb.append(_serialUUID).append('+');
		sb.append(_serialNumber.getAndIncrement());
		return sb.toString();
	}
}
