package com.mybook.sanctify.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.util.UUID

/** Файлы вложений записей (фото, видео, документы) — filesDir/media. */
object Media {
    /** Адрес, по которому WebView-редактор видит файлы (см. WebViewAssetLoader). */
    const val HOST = "https://appassets.androidplatform.net"

    class Copied(val file: File, val name: String, val mime: String)

    fun dir(ctx: Context): File = File(ctx.filesDir, "media").apply { mkdirs() }
    fun exportDir(ctx: Context): File = File(ctx.cacheDir, "export").apply { mkdirs() }
    fun url(fileName: String) = "$HOST/media/$fileName"

    /** Документы, которые можно открыть в редакторе. */
    val editableExt = setOf("docx", "txt", "md", "html", "htm")

    fun displayName(ctx: Context, uri: Uri): String? =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun extensionOf(name: String?, mime: String?): String {
        val fromName = name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.length in 1..5 && it.all { c -> c.isLetterOrDigit() } }
        if (fromName != null) return fromName
        return mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "bin"
    }

    fun kindFor(mime: String, name: String): String {
        val ext = extensionOf(name, mime)
        return when {
            mime.startsWith("image/") -> "photo"
            mime.startsWith("video/") -> "video"
            ext in editableExt -> "doc"
            else -> "file"
        }
    }

    /** Копирует выбранный пользователем файл в хранилище приложения. */
    fun copy(ctx: Context, uri: Uri): Copied {
        val name = displayName(ctx, uri) ?: "Файл"
        val mime = ctx.contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extensionOf(name, null)) ?: "application/octet-stream"
        val file = File(dir(ctx), "${UUID.randomUUID()}.${extensionOf(name, mime)}")
        ctx.contentResolver.openInputStream(uri)?.use { i -> file.outputStream().use { o -> i.copyTo(o) } }
            ?: error("Не удалось открыть файл")
        return Copied(file, name, mime)
    }

    /** Сохраняет картинку из base64 (импорт из Word) и возвращает адрес для редактора. */
    fun saveBase64Image(ctx: Context, b64: String, mime: String): String {
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "png"
        val f = File(dir(ctx), "img_${UUID.randomUUID()}.$ext")
        f.writeBytes(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
        return url(f.name)
    }

    /** Копирует выбранную картинку для вставки в текст и возвращает адрес для редактора. */
    fun importImage(ctx: Context, uri: Uri): String {
        val c = copy(ctx, uri)
        val renamed = File(c.file.parentFile, "img_${c.file.name}")
        c.file.renameTo(renamed)
        return url(renamed.name)
    }
}
