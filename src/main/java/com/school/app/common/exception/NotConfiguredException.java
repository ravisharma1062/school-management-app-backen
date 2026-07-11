package com.school.app.common.exception;

/** Thrown when a pluggable external provider (SMS, email, payment gateway...) is called before its credentials are configured. */
public class NotConfiguredException extends RuntimeException {

    public NotConfiguredException(String message) {
        super(message);
    }
}
