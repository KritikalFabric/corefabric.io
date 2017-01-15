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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import org.kritikal.fabric.contrib.jdtn.BlobAndBundleDatabase;

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

	/**
	 * A class allowing us to do file-like operations on database objects.
	 */
	public final static class File {
		public File(final BlobAndBundleDatabase.StorageType storageType, final String fileName) {
			if (fileName.contains("'")) throw new Error("Invalid filename");
			this.storageType = storageType;
			this.fileName = fileName;
		}
		final BlobAndBundleDatabase.StorageType storageType;
		final String fileName;
		public final String getAbsolutePath() { return fileName; }
		public final String getName() {
			int x = fileName.lastIndexOf('/');
			if (x >= 0) return fileName.substring(x+1);
			return fileName;
		}
		public final BlobAndBundleDatabase.StorageType getStorageType() { return storageType; }
		public final boolean exists(Connection con) {
			return BlobAndBundleDatabase.getInstance().mediaFileExists(con, this);
		}
		public final long length(Connection con) {
			return BlobAndBundleDatabase.getInstance().mediaFileLength(con, this);
		}
		public final boolean delete(Connection con) {
			return BlobAndBundleDatabase.getInstance().mediaFileDelete(con, this);
		}
		public final InputStream inputStream(Connection con) {
			byte[] data = BlobAndBundleDatabase.getInstance().mediaGetBodyData(con, this);
			if (data == null) return null;
			return new ByteArrayInputStream(data, 0, data.length);
		}
		public final boolean equals(Object other) {
			File o = (File)other;
			return this.storageType == o.storageType && this.fileName.equals(o.fileName);
		}
		long oid = 0l;
		public long getOid() { return oid; }
		public void setOid(long oid) { this.oid = oid; }

		@Override
		public int hashCode() {
			return this.fileName.hashCode();
		}
	}

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
		Connection con = BlobAndBundleDatabase.getInstance().getInterface().createConnection();
		try {
			BlobAndBundleDatabase.getInstance().cleanMediaRepository(con, BlobAndBundleDatabase.StorageType.MEDIA);
			try { con.commit(); } catch (SQLException e) {
				_logger.warning(e.getMessage());
			}
		}
		finally {
			try { con.close(); } catch (SQLException ignore) {}
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
	public MediaRepository.File formMediaFilename(
			String appName, 
			Date dateReceived, 
			String from, 
			String extension) {
		
        String fileName =
        	"/" +
        	appName +
        	"/" +
        	_formatter.format(dateReceived) +
        	"-" +
        	from +
        	extension;

        MediaRepository.File result = new MediaRepository.File(BlobAndBundleDatabase.StorageType.MEDIA, fileName);
        return result;
	}
	
	/**
	 * Move a file from one place to another.  Used to move files from the
	 * bundle directory to the media repository.  Called from DTN Apps to
	 * move a received media file into the repository.
	 * @param appName The Name of the app requesting this action
	 * @param fromFilename StorageType filename - file in bundle directory
	 * @param mediaFilename - Media filename - file in media directory
	 */
	public void moveFile(String appName, String fromFilename, File mediaFilename) {
		MediaRepository.File fromFile = new File(BlobAndBundleDatabase.StorageType.MEDIA, fromFilename);
		boolean success = false;
		Connection con = BlobAndBundleDatabase.getInstance().getInterface().createConnection();
		try {
			success = BlobAndBundleDatabase.getInstance().renameTo(con, fromFile, mediaFilename);
			try { con.commit(); } catch (SQLException e) {
				_logger.warning(e.getMessage());
				success = false;
			}
		}
		finally {
			try { con.close(); } catch (SQLException ignore) {}
		}
		if (!success) {
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
		boolean success = false;
		Connection con = BlobAndBundleDatabase.getInstance().getInterface().createConnection();
		try {
			success = BlobAndBundleDatabase.getInstance().spillByteArrayToFile(con, appName, bytes, offset, length, mediaFilename);
			try { con.commit(); } catch (SQLException e) {
				_logger.warning(e.getMessage());
				success = false;
			}
		}
		finally {
			try { con.close(); } catch (SQLException ignore) {}
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
		File[] files = null;
		Connection con = BlobAndBundleDatabase.getInstance().getInterface().createConnection();
		try {
			files = BlobAndBundleDatabase.getInstance().listFiles(con, BlobAndBundleDatabase.StorageType.MEDIA, "/" + appName + "/");
			try { con.commit(); } catch (SQLException e) {
				_logger.warning(e.getMessage());
				files = null;
			}
		}
		finally {
			try { con.close(); } catch (SQLException ignore) {}
		}
		if (files == null) {
			return new MediaDescription[0];
		}
		for (File file : files) {
			MediaDescription mediaDescription =
				new MediaDescription(appName, file.getAbsolutePath());
			fileList.add(mediaDescription);
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
		File file = new File(BlobAndBundleDatabase.StorageType.MEDIA, mediaDescription.mediaFilePath);
		Connection con = BlobAndBundleDatabase.getInstance().getInterface().createConnection();
		try {
			BlobAndBundleDatabase.getInstance().cleanMediaRepository(con, BlobAndBundleDatabase.StorageType.MEDIA, file);
			try { con.commit(); } catch (SQLException e) {
				_logger.warning(e.getMessage());
				return false;
			}
			return true;
		}
		finally {
			try { con.close(); } catch (SQLException ignore) {}
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
