package com.memoirist.tag;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Tags, always looked up together with the owning user (security.md). */
public interface TagRepository extends Repository<Tag, UUID> {

    <S extends Tag> S save(S tag);

    void delete(Tag tag);

    Optional<Tag> findByIdAndUserId(UUID id, UUID userId);

    List<Tag> findByUserIdOrderByNameAsc(UUID userId);

    /**
     * The user's tags among these ids. A service that gets {@code tagIds} in a request compares the size of this list
     * with the number of distinct ids: a missing one is a tag that isn't theirs, which is {@code 404}.
     */
    List<Tag> findByIdInAndUserId(Collection<UUID> ids, UUID userId);

    /** For the friendly duplicate-name error; the unique index on (user_id, lower(name)) is the real guard. */
    Optional<Tag> findByUserIdAndNameIgnoreCase(UUID userId, String name);
}
