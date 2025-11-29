package com.codewithfk.models

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import java.util.UUID

object Categories : UUIDTable("categories") {
    val name = varchar("name", 100).uniqueIndex()
    val slug = varchar("slug", 100).uniqueIndex()
    val description = text("description").nullable()
    val icon = varchar("icon", 255).nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}

