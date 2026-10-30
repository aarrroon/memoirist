package com.memoirist.template;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Templates, always looked up together with the owning user (security.md). */
public interface TemplateRepository extends Repository<Template, UUID> {

    <S extends Template> S save(S template);

    void delete(Template template);

    Optional<Template> findByIdAndUserId(UUID id, UUID userId);

    List<Template> findByUserIdOrderByNameAsc(UUID userId);
}
