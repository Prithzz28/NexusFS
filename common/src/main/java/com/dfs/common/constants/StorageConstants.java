package com.dfs.common.constants;

public final class StorageConstants {

    private StorageConstants() {
    }

    /** Default chunk size: 4 MB */
    public static final long DEFAULT_CHUNK_SIZE_BYTES = 4 * 1024 * 1024;

    /** Default replication factor */
    public static final int DEFAULT_REPLICATION_FACTOR = 3;

    /** Default heartbeat interval in seconds */
    public static final int DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 5;

    /** Default node failure timeout in seconds */
    public static final int DEFAULT_NODE_FAILURE_TIMEOUT_SECONDS = 15;

    /** Maximum file size: 5 GB */
    public static final long DEFAULT_MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024 * 1024;

    /** Upload session timeout in minutes */
    public static final int DEFAULT_UPLOAD_SESSION_TIMEOUT_MINUTES = 60;

    /** SHA-256 algorithm name */
    public static final String CHECKSUM_ALGORITHM = "SHA-256";
}
