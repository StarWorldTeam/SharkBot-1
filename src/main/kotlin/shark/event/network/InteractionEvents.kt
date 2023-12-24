package shark.event.network

import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.Interaction
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import shark.event.Event
import shark.network.command.Command
import shark.util.promise

open class InteractionEvent<T : Interaction>(private val interaction: T) : Event() {

    open fun getInteraction() = interaction

}

class CommandInteractionEvent(private val command: Command, interaction: GenericCommandInteractionEvent) : InteractionEvent<GenericCommandInteractionEvent>(interaction) {

    fun getCommand() = command

    fun reply(message: String) = promise {
        getInteraction().reply(message).queue(::resolve, ::reject)
    }

    fun reply(data: MessageCreateData) = promise {
        getInteraction().reply(data).queue(::resolve, ::reject)
    }

}