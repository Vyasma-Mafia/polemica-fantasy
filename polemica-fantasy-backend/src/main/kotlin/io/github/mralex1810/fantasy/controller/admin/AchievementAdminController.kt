package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.request.UpdateAchievementRequest
import io.github.mralex1810.fantasy.dto.admin.response.AchievementAdminDto
import io.github.mralex1810.fantasy.service.AchievementAdminService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/achievements")
class AchievementAdminController(
    private val achievementAdminService: AchievementAdminService,
) {

    @GetMapping
    fun listAchievements(): List<AchievementAdminDto> = achievementAdminService.listAchievements()

    @PutMapping("/{id}")
    fun updateAchievement(
        @PathVariable id: String,
        @Valid @RequestBody body: UpdateAchievementRequest,
    ): AchievementAdminDto = achievementAdminService.updateAchievement(id, body)
}
