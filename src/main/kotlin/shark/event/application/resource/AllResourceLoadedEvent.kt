package shark.event.application.resource

import shark.annotation.Property
import shark.data.resource.ResourceLoader
import shark.event.Event

class AllResourceLoadedEvent(
    @field:Property("loader") val loader: ResourceLoader
) : Event()