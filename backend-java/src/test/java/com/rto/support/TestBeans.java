package com.rto.support;

import com.rto.service.MockNotificationProvider;
import com.rto.service.NotificationProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Test-only wiring: a switchable notification provider so tests can simulate a failing SMS/e-mail gateway. */
@TestConfiguration
public class TestBeans {

    public static class SwitchableProvider implements NotificationProvider {
        public final MockNotificationProvider mock = new MockNotificationProvider();
        private volatile NotificationProvider target = mock;

        public void use(NotificationProvider p) { target = p; }
        public void reset() { target = mock; mock.sent.clear(); }

        @Override
        public void send(String channel, String recipient, String subject, String message) throws Exception {
            target.send(channel, recipient, subject, message);
        }
    }

    @Bean
    @Primary
    SwitchableProvider switchableProvider() {
        return new SwitchableProvider();
    }
}
