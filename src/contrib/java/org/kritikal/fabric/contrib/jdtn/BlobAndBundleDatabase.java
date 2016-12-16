package org.kritikal.fabric.contrib.jdtn;

import com.cisco.qte.jdtn.apps.MediaRepository;
import com.cisco.qte.jdtn.persistance.BundleDatabase;

import java.nio.ByteBuffer;

/**
 * Created by ben on 12/16/16.
 */
public class BlobAndBundleDatabase extends BundleDatabase {

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

    public void cleanMediaRepository(StorageType storageType) {
        throw new Error("Not implemented.");
    }

    public void cleanMediaRepository(StorageType storageType, MediaRepository.File dir) {
        throw new Error("Not implemented.");
    }

    public boolean renameTo(MediaRepository.File fromFile, MediaRepository.File toFile) {
        throw new Error("Not implemented.");
    }

    public boolean spillByteArrayToFile(String appName,
			byte[] bytes,
			int offset,
			int length,
			MediaRepository.File mediaFilename) {
        throw new Error("Not implemented.");
    }

    public boolean copyByteBufferToFile(ByteBuffer buffer,
                                        MediaRepository.File mediaFilename) {
        throw new Error("Not implemented.");
    }

    public boolean copyByteArrayToFile(byte[] buffer,
                                         int offset, int length,
                                         MediaRepository.File mediaFilename) {
        throw new Error("Not implemented.");
    }

    public boolean appendByteArrayToFile(byte[] buffer,
                                         int offset, int length,
                                        MediaRepository.File mediaFilename) {
        throw new Error("Not implemented.");
    }

    public MediaRepository.File[] listFiles(StorageType storageType, String path) {
        throw new Error("Not implemented.");
    }

    public boolean mediaFileExists(MediaRepository.File file) {
        throw new Error("Not implemented.");
    }

    public long mediaFileLength(MediaRepository.File file) {
        throw new Error("Not implemented.");
    }

    public boolean mediaFileDelete(MediaRepository.File file) {
        throw new Error("Not implemented.");
    }

    public byte[] mediaGetBodyData(MediaRepository.File file) {
        throw new Error("Not implemented.");
    }

}
