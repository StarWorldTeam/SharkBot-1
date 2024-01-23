package shark.event.network

import kodash.coroutine.promise
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.Interaction
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import shark.event.Event
import shark.network.SharkClient
import shark.network.chat.CommandSession
import shark.network.command.Command

open class InteractionEvent<T : Interaction>(private val interaction: T, private val client: SharkClient) : Event() {

    open fun getInteraction() = interaction
    open fun getClient() = client

}

class CommandInteractionEvent(private val command: Command, interaction: GenericCommandInteractionEvent, client: SharkClient) : InteractionEvent<GenericCommandInteractionEvent>(interaction, client) {

    private val session = CommandSession(this)

    fun getCommand() = command
    fun getSession() = session

    fun reply(message: String) = promise {
        getInteraction().reply(message).queue(::resolve, ::reject)
    }

    fun reply(data: MessageCreateData) = promise {
        getInteraction().reply(data).queue(::resolve, ::reject)
    }

}