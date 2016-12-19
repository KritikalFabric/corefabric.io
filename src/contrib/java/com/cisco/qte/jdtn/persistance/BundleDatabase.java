/**
Copyright (c) 2011, Cisco Systems, Inc.
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
package com.cisco.qte.jdtn.persistance;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.apps.MediaRepository;
import com.cisco.qte.jdtn.bp.Bundle;
import com.cisco.qte.jdtn.bp.EidScheme;
import com.cisco.qte.jdtn.component.AbstractStartableComponent;
import com.cisco.qte.jdtn.general.DecodeState;
import com.cisco.qte.jdtn.general.EncodeState;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Link;
import com.cisco.qte.jdtn.general.LinksList;
import com.cisco.qte.jdtn.general.Platform;
import com.cisco.qte.jdtn.general.Store;
import com.cisco.qte.jdtn.general.Utils;
import com.cisco.qte.jdtn.general.XmlRDParser;
import com.cisco.qte.jdtn.general.XmlRdParserException;
import org.kritikal.fabric.contrib.jdtn.BlobAndBundleDatabase;

/**
 * The Bundle Database; persistent store for state information about each Bundle
 * under our custody.  We maintain:
 * <ul>
 *   <li>A sqlite database summarizing the state of each Bundle.
 *   <li>The Bundles themselves are encoding into files, and the
 *       DB references these files.
 * </ul>
 * <p/>
 * Properties consist of:
 * <ul>
 *   <li>StartClean - Whether to empty the database (true) or not on startup.
 * </ul>
 * <p/>
 * There is some quirkiness in this component.  We have to dynamically
 * load in the JDBC Connector Driver, since which one we use depends on the
 * platform.  This leads to some restrictions on when properties of this
 * component can be set (i.e., all sets() must occur before start()).
 */
public class BundleDatabase extends AbstractStartableComponent {
	private static final boolean START_CLEAN_DEFAULT = false;
	
	private static final String START_CLEAN_ATTR = "startClean";
	
	private static final Logger _logger =
		Logger.getLogger(BundleDatabase.class.getCanonicalName());
	
	//private static BundleDatabase _instance = null;
	
	/** StartClean property */
	private boolean _startClean = START_CLEAN_DEFAULT;
	
	protected DBInterface _dbInterface = null;
	
	/**
	 * Get singleton instance of this component
	 * @return Singleton instance
	 */
	//public static BundleDatabase getInstance() {
	//	if (_instance == null) {
	//		_instance = new BundleDatabase();
	//	}
	//	return _instance;
	//}
	public static BundleDatabase getInstance() {
		return BlobAndBundleDatabase.getInstance();
	}
	
