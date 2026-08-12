package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.Series
import io.github.mralex1810.fantasy.entity.Tournament
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.repository.TournamentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class SeriesFinalizationConcurrencyIntegrationTest {

    @Autowired
    private lateinit var service: SeriesFinalizationService

    @Autowired
    private lateinit var seriesRepository: SeriesRepository

    @Autowired
    private lateinit var tournamentRepository: TournamentRepository

    @MockBean
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @MockBean
    private lateinit var seriesCompletionService: SeriesCompletionService

    @BeforeEach
    fun resetPublisher() {
        reset(applicationEventPublisher)
        reset(seriesCompletionService)
    }

    @Test
    fun `concurrent finalize commits once`() {
        val tournament = tournamentRepository.save(Tournament(name = "Concurrent finalize test"))
        val series = seriesRepository.saveAndFlush(
            Series(
                tournament = tournament,
                name = "Concurrent series",
                startsAt = Instant.now(),
                teamDeadline = Instant.now(),
            ),
        )
        val seriesId = series.id!!
        val firstFinalizerReachedReadiness = CountDownLatch(1)
        val releaseFirstFinalizer = CountDownLatch(1)
        val completionCalls = AtomicInteger()
        val eventCalls = AtomicInteger()
        doAnswer {
            if (completionCalls.incrementAndGet() == 1) {
                firstFinalizerReachedReadiness.countDown()
                check(releaseFirstFinalizer.await(5, TimeUnit.SECONDS))
            }
            SeriesCompletion(SeriesCompletionStatus.READY, "c".repeat(64))
        }.`when`(seriesCompletionService).evaluateForFinalization(seriesId)
        doAnswer {
            eventCalls.incrementAndGet()
            Unit
        }.`when`(applicationEventPublisher).publishEvent(anyNotNull())

        val startTogether = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val calls = List(2) {
                executor.submit<Int> {
                    startTogether.await(5, TimeUnit.SECONDS)
                    try {
                        service.finalizeSeries(seriesId)
                        200
                    } catch (e: ResponseStatusException) {
                        e.statusCode.value()
                    }
                }
            }

            check(firstFinalizerReachedReadiness.await(5, TimeUnit.SECONDS))
            Thread.sleep(250)
            releaseFirstFinalizer.countDown()

            val statuses = calls.map { future ->
                try {
                    future.get(10, TimeUnit.SECONDS)
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            }.sorted()

            assertEquals(listOf(200, 409), statuses)
            val stored = seriesRepository.findById(seriesId).orElseThrow()
            assertEquals(true, stored.finalized)
            assertEquals(1, completionCalls.get())
        } finally {
            releaseFirstFinalizer.countDown()
            executor.shutdownNow()
        }
    }

    private companion object {
        private fun <T> anyNotNull(): T {
            org.mockito.ArgumentMatchers.any<T>()
            @Suppress("UNCHECKED_CAST")
            return null as T
        }

        @JvmField
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}
