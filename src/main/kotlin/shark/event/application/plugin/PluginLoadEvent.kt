package shark.event.application.plugin

import shark.annotation.Property
import shark.data.plugin.IPlugin
import shark.data.plugin.PluginConfig
import shark.data.plugin.PluginLoader
import shark.event.Event

class PluginLoadEvent(
    @field:Property("plugin") val plugin: IPlugin,
    @field:Property("loader") private val loader: PluginLoader
) : Event() {

    override fun isCancellable() = true

    val config: PluginConfig
        get() = plugin.config!!

}