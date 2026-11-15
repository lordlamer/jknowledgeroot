package org.knowledgeroot.app.notification;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Durable mail queue (R66): mails are rows in {@code mail_queue}, a single worker thread sends the due
 * ones and retries failures with a growing delay. After {@link #MAX_ATTEMPTS} a mail stays in the
 * queue as failed until an administrator retries or deletes it under Admin → Mail. Sent mails are
 * kept for {@link #SENT_RETENTION_DAYS} days.
 */
@Service
@Slf4j
public class MailQueue {
    public static final int MAX_ATTEMPTS = 8;
    public static final int SENT_RETENTION_DAYS = 30;
    private static final int BATCH = 20;
    private static final String COLUMNS = "id, user_id, sender, recipient, subject, body, create_date, next_attempt, attempts, last_error, sent_date";
    private final JdbcClient jdbc;
    private final ObjectProvider<JavaMailSender> sender;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("mail-queue-", 0).factory());
    private final AtomicBoolean scheduled = new AtomicBoolean();

    public MailQueue(JdbcClient jdbc, ObjectProvider<JavaMailSender> sender) {
        this.jdbc = jdbc;
        this.sender = sender;
    }

    /** A queued mail; {@code status} is pending, failed or sent. */
    public record Mail(int id, Integer userId, String sender, String recipient, String subject, String body, LocalDateTime createDate,
                       LocalDateTime nextAttempt, int attempts, String lastError, LocalDateTime sentDate) {
        public String status() {
            if (sentDate != null) return "sent";
            return attempts >= MAX_ATTEMPTS ? "failed" : "pending";
        }
    }

    public record Counts(int pending, int failed, int sent) {}

    private static Mail mail(ResultSet rs, int row) throws SQLException {
        return new Mail(rs.getInt("id"), rs.getObject("user_id", Integer.class), rs.getString("sender"), rs.getString("recipient"),
                rs.getString("subject"), rs.getString("body"), rs.getTimestamp("create_date").toLocalDateTime(),
                rs.getTimestamp("next_attempt").toLocalDateTime(), rs.getInt("attempts"), rs.getString("last_error"),
                rs.getTimestamp("sent_date") == null ? null : rs.getTimestamp("sent_date").toLocalDateTime());
    }

    /** Stores the mail and wakes the worker; the row survives a restart and a mail server outage. */
    public int enqueue(Integer userId, String from, String to, String subject, String body) {
        var keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
        LocalDateTime now = LocalDateTime.now();
        jdbc.sql("INSERT INTO mail_queue (user_id, sender, recipient, subject, body, create_date, next_attempt) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(userId, from, to, subject, body, now, now).update(keys);
        wake();
        return keys.getKey().intValue();
    }

    /** Runs the worker once more if it is not already waiting to run; callers never block on SMTP. */
    public void wake() {
        if (!scheduled.compareAndSet(false, true)) return;
        try {
            worker.execute(() -> {
                scheduled.set(false);
                deliverDue();
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            scheduled.set(false);
        }
    }

    /** Fallback for retries whose delay has passed, and the cleanup of old sent mails. */
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    public void sweep() {
        wake();
    }

    @Scheduled(initialDelayString = "PT10M", fixedDelayString = "PT6H")
    public void cleanup() {
        int removed = jdbc.sql("DELETE FROM mail_queue WHERE sent_date IS NOT NULL AND sent_date < ?")
                .param(LocalDateTime.now().minusDays(SENT_RETENTION_DAYS)).update();
        if (removed > 0) log.info("Removed {} sent mails older than {} days from the queue", removed, SENT_RETENTION_DAYS);
    }

    /** Sends every due mail; failures are rescheduled with a growing delay. Also used by the tests directly. */
    public void deliverDue() {
        try {
            for (int round = 0; round < 10; round++) {
                List<Mail> due = jdbc.sql("SELECT " + COLUMNS + " FROM mail_queue WHERE sent_date IS NULL AND attempts < :max AND next_attempt <= :now ORDER BY id LIMIT " + BATCH)
                        .param("max", MAX_ATTEMPTS).param("now", LocalDateTime.now()).query(MailQueue::mail).list();
                if (due.isEmpty()) return;
                for (Mail mail : due) deliver(mail);
                if (due.size() < BATCH) return;
            }
        } catch (RuntimeException e) {
            log.warn("Mail queue worker stopped this round: {}", e.getMessage());
        }
    }

    private void deliver(Mail mail) {
        try {
            JavaMailSender configured = sender.getIfAvailable();
            if (configured == null || (configured instanceof org.springframework.mail.javamail.JavaMailSenderImpl smtp
                    && (smtp.getHost() == null || smtp.getHost().isBlank())))
                throw new IllegalStateException("No SMTP server is configured (KR_SMTP_HOST)");
            var message = new SimpleMailMessage();
            message.setFrom(mail.sender());
            message.setTo(mail.recipient());
            message.setSubject(mail.subject());
            message.setText(mail.body());
            configured.send(message);
            jdbc.sql("UPDATE mail_queue SET sent_date = ?, last_error = NULL WHERE id = ?").params(LocalDateTime.now(), mail.id()).update();
        } catch (RuntimeException e) {
            int attempts = mail.attempts() + 1;
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (error.length() > 1024) error = error.substring(0, 1024);
            LocalDateTime next = LocalDateTime.now().plusSeconds(Math.min(3600, 60L << Math.min(attempts, 6)));
            jdbc.sql("UPDATE mail_queue SET attempts = ?, last_error = ?, next_attempt = ? WHERE id = ?").params(attempts, error, next, mail.id()).update();
            if (attempts >= MAX_ATTEMPTS) log.error("Mail {} to {} failed {} times and waits for an administrator: {}", mail.id(), mail.recipient(), attempts, error);
            else log.warn("Mail {} to {} could not be sent (attempt {}), next try at {}: {}", mail.id(), mail.recipient(), attempts, next, error);
        }
    }

    // --- administration -----------------------------------------------------------------------------

    public Counts counts() {
        return jdbc.sql("""
                SELECT SUM(CASE WHEN sent_date IS NULL AND attempts < :max THEN 1 ELSE 0 END) AS pending,
                       SUM(CASE WHEN sent_date IS NULL AND attempts >= :max THEN 1 ELSE 0 END) AS failed,
                       SUM(CASE WHEN sent_date IS NOT NULL THEN 1 ELSE 0 END) AS sent
                FROM mail_queue
                """).param("max", MAX_ATTEMPTS).query((rs, row) -> new Counts(rs.getInt("pending"), rs.getInt("failed"), rs.getInt("sent"))).single();
    }

    /** Unsent mails first (failed ones on top), then the most recent sent ones; at most {@code limit}. */
    public List<Mail> list(int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM mail_queue ORDER BY sent_date IS NOT NULL, attempts >= :max DESC, id DESC LIMIT :limit")
                .param("max", MAX_ATTEMPTS).param("limit", limit).query(MailQueue::mail).list();
    }

    public java.util.Optional<Mail> find(int id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM mail_queue WHERE id = :id").param("id", id).query(MailQueue::mail).optional();
    }

    /** Puts a failed or waiting mail back to the front of the queue. */
    public boolean retry(int id) {
        boolean reset = jdbc.sql("UPDATE mail_queue SET attempts = 0, next_attempt = ? WHERE id = ? AND sent_date IS NULL")
                .params(LocalDateTime.now(), id).update() == 1;
        if (reset) wake();
        return reset;
    }

    public boolean delete(int id) {
        return jdbc.sql("DELETE FROM mail_queue WHERE id = ?").param(id).update() == 1;
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }
}
