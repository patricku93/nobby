package com.nobby.ring.data.repo

import com.nobby.ring.data.db.ReminderRuleDao
import com.nobby.ring.data.db.ReminderRuleEntity
import com.nobby.ring.domain.model.Importance
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ReminderTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalTime
import javax.inject.Inject

class ReminderRepository @Inject constructor(private val dao: ReminderRuleDao) {
    val rules: Flow<List<ReminderRule>> = dao.all().map { it.map(::toDomain) }
    suspend fun rulesOnce(): List<ReminderRule> = dao.allOnce().map(::toDomain)
    /** Inserts or updates [rule]; returns the rule's id (new or existing). Room's upsert returns -1 on update. */
    suspend fun upsert(rule: ReminderRule): Long {
        val id = dao.upsert(toEntity(rule))
        return if (id == -1L) rule.id else id
    }
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun replaceAll(rules: List<ReminderRule>) = dao.replaceAll(rules.map { toEntity(it.copy(id = 0)) })

    private fun toDomain(e: ReminderRuleEntity) = ReminderRule(
        id = e.id,
        target = ReminderTarget.valueOf(e.target),
        offsetDays = e.offsetDays,
        timeOfDay = if (e.timeHour != null && e.timeMinute != null) LocalTime(e.timeHour, e.timeMinute) else null,
        offsetHours = e.offsetHours,
        importance = Importance.valueOf(e.importance),
        enabled = e.enabled,
    )

    private fun toEntity(r: ReminderRule) = ReminderRuleEntity(
        id = r.id,
        target = r.target.name,
        offsetDays = r.offsetDays,
        timeHour = r.timeOfDay?.hour,
        timeMinute = r.timeOfDay?.minute,
        offsetHours = r.offsetHours,
        importance = r.importance.name,
        enabled = r.enabled,
    )
}
