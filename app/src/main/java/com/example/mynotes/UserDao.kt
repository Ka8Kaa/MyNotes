package com.example.mynotes

import androidx.room.*

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: User): Long

    @Query("SELECT * FROM User WHERE username = :username")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM User WHERE username = :username AND passwordHash = :password")
    suspend fun authenticate(username: String, password: String): User?
}
