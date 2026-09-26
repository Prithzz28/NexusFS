package com.dfs.common.exception;

public class DfsException extends RuntimeException {

    private final String errorCode;

    public DfsException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DfsException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
