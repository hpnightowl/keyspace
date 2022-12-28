package cloud.keyspace.android

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.AnimatedVectorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils.loadAnimation
import android.view.inputmethod.InputMethodManager
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import com.github.dhaval2404.colorpicker.MaterialColorPickerDialog
import com.github.dhaval2404.colorpicker.listener.ColorListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.keyspace.keyspacemobile.NetworkUtilities
import com.yydcdut.markdown.MarkdownConfiguration
import com.yydcdut.markdown.MarkdownProcessor
import com.yydcdut.markdown.callback.OnTodoClickCallback
import com.yydcdut.markdown.loader.DefaultLoader
import com.yydcdut.markdown.syntax.edit.EditFactory
import com.yydcdut.markdown.syntax.text.TextFactory
import com.yydcdut.markdown.theme.Theme
import com.yydcdut.markdown.theme.ThemeDefault
import com.yydcdut.markdown.theme.ThemeDesert
import java.time.Instant
import java.util.*
import kotlin.properties.Delegates

class AddNote : AppCompatActivity() {

    lateinit var utils: MiscUtilities
    lateinit var crypto: CryptoUtilities
    lateinit var io: IOUtilities
    lateinit var network: NetworkUtilities

    lateinit var dateAndTime: TextView
    var timestamp by Delegates.notNull<Long>()

    var noteData = ""
    lateinit var noteEditor: com.yydcdut.markdown.MarkdownEditText
    lateinit var notePreview: com.yydcdut.markdown.MarkdownEditText

    lateinit var input: InputMethodManager

    lateinit var tagButton: ImageView
    private lateinit var tagPicker: AddTag
    private var tagId: String? = null
    val tagIdGrabber = Handler(Looper.getMainLooper())

    var favorite: Boolean = false
    lateinit var favoriteButton: ImageView

    var preview: Boolean = true
    lateinit var previewButton: ImageView

    var noteColor: String? = null
    lateinit var colorButton: ImageView

    private var frequencyAccessed = 0L
    private var previousTimestamp = 0L

    lateinit var doneButton: ImageView
    lateinit var backButton: ImageView
    lateinit var deleteButton: ImageView

    lateinit var noteToolbar: HorizontalScrollView

    var unrenderedText = ""
    var markdownToolbar = true
    lateinit var theme: Theme

    lateinit var keyring: CryptoUtilities.Keyring
    private var itemId: String? = null
    private lateinit var vault: IOUtilities.Vault
    private lateinit var note: IOUtilities.Note

