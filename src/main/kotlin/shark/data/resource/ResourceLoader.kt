package shark.data.resource

import lombok.SneakyThrows
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.UrlResource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.util.ResourceUtils
import shark.SharkBot
import shark.data.plugin.JarPlugin
import shark.data.plugin.PluginLoader
import shark.data.registry.ResourceLocation
import shark.event.EventBus
import shark.event.application.resource.AllResourceLoadedEvent
import shark.event.application.resource.ResourceLoadEvent
import java.net.URI
import java.nio.file.Path
import java.util.*
import java.util.Locale
import kotlin.collections.ArrayList

class ResourceLoaderOptions(
    var loadSharkResource: Boolean = true,
    var loadPluginResource: Boolean = true,
    var pluginLoaders: Iterable<PluginLoader>? = null,
    var loadFileResources: Boolean = true,
    var fileResourcesPath: Path = Path.of(SharkBot.baseDir, "resourcepacks")
)

class ResourceLoader {

    val eventBus: EventBus = EventBus(this::class)

    @Synchronized
    fun load(
        options: ResourceLoaderOptions
    ) {
        if (options.loadSharkResource) try {
            load("classpath:assets/*/*/**/*.*", "classpath:assets")
        } catch (ignored: Throwable) {}
        if (options.loadPluginResource && options.pluginLoaders != null) try {
            for (plugins in options.pluginLoaders!!) {
                for (plugin in plugins.plugins) {
                    if (plugin !is JarPlugin) continue
                    val baseDir = URI.create(getJarResource(plugin.file.absolutePath, "assets")).toString()
                    val path = "$baseDir/*/*/**/*.*"
                    load(path, baseDir)
                }
            }
        } catch (ignored: Throwable) {}
        if (options.loadFileResources) try {
            if (!options.fileResourcesPath.toFile().exists()) options.fileResourcesPath.toFile().mkdirs()
            for (file in Objects.requireNonNull(options.fileResourcesPath.toFile().listFiles())) {
                if (!file.isDirectory) continue
                try {
                    load(
                        "file:" + Path.of(options.fileResourcesPath.toString(), file.name, "assets") + "/*/*/**/*.*",
                        Path.of(options.fileResourcesPath.toString(), file.name, "assets").toString()
                    )
                } catch (ignored: Throwable) {}
            }
        } catch (ignored: Throwable) {}
        eventBus.emit(AllResourceLoadedEvent(this))
    }

    /**
     * @see UrlResource
     * 加载URL资源
     */
    @SneakyThrows
    fun loadURLResource(
        resource: UrlResource,
        resourceMap: HashMap<ResourceLocation, ArrayList<SharkResource>>,
        rawBaseDir: String
    ) {
        val realPath = splitPath(resource.uri.toString().substring(rawBaseDir.length))
        if (realPath.size >= 2) {
            val type = ResourceLocation.of(realPath[0], realPath[1])
            val resourcePath = realPath.subList(2, realPath.size)
            val resourceLocation =
                ResourceLocation.of(realPath[0], realPath[1] + "/" + java.lang.String.join("/", resourcePath))
            val splitRawBaseDir = Arrays.stream(rawBaseDir.split("!/".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()).toList()
            val baseDir = java.lang.String.join("!/", splitRawBaseDir.subList(1, splitRawBaseDir.size))
            val sharkResource = SharkResource(
                type,
                resource, baseDir, java.lang.String.join("/", resourcePath), resourceLocation
            )
            if (!resourceMap.containsKey(type)) resourceMap[type] = arrayListOf()
            resourceMap[type]!!.add(sharkResource)
        }
    }

    /**
     * @see FileSystemResource
     * 加载文件资源
     */
    @SneakyThrows
    fun loadFileSystemResource(
        resource: FileSystemResource,
        resourceMap: HashMap<ResourceLocation, ArrayList<SharkResource>>,
        baseDir: String?
    ) {
        var list = splitPath(resource.path)
        val baseDirPath = splitPath(
            ResourceUtils.getURL(
                baseDir!!
            ).path
        )
        list = list.subList(baseDirPath.size, list.size)
        if (list.size >= 2) {
            val type = ResourceLocation.of(list[0], list[1])
            val resourcePath = java.lang.String.join("/", list.subList(2, list.size))
            val sharkResource = SharkResource(
                type,
                resource, java.lang.String.join("/", baseDirPath), resourcePath,
                ResourceLocation.of(
                    type.namespace,
                    java.lang.String.join("/", splitPath(type.path + "/" + resourcePath))
                )
            )
            if (!resourceMap.containsKey(type)) resourceMap[type] = arrayListOf()
            resourceMap[type]!!.add(sharkResource)
        }
    }

    /**
     * @see ClassPathResource
     * 加载类内部资源
     */
    @SneakyThrows
    fun loadClassPathResource(
        resource: ClassPathResource,
        resourceMap: HashMap<ResourceLocation, ArrayList<SharkResource>>,
        baseDir: String
    ) {
        val baseDirFiles = PathMatchingResourcePatternResolver().getResources("%s".format(Locale.ROOT, baseDir))
        if (baseDirFiles.isEmpty() || baseDirFiles[0] !is ClassPathResource) return
        val resourceBaseDir: List<String> = splitPath((baseDirFiles[0] as ClassPathResource).path)
        val locationPath = splitPath(resource.path).subList(resourceBaseDir.size, splitPath(resource.path).size)
        if (locationPath.size >= 2) {
            val typeLocation = ResourceLocation.of(locationPath[0], locationPath[1])
            val resourcePath = java.lang.String.join("/", locationPath.subList(2, locationPath.size))
            val sharkResource = SharkResource(
                typeLocation,
                resource, java.lang.String.join("/", resourceBaseDir), resourcePath,
                ResourceLocation.of(
                    typeLocation.namespace,
                    java.lang.String.join("/", splitPath(resourcePath).subList(1, splitPath(resourcePath).size))
                )
            )
            if (!resourceMap.containsKey(typeLocation)) resourceMap[typeLocation] = arrayListOf()
            resourceMap[typeLocation]!!.add(sharkResource)
        }
    }

    @SneakyThrows
    fun load(path: String, baseDir: String) {
        val urlList = PathMatchingResourcePatternResolver().getResources(
            path
        )
        val resourceMap: HashMap<ResourceLocation, ArrayList<SharkResource>> = hashMapOf()
        for (i in urlList) {
            (i as? UrlResource)?.let { loadURLResource(it, resourceMap, baseDir) }
                ?: ((i as? FileSystemResource)?.let { loadFileSystemResource(it, resourceMap, baseDir) }
                    ?: (i as? ClassPathResource)?.let { loadClassPathResource(it, resourceMap, baseDir) })
        }
        resourceMap.forEach { (location: ResourceLocation, resources: List<SharkResource>) ->
            loadResource(location, resources)
        }
    }

    @SneakyThrows
    fun loadResource(location: ResourceLocation, resources: List<SharkResource>) {
        this.eventBus.emit(ResourceLoadEvent(location, resources, this))
    }

    companion object {

        fun getJarResource(jarFile: String, vararg files: String?): String {
            return "jar:file:/%s!/%s".format(
                Locale.ROOT,
                jarFile.replace("\\\\".toRegex(), "/"),
                java.lang.String.join("/", *files)
            )
        }

        fun splitPath(path: String): List<String> {
            return Arrays.stream(java.lang.String.join("/", *path.split("\\\\".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()).split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
                .filter { i: String -> i.isNotEmpty() }
                .toList()
        }

        fun splitPath(path: Path): List<String> {
            return splitPath(path.toString())
        }

    }

}