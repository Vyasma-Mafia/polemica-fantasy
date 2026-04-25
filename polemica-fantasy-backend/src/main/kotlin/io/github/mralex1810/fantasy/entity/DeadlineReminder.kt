package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "deadline_reminder")
class DeadlineReminder(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_id", nullable = false)
    var series: Series? = null,

    @Column(name = "remind_at", nullable = false)
    var remindAt: Instant = Instant.now(),

    @Column(nullable = false)
    var sent: Boolean = false,

    @Column(name = "sent_at")
    var sentAt: Instant? = null,

    @Column(name = "recipient_count")
    var recipientCount: Int? = null,
)
