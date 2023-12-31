package sharkbot.application

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.thymeleaf.templatemode.TemplateMode
import shark.SharkBot
import shark.data.resource.Locale
import shark.util.WebUtil
import sharkbot.application.LoginSystem.auth
import sharkbot.data.resource.StaticResourcePool

@RestController
@RequestMapping("/panel")
class WebPanel {

    fun isEnabled() = SharkBot.applicationConfig.webPanel
    fun <T> ifActive(block: () -> T) = if (isEnabled()) block() else null

    @RequestMapping(value = ["/resource", "/resource/"])
    fun resource(
        @RequestParam("name") resourceName: String,
        @RequestParam("type", defaultValue = "text/plain") mimeType: String
    ) = ifActive {
        val resource = StaticResourcePool["sharkbot:resource/panel/${resourceName}"]!!
        return@ifActive ResponseEntity.ok().contentType(MediaType.parseMediaType(mimeType)).body(resource.resource.contentAsByteArray)
    }

    @RequestMapping(value = ["/", "/index", "/index.html"])
    fun homeView(
        request: HttpServletRequest, @RequestParam(name = "locale", required = false) localeName: String? = null) = ifActive {
        auth(request) {
            val locale = if (localeName != null) Locale.getByName(localeName) else Locale.default
            WebUtil.template("sharkbot:template/panel/index.html", TemplateMode.HTML) {
                putAll(
                    mapOf(
                        Pair("locale", locale),
                        Pair("guilds", SharkBot.client.getClientOptional()?.guilds ?: listOf())
                    )
                )
            }
        }
    }

    @RequestMapping(value = ["/guild/{guild}", "/guild/{guild}/"])
    fun guildView(
        request: HttpServletRequest, @RequestParam(name = "locale", required = false) localeName: String? = null,
        @PathVariable("guild") guildId: Long
    ) = ifActive {
        val guild = SharkBot.client.getClientOptional()?.getGuildById(guildId) ?: return@ifActive null
        return@ifActive auth(request) {
            val locale = if (localeName != null) Locale.getByName(localeName) else Locale.default
            return@auth WebUtil.template("sharkbot:template/panel/guild.html", TemplateMode.HTML) {
                putAll(
                    mapOf(
                        Pair("locale", locale),
                        Pair("guild", guild)
                    )
                )
            }
        }
    }

}