package com.dfs.master.exception;

import com.dfs.common.exception.DfsException;

public class InsufficientStorageException extends DfsException {

    public InsufficientStorageException(String message) {
        super("INSUFFICIENT_STORAGE", message);
    }
}
