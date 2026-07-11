package com.school.app.common.notification.sms;

import com.school.app.common.exception.NotConfiguredException;

public interface SmsProvider {

    /**
     * Sends an SMS.
     *
     * @throws NotConfiguredException if the provider has no credentials configured yet
     * @throws RuntimeException on any other send failure
     */
    void send(String toPhone, String message);
}
