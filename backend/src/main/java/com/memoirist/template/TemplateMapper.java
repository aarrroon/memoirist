package com.memoirist.template;

import com.memoirist.template.dto.TemplateResponse;

/** Template to DTO, written by hand (no MapStruct, backend rules). Call it inside the service's transaction. */
final class TemplateMapper {

    private TemplateMapper() {}

    static TemplateResponse toResponse(Template template) {
        return new TemplateResponse(
                template.getId(),
                template.getName(),
                template.getSubheadings(),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }
}
