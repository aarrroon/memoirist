package com.memoirist.week;

import com.memoirist.week.dto.WeeklyLogResponse;
import com.memoirist.week.dto.WeeklyLogUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/weekly-logs}: one week of the journal (API_CONTRACT, Weekly logs). HTTP only: the service finds the
 * user and checks that the week is theirs, and no user id comes from the request (D-11). There is no create or delete:
 * weeks come into being with their first entry (D-08).
 */
@RestController
@RequestMapping("/api/v1/weekly-logs")
@Tag(name = "Weekly Logs")
class WeeklyLogController {

    private final WeeklyLogService weeklyLogs;

    WeeklyLogController(WeeklyLogService weeklyLogs) {
        this.weeklyLogs = weeklyLogs;
    }

    @GetMapping("/{logId}")
    @Operation(
            summary = "One week",
            description = "With its Eras and its entries, oldest first. A sealed Loot Drop is a locked row with nothing"
                    + " private in it; entries in Recently deleted are left out. A week with no entries left is still"
                    + " found.")
    @ApiResponse(responseCode = "200", description = "The week")
    @ApiResponse(responseCode = "400", description = "malformed-request: the id is not a UUID")
    @ApiResponse(responseCode = "404", description = "not-found: no such week, or it is another user's")
    WeeklyLogResponse get(@PathVariable UUID logId) {
        return weeklyLogs.get(logId);
    }

    @PutMapping("/{logId}")
    @Operation(
            summary = "Replace a week's title",
            description = "A full replace: a title that is left out, null or only spaces clears the title. A week's"
                    + " Eras follow from their dates and can't be set here.")
    @ApiResponse(responseCode = "200", description = "The week as saved")
    @ApiResponse(
            responseCode = "400",
            description = "validation-failed with fieldErrors.title (over 200 characters), or malformed-request"
                    + " (unreadable JSON, a bad id)")
    @ApiResponse(responseCode = "404", description = "not-found: no such week, or it is another user's")
    @ApiResponse(responseCode = "415", description = "unsupported-media-type: the body is not application/json")
    WeeklyLogResponse update(@PathVariable UUID logId, @Valid @RequestBody WeeklyLogUpdateRequest request) {
        return weeklyLogs.update(logId, request);
    }

    @PostMapping("/{logId}/close")
    @Operation(
            summary = "Close a week",
            description = "Closing a closed week is not an error. A closed week stays editable and can be reopened.")
    @ApiResponse(responseCode = "200", description = "The week, with isClosed true")
    @ApiResponse(responseCode = "400", description = "malformed-request: the id is not a UUID")
    @ApiResponse(responseCode = "404", description = "not-found: no such week, or it is another user's")
    WeeklyLogResponse close(@PathVariable UUID logId) {
        return weeklyLogs.close(logId);
    }

    @PostMapping("/{logId}/reopen")
    @Operation(summary = "Reopen a week", description = "Reopening an open week is not an error.")
    @ApiResponse(responseCode = "200", description = "The week, with isClosed false")
    @ApiResponse(responseCode = "400", description = "malformed-request: the id is not a UUID")
    @ApiResponse(responseCode = "404", description = "not-found: no such week, or it is another user's")
    WeeklyLogResponse reopen(@PathVariable UUID logId) {
        return weeklyLogs.reopen(logId);
    }
}
