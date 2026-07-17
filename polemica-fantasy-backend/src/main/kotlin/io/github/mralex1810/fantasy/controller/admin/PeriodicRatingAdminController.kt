package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.periodicrating.CreatePeriodicRatingPeriodRequest
import io.github.mralex1810.fantasy.dto.periodicrating.FinalizePeriodicRatingRequest
import io.github.mralex1810.fantasy.dto.periodicrating.PeriodicRatingRewardVersionRequest
import io.github.mralex1810.fantasy.dto.periodicrating.RequestPeriodicRatingRewardChangesRequest
import io.github.mralex1810.fantasy.dto.periodicrating.UpdatePeriodicRatingSeriesRequest
import io.github.mralex1810.fantasy.service.PeriodicRatingService
import io.github.mralex1810.fantasy.service.PeriodicRatingRewardService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.security.core.Authentication

@RestController
@RequestMapping("/api/v1/admin/periodic-ratings")
class PeriodicRatingAdminController(
    private val service: PeriodicRatingService,
    private val rewards: PeriodicRatingRewardService,
) {
    @GetMapping("/periods") fun periods() = service.listPeriods()
    @PostMapping("/periods") fun create(@RequestBody request: CreatePeriodicRatingPeriodRequest) = service.createPeriod(request)
    @PostMapping("/periods/{id}/open") fun open(@PathVariable id: Long) = service.openPeriod(id)
    @PutMapping("/periods/{id}/series/{seriesId}")
    fun series(
        @PathVariable id: Long,
        @PathVariable seriesId: Long,
        @RequestBody request: UpdatePeriodicRatingSeriesRequest,
    ) = service.updateSeries(id, seriesId, request)
    @PostMapping("/periods/{id}/preview") fun preview(@PathVariable id: Long) = service.preview(id)
    @PostMapping("/periods/{id}/finalize")
    fun finalize(
        @PathVariable id: Long,
        @RequestBody request: FinalizePeriodicRatingRequest,
        authentication: Authentication,
    ) = service.finalize(id, request, authentication.name)

    @GetMapping("/rewards")
    fun rewards(
        @RequestParam(required = false) periodId: Long?,
        @RequestParam(required = false) status: String?,
    ) = rewards.listForAdmin(periodId, status)

    @PostMapping("/rewards/{id}/request-changes")
    fun requestChanges(
        @PathVariable id: Long,
        @RequestBody request: RequestPeriodicRatingRewardChangesRequest,
        authentication: Authentication,
    ) = rewards.requestChanges(id, request.reason, request.version, authentication.name)

    @PostMapping("/rewards/{id}/approve-and-issue")
    fun approveAndIssue(
        @PathVariable id: Long,
        @RequestBody request: PeriodicRatingRewardVersionRequest,
        authentication: Authentication,
    ) = rewards.approveAndIssue(id, request.version, authentication.name)
}
