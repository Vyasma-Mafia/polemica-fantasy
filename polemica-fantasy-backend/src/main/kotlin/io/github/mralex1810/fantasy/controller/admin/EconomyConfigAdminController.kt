package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.request.BulkUpdateEconomyConfigRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateEconomyConfigRequest
import io.github.mralex1810.fantasy.dto.admin.response.EconomyConfigItemDto
import io.github.mralex1810.fantasy.service.EconomyConfigAdminService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/economy-config")
class EconomyConfigAdminController(
    private val economyConfigAdminService: EconomyConfigAdminService,
) {

    @GetMapping
    fun list(): List<EconomyConfigItemDto> = economyConfigAdminService.listAll()

    @PutMapping("/{key}")
    fun updateKey(
        @PathVariable key: String,
        @Valid @RequestBody body: UpdateEconomyConfigRequest,
    ): EconomyConfigItemDto = economyConfigAdminService.updateKey(key, body)

    @PutMapping
    fun bulkUpdate(@Valid @RequestBody body: BulkUpdateEconomyConfigRequest): List<EconomyConfigItemDto> =
        economyConfigAdminService.bulkUpdate(body)
}
