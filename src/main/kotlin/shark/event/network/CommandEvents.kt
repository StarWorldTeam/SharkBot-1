package shark.event.network

import net.dv8tion.jda.api.interactions.commands.build.CommandData
import shark.annotation.Property
import shark.event.Event
import shark.network.command.Command

open class CommandSetupEvent(
    @field:Property("command") private val command: Command,
    @field:Property("commandData") private val commandData: CommandData
) : Event() {

    override fun isCancellable() = true

    fun getCommand() = command
    fun getCommandData() = commandData

}