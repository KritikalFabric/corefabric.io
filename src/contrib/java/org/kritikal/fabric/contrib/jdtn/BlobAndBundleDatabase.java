package org.kritikal.fabric.contrib.jdtn;

import com.cisco.qte.jdtn.apps.MediaRepository;
import com.cisco.qte.jdtn.persistance.BundleDatabase;
import com.cisco.qte.jdtn.persistance.BundleDatabaseConstants;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.postgresql.PGConnection;
import org.postgresql.largeobject.LargeObject;
import org.postgresql.largeobject.LargeObjectManager;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

/**
 * Created by ben on 12/16/16.
 */
public class BlobAndBundleDatabase extends BundleDatabase {

    static final Logger logger = LoggerFactory.getLogger(BlobAndBundleDatabase.class);

    public enum StorageType {
        STORE,
        MEDIA
    }

    private BlobAndBundleDatabase() {
        super();
    }

    private static BlobAndBundleDatabase _instance = null;

    public static BlobAndBundleDatabase getInstance() {
        if (_instance == null) _instance = new BlobAndBundleDatabase();
        return _instance;
    }

    public static StorageType storageTypeOf(int n) {
        if (n == 0) return StorageType.STORE;
        return StorageType.MEDIA;
    }

    public static int intOf(StorageType s) {
        if (s == StorageType.STORE) return 0;
        return -1;
    }

    /**
     * cleans the media repository and calls commit
     * @param storageType
     */
    public void cleanMediaRepository(Connection con, StorageType storageType) {
        if (!isStarted()) {
            throw new Error("Not started or has been stopped");
        }
        try {
            Statement stmt = con.createStatement();
            try {
                LargeObjectManager lom = con.unwrap(PGConnection.class).getLargeObjectAPI();
                ArrayList<Long> list = new ArrayList<>();
                ResultSet rs = stmt.executeQuery("SELECT " + BundleDatabaseConstants.DATA_BLOB_COL + " FROM " + BundleDatabaseConstants.FILE_TABLE_NAME + " WHERE " + BundleDatabaseConstants.STORAGETYPE_COL + " = " + BlobAndBundleDatabase.intOf(storageType));
                try {
                    long l = rs.getLong(1);
                    if (!rs.wasNull())
                        list.add(l);
                }
                finally {
                    rs.close();
                }
                for (Long l : list) {
                    lom.delete(l);
                    stmt.executeUpdate("DELETE FROM " + BundleDatabaseConstants.FILE_TABLE_NAME + " WHERE " + BundleDatabaseConstants.DATA_BLOB_COL + " = " + l);
                    stmt.executeUpdate("DELETE FROM " + BundleDatabaseConstants.TABLE_NAME + " WHERE " + BundleDatabaseConstants.DATA_BLOB_COL + " = " + l);
                    con.commit();
                }
            } finally {
                stmt.close();
            }
        }
        catch (SQLException e) {
            logger.fatal("", e);
        }
    }

    /**
     * cleans the media repository via the connection associated with dir
     * only removes some files
     * @param storageType
     * @param dir
     */
    public void cleanMediaRepository(Connection con, StorageType storageType, MediaRepository.File dir) {
        try {
            Statement stmt = con.createStatement();
            try {
                LargeObjectManager lom = con.unwrap(PGConnection.class).getLargeObjectAPI();
                ArrayList<Long> list = new ArrayList<>();
                ResultSet rs = stmt.executeQuery("SELECT " + BundleDatabaseConstants.DATA_BLOB_COL + " FROM " + BundleDatabaseConstants.FILE_TABLE_NAME + " WHERE " + BundleDatabaseConstants.STORAGETYPE_COL + " = " + BlobAndBundleDatabase.intOf(storageType) + " AND (" + BundleDatabaseConstants.PATH_COL + " LIKE '" + dir.getAbsolutePath() + "'||'/%' OR " + BundleDatabaseConstants.PATH_COL + " = '" + dir.getAbsolutePath() + "')");
                try {
                    long l = rs.getLong(1);
                    if (!rs.wasNull())
                        list.add(l);
                } finally {
                    rs.close();
                }
                for (Long l : list) {
                    lom.delete(l);
                    stmt.executeUpdate("DELETE FROM " + BundleDatabaseConstants.FILE_TABLE_NAME + " WHERE " + BundleDatabaseConstants.DATA_BLOB_COL + " = " + l);
                    stmt.executeUpdate("DELETE FROM " + BundleDatabaseConstants.TABLE_NAME + " WHERE " + BundleDatabaseConstants.DATA_BLOB_COL + " = " + l);
                }
            } finally {
                stmt.close();
            }
        } catch (SQLException e) {
            logger.fatal("", e);
        }
    }

