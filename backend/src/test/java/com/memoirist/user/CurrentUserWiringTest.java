package com.memoirist.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memoirist.IntegrationTest;
import com.memoirist.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * The current-user pieces inside the real application in the {@code test} profile (the same classes the {@code local}
 * profile uses): Spring picks the provider up by component scanning, injects the request into it, and the user it
 * falls back to is really in the database.
 */
@IntegrationTest
class CurrentUserWiringTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    CurrentUserProvider currentUser;

    @Autowired
    UserRepository users;

    @Test
    void theOnlyProviderIsTheDevHeaderOne() {
        assertThat(context.getBeansOfType(CurrentUserProvider.class)).hasSize(1);
        assertThat(currentUser).isInstanceOf(DevHeaderCurrentUserProvider.class);
    }

    @Test
    void theFallbackUserIsTheOneTheSeedCreates() {
        // The constant in Java and the id in db/seed-local/R__seed_dev_user.sql are two copies of one fact. If they
        // drift apart, every request without a header would act as a user who doesn't exist.
        User dev = users.findById(DevHeaderCurrentUserProvider.DEV_USER_ID).orElseThrow();

        assertThat(dev.getEmail()).isEqualTo("dev@memoirist.local");
    }

    @Test
    void callingItOutsideARequestFailsInsteadOfGuessingAUser() {
        // A test method runs on no request thread. A background job is the same: it has no current user, so asking
        // for one is a bug, and the dev user must not quietly stand in.
        assertThatThrownBy(currentUser::currentUserId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theErrorHandlerThatAnswersAMalformedHeaderIsPickedUpByScanning() {
        // CurrentUserHttpTest imports the handler by hand. This checks that the running app finds it on its own.
        assertThat(context.getBeansOfType(GlobalExceptionHandler.class)).hasSize(1);
    }
}
