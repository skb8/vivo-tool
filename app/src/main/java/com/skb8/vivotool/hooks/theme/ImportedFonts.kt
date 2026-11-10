package com.skb8.vivotool.hooks.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object ImportedFonts {
    private val idPattern = Regex("6F[0-9A-F]{12}")
    private val controls = Regex("[\\p{Cc}\\p{Cf}]")
    private const val MAX_BYTES = 32L * 1024 * 1024

    data class Font(val id: String, val name: String, val archive: File)

    private fun directory(context: Context) = File(context.filesDir, "vivotool-fonts")

    fun find(context: Context, id: String): Font? {
        if (!idPattern.matches(id)) return null
        val folder = File(directory(context), id)
        val archive = File(folder, "font.zip")
        val nameFile = File(folder, "name")
        if (!archive.isFile || !nameFile.isFile || nameFile.length() !in 1L..1024L) return null
        return runCatching { Font(id, nameFile.readText(), archive) }.getOrNull()
    }

    fun list(context: Context): List<Font> =
        directory(context).listFiles().orEmpty()
            .sortedByDescending { it.lastModified() }
            .mapNotNull { find(context, it.name) }

    fun importFont(context: Context, uri: Uri, unnamed: String) {
        val resolver = context.contentResolver
        val fileName = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull().orEmpty()
        val name = fileName.replace(Regex("\\.(ttf|otf)$", RegexOption.IGNORE_CASE), "")
            .replace(controls, " ").trim().take(96).ifBlank { unnamed }

        val root = directory(context)
        check(root.isDirectory || root.mkdirs())
        val token = UUID.randomUUID().toString().replace("-", "")
        val id = "6F" + token.take(12).uppercase(Locale.ROOT)
        val pending = File(root, ".import-$token")
        check(pending.mkdir())
        try {
            val raw = File(pending, "input.ttf")
            checkNotNull(resolver.openInputStream(uri)).use { input ->
                raw.outputStream().buffered().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var size = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        require(size <= MAX_BYTES) { "Font exceeds 32 MiB" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            val signature = raw.inputStream().buffered().use {
                java.io.DataInputStream(it).readInt()
            }
            require(signature == 0x00010000 || signature == 0x4f54544f) { "Unsupported font format" }
            require(Typeface.createFromFile(raw) != Typeface.DEFAULT) { "Invalid font" }

            // The native font installer extracts this entry into its own system-readable directory.
            ZipOutputStream(File(pending, "font.zip").outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("$id.ttf"))
                raw.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            check(raw.delete())
            File(pending, "name").writeText(name)
            val destination = File(root, id)
            check(!destination.exists() && pending.renameTo(destination))
        } finally {
            if (pending.exists()) pending.deleteRecursively()
        }
    }
}
