package shark.event.network

import net.dv8tion.jda.api.events.message.GenericMessageEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import shark.event.Event
import shark.network.SharkClient
import shark.network.chat.ChatMessageReceivedSession

open class MessageEvent(private val event: GenericMessageEvent, private val client: SharkClient) : Event() {

    open fun getMessageEvent() = event

    fun getClient() = client

}

class MessageReceivedEvent(event: MessageReceivedEvent, client: SharkClient) : MessageEvent(event, client) {

    private val session = ChatMessageReceivedSession(this)

    override fun getMessageEvent(): MessageReceivedEvent = super.getMessageEvent() as MessageReceivedEvent
    fun getMessage() = getMessageEvent().message

    fun getSession() = session

}

