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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cisco.qte.jdtn.persistance.DBInterface;
import com.cisco.qte.jdtn.persistance.DBInterfaceException;
import com.cisco.qte.jdtn.persistance.QueryResults;
import com.cisco.qte.jdtn.general.GeneralManagement;
import com.cisco.qte.jdtn.general.JDtnException;
import com.cisco.qte.jdtn.general.Store;
import org.apache.commons.dbcp2.BasicDataSource;
import org.kritikal.fabric.contrib.db.CFPostgresqlPoolHelper;

/**
 * An implementation of the DBInterface using a JDBC Connector Driver as the
 * underlying delegate.  This is suitable for all platforms which support a
 * JDBC Connector Driver as the interface to the database, which means all
 * platforms except Android.
 *
 * OH BOY THIS NEEDED A FIX FOR VERTX!!!
 */
public class DBInterfaceJDBC implements DBInterface {
	private static final Logger _logger =
		Logger.getLogger(DBInterfaceJDBC.class.getCanonicalName());

    private static BasicDataSource pool = null;

    public static String host;
    public static int port = 5432;
    public static String db;
    public static String user;
    public static String password;

    public Connection createConnection() {
        try {
            synchronized (pool) {
                return pool.getConnection();
            }
        }
        catch (SQLException e) {
            _logger.warning(e.getMessage());
            throw new Error("", e);
        }
    }

	private boolean _isStarted = false;
	
	public DBInterfaceJDBC() {
		// Nothing
	}
	
	/* (non-Javadoc)
	 * @see com.cisco.qte.dbif.DBInterface#openDB()
	 */
	@Override
	public void openDB(boolean startClean) throws DBInterfaceException {
		if (isStarted()) {
			throw new DBInterfaceException("DB has been previously opened");
		}
		_isStarted = true;
        initializeDatabase(startClean);
	}

	/**
	 * Intialize the database.  This should be a one-time only call when the
	 * JDTN app is first installed.  It creates the database.
	 * @throws SQLException Various Exceptions that can be thrown from the
	 * java.sql classes.
	 * @throws JDtnException if BundleDatabase component not started yet.
	 * @throws ClassNotFoundException if the JDBC Connector not found
	 */
	private synchronized void initializeDatabase(boolean startClean)
	throws DBInterfaceException {
		try {
			if (GeneralManagement.isDebugLogging()) {
				_logger.finer("initializeDatabase()");
			}
			
			if (!isStarted()) {
				throw new DBInterfaceException("Not Started");
			}

            if (pool == null) {
                pool = CFPostgresqlPoolHelper.pool(2, basicDataSource -> {
                    basicDataSource.setUrl("jdbc:postgresql://" + host + ":" + port + "/" + db + "?charSet=UTF8");
                    basicDataSource.setUsername(user);
                    basicDataSource.setPassword(password);
                    basicDataSource.setDefaultAutoCommit(false);
                    basicDataSource.setDefaultTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                });
            }

            Connection _connection = createConnection();
			try {
                Statement statement = _connection.createStatement();
                try {
                    // Start clean - from unit tests only!
                    if (startClean) {
                        _logger.warning("Clearing " + db + " of DTN data.");
                        try {
                            statement.executeUpdate("DROP TABLE " + BundleDatabaseConstants.TABLE_NAME);
                            _connection.commit();
                        } catch (SQLException ignore) {
                            _connection.rollback();
                        }
                        try {
                            statement.executeUpdate("DROP TABLE " + BundleDatabaseConstants.FILE_TABLE_NAME);
                            _connection.commit();
                        } catch (SQLException ignore) {
                            _connection.rollback();
                        }
                    }
                    String statementText =
                            "CREATE TABLE IF NOT EXISTS " + BundleDatabaseConstants.TABLE_NAME +
                                    " (" +
                                    BundleDatabaseConstants.SOURCE_EID_COL + " varchar, " +
                                    BundleDatabaseConstants.TIME_SECS_COL + " int8, " +
                                    BundleDatabaseConstants.SEQUENCE_NO_COL + " int8, " +
                                    BundleDatabaseConstants.FRAG_OFFSET_COL + " int8, " +
                                    BundleDatabaseConstants.PATH_COL + " varchar, " +
                                    BundleDatabaseConstants.STORAGETYPE_COL + " int4, " +
                                    BundleDatabaseConstants.LENGTH_COL + " int8, " +
                                    BundleDatabaseConstants.SOURCE_COL + " varchar, " +
                                    BundleDatabaseConstants.STATE_COL + " varchar, " +
                                    BundleDatabaseConstants.EID_SCHEME_COL + " varchar, " +
                                    BundleDatabaseConstants.LINK_NAME_COL + " varchar, " +
                                    BundleDatabaseConstants.IS_INBOUND_COL + " varchar, " +
                                    BundleDatabaseConstants.RETENTION_CONSTRAINT_COL + " varchar, " +
                                    BundleDatabaseConstants.DATA_BLOB_COL + " oid, " +
                                    "CONSTRAINT stsf PRIMARY KEY (" +
                                    BundleDatabaseConstants.SOURCE_EID_COL + ", " +
                                    BundleDatabaseConstants.TIME_SECS_COL + ", " +
                                    BundleDatabaseConstants.SEQUENCE_NO_COL + ", " +
                                    BundleDatabaseConstants.FRAG_OFFSET_COL +
                                    "), " +
                                    "CONSTRAINT stpath1 UNIQUE (" +
                                    BundleDatabaseConstants.PATH_COL + ", " +
                                    BundleDatabaseConstants.STORAGETYPE_COL +
                                    "));";
                    //") on conflict abort);";
                    _logger.fine(statementText);
                    statement.executeUpdate(statementText);
                    _connection.commit();
                    statementText =
                            "CREATE TABLE IF NOT EXISTS " + BundleDatabaseConstants.FILE_TABLE_NAME +
                                    " (" +
                                    BundleDatabaseConstants.PATH_COL + " varchar NOT NULL, " +
                                    BundleDatabaseConstants.STORAGETYPE_COL + " int4 NOT NULL, " +
                                    BundleDatabaseConstants.DATA_BLOB_COL + " oid NOT NULL, " +
                                    "CONSTRAINT stpath2 PRIMARY KEY (" +
                                    BundleDatabaseConstants.PATH_COL + ", " +
                                    BundleDatabaseConstants.STORAGETYPE_COL +
                                    "), " +
                                    "CONSTRAINT stblob2 UNIQUE (" +
                                    BundleDatabaseConstants.DATA_BLOB_COL +
                                    "));";
                    //") on conflict abort);";
                    _logger.fine(statementText);
                    statement.executeUpdate(statementText);
                    _connection.commit();
                } finally {
                    statement.close();
                }
            }
            catch (Throwable t) {
			    try { _connection.rollback(); } catch (SQLException dontIgnore) {
			        _logger.warning("Database error creating tables.");
			        _logger.warning(dontIgnore.getMessage());
                }
			    throw t;
            }
            finally {
                _connection.close();
            }

		} catch (SQLException e) {
			throw new DBInterfaceException(e);
		}
	}
	
