package shark.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import de.undercouch.bson4jackson.BsonFactory
import lombok.SneakyThrows
import shark.SharkBot
import java.io.File
import java.nio.file.Path
import java.util.function.Supplier
import kotlin.reflect.full.createInstance

interface Holder <T> {

    fun get(): T

}

interface DataHolder<T> : Holder<T> {

    fun getDefaultValue(): T? = null

    override fun get(): T
    fun set(value: T): DataHolder<T>
    fun reset() {
        getDefaultValue()?.let { set(it) }
    }

}

class FileDataHolder<T>(
    private val name: String,
    val file: File,
    private val mapper: ObjectMapper,
    clazz: Class<T>,
    private val defaultValue: Supplier<T>,
    private val type: DataUtil.FileType
) : DataHolder<T> {

    var cache: T = mapper.readValue(file, clazz)

    override fun getDefaultValue() = defaultValue.get()

    @SneakyThrows
    override fun get() = cache

    @SneakyThrows
    override fun set(value: T) = this.also {
        cache = value
        mapper.writeValue(file, cache)
    }

    @SneakyThrows
    override fun reset() {
        mapper.writeValue(file, defaultValue.get())
    }

}

object DataUtil {

    val dataPath: Path
        get() = Path.of(
            SharkBot.baseDir, "data"
        )

    @SneakyThrows
    fun getDataFile(name: String, fileType: String): File {
        val path = dataPath.toFile()
        val filePath = Path.of(path.path, "$name.$fileType")
        filePath.parent.toFile().mkdirs()
        return filePath.toFile()
    }

    @SneakyThrows
    inline fun <reified T : Any> useSharkFileData(
        name: String,
        fileType: FileType,
        noinline defaultValue: () -> T = { T::class.createInstance() }
    ): DataHolder<T> {
        var defaultValueSupplier = defaultValue
        val mapper = fileType.getMapper()
        val file = getDataFile(name, fileType.type)
        if (!file.exists()) {
            file.createNewFile()
            val value = defaultValue()
            defaultValueSupplier = { value }
            mapper.writeValue(file, defaultValueSupplier())
        }
        return FileDataHolder(name, file, mapper, T::class.java, defaultValueSupplier, fileType)
    }

    enum class FileType(val type: String, private val mapperGetter: () -> ObjectMapper) {

        YAML("yml", { ObjectMapper(YAMLFactory()) }),
        BSON("bson", { ObjectMapper(BsonFactory()) }),
        JSON("json", { ObjectMapper() });

        fun getMapper() = mapperGetter()

    }

}