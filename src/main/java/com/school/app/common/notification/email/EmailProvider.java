package com.school.app.common.notification.email;

import com.school.app.common.exception.NotConfiguredException;

public interface EmailProvider {

    /**
     * Sends an email.
     *
     * @throws NotConfiguredException if the provider has no SMTP credentials configured yet
     * @throws RuntimeException on any other send failure
     */
    void send(String toEmail, String subject, String body);
}
