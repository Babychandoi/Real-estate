package com.company.bds.shared.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.smtp.SMTPSenderFailedException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Tells retryable mail failures (connection problems, SMTP 4xx) from permanent ones (SMTP 5xx replies, addresses or
 * messages JavaMail cannot build), so a message the server will never accept is dead-lettered at once instead of being
 * retried for a day.
 */
public final class SmtpFailures {
    private SmtpFailures() {}

    public static boolean isPermanent(Throwable error) {
        boolean permanentReply = false;
        boolean transientReply = false;
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Throwable> pending = new ArrayDeque<>();
        pending.push(error);
        while (!pending.isEmpty()) {
            Throwable current = pending.pop();
            if (!seen.add(current)) continue;
            if (current instanceof AddressException || current instanceof MailParseException || current instanceof MailPreparationException) {
                return true;
            }
            int code = replyCode(current);
            if (code >= 500 && code < 600) permanentReply = true;
            else if (code >= 400 && code < 500) transientReply = true;
            if (current instanceof MailSendException send) {
                for (Exception nested : send.getMessageExceptions()) push(pending, nested);
            }
            if (current instanceof MessagingException messaging) push(pending, messaging.getNextException());
            push(pending, current.getCause());
        }
        return permanentReply && !transientReply;
    }

    private static void push(Deque<Throwable> pending, Throwable error) {
        if (error != null) pending.push(error);
    }

    private static int replyCode(Throwable error) {
        if (error instanceof SMTPAddressFailedException failed) return failed.getReturnCode();
        if (error instanceof SMTPSendFailedException failed) return failed.getReturnCode();
        if (error instanceof SMTPSenderFailedException failed) return failed.getReturnCode();
        return -1;
    }
}
