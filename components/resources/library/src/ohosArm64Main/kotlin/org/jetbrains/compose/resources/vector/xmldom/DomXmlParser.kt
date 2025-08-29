package org.jetbrains.compose.resources.vector.xmldom


import nl.adaptivity.xmlutil.DomWriter
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlUtilInternal
import nl.adaptivity.xmlutil.writeCurrent
import nl.adaptivity.xmlutil.xmlStreaming
import nl.adaptivity.xmlutil.dom2.length
import nl.adaptivity.xmlutil.dom2.Element as XElem
import nl.adaptivity.xmlutil.dom2.Node as XNode
import nl.adaptivity.xmlutil.dom2.NodeList as XNodeList

// ========== 解析入口 ==========
@OptIn(XmlUtilInternal::class)
internal fun parse(xml: String): Element {
    // 1) 用事件流读取字符串
    val reader =
        xmlStreaming.newReader(xml) // 等价于 decodeFromReader 使用的 reader 构造。 [oai_citation:2‡pdvrieze.github.io](https://pdvrieze.github.io/xmlutil/serialization/nl.adaptivity.xmlutil.serialization/-x-m-l/index.html?utm_source=chatgpt.com)
    // 2) 把事件“抄写”进 DomWriter 构建 DOM
    val writer = DomWriter()
    do {
        val event = reader.next()
        reader.writeCurrent(writer) // 官方建议用法。 [oai_citation:3‡GitHub](https://github.com/pdvrieze/xmlutil/discussions/165)
    } while (event != EventType.END_DOCUMENT)

    // 3) 拿到 Document 的根元素并做适配
    val doc = writer.target
    val root = doc.getDocumentElement() ?: error("XML parse error: no root element")
    return XmlUtilElement(root)
}

// ========== 适配器，把 xmlutil DOM 包装为你的接口 ==========
private open class XmlUtilNode(private val node: XNode) : Node {
    override val textContent: String?
        get() = node.getTextContent()

    override val nodeName: String
        get() = node.getNodeName()

    override val localName: String
        get() = (node as? XElem)?.getLocalName() ?: node.getNodeName()

    override val namespaceURI: String
        get() = (node as? XElem)?.getNamespaceURI() ?: ""

    override val childNodes: NodeList
        get() = XmlUtilNodeList(node.getChildNodes())

    override fun lookupPrefix(namespaceURI: String): String =
        (node as? XElem)?.lookupPrefix(namespaceURI) ?: ""
}

private class XmlUtilElement(private val elem: XElem) : XmlUtilNode(elem), Element {
    override fun getAttributeNS(nameSpaceURI: String, localName: String): String =
        elem.getAttributeNS(nameSpaceURI, localName) ?: ""

    override fun getAttribute(name: String): String =
        elem.getAttribute(name) ?: ""
}

private class XmlUtilNodeList(private val nodeList: XNodeList) : NodeList {
    override fun item(i: Int): Node {
        val node = nodeList.item(i) ?: error("Index out of bounds")
        return if (node is XElem)
            XmlUtilElement(node)
        else
            XmlUtilNode(node)
    }

    override val length: Int
        get() = nodeList.length
}