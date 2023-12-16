package shark.network

import com.neovisionaries.ws.client.WebSocketFactory
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import okhttp3.OkHttpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import shark.data.registry.SharkRegistries
import shark.data.resource.Locale
import shark.event.EventBus
import shark.event.network.CommandInteractionEvent
import shark.event.network.CommandSetupEvent
import shark.network.command.Command
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.*

/** Shark客户端设置 */
class SharkClientConfig {
    /** 网络代理 */
    class ProxyConfig {
        /** 是否启用 */
        val enabled = false
        /** 地址 */
        val host = "127.0.0.1"
        /** 端口 */
        val port = 7890
    }
    /** 默认语言 */
    val defaultLanguage = "zh_cn"
    /** 登录令牌 */
    val token: String = ""
    /** 代理设置 */
    val proxy: ProxyConfig = ProxyConfig()
}

/** 事件监听器 */
class SharkClientEventListener(val client: SharkClient) : ListenerAdapter() {

    override fun onGenericCommandInteraction(event: GenericCommandInteractionEvent) {
        client.commands.filter { it.second.name == event.name }.forEach {
            it.first.run(CommandInteractionEvent(it.first, event))
        }
    }

}

/**
 * Shark客户端
 * @param config 设置
 */
open class SharkClient(val config: SharkClientConfig) {

    open val eventBus = EventBus(this::class)
    open val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private var client: JDA? = null
    open val commands: ArrayList<Pair<Command, CommandData>> = arrayListOf()

    /** 获取Discord客户端，登录后才可以获取 */
    open fun getClient() = Objects.requireNonNull(client)!!

    /** 登录 */
    open fun login() = this.also {
        client = JDABuilder.createDefault(config.token)
            .setHttpClientBuilder(
                OkHttpClient.Builder().also {
                    if (config.proxy.enabled) it.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(config.proxy.host, config.proxy.port)))
                }
            )
            .setWebsocketFactory(
                WebSocketFactory().also {
                    if (config.proxy.enabled) it.proxySettings.setHost(config.proxy.host).port = config.proxy.port
                }
            )
            .addEventListeners(SharkClientEventListener(this))
            .build()
        getClient().awaitReady()
        updateCommands()
    }

    fun updateCommands() {
        val action = getClient().updateCommands()
        for (command in SharkRegistries.commands.getEntries()) {
            val data = command.second.getCommandType().create(command.second.getCommandTranslation().getCommandFullName().getString(Locale.empty))
            val exist = commands.find { it.second.name == data.name } != null
            commands.add(Pair(command.second, data))
            val event = CommandSetupEvent(command.second, data)
            eventBus.emit(event)
            Command.putTranslation(event)
            if (!exist) action.addCommands(data)
        }
        action.queue()
    }

}