package shark.data.entity.user

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.Interaction
import shark.SharkBot
import shark.data.registry.ResourceLocation
import shark.data.resource.Locale
import shark.data.serialization.CompoundTag
import shark.event.network.InteractionEvent
import shark.network.SharkClient
import shark.util.DataHolder
import shark.util.DataUtil

class UserData {

    var meta: Map<String, Any?> = hashMapOf()
    var tag: Map<String, Any?> = hashMapOf()

    var locale: String = SharkBot.sharkConfig.defaultLanguage

}

object UserMetaField {

    val locale = ResourceLocation.of("locale")

}

class User(private val dataHolder: DataHolder<UserData>, private val id: String) {

    private val tag: CompoundTag = CompoundTag()
    private val meta: CompoundTag = CompoundTag()

    fun getName() = SharkBot.client.getClient().getUserById(id)!!.name
    fun getEffectiveName() = SharkBot.client.getClient().getUserById(id)!!.effectiveName
    fun getMemberEffectiveName(guild: Guild) = guild.getMemberById(id)!!.effectiveName
    fun getMemberNickName(guild: Guild) = guild.getMemberById(id)!!.nickname

    fun getDataHolder() = dataHolder

    fun saveTag() = this.also {
        val data = getDataHolder().get()
        data.tag = tag.saveAsObject()
        getDataHolder().set(data)
    }

    fun saveMeta() = this.also {
        val data = getDataHolder().get()
        data.meta = meta.saveAsObject()
        getDataHolder().set(data)
    }

    fun save() = this.also {
        saveTag()
        saveMeta()
    }

    fun modifyTag(block: CompoundTag.(user: User) -> Unit) = this.also {
        block(tag, this)
        saveTag()
    }

    fun modifyMeta(block: CompoundTag.(user: User) -> Unit) = this.also {
        block(meta, this)
        saveMeta()
    }

    companion object {
        val users: ArrayList<User> = arrayListOf()
        fun of(id: String) = User(DataUtil.useSharkFileData("users/$id", DataUtil.FileType.BSON), id).also(users::add)
        fun of(user: net.dv8tion.jda.api.entities.User) = of(user.id)
        fun of(member: Member) = of(member.user)
        fun of(interaction: Interaction) = of(interaction.user)
        fun of(interactionEvent: InteractionEvent<*>) = of(interactionEvent.getInteraction())
        fun of(bot: SharkClient) = of(bot.getClient().selfUser)
    }

    init {
        meta.load(dataHolder.get().meta)
        tag.load(dataHolder.get().tag)
    }

    fun setLocale(locale: Locale) {
        meta.putString(UserMetaField.locale, locale.name)
    }

    fun setLocale(locale: DiscordLocale) = setLocale(Locale.fromDiscord(locale) ?: Locale.default )

    fun getLocale() = Locale.getByName(meta.getString(UserMetaField.locale))

}