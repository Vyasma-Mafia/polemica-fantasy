package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.response.PolemicaCompetitionDetailDto
import io.github.mralex1810.fantasy.dto.admin.response.PolemicaCompetitionSummaryDto
import io.github.mralex1810.fantasy.polemica.PolemicaIntegrationService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/polemica")
class PolemicaAdminController(
    private val polemicaIntegrationService: PolemicaIntegrationService,
) {

    @GetMapping("/competitions")
    fun listCompetitions(): List<PolemicaCompetitionSummaryDto> =
        polemicaIntegrationService.listCompetitionsSummary()

    @GetMapping("/competitions/{id}")
    fun getCompetition(@PathVariable id: Long): PolemicaCompetitionDetailDto =
        polemicaIntegrationService.getCompetitionDetail(id)
}
