package shark.network.chat

import j2html.TagCreator
import j2html.tags.Renderable
import shark.data.resource.Locale

open class MutableComponent(vararg components: Component) : Component() {

    open val components = mutableListOf(*components)

    override fun getString(): String = components.stream().map { obj: Component -> obj.toString() }.toList().joinToString("")
    override fun getDomContent(): Renderable = TagCreator.rawHtml(
        components.stream().map(Component::getDomContent).map(Renderable::render).toList().joinToString("")
    )
    override fun getString(locale: Locale): String {
        return components.stream().map { i: Component -> i.getString(locale) }.toList().joinToString("")
    }
    override fun getDomContent(locale: Locale): Renderable {
        return TagCreator.rawHtml(components.stream().map { i: Component -> i.getDomContent(locale) }.map<Any>(Renderable::render).toList().joinToString(""))
    }

    open fun append(component: Component) = this.also {
        components.add(component)
    }

    open fun append(string: String) = this.also {
        components.add(literal(string))
    }

    open fun append(renderable: Renderable) = this.also {
        components.add(literal(renderable.render()))
    }

}