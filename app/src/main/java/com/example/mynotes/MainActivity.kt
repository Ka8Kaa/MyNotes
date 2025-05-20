package com.example.mynotes

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var addNoteButton: Button
    private lateinit var notesContainer: LinearLayout
    private lateinit var db: NoteDatabase
    private lateinit var toolbarUsername: TextView

    private var currentSort = SortOption.BY_DATE
    private val formatter = SimpleDateFormat("dd.MM.yyyy. HH:mm", Locale.getDefault())

    enum class SortOption {
        BY_TITLE,
        BY_DATE
    }

    private var loggedInUsername: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val username = sharedPrefs.getString("logged_in_user", null)
        if (username == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        loggedInUsername = username

        setContentView(R.layout.activity_main)

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val toolbarTitle = toolbar.findViewById<TextView>(R.id.toolbarTitle)
        toolbarTitle.text = getString(R.string.app_name)

        toolbarUsername = toolbar.findViewById(R.id.toolbarUsername)
        toolbarUsername.text = username
        toolbarUsername.setOnClickListener {
            showLogoutConfirmation()
        }

        addNoteButton = findViewById(R.id.addNoteButton)
        notesContainer = findViewById(R.id.notesContainer)
        db = NoteDatabase.getDatabase(this)

        addNoteButton.setOnClickListener {
            val intent = Intent(this, NoteDetailActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadNotesSorted()
    }

    private fun loadNotesSorted() {
        CoroutineScope(Dispatchers.IO).launch {
            val notes = db.noteDao().getNotesByUser(loggedInUsername ?: "")

            val sortedNotes = when (currentSort) {
                SortOption.BY_TITLE -> notes.sortedWith(
                    compareBy<Note> { it.title.lowercase() }
                        .thenBy { it.createdAt.coerceAtLeast(it.updatedAt) }
                )
                SortOption.BY_DATE -> notes.sortedWith(
                    compareByDescending<Note> { it.createdAt.coerceAtLeast(it.updatedAt) }
                        .thenBy { it.title.lowercase() }
                )
            }

            runOnUiThread {
                notesContainer.removeAllViews()

                for (note in sortedNotes) {
                    val isNew = note.createdAt == note.updatedAt
                    val label = if (isNew) getString(R.string.created_at) else getString(R.string.last_edited)
                    val dateStr = formatter.format(if (isNew) note.createdAt else note.updatedAt)

                    val noteLayout = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, 16, 0, 16)
                    }

                    val titleView = TextView(this@MainActivity).apply {
                        text = note.title
                        textSize = 20f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }

                    val textView = TextView(this@MainActivity).apply {
                        text = "${note.text}\n\n$label $dateStr"
                        textSize = 16f
                        setPadding(0, 4, 0, 0)
                    }

                    val divider = View(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1
                        )
                        setBackgroundColor(android.graphics.Color.LTGRAY)
                    }

                    noteLayout.setOnClickListener {
                        val intent = Intent(this@MainActivity, NoteDetailActivity::class.java)
                        intent.putExtra("note_id", note.id)
                        startActivity(intent)
                    }

                    noteLayout.setOnLongClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle(getString(R.string.delete_note))
                            .setMessage(getString(R.string.confirm_delete))
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    db.noteDao().deleteNote(note)
                                    runOnUiThread { loadNotesSorted() }
                                }
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                        true
                    }

                    noteLayout.addView(titleView)
                    noteLayout.addView(textView)
                    notesContainer.addView(noteLayout)
                    notesContainer.addView(divider)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_change_language -> {
                showLanguageSelectionDialog()
                true
            }
            R.id.action_sort_notes -> {
                showSortDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.logout))
            .setMessage(getString(R.string.confirm_logout))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                prefs.edit().remove("logged_in_user").apply()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.sort_by_title),
            getString(R.string.sort_by_date)
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sort_notes))
            .setItems(options) { _, which ->
                currentSort = when (which) {
                    0 -> SortOption.BY_TITLE
                    else -> SortOption.BY_DATE
                }
                loadNotesSorted()
            }
            .show()
    }

    private fun showLanguageSelectionDialog() {
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
        val config = resources.configuration
        config.setLocale(locale)
        baseContext.resources.updateConfiguration(config, baseContext.resources.displayMetrics)
    }
}
