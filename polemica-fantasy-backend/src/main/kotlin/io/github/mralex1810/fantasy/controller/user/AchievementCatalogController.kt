package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.response.AchievementCatalogItemDto
import io.github.mralex1810.fantasy.service.AchievementCatalogService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/achievements")
class AchievementCatalogController(
    private val achievementCatalogService: AchievementCatalogService,
) {

    @GetMapping
    fun listAchievements(): List<AchievementCatalogItemDto> = achievementCatalogService.listCatalog()
}