    lateinit var configData: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.edit_note)

        configData = getSharedPreferences(applicationContext.packageName + "_configuration_data", MODE_PRIVATE)

        val allowScreenshots = configData.getBoolean("allowScreenshots", false)
        if (!allowScreenshots) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        utils = MiscUtilities (applicationContext)
        crypto = CryptoUtilities(applicationContext, this)

        utils = MiscUtilities (applicationContext)
        crypto = CryptoUtilities(applicationContext, this)

        val intentData = crypto.receiveKeyringFromSecureIntent (
            currentActivityClassNameAsString = getString(R.string.title_activity_add_note),
            intent = intent
        )

        keyring = intentData.first
        network = NetworkUtilities(applicationContext, this, keyring)
        itemId = intentData.second

        network = NetworkUtilities(applicationContext, this, keyring)

        io = IOUtilities(applicationContext, this, keyring)

        initializeUI()

        vault = io.getVault()
        if (itemId != null) {
            note = io.decryptNote(io.getNote(itemId!!, vault)!!)
            loadNote (note)

            frequencyAccessed = note.frequencyAccessed!!
        }

    }

    @SuppressLint("UseCompatLoadingForDrawables", "ClickableViewAccessibility", "SetTextI18n")
    private fun initializeUI (): Boolean {
        theme = when (applicationContext.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)) {
            Configuration.UI_MODE_NIGHT_YES -> ThemeDesert()
            Configuration.UI_MODE_NIGHT_NO -> ThemeDefault()
            Configuration.UI_MODE_NIGHT_UNDEFINED -> ThemeDefault()
            else -> ThemeDefault()
        }

        noteEditor = findViewById(R.id.noteEditor)
        noteEditor.isActivated = true
        noteEditor.isPressed = true

        noteEditor.doOnTextChanged { text, start, before, count ->
            noteData = text.toString()
        }

        val editorMarkdownConfig = MarkdownConfiguration.Builder(applicationContext)
            .setTheme(theme)
            .showLinkUnderline(true)
            .setOnLinkClickCallback { _, link ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            }
            .setLinkFontColor(R.attr.colorControlActivated)
            .setOnTodoClickCallback(object : OnTodoClickCallback {
                override fun onTodoClicked(view: View?, line: String?, lineNumber: Int): CharSequence { return "" }
            })
            .setRxMDImageLoader(DefaultLoader(applicationContext))
            .build()

        val editorMarkdownProcessor = MarkdownProcessor(this)
        editorMarkdownProcessor.config(editorMarkdownConfig)
        editorMarkdownProcessor.factory(EditFactory.create())
        editorMarkdownProcessor.live(noteEditor)

        val previewMarkdownConfig = MarkdownConfiguration.Builder(applicationContext)
            .setTheme(theme)
            .showLinkUnderline(true)
            .setOnLinkClickCallback { view, link ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            }
            .setLinkFontColor(R.attr.colorControlActivated)
            .setOnTodoClickCallback(object : OnTodoClickCallback {
                override fun onTodoClicked(view: View?, text: String?, lineNumber: Int): CharSequence {
                    return text.toString()
                }
            })
            .setRxMDImageLoader(DefaultLoader(applicationContext))
            .build()

        var previewMarkdownProcessor = MarkdownProcessor(this)
        previewMarkdownProcessor.config(previewMarkdownConfig)
        previewMarkdownProcessor.factory(TextFactory.create())

        timestamp = Instant.now().epochSecond

        dateAndTime = findViewById(R.id.dateAndTime)
        dateAndTime.visibility = View.GONE

        // Load toolbar
        noteToolbar = findViewById<HorizontalScrollView>(R.id.noteToolbar)

        noteToolbar.visibility = View.VISIBLE

        findViewById<ImageView>(R.id.helpButton).setOnClickListener {
            val inflater = layoutInflater
            val dialogView: View = inflater.inflate (R.layout.markdown_help, null)
            val dialogBuilder = MaterialAlertDialogBuilder(this)
            dialogBuilder
                .setView(dialogView)
                .setTitle("Markdown guide")
                .setIcon(getDrawable(R.drawable.markdown))
                .setCancelable(true)
            val markdownDialog = dialogBuilder.show()

            val markdownUnrendered = markdownDialog.findViewById<View>(R.id.guide) as TextView
            val markdownRendered = markdownDialog.findViewById<View>(R.id.guideRendered) as com.yydcdut.markdown.MarkdownEditText

            previewMarkdownProcessor.live(markdownRendered)

            markdownUnrendered.visibility = View.VISIBLE
            markdownRendered.visibility = View.GONE
            markdownUnrendered.startAnimation(loadAnimation(applicationContext, R.anim.from_top))
            val renderButton = markdownDialog.findViewById<View>(R.id.renderButton) as MaterialButton
            var rendered = false
            renderButton.setOnClickListener {
                if (!rendered) {
                    rendered = true
                    markdownUnrendered.visibility = View.GONE
                    markdownRendered.visibility = View.VISIBLE
                    renderButton.text = "Tap to view raw"
                    renderButton.icon = getDrawable(R.drawable.ic_baseline_visibility_off_24)
                } else {
                    rendered = false
                    markdownUnrendered.visibility = View.VISIBLE
                    markdownRendered.visibility = View.GONE
                    renderButton.text = "Tap to render"
                    renderButton.icon = getDrawable(R.drawable.ic_baseline_visibility_24)
                }
            }
            val backButton =  markdownDialog.findViewById<View>(R.id.backButton) as MaterialButton
            backButton.setOnClickListener { markdownDialog.dismiss() }
            dialogBuilder.create()
        }

        findViewById<ImageView>(R.id.numberListButton).setOnClickListener {
            val start = noteEditor.selectionStart.coerceAtLeast(0)
            val end = noteEditor.selectionEnd.coerceAtLeast(0)
            val selectedText = noteEditor.text.toString().substring(start, end)
            if (selectedText.isNotEmpty()) noteEditor.setText(noteEditor.text.toString().replace(selectedText, utils.stringToNumberedString(selectedText)))
            else noteEditor.append(utils.stringToNumberedString(selectedText))
            noteEditor.setSelection(noteEditor.text.toString().length)
        }

        findViewById<ImageView>(R.id.bulletListButton).setOnClickListener {
            val start = noteEditor.selectionStart.coerceAtLeast(0)
            val end = noteEditor.selectionEnd.coerceAtLeast(0)
            val selectedText = noteEditor.text.toString().substring(start, end)
            if (selectedText.isNotEmpty()) noteEditor.setText(noteEditor.text.toString().replace(selectedText, utils.stringToBulletedString(selectedText)))
            else noteEditor.append(utils.stringToBulletedString(selectedText))
            noteEditor.setSelection(noteEditor.text.toString().length)
        }

        findViewById<ImageView>(R.id.linkButton).setOnClickListener {
            val start = noteEditor.selectionStart.coerceAtLeast(0)
            val end = noteEditor.selectionEnd.coerceAtLeast(0)
            val selectedText = noteEditor.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteEditor.setText(noteEditor.text.toString().replace(selectedText, "[${selectedText}]()"))
                noteEditor.setSelection(noteEditor.text.toString().indexOf(selectedText) + selectedText.length + 2)
            } else {
                val markdown = "[text](url)"
                try {
                    noteEditor.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), markdown, 0, markdown.length)
                } catch (_: Exception) {
                    noteEditor.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.italicButton).setOnClickListener {
            val start = noteEditor.selectionStart.coerceAtLeast(0)
            val end = noteEditor.selectionEnd.coerceAtLeast(0)
            val selectedText = noteEditor.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteEditor.setText(noteEditor.text.toString().replace(selectedText, "_${selectedText}_"))
                noteEditor.setSelection(noteEditor.text.toString().indexOf(selectedText) + selectedText.length)
            } else {
                val markdown = "_text_"
                try {
                    noteEditor.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), markdown, 0, markdown.length)
                } catch (_: Exception) {
                    noteEditor.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.checkedButton).setOnClickListener {
            val start = noteEditor.selectionStart.coerceAtLeast(0)
            val end = noteEditor.selectionEnd.coerceAtLeast(0)
            val selectedText = noteEditor.text.toString().substring(start, end)
            if (selectedText.isNotEmpty()) noteEditor.setText(noteEditor.text.toString().replace(selectedText, utils.stringToCheckedString(selectedText)))
            else noteEditor.append(utils.stringToCheckedString(selectedText))
            noteEditor.setSelection(noteEditor.text.toString().length)
        }

        findViewById<ImageView>(R.id.uncheckedButton).setOnClickListener {
            val start = noteEditor.selectionStart.coerceAtLeast(0)
            val end = noteEditor.selectionEnd.coerceAtLeast(0)
            val selectedText = noteEditor.text.toString().substring(start, end)
            if (selectedText.isNotEmpty()) noteEditor.setText(noteEditor.text.toString().replace(selectedText, utils.stringToUncheckedString(selectedText)))
            else noteEditor.append(utils.stringToUncheckedString(selectedText))
            noteEditor.setSelection(noteEditor.text.toString().length)
        }

        findViewById<ImageView>(R.id.imageButton).setOnClickListener {
            val start = noteEditor.selectionStart.coerceAtLeast(0)
            val end = noteEditor.selectionEnd.coerceAtLeast(0)
            val selectedText = noteEditor.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteEditor.setText(noteEditor.text.toString().replace(selectedText, "![${selectedText}]()"))
                noteEditor.setSelection(noteEditor.text.toString().indexOf(selectedText) + selectedText.length + 2)
            } else {
                val markdown = "![caption](url)"
                try {
                    noteEditor.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), markdown, 0, markdown.length)
                } catch (_: Exception) {
                    noteEditor.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.lineButton).setOnClickListener {
            val start = noteEditor.selectionStart.coerceAtLeast(0)
            val end = noteEditor.selectionEnd.coerceAtLeast(0)
            val selectedText = noteEditor.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteEditor.setText(noteEditor.text.toString().replace(selectedText, "$selectedText\n****"))
                noteEditor.setSelection(noteEditor.text.toString().indexOf(selectedText) + selectedText.length)
            } else {
                val markdown = "\n****"
                try {
                    noteEditor.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), markdown, 0, markdown.length)
                } catch (_: Exception) {
                    noteEditor.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.quoteButton).setOnClickListener {
            val start = noteEditor.selectionStart.coerceAtLeast(0)
            val end = noteEditor.selectionEnd.coerceAtLeast(0)
            val selectedText = noteEditor.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteEditor.setText(noteEditor.text.toString().replace(selectedText, "> $selectedText"))
                noteEditor.setSelection(noteEditor.text.toString().indexOf(selectedText) + selectedText.length)
            } else {
                val markdown = "\n> "
                try {
                    noteEditor.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), "> ", 0, "> ".length)
                } catch (_: Exception) {
                    noteEditor.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.strikethroughButton).setOnClickListener {
            val start = noteEditor.selectionStart.coerceAtLeast(0)
            val end = noteEditor.selectionEnd.coerceAtLeast(0)
            val selectedText = noteEditor.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteEditor.setText(noteEditor.text.toString().replace(selectedText, "~~$selectedText~~"))
                noteEditor.setSelection(noteEditor.text.toString().indexOf(selectedText) + selectedText.length)
            } else {
                val markdown = "~~text~~"
                try {
                    noteEditor.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), markdown, 0, markdown.length)
                } catch (_: Exception) {
                    noteEditor.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.codeButton).setOnClickListener {
            val start = noteEditor.selectionStart.coerceAtLeast(0)
            val end = noteEditor.selectionEnd.coerceAtLeast(0)
            val selectedText = noteEditor.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteEditor.setText(noteEditor.text.toString().replace(selectedText, "\n```\n$selectedText\n```"))
                noteEditor.setSelection(noteEditor.text.toString().indexOf(selectedText) + selectedText.length)
            } else {
                val markdown = "```\ntext\n```"
                try {
                    noteEditor.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), "```\ntext\n```", 0, "```\ntext\n```".length)
                } catch (_: Exception) {
                    noteEditor.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.boldButton).setOnClickListener {
            val start = noteEditor.selectionStart.coerceAtLeast(0)
            val end = noteEditor.selectionEnd.coerceAtLeast(0)
            val selectedText = noteEditor.text.toString().substring(start, end)
            if (selectedText.trim().replace(" ", "").isNotEmpty()) {
                noteEditor.setText(noteEditor.text.toString().replace(selectedText, "**$selectedText**"))
                noteEditor.setSelection(noteEditor.text.toString().indexOf(selectedText) + selectedText.length)
            } else {
                val markdown = "**text**"
                try {
                    noteEditor.text.replace(start.coerceAtMost(end), start.coerceAtLeast(end), markdown, 0, markdown.length)
                } catch (_: Exception) {
                    noteEditor.text.append(markdown)
                }
            }
        }

        findViewById<ImageView>(R.id.titleButton).setOnClickListener {
            val start = noteEditor.selectionStart.coerceAtLeast(0)
            val end = noteEditor.selectionEnd.coerceAtLeast(0)
            val selectedText = noteEditor.text.toString().substring(start, end)
            if (selectedText.isNotEmpty()) noteEditor.setText(noteEditor.text.toString().replace(selectedText, utils.stringToTitledStrings(selectedText)))
            else noteEditor.append(utils.stringToTitledStrings(selectedText))
            noteEditor.setSelection(noteEditor.text.toString().length)
        }

        doneButton = findViewById (R.id.done)
        doneButton.setOnClickListener {
            if (noteEditor.text.isNullOrBlank() || noteEditor.text.toString().length <= 1) {
                val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this).create()
                alertDialog.setTitle("Blank Note")
                alertDialog.setMessage("Note can't be blank")
                alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Go back") { dialog, _ -> dialog.dismiss() }
                alertDialog.show()
            } else saveNote()

        }

        findViewById<TextView>(R.id.toolbarTitle).visibility = View.VISIBLE

        backButton = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            onBackPressed()
        }

        deleteButton = findViewById (R.id.delete)
        if (itemId != null) {
            deleteButton.setOnClickListener {
                val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this).create()
                alertDialog.setTitle("Delete")
                alertDialog.setMessage("Would you like to delete this note?")
                alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Delete") { dialog, _ ->

                    vault.note!!.remove(io.getNote(itemId!!, vault))
                    io.writeVault(vault)

                    network.writeQueueTask (itemId!!, mode = network.MODE_DELETE)
                    crypto.secureStartActivity (
                        nextActivity = Dashboard(),
                        nextActivityClassNameAsString = getString(R.string.title_activity_dashboard),
                        keyring = keyring,
                        itemId = null
                    )

                }
                alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Go back") { dialog, _ -> dialog.dismiss() }
                alertDialog.show()

            }
        } else {
            deleteButton.visibility = View.GONE
        }

        tagButton = findViewById (R.id.tag)
        tagPicker = AddTag (tagId, applicationContext, this@AddNote, keyring)

        tagButton.setOnClickListener {
            tagPicker.showPicker(tagId)
            tagIdGrabber.post(object : Runnable {
                override fun run() {
                    tagId = tagPicker.getSelectedTagId()
                    tagIdGrabber.postDelayed(this, 100)
                }
            })
        }

        favoriteButton = findViewById(R.id.favoriteButton)
        favoriteButton.setImageDrawable(ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_star_border_24))
        favoriteButton.setOnClickListener {
            favorite = if (!favorite) {
                favoriteButton.setImageDrawable (ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_star_24))
                favoriteButton.startAnimation(loadAnimation(applicationContext, R.anim.heartbeat))
                true
            } else {
                favoriteButton.setImageDrawable (ContextCompat.getDrawable(applicationContext, R.drawable.ic_baseline_star_border_24))
                false
            }
        }

        colorButton = findViewById(R.id.colorButton)
        colorButton.setOnClickListener {
            MaterialColorPickerDialog.Builder(this@AddNote)
                .setColors(resources.getStringArray(R.array.vault_item_colors))
                .setTickColorPerCard(true)
                .setDefaultColor(noteColor.toString())
                .setPositiveButton("Pick color")
                .setNegativeButton("Go back")
                .setColorListener(object : ColorListener {
                    override fun onColorSelected(color: Int, colorHex: String) {
                        noteColor = colorHex
                        noteEditor.setBackgroundColor(Color.parseColor(noteColor))
                        if (noteColor != null) {
                            val intColor: Int = noteColor!!.replace("#", "").toInt(16)
                            val r = intColor shr 16 and 0xFF; val g = intColor shr 8 and 0xFF; val b = intColor shr 0 and 0xFF
                            if (g >= 200 || b >= 200) {
                                noteEditor.setTextColor (Color.BLACK)
                                noteEditor.setHintTextColor(Color.BLACK)
                            } else {
                                noteEditor.setTextColor(Color.WHITE)
                                noteEditor.setHintTextColor(Color.WHITE)
                            }
                        }
                    }
                })
                .show()
        }

        previewButton = findViewById(R.id.previewButton)
        notePreview = findViewById(R.id.notePreview)

        previewButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_visibility_24))
        noteToolbar.visibility = View.VISIBLE
        notePreview.visibility = View.GONE

        previewButton.setOnClickListener {
            notePreview.setText (noteData)
            preview = if (preview) {

                var intColor: Int

                try {
                    notePreview.setBackgroundColor(Color.parseColor(noteColor))
                    intColor = noteColor!!.replace("#", "").toInt(16)
                } catch (_: Exception) {
                    when (applicationContext.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)) {
                        Configuration.UI_MODE_NIGHT_YES -> {
                            notePreview.setBackgroundColor(Color.BLACK)
                            intColor = Color.BLACK
                        }
                        Configuration.UI_MODE_NIGHT_NO or Configuration.COLOR_MODE_HDR_UNDEFINED -> {
                            notePreview.setBackgroundColor(Color.WHITE)
                            intColor = Color.WHITE
                        }
                        else -> {
                            notePreview.setBackgroundColor(Color.WHITE)
                            intColor = Color.WHITE
                        }
                    }
                }

                val r = intColor shr 16 and 0xFF; val g = intColor shr 8 and 0xFF; val b = intColor shr 0 and 0xFF
                if (g >= 200 || b >= 200) {
                    notePreview.setTextColor (Color.BLACK)
                } else {
                    notePreview.setTextColor(Color.WHITE)
                }

                previewButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_edit_24))
                noteToolbar.visibility = View.GONE
                noteEditor.visibility = View.GONE
                notePreview.visibility = View.VISIBLE
                previewMarkdownProcessor.live(notePreview)
                false
            } else {
                previewButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_visibility_24))
                noteToolbar.visibility = View.VISIBLE
                noteEditor.visibility = View.VISIBLE
                notePreview.visibility = View.GONE
                true
            }
        }

        input = this.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

        notePreview.setOnClickListener {
            val location = IntArray(2)
            notePreview.getLocationOnScreen(location)
            previewButton.performClick()
            noteEditor.setSelection(noteEditor.text.length)
            noteEditor.requestFocusFromTouch()
            Handler().postDelayed({
                input.showSoftInput(noteEditor, 0)
            }, 250)
        }

        return true
    }

    private fun loadNote (note: IOUtilities.Note): Boolean {

        favorite = if (note.favorite) {
            favoriteButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_star_24)); true
        } else {
            favoriteButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_star_border_24)); false
        }

        tagId = note.tagId
        tagPicker = AddTag (tagId, applicationContext, this@AddNote, keyring)

        dateAndTime.visibility = View.VISIBLE

        previousTimestamp = note.dateModified!!

        val time = Calendar.getInstance(Locale.ENGLISH)
        time.timeInMillis = previousTimestamp?.times(1000L)!!
        dateAndTime.text = "Last edited on " + DateFormat.format("MMM dd, yyyy ⋅  hh:mm a", time).toString()

        if (!note.notes.isNullOrEmpty()) {
            noteEditor.setText (note.notes)
        }

        if (!note.color.isNullOrEmpty()) {
            noteColor = note.color
            noteEditor.setBackgroundColor(Color.parseColor(noteColor))
            val intColor: Int = noteColor!!.replace("#", "").toInt(16)
            val r = intColor shr 16 and 0xFF; val g = intColor shr 8 and 0xFF; val b = intColor shr 0 and 0xFF
            if (g >= 200 || b >= 200) {
                noteEditor.setTextColor (Color.BLACK)
                noteEditor.setHintTextColor(Color.BLACK)
            } else {
                noteEditor.setTextColor(Color.WHITE)
                noteEditor.setHintTextColor(Color.WHITE)
            }

        }

        if (configData.getBoolean("notesPreview", false)) {
            previewButton.setImageDrawable(getDrawable(R.drawable.ic_baseline_edit_24))
            noteToolbar.visibility = View.VISIBLE
            notePreview.visibility = View.GONE
            noteEditor.visibility = View.VISIBLE
            noteEditor.setSelection(noteEditor.text.length)
            noteEditor.requestFocusFromTouch()
            Handler().postDelayed({
                input.showSoftInput(noteEditor, 0)
            }, 250)
        } else {
            previewButton.performClick()
        }

        return true
    }

    private fun saveNote () {
        var dateCreated = Instant.now().epochSecond

        if (itemId != null) {
            dateCreated = note.dateCreated!!
            vault.note?.remove(io.getNote(itemId!!, vault))
        }

        val data = IOUtilities.Note (
            id = itemId ?: UUID.randomUUID().toString(),
            organizationId = null,
            type = io.TYPE_NOTE,
            notes = noteEditor.text.toString(),
            color = noteColor,
            favorite = favorite,
            tagId = tagPicker.getSelectedTagId() ?: tagId,
            dateCreated = dateCreated,
            dateModified = timestamp,
            frequencyAccessed = frequencyAccessed
        )

        val encryptedNote = io.encryptNote(data)

        vault.note?.add (encryptedNote)
        io.writeVault(vault)

        if (itemId != null) network.writeQueueTask (encryptedNote, mode = network.MODE_PUT)
        else network.writeQueueTask (encryptedNote, mode = network.MODE_POST)

        crypto.secureStartActivity (
            nextActivity = Dashboard(),
            nextActivityClassNameAsString = getString(R.string.title_activity_dashboard),
            keyring = keyring,
            itemId = null
        )

    }

    override fun onBackPressed () {
        val alertDialog: AlertDialog = MaterialAlertDialogBuilder(this).create()
        alertDialog.setTitle("Confirm exit")
        alertDialog.setMessage("Would you like to go back to the Dashboard?")
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Exit") { dialog, _ ->
            crypto.secureStartActivity (
                nextActivity = Dashboard(),
                nextActivityClassNameAsString = getString(R.string.title_activity_dashboard),
                keyring = keyring,
                itemId = null
            )
            super.onBackPressed()
            tagIdGrabber.removeCallbacksAndMessages(null)
        }
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel") { dialog, _ -> dialog.dismiss() }
        alertDialog.show()
    }

    override fun onUserLeaveHint() {
        if (configData.getBoolean("lockApp", true)) {
            finish()
            finishAffinity()
        }
        super.onUserLeaveHint()

    }
}