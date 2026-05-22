package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.response.AdminMeDto
import io.github.mralex1810.fantasy.dto.admin.response.AdminRole
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
class MeAdminController {

    @GetMapping("/me")
    fun me(authentication: Authentication): AdminMeDto {
        val authorities = authentication.authorities.map { it.authority }.toSet()
        val role = when {
            "ROLE_ADMIN" in authorities -> AdminRole.ADMIN
            "ROLE_MODERATOR" in authorities -> AdminRole.MODERATOR
            else -> error("Unknown admin role")
        }
        return AdminMeDto(username = authentication.name, role = role)
    }
}
