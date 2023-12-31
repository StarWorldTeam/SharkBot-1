package shark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.Banner
import org.springframework.boot.SpringBootVersion
import org.springframework.boot.ansi.AnsiColor
import org.springframework.boot.ansi.AnsiOutput
import org.springframework.boot.ansi.AnsiStyle
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.system.JavaVersion
import org.springframework.core.env.Environment
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
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
import java.io.PrintStream
import java.nio.charset.Charset
import java.nio.file.Path
import java.util.*

@SpringBootApplication(scanBasePackages = ["sharkbot"])
class SharkBotApplication

class SharkMeta {

    companion object {
        const val sharkMeta = "META-INF/shark.yml"
    }

    var version: String = "UNKNOWN"

}

class SharkVersion(val version: String) {

    companion object {

        private var current: SharkVersion? = getVersion()
            set(value) {
                if (current != null) throw UnsupportedOperationException()
                field = value
            }

        fun getVersion(): SharkVersion {
            if (current != null) return current!!
            val yaml = PathMatchingResourcePatternResolver()
                .getResources(SharkMeta.sharkMeta)
                .first()
                .getContentAsString(Charset.forName("utf-8"))
            val meta = ObjectMapper(YAMLFactory()).readValue(yaml, SharkMeta::class.java)
            return SharkVersion(meta.version)
        }

    }

    override fun hashCode() = version.hashCode()
    override fun toString() = version
    override fun equals(other: Any?) = version === other

}

class SharkApplicationConfig {

    var headless = true
    var webPanel = false
    var webPort = 8080
    var customSpringProperties: Map<String, Any?> = mapOf()
    var sharkBotUsers = mutableMapOf<String, String>()
    var bannerMode: Banner.Mode = Banner.Mode.CONSOLE

}

class SharkBotBanner : Banner {

    companion object {
        const val sharkBot = "SharkBot"
        val banner = listOf(
            " ____    __                       __          ____            __      ",
            "/\\  _`\\ /\\ \\                     /\\ \\        /\\  _`\\         /\\ \\__   ",
            "\\ \\,\\L\\_\\ \\ \\___      __     _ __\\ \\ \\/'\\    \\ \\ \\L\\ \\    ___\\ \\ ,_\\  ",
            " \\/_\\__ \\\\ \\  _ `\\  /'__`\\  /\\`'__\\ \\ , <     \\ \\  _ <'  / __`\\ \\ \\/  ",
            "   /\\ \\L\\ \\ \\ \\ \\ \\/\\ \\L\\.\\_\\ \\ \\/ \\ \\ \\\\`\\    \\ \\ \\L\\ \\/\\ \\L\\ \\ \\ \\_ ",
            "   \\ `\\____\\ \\_\\ \\_\\ \\__/.\\_\\\\ \\_\\  \\ \\_\\ \\_\\   \\ \\____/\\ \\____/\\ \\__\\",
            "    \\/_____/\\/_/\\/_/\\/__/\\/_/ \\/_/   \\/_/\\/_/    \\/___/  \\/___/  \\/__/"
        )
    }

    override fun printBanner(environment: Environment, sourceClass: Class<*>, printStream: PrintStream) {
        banner.forEach {
            printStream.println(
                AnsiOutput.toString(
                    AnsiColor.BLUE, it
                )
            )
        }
        printStream.println(
            AnsiOutput.toString(
                AnsiColor.CYAN, "Shark", AnsiColor.DEFAULT, " ", AnsiStyle.FAINT, "(${SharkVersion.getVersion()})", " ",
                AnsiColor.CYAN, "Spring", AnsiColor.DEFAULT, " ", AnsiStyle.FAINT, "(${SpringBootVersion.getVersion()})", " ",
                AnsiColor.CYAN, "Kotlin", AnsiColor.DEFAULT, " ", AnsiStyle.FAINT, "(${KotlinVersion.CURRENT})", " ",
                AnsiColor.CYAN, "Java", AnsiColor.DEFAULT, " ", AnsiStyle.FAINT, "(${JavaVersion.getJavaVersion()})"
            )
        )
        printStream.println()
    }

}

object SharkBot {

    val startTime = Date()
    val baseDir: String = Path.of(System.getProperty("user.dir"), "shark").toFile().let {
        it.mkdirs(); it.path
    }
    val sharkConfig = ConfigUtil.useConfig<SharkClientConfig>(ResourceLocation.of("shark"))
    val applicationConfig = ConfigUtil.useConfig<SharkApplicationConfig>(ResourceLocation.of("application"))
    val logger: Logger = LoggerFactory.getLogger(SharkBotApplication::class.java)
    val client = SharkClient(sharkConfig)
    val eventBus = EventBus(this::class)
    val clientNetworkThread = Thread {
        client.login()
        client.getClient().awaitReady()
        if ("bot" !in applicationConfig.sharkBotUsers) {
            applicationConfig.sharkBotUsers["bot"] = sharkConfig.token
        }
        eventBus.emit(DiscordSetupEvent())
        eventBus.emit(CommonSetupEvent())
    }
    val application: SpringApplicationBuilder = SpringApplicationBuilder(SharkBotApplication::class.java)
        .headless(applicationConfig.headless)
        .bannerMode(applicationConfig.bannerMode)
        .banner(SharkBotBanner())
        .properties(
            Properties().also {
                it["server.port"] = applicationConfig.webPort
                it.putAll(applicationConfig.customSpringProperties)
            }
        )
    val pluginLoader = PluginLoader().register()
    val resourceLoader = ResourceLoader()
}

class WrappedSharkEvent(val wrappedSharkEvent: WrappedEvent <Event>, val shark: SharkBot) : Event()

fun main(args: Array<String>) {
    SharkBot.application.run(*args)
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
            pluginLoaders = PluginLoader.registered
        )
    )
    SharkBot.eventBus.emit(ClientSetupEvent())
    SharkBot.clientNetworkThread.start()
}
