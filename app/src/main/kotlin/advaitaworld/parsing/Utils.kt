package advaitaworld.parsing

import org.jsoup.nodes.Element

/**
 * Find first element which matches the cssQuery.
 * If no elements found, RuntimeException will be thrown
 */
private fun Element.selectFirst(cssQuery: String) : Element {
    val elements = this.select(cssQuery)
    if(elements.isEmpty()) {
        throw RuntimeException("failed to find node using css: $cssQuery")
    }
    return elements.get(0)
}
