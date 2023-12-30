package sharkbot.application

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.thymeleaf.templatemode.TemplateMode
import shark.SharkBot
import shark.data.resource.Locale
import shark.util.WebUtil
import sharkbot.data.resource.StaticResourcePool

@RestController
@RequestMapping("/panel")
class WebPanel {

    fun isEnabled() = SharkBot.applicationConfig.webPanel
    fun <T> ifActive(block: () -> T) = if (isEnabled()) block() else null

    @RequestMapping(value = ["/resource"])
    fun resource(@RequestParam("name") resourceName: String) = ifActive {
        return@ifActive StaticResourcePool["sharkbot/resource/panel/${resourceName}"]
    }

    @RequestMapping(value = ["/", "/index", "/index.html"])
    fun index(request: HttpServletRequest, @RequestParam(name = "locale", required = false) localeName: String? = null) = ifActive {
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