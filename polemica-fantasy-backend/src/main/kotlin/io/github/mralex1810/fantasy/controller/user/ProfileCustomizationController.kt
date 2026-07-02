package io.github.mralex1810.fantasy.controller.user

import com.fasterxml.jackson.databind.JsonNode
import io.github.mralex1810.fantasy.dto.user.request.UpdateProfileCustomizationRequest
import io.github.mralex1810.fantasy.dto.user.response.ProfileCustomizationDto
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.service.achievement.ProfileCustomizationService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/me/profile-customization")
class ProfileCustomizationController(
    private val profileCustomizationService: ProfileCustomizationService,
) {
    @GetMapping
    fun get(@AuthenticationPrincipal user: TelegramUser): ProfileCustomizationDto =
        profileCustomizationService.getCustomization(user)

    @PutMapping
    fun update(
        @AuthenticationPrincipal user: TelegramUser,
        @RequestBody request: JsonNode,
    ): ProfileCustomizationDto = profileCustomizationService.updateCustomization(user, parseRequest(request))

    private fun parseRequest(node: JsonNode): UpdateProfileCustomizationRequest =
        UpdateProfileCustomizationRequest(
            profileFrameCode = nullableText(node, "profileFrameCode"),
            featuredAchievementCodes = stringList(node, "featuredAchievementCodes"),
            favoriteBadgeFantasyPlayerId = nullableLong(node, "favoriteBadgeFantasyPlayerId"),
            profileTitleCode = nullableText(node, "profileTitleCode"),
            profileTitleCodeSet = node.has("profileTitleCode"),
            profileAccentCode = nullableText(node, "profileAccentCode"),
            profileAccentCodeSet = node.has("profileAccentCode"),
            profileBackgroundCode = nullableText(node, "profileBackgroundCode"),
            profileBackgroundCodeSet = node.has("profileBackgroundCode"),
        )

    private fun nullableText(node: JsonNode, field: String): String? {
        val value = node.get(field) ?: return null
        if (value.isNull) return null
        if (!value.isTextual) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$field must be a string or null")
        }
        return value.asText()
    }

    private fun nullableLong(node: JsonNode, field: String): Long? {
        val value = node.get(field) ?: return null
        if (value.isNull) return null
        if (!value.canConvertToLong()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$field must be a number or null")
        }
        return value.asLong()
    }

    private fun stringList(node: JsonNode, field: String): List<String> {
        val value = node.get(field) ?: return emptyList()
        if (!value.isArray) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$field must be an array")
        }
        return value.map { item ->
            if (!item.isTextual) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$field must contain only strings")
            }
            item.asText()
        }
    }
}
