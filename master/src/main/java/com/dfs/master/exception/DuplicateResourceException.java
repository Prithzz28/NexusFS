package com.dfs.master.exception;

import com.dfs.common.exception.DfsException;

public class DuplicateResourceException extends DfsException {

    public DuplicateResourceException(String message) {
        super("DUPLICATE_RESOURCE", message);
    }
}
