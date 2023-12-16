package shark.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import lombok.SneakyThrows
import shark.SharkBot
import shark.data.registry.ResourceLocation
import java.io.File
import java.nio.file.Path
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

object ConfigUtil {

    fun getConfigPath(): Path {
        return Path.of(
            SharkBot.baseDir, "config"
        )
    }

    @SneakyThrows
    fun getConfigFile(name: String): File {
        val path = getConfigPath().toFile()
        path.parentFile.mkdirs()
        return Path.of(path.path, "$name.yml").toFile()
    }

    @SneakyThrows
    inline fun <reified T : Any> useConfig(
        location: ResourceLocation,
        noinline defaultValue: () -> T = {
            T::class.createInstance()
        }
    ): T {
        return useConfig("%s/%s".format(Locale.ROOT, location.namespace, location.path), T::class, defaultValue)
    }

    @SneakyThrows
    fun <T : Any> useConfig(name: String, clazz: KClass<T>, defaultValue: () -> T): T {
        val mapper = ObjectMapper(YAMLFactory())
        val file = getConfigFile(name)
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
            mapper.writeValue(file, defaultValue())
        }
        return mapper.readValue(
            file,
            clazz.java
        )
    }

}