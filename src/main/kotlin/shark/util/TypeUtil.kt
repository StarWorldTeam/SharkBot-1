package shark.util

import com.fasterxml.jackson.core.type.TypeReference

object StringTypeReference : TypeReference<String>()
object StringAnyNullableMapTypeReference : TypeReference<Map<String, Any?>>()
object AnyAnyNullableMapTypeReference : TypeReference<Map<Any, Any?>>()
object AnyListTypeReference : TypeReference<List<Any?>>()

typealias Callable = () -> Unit
