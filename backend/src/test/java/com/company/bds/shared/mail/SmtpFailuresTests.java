package com.company.bds.shared.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.util.MailConnectException;
import org.eclipse.angus.mail.util.SocketConnectException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SmtpFailuresTests {

    @Test
    void smtpFiveHundredRepliesAndUnusableAddressesArePermanent() throws Exception {
        assertThat(SmtpFailures.isPermanent(rejectedRecipient(550))).isTrue();
        assertThat(SmtpFailures.isPermanent(new MailSendException(Map.of("message", new SMTPSendFailedException("DATA", 552,
                "552 5.3.4 Message too big", null, null, null, null))))).isTrue();
        assertThat(SmtpFailures.isPermanent(new AddressException("Illegal address", "not an address"))).isTrue();
    }

    @Test
    void connectionProblemsTemporaryRepliesAndAuthenticationFailuresAreRetried() throws Exception {
        assertThat(SmtpFailures.isPermanent(rejectedRecipient(451))).isFalse();
        assertThat(SmtpFailures.isPermanent(new MailSendException("Mail server connection failed",
                new MailConnectException(new SocketConnectException("refused", new java.net.ConnectException(), "127.0.0.1", 25, 5000)))))
                .isFalse();
        assertThat(SmtpFailures.isPermanent(new MailAuthenticationException("535 5.7.8 Authentication failed"))).isFalse();
        assertThat(SmtpFailures.isPermanent(new IllegalStateException("anything else"))).isFalse();
    }

    @Test
    void mixedRepliesAreRetriedSoTheTemporaryPartCanStillSucceed() throws Exception {
        Map<Object, Exception> failures = new LinkedHashMap<>();
        failures.put("first", sendFailure(550));
        failures.put("second", sendFailure(451));
        assertThat(SmtpFailures.isPermanent(new MailSendException(failures))).isFalse();
    }

    private static MailSendException rejectedRecipient(int code) throws Exception {
        return new MailSendException(Map.of("message", sendFailure(code)));
    }

    private static MessagingException sendFailure(int code) throws Exception {
        InternetAddress recipient = new InternetAddress("recipient@example.test");
        SMTPAddressFailedException rejected = new SMTPAddressFailedException(recipient, "RCPT TO:<recipient@example.test>", code,
                code + " reply from the fake server");
        return new SendFailedException("Invalid Addresses", rejected, new InternetAddress[0], new InternetAddress[0],
                new InternetAddress[] {recipient});
    }
}
