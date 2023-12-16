package shark.data.plugin

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import lombok.SneakyThrows
import shark.SharkBot
import shark.data.registry.ResourceLocation
import shark.event.EventBus
import shark.event.application.plugin.PluginLoadEvent
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.JarFile

/** 插件接口 */
interface IPlugin {

    /** 插件加载器 */
    val pluginLoader: PluginLoader
    /** 插件配置 */
    var config: PluginConfig?
    /** 插件主类 */
    val mainClass: Class <*>?
    /** 插件实例 */
    var instance: Any?
    /** 插件类加载器 */
    var loader: ClassLoader?
    /** 插件加载状态 */
    var loaded: Boolean
    /** 插件名称 */
    val name: String
        get() = config!!.name
    /** 插件标识符 */
    val pluginId: String
        get() = config!!.id
    /** 插件事件总线 */
    var eventBus: EventBus?
    /** 插件注解 */
    val pluginAnnotation: Plugin
        get() = mainClass!!.getAnnotation(Plugin::class.java)!!
    /** 加载 */
    fun load(event: PluginLoadEvent)
}

class JarPlugin(val file: File, override val pluginLoader: PluginLoader) : IPlugin {

    override var eventBus: EventBus? = null

    override var config: PluginConfig? = null
        get() {
            if (field != null) return field
            val jarFile = jarFile
            val stream = jarFile.getInputStream(jarFile.getEntry("plugin.yml"))
            val mapper = ObjectMapper(YAMLFactory())
            field = mapper.readValue(stream, PluginConfig::class.java)
            return field
        }
        set(config) {
            if (this.config == null) field = config
        }
    override var mainClass: Class<*>? = null
    override var instance: Any? = null
        get() {
            if (field == null) throw RuntimeException("Plugin not constructed yet.")
            return field
        }
        set(value) {
            if (instance == null) field = value
        }

    override var loader: ClassLoader? = null

    override var loaded = true
        set(_) {
            field = true
        }
    @SneakyThrows
    override fun load(event: PluginLoadEvent) {
        event.getEventBus()!!.emit(event)
        if (event.isCancelled()) return
        if (!ResourceLocation.isValidNamespace(config!!.id)) return
        for (plugin in pluginLoader.plugins) {
            if (!plugin.loaded || plugin === this) continue
            if (plugin.config!!.id === config!!.id) throw RuntimeException(
                "Cannot load the same plugin: ${config!!.id} ($this, $plugin)"
            )
        }
        loader = (URLClassLoader(arrayOf(url), SharkBot.javaClass.classLoader))
        mainClass = (loader!!.loadClass(config!!.main))
        eventBus = EventBus(mainClass!!)
        if (mainClass!!.isAnnotationPresent(Plugin::class.java)) {
            instance = mainClass!!.getConstructor(PluginLoadEvent::class.java).newInstance(event)
            setLoaded()
        }
    }

    override val pluginId: String
        get() = pluginAnnotation.id

    @get:SneakyThrows
    val url: URL
        get() = URL("file:" + this.file.path)

    fun setLoaded() {
        loaded = true
    }

    val jarFile: JarFile
        get() = JarFile(this.file)

    override val name: String
        get() = config!!.name

    override fun toString(): String {
        return "Plugin [${file.name}]"
    }

}