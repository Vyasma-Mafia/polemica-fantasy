package io.github.mralex1810.fantasy.service.achievement

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.mralex1810.fantasy.dto.user.response.AchievementCardChoiceOptionDto
import io.github.mralex1810.fantasy.entity.*
import io.github.mralex1810.fantasy.repository.AchievementDefinitionRepository
import io.github.mralex1810.fantasy.repository.UserAchievementRepository
import io.github.mralex1810.fantasy.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.web.server.ResponseStatusException
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

class AchievementClaimStateTest {
    private val definitions = mock<AchievementDefinitionRepository>()
    private val achievements = mock<UserAchievementRepository>()
    private val progress = mock<AchievementProgressService>()
    private val cards = mock<AchievementCardRewardService>()
    private val users = mock<UserService>()
    private val jdbc = mock<JdbcTemplate>()
    private val events = mock<ApplicationEventPublisher>()
    private val service = AchievementClaimService(definitions, achievements, progress, cards, users, jdbc, jacksonObjectMapper(), events)
    private val user = TelegramUser(id = 17L, telegramId = 12345L)
    private val definition = AchievementDefinition(id = 9L, code = "choice")
    private val now = Instant.parse("2026-09-06T00:00:00Z")

    @Test
    fun `absent progress stays absent without eligibility evaluation or grants`() {
        whenever(definitions.findByCodeWithRewards("choice")).thenReturn(definition)
        val state = service.claimState(user, "choice")
        assertThat(state.completedAt).isNull()
        assertThat(state.claimedAt).isNull()
        assertThat(state.pendingChoices).isEmpty()
        verify(achievements).findByTelegramUser_IdAndAchievement_Id(17L, 9L)
        verifyNoMoreInteractions(achievements)
        verifyNoInteractions(progress, cards, users, jdbc, events)
    }

    @Test
    fun `pending and selected receipts coexist before overall finalization and use authenticated internal id`() {
        definition.rewards.add(AchievementReward(id = 1L, rewardType = "CARD_CHOICE_ROLL"))
        definition.rewards.add(AchievementReward(id = 2L, rewardType = "CARD_CHOICE_ROLL"))
        whenever(definitions.findByCodeWithRewards("choice")).thenReturn(definition)
        whenever(achievements.findByTelegramUser_IdAndAchievement_Id(17L, 9L))
            .thenReturn(UserAchievement(completedAt = now))
        whenever(cards.optionDto(any())).thenReturn(AchievementCardChoiceOptionDto("pick", 42L, "Player", null, "COMMON", null, emptyList()))
        whenever(jdbc.query(any<String>(), any<RowMapper<Any>>(), eq(17L), eq(9L), any<Long>())).thenAnswer { call ->
            val rewardId = call.getArgument<Long>(4)
            val rs = mock<ResultSet>()
            whenever(rs.getInt("required_count")).thenReturn(1)
            whenever(rs.getString("options")).thenReturn("""[{"optionId":"pick","fantasyPlayerId":42,"playerName":"Player","playerPhotoUrl":null,"rarity":"COMMON","skinCode":null,"perkIds":[]}]""")
            if (rewardId == 1L) {
                whenever(rs.getTimestamp("claimed_at")).thenReturn(Timestamp.from(now))
                whenever(rs.getString("selected_option_ids")).thenReturn("[\"pick\"]")
                whenever(rs.getString("selected_user_card_ids")).thenReturn("[987]")
            }
            listOf(call.getArgument<RowMapper<Any>>(1).mapRow(rs, 0))
        }
        val state = service.claimState(user, "choice")
        assertThat(state.claimedAt).isNull()
        assertThat(state.pendingChoices.map { it.rewardId }).containsExactly(2L)
        assertThat(state.pendingChoices.single().options.single().optionId).isEqualTo("pick")
        assertThat(state.selectedChoices.single().selectedOptionIds).containsExactly("pick")
        assertThat(state.selectedChoices.single().selectedUserCardIds).containsExactly(987L)
        assertThat(state.selectedChoices.single().claimedAt).isEqualTo(now)
        verify(achievements).findByTelegramUser_IdAndAchievement_Id(17L, 9L)
        verifyNoMoreInteractions(achievements)
        verifyNoInteractions(progress, users, events)
        verify(jdbc, times(2)).query(any<String>(), any<RowMapper<Any>>(), eq(17L), eq(9L), any<Long>())
        verifyNoMoreInteractions(jdbc)
        verify(cards).optionDto(any())
        verifyNoMoreInteractions(cards)
    }

    @Test
    fun `hidden disabled and missing definitions return not found without reading user state`() {
        listOf(null, AchievementDefinition(visibility = "HIDDEN"), AchievementDefinition(enabled = false)).forEach { value ->
            whenever(definitions.findByCodeWithRewards("choice")).thenReturn(value)
            assertThat(assertThrows<ResponseStatusException> { service.claimState(user, "choice") }.statusCode.value()).isEqualTo(404)
        }
        verifyNoInteractions(achievements, jdbc, progress, cards, users, events)
    }
}