    /**
     * renames from 2 to
     * @param fromFile
     * @param toFile
     * @return
     */
    public boolean renameTo(Connection con, MediaRepository.File fromFile, MediaRepository.File toFile) {
        try {
            Statement stmt = con.createStatement();
            try {
                int n = stmt.executeUpdate("UPDATE " + BundleDatabaseConstants.FILE_TABLE_NAME + " SET " + BundleDatabaseConstants.PATH_COL + "= '" + toFile.getAbsolutePath() +"', " + BundleDatabaseConstants.STORAGETYPE_COL + "=" + toFile.getStorageType() + " WHERE " + BundleDatabaseConstants.STORAGETYPE_COL + " = " + BlobAndBundleDatabase.intOf(fromFile.getStorageType()) + " AND " + BundleDatabaseConstants.PATH_COL + " = '" + fromFile.getAbsolutePath() +"'");
                stmt.executeUpdate("UPDATE " + BundleDatabaseConstants.TABLE_NAME + " SET " + BundleDatabaseConstants.PATH_COL + "= '" + toFile.getAbsolutePath() +"', " + BundleDatabaseConstants.STORAGETYPE_COL + "=" + toFile.getStorageType() + " WHERE " + BundleDatabaseConstants.STORAGETYPE_COL + " = " + BlobAndBundleDatabase.intOf(fromFile.getStorageType()) + " AND " + BundleDatabaseConstants.PATH_COL + " = '" + fromFile.getAbsolutePath() +"'");
                return n > 0;
            } finally {
                stmt.close();
            }
        }
        catch (SQLException e) {
            logger.fatal("", e);
            throw new Error("", e);
        }
    }

    /**
     * writes an entire file, replacing old data if necessary.
     * @param appName
     * @param bytes
     * @param offset
     * @param length
     * @param mediaFilename
     * @return
     */
    public boolean spillByteArrayToFile(Connection con, String appName,
			byte[] bytes,
			int offset,
			int length,
			MediaRepository.File mediaFilename) {
        try {
            LargeObjectManager lom = con.unwrap(PGConnection.class).getLargeObjectAPI();
            Statement stmt = con.createStatement();
            try {
                long oldOid = mediaFilename.getOid();
                if (oldOid != 0) {
                    stmt.executeUpdate("DELETE FROM " + BundleDatabaseConstants.FILE_TABLE_NAME + " WHERE " + BundleDatabaseConstants.DATA_BLOB_COL + " = " + oldOid);
                    lom.delete(oldOid);
                }
                mediaFilename.setOid(lom.createLO());
                stmt.executeUpdate("INSERT INTO " + BundleDatabaseConstants.FILE_TABLE_NAME + " (" +
                        BundleDatabaseConstants.DATA_BLOB_COL + ", " +
                        BundleDatabaseConstants.PATH_COL + ", " +
                        BundleDatabaseConstants.STORAGETYPE_COL +
                        ") VALUES ("+mediaFilename.getOid()+",'"+mediaFilename.getAbsolutePath()+"',"+BlobAndBundleDatabase.intOf(mediaFilename.getStorageType())+")");
                LargeObject lo = lom.open(mediaFilename.getOid(), LargeObjectManager.WRITE);
                try {
                    lo.write(bytes, offset, length);
                } finally {
                    lo.close();
                }
                if (oldOid != 0) {
                    stmt.executeUpdate("UPDATE " + BundleDatabaseConstants.TABLE_NAME + " SET " + BundleDatabaseConstants.DATA_BLOB_COL + " = " + mediaFilename.getOid() + " WHERE " + BundleDatabaseConstants.DATA_BLOB_COL + " = " + oldOid);
                }
                return true;
            }
            finally {
                stmt.close();
            }
        }
        catch (SQLException e) {
            logger.fatal("", e);
            throw new Error("", e);
        }
    }