	/**
	 * Private constructor
	 */
	protected BundleDatabase() {
		super("BundleDatabase");
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("BundleDatabase");
		}
		setDefaults();
	}
	
	/**
	 * Set default values for properties we own
	 */
	public void setDefaults() {
		_startClean = START_CLEAN_DEFAULT;
	}
	
	/**
	 * Get values for the properties we own from given XML parser.  We do
	 * not advance the state of the parser, we just retrieve attributes.
	 * Note that this should be called before this component is started.
	 * Properties:
	 * <ul>
	 *   <li>startClean - whether to start with a clean database
	 * </ul>
	 * @param parser Given XML parser
	 * @throws IOException On I/O errors
	 * @throws JDtnException On JDTN specific errors
	 * @throws XmlRdParserException On XML Parsing errors
	 */
	public void parse(XmlRDParser parser) 
	throws IOException, JDtnException, XmlRdParserException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("parse()");
		}
		Boolean startClean = Utils.getBooleanAttribute(parser, START_CLEAN_ATTR);
		if (startClean != null) {
			setStartClean(startClean);
		}
	}
	
	/**
	 * Write values for our properties out to the given PrintWriter as XML
	 * attributes.
	 * @param pw Given PrintWriter
	 */
	public void writeConfig(PrintWriter pw) {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("writeConfig()");
		}
		if (_startClean != START_CLEAN_DEFAULT) {
			pw.println("    " + START_CLEAN_ATTR + "='" + _startClean + "'");
		}
	}
	
	/**
	 * Called when JDTN is started up.  We respond by obtaining a connection
	 * to the database.  Note that the property 'startClean'
	 * should be set before this component is started.
	 * @see com.cisco.qte.jdtn.component.AbstractStartableComponent#startImpl()
	 */
	@Override
	protected void startImpl() {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("startImpl()");
		}
		try {

			_dbInterface = Platform.getDBInterface();
			_dbInterface.openDB(isStartClean());

		} catch (JDtnException e) {
			_logger.log(Level.SEVERE, "startImpl()", e);
		} catch (DBInterfaceException e) {
			_logger.log(Level.SEVERE, "startImpl()", e);
		}

		
	}

	/**
	 * Called when JDTN is shutting down.  We respond by closing the
	 * connection to the database.
	 * @see com.cisco.qte.jdtn.component.AbstractStartableComponent#stopImpl()
	 */
	@Override
	protected void stopImpl() throws InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("stopImpl()");
		}
		if (_dbInterface != null) {
			try {
				
				_dbInterface.closeDB();
				
			} catch (DBInterfaceException e) {
				_logger.log(Level.SEVERE, "Closing database connection", e);
			} finally {
				_dbInterface = null;
			}
		}
	}

	/**
	 * Called when a Bundle is first placed into service, either because
	 * our end system is sourcing a Bundle, or because the Bundle Agent is
	 * being asked to deliver or forward a Bundle.  We add info about the
	 * Bundle to the Database, and encode the Bundle into a File in our
	 * Store directory.
	 * @param bundle The affected Bundle.
	 * @param source Where the bundle came from
	 * @param state The State of the Bundle
	 * @param eidScheme The Endpoint ID scheme of the Bundle.
	 * @throws JDtnException on errors
	 * @throws InterruptedException On Interrupt from serializaiton
	 */
	public void introduceBundle(
			Bundle bundle, 
			BundleSource source, 
			BundleState state,
			EidScheme eidScheme) 
	throws JDtnException, InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("introduceBundle(" + 
					" state=" + BundleState.toParseableString(state) +
					" source=" + BundleSource.toParseableString(source) +
					")");
		}
		if (!isStarted()) {
			throw new JDtnException("Not started or has been stopped");
		}
		if (_dbInterface == null) {
			throw new JDtnException("Database has not been connected");
		}
		
		// Encode the entire Bundle to a File
		MediaRepository.File path = Store.getInstance().createBundleFile();
		EncodeState encodeState = new EncodeState(path);
		bundle.encode(encodeState, eidScheme);
		encodeState.close();
		
		// Query to see if Bundle is already in Database.  If so, replace it.
		String statementText = null;
		statementText = 
			"select " + 
			BundleDatabaseConstants.PATH_COL +
			" from " + BundleDatabaseConstants.TABLE_NAME + " where " +
					BundleDatabaseConstants.STORAGETYPE_COL + " = " + BlobAndBundleDatabase.intOf(path.getStorageType()) + " and " +
			BundleDatabaseConstants.SOURCE_EID_COL + "='" + bundle.getBundleId().sourceEndPointId.getEndPointIdString() + "' and " +
			BundleDatabaseConstants.TIME_SECS_COL + "=" + bundle.getBundleId().timestamp.getTimeSecsSinceY2K() + " and " +
			BundleDatabaseConstants.SEQUENCE_NO_COL + "=" + bundle.getBundleId().timestamp.getSequenceNumber() + " and " +
			BundleDatabaseConstants.FRAG_OFFSET_COL + "=" + bundle.getPrimaryBundleBlock().getFragmentOffset() +
			";"
			;
	    _logger.fine(statementText);
	    
	    try {
			QueryResults qr = _dbInterface.executeQuery(statementText);
			if (qr.next()) {
				qr.close();
				
				// Already in Database; replace it
				statementText =
					"update " + BundleDatabaseConstants.TABLE_NAME + " set " +
					BundleDatabaseConstants.PATH_COL + "='" + path.getAbsolutePath() + "', " +
							BundleDatabaseConstants.STORAGETYPE_COL + "=" + BlobAndBundleDatabase.intOf(path.getStorageType()) + ", " +
					BundleDatabaseConstants.LENGTH_COL + "=" + path.length() + ", " +
					BundleDatabaseConstants.SOURCE_COL + "='" + BundleSource.toParseableString(source) + "', " +
					BundleDatabaseConstants.STATE_COL + "='" + BundleState.toParseableString(state) + "', " +
					BundleDatabaseConstants.EID_SCHEME_COL + "='" + EidScheme.eidSchemeToString(eidScheme) + "', " +
					(bundle.getLink() != null ?
							BundleDatabaseConstants.LINK_NAME_COL + "='" + bundle.getLink().getName() + "', " :
								BundleDatabaseConstants.LINK_NAME_COL + "=null, ") +
					(bundle.isInboundBundle() ?
							BundleDatabaseConstants.IS_INBOUND_COL + "=1, " :
							BundleDatabaseConstants.IS_INBOUND_COL + "=0, ") +
							BundleDatabaseConstants.RETENTION_CONSTRAINT_COL + "=" + bundle.getRetentionConstraint() + " " +
							BundleDatabaseConstants.DATA_BLOB_COL + "=" + path.getOid() + " " +
					"where " +
					BundleDatabaseConstants.SOURCE_EID_COL + "='" + bundle.getBundleId().sourceEndPointId.getEndPointIdString() + "' and " +
					BundleDatabaseConstants.TIME_SECS_COL + "=" + bundle.getBundleId().timestamp.getTimeSecsSinceY2K() + " and " +
					BundleDatabaseConstants.SEQUENCE_NO_COL + "=" + bundle.getBundleId().timestamp.getSequenceNumber() + " and " +
					BundleDatabaseConstants.FRAG_OFFSET_COL + "=" + bundle.getPrimaryBundleBlock().getFragmentOffset() +
					";"
					;
			    _logger.fine(statementText);
			    _dbInterface.executeUpdate(statementText);
			    _dbInterface.commit();
			    return;
			}
			qr.close();
			
			// Not already in database; Create an entry in the Database
			statementText =
				"insert into " + BundleDatabaseConstants.TABLE_NAME + " values (" +
					"'" + bundle.getExtendedBundleId().getBundleId().sourceEndPointId.getEndPointIdString() + "', " +
					bundle.getPrimaryBundleBlock().getCreationTimestamp().getTimeSecsSinceY2K() + ", " +
					bundle.getPrimaryBundleBlock().getCreationTimestamp().getSequenceNumber() + ", " +
					bundle.getPrimaryBundleBlock().getFragmentOffset() + ", " +
					"'" + path.getAbsolutePath() + "', " +
						BlobAndBundleDatabase.intOf(path.getStorageType()) + ", " +
					path.length() + ", " +
					"'" + BundleSource.toParseableString(source) + "', " +
					"'" + BundleState.toParseableString(state) + "', " +
					"'" + EidScheme.eidSchemeToString(eidScheme) + "', " +
					((bundle.getLink() == null) ?
						"NULL, " :
						"'" + bundle.getLink().getName() + "', ") +
					"'" + bundle.isInboundBundle() + "', " +
					bundle.getRetentionConstraint() + ", " +
						path.getOid() +
					");";
			_logger.fine(statementText);
			_dbInterface.executeInsert(statementText);
			_dbInterface.commit();
		} catch (IllegalArgumentException e) {
			_logger.log(Level.SEVERE, "introduceBundle()", e);
		} catch (DBInterfaceException e) {
			_logger.log(Level.SEVERE, "introduceBundle()", e);
		}
	}
	
	/**
	 * Update the retention constraint for an existing Bundle.  It is assumed
	 * the Bundle is already in the database.
	 * @param bundle The bundle to be updated
	 * @throws JDtnException on errors
	 */
	public void updateRetentionConstraint(Bundle bundle) throws JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("updateRetentionConstraint()");
		}
		if (!isStarted()) {
			throw new JDtnException("Not started or has been stopped");
		}
		if (_dbInterface == null) {
			throw new JDtnException("Database has not been connected");
		}
		String statementText =
			"update " + BundleDatabaseConstants.TABLE_NAME + " set " +
			BundleDatabaseConstants.RETENTION_CONSTRAINT_COL + "=" + bundle.getRetentionConstraint() + " " +
			"where " +
			BundleDatabaseConstants.SOURCE_EID_COL + "='" + bundle.getBundleId().sourceEndPointId.getEndPointIdString() + "' and " +
			BundleDatabaseConstants.TIME_SECS_COL + "=" + bundle.getBundleId().timestamp.getTimeSecsSinceY2K() + " and " +
			BundleDatabaseConstants.SEQUENCE_NO_COL + "=" + bundle.getBundleId().timestamp.getSequenceNumber() + " and " +
			BundleDatabaseConstants.FRAG_OFFSET_COL + "=" + bundle.getPrimaryBundleBlock().getFragmentOffset() +
			";"
			;
	    _logger.fine(statementText);
		try {
			_dbInterface.executeUpdate(statementText);
			_dbInterface.commit();
		} catch (DBInterfaceException e) {
			throw new JDtnException(e);
		}
	    return;
	}
	
	/**
	 * Update the Link name property for an existing Bundle.  It is assumed
	 * the Bundle is already in the database.
	 * @param bundle The bundle to be updated
	 * @throws JDtnException on errors
	 */
	public void updateLink(Bundle bundle, Link link) throws JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("updateLink()");
		}
		if (!isStarted()) {
			throw new JDtnException("Not started or has been stopped");
		}
		if (_dbInterface == null) {
			throw new JDtnException("Database has not been connected");
		}
		String statementText =
			"update " + BundleDatabaseConstants.TABLE_NAME + " set " +
			BundleDatabaseConstants.LINK_NAME_COL + "='" + link.getName() + "' " +
			"where " +
			BundleDatabaseConstants.SOURCE_EID_COL + "='" + bundle.getBundleId().sourceEndPointId.getEndPointIdString() + "' and " +
			BundleDatabaseConstants.TIME_SECS_COL + "=" + bundle.getBundleId().timestamp.getTimeSecsSinceY2K() + " and " +
			BundleDatabaseConstants.SEQUENCE_NO_COL + "=" + bundle.getBundleId().timestamp.getSequenceNumber() + " and " +
			BundleDatabaseConstants.FRAG_OFFSET_COL + "=" + bundle.getPrimaryBundleBlock().getFragmentOffset() +
			";"
			;
	    _logger.fine(statementText);
		try {
			_dbInterface.executeUpdate(statementText);
			_dbInterface.commit();
		} catch (DBInterfaceException e) {
			throw new JDtnException(e);
		}
	    return;
	}
	
	/**
	 * Update the EidScheme property for an existing Bundle.  It is assumed
	 * the Bundle is already in the database.
	 * @param bundle The bundle to be updated
	 * @param eidScheme The EidScheme
	 * @throws JDtnException on errors
	 */
	public void updateEidScheme(Bundle bundle, EidScheme eidScheme) 
	throws JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("updateEidScheme()");
		}
		if (!isStarted()) {
			throw new JDtnException("Not started or has been stopped");
		}
		if (_dbInterface == null) {
			throw new JDtnException("Database has not been connected");
		}
		String statementText =
			"update " + BundleDatabaseConstants.TABLE_NAME + " set " +
			BundleDatabaseConstants.EID_SCHEME_COL + "='" + EidScheme.eidSchemeToString(eidScheme) + "' " +
			"where " +
			BundleDatabaseConstants.SOURCE_EID_COL + "='" + bundle.getBundleId().sourceEndPointId.getEndPointIdString() + "' and " +
			BundleDatabaseConstants.TIME_SECS_COL + "=" + bundle.getBundleId().timestamp.getTimeSecsSinceY2K() + " and " +
			BundleDatabaseConstants.SEQUENCE_NO_COL + "=" + bundle.getBundleId().timestamp.getSequenceNumber() + " and " +
			BundleDatabaseConstants.FRAG_OFFSET_COL + "=" + bundle.getPrimaryBundleBlock().getFragmentOffset() +
			";"
			;
	    _logger.fine(statementText);
		try {
			_dbInterface.executeUpdate(statementText);
			_dbInterface.commit();
		} catch (DBInterfaceException e) {
			throw new JDtnException(e);
		}
	    return;
	}
	
	/**
	 * Get the EidScheme for given Bundle from the Bundle Database.
	 * @param bundle Given Bundle
	 * @return Stored EidScheme
	 * @throws JDtnException on errors
	 */
	public EidScheme getEidScheme(Bundle bundle)
	throws JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("getEidScheme()");
		}
		if (!isStarted()) {
			throw new JDtnException("Not started or has been stopped");
		}
		if (_dbInterface == null) {
			throw new JDtnException("Database has not been connected");
		}
		String statementText =
			"select " + BundleDatabaseConstants.EID_SCHEME_COL + " from " + BundleDatabaseConstants.TABLE_NAME +
			"where " +
			BundleDatabaseConstants.SOURCE_EID_COL + "='" + bundle.getBundleId().sourceEndPointId.getEndPointIdString() + "' and " +
			BundleDatabaseConstants.TIME_SECS_COL + "=" + bundle.getBundleId().timestamp.getTimeSecsSinceY2K() + " and " +
			BundleDatabaseConstants.SEQUENCE_NO_COL + "=" + bundle.getBundleId().timestamp.getSequenceNumber() + " and " +
			BundleDatabaseConstants.FRAG_OFFSET_COL + "=" + bundle.getPrimaryBundleBlock().getFragmentOffset() +
			";"
			;
		_logger.fine(statementText);
		try {
			QueryResults qr = _dbInterface.executeQuery(statementText);
			if (qr.next()) {
				qr.close();
				EidScheme eidScheme =
					EidScheme.parseEidScheme(qr.getString(1));
				return eidScheme;
				
			} else {
				qr.close();
				throw new JDtnException("No such Bundle: " + bundle.getExtendedBundleId().dump("", false));
			}
		} catch (DBInterfaceException e) {
			throw new JDtnException(e);
		}
	}
	
	/**
	 * Update encoded Bundle file blob
	 * @param bundle Bundle to update
	 * @throws JDtnException on errors
	 * @throws InterruptedException If interrupted
	 */
	public void updateBundleData(Bundle bundle) 
	throws JDtnException, InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("updateBundleData()");
		}
		if (!isStarted()) {
			throw new JDtnException("Not started or has been stopped");
		}
		if (_dbInterface == null) {
			throw new JDtnException("Database has not been connected");
		}
		// Select entry from DB corresponding to given bundle
		String statementText = 
			"select " + 
			BundleDatabaseConstants.PATH_COL + ", " + BundleDatabaseConstants.EID_SCHEME_COL + ", " + BundleDatabaseConstants.STORAGETYPE_COL + ", " + BundleDatabaseConstants.DATA_BLOB_COL +
			" from " + BundleDatabaseConstants.TABLE_NAME + " where " +
			BundleDatabaseConstants.SOURCE_EID_COL + "='" + bundle.getBundleId().sourceEndPointId.getEndPointIdString() + "' and " +
			BundleDatabaseConstants.TIME_SECS_COL + "=" + bundle.getBundleId().timestamp.getTimeSecsSinceY2K() + " and " +
			BundleDatabaseConstants.SEQUENCE_NO_COL + "=" + bundle.getBundleId().timestamp.getSequenceNumber() + " and " +
			BundleDatabaseConstants.FRAG_OFFSET_COL + "=" + bundle.getPrimaryBundleBlock().getFragmentOffset() +
			";"
			;
	    _logger.fine(statementText);
	    try {
			QueryResults qr = _dbInterface.executeQuery(statementText);
			
			// Pull queried column values from ResultSet of query
			if (!qr.next()) {
				qr.close();
				throw new IllegalStateException("Query for bundle returned no results");
			}
			String pathnameStr = qr.getString(1);
			EidScheme eidScheme = EidScheme.parseEidScheme(qr.getString(2));
			BlobAndBundleDatabase.StorageType storageType = BlobAndBundleDatabase.storageTypeOf(qr.getInt(3));
			MediaRepository.File file = new MediaRepository.File(storageType, pathnameStr);
			file.setOid(qr.getLong(4));
			qr.close();
			
			// Encode the bundle to its file
			EncodeState encodeState = new EncodeState();
			bundle.encode(encodeState, eidScheme);
			encodeState.close();

			_dbInterface.executeUpdate("UPDATE " + BundleDatabaseConstants.TABLE_NAME + " SET " + BundleDatabaseConstants.DATA_BLOB_COL + "=" + file.getOid() + " where " +
					BundleDatabaseConstants.SOURCE_EID_COL + "='" + bundle.getBundleId().sourceEndPointId.getEndPointIdString() + "' and " +
					BundleDatabaseConstants.TIME_SECS_COL + "=" + bundle.getBundleId().timestamp.getTimeSecsSinceY2K() + " and " +
					BundleDatabaseConstants.SEQUENCE_NO_COL + "=" + bundle.getBundleId().timestamp.getSequenceNumber() + " and " +
					BundleDatabaseConstants.FRAG_OFFSET_COL + "=" + bundle.getPrimaryBundleBlock().getFragmentOffset() +
					";");
			_dbInterface.commit();

		} catch (DBInterfaceException e) {
			throw new JDtnException(e);
		}

	}
	
	/**
	 * Update database as Bundle is being enqueued for forwarding.
	 * @param bundle The affected Bundle
	 * @throws JDtnException on errors
	 * @throws InterruptedException process interrupted
	 */
	public void bundleForwardEnqueued(Bundle bundle)
	throws JDtnException, InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("bundleForwardEnqueued()");
		}
		if (!isStarted()) {
			throw new JDtnException("Not started or has been stopped");
		}
		if (_dbInterface == null) {
			throw new JDtnException("Database has not been connected");
		}
		
		// Update the BundleState and Retention constraint in the DB
		String statementText =
			"update " + BundleDatabaseConstants.TABLE_NAME + " set " +
			BundleDatabaseConstants.STATE_COL + "='" + BundleState.toParseableString(BundleState.FORWARD_ENQUEUED) + "', " +
			BundleDatabaseConstants.RETENTION_CONSTRAINT_COL + "=" + bundle.getRetentionConstraint() +
			" where " +
			BundleDatabaseConstants.SOURCE_EID_COL + "='" + bundle.getBundleId().sourceEndPointId.getEndPointIdString() + "' and " +
			BundleDatabaseConstants.TIME_SECS_COL + "=" + bundle.getBundleId().timestamp.getTimeSecsSinceY2K() + " and " +
			BundleDatabaseConstants.SEQUENCE_NO_COL + "=" + bundle.getBundleId().timestamp.getSequenceNumber() + " and " +
			BundleDatabaseConstants.FRAG_OFFSET_COL + "=" + bundle.getPrimaryBundleBlock().getFragmentOffset() +
			";"
			;
	    _logger.fine(statementText);
		try {
			_dbInterface.executeUpdate(statementText);
			_dbInterface.commit();
		} catch (DBInterfaceException e) {
			throw new JDtnException(e);
		}
	}
	
	/**
	 * Update database as Bundle is being held awaiting a Route to destination
	 * @param bundle The affected Bundle
	 * @throws JDtnException on errors
	 * @throws InterruptedException process interrupted
	 */
	public void bundleHeld(Bundle bundle) 
	throws JDtnException, InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("bundleHeld()");
		}
		if (!isStarted()) {
			throw new JDtnException("Not started or has been stopped");
		}
		if (_dbInterface == null) {
			throw new JDtnException("Database has not been connected");
		}

		// Update the BundleState and Retention Constraint in the DB
		String statementText =
			"update " + BundleDatabaseConstants.TABLE_NAME + " set " +
			BundleDatabaseConstants.STATE_COL + "='" + BundleState.toParseableString(BundleState.HELD) + "', " +
			BundleDatabaseConstants.RETENTION_CONSTRAINT_COL + "=" + bundle.getRetentionConstraint() +
			" where " +
			BundleDatabaseConstants.SOURCE_EID_COL + "='" + bundle.getBundleId().sourceEndPointId.getEndPointIdString() + "' and " +
			BundleDatabaseConstants.TIME_SECS_COL + "=" + bundle.getBundleId().timestamp.getTimeSecsSinceY2K() + " and " +
			BundleDatabaseConstants.SEQUENCE_NO_COL + "=" + bundle.getBundleId().timestamp.getSequenceNumber() + " and " +
			BundleDatabaseConstants.FRAG_OFFSET_COL + "=" + bundle.getPrimaryBundleBlock().getFragmentOffset() +
			";"
			;
	    _logger.fine(statementText);
		try {
			_dbInterface.executeUpdate(statementText);
			_dbInterface.commit();
		} catch (DBInterfaceException e) {
			throw new JDtnException(e);
		}
	}
	
	/**
	 * Update database as Bundle is being held in custody awaiting custody
	 * transfer.
	 * @param bundle
	 * @throws JDtnException on errors
	 * @throws InterruptedException process interrupted
	 */
	public void bundleInCustody(Bundle bundle) 
	throws JDtnException, InterruptedException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("bundleInCustody()");
		}
		if (!isStarted()) {
			throw new JDtnException("Not started or has been stopped");
		}
		if (_dbInterface == null) {
			throw new JDtnException("Database has not been connected");
		}

		// Update the BundleState in the DB
		String statementText =
			"update " + BundleDatabaseConstants.TABLE_NAME + " set " +
			BundleDatabaseConstants.STATE_COL + "='" + BundleState.toParseableString(BundleState.IN_CUSTODY) + "', " +
			BundleDatabaseConstants.RETENTION_CONSTRAINT_COL + "=" + bundle.getRetentionConstraint() +
			" where " +
			BundleDatabaseConstants.SOURCE_EID_COL + "='" + bundle.getBundleId().sourceEndPointId.getEndPointIdString() + "' and " +
			BundleDatabaseConstants.TIME_SECS_COL + "=" + bundle.getBundleId().timestamp.getTimeSecsSinceY2K() + " and " +
			BundleDatabaseConstants.SEQUENCE_NO_COL + "=" + bundle.getBundleId().timestamp.getSequenceNumber() + " and " +
			BundleDatabaseConstants.FRAG_OFFSET_COL + "=" + bundle.getPrimaryBundleBlock().getFragmentOffset() +
			";"
			;
	    _logger.fine(statementText);
		try {
			_dbInterface.executeUpdate(statementText);
			_dbInterface.commit();
		} catch (DBInterfaceException e) {
			throw new JDtnException(e);
		}
	}
	
	/**
	 * Delete the given Bundle from the database
	 * @param bundle Given Bundle
	 * @throws JDtnException on errors
	 */
	public void bundleDeleted(Bundle bundle) throws JDtnException
	{
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("bundleDeleted()");
		}
		if (!isStarted()) {
			throw new JDtnException("Not started or has been stopped");
		}
		if (_dbInterface == null) {
			throw new JDtnException("Database has not been connected");
		}
		
		// Select entry from DB corresponding to given bundle
		String statementText =
			"select " + 
			BundleDatabaseConstants.PATH_COL +
			" from " + BundleDatabaseConstants.TABLE_NAME + " where " +
			BundleDatabaseConstants.SOURCE_EID_COL + "='" + bundle.getBundleId().sourceEndPointId.getEndPointIdString() + "' and " +
			BundleDatabaseConstants.TIME_SECS_COL + "=" + bundle.getBundleId().timestamp.getTimeSecsSinceY2K() + " and " +
			BundleDatabaseConstants.SEQUENCE_NO_COL + "=" + bundle.getBundleId().timestamp.getSequenceNumber() + " and " +
			BundleDatabaseConstants.FRAG_OFFSET_COL + "=" + bundle.getPrimaryBundleBlock().getFragmentOffset() +
			";"
			;
	    _logger.fine(statementText);
	    String pathnameStr;
		try {
			QueryResults qr = _dbInterface.executeQuery(statementText);
			
			// Pull queried column values from ResultSet of query
			if (!qr.next()) {
				// No such Bundle.  Silently ignore this.
				qr.close();
				return;
			}
			pathnameStr = qr.getString(1);
			qr.close();
			
			// Delete entry from DB corresponding to given bundle
			statementText = 
				"delete from " + BundleDatabaseConstants.TABLE_NAME + " where " +
				BundleDatabaseConstants.SOURCE_EID_COL + "='" + bundle.getBundleId().sourceEndPointId.getEndPointIdString() + "' and " +
				BundleDatabaseConstants.TIME_SECS_COL + "=" + bundle.getBundleId().timestamp.getTimeSecsSinceY2K() + " and " +
				BundleDatabaseConstants.SEQUENCE_NO_COL + "=" + bundle.getBundleId().timestamp.getSequenceNumber() + " and " +
				BundleDatabaseConstants.FRAG_OFFSET_COL + "=" + bundle.getPrimaryBundleBlock().getFragmentOffset() +
				";"
				;
			_logger.fine(statementText);
			_dbInterface.executeDelete(statementText);

			// Delete Bundle storage file
			File file = new File(pathnameStr);
			if (!file.delete()) {
				throw new IllegalStateException("Cannot delete bundle file " + pathnameStr);
			}

			_dbInterface.commit();
		} catch (DBInterfaceException e) {
			throw new JDtnException(e);
		}
	}
	
	/**
	 * Restore all Bundles from BundleDatabase
	 * @param callback Callback for each Bundle restored
	 * @throws JDtnException on errors
	 */
	public void restoreBundles(BundleDatabaseRestoreCallback callback) 
	throws  JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("restoreBundles()");
		}
		if (!isStarted()) {
			throw new JDtnException("Not started or has been stopped");
		}
		if (_dbInterface == null) {
			throw new JDtnException("Database has not been connected");
		}

		// Select entry from DB all Bundles
		String statementText =
			"select " + 
			BundleDatabaseConstants.PATH_COL + ", " +
			BundleDatabaseConstants.LENGTH_COL + ", " + 
			BundleDatabaseConstants.STATE_COL + ", " + 
			BundleDatabaseConstants.SOURCE_COL + ", " + 
			BundleDatabaseConstants.EID_SCHEME_COL + ", " + 
			BundleDatabaseConstants.LINK_NAME_COL + ", " +
			BundleDatabaseConstants.IS_INBOUND_COL + ", " + 
			BundleDatabaseConstants.RETENTION_CONSTRAINT_COL + ", " +
					BundleDatabaseConstants.STORAGETYPE_COL + ", " +
					BundleDatabaseConstants.DATA_BLOB_COL +
			" from " + BundleDatabaseConstants.TABLE_NAME +
			" order by " +
			BundleDatabaseConstants.SOURCE_EID_COL + ", " + 
			BundleDatabaseConstants.TIME_SECS_COL + ", " + 
			BundleDatabaseConstants.SEQUENCE_NO_COL + ", " + 
			BundleDatabaseConstants.FRAG_OFFSET_COL +
			";"
			;
	    _logger.fine(statementText);
	    try {
			QueryResults qr = _dbInterface.executeQuery(statementText);
			while (qr.next()) {
				if (GeneralManagement.isDebugLogging() && _logger.isLoggable(Level.FINEST)) {
					_logger.finest("resultSet[1]=" + qr.getString(1));
					_logger.finest("resultSet[2]=" + qr.getLong(2));
					_logger.finest("resultSet[3]=" + qr.getString(3));
					_logger.finest("resultSet[4]=" + qr.getString(4));
				}
				String pathnameStr = qr.getString(1);
				long fileLength = qr.getLong(2);
				BundleState bundleState = BundleState.parseBundleState(qr.getString(3));
				BundleSource bundleSource = BundleSource.parseBundleSource(qr.getString(4));
				EidScheme eidScheme = EidScheme.parseEidScheme(qr.getString(5));
				String linkName = qr.getString(6);
				boolean isInbound = qr.getBoolean(7);
				int retentionConstraint = qr.getInt(8);
				BlobAndBundleDatabase.StorageType storageType = BlobAndBundleDatabase.storageTypeOf(qr.getInt(9));
				long oid = qr.getLong(10);
				
				if (GeneralManagement.isDebugLogging() && _logger.isLoggable(Level.FINEST)) {
					_logger.finest("Bundle Restoration: pathnameStr=" + pathnameStr);
					_logger.finest("Bundle Restoration: fileLength=" + fileLength);
					_logger.finest("Bundle Restoration: bundleState=" + bundleState);
					_logger.finest("Bundle Restoration: bundleSource=" + bundleSource);
					_logger.finest("Bundle Restoration: eidScheme=" + eidScheme);
					_logger.finest("Bundle Restoration: linkName=" + linkName);
					_logger.finest("Bundle Restoration: isInbound=" + isInbound);
					_logger.finest("Bundle Restoration: retentionConstraint=" + retentionConstraint);
				}
				MediaRepository.File file = new MediaRepository.File(storageType, pathnameStr);
				file.setOid(oid);
				DecodeState decodeState = new DecodeState(file, 0L, fileLength);
				Bundle bundle = new Bundle(decodeState, eidScheme);
				
				Link link = LinksList.getInstance().findLinkByName(linkName);
				bundle.setLink(link);
				bundle.setInboundBundle(isInbound);
				bundle.setRetentionConstraint(retentionConstraint);
				
				if (GeneralManagement.isDebugLogging()  && _logger.isLoggable(Level.FINEST)) {
					_logger.finest("Restored Bundle:");
					_logger.finest(bundle.dump("", true));
				}
				
				callback.restoreBundle(bundle, bundleSource, bundleState);
			}
			qr.close();
		} catch (IllegalArgumentException e) {
			throw new JDtnException(e);
		} catch (DBInterfaceException e) {
			throw new JDtnException(e);
		}
	}

	/**
	 * Empty the database.
	 * @throws JDtnException on errors
	 */
	public void emptyDatabase() throws JDtnException {
		if (GeneralManagement.isDebugLogging()) {
			_logger.finer("emptyDatabase()");
		}
		if (!isStarted()) {
			throw new JDtnException("Not started or has been stopped");
		}
		if (_dbInterface == null) {
			throw new JDtnException("Database has not been connected");
		}
		try {
			_dbInterface.clear();
			
			// Make sure everything removed
			String statement = "select * from " + BundleDatabaseConstants.TABLE_NAME + ";";
			_logger.fine(statement);
			QueryResults qr = _dbInterface.executeQuery(statement);
			if (qr.next()) {
				qr.close();
				throw new JDtnException("Empty database did not work!");
			}
			qr.close();
		} catch (DBInterfaceException e) {
			throw new JDtnException(e);
		}
	}
	
	/**
	 * Dump this component
	 * @param indent Amount of indentation
	 * @param detailed Whether detailed dump desired
	 * @return String containing dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		if (!isStarted()) {
			throw new IllegalStateException("Not started or has been stopped");
		}
		if (_dbInterface == null) {
			throw new IllegalStateException("Database has not been connected");
		}
		StringBuilder sb = new StringBuilder(indent + "BundleDatabase\n");
		sb.append(indent + "  StartClean=" + isStartClean() + "\n");
		sb.append(super.dump(indent + "  ", detailed));
		// Select entry from DB all Bundles
		String statementText =
			"select " + 
			BundleDatabaseConstants.PATH_COL + ", " + 
			BundleDatabaseConstants.LENGTH_COL + ", " + 
			BundleDatabaseConstants.STATE_COL + ", " + 
			BundleDatabaseConstants.SOURCE_COL + ", " + 
			BundleDatabaseConstants.EID_SCHEME_COL + ", " + 
			BundleDatabaseConstants.LINK_NAME_COL + ", " +
			BundleDatabaseConstants.IS_INBOUND_COL + ", " + 
			BundleDatabaseConstants.RETENTION_CONSTRAINT_COL + ", " +
					BundleDatabaseConstants.STORAGETYPE_COL + ", " +
					BundleDatabaseConstants.DATA_BLOB_COL +
			" from " + BundleDatabaseConstants.TABLE_NAME + 
			" order by " +
			BundleDatabaseConstants.SOURCE_EID_COL + ", " + 
			BundleDatabaseConstants.TIME_SECS_COL + ", " + 
			BundleDatabaseConstants.SEQUENCE_NO_COL + ", " + 
			BundleDatabaseConstants.FRAG_OFFSET_COL +
			";"
			;
	    _logger.fine(statementText);
		try {
			QueryResults qr = _dbInterface.executeQuery(statementText);
			while (qr.next()) {
				String pathnameStr = qr.getString(1);
				long fileLength = qr.getLong(2);
				BundleState bundleState = BundleState.parseBundleState(qr.getString(3));
				BundleSource bundleSource = BundleSource.parseBundleSource(qr.getString(4));
				EidScheme eidScheme = EidScheme.parseEidScheme(qr.getString(5));
				String linkName = qr.getString(6);
				boolean isInbound = qr.getBoolean(7);
				int retentionConstraint = qr.getInt(8);
				int storageType = qr.getInt(9);
				long oid = qr.getLong(10);
				
				sb.append(indent + "  Bundle\n");
				sb.append(indent + "    BundleState=" + BundleState.toParseableString(bundleState) + "\n");
				sb.append(indent + "    BundleSource=" + BundleSource.toParseableString(bundleSource) + "\n");
				sb.append(indent + "    pathname=" + pathnameStr + "\n");
				sb.append(indent + "    storageType=" + storageType + "\n");
				sb.append(indent + "    oid="+oid+"\n");
				sb.append(indent + "    fileLength=" + fileLength + "\n");
				sb.append(indent + "    bundleState=" + bundleState + "\n");
				sb.append(indent + "    bundleSource=" + bundleSource + "\n");
				sb.append(indent + "    eidScheme=" + eidScheme + "\n");
				sb.append(indent + "    linkName=" + linkName + "\n");
				sb.append(indent + "    isInbound=" + isInbound + "\n");
				sb.append(indent + "    retentionConstraint=" + retentionConstraint + "\n");
			}
			qr.close();
		} catch (Exception e) {
			_logger.log(Level.SEVERE, "BundleState.dump()", e);
		}
		return sb.toString();
	}
	
	/**
	 * StartClean property: Whether to empty the database when started.
	 * <ul>
	 *   <li>True => Empty the database (used for unit testing)
	 *   <li>False => Don't empty the database (used in normal operation)
	 *   <li>Default - false
	 * </ul>
	 * Note: This property should be set before starting the JDTN system.
	 * @return StartClean property
	 */
	public boolean isStartClean() {
		return _startClean;
	}

	/**
	 * StartClean property: Whether to empty the database when started.
	 * <ul>
	 *   <li>True => Empty the database (used for unit testing)
	 *   <li>False => Don't empty the database (used in normal operation)
	 *   <li>Default - false
	 * </ul>
	 * Note: This property should be set before starting the JDTN system.
	 * @param startClean StartClean property
	 */
	public void setStartClean(boolean startClean) {
		this._startClean = startClean;
	}

}

