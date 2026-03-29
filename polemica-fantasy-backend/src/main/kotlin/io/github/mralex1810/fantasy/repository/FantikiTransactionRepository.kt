package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.FantikiTransaction
import org.springframework.data.jpa.repository.JpaRepository

interface FantikiTransactionRepository : JpaRepository<FantikiTransaction, Long>