    /*
     * writes an entire file, replacing old data if necessary.  calls commit.
     */
    public boolean copyByteBufferToFile(Connection con, ByteBuffer buffer,
                                        MediaRepository.File mediaFilename) {
        final byte[] array = buffer.array();
        return spillByteArrayToFile(con, null, array, 0, array.length, mediaFilename);
    }

    public boolean copyByteArrayToFile(Connection con, byte[] buffer,
                                         int offset, int length,
                                         MediaRepository.File mediaFilename) {
        return spillByteArrayToFile(con, null, buffer, offset, length, mediaFilename);
    }

    /**
     * appends data to an existing large object, and/or creates one
     * does NOT call commit
     * @param bytes
     * @param offset
     * @param length
     * @param mediaFilename
     * @return
     */
    public boolean appendByteArrayToFile(Connection con, byte[] bytes,
                                         int offset, int length,
                                        MediaRepository.File mediaFilename) {
        try {
            LargeObjectManager lom = con.unwrap(PGConnection.class).getLargeObjectAPI();
            long oldOid = mediaFilename.getOid();
            if (oldOid == 0) {
                mediaFilename.setOid(lom.createLO());
                Statement stmt = con.createStatement();
                try {
                    stmt.executeUpdate("INSERT INTO " + BundleDatabaseConstants.FILE_TABLE_NAME + " (" +
                            BundleDatabaseConstants.DATA_BLOB_COL + ", " +
                            BundleDatabaseConstants.PATH_COL + ", " +
                            BundleDatabaseConstants.STORAGETYPE_COL +
                            ") VALUES ("+mediaFilename.getOid()+",'"+mediaFilename.getAbsolutePath()+"',"+BlobAndBundleDatabase.intOf(mediaFilename.getStorageType())+")");
                }
                finally {
                    stmt.close();
                }
            }
            LargeObject lo = lom.open(mediaFilename.getOid(), LargeObjectManager.WRITE);
            lo.seek64(0, LargeObject.SEEK_END);
            try {
                lo.write(bytes, offset, length);
            } finally {
                lo.close();
            }
            return true;
        }
        catch (SQLException e) {
            logger.fatal("", e);
            throw new Error("", e);
        }
    }

    public MediaRepository.File[] listFiles(Connection con, StorageType storageType, String path) {
        if (!isStarted()) {
            throw new Error("Not started or has been stopped");
        }
        String p = path.endsWith("/")?path.substring(0, path.length()-1):path;
        ArrayList<MediaRepository.File> list = new ArrayList<>();
        try {
            Statement stmt = con.createStatement();
            try {
                ResultSet rs = stmt.executeQuery(
                        "SELECT " +
                                BundleDatabaseConstants.STORAGETYPE_COL + ", " +
                                BundleDatabaseConstants.PATH_COL + ", " +
                                BundleDatabaseConstants.DATA_BLOB_COL + " FROM " +
                                BundleDatabaseConstants.FILE_TABLE_NAME + " WHERE " +
                                BundleDatabaseConstants.STORAGETYPE_COL + "=" + BlobAndBundleDatabase.intOf(storageType) + " AND " +
                                "(" + BundleDatabaseConstants.PATH_COL + " LIKE '" + p + "'||'/%' OR " +
                                      BundleDatabaseConstants.PATH_COL + " = '" + p + "'||'/')" +
                                " ORDER BY " + BundleDatabaseConstants.PATH_COL + ";");
                try {
                    while (rs.next()) {
                        MediaRepository.File file = new MediaRepository.File(BlobAndBundleDatabase.storageTypeOf(rs.getInt(1)), rs.getString(2));
                        file.setOid(rs.getLong(3));
                        list.add(file);
                    }
                }
                finally {
                    rs.close();
                }
            }
            finally {
                stmt.close();
            }
            final MediaRepository.File[] array = new MediaRepository.File[list.size()];
            list.toArray(array);
            return array;
        }
        catch (SQLException e) {
            logger.fatal("", e);
            throw new Error("", e);
        }
    }

