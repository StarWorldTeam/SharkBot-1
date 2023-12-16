package shark.event

import shark.annotation.Property
import java.lang.reflect.Field
import java.util.*

abstract class Event {

    protected var bus: EventBus? = null
    protected var cancelled = false

    open fun isCancellable() = false
    open fun isCancelled() = isCancellable() && cancelled

    open fun setCancelled(cancelled: Boolean) = this.also {
        this.cancelled = cancelled
    }

    open fun getEventBus() = bus
    open fun setEventBus(bus: EventBus) = this.also {
        it.bus = bus
    }

    open fun getEventName(): String { return this.javaClass.simpleName }

    open fun getDisplayString(): String {
        val properties: MutableList<String> = ArrayList()
        for (i in Arrays.stream(this.javaClass.declaredFields).filter { i: Field ->
            i.isAnnotationPresent(Property::class.java)
        }.toList()) {
            try {
                i.isAccessible = true
                properties.add(i.getAnnotation(Property::class.java).name + "=" + i[this])
            } catch (ignored: Throwable) { }
        }
        return getEventName() + "(" + java.lang.String.join(", ", properties) + ")"
    }

}