	/* (non-Javadoc)
	 * @see com.cisco.qte.dbif.DBInterface#closeDB(boolean)
	 */
	@Override
	public void closeDB() throws DBInterfaceException {
		if (!isStarted()) {
			throw new DBInterfaceException("DB has not been previously opened");
		}
		_isStarted = false;
	}

	/* (non-Javadoc)
	 * @see com.cisco.qte.dbif.DBInterface#executeDelete(java.lang.String)
	 */
	@Override
	public void executeDelete(Connection connection, String statementText) throws DBInterfaceException {
		if (!isStarted()) {
			throw new DBInterfaceException("DB has not been previously opened");
		}
        try {
            Statement statement = connection.createStatement();
            try {
                _logger.fine(statementText);
                statement.executeUpdate(statementText);
            }
            finally {
                statement.close();
            }
		} catch (SQLException e) {
			throw new DBInterfaceException(e);
		}
	}

	/* (non-Javadoc)
	 * @see com.cisco.qte.dbif.DBInterface#executeInsert(java.lang.String)
	 */
	@Override
	public void executeInsert(Connection connection, String statementText) throws DBInterfaceException {
		if (!isStarted()) {
			throw new DBInterfaceException("DB has not been previously opened");
		}
        try {
            Statement statement = connection.createStatement();
            try {
                _logger.fine(statementText);
                statement.executeUpdate(statementText);
            }
            finally {
                statement.close();
            }
        } catch (SQLException e) {
            throw new DBInterfaceException(e);
        }
	}

	/* (non-Javadoc)
	 * @see com.cisco.qte.dbif.DBInterface#executeQuery(java.lang.String)
	 */
	@Override
	public QueryResults executeQuery(Connection connection, String statementText) throws DBInterfaceException {
		if (!isStarted()) {
			throw new DBInterfaceException("DB has not been previously opened");
		}
        try {
            Statement statement = connection.createStatement();
            // this goes beyond my better judgment -- resource leaks? // TODO: FIXME
            //try {
                _logger.fine(statementText);
                ResultSet rs = statement.executeQuery(statementText);
                // nb. QueryResultsJDBC closes the rs
                //try {
                    QueryResultsJDBC results = new QueryResultsJDBC(rs);
                    return results;
                ///}
                //finally {
                //    rs.close();
                //}
            //}
            //finally {
            //    statement.close();
            //}
		} catch (SQLException e) {
			throw new DBInterfaceException(e);
		}
	}

	/* (non-Javadoc)
	 * @see com.cisco.qte.dbif.DBInterface#executeUpdate(java.lang.String)
	 */
	@Override
	public void executeUpdate(Connection connection, String statementText) throws DBInterfaceException {
        try {
            Statement statement = connection.createStatement();
            try {
                _logger.fine(statementText);
                statement.executeUpdate(statementText);
            }
            finally {
                statement.close();
            }
        } catch (SQLException e) {
            throw new DBInterfaceException(e);
        }
	}

	/* (non-Javadoc)
	 * @see com.cisco.qte.dbif.DBInterface#clear()
	 */
	@Override
	public void clear(Connection connection) throws DBInterfaceException {
        executeUpdate(connection, "delete from " + BundleDatabaseConstants.TABLE_NAME + ";");
	}

	private boolean isStarted() {
		return _isStarted;
	}

}
