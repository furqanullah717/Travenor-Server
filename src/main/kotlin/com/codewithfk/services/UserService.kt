package com.codewithfk.services

import com.codewithfk.database.DatabaseFactory
import com.codewithfk.dto.UserResponse
import com.codewithfk.models.UserRole
import com.codewithfk.models.Users
import org.jetbrains.exposed.sql.*
import org.mindrot.jbcrypt.BCrypt
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

class UserService {
    suspend fun createUser(
        email: String,
        password: String,
        firstName: String?,
        lastName: String?,
        phone: String?,
        role: String
    ): UserResponse = DatabaseFactory.dbQuery {
        val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())
        val userRole = try {
            UserRole.valueOf(role.uppercase())
        } catch (e: IllegalArgumentException) {
            UserRole.CUSTOMER
        }
        
        val id = Users.insert {
            it[Users.email] = email
            it[Users.passwordHash] = passwordHash
            it[Users.firstName] = firstName
            it[Users.lastName] = lastName
            it[Users.phone] = phone
            it[Users.role] = userRole.name
        }[Users.id].value
        
        val userRow = Users.select { Users.id eq id }
            .singleOrNull()
            ?: throw IllegalStateException("Failed to retrieve created user")
        
        rowToUser(userRow)
    }
    
    suspend fun getUserByEmail(email: String): UserResponse? = DatabaseFactory.dbQuery {
        Users.select { Users.email eq email }
            .map { rowToUser(it) }
            .singleOrNull()
    }
    
    suspend fun getUserById(id: String): UserResponse? = DatabaseFactory.dbQuery {
        Users.selectAll().where { Users.id eq UUID.fromString(id) }
            .map { rowToUser(it) }
            .singleOrNull()
    }
    
    suspend fun verifyPassword(email: String, password: String): Boolean = DatabaseFactory.dbQuery {
        val user = Users.select { Users.email eq email }.singleOrNull()
        user?.let {
            BCrypt.checkpw(password, it[Users.passwordHash])
        } ?: false
    }
    
    suspend fun updateUser(id: String, firstName: String?, lastName: String?, phone: String?): UserResponse? =
        DatabaseFactory.dbQuery {
            Users.update({ Users.id eq UUID.fromString(id) }) {
                if (firstName != null) it[Users.firstName] = firstName
                if (lastName != null) it[Users.lastName] = lastName
                if (phone != null) it[Users.phone] = phone
                it[Users.updatedAt] = Clock.System.now()
            }
            getUserById(id)
        }
    
    suspend fun getAllUsers(page: Int = 1, pageSize: Int = 20): List<UserResponse> = DatabaseFactory.dbQuery {
        Users.selectAll()
            .limit(pageSize, offset = ((page - 1) * pageSize).toLong())
            .map { rowToUser(it) }
    }
    
    private fun rowToUser(row: ResultRow): UserResponse {
        return UserResponse(
            id = row[Users.id].value.toString(),
            email = row[Users.email],
            firstName = row[Users.firstName],
            lastName = row[Users.lastName],
            phone = row[Users.phone],
            role = row[Users.role],
            isActive = row[Users.isActive]
        )
    }
}

