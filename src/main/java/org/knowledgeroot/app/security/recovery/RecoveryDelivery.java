package org.knowledgeroot.app.security.recovery;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;

/** Bounded in-memory work queue: account lookup and SMTP are outside the public request. */
@Component
@Slf4j
public class RecoveryDelivery {
    private final RecoverySettings settings;
    private final ObjectProvider<JavaMailSender> sender;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100), Thread.ofPlatform().daemon().name("account-recovery-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    public RecoveryDelivery(RecoverySettings settings, ObjectProvider<JavaMailSender> sender) {
        this.settings = settings; this.sender = sender;
    }

    @PostConstruct void validate() {
        if (settings.isEnabled()) {
            var configured = sender.getIfAvailable();
            if (configured == null || (configured instanceof org.springframework.mail.javamail.JavaMailSenderImpl smtp
                    && (smtp.getHost() == null || smtp.getHost().isBlank())))
                throw new IllegalStateException("Account recovery requires spring.mail.host");
        }
    }

    public void submit(Runnable task) {
        try {
            worker.execute(() -> {
                try { task.run(); }
                catch (Exception ex) { log.warn("Account recovery delivery failed; check mail and database availability."); }
            });
        } catch (RejectedExecutionException ex) { log.warn("Account recovery queue is full; request was not queued."); }
    }

    public void send(String email, String subject, String text) {
        var message = new SimpleMailMessage();
        message.setFrom(settings.getFrom()); message.setTo(email);
        message.setSubject(subject); message.setText(text);
        sender.getObject().send(message);
    }

    @PreDestroy void stop() { worker.shutdownNow(); }
}
