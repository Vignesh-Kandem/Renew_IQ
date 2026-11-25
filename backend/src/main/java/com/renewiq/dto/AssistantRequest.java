package com.renewiq.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AssistantRequest(@NotEmpty @Size(max = 30) @Valid List<Msg> messages,
                               @Size(max = 100) List<@jakarta.validation.constraints.Positive Long> documentIds) {

    public record Msg(
            @NotBlank @Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = 2000) String content
    ) { }
}
