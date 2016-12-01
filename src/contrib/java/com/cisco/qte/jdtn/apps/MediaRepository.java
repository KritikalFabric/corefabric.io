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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.general.GeneralManagement;

/**
 * The media respository.  When other DTN nodes send media to this DTN
 * node, it stores the media into this Media Repository.  The storage
 * for the repository consists of file space in the server on which this
 * DTN node runs.
 * Media filename format and physical storage layout:
 * <pre>
 * <mediaPath>/<appName>/yyyy-mmm-dd-hh-mm-ss--SSS-from.extension
 * </pre>
 * where:
 * <ul>
 *   <li>mediaPath - is the base of the physical file space where media get stored.
 *   <li>appName - Identifies the application that originated the media and its
 *                 format.  Possibilities include (but are not limited to):
 *   <ul>
 *     <li>/text
 *     <li>/photo
 *     <li>/video
 *     <li>/voice
 *   </ul>
 * </ul>
 */
public class MediaRepository extends AbstractStartableComponent {

	private static final Logger _logger =
		Logger.getLogger(MediaRepository.class.getCanonicalName());
	
	private static final MediaRepository _instance = new MediaRepository();
	
	/**
	 * Get singleton instance
	 * @return Singleton instance
	 */
	public static MediaRepository getInstance() {
		return _instance;
	}
	
	private final SimpleDateFormat _formatter =
		new SimpleDateFormat("yyyy-MMM-dd-hh-mm-ss-SSS");
	private final ArrayList<MediaEventRegistration> _listeners =
		new ArrayList<MediaEventRegistration>();
	
