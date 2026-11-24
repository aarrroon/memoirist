package com.memoirist.template.dto;

import com.memoirist.template.Template;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * The body of {@code POST /api/v1/templates} (API_CONTRACT, Templates). {@code id} is the client's own UUID, so a retry
 * of the same request finds the template it already made (D-12); leave it out and the server picks one.
 *
 * <p>The text is cleaned in the constructor, before it is validated: the name and every subheading lose the spaces at
 * both ends, so a subheading of only spaces is empty and fails its length rule. A missing list stays {@code null} and
 * is reported as {@code must not be null}: the contract does not mark {@code subheadings} as optional, and an empty
 * list ({@code []}) is the way to say "no prompts". The order of the subheadings is kept, and repeats are allowed.
 *
 * @param name 1 to {@value Template#MAX_NAME_LENGTH} characters once stripped
 * @param subheadings at most {@value Template#MAX_SUBHEADINGS} items of 1 to {@value Template#MAX_SUBHEADING_LENGTH}
 *     characters each once stripped; a failing item is reported by its position, such as {@code subheadings[2]}
 */
public record TemplateCreateRequest(
        UUID id,

        @NotNull @Size(min = 1, max = Template.MAX_NAME_LENGTH)
        String name,

        @NotNull @Size(max = Template.MAX_SUBHEADINGS)
        List<@NotNull @Size(min = 1, max = Template.MAX_SUBHEADING_LENGTH) String> subheadings) {

    public TemplateCreateRequest {
        name = TemplateText.name(name);
        subheadings = TemplateText.subheadings(subheadings);
    }
}
