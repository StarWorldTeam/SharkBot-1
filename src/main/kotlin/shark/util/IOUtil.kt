package shark.util

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import de.undercouch.bson4jackson.BsonFactory
import kodash.coroutine.Promise
import kodash.coroutine.promise
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import shark.data.serialization.CompoundTagSerializer
import java.io.InputStream
import java.util.*
import java.util.stream.Stream
import java.util.stream.StreamSupport
import kotlin.collections.set

enum class RequestMethod {

    GET, HEAD, POST, PUT, PATCH, DELETE;

    fun method(request: Request.Builder, requestBody: RequestBody? = null): Request.Builder {
        return request.method(name, requestBody)
    }

    fun fromName(method: String): RequestMethod {
        return when (method) {
            "GET" -> GET
            "HEAD" -> HEAD
            "POST" -> POST
            "PUT" -> PUT
            "PATCH" -> PATCH
            "DELETE" -> DELETE
            else -> GET
        }
    }
}

class Header (map: Map<String, String> = mapOf()) {

    private val map = hashMapOf<String, String>()

    init {
        this.map.putAll(map)
    }

    fun addHeader(name: String, value: String) = this.also {
        map[name] = value
    }

    fun removeHeader(name: String) = this.also {
        map.remove(name)
    }

    fun getHeaders() = map.entries.map { Pair(it.key, it.value) }

}

class ResponseMapping(
    private val response: Response
) {

    fun response() = response
    fun request() = response.request
    fun body() = response.body!!
    fun redirected() = response.isRedirect
    fun statusCode() = response.code
    fun stream(): InputStream = body().byteStream()
    fun bytes() = body().bytes()
    fun url() = request().url
    fun string() = body().string()

    inline fun <reified T> mapping(factory: JsonFactory = JsonFactory()) = mapping(T::class.java, factory)

    fun <T> mapping(type: Class<T>, factory: JsonFactory = JsonFactory()): Promise<T> = promise {
        try {
            resolve(ObjectMapper(factory).readValue(stream(), type))
        } catch (throwable: Throwable) {
            reject(throwable)
        }
    }

    fun <T> mapping(type: TypeReference<T>, factory: JsonFactory = JsonFactory()): Promise <T> = promise {
        try {
            resolve(ObjectMapper(factory).readValue(stream(), type))
        } catch (throwable: Throwable) {
            reject(throwable)
        }
    }

    fun json() = mapping(AnyAnyNullableMapTypeReference)
    fun yaml() = mapping(AnyAnyNullableMapTypeReference, YAMLFactory())
    fun bson() = mapping(AnyAnyNullableMapTypeReference, BsonFactory())
    fun compoundTag() = CompoundTagSerializer.parseObject(this.string())

    fun html(charsetName: String? = null): Promise<Document> = promise {
        try {
            resolve(Jsoup.parse(stream(), charsetName, url().toString()))
        } catch (throwable: Throwable) {
            reject(throwable)
        }
    }

}

class FetchOptions(
    val method: RequestMethod = RequestMethod.GET,
    val header: Header = Header(),
    val requestBody: RequestBody? = null,
    val block: Request.Builder.(client: OkHttpClient.Builder) -> Unit = {},
)

object IOUtil {

    fun fetch(url: String, options: FetchOptions = FetchOptions()): Promise<ResponseMapping> = promise {
        val requestBuilder = Request.Builder()
        options.header.getHeaders().forEach { requestBuilder.addHeader(it.first, it.second) }
        requestBuilder.url(url)
        options.method.method(requestBuilder, options.requestBody)
        val clientBuilder = OkHttpClient.Builder()
        options.block(requestBuilder, clientBuilder)
        val request = requestBuilder.build()
        val client = clientBuilder.build()
        try {
            val response = client.newCall(request).execute()
            resolve(ResponseMapping(response))
        } catch (throwable: Throwable) {
            reject(throwable)
        }
    }

    fun <T> stream(iterable: Iterable<T>) = stream(iterable.iterator())
    fun <T> stream(iterator: Iterator<T>): Stream<T> {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false)
    }

    fun <T> stream(sequence: Sequence<T>) = stream(sequence.iterator())


}
