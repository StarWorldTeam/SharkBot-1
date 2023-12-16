package shark.data.resource

import j2html.tags.Renderable
import net.dv8tion.jda.api.interactions.DiscordLocale
import shark.SharkBot
import java.util.*

open class Locale {
    protected open val language: HashMap<String, String> = hashMapOf()
    val name: String
        get() = Optional.ofNullable(
            INSTANCES.entries.find { (_, value) -> value == this }
        ).map { it.key }.orElse("unknown")

    open fun toDiscord() = DiscordLocale.from(this["locale.discord.localeTag"])
    open fun getKeys(): Set <String> = Collections.unmodifiableSet(language.keys)
    open fun putTranslation(key: String, value: String) {
        language[key] = value
    }
    open fun getTranslation(key: String) = language.getOrDefault(key, default.getOrDefault(key, key))

    operator fun get(key: String) = getTranslation(key)
    operator fun contains(key: String) = language.containsKey(key)
    operator fun set(key: String, value: String) = putTranslation(key, value)

    fun getOrDefault(key: String, defaultValue: String) = language.getOrDefault(key, defaultValue)

    fun format(key: String, vararg parameters: Any): String {
        return get(key).format(
            java.util.Locale.ROOT,
            *Arrays.stream(parameters).map {
                when(true) {
                    (it is Renderable) -> return@map it.render()
                    else -> return@map it.toString()
                }
            }.toArray()
        )
    }

    companion object {
        val INSTANCES: HashMap<String, Locale> = HashMap()

        operator fun get(name: String) = INSTANCES.getOrDefault(name, default)
        operator fun get(locale: DiscordLocale) = fromDiscord(locale)
        operator fun set(name: String, locale: Locale) = INSTANCES.put(name, locale)

        operator fun contains(locale: DiscordLocale) = fromDiscord(locale) != null
        operator fun contains(key: String) = INSTANCES.containsKey(key)

        val empty = object : Locale() {
            override val language: HashMap<String, String>
                get() = super.language.also { it.clear() }
            override fun putTranslation(key: String, value: String) {}
        }
        val default: Locale
            get() = INSTANCES[SharkBot.sharkConfig.defaultLanguage] ?: empty

        fun fromDiscord(locale: DiscordLocale) = INSTANCES.values.find {
            it.getOrDefault("locale.discord.localeTag", "unknown") == locale.locale
        }

        fun getByName(name: String): Locale {
            return INSTANCES.getOrDefault(name, default)
        }
    }
}