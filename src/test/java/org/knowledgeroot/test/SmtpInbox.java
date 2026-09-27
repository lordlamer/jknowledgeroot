package org.knowledgeroot.test;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.*;

/** Local, disposable SMTP receiver for the packaged application test. Never relays mail. */
public final class SmtpInbox implements AutoCloseable {
    private final ServerSocket server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
    private final BlockingQueue<MimeMessage> messages = new LinkedBlockingQueue<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().factory());
    private volatile Throwable failure;

    public SmtpInbox() throws IOException {
        worker.submit(() -> {
            while (!server.isClosed()) {
                try (var socket = server.accept()) { receive(socket); }
                catch (Exception ex) { if (!server.isClosed()) failure = ex; }
            }
        });
    }
    public int port() { return server.getLocalPort(); }
    public String host() { return server.getInetAddress().getHostAddress(); }
    public MimeMessage take() throws Exception {
        var mail = messages.poll(15, TimeUnit.SECONDS);
        if (mail == null) throw new AssertionError("No SMTP message received", failure);
        return mail;
    }
    private void receive(Socket socket) throws Exception {
        socket.setSoTimeout(5000);
        var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        var output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII), true);
        reply(output, "220 localhost fixture");
        for (String line; (line = input.readLine()) != null;) {
            if (line.startsWith("EHLO") || line.startsWith("HELO")) reply(output, "250 localhost");
            else if (line.equals("DATA")) {
                reply(output, "354 End with a dot");
                var body = new StringBuilder();
                while ((line = input.readLine()) != null && !line.equals("."))
                    body.append(line.startsWith("..") ? line.substring(1) : line).append("\r\n");
                messages.add(new MimeMessage(Session.getInstance(new Properties()),
                        new ByteArrayInputStream(body.toString().getBytes(StandardCharsets.US_ASCII))));
                reply(output, "250 Received");
            } else if (line.equals("QUIT")) { reply(output, "221 Bye"); return; }
            else reply(output, "250 OK");
        }
    }
    private static void reply(PrintWriter output, String value) { output.print(value + "\r\n"); output.flush(); }
    @Override public void close() throws IOException { server.close(); worker.shutdownNow(); }
}
