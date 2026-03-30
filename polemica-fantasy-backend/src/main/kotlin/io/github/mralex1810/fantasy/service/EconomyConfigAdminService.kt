package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.BulkUpdateEconomyConfigRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateEconomyConfigRequest
import io.github.mralex1810.fantasy.dto.admin.response.EconomyConfigItemDto
import io.github.mralex1810.fantasy.repository.EconomyConfigRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class EconomyConfigAdminService(
    private val economyConfigRepository: EconomyConfigRepository,
    private val economyConfigService: EconomyConfigService,
) {

    @Transactional(readOnly = true)
    fun listAll(): List<EconomyConfigItemDto> =
        economyConfigRepository.findAll().map {
            EconomyConfigItemDto(key = it.key, value = it.value, description = it.description)
        }.sortedBy { it.key }

    @Transactional
    fun updateKey(key: String, request: UpdateEconomyConfigRequest): EconomyConfigItemDto {
        validateNumericValue(request.value.trim())
        val row = economyConfigRepository.findById(key).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Economy config key not found: $key")
        }
        row.value = request.value.trim()
        economyConfigRepository.save(row)
        economyConfigService.invalidateCache()
        return EconomyConfigItemDto(key = row.key, value = row.value, description = row.description)
    }

    @Transactional
    fun bulkUpdate(request: BulkUpdateEconomyConfigRequest): List<EconomyConfigItemDto> {
        val out = mutableListOf<EconomyConfigItemDto>()
        for (item in request.items) {
            validateNumericValue(item.value.trim())
            val row = economyConfigRepository.findById(item.key).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Economy config key not found: ${item.key}")
            }
            row.value = item.value.trim()
            economyConfigRepository.save(row)
            out.add(EconomyConfigItemDto(key = row.key, value = row.value, description = row.description))
        }
        economyConfigService.invalidateCache()
        return out.sortedBy { it.key }
    }

    private fun validateNumericValue(value: String) {
        if (value.toLongOrNull() == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Value must be a valid integer number")
        }
    }
}
