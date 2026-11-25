package com.renewiq.dto;

import com.renewiq.model.SubscriptionStatus;
import jakarta.validation.constraints.NotNull;

public record ConfirmRequest(@NotNull Long subscriptionId, @NotNull SubscriptionStatus status) { }
