package com.memoirist.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Which profiles have the dev provider (security.md: "No other profile may resolve X-Dev-User-Id").
 *
 * <p>A {@link ApplicationContextRunner} builds a tiny Spring context with just the provider registered and a chosen
 * profile active, then asks whether the bean exists. It needs no database or Docker, so the whole profile list can be
 * checked in milliseconds. The real application is checked in {@link CurrentUserWiringTest} and
 * {@link DevProviderAbsentInProdTest}.
 */
class CurrentUserProfileTest {

    /**
     * The provider is registered by class, so its {@code @Profile} decides whether the bean is created. The request it
     * needs is a plain mock: nothing here handles a request.
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DevHeaderCurrentUserProvider.class)
            .withBean(HttpServletRequest.class, MockHttpServletRequest::new);

    @ParameterizedTest(name = "profile {0}: the dev provider exists")
    @ValueSource(strings = {"local", "test"})
    void existsInLocalAndTest(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DevHeaderCurrentUserProvider.class);
            assertThat(context).hasSingleBean(CurrentUserProvider.class);
        });
    }

    @ParameterizedTest(name = "profile {0}: the dev provider does not exist")
    @ValueSource(strings = {"prod", "production", "staging", "dev", "docker", "prod,docker"})
    void doesNotExistInAnyOtherProfile(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DevHeaderCurrentUserProvider.class);
            assertThat(context).doesNotHaveBean(CurrentUserProvider.class);
        });
    }

    @Test
    void doesNotExistWhenNoProfileIsChosen() {
        // Spring's "default" profile is what an app runs with when nobody sets one. That must not be a dev app.
        runner.run(context -> {
            assertThat(context.getEnvironment().getActiveProfiles()).isEmpty();
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(CurrentUserProvider.class);
        });
    }

    @Test
    void anAppThatNeedsAProviderDoesNotStartWithoutOne() {
        // Task 3.2's UserService asks for a CurrentUserProvider. Outside local and test no bean provides one (until
        // Phase 2), and the context must refuse to start instead of falling back to some default user. This is the
        // safety that the missing bean was always meant to give (TECH_STACK, Current user).
        new ApplicationContextRunner()
                .withUserConfiguration(DevHeaderCurrentUserProvider.class, UserService.class)
                .withBean(HttpServletRequest.class, MockHttpServletRequest::new)
                .withBean(UserRepository.class, () -> mock(UserRepository.class))
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("CurrentUserProvider");
                });
    }
}
