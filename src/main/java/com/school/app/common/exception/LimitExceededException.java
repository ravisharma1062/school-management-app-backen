package com.school.app.common.exception;

public class LimitExceededException extends RuntimeException {

    public LimitExceededException(String message) {
        super(message);
    }
}
