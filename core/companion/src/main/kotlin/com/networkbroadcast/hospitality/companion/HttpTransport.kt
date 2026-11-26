package com.networkbroadcast.hospitality.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpRequest(
    val method: String,
    /** Ruta y query, con la barra inicial: "/api/tv/commands/?after=0". */
    val path: String,
    val headers: Map<String, String> = emptyMap(),
    /** JSON ya serializado, o null si no lleva cuerpo. */
    val body: String? = null,
)

data class HttpResponse(val status: Int, val body: String)

/**
 * Lo único que los clientes del backend necesitan de la red. Se separa para probarlos sin red
 * (y para cambiar la librería HTTP sin tocar la lógica). Sin conexión lanza [IOException]; un
 * 4xx/5xx NO es una excepción, vuelve como [HttpResponse].
 */
fun interface HttpTransport {
    @Throws(IOException::class)
    suspend fun execute(request: HttpRequest): HttpResponse
}

/** [HttpTransport] sobre `HttpURLConnection`: sin dependencias nuevas. */
class UrlConnectionTransport(
    baseUrl: String,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 10_000,
) : HttpTransport {
    private val base = baseUrl.trimEnd('/')

    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val connection = URL(base + request.path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/json")
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            request.body?.let { body ->
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            HttpResponse(status, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}
