package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.response.AchievementStatisticsReportDto
import io.github.mralex1810.fantasy.service.AchievementStatisticsService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/achievement-statistics")
class AchievementStatisticsAdminController(
    private val achievementStatisticsService: AchievementStatisticsService,
) {

    /** Long-running: loads profile pages and each unique match from Polemica API, then aggregates detectors. */
    @PostMapping("/collect")
    fun collect(): AchievementStatisticsReportDto = achievementStatisticsService.collectReport()
}
