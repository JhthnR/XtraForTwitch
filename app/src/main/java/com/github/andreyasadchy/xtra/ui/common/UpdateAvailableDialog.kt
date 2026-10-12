package com.github.andreyasadchy.xtra.ui.common

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.method.LinkMovementMethod
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
        Markwon.builder(context).usePlugin(LinkifyPlugin.create()).build()
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

    internal fun releaseNotesMarkdown(info: UpdateInfo): String {
        val lines = info.releaseNotes.orEmpty().trim().lines()
        val first = lines.firstOrNull()?.trim().orEmpty()
        val duplicate = first.startsWith("#") && listOfNotNull(info.tagName, info.versionName, info.title)
            .filter { it.isNotBlank() }.any { first.contains(it, ignoreCase = true) }
        return (if (duplicate) lines.drop(1) else lines).joinToString("\n").trim()
    }
}
