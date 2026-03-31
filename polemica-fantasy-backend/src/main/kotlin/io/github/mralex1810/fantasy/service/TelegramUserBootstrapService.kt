package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.FantikiTransaction
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.FantikiTransactionRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Inserts a new [TelegramUser] + INITIAL fantiki in an independent transaction so a concurrent
 * duplicate [telegram_id] only rolls back this attempt; the caller can then load the existing row.
 */
@Service
class TelegramUserBootstrapService(
    private val telegramUserRepository: TelegramUserRepository,
    private val fantikiTransactionRepository: FantikiTransactionRepository,
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun insertNewUserWithInitialFantiki(telegramId: Long, username: String?, firstName: String?): TelegramUser {
        val saved = telegramUserRepository.save(
            TelegramUser(
                telegramId = telegramId,
                username = username,
                firstName = firstName,
            ),
        )
        fantikiTransactionRepository.save(
            FantikiTransaction(
                telegramUser = saved,
                amount = 1000L,
                reason = FantikiTransactionReason.INITIAL,
            ),
        )
        return saved
    }
}
