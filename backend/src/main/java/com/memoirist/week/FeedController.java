package com.memoirist.week;

import com.memoirist.common.PageResponse;
import com.memoirist.week.dto.WeeklyLogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/feed}: the journal, a page of weeks at a time (API_CONTRACT, Feed). HTTP only: the service finds the
 * user, and no user id comes from the request (D-11).
 */
@RestController
@RequestMapping("/api/v1/feed")
@Tag(name = "Feed")
class FeedController {

    private final WeeklyLogService weeklyLogs;

    FeedController(WeeklyLogService weeklyLogs) {
        this.weeklyLogs = weeklyLogs;
    }

    @GetMapping
    @Operation(
            summary = "A page of the feed",
            description = "The user's weeks that have at least one entry, newest first, each with its Eras and its"
                    + " entries. A sealed Loot Drop is a locked row with nothing private in it; entries in Recently"
                    + " deleted are left out, and so is a week whose entries are all deleted. A week in several Eras"
                    + " is listed once. Media URLs are presigned for this response.")
    @ApiResponse(responseCode = "200", description = "The page; items is empty when there is nothing on it")
    @ApiResponse(
            responseCode = "400",
            description = "validation-failed with fieldErrors (page below 0, size below 1 or above 50), or"
                    + " malformed-request (page or size is not a whole number, a bad X-Dev-User-Id)")
    PageResponse<WeeklyLogResponse> feed(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return weeklyLogs.feed(page, size);
    }
}
