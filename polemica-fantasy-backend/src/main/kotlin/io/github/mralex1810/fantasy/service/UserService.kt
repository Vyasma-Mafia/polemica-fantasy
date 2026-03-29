package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.response.UserProfileDto
import io.github.mralex1810.fantasy.entity.FantikiTransaction
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.FantikiTransactionRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class UserService(
    private val telegramUserRepository: TelegramUserRepository,
    private val fantikiTransactionRepository: FantikiTransactionRepository,
) {

    @Transactional
    fun getOrCreateAndUpdateProfile(telegramId: Long, username: String?, firstName: String?): TelegramUser {
        val existing = telegramUserRepository.findByTelegramId(telegramId)
        return if (existing != null) {
            username?.let { existing.username = it }
            firstName?.let { existing.firstName = it }
            telegramUserRepository.save(existing)
        } else {
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
            saved
        }
    }

    fun getBalance(internalUserId: Long): Long =
        telegramUserRepository.findById(internalUserId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User $internalUserId not found")
        }.fantiki

    @Transactional
    fun addBalance(internalUserId: Long, amount: Long, reason: FantikiTransactionReason) {
        require(amount > 0) { "amount must be positive" }
        val updated = telegramUserRepository.addFantiki(internalUserId, amount)
        if (updated == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User $internalUserId not found")
        }
        val user = telegramUserRepository.getReferenceById(internalUserId)
        fantikiTransactionRepository.save(
            FantikiTransaction(
                telegramUser = user,
                amount = amount,
                reason = reason,
            ),
        )
    }

    @Transactional
    fun deductBalance(internalUserId: Long, amount: Long, reason: FantikiTransactionReason) {
        require(amount >= 0) { "amount must be non-negative" }
        if (amount == 0L) return
        val updated = telegramUserRepository.deductFantikiIfSufficient(internalUserId, amount)
        if (updated == 0) {
            val balance = telegramUserRepository.findById(internalUserId).orElse(null)?.fantiki
            if (balance == null) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "User $internalUserId not found")
            }
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient fantiki balance")
        }
        val user = telegramUserRepository.getReferenceById(internalUserId)
        fantikiTransactionRepository.save(
            FantikiTransaction(
                telegramUser = user,
                amount = -amount,
                reason = reason,
            ),
        )
    }

    fun toProfileDto(user: TelegramUser): UserProfileDto =
        UserProfileDto(
            id = user.id!!,
            telegramId = user.telegramId,
            username = user.username,
            firstName = user.firstName,
            createdAt = user.createdAt,
            fantiki = user.fantiki,
        )

    @Transactional
    fun grantFantikiByTelegramId(telegramUserId: Long, amount: Long): UserProfileDto {
        val user = getOrCreateAndUpdateProfile(telegramUserId, null, null)
        addBalance(user.id!!, amount, FantikiTransactionReason.ADMIN_GRANT)
        val fresh = telegramUserRepository.findById(user.id!!).get()
        return toProfileDto(fresh)
    }
}
