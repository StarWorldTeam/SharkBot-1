package shark.util

import com.fasterxml.jackson.core.type.TypeReference

object StringTypeReference : TypeReference<String>()
object DictTypeReference : TypeReference<Map<String, Any?>>()
object ArrayTypeReference : TypeReference<List<Any?>>()