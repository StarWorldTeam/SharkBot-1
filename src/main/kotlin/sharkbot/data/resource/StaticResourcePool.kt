package sharkbot.data.resource

import shark.data.registry.ResourceLocation
import shark.data.resource.SharkResource
import java.util.*
import kotlin.collections.HashMap

object StaticResourcePool {

    private val map: HashMap <ResourceLocation, SharkResource> = hashMapOf()

    operator fun set(key: ResourceLocation, value: SharkResource) = map.put(key, value)
    operator fun get(key: ResourceLocation) = map[key]
    operator fun get(key: String) = get(ResourceLocation.of(key))

    fun all(): Set <Map.Entry<ResourceLocation, SharkResource>> = Collections.unmodifiableSet(map.entries)
    fun values(): Collection<SharkResource> = Collections.unmodifiableCollection(map.values)
    fun keys(): Set<ResourceLocation> = Collections.unmodifiableSet(map.keys)

    operator fun contains(key: ResourceLocation) = map.containsKey(key)
    operator fun contains(key: String) = map.containsKey(ResourceLocation.of(key))
    operator fun contains(key: SharkResource) = map.containsValue(key)

}