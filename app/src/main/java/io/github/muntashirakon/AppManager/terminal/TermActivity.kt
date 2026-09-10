// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.terminal

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.PowerManager
import android.text.Editable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.KeyEvent
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.MainThread
import androidx.annotation.UiThread
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.color.MaterialColors
import io.github.muntashirakon.AppManager.BaseActivity
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.compat.ProcessCompat
import io.github.muntashirakon.AppManager.logs.Log
import io.github.muntashirakon.AppManager.utils.CpuUtils
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// TODO: 11/9/23 Replace it with an actual terminal
class TermActivity : BaseActivity() {
    private val mLock = Any()
    private lateinit var mCommandInput: AppCompatEditText
    private lateinit var mCommandOutput: AppCompatTextView
    private var mProc: Process? = null
    private var mProcessOutputStream: OutputStream? = null
    private var mWakeLock: PowerManager.WakeLock? = null
    private val mExecutor: ExecutorService = Executors.newFixedThreadPool(3)
    private var mDefaultForegroundColor = 0
    private var mDefaultBackgroundColor = 0
    private val mAnsiState = AnsiState()

    override fun onAuthenticated(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_term)
        setSupportActionBar(findViewById(R.id.toolbar))
        mCommandInput = findViewById(R.id.command_input)
        mCommandOutput = findViewById(R.id.command_output)
        mCommandOutput.setText("", TextView.BufferType.EDITABLE)
        mDefaultForegroundColor = mCommandInput.currentTextColor
        mDefaultBackgroundColor = MaterialColors.getColor(mCommandInput, com.google.android.material.R.attr.colorSurface)
        mCommandInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val command = mCommandInput.text.toString()
                appendBoldOutput(command)
                appendOutput("\n")
                mAnsiState.homePosition = mCommandOutput.editableText.length
                sendToStdin(command, true)
            } else false
        }
        mCommandInput.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

            // TAB
            if (keyCode == KeyEvent.KEYCODE_TAB) {
                // TODO: 7/21/25 Support minimal completion
            }

            // Arrow Keys (UP/DOWN/PGUP/PGDOWN)
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_PAGE_UP,
                KeyEvent.KEYCODE_PAGE_DOWN -> {
                    // TODO: 7/21/25 Support history
                }
            }

            // CTRL + KEY
            if (event.isCtrlPressed) {
                val c = event.unicodeChar
                val ctrlChar = when {
                    c in 'a'.code..'z'.code -> (c - 'a'.code + 1).toChar()
                    c in 'A'.code..'Z'.code -> (c - 'A'.code + 1).toChar()
                    else -> return@setOnKeyListener false
                }
                appendBoldOutput("^${(c + 'A'.code).toChar()}")
                appendOutput("\n")
                sendToStdin(ctrlChar.toString(), true)
            }
            false
        }
        initShell()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        CpuUtils.releaseWakeLock(mWakeLock)
        mExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun initShell() {
        mWakeLock = CpuUtils.getPartialWakeLock("term")
        mWakeLock!!.acquire()
        mExecutor.submit {
            try {
                mProc = ProcessCompat.exec(arrayOf("sh", "-i"), arrayOf("TERM=xterm-256color", "HOME=/"))
                mProcessOutputStream = BufferedOutputStream(mProc!!.outputStream)
                mExecutor.submit {
                    try {
                        mProc!!.inputStream.use { readStream(it) }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
                mExecutor.submit {
                    try {
                        mProc!!.errorStream.use { readStream(it) }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
                // TODO: 7/21/25 Support init script
                mProc!!.waitFor()
                runOnUiThread { finishAndRemoveTask() }
            } catch (e: Throwable) {
                e.printStackTrace()
                // TODO: 23/1/23 Handle error
            }
        }
    }

    private fun readStream(inputStream: InputStream) {
        val buffer = ByteArray(1024)
        var len: Int
        while (inputStream.read(buffer).also { len = it } != -1) {
            synchronized(mLock) {
                val chunk = String(buffer, 0, len, StandardCharsets.UTF_8)
                runOnUiThread { processChunk(chunk, mAnsiState) }
            }
        }
    }

    internal class AnsiState {
        @ColorInt var foreground: Int = -1
        @ColorInt var background: Int = -1
        var bold: Boolean = false
        var underline: Boolean = false
        var italic: Boolean = false
        var strike: Boolean = false
        var reverse: Boolean = false
        var hide: Boolean = false
        var savedPosition: Int = -1
        var navigateToHome: Boolean = false
        var homePosition: Int = 0

        fun reset() {
            foreground = -1
            background = -1
            bold = false
            underline = false
            italic = false
            strike = false
            reverse = false
            hide = false
            savedPosition = -1
            navigateToHome = false
        }
    }

    @UiThread
    private fun processChunk(chunk: String, state: AnsiState) {
        val plainTextBuffer = StringBuilder()
        var i = 0
        while (i < chunk.length) {
            val ch = chunk[i]
            if (ch == '') {
                if (plainTextBuffer.isNotEmpty()) {
                    appendOutput(plainTextBuffer.toString(), state)
                    plainTextBuffer.setLength(0)
                }
                ++i
                if (i >= chunk.length) break
                val escChar = chunk[i]
                if (escChar == '[') {
                    val numberBuilder = StringBuilder()
                    numberBuilder.append(escChar)
                    ++i
                    while (i < chunk.length) {
                        val c = chunk[i]
                        if (c in '0'..'9' || c == ';' || c == '?') {
                            numberBuilder.append(c)
                        } else {
                            numberBuilder.append(c)
                            processAnsiSeq(numberBuilder.toString(), state)
                            break
                        }
                        ++i
                    }
                } else if (escChar == 'M') {
                    processAnsiSeq("M", state)
                }
            } else {
                plainTextBuffer.append(ch)
            }
            ++i
        }
        if (plainTextBuffer.isNotEmpty()) {
            appendOutput(plainTextBuffer.toString(), state)
        }
    }

    @MainThread
    internal fun processAnsiSeq(seq: String, state: AnsiState) {
        when {
            seq.matches(Regex("\\[[0-2]?J")) -> {
                val code = seq.substring(1, seq.length - 1)
                when (code) {
                    "2" -> resetOutput(state)
                    "1" -> Log.i(TAG, "Unhandled escape sequence: $seq")
                    else -> if (state.navigateToHome) resetOutputToPosition(state.homePosition)
                }
            }
            seq == "[H" || seq == "[;H" || seq == "[f" -> state.navigateToHome = true
            seq == "[2K" -> clearLastLine()
            seq == "[s" -> state.savedPosition = mCommandOutput.editableText.length
            seq == "[u" -> {
                val editable = mCommandOutput.editableText
                if (state.savedPosition in 0..editable.length) {
                    editable.delete(state.savedPosition, editable.length)
                }
            }
            seq.matches(Regex("\\[[0-9;]*m")) -> {
                val params = seq.substring(1, seq.length - 1)
                for (code in params.split(";")) {
                    val value = code.toIntOrNull() ?: if (code.isEmpty()) 0 else continue
                    when (value) {
                        0 -> state.reset()
                        1 -> state.bold = true
                        22 -> state.bold = false
                        3 -> state.italic = true
                        23 -> state.italic = false
                        4 -> state.underline = true
                        24 -> state.underline = false
                        7 -> state.reverse = true
                        27 -> state.reverse = false
                        8 -> state.hide = true
                        28 -> state.hide = false
                        9 -> state.strike = true
                        29 -> state.strike = false
                        39 -> state.foreground = -1
                        30 -> state.foreground = Color.BLACK
                        31 -> state.foreground = Color.RED
                        32 -> state.foreground = Color.GREEN
                        33 -> state.foreground = Color.YELLOW
                        34 -> state.foreground = Color.BLUE
                        35 -> state.foreground = Color.MAGENTA
                        36 -> state.foreground = Color.CYAN
                        37 -> state.foreground = Color.WHITE
                        49 -> state.background = -1
                        40 -> state.background = Color.BLACK
                        41 -> state.background = Color.RED
                        42 -> state.background = Color.GREEN
                        43 -> state.background = Color.YELLOW
                        44 -> state.background = Color.BLUE
                        45 -> state.background = Color.MAGENTA
                        46 -> state.background = Color.CYAN
                        47 -> state.background = Color.WHITE
                        else -> Log.i(TAG, "Unhandled escape sequence: $seq")
                    }
                }
            }
            else -> Log.i(TAG, "Unhandled escape sequence: $seq")
        }
    }

    @MainThread
    private fun resetOutput(state: AnsiState) {
        mCommandOutput.editableText.clear()
        state.homePosition = 0
    }

    @MainThread
    private fun resetOutputToPosition(position: Int) {
        val editable: Editable = mCommandOutput.editableText
        if (position <= 0) {
            editable.clear()
        } else {
            val length = editable.length
            if (position < length) editable.delete(position, length)
        }
    }

    private fun clearLastLine() {
        val editable: Editable = mCommandOutput.editableText
        val length = editable.length
        if (length > 0) {
            val lastNewline = editable.toString().lastIndexOf('\n', length - 2)
            resetOutputToPosition(lastNewline + 1)
        }
    }

    @MainThread
    private fun appendOutput(text: String) {
        mCommandOutput.editableText.append(text)
    }

    @MainThread
    private fun appendOutput(text: String, state: AnsiState) {
        state.navigateToHome = false
        val displayText = if (state.hide) " ".repeat(text.length) else text
        val editable: Editable = mCommandOutput.editableText
        val start = editable.length
        editable.append(displayText)
        val end = editable.length
        if (start == end) return
        if (state.bold) editable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (state.italic) editable.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (state.underline) editable.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (state.strike) editable.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val foregroundColor = if (state.foreground != -1) state.foreground else mDefaultForegroundColor
        val backgroundColor = if (state.background != -1) state.background else mDefaultBackgroundColor
        editable.setSpan(ForegroundColorSpan(if (state.reverse) backgroundColor else foregroundColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        editable.setSpan(BackgroundColorSpan(if (state.reverse) foregroundColor else backgroundColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    @MainThread
    private fun appendBoldOutput(boldText: String) {
        val editable: Editable = mCommandOutput.editableText
        val start = editable.length
        editable.append(boldText)
        editable.setSpan(StyleSpan(Typeface.BOLD), start, editable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun sendToStdin(command: String, newLine: Boolean): Boolean {
        val os = mProcessOutputStream ?: return true
        val proc = mProc ?: return true
        if (!ProcessCompat.isAlive(proc)) return false
        try {
            os.write(command.toByteArray(StandardCharsets.UTF_8))
            if (newLine) os.write("\n".toByteArray(StandardCharsets.UTF_8))
            os.flush()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return true
    }

    companion object {
        const val TAG = "TermActivity"
    }
}
