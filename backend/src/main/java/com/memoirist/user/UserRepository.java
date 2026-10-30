package com.memoirist.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Users by id. The id <em>is</em> the current user's id (from {@code CurrentUserProvider}), so this is the one
 * repository where a plain {@code findById} is the user-scoped lookup. Like every repository here it extends
 * {@link Repository} rather than {@code JpaRepository}, so only the methods below exist.
 */
public interface UserRepository extends Repository<User, UUID> {

    Optional<User> findById(UUID id);

    <S extends User> S save(S user);
}
