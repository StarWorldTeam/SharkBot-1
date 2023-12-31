package shark.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper

object StringTypeReference : TypeReference<String>()
object StringAnyNullableMapTypeReference : TypeReference<Map<String, Any?>>()
object AnyAnyNullableMapTypeReference : TypeReference<Map<Any, Any?>>()
object AnyListTypeReference : TypeReference<List<Any?>>()

typealias Callable = () -> Unit

private val objectMapper = ObjectMapper()

fun mapToJson(map: Map<String, Any?>): String = objectMapper.writeValueAsString(map)
