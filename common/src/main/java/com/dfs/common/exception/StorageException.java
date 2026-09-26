package com.dfs.common.exception;

public class StorageException extends DfsException {

    public StorageException(String message) {
        super("STORAGE_ERROR", message);
    }

    public StorageException(String message, Throwable cause) {
        super("STORAGE_ERROR", message, cause);
    }
}
