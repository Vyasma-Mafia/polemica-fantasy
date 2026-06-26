package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.UserCardMergeInput
import org.springframework.data.jpa.repository.JpaRepository

interface UserCardMergeInputRepository : JpaRepository<UserCardMergeInput, Long>
