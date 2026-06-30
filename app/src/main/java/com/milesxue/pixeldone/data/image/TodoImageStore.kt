package com.milesxue.pixeldone.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

internal const val PreviewMaxBitmapLongEdgePx = 2_048

/**
 * Todo 图片附件的数据层边界。
 *
 * 教学说明：UI 只知道“给某个任务附上一张图”，不应该关心文件名、安全路径或 bitmap 采样。
 * 这里把外部 Uri 复制到 app 私有目录，并在读取预览时按长边采样，避免大图直接解码造成内存压力。
 */
class TodoImageStore(context: Context) {
    private val appContext = context.applicationContext
    private val imageDirectory = File(appContext.filesDir, ImageDirectoryName)

    fun copyImage(uri: Uri): String? {
        val fileName = "${UUID.randomUUID()}.img"
        return runCatching {
            imageDirectory.mkdirs()
            val targetFile = imageFile(fileName) ?: return null
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            fileName
        }.getOrElse {
            deleteImage(fileName)
            null
        }
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
    }
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
