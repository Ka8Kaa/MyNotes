package com.example.mynotes

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.os.Environment



class NoteDetailActivity : AppCompatActivity() {

    private lateinit var titleEditText: EditText
    private lateinit var noteEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var deleteButton: Button
    private lateinit var exportButton: Button
    private lateinit var backToNotesTextView: TextView
    private lateinit var noteHeaderTextView: TextView

    private var noteId: Int? = null
    private lateinit var db: NoteDatabase

    private val formatter = SimpleDateFormat("dd.MM.yyyy. HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_detail)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val titleText = toolbar.findViewById<TextView>(R.id.toolbarTitle)
        titleText.text = getString(R.string.app_name)

        val sharedPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val username = sharedPrefs.getString("logged_in_user", null)

        val usernameTextView = toolbar.findViewById<TextView>(R.id.toolbarUsername)
        usernameTextView.text = username ?: "User"
        usernameTextView.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.logout))
                .setMessage(getString(R.string.confirm_logout))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    sharedPrefs.edit().remove("logged_in_user").apply()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // View binding
        titleEditText = findViewById(R.id.titleEditText)
        noteEditText = findViewById(R.id.noteEditText)
        saveButton = findViewById(R.id.saveButton)
        deleteButton = findViewById(R.id.deleteButton)
        exportButton = findViewById(R.id.exportButton)
        backToNotesTextView = findViewById(R.id.backToNotesTextView)
        noteHeaderTextView = findViewById(R.id.noteHeaderTextView)

        backToNotesTextView.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        db = NoteDatabase.getDatabase(this)
        noteId = intent.getIntExtra("note_id", -1).takeIf { it != -1 }

        if (noteId != null) {
            noteHeaderTextView.text = getString(R.string.edit_note)
            loadNote(noteId!!)
            deleteButton.visibility = View.VISIBLE
            exportButton.visibility = View.VISIBLE
        } else {
            noteHeaderTextView.text = getString(R.string.new_note)
            deleteButton.visibility = View.GONE
            exportButton.visibility = View.GONE
        }

        saveButton.setOnClickListener {
            val title = titleEditText.text.toString()
            val text = noteEditText.text.toString()

            CoroutineScope(Dispatchers.IO).launch {
                if (noteId != null) {
                    val existing = db.noteDao().getNoteById(noteId!!)
                    existing?.let {
                        val updated = it.copy(
                            title = title,
                            text = text,
                            updatedAt = System.currentTimeMillis()
                        )
                        db.noteDao().updateNote(updated)
                    }
                } else {
                    val now = System.currentTimeMillis()
                    val newNote = Note(
                        title = title,
                        text = text,
                        createdAt = now,
                        updatedAt = now,
                        ownerUsername = username!!
                    )
                    db.noteDao().insertNote(newNote)
                }
                finish()
            }
        }

        deleteButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_note))
                .setMessage(getString(R.string.confirm_delete))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        noteId?.let {
                            db.noteDao().getNoteById(it)?.let { note ->
                                db.noteDao().deleteNote(note)
                            }
                        }
                        finish()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        exportButton.setOnClickListener {
            exportNotesToTextFile()
        }
    }

    private fun loadNote(id: Int) {
        val sharedPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val username = sharedPrefs.getString("logged_in_user", null)

        CoroutineScope(Dispatchers.IO).launch {
            val note = db.noteDao().getNoteById(id)
            runOnUiThread {
                if (note != null) {
                    if (note.ownerUsername == username) {
                        titleEditText.setText(note.title)
                        noteEditText.setText(note.text)
                    } else {
                        AlertDialog.Builder(this@NoteDetailActivity)
                            .setTitle(getString(R.string.access_denied))
                            .setMessage(getString(R.string.not_authorized))
                            .setPositiveButton("OK") { _, _ ->
                                finish()
                            }
                            .setCancelable(false)
                            .show()
                    }
                } else {
                    finish() // ako bilješka ne postoji
                }
            }
        }
    }

    private fun exportNotesToTextFile() {
        CoroutineScope(Dispatchers.IO).launch {
            val notes = db.noteDao().getAllNotes()
            val exportContent = StringBuilder()

            notes.forEach { note ->
                exportContent.append("${getString(R.string.title)}: ${note.title}\n")
                exportContent.append("${getString(R.string.text)}:  ${note.text}\n")
                exportContent.append("------\n\n")
            }

            try {
                val fileName = "my_notes_${System.currentTimeMillis()}.txt"
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                file.writeText(exportContent.toString())

                runOnUiThread {
                    AlertDialog.Builder(this@NoteDetailActivity)
                        .setTitle(getString(R.string.export_success))
                        .setMessage("${getString(R.string.notes_saved_to)}\n${file.absolutePath}")
                        .setPositiveButton("OK", null)
                        .show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    AlertDialog.Builder(this@NoteDetailActivity)
                        .setTitle(getString(R.string.export_failed))
                        .setMessage("${getString(R.string.error)}: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_note_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                true
            }
            R.id.action_change_language -> {
                showLanguageDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("English", "Hrvatski", "Deutsch")
        val languageCodes = arrayOf("en", "hr", "de")

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.change_language))
            .setItems(languages) { _, which ->
                setLocale(languageCodes[which])
                recreate()
            }
            .show()
    }

    private fun setLocale(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        baseContext.resources.updateConfiguration(config, baseContext.resources.displayMetrics)

        val intent = intent
        finish()
        startActivity(intent)
    }
}
