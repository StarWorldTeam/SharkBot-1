package shark.event.application.resource

import shark.data.registry.ResourceLocation
import shark.data.resource.ResourceLoader
import shark.data.resource.SharkResource
import shark.event.Event


class ResourceLoadEvent(
    val location: ResourceLocation,
    val resources: List<SharkResource>,
    val loader: ResourceLoader
) : Event()