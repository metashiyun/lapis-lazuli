package dev.lapislazuli.runtimes.jvm.core.host

data class HttpRequestSpec(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

data class HttpResponsePayload(
    val url: String,
    val status: Int,
    val headers: Map<String, String>,
    val body: String,
) {
    val ok: Boolean
        get() = status in 200..299
}

interface HttpService {
    @Throws(Exception::class)
    fun fetch(request: HttpRequestSpec): HttpResponsePayload

    @Throws(Exception::class)
    fun fetchAsync(
        request: HttpRequestSpec,
        onSuccess: Callback,
        onError: Callback,
    )
}
