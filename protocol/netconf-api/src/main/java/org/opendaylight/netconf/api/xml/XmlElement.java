/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.xml;

import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.xml.XMLConstants;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

public final class XmlElement {

    public static final String DEFAULT_NAMESPACE_PREFIX = "";

    private final Element element;
    private static final Logger LOG = LoggerFactory.getLogger(XmlElement.class);

    private XmlElement(final Element element) {
        this.element = element;
    }

    public static XmlElement fromDomElement(final Element element) {
        return new XmlElement(element);
    }

    public static XmlElement fromDomDocument(final Document xml) {
        return new XmlElement(xml.getDocumentElement());
    }

    public static XmlElement fromString(final String str) throws DocumentedException {
        try {
            return new XmlElement(XmlUtil.readXmlToElement(str));
        } catch (IOException | SAXException e) {
            throw DocumentedException.wrap(e);
        }
    }

    public static XmlElement fromDomElementWithExpected(final Element element, final String expectedName)
            throws DocumentedException {
        XmlElement xmlElement = XmlElement.fromDomElement(element);
        xmlElement.checkName(expectedName);
        return xmlElement;
    }

    public static XmlElement fromDomElementWithExpected(final Element element, final String expectedName,
            final String expectedNamespace) throws DocumentedException {
        XmlElement xmlElement = XmlElement.fromDomElementWithExpected(element, expectedName);
        xmlElement.checkNamespace(expectedNamespace);
        return xmlElement;
    }

    private Map<String, String> extractNamespaces() throws DocumentedException {
        Map<String, String> namespaces = new HashMap<>();
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String attribKey = attribute.getNodeName();
            if (attribKey.startsWith(XMLConstants.XMLNS_ATTRIBUTE)) {
                String prefix;
                if (attribKey.equals(XMLConstants.XMLNS_ATTRIBUTE)) {
                    prefix = DEFAULT_NAMESPACE_PREFIX;
                } else {
                    if (!attribKey.startsWith(XMLConstants.XMLNS_ATTRIBUTE + ":")) {
                        throw new DocumentedException("Attribute doesn't start with :",
                                ErrorType.APPLICATION, ErrorTag.INVALID_VALUE, ErrorSeverity.ERROR);
                    }
                    prefix = attribKey.substring(XMLConstants.XMLNS_ATTRIBUTE.length() + 1);
                }
                namespaces.put(prefix, attribute.getNodeValue());
            }
        }

        // namespace does not have to be defined on this element but inherited
        if (!namespaces.containsKey(DEFAULT_NAMESPACE_PREFIX)) {
            Optional<String> namespaceOptionally = getNamespaceOptionally();
            if (namespaceOptionally.isPresent()) {
                namespaces.put(DEFAULT_NAMESPACE_PREFIX, namespaceOptionally.get());
            }
        }