	/**
	 * Constructor
	 */
	private MediaRepository() {
		super("MediaRepository");
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
	 * Clean the Media Repository of all files
	 */
	public void clean() {
		File dir = new File(GeneralManagement.getInstance().getMediaRepositoryPath());
		if (dir.isDirectory()) {
			clean(dir);
		}
		if (!dir.mkdirs()) {
			_logger.severe(
					"Could not create the media Repository at path " +
					GeneralManagement.getInstance().getMediaRepositoryPath());
		}
	}
	
	/**
	 * Internal method to clean given directory by removing all files,
	 * recursively clean all subdirectories, and remove given directory.
	 * @param dir Given directory
	 */
	private void clean(File dir) {
		File[] files = dir.listFiles();
		for (File file : files) {
			if (file.isDirectory()) {
				clean(file);
			} else {
				if (!file.delete()) {
					_logger.severe("Could not remove file " + file);
				}
			}
		}
		if (!dir.delete()) {
			_logger.severe("Could not remove directory " + dir);
		}
	}
	
	/**
	 * Form the full pathname filename for a media file.  This is called from
	 * one of the DTN receive threads when they receive a bunlde and wish to
	 * store the media bundle file into the media repository.
	 * @param appName App Name (like photo, video, text, voice)
	 * @param dateReceived Date data received
	 * @param from Indication of whom the data was from
	 * @param extension Filename extension (like .jpg, .3gp. txt)
	 * @return Full pathname in the media repository
	 */
	public File formMediaFilename(
			String appName, 
			Date dateReceived, 
			String from, 
			String extension) {
		
        String fileName = GeneralManagement.getInstance().getMediaRepositoryPath() +
        	File.separator +
        	appName +
        	File.separator +
        	_formatter.format(dateReceived) +
        	"-" +
        	from +
        	extension;
        File result = new File(fileName);
        
        // Make sure directories exist
        File parent = result.getParentFile();
        if (!parent.exists()) {
	        if (!result.getParentFile().mkdirs()) {
	        	_logger.severe(
	        			"MediaRepository: Failed to make parent directories for " +
	        			result.getAbsolutePath());
	        }
        }
        return result;
	}
	
	/**
	 * Move a file from one place to another.  Used to move files from the
	 * bundle directory to the media repository.  Called from DTN Apps to
	 * move a received media file into the repository.
	 * @param appName The Name of the app requesting this action
	 * @param fromFilename Source filename - file in bundle directory
	 * @param mediaFilename - Media filename - file in media directory
	 */
	public void moveFile(String appName, String fromFilename, File mediaFilename) {
		File f = new File(fromFilename);
		if (!f.renameTo(mediaFilename)) {
        	_logger.severe(
        			"MediaRepository: Failed to rename " +
        			fromFilename + " to " +
        			mediaFilename.getAbsolutePath());
		} else {
			notifyMediaEvent(
					true, 
					new MediaDescription(
							appName, 
							mediaFilename.getAbsolutePath()));
		}
	}
	
	/**
	 * Write given Byte Array to given media repository file.  Called from
	 * DTN Apps to create a media file in the repository.
	 * @param appName The Name of the app requesting this action
	 * @param bytes Given byte array
	 * @param offset Starting offset
	 * @param length Length to write
	 * @param mediaFilename media repository file to write to
	 */
	public void spillByteArrayToFile(
			String appName,
			byte[] bytes, 
			int offset, 
			int length, 
			File mediaFilename) {
		FileOutputStream fos = null;
		boolean success = false;
		
		try {
			fos = new FileOutputStream(mediaFilename);
			fos.write(bytes, offset, length);
			success = true;
			
		} catch (IOException e) {
			_logger.log(Level.SEVERE, "spillByteArrayToFile", e);
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					// Ignore
				}
			}
		}
		if (success) {
			notifyMediaEvent(
					true, 
					new MediaDescription(
							appName, 
							mediaFilename.getAbsolutePath()));
		}
	}
	
	/**
	 * Internal method to notify registered listeners when files are added to
	 * or removed from the media respository.
	 * @param addition True => file was added; otherwise, file was removed
	 * @param mediaDescription Description of the file added
	 */
	private void notifyMediaEvent(boolean addition, MediaDescription mediaDescription) {
		ArrayList<MediaEventRegistration> tempList = null;
		synchronized (_listeners) {
			tempList = new ArrayList<MediaEventRegistration>(_listeners);
		}
		for (MediaEventRegistration registration : tempList) {
			if (registration.appName.equals(mediaDescription.appName)) {
				if (addition) {
					registration.listener.onMediaAdded(mediaDescription);
				} else {
					registration.listener.onMediaRemoved(mediaDescription);
				}
			}
		}
	}
	
	/**
	 * Add given MediaRepositoryListener to be notified upon media repository
	 * events.  Called from DTN Service Users.
	 * @param appName AppName that service user is interested in notifications from.
	 * @param listener Service User's methods to call on media repository events.
	 */
	public void addMediaRepositoryListener(
			String appName,
			MediaRepositoryListener listener) {
		MediaEventRegistration registration =
			new MediaEventRegistration(appName, listener);
		synchronized (_listeners) {
			_listeners.add(registration);
		}
	}
	
	/**
	 * Remove given MediaRepositoryListener previously registered as a Media
	 * Repository Listener.  Called from DTN Service Users.
	 * @param appName AppName that service user registered for
	 * @param listener Service User's previously registered methods
	 */
	public void removeMediaRepositoryListener(
			String appName,
			MediaRepositoryListener listener) {
		synchronized (_listeners) {
			for (MediaEventRegistration registration : _listeners) {
				if (registration.appName.equals(appName) &&
					registration.listener == listener) {
					_listeners.remove(registration);
					break;
				}
			}
			
		}
	}
	
	/**
	 * Get a list of all Media files for the given AppName in the Media
	 * Repository.  Called from DTN Service users to find out what's in the
	 * media repository.
	 * @param appName Given AppName
	 * @return array of MediaDescription; one MediaDescription for each media
	 * file.
	 */
	public MediaDescription[] getMediaFiles(String appName) {
		ArrayList<MediaDescription> fileList = new ArrayList<MediaDescription>();
		File dir = new File(GeneralManagement.getInstance().getMediaRepositoryPath(), appName);
		File[] files = dir.listFiles();
		if (files == null) {
			return new MediaDescription[0];
		}
		for (File file : dir.listFiles()) {
			if (!file.isDirectory()) {
				MediaDescription mediaDescription =
					new MediaDescription(appName, file.getAbsolutePath());
				fileList.add(mediaDescription);
			}
		}
		MediaDescription[] result = new MediaDescription[fileList.size()];
		result = fileList.toArray(result);
		return result;
	}
	
	/**
	 * Remove a Media File from the Repository.  Called from DTN Service users
	 * when it is determined that a media file can be removed.
	 * @param mediaDescription The Description of the Media File to delete
	 * @return True if deletion successful, false otherwise
	 */
	public boolean removeMedia(MediaDescription mediaDescription) {
		File file = new File(mediaDescription.mediaFilePath);
		if (file.exists()) {
			return file.delete();
		} else {
			return false;
		}
	}
	
	/**
	 * A little class which keeps track of MediaRepositoryListener registrations.
	 */
	public class MediaEventRegistration {
		public String appName;
		public MediaRepositoryListener listener;
		
		public MediaEventRegistration(
				String aAppName, 
				MediaRepositoryListener aListener) {
			appName = aAppName;
			listener = aListener;
		}
	}
}
