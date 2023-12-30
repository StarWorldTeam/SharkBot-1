package shark.network.command

import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import shark.data.registry.Registrable
import shark.data.registry.ResourceLocation
import shark.data.registry.SharkRegistries
import shark.event.network.CommandInteractionEvent
import shark.event.network.CommandSetupEvent
import shark.network.chat.Component
import shark.network.chat.MutableComponent
import shark.util.Promise
import java.util.*

enum class CommandType {

    UNKNOWN, SLASH, MESSAGE, USER;

    fun toDiscord() = when (this) {
        SLASH -> Command.Type.SLASH
        MESSAGE -> Command.Type.MESSAGE
        USER -> Command.Type.USER
        else -> Command.Type.UNKNOWN
    }

    fun create (name: String, description: String = "-") = when (this) {
        SLASH -> Commands.slash(name, description)
        else -> Commands.context(toDiscord(), name)
    }

}

open class CommandTranslation(private val command: shark.network.command.Command) {

    open fun getCommand() = command

    open fun getCommandName() = Component.translatable(
        "network.command.${getCommand().getLocation().namespace}.${getCommand().getLocation().path.split("/")[0]}.name"
    ) { Component.literal(getCommand().getLocation().path.split("/")[0]) }

    open fun getCommandFullName(): Component {
        val component = MutableComponent()
        component.append(getCommandName())
        val subNames = getCommandSubNames()
        if (subNames.isNotEmpty()) for (name in subNames) {
            component.append("-")
            component.append(name)
        }
        return component
    }

    open fun getCommandSubNames(): Array<Component> {
        val location = getCommand().getLocation()
        val split = command.getLocation().path.split("/")
        return if (split.size > 1) (
            split.subList(1, split.size).stream()
                .map { name ->
                    Component.translatable(
                        "network.command.%s.%s.subName.%s".format(
                            Locale.ROOT,
                            location.namespace,
                            split[0],
                            name
                        )
                    ) { Component.literal(name) }
                }
                .toList()
        ).toTypedArray() else arrayOf()
    }
    open fun getCommandDescription() = Component.translatable("network.command.${getCommand().getLocation().namespace}.${command.getLocation().path.split("/").joinToString("-")}.description")

}

@Suppress("LeakingThis")
abstract class Command : Registrable {

    companion object {

        fun putTranslation(event: CommandSetupEvent) {
            if (event.isCancelled()) return
            val translation = event.getCommand().getCommandTranslation()
            val commandData = event.getCommandData()
            commandData.name = event.getCommand().getLocation().path.split("/")[0]
            if (event.getCommand().getCommandType() == CommandType.SLASH) {
                commandData as SlashCommandData
                commandData.description = translation.getCommandDescription().getString()
            }
            for (locale in DiscordLocale.values()) {
                if (locale == DiscordLocale.UNKNOWN) continue
                val sharkLocale = shark.data.resource.Locale.fromDiscord(locale) ?: shark.data.resource.Locale.default
                commandData.setNameLocalization(locale, translation.getCommandFullName().getString(sharkLocale))
                if (event.getCommand().getCommandType() == CommandType.SLASH) {
                    val slashData = commandData as SlashCommandData
                    slashData.setDescriptionLocalization(locale, translation.getCommandFullName().getString(sharkLocale))
                }
            }
        }

    }

    private var location: ResourceLocation? = null
    protected open val defaultCommandTranslation = CommandTranslation(this)

    open fun getCommandTranslation() = defaultCommandTranslation

    open fun getCommandType() = CommandType.SLASH
    abstract fun run(event: CommandInteractionEvent): Promise<Any?>
    open fun setup(event: CommandSetupEvent) {}

    fun getLocation() = if (location != null) location!! else SharkRegistries.commands[this].also {
        this.location = it
    }

}
