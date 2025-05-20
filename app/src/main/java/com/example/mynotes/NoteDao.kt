package com.example.mynotes

import androidx.room.*

@Dao
interface NoteDao {

    @Insert
    suspend fun insertNote(note: Note)

    @Query("SELECT * FROM notes")
    suspend fun getAllNotes(): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Int): Note?

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("SELECT * FROM notes ORDER BY title COLLATE NOCASE ASC, updatedAt ASC")
    suspend fun getNotesSortedByTitleThenDate(): List<Note>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC, title COLLATE NOCASE ASC")
    suspend fun getNotesSortedByDateThenTitle(): List<Note>

    @Query("SELECT * FROM notes WHERE ownerUsername = :username ORDER BY updatedAt DESC")
    fun getNotesByUser(username: String): List<Note>


}
