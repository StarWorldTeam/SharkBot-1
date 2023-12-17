package shark.data.serialization
import arrow.core.getOrHandle
import com.fasterxml.jackson.databind.ObjectMapper
import de.undercouch.bson4jackson.BsonFactory
import io.kpeg.PegParser
import io.kpeg.pe.EvalSymbol
import io.kpeg.pe.Symbol
import lombok.SneakyThrows
import shark.util.ArrayTypeReference
import shark.util.DictTypeReference
import shark.util.StringTypeReference
import java.math.BigDecimal
import java.util.*
import java.util.function.Supplier
import kotlin.math.pow

interface TagLike <T> {

    fun saveAsObject(): T

}

object CompoundTagSerializer {

    /**
     * 将数据文本化
     * @see CompoundTag
     */
    fun stringify(tag: CompoundTag): String {
        val map = tag.saveAsObject()
        val stringBuilder = StringBuilder()
        stringBuilder.append("{")
        for (entry in map.entries) {
            stringBuilder.append(stringify(entry.key))
                .append(":")
                .append(stringify(entry.value))
                .append(",")
        }
        if (stringBuilder.toString().endsWith(",")) stringBuilder.deleteCharAt(stringBuilder.length - 1)
        stringBuilder.append("}")
        return stringBuilder.toString()
    }

    /**
     * 将数据文本化
     * @see CompoundTag
     */
    @SneakyThrows
    fun stringify(string: String): String {
        return ObjectMapper().writeValueAsString(string)
    }

    /**
     * 将数据文本化
     * @see CompoundTag
     */
    fun stringify(listTag: ListTag): String {
        val list = listTag.saveAsObject()
        val stringBuilder = StringBuilder()
        stringBuilder.append("[")
        for (i in list.indices) {
            val value = list[i]
            stringBuilder.append(stringify(value)).append(",")
        }
        if (stringBuilder.toString().endsWith(",")) stringBuilder.deleteCharAt(stringBuilder.length - 1)
        stringBuilder.append("]")
        return stringBuilder.toString()
    }

    /**
     * 将数据文本化
     * @see CompoundTag
     */
    @SneakyThrows
    fun stringify(obj: Any?): String {
        return when (obj) {
            null -> "null"
            is String -> stringify(obj)
            is CompoundTag -> stringify(obj)
            is TagSerializable -> stringify(obj.save())
            is Map<*, *> -> stringify(CompoundTag().load(obj))
            is Long -> BigDecimal(obj).toString() + "L"
            is Double -> BigDecimal(obj).toString() + "D"
            is Short -> BigDecimal(obj.toInt()).toString() + "S"
            is Int -> BigDecimal(obj).toString() + "I"
            is Float -> BigDecimal(obj.toDouble()).toString() + "F"
            is Boolean -> (if (obj) "1" else "0") + "B"
            is Iterable<*> -> stringify(ListTag().load(obj.toList()))
            else -> {
                val mapper = ObjectMapper()
                stringify(
                    CompoundTag().load(
                        mapper.readValue(
                            mapper.writeValueAsString(obj),
                            DictTypeReference
                        )!!
                    )
                )
            }
        }
    }

    val identifierParser: EvalSymbol<String> = Symbol.rule("Identifier", ignoreWS = false) {
        seq {
            +char('#')
            val part = +char {
                (it != ':') && (it !in "\n\r") && (it.code != 0) && !(it.isISOControl())
            }.list().mapPe { it.joinToString("") }.orDefault("")
            value {
                part.get
            }
        }
    }

    val expressionParser: EvalSymbol<Any?> = Symbol.rule(name = "Expression", ignoreWS = true) {
        choice(objectParser, arrayParser, numberParser, stringParser, literalParser, identifierParser)
    }

    val tagParser = Symbol.rule(name = "Tag", ignoreWS = true) {
        choice(objectParser, arrayParser)
    }

    val literalParser: EvalSymbol<Any?> = Symbol.rule(name = "Literal") {
        choice(
            literal("true").mapPe { true },
            literal("false").mapPe { false },
            literal("null").mapPe { null },
        )
    }

