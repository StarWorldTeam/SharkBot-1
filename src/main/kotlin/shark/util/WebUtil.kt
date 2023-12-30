package shark.util

import org.thymeleaf.TemplateEngine
import org.thymeleaf.TemplateSpec
import org.thymeleaf.context.Context
import org.thymeleaf.templatemode.TemplateMode
import shark.data.registry.ResourceLocation
import sharkbot.data.resource.StaticResourcePool
import java.nio.charset.Charset
import java.util.*

object WebUtil {

    val templateEngine = TemplateEngine()

    /**
     * 调用模板引擎处理静态资源
     * @see StaticResourcePool
     */
    fun template(location: ResourceLocation, mode: TemplateMode, block: MutableMap<String, Any?>.() -> Unit) = template(location.toString(), mode, block)

    /**
     * 调用模板引擎处理静态资源
     * @see StaticResourcePool
     */
    fun template(location: String, mode: TemplateMode, block: MutableMap<String, Any?>.() -> Unit): String {
        val template = StaticResourcePool[location]!!.resource.getContentAsString(Charset.forName("utf-8"))
        val map = mutableMapOf<String, Any?>()
        block(map)
        return templateEngine.process(TemplateSpec(template, null, mode, null), Context(Locale.ROOT, map))
    }

}