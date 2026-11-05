package com.memoirist.tag;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Tags, always looked up together with the owning user (security.md). */
public interface TagRepository extends Repository<Tag, UUID> {

    <S extends Tag> S save(S tag);

    /**
     * Saves and sends the INSERT or UPDATE now, instead of at commit. Hibernate fills {@code updatedAt} while it
     * writes the row, so a response built from the entity before that would show the old one. A rename that loses a
     * race on the unique name also fails here, inside the service, where it can become a {@code 409}.
     */
    <S extends Tag> S saveAndFlush(S tag);

    void delete(Tag tag);

    Optional<Tag> findByIdAndUserId(UUID id, UUID userId);

    /** All of the user's tags, in no promised order: the service sorts them with {@link Tag#BY_NAME}. */
    List<Tag> findByUserId(UUID userId);

    /**
     * The user's tags among these ids. A service that gets {@code tagIds} in a request compares the size of this list
     * with the number of distinct ids: a missing one is a tag that isn't theirs, which is {@code 404}.
     *
     * <p>Pass a non-empty collection: some databases refuse an empty {@code IN ()}.
     */
    List<Tag> findByIdInAndUserId(Collection<UUID> ids, UUID userId);

    /**
     * The same lookup, and the rows it finds stay locked until the transaction ends ({@code SELECT ... FOR SHARE}).
     * Used when an entry is about to link to these tags: a {@code DELETE} of one of them waits for the entry's
     * transaction, and a tag whose delete committed first is simply not found. Without the lock the entry would pass
     * the check, the tag would be deleted, and the insert into {@code entry_tags} would break the foreign key with a
     * {@code 500}. Several entries can hold the share lock on the same tag at once, so two edits never wait for each
     * other. Needs a running transaction.
     *
     * <p>Pass a non-empty collection: some databases refuse an empty {@code IN ()}.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select t from Tag t where t.id in :ids and t.userId = :userId")
    List<Tag> findAndLockByIdInAndUserId(@Param("ids") Collection<UUID> ids, @Param("userId") UUID userId);

    /**
     * The user's tag with this name, ignoring case. Written as {@code lower(name) = lower(:name)} because that is the
     * expression the unique index {@code uq_tags_user_name} is built on, so the lookup and the guard agree on what "the
     * same name" means and the index serves the query. (A derived {@code ...IgnoreCase} finder would compare
     * {@code upper(...)}, which the index cannot use and which can disagree for a few letters.)
     */
    @Query("select t from Tag t where t.userId = :userId and lower(t.name) = lower(:name)")
    Optional<Tag> findByUserIdAndNameIgnoreCase(@Param("userId") UUID userId, @Param("name") String name);

    /**
     * Inserts the tag unless the user already has one with this name, ignoring case, and says nothing if so. Native
     * SQL, because JPA has no {@code ON CONFLICT}; the conflict target is the expression of the unique index, and
     * {@code created_at} and {@code updated_at} take their column defaults. {@code TagService.create} calls it and
     * then selects the row, the same two steps as a week (D-08): "select, then insert if missing" leaves a gap in which
     * two requests for the same new name both insert, and the loser dies on the unique index with a {@code 500}.
     *
     * @return 1 if the tag was inserted, 0 if the user already had one with that name
     */
    @Modifying
    @Query(value = """
                    INSERT INTO tags (id, user_id, name)
                    VALUES (:id, :userId, :name)
                    ON CONFLICT (user_id, lower(name)) DO NOTHING
                    """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("userId") UUID userId, @Param("name") String name);
}
