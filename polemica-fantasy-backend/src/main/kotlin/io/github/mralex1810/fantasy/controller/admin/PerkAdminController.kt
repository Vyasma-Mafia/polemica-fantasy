package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.request.UpdatePerkRequest
import io.github.mralex1810.fantasy.dto.admin.response.PerkAdminDto
import io.github.mralex1810.fantasy.service.PerkAdminService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/perks")
class PerkAdminController(
    private val perkAdminService: PerkAdminService,
) {

    @GetMapping
    fun listPerks(): List<PerkAdminDto> = perkAdminService.listPerks()

    @PutMapping("/{id}")
    fun updatePerk(
        @PathVariable id: String,
        @Valid @RequestBody body: UpdatePerkRequest,
    ): PerkAdminDto = perkAdminService.updatePerk(id, body)
}
