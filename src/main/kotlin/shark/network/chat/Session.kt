package shark.network.chat

import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageData
import shark.data.entity.user.User
import shark.event.network.CommandInteractionEvent
import shark.event.network.MessageReceivedEvent

interface Session {

    fun getUser(): User
    fun getUserId(): Long

    fun getBot(): User
    fun getBotId(): Long

    fun getMessage(): MessageData

}

open class ChatMessageReceivedSession(private val event: MessageReceivedEvent) : Session {

    private val message = MessageCreateBuilder.fromMessage(event.getMessage())

    override fun getMessage() = message

    override fun getUser() = User.of(event.getMessageEvent().author)
    override fun getUserId() = event.getMessageEvent().author.idLong
    override fun getBot() = User.of(event.getClient())
    override fun getBotId() = event.getClient().getClient().selfUser.idLong

    override fun toString() = "[${event.getMessageEvent().guild.name} @${event.getMessageEvent().guild.idLong}] [${event.getMessageEvent().channel.name} @${event.getMessageEvent().channel.idLong}] [${getUser().getEffectiveName()} (${getUser().getName()}) @${getUserId()}] ${getMessage().content}"

}

open class CommandSession(private val event: CommandInteractionEvent) : Session {

    private val message = MessageCreateBuilder().addContent(event.getInteraction().commandString).build()

    override fun getUser() = User.of(event.getInteraction().user)
    override fun getBot() = User.of(event.getClient())
    override fun getUserId() = event.getInteraction().user.idLong
    override fun getBotId() = event.getClient().getClient().selfUser.idLong

    override fun getMessage() = message

    override fun toString() = "[${event.getInteraction().guild!!.name} @${event.getInteraction().guild!!.idLong}]  [${event.getInteraction().channel!!.name} @${event.getInteraction().channel!!.idLong}]  [${getUser().getEffectiveName()} (${getUser().getName()}) @${getUserId()}] ${getMessage().content}"

}
