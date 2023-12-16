package shark.data.registry

import com.google.common.collect.HashBiMap
import org.jetbrains.annotations.NotNull
import shark.network.command.Command
import java.util.*
import kotlin.collections.HashSet

object SharkRegistries {

    val commands = RegistryManager.DEFAULT.getRegistry<Command>(ResourceLocation.of("command"))

}

interface Registrable

open class TagKey (private val location: ResourceLocation) {

    fun getLocation() = location
    open fun splitPath () = getLocation().path.split("/").filter { it.isNotEmpty() }.toTypedArray()

    open fun isChildOf(key: TagKey): Boolean {
        val thisSplitPath = splitPath()
        val keySplitPath = key.splitPath()
        if (thisSplitPath.size <= keySplitPath.size) return false
        keySplitPath.forEachIndexed { index, value ->
            if (thisSplitPath[index] != value) return false
        }
        return true
    }

    open fun isParentOf(key: TagKey) = key.isChildOf(this)

    override fun hashCode() = getLocation().hashCode()

    override fun equals(other: Any?) = other != null && other is TagKey && other.hashCode() == this.hashCode()

}

open class TagManager <T : Registrable> internal constructor(private val registry: Registry <T>) {

    private val entries: HashMap <TagKey, HashSet<T>> = hashMapOf()

    open fun getTags() = entries.keys
    open fun getRegistry() = registry
    open fun createTagKey(location: ResourceLocation) = (entries.keys.find { i -> i.getLocation() == location } ?: TagKey(location)).also {
        if (it !in entries.keys) entries[it] = hashSetOf()
    }
    open fun getEntries(): Set<Pair<TagKey, Set<T>>> = Collections.unmodifiableSet(entries.entries.map { Pair(it.key, Collections.unmodifiableSet(it.value)) }.toSet())


    open fun addValues(key: TagKey, vararg values: T) = entries.getOrPut(key, ::hashSetOf).addAll(values)

    open fun getSelfValues(tagKey: TagKey): Set<T> = Collections.unmodifiableSet(entries[tagKey]!!.toSet())

    open fun getChildValues(tagKey: TagKey): Set<T> {
        val result = hashSetOf<T>()
        for (i in getEntries())
            if (i.first.isChildOf(tagKey)) result.addAll(i.second)
        return Collections.unmodifiableSet(result)
    }

    open fun removeTags(vararg tagKeys: TagKey) = tagKeys.forEach(entries::remove)
    open fun removeValues(tagKey: TagKey, vararg values: T) {
        if (tagKey in entries) entries[tagKey]!!.removeIf { it in values }
    }

    open fun getValues(tagKey: TagKey): Set<T> = Collections.unmodifiableSet(
        hashSetOf<T>().also {
            it.addAll(getSelfValues(tagKey))
            it.addAll(getChildValues(tagKey))
        }
    )

    override fun hashCode() = registry.hashCode()
    override fun equals(other: Any?) = other != null && other is TagManager <*> && other.hashCode() == this.hashCode()

}

@Suppress("LeakingThis")
open class Registry <T : Registrable> {

    private val entries: ResourceLocationBiMap <T> = HashBiMap.create()
    private val tagManager = TagManager(this)

    open fun getEntries(): Set<Pair<ResourceLocation, T>> = Collections.unmodifiableSet(entries.entries.map { Pair(it.key, it.value) }.toSet())
    open fun getTagManager() = tagManager

    fun register(@NotNull location: ResourceLocation, @NotNull function: (location: ResourceLocation) -> T) = register(location, function(location))
    fun register(@NotNull location: ResourceLocation, supplier: () -> T) = register(location, supplier())
    open fun register(@NotNull location: ResourceLocation, @NotNull value: T): T {
        require(location !in entries) { "Registry entry already present: $location" }
        entries[location] = value
        return value
    }

    open fun getEntry(location: ResourceLocation) = entries[location]!!
    open fun getLocation(entry: T) = entries.inverse()[entry]!!

    operator fun get(location: ResourceLocation) = getEntry(location)
    operator fun get(entry: T) = getLocation(entry)

    operator fun set(@NotNull location: ResourceLocation, @NotNull function: (location: ResourceLocation) -> T) = register(location, function)
    operator fun set(@NotNull location: ResourceLocation, @NotNull supplier: () -> T) = register(location, supplier)
    operator fun set(@NotNull location: ResourceLocation, @NotNull value: T) = register(location, value)

}

open class RegistryManager(private val location: ResourceLocation) {

    private val registries: ResourceLocationBiMap <Registry <*>> = HashBiMap.create()

    companion object {
        val DEFAULT = RegistryManager(ResourceLocation.of("registry_manager"))
    }

    fun getLocation () = location

    @Suppress("UNCHECKED_CAST")
    open fun <T : Registrable> getRegistry(key: ResourceLocation): Registry <T> {
        if (key !in registries) registries[key] = Registry<T>()
        return registries[key] as Registry<T>
    }
    operator fun get(key: ResourceLocation) = getRegistry<Registrable>(key)

}
