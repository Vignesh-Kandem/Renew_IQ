package com.renewiq.service;

import com.renewiq.model.Subscription;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class RenewalEmailSender {
    private final ObjectProvider<JavaMailSender> mail;
    private final boolean enabled;
    private final String from;
    public RenewalEmailSender(ObjectProvider<JavaMailSender> mail,
            @Value("${renewiq.reminders.email-enabled:false}") boolean enabled,
            @Value("${renewiq.reminders.from:}") String from,
            @Value("${spring.mail.host:}") String host) {
        this.mail = mail; this.enabled = enabled; this.from = from;
        if (enabled && (from.isBlank() || host.isBlank() || mail.getIfAvailable() == null)) {
            throw new IllegalStateException("Renewal email requires SMTP_HOST and REMINDER_FROM.");
        }
    }
    public boolean enabled() { return enabled; }
    public void send(Subscription subscription, int days) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(subscription.getUser().getEmail());
        String timing = days == 0 ? " renews today" : " renews in " + days + " day" + (days == 1 ? "" : "s");
        message.setSubject("RenewIQ: " + subscription.getName().replaceAll("[\\r\\n]", " ") + timing);
        message.setText("Your " + subscription.getName() + " subscription renews on "
                + subscription.getNextRenewalDate() + ".\nAmount: INR " + subscription.getAmount().toPlainString()
                + "\n\nOpen RenewIQ to review your subscription. This is a reminder, not a payment receipt.");
        mail.getObject().send(message);
    }
}
