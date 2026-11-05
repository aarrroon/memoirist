package com.memoirist.era;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Eras, always looked up together with the owning user (security.md). */
public interface EraRepository extends Repository<Era, UUID> {

    <S extends Era> S save(S era);

    /**
     * Saves and sends the INSERT or UPDATE now, instead of at commit. Hibernate fills {@code createdAt} and
     * {@code updatedAt} while it writes the row, so a response built from the entity before that would show them empty
     * (or, after an update, show the old {@code updatedAt}).
     */
    <S extends Era> S saveAndFlush(S era);

    void delete(Era era);

    Optional<Era> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Whether any user has an Era with this id. The one lookup that isn't scoped to a user, and only for the client
     * supplied {@code id} of a create (D-12): it tells {@code 409 conflict} from a fresh id. Never use it to read an
     * Era; the answer reveals nothing but "taken".
     */
    boolean existsById(UUID id);

    /** All of the user's Eras, latest start first. A week's Eras are matched from this one list by date (D-22). */
    List<Era> findByUserIdOrderByStartDateDesc(UUID userId);
}
