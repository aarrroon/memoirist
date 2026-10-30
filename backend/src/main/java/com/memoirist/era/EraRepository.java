package com.memoirist.era;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Eras, always looked up together with the owning user (security.md). */
public interface EraRepository extends Repository<Era, UUID> {

    <S extends Era> S save(S era);

    void delete(Era era);

    Optional<Era> findByIdAndUserId(UUID id, UUID userId);

    /** All of the user's Eras, latest start first. A week's Eras are matched from this one list by date (D-22). */
    List<Era> findByUserIdOrderByStartDateDesc(UUID userId);
}
