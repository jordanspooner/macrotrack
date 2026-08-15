package com.macrotrack.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodSourceDownloader @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun download(url: String, destFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw RuntimeException("Download failed: ${response.code}")
            }
            val body = response.body ?: throw RuntimeException("Empty response body")
            FileOutputStream(destFile).use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
                Unit
            }
        }
    }

    suspend fun gunzip(gzFile: File, outFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            GZIPInputStream(gzFile.inputStream()).use { gz ->
                FileOutputStream(outFile).use { out ->
                    gz.copyTo(out)
                }
                Unit
            }
        }
    }

    suspend fun verifySha256(file: File, expected: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) } == expected
        }.getOrDefault(false)
    }
}
