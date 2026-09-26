package com.dfs.master.exception;

import com.dfs.common.exception.DfsException;

public class FileNotFoundException extends DfsException {

    public FileNotFoundException(String message) {
        super("FILE_NOT_FOUND", message);
    }
}
