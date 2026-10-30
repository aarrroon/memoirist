package com.memoirist.week;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Weeks, always looked up together with the owning user (security.md). */
public interface WeeklyLogRepository extends Repository<WeeklyLog, UUID> {

    <S extends WeeklyLog> S save(S week);

    Optional<WeeklyLog> findByIdAndUserId(UUID id, UUID userId);

    /** The user's week that starts on this Monday; backed by the unique constraint on (user_id, start_date). */
    Optional<WeeklyLog> findByUserIdAndStartDate(UUID userId, LocalDate startDate);
}
