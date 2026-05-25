package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.request.UpdateAchievementAdminRequest
import io.github.mralex1810.fantasy.dto.admin.response.AchievementAdminDefinitionDto
import io.github.mralex1810.fantasy.dto.admin.response.AchievementAdminListResponseDto
import io.github.mralex1810.fantasy.dto.admin.response.AchievementDryRunResponseDto
import io.github.mralex1810.fantasy.service.achievement.AchievementAdminService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/achievements")
class AchievementAdminController(
    private val achievementAdminService: AchievementAdminService,
) {
    @GetMapping
    fun list(): AchievementAdminListResponseDto = achievementAdminService.listAchievements()

    @PatchMapping("/{code}")
    fun update(
        @PathVariable code: String,
        @RequestBody request: UpdateAchievementAdminRequest,
    ): AchievementAdminDefinitionDto =
        achievementAdminService.updateAchievement(code, request)

    @PostMapping("/backfill/dry-run")
    fun dryRun(): AchievementDryRunResponseDto = achievementAdminService.dryRun()
}
