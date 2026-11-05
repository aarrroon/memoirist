package com.memoirist.template.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A template as the API shows it (API_CONTRACT, Templates). There is no {@code userId}: whose it is is never sent.
 *
 * @param subheadings the prompts in the order the user put them; empty (never {@code null}) for a template with none
 */
public record TemplateResponse(UUID id, String name, List<String> subheadings, Instant createdAt, Instant updatedAt) {}
