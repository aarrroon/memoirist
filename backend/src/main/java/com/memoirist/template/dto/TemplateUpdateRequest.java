package com.memoirist.template.dto;

import com.memoirist.template.Template;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The body of {@code PUT /api/v1/templates/{templateId}}: the same as {@link TemplateCreateRequest} without the
 * {@code id}, because the path names the template. A {@code PUT} replaces the whole template, which is why
 * {@code subheadings} is required here too: leaving it out must not quietly empty the list.
 *
 * @param name 1 to {@value Template#MAX_NAME_LENGTH} characters once stripped
 * @param subheadings at most {@value Template#MAX_SUBHEADINGS} items of 1 to {@value Template#MAX_SUBHEADING_LENGTH}
 *     characters each once stripped
 */
public record TemplateUpdateRequest(
        @NotNull @Size(min = 1, max = Template.MAX_NAME_LENGTH)
        String name,

        @NotNull @Size(max = Template.MAX_SUBHEADINGS)
        List<@NotNull @Size(min = 1, max = Template.MAX_SUBHEADING_LENGTH) String> subheadings) {

    public TemplateUpdateRequest {
        name = TemplateText.name(name);
        subheadings = TemplateText.subheadings(subheadings);
    }
}
