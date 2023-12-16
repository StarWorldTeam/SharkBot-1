package shark.event.network

import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.Interaction
import shark.event.Event
import shark.network.command.Command

open class InteractionEvent<T : Interaction>(private val interaction: T) : Event() {

    open fun getInteraction() = interaction

}

class CommandInteractionEvent(private val command: Command, interaction: GenericCommandInteractionEvent) : InteractionEvent<GenericCommandInteractionEvent>(interaction) {

    fun getCommand() = command

}