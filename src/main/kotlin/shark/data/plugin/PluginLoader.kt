package shark.data.plugin

import shark.SharkBot.baseDir
import shark.event.EventBus
import shark.event.application.plugin.PluginLoadEvent
import java.io.File
import java.nio.file.Path
import java.util.function.Consumer

abstract class AbstractPluginLoader {

    open val pluginTypes: ArrayList<String> = arrayListOf()
    open val plugins: ArrayList<IPlugin> = arrayListOf()
    open val eventBus: EventBus = EventBus(this.javaClass)

    abstract fun load(plugin: IPlugin)

}

class PluginConfig (
    val name: String = "",
    val id: String = "",
    val main: String = "",
    val description: String = "",
    val version: String = "0.0.0",
)

class PluginLoader : AbstractPluginLoader() {

    override val pluginTypes: ArrayList<String> = arrayListOf()
    override val plugins: ArrayList<IPlugin> = arrayListOf()
    override val eventBus = EventBus(this.javaClass)

    fun register() = this.also { REGISTERED.add(it) }

    init {
        pluginTypes.add(".jar")
        pluginTypes.add(".plugin")
    }

    fun load(path: Path) {
        if (!path.toFile().exists()) path.toFile().mkdirs()
        val files = path.toFile().listFiles()
        if (files != null) for (i in files) {
            if (pluginTypes.stream().anyMatch { type: String? ->
                    i.isFile && i.path.endsWith(
                        type!!
                    )
                }) {
                plugins.add(JarPlugin(i, this))
            }
        }
    }

    fun load(file: File) {
        plugins.add(JarPlugin(file, this))
    }

    override fun load(plugin: IPlugin) {
        plugins.add(plugin)
    }

    @Synchronized
    fun loadPlugins() {
        plugins.forEach(
            Consumer { plugin ->
                val event = PluginLoadEvent(plugin, this)
                event.setEventBus(eventBus)
                plugin.load(event)
            }
        )
    }

    companion object {
        val REGISTERED = mutableListOf<PluginLoader>()
        fun getPluginPath(path: String?): Path {
            return Path.of(
                baseDir,
                path
            )
        }
    }

}