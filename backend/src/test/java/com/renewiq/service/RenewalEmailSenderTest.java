package com.renewiq.service;

import com.renewiq.model.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RenewalEmailSenderTest {
    @Test void formatsMessageToSubscriptionOwner() {
        JavaMailSender mail = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mail); when(provider.getObject()).thenReturn(mail);
        var sender = new RenewalEmailSender(provider, true, "reminders@example.com", "smtp.example.com");
        var owner = new AppUser(); owner.setEmail("owner@example.com");
        var plan = new Subscription(); plan.setUser(owner); plan.setName("Music\r\nPlan");
        plan.setNextRenewalDate(LocalDate.of(2026, 11, 18)); plan.setAmount(new BigDecimal("299.00"));
        sender.send(plan, 1);
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class); verify(mail).send(message.capture());
        assertArrayEquals(new String[]{"owner@example.com"}, message.getValue().getTo());
        assertEquals("reminders@example.com", message.getValue().getFrom());
        assertFalse(message.getValue().getSubject().contains("\n"));
        assertTrue(message.getValue().getText().contains("2026-11-18"));
        assertTrue(message.getValue().getText().contains("INR 299.00"));
    }
    @Test void refusesEnabledEmailWithoutSmtpHostOrSender() {
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        assertThrows(IllegalStateException.class, () -> new RenewalEmailSender(provider, true, "", ""));
        assertDoesNotThrow(() -> new RenewalEmailSender(provider, false, "", ""));
    }
}