        return namespaces;
    }

    public void checkName(final String expectedName) throws UnexpectedElementException {
        if (!getName().equals(expectedName)) {
            throw new UnexpectedElementException(
                    String.format("Expected %s xml element but was %s", expectedName, getName()),
                    ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR);
        }
    }

    public void checkNamespaceAttribute(final String expectedNamespace)
            throws UnexpectedNamespaceException, MissingNameSpaceException {
        if (!getNamespaceAttribute().equals(expectedNamespace)) {
            throw new UnexpectedNamespaceException(
                    String.format("Unexpected namespace %s should be %s", getNamespaceAttribute(), expectedNamespace),
                    ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR);
        }
    }

    public void checkNamespace(final String expectedNamespace)
            throws UnexpectedNamespaceException, MissingNameSpaceException {
        if (!getNamespace().equals(expectedNamespace)) {
            throw new UnexpectedNamespaceException(
                    String.format("Unexpected namespace %s should be %s", getNamespace(), expectedNamespace),
                    ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR);
        }
    }

    public String getName() {
        final String localName = element.getLocalName();
        if (!Strings.isNullOrEmpty(localName)) {
            return localName;
        }
        return element.getTagName();
    }

    public String getAttribute(final String attributeName) {
        return element.getAttribute(attributeName);
    }

    public String getAttribute(final String attributeName, final String namespace) {
        return element.getAttributeNS(namespace, attributeName);
    }

    public NodeList getElementsByTagName(final String name) {
        return element.getElementsByTagName(name);
    }

    public void appendChild(final Element toAppend) {
        element.appendChild(toAppend);
    }

    public Element getDomElement() {
        return element;
    }

    public Map<String, Attr> getAttributes() {

        Map<String, Attr> mappedAttributes = new HashMap<>();

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            mappedAttributes.put(attr.getNodeName(), attr);
        }

        return mappedAttributes;
    }

    /**
     * Non recursive.
     */
    private List<XmlElement> getChildElementsInternal(final ElementFilteringStrategy strat) {
        NodeList childNodes = element.getChildNodes();
        final List<XmlElement> result = new ArrayList<>();
        for (int i = 0; i < childNodes.getLength(); i++) {
            if (childNodes.item(i) instanceof Element elem && strat.accept(elem)) {
                result.add(new XmlElement(elem));
            }
        }

        return result;
    }

    public List<XmlElement> getChildElements() {
        return getChildElementsInternal(e -> true);
    }

    /**
     * Returns the child elements for the given tag.
     *
     * @param tagName tag name without prefix
     * @return List of child elements
     */
    public List<XmlElement> getChildElements(final String tagName) {
        return getChildElementsInternal(e -> e.getLocalName().equals(tagName));
    }

    public List<XmlElement> getChildElementsWithinNamespace(final String childName, final String namespace) {
        return Lists.newArrayList(Collections2.filter(getChildElementsWithinNamespace(namespace),
            xmlElement -> xmlElement.getName().equals(childName)));
    }

    public List<XmlElement> getChildElementsWithinNamespace(final String namespace) {
        return getChildElementsInternal(e -> {
            try {
                return XmlElement.fromDomElement(e).getNamespace().equals(namespace);
            } catch (final MissingNameSpaceException e1) {
                return false;
            }
        });
    }

    public Optional<XmlElement> getOnlyChildElementOptionally(final String childName) {
        List<XmlElement> nameElements = getChildElements(childName);
        if (nameElements.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(nameElements.get(0));
    }

    public Optional<XmlElement> getOnlyChildElementOptionally(final String childName, final String namespace) {
        List<XmlElement> children = getChildElementsWithinNamespace(namespace);
        children = Lists.newArrayList(Collections2.filter(children,
            xmlElement -> xmlElement.getName().equals(childName)));
        if (children.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(children.get(0));
    }

    public Optional<XmlElement> getOnlyChildElementOptionally() {
        List<XmlElement> children = getChildElements();
        if (children.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(children.get(0));
    }

    public XmlElement getOnlyChildElementWithSameNamespace(final String childName) throws  DocumentedException {
        return getOnlyChildElement(childName, getNamespace());
    }

    public XmlElement getOnlyChildElementWithSameNamespace() throws DocumentedException {
        XmlElement childElement = getOnlyChildElement();
        childElement.checkNamespace(getNamespace());
        return childElement;
    }

    public Optional<XmlElement> getOnlyChildElementWithSameNamespaceOptionally(final String childName) {
        Optional<String> namespace = getNamespaceOptionally();
        if (namespace.isPresent()) {
            List<XmlElement> children = getChildElementsWithinNamespace(namespace.get());
            children = Lists.newArrayList(Collections2.filter(children,
                xmlElement -> xmlElement.getName().equals(childName)));
            if (children.size() != 1) {
                return Optional.empty();
            }
            return Optional.of(children.get(0));
        }
        return Optional.empty();
    }

    public Optional<XmlElement> getOnlyChildElementWithSameNamespaceOptionally() {
        Optional<XmlElement> child = getOnlyChildElementOptionally();
        if (child.isPresent()
                && child.get().getNamespaceOptionally().isPresent()
                && getNamespaceOptionally().isPresent()
                && getNamespaceOptionally().get().equals(child.get().getNamespaceOptionally().get())) {
            return child;
        }
        return Optional.empty();
    }

    public XmlElement getOnlyChildElement(final String childName, final String namespace) throws DocumentedException {
        List<XmlElement> children = getChildElementsWithinNamespace(namespace);
        children = Lists.newArrayList(Collections2.filter(children,
            xmlElement -> xmlElement.getName().equals(childName)));
        if (children.size() != 1) {
            throw new DocumentedException(String.format("One element %s:%s expected in %s but was %s", namespace,
                    childName, toString(), children.size()),
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE, ErrorSeverity.ERROR);
        }

        return children.get(0);
    }

    public XmlElement getOnlyChildElement(final String childName) throws DocumentedException {
        List<XmlElement> nameElements = getChildElements(childName);
        if (nameElements.size() != 1) {
            throw new DocumentedException("One element " + childName + " expected in " + toString(),
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE, ErrorSeverity.ERROR);
        }
        return nameElements.get(0);
    }

    public XmlElement getOnlyChildElement() throws DocumentedException {
        List<XmlElement> children = getChildElements();
        if (children.size() != 1) {
            throw new DocumentedException(
                    String.format("One element expected in %s but was %s", toString(), children.size()),
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE, ErrorSeverity.ERROR);
        }
        return children.get(0);
    }

    public String getTextContent() throws DocumentedException {
        NodeList childNodes = element.getChildNodes();
        if (childNodes.getLength() == 0) {
            return DEFAULT_NAMESPACE_PREFIX;
        }
        for (int i = 0; i < childNodes.getLength(); i++) {
            if (childNodes.item(i) instanceof Text textChild) {
                return textChild.getTextContent().trim();
            }
        }
        throw new DocumentedException(getName() + " should contain text.",
                ErrorType.APPLICATION, ErrorTag.INVALID_VALUE, ErrorSeverity.ERROR);
    }

    public Optional<String> getOnlyTextContentOptionally() {
        // only return text content if this node has exactly one Text child node
        if (element.getChildNodes().getLength() == 1) {
            if (element.getChildNodes().item(0) instanceof Text textChild) {
                return Optional.of(textChild.getWholeText());
            }
        }
        return Optional.empty();
    }

    public String getNamespaceAttribute() throws MissingNameSpaceException {
        String attribute = element.getAttribute(XMLConstants.XMLNS_ATTRIBUTE);
        if (attribute.isEmpty() || attribute.equals(DEFAULT_NAMESPACE_PREFIX)) {
            throw new MissingNameSpaceException(String.format("Element %s must specify namespace", toString()),
                    ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR);
        }
        return attribute;
    }

    public Optional<String> getNamespaceAttributeOptionally() {
        String attribute = element.getAttribute(XMLConstants.XMLNS_ATTRIBUTE);
        if (attribute.isEmpty() || attribute.equals(DEFAULT_NAMESPACE_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(attribute);
    }

    public Optional<String> getNamespaceOptionally() {
        String namespaceURI = element.getNamespaceURI();
        if (Strings.isNullOrEmpty(namespaceURI)) {
            return Optional.empty();
        } else {
            return Optional.of(namespaceURI);
        }
    }

    public String getNamespace() throws MissingNameSpaceException {
        Optional<String> namespaceURI = getNamespaceOptionally();
        if (namespaceURI.isEmpty()) {
            throw new MissingNameSpaceException(String.format("No namespace defined for %s", this),
                    ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR);
        }
        return namespaceURI.get();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("XmlElement{");
        sb.append("name='").append(getName()).append('\'');
        if (element.getNamespaceURI() != null) {
            try {
                sb.append(", namespace='").append(getNamespace()).append('\'');
            } catch (final MissingNameSpaceException e) {
                LOG.trace("Missing namespace for element.");
            }
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * Search for element's attributes defining namespaces. Look for the one
     * namespace that matches prefix of element's text content. E.g.
     *
     * <pre>
     * &lt;type
     * xmlns:th-java="urn:opendaylight:params:xml:ns:yang:controller:threadpool:impl"&gt;
     *     th-java:threadfactory-naming&lt;/type&gt;
     * </pre>
     *
     * <p>
     * returns {"th-java","urn:.."}. If no prefix is matched, then default
     * namespace is returned with empty string as key. If no default namespace
     * is found value will be null.
     */
    public Map.Entry<String/* prefix */, String/* namespace */> findNamespaceOfTextContent()
            throws DocumentedException {
        Map<String, String> namespaces = extractNamespaces();
        String textContent = getTextContent();
        int indexOfColon = textContent.indexOf(':');
        String prefix;
        if (indexOfColon > -1) {
            prefix = textContent.substring(0, indexOfColon);
        } else {
            prefix = DEFAULT_NAMESPACE_PREFIX;
        }
        if (!namespaces.containsKey(prefix)) {
            throw new IllegalArgumentException("Cannot find namespace for " + XmlUtil.toString(element)
                + ". Prefix from content is " + prefix + ". Found namespaces " + namespaces);
        }
        return new SimpleImmutableEntry<>(prefix, namespaces.get(prefix));
    }

    public List<XmlElement> getChildElementsWithSameNamespace(final String childName) throws MissingNameSpaceException {
        List<XmlElement> children = getChildElementsWithinNamespace(getNamespace());
        return Lists.newArrayList(Collections2.filter(children, xmlElement -> xmlElement.getName().equals(childName)));
    }

    public void checkUnrecognisedElements(final List<XmlElement> recognisedElements,
                                          final XmlElement... additionalRecognisedElements) throws DocumentedException {
        List<XmlElement> childElements = getChildElements();
        childElements.removeAll(recognisedElements);
        for (XmlElement additionalRecognisedElement : additionalRecognisedElements) {
            childElements.remove(additionalRecognisedElement);
        }
        if (!childElements.isEmpty()) {
            throw new DocumentedException(String.format("Unrecognised elements %s in %s", childElements, this),
                    ErrorType.APPLICATION, ErrorTag.INVALID_VALUE, ErrorSeverity.ERROR);
        }
    }

    public void checkUnrecognisedElements(final XmlElement... additionalRecognisedElements) throws DocumentedException {
        checkUnrecognisedElements(Collections.emptyList(), additionalRecognisedElements);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        XmlElement that = (XmlElement) obj;

        return element.isEqualNode(that.element);

    }

    @Override
    public int hashCode() {
        return element.hashCode();
    }

    public boolean hasNamespace() {
        return getNamespaceAttributeOptionally().isPresent() || getNamespaceOptionally().isPresent();
    }

    private interface ElementFilteringStrategy {
        boolean accept(Element element);
    }
}
