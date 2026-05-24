package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.ProductCampaign
import org.springframework.data.jpa.repository.JpaRepository

interface ProductCampaignRepository : JpaRepository<ProductCampaign, Long> {
    fun findTop50ByOrderByCreatedAtDesc(): List<ProductCampaign>
}
