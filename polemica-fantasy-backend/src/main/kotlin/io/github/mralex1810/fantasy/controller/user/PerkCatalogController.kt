package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.response.PerkCatalogItemDto
import io.github.mralex1810.fantasy.service.PerkCatalogService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/perks")
class PerkCatalogController(
    private val perkCatalogService: PerkCatalogService,
) {

    @GetMapping
    fun listPerks(): List<PerkCatalogItemDto> = perkCatalogService.listCatalog()
}
