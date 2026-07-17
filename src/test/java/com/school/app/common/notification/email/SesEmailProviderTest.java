package com.school.app.common.notification.email;

import com.school.app.common.exception.NotConfiguredException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SesEmailProviderTest {

    @Test
    void sendThrowsNotConfiguredWhenHostIsBlank() {
        SesEmailProvider provider = new SesEmailProvider("", 587, "user", "pass", "no-reply@school.app");

        assertThatThrownBy(() -> provider.send("to@school.app", "subject", "body"))
                .isInstanceOf(NotConfiguredException.class);
    }

    @Test
    void sendThrowsNotConfiguredWhenUsernameIsBlank() {
        SesEmailProvider provider = new SesEmailProvider("smtp.example.com", 587, "", "pass", "no-reply@school.app");

        assertThatThrownBy(() -> provider.send("to@school.app", "subject", "body"))
                .isInstanceOf(NotConfiguredException.class);
    }

    @Test
    void sendThrowsNotConfiguredWhenPasswordIsBlank() {
        SesEmailProvider provider = new SesEmailProvider("smtp.example.com", 587, "user", "", "no-reply@school.app");

        assertThatThrownBy(() -> provider.send("to@school.app", "subject", "body"))
                .isInstanceOf(NotConfiguredException.class);
    }
}
