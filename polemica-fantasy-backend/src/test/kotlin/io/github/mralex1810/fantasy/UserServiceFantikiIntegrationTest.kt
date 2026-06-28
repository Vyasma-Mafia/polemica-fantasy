package io.github.mralex1810.fantasy

import io.github.mralex1810.fantasy.entity.FantikiTransactionReason
import io.github.mralex1810.fantasy.entity.TelegramUser
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import io.github.mralex1810.fantasy.repository.FantikiTransactionRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.service.UserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class UserServiceFantikiIntegrationTest {

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var telegramUserRepository: TelegramUserRepository

    @Autowired
    private lateinit var fantikiTransactionRepository: FantikiTransactionRepository

    @Test
    @Transactional
    fun `deductBalance throws when insufficient`() {
        val u = telegramUserRepository.save(TelegramUser(telegramId = 993_001L))
        val ex = assertThrows<ResponseStatusException> {
            userService.deductBalance(u.id!!, 9_000L, FantikiTransactionReason.PACK_PURCHASE)
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `concurrent getOrCreateAndUpdateProfile yields single user and single INITIAL tx`() {
        val telegramId = 994_000_001L
        // REQUIRES_NEW briefly needs an extra pool connection per caller; keep modest to avoid Hikari wait.
        val n = 6
        val executor = Executors.newFixedThreadPool(n)
        try {
            val futures = (1..n).map {
                executor.submit<TelegramUser> {
                    userService.getOrCreateAndUpdateProfile(telegramId, "u$it", "FN$it")
                }
            }
            futures.forEach { it.get(60, TimeUnit.SECONDS) }
        } finally {
            executor.shutdown()
        }
        val users = telegramUserRepository.findAll().filter { it.telegramId == telegramId }
        org.junit.jupiter.api.Assertions.assertEquals(1, users.size)
        val uid = users.single().id!!
        val initialCount = fantikiTransactionRepository.findAll().count {
            it.telegramUser!!.id == uid && it.reason == FantikiTransactionReason.INITIAL
        }
        org.junit.jupiter.api.Assertions.assertEquals(1, initialCount)
    }

    @Test
    @Transactional
    fun `addBalance writes positive transaction`() {
        val u = telegramUserRepository.save(TelegramUser(telegramId = 993_002L))
        userService.addBalance(u.id!!, 42L, FantikiTransactionReason.ADMIN_GRANT)
        val txs = fantikiTransactionRepository.findAll().filter { it.telegramUser!!.id == u.id }
        assertEquals(1, txs.size)
        assertEquals(42L, txs.single().amount)
        assertEquals(null, txs.single().adminReason)
    }

    @Test
    fun `grantFantikiByTelegramId writes trimmed admin reason`() {
        userService.grantFantikiByTelegramId(993_003L, 42L, "  Manual correction  ")

        val user = telegramUserRepository.findByTelegramId(993_003L)!!
        val txs = fantikiTransactionRepository.findAll()
            .filter { it.telegramUser!!.id == user.id && it.reason == FantikiTransactionReason.ADMIN_GRANT }
        assertEquals(1, txs.size)
        assertEquals(42L, txs.single().amount)
        assertEquals("Manual correction", txs.single().adminReason)
    }

    companion object {
        @JvmField
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}
