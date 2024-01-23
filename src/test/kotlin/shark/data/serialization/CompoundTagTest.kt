package shark.data.serialization

import org.junit.jupiter.api.Test

class CompoundTagTest {

    @Test
    fun `Compound Tag Syntax Test`() {
        val text =
            """
            {
                #identifier: "=identifier",
                "string": "=string",
                1L: "=1 (long -> string)",
                1D: "=1.0 (double -> string)",
                #compoundTag: {#long: 1l, #short: 1s, #float: 1f, #float2: 1.0, #int: 1i, #int2: 2, #bool: 1b, #double: 1d},
                #listTag: [{#inner: [{}]}, [[[{}]]], #2, 1e10, 1e+10, 1e-10, 1.0e10, true, false, null]
            }
            """.trimIndent().filter { it !in "\n\r" }
        CompoundTagSerializer.parseObject(text).also { CompoundTagSerializer.parseObject(it.toString()) }
        val parsedExpression = CompoundTagSerializer.parseExpression(text)
        assert(parsedExpression != null) { "Value 'parsedExpression' must be not null" }
        assert(parsedExpression is CompoundTag) {
            "Value 'parsedExpression' must be a CompoundTag, but got ${parsedExpression!!.javaClass.name}"
        }
    }

}
