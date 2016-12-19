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

    static class BasicDataSourceHelper {

        /**
         * Create pools based on a standard template
         */
        public static /*synchronized*/ BasicDataSource pool(int concurrency, Consumer<BasicDataSource> config) {
            BasicDataSource basicDataSource = new BasicDataSource();
            basicDataSource.setCacheState(true);
            basicDataSource.setDriverClassName("org.postgresql.Driver");
            basicDataSource.setDefaultAutoCommit(false);
            basicDataSource.setMaxWaitMillis(-1);
            basicDataSource.setValidationQuery("SELECT 1;");
            basicDataSource.setTestOnBorrow(true);
            basicDataSource.setTestOnReturn(false);
            basicDataSource.setTestWhileIdle(false);
            basicDataSource.setLifo(false);
            basicDataSource.setRollbackOnReturn(true);
            basicDataSource.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
            config.accept(basicDataSource);
            basicDataSource.setInitialSize(concurrency);
            basicDataSource.setMinIdle(2);
            //basicDataSource.setMaxTotal(concurrency);
            //try { basicDataSource.getConnection().close(); } catch (Throwable t) { } // error will happen again for sure
            //try  { Thread.sleep(Constants.INITIAL_BASIC_DATA_SOURCE_SLEEP); } catch (InterruptedException ie) { }
            return basicDataSource;
        }

    }

    private static BasicDataSource pool = null;

    public static String host;
    public static String db;
    public static String user;
    public static String password;

    private Connection _connection;

    public Connection getConnection() { return _connection; }

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
                pool = BasicDataSourceHelper.pool(2, basicDataSource -> {
                    basicDataSource.setUrl("jdbc:postgresql://" + host + ":5432/" + db + "?charSet=UTF8");
                    basicDataSource.setUsername(user);
                    basicDataSource.setPassword(password);
                    basicDataSource.setDefaultAutoCommit(false);
                    basicDataSource.setDefaultTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                });
            }

            _connection = pool.getConnection();
            Statement statement = _connection.createStatement();
            try {
                // TODO:FIXME
                // DROP old table anyway
                try { statement.executeUpdate("DROP TABLE " + BundleDatabaseConstants.TABLE_NAME_OLD); } catch (SQLException ignore) { }
                // Start clean - from unit tests only!
                if (startClean) {
                    try { statement.executeUpdate("DROP TABLE " + BundleDatabaseConstants.TABLE_NAME); } catch (SQLException ignore) { }
                    try { statement.executeUpdate("DROP TABLE " + BundleDatabaseConstants.FILE_TABLE_NAME); } catch (SQLException ignore) { }
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
                                BundleDatabaseConstants.LENGTH_COL + " int8," +
                                BundleDatabaseConstants.SOURCE_COL + " varchar, " +
                                BundleDatabaseConstants.STATE_COL + " varchar," +
                                BundleDatabaseConstants.EID_SCHEME_COL + " varchar, " +
                                BundleDatabaseConstants.LINK_NAME_COL + " varchar, " +
                                BundleDatabaseConstants.IS_INBOUND_COL + " varchar, " +
                                BundleDatabaseConstants.RETENTION_CONSTRAINT_COL +" varchar, " +
                                BundleDatabaseConstants.DATA_BLOB_COL + " oid," +
                                "CONSTRAINT stsf primary key (" +
                                BundleDatabaseConstants.SOURCE_EID_COL + ", " +
                                BundleDatabaseConstants.TIME_SECS_COL + ", " +
                                BundleDatabaseConstants.SEQUENCE_NO_COL + ", " +
                                BundleDatabaseConstants.FRAG_OFFSET_COL +
                                ") " +
                                "CONSTRAINT stpath1 unique key (" +
                                BundleDatabaseConstants.PATH_COL + " varchar_pattern_ops, " +
                                BundleDatabaseConstants.STORAGETYPE_COL +
                                ")" +
                                "CONSTRAINT stblob1 KEY (" +
                                BundleDatabaseConstants.DATA_BLOB_COL +
                                "));";
                                //") on conflict abort);";
                _logger.fine(statementText);
                statement.executeUpdate(statementText);
                statementText =
                        "CREATE TABLE IF NOT EXISTS " + BundleDatabaseConstants.FILE_TABLE_NAME +
                                " (" +
                                BundleDatabaseConstants.PATH_COL + " varchar NOT NULL, " +
                                BundleDatabaseConstants.STORAGETYPE_COL + " int4 NOT NULL, " +
                                BundleDatabaseConstants.DATA_BLOB_COL + " oid NOT NULL," +
                                "CONSTRAINT stpath2 primary key (" +
                                BundleDatabaseConstants.PATH_COL + " varchar_pattern_ops, " +
                                BundleDatabaseConstants.STORAGETYPE_COL +
                                ") " +
                                "CONSTRAINT stblob2 unique key(" +
                                BundleDatabaseConstants.DATA_BLOB_COL +
                                "));";
                //") on conflict abort);";
                _logger.fine(statementText);
                statement.executeUpdate(statementText);
            }
            finally {
                statement.close();
            }
            _connection.commit();

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
        try {
            _connection.close();
        } catch (SQLException e) {
            throw new DBInterfaceException(e);
        }
		_isStarted = false;
	}

	/* (non-Javadoc)
	 * @see com.cisco.qte.dbif.DBInterface#executeDelete(java.lang.String)
	 */
	@Override
	public void executeDelete(String statementText) throws DBInterfaceException {
		if (!isStarted()) {
			throw new DBInterfaceException("DB has not been previously opened");
		}
        try {
            Statement statement = _connection.createStatement();
            try {
                _logger.fine(statementText);
                statement.executeUpdate(statementText);
            }
            finally {
                statement.close();
            }
            _connection.commit();
		} catch (SQLException e) {
			throw new DBInterfaceException(e);
		}
	}

	/* (non-Javadoc)
	 * @see com.cisco.qte.dbif.DBInterface#executeInsert(java.lang.String)
	 */
	@Override
	public void executeInsert(String statementText) throws DBInterfaceException {
		if (!isStarted()) {
			throw new DBInterfaceException("DB has not been previously opened");
		}
        try {
            Statement statement = _connection.createStatement();
            try {
                _logger.fine(statementText);
                statement.executeUpdate(statementText);
            }
            finally {
                statement.close();
            }
            _connection.commit();
        } catch (SQLException e) {
            throw new DBInterfaceException(e);
        }
	}

	/* (non-Javadoc)
	 * @see com.cisco.qte.dbif.DBInterface#executeQuery(java.lang.String)
	 */
	@Override
	public QueryResults executeQuery(String statementText) throws DBInterfaceException {
		if (!isStarted()) {
			throw new DBInterfaceException("DB has not been previously opened");
		}
        try {
            Statement statement = _connection.createStatement();
            // this goes beyond my better judgment -- resource leaks?
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
	public void executeUpdate(String statementText) throws DBInterfaceException {
        try {
            Statement statement = _connection.createStatement();
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
	public void clear() throws DBInterfaceException {
        executeUpdate("delete from " + BundleDatabaseConstants.TABLE_NAME + ";");
	}

	private boolean isStarted() {
		return _isStarted;
	}

	public void commit() throws DBInterfaceException {
        try {
            _connection.commit();
        } catch (SQLException e) {
            throw new DBInterfaceException(e);
        }
    }
}
