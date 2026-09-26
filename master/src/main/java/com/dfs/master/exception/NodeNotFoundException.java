package com.dfs.master.exception;

import com.dfs.common.exception.DfsException;

public class NodeNotFoundException extends DfsException {

    public NodeNotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
