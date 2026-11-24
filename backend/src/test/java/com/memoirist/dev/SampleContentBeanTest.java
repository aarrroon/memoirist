package com.memoirist.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.memoirist.IntegrationTest;
import com.memoirist.entry.EntryRepository;
import com.memoirist.era.EraRepository;
import com.memoirist.media.MediaRepository;
import com.memoirist.storage.BucketBootstrap;
import com.memoirist.storage.StorageService;
import com.memoirist.tag.TagRepository;
import com.memoirist.template.TemplateRepository;
import com.memoirist.user.UserRepository;
import com.memoirist.week.WeekService;
import java.time.Clock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Where the sample content seeder exists (task 3.16): only in the {@code local} profile, and only with
 * {@code memoirist.sample-content=true}. The first nested class asks the real app in the {@code test} profile; the
 * second asks a small context with the profile and the property set both ways, so each gate is shown to work alone.
 */
class SampleContentBeanTest {

    @Nested
    @IntegrationTest
    class InTheTestProfile {

        @Autowired
        ApplicationContext context;

        @Test
        void theSeederIsNotABeanOfTheTestApp() {
            assertThat(context.getBeanNamesForType(SampleContentSeeder.class)).isEmpty();
            assertThat(context.getBeansOfType(SampleContentSeeder.class)).isEmpty();
        }

        @Test
        void thePropertyIsNotSetEither() {
            assertThat(context.getEnvironment().getProperty("memoirist.sample-content"))
                    .isNull();
        }
    }

    @Nested
    class InASmallContext {

        /**
         * The profiles as a property, not set on the environment by an initializer: the mock of {@code BucketBootstrap}
         * (itself {@code @Profile("local")}) is registered before an initializer runs, and would be skipped.
         */
        private static String[] activeProfiles(String... profiles) {
            return profiles.length == 0
                    ? new String[0]
                    : new String[] {"spring.profiles.active=" + String.join(",", profiles)};
        }

        /** The seeder with every collaborator replaced by a mock: only its annotations are under test. */
        private ApplicationContextRunner contextWith(String... profiles) {
            return new ApplicationContextRunner()
                    .withPropertyValues(activeProfiles(profiles))
                    .withBean(UserRepository.class, () -> mock(UserRepository.class))
                    .withBean(TagRepository.class, () -> mock(TagRepository.class))
                    .withBean(TemplateRepository.class, () -> mock(TemplateRepository.class))
                    .withBean(EntryRepository.class, () -> mock(EntryRepository.class))
                    .withBean(MediaRepository.class, () -> mock(MediaRepository.class))
                    .withBean(EraRepository.class, () -> mock(EraRepository.class))
                    .withBean(WeekService.class, () -> mock(WeekService.class))
                    .withBean(StorageService.class, () -> mock(StorageService.class))
                    .withBean(BucketBootstrap.class, () -> mock(BucketBootstrap.class))
                    .withBean(JdbcClient.class, () -> mock(JdbcClient.class))
                    .withBean(Clock.class, Clock::systemUTC)
                    .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                    .withUserConfiguration(SampleContentSeeder.class);
        }

        @Test
        void theLocalProfileWithThePropertyOnHasTheSeeder() {
            contextWith("local")
                    .withPropertyValues("memoirist.sample-content=true")
                    .run(context -> assertThat(context).hasSingleBean(SampleContentSeeder.class));
        }

        @Test
        void theLocalProfileWithoutTheProperty_doesNot() {
            contextWith("local").run(context -> assertThat(context).doesNotHaveBean(SampleContentSeeder.class));
        }

        @ParameterizedTest
        @ValueSource(strings = {"false", "", "1", "yes"})
        void theLocalProfileWithAnyOtherValue_doesNot(String value) {
            contextWith("local")
                    .withPropertyValues("memoirist.sample-content=" + value)
                    .run(context -> assertThat(context).doesNotHaveBean(SampleContentSeeder.class));
        }

        @ParameterizedTest
        @ValueSource(strings = {"test", "prod", "staging"})
        void everyOtherProfile_doesNotEvenWithThePropertyOn(String profile) {
            contextWith(profile)
                    .withPropertyValues("memoirist.sample-content=true")
                    .run(context -> assertThat(context).doesNotHaveBean(SampleContentSeeder.class));
        }

        @Test
        void noProfileAtAll_doesNotEvenWithThePropertyOn() {
            contextWith()
                    .withPropertyValues("memoirist.sample-content=true")
                    .run(context -> assertThat(context).doesNotHaveBean(SampleContentSeeder.class));
        }
    }
}
