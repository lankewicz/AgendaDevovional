package com.agendadevocional.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AudioDownloadManager(private val context: Context) {

    // Configurable base URL for Cloudflare R2 audio hosting
    val r2BaseUrl = "https://pub-948671b7cec62fec60e5285ca2f5f977.r2.dev/"

    fun getAudioDir(): File {
        val dir = File(context.filesDir, "audio_plan")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getLocalFile(relPath: String): File {
        // Flatten path to avoid subfolder creation issues, or create folders
        val file = File(getAudioDir(), relPath.replace("/", "_"))
        return file
    }

    fun isDownloaded(relPath: String): Boolean {
        val file = getLocalFile(relPath)
        return file.exists() && file.length() > 0
    }

    suspend fun downloadAudio(relPath: String, onProgress: (Float) -> Unit = {}): File? = withContext(Dispatchers.IO) {
        val localFile = getLocalFile(relPath)
        if (localFile.exists() && localFile.length() > 0) {
            onProgress(1f)
            return@withContext localFile
        }

        val urlString = "$r2BaseUrl$relPath"
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            val fileLength = connection.contentLength
            val input = connection.inputStream
            val output = FileOutputStream(localFile)

            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int
            while (input.read(data).also { count = it } != -1) {
                total += count
                if (fileLength > 0) {
                    onProgress(total.toFloat() / fileLength.toFloat())
                }
                output.write(data, 0, count)
            }

            output.flush()
            output.close()
            input.close()
            return@withContext localFile
        } catch (e: Exception) {
            e.printStackTrace()
            if (localFile.exists()) {
                localFile.delete()
            }
            return@withContext null
        } finally {
            connection?.disconnect()
        }
    }

    fun deleteAudio(relPath: String) {
        val file = getLocalFile(relPath)
        if (file.exists()) {
            file.delete()
        }
    }

    fun clearAllDownloads() {
        val dir = getAudioDir()
        dir.deleteRecursively()
    }
}
