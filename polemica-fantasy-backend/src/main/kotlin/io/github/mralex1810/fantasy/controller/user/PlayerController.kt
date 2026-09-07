package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.service.UserPlayerService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/players")
class PlayerController(private val userPlayerService: UserPlayerService) {
    @GetMapping("/{fantasyPlayerId}")
    fun get(@PathVariable fantasyPlayerId: Long) = userPlayerService.getPlayer(fantasyPlayerId)
}
