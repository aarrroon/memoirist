package com.memoirist.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.memoirist.Fixtures;
import com.memoirist.IntegrationTest;
import com.memoirist.era.Era;
import com.memoirist.era.EraRepository;
import com.memoirist.tag.Tag;
import com.memoirist.tag.TagRepository;
import com.memoirist.template.Template;
import com.memoirist.template.TemplateRepository;
import com.memoirist.user.User;
import com.memoirist.user.UserRepository;
import com.memoirist.week.WeeklyLog;
import com.memoirist.week.WeeklyLogRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * No finder returns another user's row (task 2.4, non-negotiable 3). Entries and media have their own tests
 * ({@code EntryRepositoryTest}, {@code MediaRepositoryTest}), because they also have Recently deleted to deal with.
 *
 * <p>For each repository: Alice's rows come back for Alice (so an empty result for Bob can't be an empty table), and
 * Bob asking for the same ids, the same names or the same dates gets nothing. The services turn that nothing into a
 * {@code 404}, never a {@code 403}, so a stranger can't tell "not yours" from "doesn't exist".
 */
@IntegrationTest
@Transactional
class OwnershipFindersTest {

    @Autowired
    EntityManager em;

    @Autowired
    UserRepository users;

    @Autowired
    EraRepository eras;

    @Autowired
    TemplateRepository templates;

    @Autowired
    WeeklyLogRepository weeks;

    @Autowired
    TagRepository tags;

    User alice;
    User bob;

    @BeforeEach
    void twoUsers() {
        alice = users.save(Fixtures.user());
        bob = users.save(Fixtures.user());
    }

    @Test
    void erasAreScopedToTheirOwner() {
        Era era = eras.save(new Era(UUID.randomUUID(), alice.getId(), "Uni Days", LocalDate.of(2022, 3, 1)));
        Era bobsEra = eras.save(new Era(UUID.randomUUID(), bob.getId(), "Bob's era", LocalDate.of(2020, 1, 1)));
        em.flush();
        em.clear();

        assertThat(eras.findByIdAndUserId(era.getId(), alice.getId())).isPresent();
        assertThat(eras.findByIdAndUserId(era.getId(), bob.getId())).isEmpty();
        assertThat(eras.findByUserIdOrderByStartDateDesc(bob.getId()))
                .extracting(Era::getId)
                .containsExactly(bobsEra.getId());
    }

    @Test
    void erasAreListedLatestStartFirst() {
        Era old = eras.save(new Era(UUID.randomUUID(), alice.getId(), "School", LocalDate.of(2010, 1, 1)));
        Era recent = eras.save(new Era(UUID.randomUUID(), alice.getId(), "Work", LocalDate.of(2024, 1, 1)));
        Era middle = eras.save(new Era(UUID.randomUUID(), alice.getId(), "Uni", LocalDate.of(2018, 1, 1)));
        em.flush();
        em.clear();

        assertThat(eras.findByUserIdOrderByStartDateDesc(alice.getId()))
                .extracting(Era::getId)
                .containsExactly(recent.getId(), middle.getId(), old.getId());
    }

    @Test
    void templatesAreScopedToTheirOwner() {
        Template template = templates.save(new Template(UUID.randomUUID(), alice.getId(), "Morning", List.of("Goals")));
        em.flush();
        em.clear();

        assertThat(templates.findByIdAndUserId(template.getId(), alice.getId())).isPresent();
        assertThat(templates.findByIdAndUserId(template.getId(), bob.getId())).isEmpty();
        assertThat(templates.findByUserId(bob.getId())).isEmpty();
        assertThat(templates.findByUserId(alice.getId())).hasSize(1);
    }

    @Test
    void weeksAreScopedToTheirOwner() {
        WeeklyLog week = weeks.save(Fixtures.week(alice.getId()));
        em.flush();
        em.clear();

        assertThat(weeks.findByIdAndUserId(week.getId(), alice.getId())).isPresent();
        assertThat(weeks.findByIdAndUserId(week.getId(), bob.getId())).isEmpty();
        assertThat(weeks.findByUserIdAndStartDate(alice.getId(), Fixtures.MONDAY))
                .get()
                .extracting(WeeklyLog::getId)
                .isEqualTo(week.getId());
        assertThat(weeks.findByUserIdAndStartDate(bob.getId(), Fixtures.MONDAY))
                .as("the same Monday is a different week for Bob")
                .isEmpty();
    }

    @Test
    void tagsAreScopedToTheirOwner() {
        Tag work = tags.save(Fixtures.tag(alice.getId(), "Work"));
        Tag family = tags.save(Fixtures.tag(alice.getId(), "Family"));
        Tag bobsWork = tags.save(Fixtures.tag(bob.getId(), "Work"));
        em.flush();
        em.clear();

        assertThat(tags.findByIdAndUserId(work.getId(), alice.getId())).isPresent();
        assertThat(tags.findByIdAndUserId(work.getId(), bob.getId())).isEmpty();
        assertThat(tags.findByUserIdOrderByNameAsc(alice.getId()))
                .extracting(Tag::getName)
                .containsExactly("Family", "Work");
        assertThat(tags.findByIdInAndUserId(List.of(work.getId(), family.getId(), bobsWork.getId()), alice.getId()))
                .as("Bob's tag id in the list is dropped, so the service sees one id missing and answers 404")
                .extracting(Tag::getId)
                .containsExactlyInAnyOrder(work.getId(), family.getId());
    }

    @Test
    void tagNamesAreUniquePerUserIgnoringCaseNotAcrossUsers() {
        tags.save(Fixtures.tag(alice.getId(), "Work"));
        em.flush();
        em.clear();

        assertThat(tags.findByUserIdAndNameIgnoreCase(alice.getId(), "wORK")).isPresent();
        assertThat(tags.findByUserIdAndNameIgnoreCase(bob.getId(), "Work"))
                .as("Bob may have his own Work")
                .isEmpty();
    }

    @Test
    void aUserIsFoundOnlyByTheirOwnId() {
        assertThat(users.findById(alice.getId()))
                .get()
                .extracting(User::getEmail)
                .isEqualTo(alice.getEmail());
        assertThat(users.findById(UUID.randomUUID())).isEmpty();
    }
}