    val arrayParser: EvalSymbol<ListTag> = Symbol.rule(name = "Array", ignoreWS = true) {
        seq {
            +char('[')
            val list = +expressionParser.list(separator = char(','))
            +char(']')
            value {
                ListTag().load(list.get)
            }
        }
    }

    private val stringParser = Symbol.rule(name = "String", ignoreWS = false) {
        choice(
            seq {
                +not(char('"', '\\'))
                val c = +ANY
                value { c.get.toString() }
            },
            literal(len = 2) {
                it in listOf("""\"""", """\\""", """\/""", """\b""", """\f""", """\n""", """\r""", """\t""")
            },
            seq {
                val prefix = +literal("""\u""")
                val unicode = +HEX_DIGIT.repeatedExactly(4u).joinToString()

                value { prefix.get + unicode.get }
            }
        ).list(prefix = char('"'), postfix = char('"')).joinToString().mapPe {
            ObjectMapper().readValue("\"$it\"", StringTypeReference)
        }
    }

    val objectParser = Symbol.rule(name = "Object") {
        seq {
            +char('{')
            val pairs = +seq {
                val left = +expressionParser
                +char(':')
                val right = +expressionParser
                value {
                    Pair(left.get.toString(), right.get)
                }
            }.list(separator = char(','))
            +char('}')
            value {
                val map = hashMapOf<String, Any?>()
                pairs.get.forEach { map[it.first] = it.second }
                CompoundTag().load(map)
            }
        }
    }

    val numberParser = Symbol.rule(name = "Number") {
        seq {
            val sign = +literal("-").orDefault("").mapPe { if (it == "-") -1 else 1  }
            val absNumber = +choice(literal("0"), seq {
                val first = +char('1'..'9')
                val others = +DIGIT.zeroOrMore().joinToString()
                value { "${first.get}${others.get}" }
            })
            val pointPart = +seq {
                +char('.')
                val digits = +DIGIT.oneOrMore().joinToString()
                value { "0.${digits.get}" }
            }.orDefault("")
            val exponentialPart = +seq {
                +char('e', 'E')
                val exponentialSign = +char('+', '-').orDefault('+').mapPe { if (it == '-') -1 else 1 }
                val exponentialDigits = +DIGIT.oneOrMore().joinToString().mapPe { it.toFloat() }
                value {
                    10f.pow(exponentialSign.get * exponentialDigits.get)
                }
            }.orDefault(1)
            val typePart = +seq {
                val type = +char('f', 'F', 'd', 'D', 'i', 'I', 'l', 'L', 's', 'S', 'b', 'B')
                value { type.get.lowercaseChar() }
            }.orDefault(null)
            value {
                val parsedTypePart = typePart.get
                val parsedAbsNumber = absNumber.get
                val parsedSign = sign.get
                val parsedPointPart = pointPart.get
                val parsedExponentialPart = exponentialPart.get
                if (parsedTypePart == 'b') parsedAbsNumber.toDouble().toInt() != 0
                else when(parsedTypePart) {
                    'i' -> parsedExponentialPart.toInt() * parsedAbsNumber.toInt() * parsedSign
                    'd' -> (if (parsedPointPart.isNotEmpty()) parsedPointPart.toDouble() else 0.0) + parsedExponentialPart.toDouble() * parsedAbsNumber.toDouble() * parsedSign.toDouble()
                    's' -> parsedExponentialPart.toShort() * parsedAbsNumber.toShort() * parsedSign.toShort()
                    'l' -> parsedExponentialPart.toLong() * parsedAbsNumber.toLong() * parsedSign.toLong()
                    'f' -> (if (parsedPointPart.isNotEmpty()) parsedPointPart.toFloat() else 0.0f) + parsedExponentialPart.toFloat() * parsedAbsNumber.toFloat() * parsedSign.toFloat()
                    else -> when(true) {
                        parsedPointPart.isNotEmpty() -> parsedPointPart.toFloat() + parsedExponentialPart.toFloat() * parsedAbsNumber.toFloat() * parsedSign.toFloat()
                        else -> (parsedExponentialPart.toFloat() * parsedAbsNumber.toFloat() * parsedAbsNumber.toFloat() * parsedSign.toFloat()).toInt()
                    }

                }
            }
        }
    }

