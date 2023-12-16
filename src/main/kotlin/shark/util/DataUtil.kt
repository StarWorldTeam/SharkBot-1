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
    private val clazz: Class<T>?,
    private val defaultValue: Supplier<T>,
    private val type: DataUtil.FileType
) : DataHolder<T> {

    @SneakyThrows
    override fun get(): T = mapper.readValue(file, clazz)

    @SneakyThrows
    override fun set(value: T) = this.also {
        mapper.writeValue(file, value)
    }

    @SneakyThrows
    override fun reset() {
        mapper.writeValue(file, defaultValue)
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
    inline fun <reified T : Any> useData(
        name: String,
        fileType: FileType,
        noinline defaultValue: () -> T = { T::class.createInstance() }
    ): DataHolder<T> {
        val mapper = fileType.getMapper()
        val file = getDataFile(name, fileType.type)
        if (!file.exists()) {
            file.createNewFile()
            mapper.writeValue(file, defaultValue())
        }
        return FileDataHolder(name, file, mapper, T::class.java, defaultValue, fileType)
    }

    enum class FileType(val type: String, private val mapperGetter: () -> ObjectMapper) {

        YAML("yml", { ObjectMapper(YAMLFactory()) }),
        BSON("bson", { ObjectMapper(BsonFactory()) }),
        JSON("json", { ObjectMapper() });

        fun getMapper() = mapperGetter()

    }

}