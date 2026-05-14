package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.entity.MarketplaceListing
import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.MarketplaceComplaintRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingSanctionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class MarketplaceComplaintServiceTest {
    @Mock
    private lateinit var marketplaceComplaintRepository: MarketplaceComplaintRepository

    @Mock
    private lateinit var marketplaceListingRepository: MarketplaceListingRepository

    @Mock
    private lateinit var marketplaceListingSanctionRepository: MarketplaceListingSanctionRepository

    @Mock
    private lateinit var economyConfigService: EconomyConfigService

    @InjectMocks
    private lateinit var service: MarketplaceComplaintService

    @Test
    fun `complain on sold listing creates complaint`() {
        val listing = soldListing(sellerId = 2L, buyerId = 3L)
        val reporter = user(1L)
        whenever(marketplaceListingRepository.findById(50L)).thenReturn(Optional.of(listing))
        whenever(marketplaceListingSanctionRepository.existsByListing_Id(50L)).thenReturn(false)
        whenever(marketplaceComplaintRepository.existsByListing_IdAndTelegramUser_Id(50L, 1L)).thenReturn(false)
        whenever(economyConfigService.getInt("marketplace.daily_complaint_limit")).thenReturn(5)
        whenever(marketplaceComplaintRepository.countByTelegramUserIdSince(any(), any())).thenReturn(1)
        whenever(marketplaceComplaintRepository.countByListing_Id(50L)).thenReturn(4)

        val result = service.complain(reporter, 50L)

        assertEquals(50L, result.listingId)
        assertEquals(4, result.totalComplaints)
        assertEquals(3, result.remainingToday)
        verify(marketplaceComplaintRepository).save(any())
    }

    @Test
    fun `complain rejects participant of transaction`() {
        val listing = soldListing(sellerId = 1L, buyerId = 3L)
        val reporter = user(1L)
        whenever(marketplaceListingRepository.findById(50L)).thenReturn(Optional.of(listing))

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.complain(reporter, 50L)
        }
        assertEquals(400, ex.statusCode.value())
    }

    @Test
    fun `complain rejects duplicate complaint`() {
        val listing = soldListing(sellerId = 2L, buyerId = 3L)
        val reporter = user(1L)
        whenever(marketplaceListingRepository.findById(50L)).thenReturn(Optional.of(listing))
        whenever(marketplaceListingSanctionRepository.existsByListing_Id(50L)).thenReturn(false)
        whenever(marketplaceComplaintRepository.existsByListing_IdAndTelegramUser_Id(50L, 1L)).thenReturn(true)

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.complain(reporter, 50L)
        }
        assertEquals(409, ex.statusCode.value())
    }

    @Test
    fun `complain rejects when daily limit reached`() {
        val listing = soldListing(sellerId = 2L, buyerId = 3L)
        val reporter = user(1L)
        whenever(marketplaceListingRepository.findById(50L)).thenReturn(Optional.of(listing))
        whenever(marketplaceListingSanctionRepository.existsByListing_Id(50L)).thenReturn(false)
        whenever(marketplaceComplaintRepository.existsByListing_IdAndTelegramUser_Id(50L, 1L)).thenReturn(false)
        whenever(economyConfigService.getInt("marketplace.daily_complaint_limit")).thenReturn(5)
        whenever(marketplaceComplaintRepository.countByTelegramUserIdSince(any(), any())).thenReturn(5)

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.complain(reporter, 50L)
        }
        assertEquals(429, ex.statusCode.value())
    }

    @Test
    fun `complain rejects already sanctioned transaction`() {
        val listing = soldListing(sellerId = 2L, buyerId = 3L)
        val reporter = user(1L)
        whenever(marketplaceListingRepository.findById(50L)).thenReturn(Optional.of(listing))
        whenever(marketplaceListingSanctionRepository.existsByListing_Id(50L)).thenReturn(true)

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.complain(reporter, 50L)
        }
        assertEquals(400, ex.statusCode.value())
    }

    private fun soldListing(sellerId: Long, buyerId: Long): MarketplaceListing =
        MarketplaceListing(
            seller = user(sellerId),
            buyer = user(buyerId),
            status = MarketplaceListingStatus.SOLD,
            soldAt = Instant.now(),
        ).apply { id = 50L }

    private fun user(id: Long): TelegramUser = TelegramUser(telegramId = 1000 + id).apply { this.id = id }
}
