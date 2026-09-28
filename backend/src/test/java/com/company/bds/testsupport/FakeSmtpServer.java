package com.company.bds.testsupport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

/**
 * Minimal SMTP server on a loopback port whose {@code RCPT TO} reply is chosen per command (e.g. {@code 550 5.1.1 ...} for
 * some recipients, {@code 451 4.7.1 ...} or {@code 250 OK} for others), to test how the outbox classifies real JavaMail
 * failures. Accepts connections until closed; accepted messages are discarded.
 */
public final class FakeSmtpServer implements AutoCloseable {
    private final ServerSocket socket;
    private final UnaryOperator<String> rcptReply;
    private final AtomicInteger rcptCommands = new AtomicInteger();
    private final Thread acceptor;

    /** @param rcptReply maps the RCPT TO command line to the reply sent back */
    public FakeSmtpServer(UnaryOperator<String> rcptReply) throws IOException {
        this.socket = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        this.rcptReply = rcptReply;
        this.acceptor = new Thread(this::acceptLoop, "fake-smtp-" + socket.getLocalPort());
        this.acceptor.setDaemon(true);
        this.acceptor.start();
    }

    public int port() { return socket.getLocalPort(); }

    /** How many RCPT TO commands were answered (one per delivery attempt and recipient). */
    public int rcptCommands() { return rcptCommands.get(); }

    private void acceptLoop() {
        while (!socket.isClosed()) {
            try (Socket client = socket.accept()) {
                converse(client);
            } catch (IOException ignored) {
                // closed or client disconnected
            }
        }
    }

    private void converse(Socket client) throws IOException {
        client.setSoTimeout(10_000);
        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
        Writer out = new OutputStreamWriter(client.getOutputStream(), StandardCharsets.US_ASCII);
        reply(out, "220 fake-smtp ESMTP ready");
        String line;
        while ((line = in.readLine()) != null) {
            String command = line.toUpperCase(Locale.ROOT);
            if (command.startsWith("EHLO")) {
                reply(out, "250-fake-smtp\r\n250 8BITMIME");
            } else if (command.startsWith("HELO") || command.startsWith("MAIL FROM") || command.startsWith("RSET")
                    || command.startsWith("NOOP")) {
                reply(out, "250 OK");
            } else if (command.startsWith("RCPT TO")) {
                rcptCommands.incrementAndGet();
                reply(out, rcptReply.apply(line));
            } else if (command.startsWith("DATA")) {
                reply(out, "354 End data with <CR><LF>.<CR><LF>");
                while ((line = in.readLine()) != null && !line.equals(".")) {
                    // discard the message body
                }
                reply(out, "250 OK queued");
            } else if (command.startsWith("QUIT")) {
                reply(out, "221 Bye");
                return;
            } else {
                reply(out, "502 Command not implemented");
            }
        }
    }

    private static void reply(Writer out, String text) throws IOException {
        out.write(text + "\r\n");
        out.flush();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
