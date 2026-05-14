package io.github.mralex1810.fantasy.controller.admin

import io.github.mralex1810.fantasy.dto.admin.request.BanPairRequest
import io.github.mralex1810.fantasy.dto.admin.request.BanUserRequest
import io.github.mralex1810.fantasy.dto.admin.request.MarkPairClearedRequest
import io.github.mralex1810.fantasy.dto.admin.request.SanctionTransactionRequest
import io.github.mralex1810.fantasy.dto.admin.response.BanPairPreviewDto
import io.github.mralex1810.fantasy.dto.admin.response.BanPairResultDto
import io.github.mralex1810.fantasy.dto.admin.response.PagedComplainedTransactionsDto
import io.github.mralex1810.fantasy.dto.admin.response.PagedPairSanctionHistoryDto
import io.github.mralex1810.fantasy.dto.admin.response.PagedUsersByComplaintsDto
import io.github.mralex1810.fantasy.dto.admin.response.PairAnalysisDto
import io.github.mralex1810.fantasy.dto.admin.response.PairTradesResultDto
import io.github.mralex1810.fantasy.dto.admin.response.SanctionTransactionResultDto
import io.github.mralex1810.fantasy.dto.admin.response.TransactionComplaintsListDto
import io.github.mralex1810.fantasy.service.MarketplaceAdminService
import jakarta.validation.Valid
import java.security.Principal
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/marketplace")
class MarketplaceAdminController(
    private val marketplaceAdminService: MarketplaceAdminService,
) {

    @GetMapping("/pair-analysis")
    fun getPairAnalysis(): List<PairAnalysisDto> = marketplaceAdminService.getPairAnalysis()

    @GetMapping("/pair-trades")
    fun getPairTrades(
        @RequestParam("userA") userA: Long,
        @RequestParam("userB") userB: Long,
    ): PairTradesResultDto = marketplaceAdminService.getPairTrades(userA, userB)

    @GetMapping("/ban-pair/preview")
    fun getBanPairPreview(
        @RequestParam("userA") userA: Long,
        @RequestParam("userB") userB: Long,
    ): BanPairPreviewDto = marketplaceAdminService.getBanPairPreview(userA, userB)

    @GetMapping("/ban-pair/history")
    fun getBanPairHistory(
        @PageableDefault(size = 20) pageable: Pageable,
    ): PagedPairSanctionHistoryDto = marketplaceAdminService.getBanPairHistory(pageable)

    @GetMapping("/complained-transactions")
    fun getComplainedTransactions(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "1") minComplaints: Int,
        @RequestParam(required = false) sortBy: String?,
    ): PagedComplainedTransactionsDto = marketplaceAdminService.getComplainedTransactions(
        page = page,
        size = size,
        minComplaints = minComplaints,
        sortBy = sortBy,
    )

    @GetMapping("/transactions/{listingId}/complaints")
    fun getTransactionComplaints(
        @PathVariable listingId: Long,
    ): TransactionComplaintsListDto = marketplaceAdminService.getTransactionComplaints(listingId)

    @PostMapping("/transactions/{listingId}/sanction")
    fun sanctionTransaction(
        @PathVariable listingId: Long,
        @Valid @RequestBody body: SanctionTransactionRequest,
        principal: Principal,
    ): SanctionTransactionResultDto = marketplaceAdminService.sanctionTransaction(
        listingId = listingId,
        request = body,
        adminUsername = principal.name,
    )

    @GetMapping("/users-by-complaints")
    fun getUsersByComplaints(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) sortBy: String?,
    ): PagedUsersByComplaintsDto = marketplaceAdminService.getUsersByComplaints(
        page = page,
        size = size,
        sortBy = sortBy,
    )

    @PostMapping("/users/{telegramId}/ban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun banUser(
        @PathVariable telegramId: Long,
        @Valid @RequestBody body: BanUserRequest,
    ) {
        marketplaceAdminService.banUser(telegramId, body)
    }

    @PostMapping("/ban-pair")
    fun banPair(@Valid @RequestBody body: BanPairRequest): BanPairResultDto = marketplaceAdminService.banPair(body)

    @PostMapping("/pair-analysis/clear")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markPairCleared(@Valid @RequestBody body: MarkPairClearedRequest) {
        marketplaceAdminService.markPairCleared(body)
    }

    @DeleteMapping("/pair-analysis/clear")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unmarkPairCleared(
        @RequestParam("userA") userA: Long,
        @RequestParam("userB") userB: Long,
    ) {
        marketplaceAdminService.unmarkPairCleared(userA, userB)
    }

    @PostMapping("/unban/{telegramId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unban(@PathVariable telegramId: Long) {
        marketplaceAdminService.unban(telegramId)
    }
}
