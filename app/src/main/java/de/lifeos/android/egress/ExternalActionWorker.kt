package de.lifeos.android.egress

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

class ExternalActionWorker : Service() {

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    inner class LocalBinder : Binder() {
        fun getService(): ExternalActionWorker = this@ExternalActionWorker
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun executeAuthorizedFetch(
        endpointUrl: String,
        authToken: String,
        onComplete: (rawPayload: String) -> Unit
    ) {
        scope.launch {
            try {
                val url = URL(endpointUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $authToken")
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                    withContext(Dispatchers.Main) { onComplete(response) }
                } else {
                    withContext(Dispatchers.Main) { onComplete("ERROR_HTTP_${connection.responseCode}") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onComplete("ERROR_${e.localizedMessage}") }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
