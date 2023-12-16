package sharkbot.registry

import shark.data.registry.ResourceLocation
import shark.data.registry.SharkRegistries
import sharkbot.command.LocaleCommand

object Commands {

    fun bootstrap() {}

    val localeCommand = SharkRegistries.commands.register(ResourceLocation.of("locale"), LocaleCommand())

}