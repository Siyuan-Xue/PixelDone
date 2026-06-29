package com.milesxue.pixeldone

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

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

    private companion object {
        const val ImageDirectoryName = "todo_images"
    }
}
