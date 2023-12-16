package shark

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import shark.data.plugin.PluginLoader
import shark.data.registry.ResourceLocation
import shark.data.resource.ResourceLoader
import shark.data.resource.ResourceLoaderOptions
import shark.event.Event
import shark.event.EventBus
import shark.event.WrappedEvent
import shark.event.application.ClientSetupEvent
import shark.event.application.CommonSetupEvent
import shark.event.application.DiscordSetupEvent
import shark.network.SharkClient
import shark.network.SharkClientConfig
import shark.util.ConfigUtil
import sharkbot.SharkBotDefaultPlugin
import java.nio.file.Path

@SpringBootApplication
class SharkBotApplication

object SharkBot {

    val baseDir: String = Path.of(System.getProperty("user.dir"), "shark").toFile().let {
        it.mkdirs(); it.path
    }
    val sharkConfig = ConfigUtil.useConfig<SharkClientConfig>(ResourceLocation.of("shark"))
    val logger: Logger = LoggerFactory.getLogger(SharkBotApplication::class.java)
    val client = SharkClient(sharkConfig)
    val eventBus = EventBus(this::class)
    val clientNetworkThread = Thread {
        client.login()
        client.getClient().awaitReady()
        eventBus.emit(DiscordSetupEvent())
        eventBus.emit(CommonSetupEvent())
    }
    val pluginLoader = PluginLoader().register()
    val resourceLoader = ResourceLoader()
}

class WrappedSharkEvent(val wrappedSharkEvent: WrappedEvent <Event>, val shark: SharkBot) : Event()

fun main(args: Array<String>) {
    runApplication<SharkBotApplication>(*args)
    SharkBot.eventBus.on { event ->
        SharkBot.pluginLoader.plugins.forEach {
            it.eventBus?.emit(WrappedSharkEvent(event, SharkBot))
        }
    }
    SharkBot.pluginLoader.load(SharkBotDefaultPlugin)
    SharkBot.pluginLoader.load(PluginLoader.getPluginPath("plugins"))
    SharkBot.pluginLoader.loadPlugins()
    SharkBot.resourceLoader.load(
        ResourceLoaderOptions(
            pluginLoaders = PluginLoader.REGISTERED
        )
    )
    SharkBot.eventBus.emit(ClientSetupEvent())
    SharkBot.clientNetworkThread.start()
}
