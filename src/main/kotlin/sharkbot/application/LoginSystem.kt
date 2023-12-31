package sharkbot.application

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.google.common.collect.HashBiMap
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.dv8tion.jda.api.exceptions.InvalidTokenException
import org.apache.catalina.util.URLEncoder
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.util.UriComponentsBuilder
import org.thymeleaf.templatemode.TemplateMode
import shark.SharkBot
import shark.data.entity.user.User
import shark.data.resource.Locale
import shark.util.WebUtil
import shark.util.mapToJson
import java.net.URI
import java.nio.charset.Charset
import java.util.*
import java.util.stream.Collectors
import kotlin.random.Random

class LoginError (val user: String, message: String? = null) : RuntimeException(message)

@RestController
object LoginSystem {

    @RequestMapping(value = ["/login", "/login/"])
    fun loginPage(
        @RequestParam(name = "redirect", defaultValue = "/login") redirectUrl: String,
        @RequestParam("locale", required = false) localeName: String?
    ) = WebUtil.template("sharkbot:template/login.html", TemplateMode.HTML) {
        val locale = if (localeName != null) Locale.getByName(localeName) else Locale.default
        put("locale", locale)
        put("redirect", URLEncoder.DEFAULT.encode(redirectUrl, Charset.forName("utf-8")))
    }

    fun auth(request: HttpServletRequest, block: (user: User) -> Any?): Any? {
        try {
            val rawCookie = request.cookies
            val cookie = rawCookie.toList().stream().collect(Collectors.toMap({ it.name }, { it.value }))
            if ("sharkBotAccessToken" in cookie) {
                val result = tokenLogin(cookie["sharkBotAccessToken"]!!)
                if (result.isSuccess && result.getOrNull() != null)
                    return block(result.getOrThrow())
            }
        } catch (_: Throwable) {}
        return ModelAndView("redirect:/login?redirect=" + URLEncoder.DEFAULT.encode(request.requestURI, Charset.forName("utf-8")))
    }

    fun auth(token: String, uri: String, block: (user: User) -> Any?): Any? {
        try {
            val result = tokenLogin(token)
            if (result.isSuccess && result.getOrNull() != null)
                return block(result.getOrThrow())
        } catch (_: Throwable) {}
        return ModelAndView("redirect:/login?redirect=" + URLEncoder.DEFAULT.encode(uri, Charset.forName("utf-8")))
    }

    @RequestMapping(value = ["/api/login", "/api/login/"])
    fun loginMapping(response: HttpServletResponse, @RequestParam("redirect", required = false) redirect: String?, @RequestParam("userName") userName: String, @RequestParam("passWord") passWord: String): Any {
        val result = login(userName, passWord)
        if (result.isSuccess)
            response.addCookie(Cookie("sharkBotAccessToken", result.getOrNull().toString()).also { it.maxAge = Int.MAX_VALUE; it.path = "/" })
        if (redirect != null) {
            val redirectUri = UriComponentsBuilder.fromUri(URI(redirect))
            redirectUri.queryParam("sharkLoginStatus", if (result.isSuccess) "success" else "failure")
            response.sendRedirect(redirectUri.build().toUriString())
        }
        if (result.isFailure)
            return ResponseEntity.status(401)
                .let {
                    val headers = HttpHeaders()
                    headers.contentType = MediaType.APPLICATION_JSON
                    it.headers(headers)
                }
                .body(
                    mapToJson(
                        mapOf(
                            Pair("type", "failure"),
                            Pair("error", result.exceptionOrNull()?.toString())
                        )
                    )
                )
        else return ResponseEntity.status(200)
            .let {
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON
                it.headers(headers)
            }
            .body(
                mapToJson(
                    mapOf(
                        Pair("type", "success"),
                        Pair("token", result.getOrNull())
                    )
                )
            )
    }

    private val sessions = HashBiMap.create<String, String>()
    private val inverseSessions = sessions.inverse()

    /**
     * @return 返回Token
     */
    fun login(userName: String, passWord: String): Result <String> {
        if (userName !in SharkBot.applicationConfig.sharkBotUsers)
            return Result.failure(LoginError(userName, "User not found"))
        if (SharkBot.applicationConfig.sharkBotUsers[userName] != passWord)
            return Result.failure(LoginError(userName, "Invalid password"))
        val jwt = JWT.create()
        jwt.withIssuedAt(Date())
        jwt.withIssuer(userName)
        val claims = mutableMapOf<String, String>()
        claims["passWord"] = passWord.hashCode().toString(8)
        claims["randomInteger"] = Random.nextInt(0, 0xFFFF).toString(8)
        claims["shark"] = "${SharkBot.baseDir}-${SharkBot.startTime}".hashCode().toString(8)
        claims.entries.forEach { jwt.withClaim(it.key, it.value) }
        val token = jwt.sign(Algorithm.HMAC256(SharkBot.startTime.toString()))
        sessions[userName] = token
        return Result.success(token)
    }

    fun tokenLogin(token: String): Result<User> {
        return if (token in inverseSessions) Result.success(User.of(inverseSessions[token]!!))
        else Result.failure(InvalidTokenException())
    }

}