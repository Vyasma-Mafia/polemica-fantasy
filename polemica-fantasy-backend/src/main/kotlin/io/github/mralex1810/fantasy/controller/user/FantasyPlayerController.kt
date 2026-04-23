package io.github.mralex1810.fantasy.controller.user

import io.github.mralex1810.fantasy.dto.user.response.FantasyPlayerBriefDto
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/fantasy-players")
class FantasyPlayerController(
    private val fantasyPlayerRepository: FantasyPlayerRepository,
) {

    @GetMapping
    fun list(): List<FantasyPlayerBriefDto> =
        fantasyPlayerRepository.findAll(Sort.by(Sort.Direction.ASC, "nickname")).map { fp ->
            FantasyPlayerBriefDto(
                id = fp.id!!,
                nickname = fp.nickname,
                photoUrl = fp.photoUrl,
            )
        }
}
