package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.request.PerkStatisticsRequest
import io.github.mralex1810.fantasy.dto.admin.response.PerkStatisticsReportDto
import io.github.mralex1810.fantasy.service.PerkStatisticsService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/perk-statistics")
class PerkStatisticsAdminController(
    private val perkStatisticsService: PerkStatisticsService,
) {

    /** Long-running: loads profile pages and each unique match from Polemica API, then aggregates detectors. */
    @PostMapping("/collect")
    fun collect(
        @RequestBody(required = false) request: PerkStatisticsRequest?,
    ): PerkStatisticsReportDto = perkStatisticsService.collectReport(request)
}
