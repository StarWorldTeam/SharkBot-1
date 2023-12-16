package shark.network.chat

import j2html.TagCreator
import j2html.tags.Renderable
import org.jsoup.Jsoup
import shark.data.resource.Locale


abstract class Component {

    open fun getString(locale: Locale) = getString()
    abstract fun getString(): String
    /** 获取可以渲染为HTML的内容 */
    open fun getDomContent(locale: Locale) = getDomContent()
    /** 获取可以渲染为HTML的内容 */
    abstract fun getDomContent(): Renderable

    override fun toString() = getString()

    companion object {

        fun text(text: String) = literal(TagCreator.text(text).render())

        fun literal(literalString: String): Component {
            return object : Component() {
                override fun getString(): String {
                    val content = getDomContent().render()
                    val doc = Jsoup.parse(content)
                    return doc.text()
                }
                override fun getDomContent() = TagCreator.rawHtml(literalString)
            }
        }

        fun newLine(): Component {
            return object : Component() {
                override fun getDomContent() = TagCreator.br()
                override fun getString() = "\n"
            }
        }

        fun translatable(key: String, vararg parameters: Any?, defaultValue: (component: Component) -> Component? = { null }): Component {
            return object : Component() {
                override fun getString(locale: Locale): String {
                    if (key !in locale) {
                        val value = defaultValue(this)
                        if (value != null) return value.getString(locale)
                    }
                    return literal(locale.format(key, parameters)).getString(locale)
                }

                override fun getString() = getString(Locale.default)
                override fun getDomContent() = getDomContent(Locale.default)

                override fun getDomContent(locale: Locale): Renderable {
                    if (key !in locale) {
                        val value = defaultValue(this)
                        if (value != null) return value.getDomContent(locale)
                    }
                    return literal(locale.format(key, parameters)).getDomContent(locale)
                }
            }
        }

    }
}