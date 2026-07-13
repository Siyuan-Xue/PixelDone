package com.milesxue.pixeldone.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal const val PreviewMaxBitmapLongEdgePx = 2_048
internal const val TodoImageMaxBytes = 10L * 1_024L * 1_024L

data class TodoImageMetadata(
    val fileName: String,
    val contentSha256: String,
    val contentType: String,
    val byteSize: Long,
)

/**
 * Data boundary for todo image attachments.
 *
 * The UI asks to attach an image to a task. File names, path safety, and bitmap sampling remain here.
 * External URIs are copied into app-private storage, and previews are sampled by long edge.
 */
class TodoImageStore(context: Context) {
    private val appContext = context.applicationContext
    private val imageDirectory = File(appContext.filesDir, ImageDirectoryName)

    fun copyImage(uri: Uri): String? {
        val temporaryName = "${UUID.randomUUID()}.pending"
        return runCatching {
            imageDirectory.mkdirs()
            val temporaryFile = imageFile(temporaryName) ?: return null
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                temporaryFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= TodoImageMaxBytes) { "Image exceeds 10 MiB." }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return null
            val contentType = detectContentType(temporaryFile)
                ?: error("Only JPEG, PNG, and WebP images are supported.")
            require(hasDecodableBounds(temporaryFile)) { "The selected image is invalid." }
            val fileName = "${UUID.randomUUID()}.${contentType.extension()}"
            val targetFile = imageFile(fileName) ?: error("Invalid image file name.")
            if (!temporaryFile.renameTo(targetFile)) {
                temporaryFile.copyTo(targetFile, overwrite = false)
                temporaryFile.delete()
            }
            fileName
        }.getOrElse {
            deleteImage(temporaryName)
            null
        }
    }

    fun inspectImage(fileName: String?): TodoImageMetadata? {
        val file = imageFile(fileName)?.takeIf { it.isFile } ?: return null
        if (file.length() !in 1..TodoImageMaxBytes) return null
        val contentType = detectContentType(file) ?: return null
        if (!hasDecodableBounds(file)) return null
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return TodoImageMetadata(
            fileName = file.name,
            contentSha256 = digest.digest().joinToString("") { "%02x".format(it) },
            contentType = contentType,
            byteSize = file.length(),
        )
    }

    fun readImageBytes(fileName: String?): ByteArray? {
        val metadata = inspectImage(fileName) ?: return null
        return imageFile(metadata.fileName)?.readBytes()
    }

    fun cacheRemoteImage(
        todoLocalId: String,
        attachmentId: String,
        expectedSha256: String,
        contentType: String,
        expectedByteSize: Long,
        bytes: ByteArray,
    ): String? = runCatching {
        require(expectedByteSize in 1..TodoImageMaxBytes && bytes.size.toLong() == expectedByteSize)
        require(contentType in SupportedContentTypes)
        require(detectContentType(bytes) == contentType)
        val actualHash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        require(actualHash == expectedSha256)
        imageDirectory.mkdirs()
        val fileName = remoteCacheFileName(todoLocalId, attachmentId, expectedSha256, contentType)
        val target = imageFile(fileName) ?: error("Invalid cache file name.")
        val temporary = imageFile("$fileName.pending") ?: error("Invalid cache file name.")
        temporary.writeBytes(bytes)
        if (!hasDecodableBounds(temporary)) {
            temporary.delete()
            error("Downloaded image is invalid.")
        }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        fileName
    }.getOrNull()

    fun remoteCacheFileName(
        todoLocalId: String,
        attachmentId: String,
        contentSha256: String,
        contentType: String,
    ): String {
        require(contentType in SupportedContentTypes)
        val safeTodoId = todoLocalId.replace(UnsafeFileCharacters, "_").take(80)
        val safeAttachmentId = attachmentId.replace(UnsafeFileCharacters, "_").take(80)
        return "remote_${safeTodoId}_${safeAttachmentId}_${contentSha256.take(12)}.${contentType.extension()}"
    }

    fun imageFile(fileName: String?): File? {
        val normalizedFileName = fileName?.takeIf { it.isNotBlank() } ?: return null
        val directory = imageDirectory.canonicalFile
        val file = File(directory, normalizedFileName).canonicalFile
        val directoryPath = directory.path + File.separator
        return file.takeIf { it.path.startsWith(directoryPath) }
    }

    fun deleteImage(fileName: String?) {
        val file = imageFile(fileName) ?: return
        if (file.isFile) {
            file.delete()
        }
    }

    fun loadPreviewBitmap(
        fileName: String?,
        maxLongEdgePx: Int = PreviewMaxBitmapLongEdgePx,
    ): Bitmap? {
        val file = imageFile(fileName)?.takeIf { it.isFile } ?: return null
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return null
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculatePreviewSampleSize(
                    imageWidth = boundsOptions.outWidth,
                    imageHeight = boundsOptions.outHeight,
                    maxLongEdgePx = maxLongEdgePx,
                )
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val ImageDirectoryName = "todo_images"
        val SupportedContentTypes = setOf("image/jpeg", "image/png", "image/webp")
        val UnsafeFileCharacters = Regex("[^A-Za-z0-9._-]")
    }
}

private fun detectContentType(file: File): String? = file.inputStream().buffered().use { input ->
    val header = ByteArray(12)
    val count = input.read(header)
    detectContentType(header.copyOf(count.coerceAtLeast(0)))
}

private fun hasDecodableBounds(file: File): Boolean {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    return options.outWidth > 0 && options.outHeight > 0
}

private fun detectContentType(bytes: ByteArray): String? = when {
    bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() ->
        "image/jpeg"
    bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
        byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
    ) -> "image/png"
    bytes.size >= 12 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
        bytes.copyOfRange(8, 12).decodeToString() == "WEBP" -> "image/webp"
    else -> null
}

private fun String.extension(): String = when (this) {
    "image/jpeg" -> "jpg"
    "image/png" -> "png"
    "image/webp" -> "webp"
    else -> error("Unsupported image type.")
}

internal fun calculatePreviewSampleSize(
    imageWidth: Int,
    imageHeight: Int,
    maxLongEdgePx: Int = PreviewMaxBitmapLongEdgePx,
): Int {
    if (imageWidth <= 0 || imageHeight <= 0 || maxLongEdgePx <= 0) return 1

    val longestEdge = maxOf(imageWidth, imageHeight)
    var sampleSize = 1
    while (longestEdge / sampleSize > maxLongEdgePx) {
        sampleSize *= 2
    }
    return sampleSize
}
