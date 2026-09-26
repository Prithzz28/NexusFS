package com.dfs.master.exception;

import com.dfs.common.exception.DfsException;

public class AccessDeniedException extends DfsException {

    public AccessDeniedException(String message) {
        super("FORBIDDEN", message);
    }
}
