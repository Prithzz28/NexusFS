package com.dfs.master.exception;

import com.dfs.common.exception.DfsException;

public class AuthenticationException extends DfsException {

    public AuthenticationException(String message) {
        super("AUTHENTICATION_FAILED", message);
    }
}
