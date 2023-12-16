package shark.event

import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

open class WrappedEvent<T : Event>(val eventBus: EventBus, val callback: (WrappedEvent<T>) -> Unit, val wrappedEvent: T) {

    fun isEvent(eventType: KClass<*>) = eventType.isInstance(wrappedEvent)

    inline fun <reified T : Event> isEvent() = isEvent(T::class)

    inline fun <reified T : Event> isEvent(block: (T) -> Unit) {
        if (isEvent<T>()) block(wrappedEvent as T)
    }


}

open class EventBus constructor(val name: String) {

    constructor(name: Class<*>) : this(name.name)
    constructor(name: KClass<*>) : this(name.java.name)

    val eventListeners = arrayListOf<Pair<KClass<in Event>, (WrappedEvent<*>) -> Unit>>()

    inline fun <reified T : Event> on(noinline callback: (WrappedEvent<T>) -> Unit): (WrappedEvent<T>) -> Unit =
        on(T::class, callback)

    @Suppress("UNCHECKED_CAST")
    fun <T : Event> on(type: KClass<T>, callback: (WrappedEvent<T>) -> Unit): (WrappedEvent<T>) -> Unit {
        for (listener in eventListeners) {
            if (listener.first == type && listener.second == callback) return callback
        }
        eventListeners.add(Pair(type as KClass<in Event>, callback as (WrappedEvent<*>) -> Unit))
        return callback
    }

    inline fun <reified T : Event> off(noinline callback: (WrappedEvent<T>) -> Unit): (WrappedEvent<T>) -> Unit =
        off(T::class, callback)

    fun <T : Event> off(type: KClass<T>, callback: (WrappedEvent<T>) -> Unit): (WrappedEvent<T>) -> Unit {
        eventListeners.removeIf {
            (it.first == type) && (it.second == callback)
        }
        return callback
    }

    fun <T : Event> emit(event: T) = this.also {
        for (pair in listAllListeners(event.javaClass.kotlin)) {
            event.setEventBus(this)
            pair.second(WrappedEvent<Event>(this, pair.second, event))
        }
    }

    inline fun <reified T : Event> listAllListeners() = listAllListeners(T::class)

    fun <T : Any> listAllListeners(eventType: KClass<T>): Sequence<Pair<KClass<in Event>, (WrappedEvent<*>) -> Unit>> {
        return sequence {
            for (pair in eventListeners) {
                if (pair.first.isSuperclassOf(eventType) || pair.first == eventType) {
                    yield(pair)
                }
            }
        }
    }

    fun offIf(filter: (KClass<in Event>, (WrappedEvent<*>) -> Unit) -> Boolean) =
        eventListeners.removeIf { filter(it.first, it.second) }

    override fun toString() = "EventBus($name)"

}