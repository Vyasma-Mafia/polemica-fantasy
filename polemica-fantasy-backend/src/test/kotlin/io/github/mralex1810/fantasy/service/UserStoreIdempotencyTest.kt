package io.github.mralex1810.fantasy.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.mralex1810.fantasy.config.EasterEggProperties
import io.github.mralex1810.fantasy.dto.user.response.BuyPackResponseDto
import io.github.mralex1810.fantasy.dto.user.response.BuyPackResponseKind
import io.github.mralex1810.fantasy.entity.PackPurchaseIdempotency
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.CardPackRepository
import io.github.mralex1810.fantasy.repository.CardTemplateRepository
import io.github.mralex1810.fantasy.repository.PackPurchaseIdempotencyRepository
import io.github.mralex1810.fantasy.repository.PerkRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.UserCardPackChoiceRepository
import io.github.mralex1810.fantasy.repository.UserCardPackFreeUsageRepository
import io.github.mralex1810.fantasy.repository.UserCardPackOpenCountRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class UserStoreIdempotencyTest {
    private val objectMapper = jacksonObjectMapper()
    private val cardPackRepository = mock<CardPackRepository>()
    private val idempotencyRepository = mock<PackPurchaseIdempotencyRepository>()
    private val telegramUserRepository = mock<TelegramUserRepository>()
    private val service = UserStoreService(
        objectMapper = objectMapper,
        cardPackRepository = cardPackRepository,
        cardService = mock<CardService>(),
        cardPackService = mock<CardPackService>(),
        userService = mock<UserService>(),
        userCardRepository = mock<UserCardRepository>(),
        cardTemplateRepository = mock<CardTemplateRepository>(),
        perkRepository = mock<PerkRepository>(),
        userCardPackChoiceRepository = mock<UserCardPackChoiceRepository>(),
        userCardPackFreeUsageRepository = mock<UserCardPackFreeUsageRepository>(),
        userCardPackOpenCountRepository = mock<UserCardPackOpenCountRepository>(),
        telegramUserRepository = telegramUserRepository,
        imageStorageService = mock<ImageStorageService>(),
        cardValueService = mock<CardValueService>(),
        economyConfigService = mock<EconomyConfigService>(),
        easterEggProperties = mock<EasterEggProperties>(),
        onboardingService = mock<OnboardingService>(),
        packPurchaseIdempotencyRepository = idempotencyRepository,
    )

    @Test
    fun `completed purchase is replayed without touching pack services`() {
        val user = TelegramUser(id = 1, telegramId = 1001)
        val response = BuyPackResponseDto(kind = BuyPackResponseKind.PENDING_CHOICE, fantiki = 900)
        whenever(telegramUserRepository.findByIdForUpdate(1)).thenReturn(user)
        whenever(idempotencyRepository.findByTelegramUser_IdAndKeyHash(eq(1), any())).thenReturn(
            PackPurchaseIdempotency(
                canonicalRequestHash = canonicalHash(42),
                responseJson = objectMapper.writeValueAsString(response),
            ),
        )

        val replay = service.buyPack(user, 42, "operation-1")

        assertThat(replay).isEqualTo(response)
        verifyNoInteractions(cardPackRepository)
    }

    @Test
    fun `same key with another pack conflicts before purchase`() {
        val user = TelegramUser(id = 2, telegramId = 1002)
        whenever(telegramUserRepository.findByIdForUpdate(2)).thenReturn(user)
        whenever(idempotencyRepository.findByTelegramUser_IdAndKeyHash(eq(2), any())).thenReturn(
            PackPurchaseIdempotency(canonicalRequestHash = canonicalHash(41), responseJson = "{}"),
        )

        assertThatThrownBy { service.buyPack(user, 42, "operation-2") }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode).isEqualTo(HttpStatus.CONFLICT)
            }
        verifyNoInteractions(cardPackRepository)
    }

    private fun canonicalHash(packId: Long): String = MessageDigest.getInstance("SHA-256")
        .digest("PACK_PURCHASE\n$packId".toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
