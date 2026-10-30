package com.memoirist.era;

import com.memoirist.era.dto.EraDetailResponse;
import com.memoirist.era.dto.EraResponse;
import com.memoirist.era.dto.EraWeekResponse;
import com.memoirist.week.WeeklyLog;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Era to DTO, written by hand (no MapStruct, backend rules). Call it inside the service's transaction. */
final class EraMapper {

    private EraMapper() {}

    static EraResponse toResponse(Era era) {
        return new EraResponse(
                era.getId(),
                era.getTitle(),
                era.getDescription(),
                era.getStartDate(),
                era.getEndDate(),
                era.isPinnedInFeed(),
                era.getCreatedAt(),
                era.getUpdatedAt());
    }

    /**
     * The Era and the weeks it holds, newest first as they come. {@code entryCounts} maps a week's id to how many of
     * its entries count; a week with none isn't in the map and shows {@code 0}.
     */
    static EraDetailResponse toDetail(Era era, List<WeeklyLog> weeks, Map<UUID, Long> entryCounts) {
        List<EraWeekResponse> weekResponses = weeks.stream()
                .map(week -> new EraWeekResponse(
                        week.getId(),
                        week.getTitle(),
                        week.getStartDate(),
                        week.getEndDate(),
                        week.isClosed(),
                        entryCounts.getOrDefault(week.getId(), 0L)))
                .toList();
        return new EraDetailResponse(
                era.getId(),
                era.getTitle(),
                era.getDescription(),
                era.getStartDate(),
                era.getEndDate(),
                era.isPinnedInFeed(),
                era.getCreatedAt(),
                era.getUpdatedAt(),
                weekResponses);
    }
}
