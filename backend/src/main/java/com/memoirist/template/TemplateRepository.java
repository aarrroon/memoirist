package com.memoirist.template;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Templates, always looked up together with the owning user (security.md). */
public interface TemplateRepository extends Repository<Template, UUID> {

    <S extends Template> S save(S template);

    /**
     * Saves and sends the INSERT or UPDATE now, instead of at commit. Hibernate fills {@code createdAt} and
     * {@code updatedAt} while it writes the row, so a response built from the entity before that would show them empty
     * (or, after an update, show the old {@code updatedAt}).
     */
    <S extends Template> S saveAndFlush(S template);

    void delete(Template template);

    Optional<Template> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Whether any user has a template with this id. The one lookup that isn't scoped to a user, and only for the
     * client supplied {@code id} of a create (D-12): it tells {@code 409 conflict} from a fresh id. Never use it to
     * read a template; the answer reveals nothing but "taken".
     */
    boolean existsById(UUID id);

    /** All of the user's templates, in no promised order: the service sorts them with {@link Template#BY_NAME}. */
    List<Template> findByUserId(UUID userId);
}
