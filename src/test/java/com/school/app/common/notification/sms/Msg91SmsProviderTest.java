package com.school.app.common.notification.sms;

import com.school.app.common.exception.NotConfiguredException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Msg91SmsProviderTest {

    @Test
    void sendThrowsNotConfiguredWhenAuthKeyIsBlank() {
        Msg91SmsProvider provider = new Msg91SmsProvider("", "SENDERID", "4");

        assertThatThrownBy(() -> provider.send("9999999999", "hello"))
                .isInstanceOf(NotConfiguredException.class);
    }

    @Test
    void sendThrowsNotConfiguredWhenSenderIdIsBlank() {
        Msg91SmsProvider provider = new Msg91SmsProvider("authkey123", "", "4");

        assertThatThrownBy(() -> provider.send("9999999999", "hello"))
                .isInstanceOf(NotConfiguredException.class);
    }
}
