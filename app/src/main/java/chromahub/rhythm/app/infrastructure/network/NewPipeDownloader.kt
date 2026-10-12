/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.network

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class NewPipeDownloader private constructor(private val client: OkHttpClient) : Downloader() {

    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder().url(url)

        var hasUserAgent = false
        headers?.forEach { (name, values) ->
            values.forEach { value ->
                if (name.equals("User-Agent", ignoreCase = true)) {
                    hasUserAgent = true
                }
                requestBuilder.addHeader(name, value)
            }
        }

        if (!hasUserAgent) {
            requestBuilder.header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            )
        }

        val requestBody = dataToSend?.toRequestBody(null)
        when (httpMethod.uppercase()) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post(requestBody ?: ByteArray(0).toRequestBody(null))
            "HEAD" -> requestBuilder.head()
            else -> requestBuilder.method(httpMethod, requestBody)
        }

        val okHttpResponse = client.newCall(requestBuilder.build()).execute()
        val responseBody = okHttpResponse.body?.string() ?: ""
        val responseHeaders = mutableMapOf<String, List<String>>()
        for (headerName in okHttpResponse.headers.names()) {
            responseHeaders[headerName] = okHttpResponse.headers(headerName)
        }

        return Response(
            okHttpResponse.code,
            okHttpResponse.message,
            responseHeaders,
            responseBody,
            okHttpResponse.request.url.toString()
        )
    }

    companion object {
        @Volatile
        private var instance: NewPipeDownloader? = null

        fun getInstance(): NewPipeDownloader {
            return instance ?: synchronized(this) {
                instance ?: NewPipeDownloader(
                    OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .followRedirects(true)
                        .build()
                ).also { instance = it }
            }
        }
    }
}
