package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.auth.AutomatedAgentTelegramAuthenticationException
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.FantikiTransactionRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException

class UserServiceAgentTmaAuthTest {
    @Test
    fun `TMA path rejects agent before profile or bot blocked mutation`() {
        val repository = mock<TelegramUserRepository>()
        val bootstrap = mock<TelegramUserBootstrapService>()
        val user = TelegramUser(
            id = 10,
            telegramId = 100,
            username = "original",
            firstName = "Original",
            botBlocked = true,
            isAutomatedAgent = true,
        )
        whenever(repository.findByTelegramIdForUpdate(100)).thenReturn(user)
        val service = UserService(
            telegramUserRepository = repository,
            fantikiTransactionRepository = mock<FantikiTransactionRepository>(),
            telegramUserBootstrapService = bootstrap,
            eventPublisher = mock<ApplicationEventPublisher>(),
        )

        assertThatThrownBy {
            service.getOrCreateAndUpdateProfileFromTelegram(100, "changed", "Changed")
        }.isInstanceOf(AutomatedAgentTelegramAuthenticationException::class.java)

        org.assertj.core.api.Assertions.assertThat(user.username).isEqualTo("original")
        org.assertj.core.api.Assertions.assertThat(user.firstName).isEqualTo("Original")
        org.assertj.core.api.Assertions.assertThat(user.botBlocked).isTrue()
        verify(repository).findByTelegramIdForUpdate(100)
        verify(repository, never()).save(user)
        verifyNoInteractions(bootstrap)
    }

    @Test
    fun `TMA duplicate bootstrap race rechecks agent under row lock before mutation`() {
        val repository = mock<TelegramUserRepository>()
        val bootstrap = mock<TelegramUserBootstrapService>()
        val racedAgent = TelegramUser(
            id = 11,
            telegramId = 101,
            username = "original",
            firstName = "Original",
            botBlocked = true,
            isAutomatedAgent = true,
        )
        whenever(repository.findByTelegramIdForUpdate(101)).thenReturn(null, racedAgent)
        whenever(bootstrap.insertNewUserWithInitialFantiki(101, "changed", "Changed"))
            .thenThrow(DataIntegrityViolationException("duplicate bootstrap race"))
        val service = UserService(
            telegramUserRepository = repository,
            fantikiTransactionRepository = mock<FantikiTransactionRepository>(),
            telegramUserBootstrapService = bootstrap,
            eventPublisher = mock<ApplicationEventPublisher>(),
        )

        assertThatThrownBy {
            service.getOrCreateAndUpdateProfileFromTelegram(101, "changed", "Changed")
        }.isInstanceOf(AutomatedAgentTelegramAuthenticationException::class.java)

        org.assertj.core.api.Assertions.assertThat(racedAgent.username).isEqualTo("original")
        org.assertj.core.api.Assertions.assertThat(racedAgent.firstName).isEqualTo("Original")
        org.assertj.core.api.Assertions.assertThat(racedAgent.botBlocked).isTrue()
        verify(repository, times(2)).findByTelegramIdForUpdate(101)
        verify(repository, never()).save(racedAgent)
    }
}
