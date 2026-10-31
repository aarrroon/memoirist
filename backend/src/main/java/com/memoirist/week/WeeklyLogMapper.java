package com.memoirist.week;

import com.memoirist.entry.Entry;
import com.memoirist.entry.EntryMapper;
import com.memoirist.era.Era;
import com.memoirist.week.dto.WeekEraResponse;
import com.memoirist.week.dto.WeeklyLogResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * A week and what hangs off it become a {@link WeeklyLogResponse}. The week's entries go through
 * {@link EntryMapper#toResponse}, the only place an entry is turned into a response, so the feed and week detail hide a
 * sealed Loot Drop by the same code as every other endpoint (D-10). This class never reads an entry's private fields
 * itself.
 *
 * <p>It runs no query of its own. The caller loads the week's entries, the user's Eras and the media and tags of the
 * open entries up front, so mapping a page of weeks costs the same number of queries however many entries and media they
 * hold. If a caller forgot, the lazy lists of an open entry would still load (Hibernate batches them, 50 entries at a
 * time) and the answer would be the same, only slower. Call it inside the service's transaction (open-in-view is off).
 */
@Component
class WeeklyLogMapper {

    private final EntryMapper entryMapper;

    WeeklyLogMapper(EntryMapper entryMapper) {
        this.entryMapper = entryMapper;
    }

    /**
     * @param userEras <em>all</em> of the user's Eras, in any order: this picks the ones that hold the week and puts
     *     them in feed order ({@link WeekEras#of}, D-22)
     * @param weekEntries this week's entries only, already in feed order
     * @param now the moment that decides which entries are still sealed
     */
    WeeklyLogResponse toResponse(WeeklyLog week, List<Era> userEras, List<Entry> weekEntries, Instant now) {
        return new WeeklyLogResponse(
                week.getId(),
                week.getTitle(),
                week.getStartDate(),
                week.getEndDate(),
                week.isClosed(),
                week.getSoundtrack(),
                WeekEras.of(week, userEras).stream().map(WeeklyLogMapper::toEra).toList(),
                weekEntries.stream()
                        .map(entry -> entryMapper.toResponse(entry, now))
                        .toList());
    }

    private static WeekEraResponse toEra(Era era) {
        return new WeekEraResponse(
                era.getId(), era.getTitle(), era.getStartDate(), era.getEndDate(), era.isPinnedInFeed());
    }
}
