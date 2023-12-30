package shark.data.plugin

import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner
import shark.SharkBot
import shark.SharkBot.baseDir
import shark.event.EventBus
import shark.event.application.plugin.PluginLoadEvent
import java.io.File
import java.nio.file.Path


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
    val springScanningPackages: Array<String> = arrayOf()
)

class PluginLoader : AbstractPluginLoader() {

    override val pluginTypes: ArrayList<String> = arrayListOf()
    override val plugins: ArrayList<IPlugin> = arrayListOf()
    override val eventBus = EventBus(this.javaClass)

    fun register() = this.also { registered.add(it) }

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
        plugins.forEach { plugin ->
            val event = PluginLoadEvent(plugin, this)
            event.setEventBus(eventBus)
            plugin.load(event)
        }
        val beanFactory = SharkBot.application.context().autowireCapableBeanFactory as DefaultListableBeanFactory
        val scanner = ClassPathBeanDefinitionScanner(beanFactory, false)
        plugins.forEach { plugin ->
            plugin.config?.springScanningPackages?.let {
                if (it.isNotEmpty()) scanner.scan(*it)
            }
        }
    }

    companion object {
        val registered = mutableListOf<PluginLoader>()
        fun getPluginPath(path: String?): Path {
            return Path.of(
                baseDir,
                path
            )
        }
    }

}