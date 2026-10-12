package com.github.andreyasadchy.xtra.ui.common

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import com.github.andreyasadchy.xtra.BuildConfig
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogUpdateAvailableBinding
import com.github.andreyasadchy.xtra.model.ui.UpdateInfo
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import io.noties.markwon.Markwon
import io.noties.markwon.linkify.LinkifyPlugin
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object UpdateAvailableDialog {
    fun show(context: Context, inflater: LayoutInflater, updateInfo: UpdateInfo,
        onDownload: () -> Unit, onLater: () -> Unit, onGithub: (String) -> Unit): AlertDialog {
        val binding = DialogUpdateAvailableBinding.inflate(inflater)
        bindRelease(context, binding, updateInfo)
        val dialog = context.getAlertDialogBuilder().setView(binding.root).create()
        UpdateDialogWindow.applyMotion(dialog)
        binding.downloadButton.setOnClickListener { dialog.dismiss(); onDownload() }
        binding.laterButton.setOnClickListener { dialog.dismiss(); onLater() }
        binding.githubButton.setOnClickListener { updateInfo.releaseUrl?.let { dialog.dismiss(); onGithub(it) } }
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0.4f)
                UpdateDialogWindow.attach(dialog) { binding.updateSheetContent.maxAvailableHeight = it }
            }
        }
        dialog.show()
        return dialog
    }

    internal fun bindRelease(context: Context, binding: DialogUpdateAvailableBinding, info: UpdateInfo) {
        ViewCompat.setAccessibilityHeading(binding.updateTitle, true)
        ViewCompat.setAccessibilityHeading(binding.updateNotesTitle, true)
        val current = displayVersion(BuildConfig.VERSION_NAME)
        val next = displayVersion(info.tagName)
        binding.updateCurrentVersion.text = current
        binding.updateVersionChip.text = next
        binding.updateVersionRow.contentDescription = context.getString(R.string.update_version_change, current, next)
        binding.updateDate.text = releaseDate(info.publishedAt)
        binding.updateDate.isVisible = binding.updateDate.text.isNotBlank()
        binding.githubButton.isVisible = !info.releaseUrl.isNullOrBlank()
        // Web links only: the default phone matcher underlines commit hashes and dates.
        Markwon.builder(context).usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS)).build()
            .setMarkdown(binding.releaseNotesText, releaseNotesMarkdown(info).ifBlank { context.getString(R.string.no_release_notes) })
        binding.releaseNotesText.movementMethod = LinkMovementMethod.getInstance()
    }

    /** Release tags carry a leading "v"; the pills read better as bare versions. */
    internal fun displayVersion(value: String): String = value.trim().removePrefix("v").removePrefix("V")

    internal fun releaseDate(value: String?): String? = try {
        val date = value?.substringBefore('T')?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        date?.let {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false; timeZone = TimeZone.getTimeZone("UTC") }
            parser.parse(it)?.let { parsed -> DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(parsed) }
        }
    } catch (_: java.text.ParseException) { null }

    internal fun releaseNotesMarkdown(info: UpdateInfo): String =
        releaseNotesMarkdown(info.releaseNotes, info.tagName, info.versionName, info.title)

    /** Drops a leading heading that repeats the release name, which the UI already shows. */
    internal fun releaseNotesMarkdown(notes: String?, vararg names: String?): String {
        val lines = notes.orEmpty().trim().lines()
        val first = lines.firstOrNull()?.trim().orEmpty()
        val duplicate = first.startsWith("#") && names.filterNotNull()
            .filter { it.isNotBlank() }.any { first.contains(it, ignoreCase = true) }
        return demoteHeadings((if (duplicate) lines.drop(1) else lines).joinToString("\n").trim())
    }

    /** Release notes use #, ## and ### for structure; inside a card those read as page titles, so they become bold body-size headings. */
    internal fun demoteHeadings(markdown: String): String {
        var fenced = false
        return markdown.lines().joinToString("\n") { line ->
            if (line.trimStart().startsWith("```")) fenced = !fenced
            if (fenced) line else HEADING.replace(line, "####$1")
        }
    }

    private val HEADING = Regex("^#{1,3}(\\s)")
}