    fun parseObject(text: String) = PegParser.parse(symbol = objectParser.value(), text).getOrHandle {
        error(it.joinToString(separator = "\n"))
    }

    fun parseArray(text: String) = PegParser.parse(symbol = arrayParser.value(), text).getOrHandle {
        error(it.joinToString(separator = "\n"))
    }

    fun parseExpression(text: String) = PegParser.parse(symbol = expressionParser.value(), text).getOrHandle {
        error(it.joinToString(separator = "\n"))
    }

}

/**
 * 数据标签
 */
class CompoundTag : TagLike <HashMap <String, Any?>>, TagSerializable {

    private var map: MutableMap<String, Any?> = mutableMapOf()

    override fun equals(other: Any?): Boolean {
        return other != null && other is CompoundTag && this.hashCode() == other.hashCode()
    }

    override fun hashCode(): Int {
        return saveAsByteArray().contentHashCode()
    }

    override fun saveAsObject(): HashMap<String, Any?> {
        val map = HashMap<String, Any?>()
        for ((name, value) in this.map) {
            map[name] = when(value) {
                is TagLike <*> -> value.saveAsObject()
                else -> value
            }
        }
        return map
    }

    override fun save() = CompoundTag().also { it.load(this) }

    override fun load(tag: CompoundTag) {
        load(tag.saveAsObject())
    }

    fun parse(text: String) = this.also {
        load(CompoundTagSerializer.parseObject(text))
    }

    @SneakyThrows
    fun saveAsByteArray(): ByteArray {
        val mapper = ObjectMapper(BsonFactory())
        return mapper.writeValueAsBytes(saveAsObject())
    }

    @SneakyThrows
    fun load(bytes: ByteArray?): CompoundTag {
        val mapper = ObjectMapper(BsonFactory())
        map = hashMapOf(
            *Objects.requireNonNullElse(
                mapper.readValue(
                    bytes,
                    DictTypeReference
                ),
                map
            ).entries.map { Pair(it.key, it.value) }.toTypedArray()
        )
        entrySet()
        return this
    }

    @SneakyThrows
    fun load(map: Map<*, *>): CompoundTag {
        this.map.clear()
        map.forEach { this.map[it.key.toString()] = it.value }
        entrySet()
        return this
    }

    fun putCompound(name: Any?, value: CompoundTag?): CompoundTag {
        map[name.toString()] = value
        return this
    }

    fun getCompound(name: Any?): CompoundTag? {
        return get(name.toString()) as CompoundTag?
    }

    fun putIfNull(name: Any?, supplier: Supplier<Any?>): CompoundTag {
        if (!map.containsKey(name) || map[name.toString()] == null) put(name, supplier.get())
        return this
    }

    fun put(name: Any?, value: Any?): CompoundTag {
        if (value != null) map[name.toString()] = value
        return this
    }

    operator fun get(name: Any?): Any? {
        val value = map[name.toString()]
        if (value !is ListTag && value is List<*>) map[name.toString()] = ListTag().load(value)
        if (value is Map<*, *>) put(name.toString(), CompoundTag().load(value))
        return map[name.toString()]
    }

    fun putLong(name: Any?, value: Number): CompoundTag {
        map[name.toString()] = value.toInt()
        return this
    }

    fun getLong(name: Any?): Long {
        val value = get(name)
        return if (value is Long) value else value.toString().toLong()
    }

    fun putInt(name: Any?, value: Number): CompoundTag {
        map[name.toString()] = value.toInt()
        return this
    }

    fun getInt(name: Any?): Int {
        val value = get(name)
        return if (value is Int) value else value.toString().toInt()
    }

    fun putFloat(name: Any?, value: Number): CompoundTag {
        map[name.toString()] = value.toFloat()
        return this
    }

    fun getFloat(name: Any?): Float {
        val value = get(name)
        return if (value is Float) value else value.toString().toFloat()
    }

    fun putDouble(name: Any?, value: Number): CompoundTag {
        map[name.toString()] = value.toDouble()
        return this
    }

