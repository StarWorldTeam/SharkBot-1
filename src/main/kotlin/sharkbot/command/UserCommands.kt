package sharkbot.command

import shark.event.network.CommandInteractionEvent
import shark.network.command.Command

class LocaleCommand : Command() {

    override fun run(event: CommandInteractionEvent) {
        event.getInteraction().reply("test").queue()
    }

}