    public boolean mediaFileExists(Connection con, MediaRepository.File file) {
        try {
            Statement stmt = con.createStatement();
            try {
                ResultSet rs = stmt.executeQuery(
                        "SELECT 1 FROM " + BundleDatabaseConstants.FILE_TABLE_NAME +
                                " WHERE " + BundleDatabaseConstants.STORAGETYPE_COL + "=" + BlobAndBundleDatabase.intOf(file.getStorageType()) +
                                " AND " + BundleDatabaseConstants.PATH_COL + "='"+file.getAbsolutePath()+"'"
                );
                try {
                    if (rs.next())
                        return true;
                    return false;
                }
                finally {
                    rs.close();
                }
            }
            finally {
                stmt.close();
            }
        }
        catch (SQLException e) {
            logger.fatal("", e);
            throw new Error("", e);
        }
    }

    public long mediaFileLength(Connection con, MediaRepository.File file) {
        try {
            if (file.getOid() == 0) {
                Statement stmt = con.createStatement();
                try {
                    ResultSet rs = stmt.executeQuery(
                            "SELECT " + BundleDatabaseConstants.DATA_BLOB_COL + " FROM " + BundleDatabaseConstants.FILE_TABLE_NAME +
                                    " WHERE " + BundleDatabaseConstants.STORAGETYPE_COL + "=" + BlobAndBundleDatabase.intOf(file.getStorageType()) +
                                    " AND " + BundleDatabaseConstants.PATH_COL + "='" + file.getAbsolutePath() + "'"
                    );
                    try {
                        if (rs.next())
                            file.setOid(rs.getLong(1));
                    } finally {
                        rs.close();
                    }
                } finally {
                    stmt.close();
                }
            }
            if (file.getOid() == 0) return -1; // file not found
            LargeObjectManager lom = con.unwrap(PGConnection.class).getLargeObjectAPI();
            LargeObject lo = lom.open(file.getOid());
            try {
                return lo.size64();
            }
            finally {
                lo.close();
            }
        }
        catch (SQLException e) {
            logger.fatal("", e);
            throw new Error("", e);
        }
    }

    public boolean mediaFileDelete(Connection con, MediaRepository.File file) {
        try {
            Statement stmt = con.createStatement();
            try {
                LargeObjectManager lom = con.unwrap(PGConnection.class).getLargeObjectAPI();
                ArrayList<Long> list = new ArrayList<>();
                ResultSet rs = stmt.executeQuery("SELECT " + BundleDatabaseConstants.DATA_BLOB_COL + " FROM " + BundleDatabaseConstants.FILE_TABLE_NAME + " WHERE " + BundleDatabaseConstants.STORAGETYPE_COL + " = " + BlobAndBundleDatabase.intOf(file.getStorageType()) + " AND " + BundleDatabaseConstants.PATH_COL + " = '" + file.getAbsolutePath() + "'");
                try {
                    if (rs.next()) {
                        long l = rs.getLong(1);
                        if (!rs.wasNull())
                            list.add(l);
                    }
                } finally {
                    rs.close();
                }
                for (Long l : list) {
                    lom.delete(l);
                    stmt.executeUpdate("DELETE FROM " + BundleDatabaseConstants.FILE_TABLE_NAME + " WHERE " + BundleDatabaseConstants.DATA_BLOB_COL + " = " + l);
                    stmt.executeUpdate("DELETE FROM " + BundleDatabaseConstants.TABLE_NAME + " WHERE " + BundleDatabaseConstants.DATA_BLOB_COL + " = " + l);
                }
            } finally {
                stmt.close();
                file.setOid(0l);
            }
            return true;
        } catch (SQLException e) {
            logger.fatal("", e);
            throw new Error("", e);
        }
    }

    public byte[] mediaGetBodyData(Connection con, MediaRepository.File file) {
        try {
            LargeObjectManager lom = con.unwrap(PGConnection.class).getLargeObjectAPI();
            if (file.getOid() == 0)
                return null;
            Buffer buffer = Buffer.buffer();
            LargeObject lo = lom.open(file.getOid(), LargeObjectManager.READ);
            try {
                byte[] buf = new byte[2048];
                long l = 0;
                while ((l = lo.read(buf, 0, buf.length)) > 0) {
                    buffer.appendBytes(buf, 0, (int)l);
                }
            }
            finally {
                lo.close();
            }
            return buffer.getBytes();
        }
        catch (SQLException e) {
            logger.fatal("", e);
            throw new Error("", e);
        }
    }

}