    fun getDouble(name: Any?): Double {
        val value = get(name)
        return if (value is Double) value else value.toString().toDouble()
    }

    fun putShort(name: Any?, value: Number): CompoundTag {
        map[name.toString()] = value.toShort()
        return this
    }

    fun getShort(name: Any?): Short {
        val value = get(name)
        return if (value is Short) value else value.toString().toShort()
    }

    fun putString(name: Any?, value: String): CompoundTag {
        map[name.toString()] = value
        return this
    }

    fun getString(name: Any?): String {
        return get(name).toString()
    }

    fun putBoolean(name: Any?, value: Boolean): CompoundTag {
        map[name.toString()] = value
        return this
    }

    fun putBoolean(name: Any?, value: Number): CompoundTag {
        map[name.toString()] = value.toDouble() != 0.0
        return this
    }

    fun getBoolean(name: Any?) = when(val value = get(name)) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        else -> value.toString() == "true"
    }

    fun putList(name: Any?, value: ListTag): CompoundTag {
        map[name.toString()] = value
        return this
    }

    fun putList(name: Any?, value: Iterable<*>): CompoundTag {
        val tag = ListTag()
        value.forEach { tag.add(it) }
        putList(name, tag)
        return this
    }

    fun getList(name: Any?): ListTag? {
        return get(name) as ListTag?
    }

    fun entrySet(): Set<Map.Entry<String, Any?>> {
        return java.util.Set.copyOf(map.keys.stream().map { key: String -> java.util.Map.entry(key, get(key)) }
            .toList())
    }

    fun remove(name: Any?): CompoundTag {
        map.remove(name.toString())
        return this
    }

    fun clear(): CompoundTag {
        map.clear()
        return this
    }

    fun containsKey(key: Any?): Boolean {
        return map.containsKey(key.toString())
    }

    fun containsValue(value: Any?): Boolean {
        return map.containsValue(value)
    }

    operator fun contains(key: Any?) = containsKey(key)
    operator fun set(key: Any?, value: Any?) = put(key, value)

    override fun toString() = CompoundTagSerializer.stringify(this)

}

/** 列表标签 */
class ListTag : ArrayList<Any?>(), TagLike <List<Any?>> {

    fun put(value: Any?): ListTag {
        add(value)
        return this
    }

    fun parse(text: String) = this.also {
        load(CompoundTagSerializer.parseArray(text))
    }

    fun putAll(value: Collection<*>): ListTag {
        value.forEach { put(it) }
        return this
    }

    override fun saveAsObject(): List<Any?> {
        val list = ArrayList<Any?>()
        for (value in this) {
            when (value) {
                is TagLike <*> -> list.add(value.saveAsObject())
                else -> list.add(value)
            }
        }
        return list
    }

    @SneakyThrows
    fun save(): ByteArray {
        val mapper = ObjectMapper(BsonFactory())
        return mapper.writeValueAsBytes(saveAsObject())
    }

    @SneakyThrows
    fun load(bytes: ByteArray?): ListTag {
        val mapper = ObjectMapper(BsonFactory())
        val value = mapper.readValue(bytes, ArrayTypeReference)
        this.clear()
        if (value != null) this.addAll(value)
        this.toList().clear()
        return this
    }

    fun getAndLoad(index: Int): Any? {
        val value = super.get(index)
        if (value !is ListTag && value is List<*>) set(index, ListTag().load(value))
        if (value is Map<*, *>) set(index, CompoundTag().load(value))
        return super.get(index)
    }

    override fun get(index: Int): Any? {
        return getAndLoad(index)
    }

    @SneakyThrows
    fun load(list: List<*>): ListTag {
        this.clear()
        this.addAll(list)
        this.toList().clear()
        return this
    }

    fun toList(): MutableList<Any?> {
        val list = ArrayList<Any?>()
        for (index in 0 until size) list.add(getAndLoad(index))
        return list
    }

    override fun equals(other: Any?) = other != null && other is ListTag && hashCode() == other.hashCode()

    override fun hashCode() = save().contentHashCode()

    override fun toString() = CompoundTagSerializer.stringify(this)

}

interface TagSerializable {
    fun load(tag: CompoundTag)
    fun save(): CompoundTag
}
