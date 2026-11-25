package com.renewiq.config;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;

@Configuration
public class ReminderConfig {
    @Bean public Clock reminderClock(@Value("${renewiq.reminders.zone:Asia/Kolkata}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
