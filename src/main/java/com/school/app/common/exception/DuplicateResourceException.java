package com.school.app.common.exception;

public class DuplicateResourceException extends RuntimeException {

    /** Machine-readable code the client can switch on instead of matching HTTP status alone. Null for plain errors. */
    private final String code;

    public DuplicateResourceException(String message) {
        this(message, null);
    }

    public DuplicateResourceException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
