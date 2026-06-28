package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.response.FantikiTransactionDto
import io.github.mralex1810.fantasy.dto.admin.response.PagedFantikiTransactionsDto
import io.github.mralex1810.fantasy.dto.user.response.UserProfileDto
import io.github.mralex1810.fantasy.entity.FantikiTransaction
import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.FantikiTransactionRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import org.springframework.data.domain.Pageable
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class UserService(
    private val telegramUserRepository: TelegramUserRepository,
    private val fantikiTransactionRepository: FantikiTransactionRepository,
    private val telegramUserBootstrapService: TelegramUserBootstrapService,
) {

    @Transactional
    fun getOrCreateAndUpdateProfile(telegramId: Long, username: String?, firstName: String?): TelegramUser {
        val existing = telegramUserRepository.findByTelegramId(telegramId)
        if (existing != null) {
            if (existing.botBlocked) {
                existing.botBlocked = false
            }
            applyTelegramProfileFields(existing, username, firstName)
            return telegramUserRepository.save(existing)
        }
        return try {
            telegramUserBootstrapService.insertNewUserWithInitialFantiki(telegramId, username, firstName)
        } catch (e: DataIntegrityViolationException) {
            val afterRace = telegramUserRepository.findByTelegramId(telegramId)
                ?: throw e
            applyTelegramProfileFields(afterRace, username, firstName)
            telegramUserRepository.save(afterRace)
        }
    }

    @Transactional
    fun updateDisplayName(internalUserId: Long, displayName: String?): UserProfileDto {
        val user = telegramUserRepository.findById(internalUserId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User $internalUserId not found")
        }
        val normalized = displayName?.trim()?.takeIf { it.isNotEmpty() }
        if (normalized != null && normalized.length > MAX_DISPLAY_NAME_LENGTH) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "displayName must be at most $MAX_DISPLAY_NAME_LENGTH characters",
            )
        }
        user.displayName = normalized
        val saved = telegramUserRepository.save(user)
        return toProfileDto(saved)
    }

    private fun applyTelegramProfileFields(user: TelegramUser, username: String?, firstName: String?) {
        username?.let { user.username = it }
        firstName?.let { user.firstName = it }
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

    @Transactional
    fun forceDeductBalance(internalUserId: Long, amount: Long, reason: FantikiTransactionReason) {
        require(amount >= 0) { "amount must be non-negative" }
        if (amount == 0L) return
        val updated = telegramUserRepository.forceDeductFantiki(internalUserId, amount)
        if (updated == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User $internalUserId not found")
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
            displayName = user.displayName,
            createdAt = user.createdAt,
            fantiki = user.fantiki,
            packOpensCount = user.packOpensCount,
        )

    @Transactional
    fun grantFantikiByTelegramId(telegramUserId: Long, amount: Long, adminReason: String): UserProfileDto {
        val user = getOrCreateAndUpdateProfile(telegramUserId, null, null)
        addBalanceWithAdminReason(
            user.id!!,
            amount,
            FantikiTransactionReason.ADMIN_GRANT,
            normalizeAdminReason(adminReason),
        )
        val fresh = telegramUserRepository.findById(user.id!!).get()
        return toProfileDto(fresh)
    }

    @Transactional
    fun confiscateFantikiByTelegramId(telegramUserId: Long, amount: Long, adminReason: String): UserProfileDto {
        val user = telegramUserRepository.findByTelegramId(telegramUserId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "User with telegram id $telegramUserId not found",
            )
        deductBalanceWithAdminReason(
            user.id!!,
            amount,
            FantikiTransactionReason.ADMIN_CONFISCATE,
            normalizeAdminReason(adminReason),
        )
        val fresh = telegramUserRepository.findById(user.id!!).get()
        return toProfileDto(fresh)
    }

    @Transactional(readOnly = true)
    fun listFantikiTransactionsForAdmin(
        telegramUserId: Long?,
        pageable: Pageable,
    ): PagedFantikiTransactionsDto {
        val page = fantikiTransactionRepository.findTransactionsForAdmin(
            telegramUserId = telegramUserId,
            pageable = pageable,
        )
        return PagedFantikiTransactionsDto(
            content = page.content.map { tx ->
                FantikiTransactionDto(
                    id = tx.id!!,
                    createdAt = tx.createdAt,
                    telegramId = tx.telegramUser!!.telegramId,
                    amount = tx.amount,
                    reason = tx.reason.name,
                    adminReason = tx.adminReason,
                )
            },
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
        )
    }

    private fun addBalanceWithAdminReason(
        internalUserId: Long,
        amount: Long,
        reason: FantikiTransactionReason,
        adminReason: String,
    ) {
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
                adminReason = adminReason,
            ),
        )
    }

    private fun deductBalanceWithAdminReason(
        internalUserId: Long,
        amount: Long,
        reason: FantikiTransactionReason,
        adminReason: String,
    ) {
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
                adminReason = adminReason,
            ),
        )
    }

    private fun normalizeAdminReason(adminReason: String): String {
        val normalized = adminReason.trim()
        if (normalized.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "adminReason must not be blank")
        }
        if (normalized.length > MAX_ADMIN_REASON_LENGTH) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "adminReason must be at most $MAX_ADMIN_REASON_LENGTH characters",
            )
        }
        return normalized
    }

    companion object {
        private const val MAX_DISPLAY_NAME_LENGTH = 255
        private const val MAX_ADMIN_REASON_LENGTH = 500
    }
}
