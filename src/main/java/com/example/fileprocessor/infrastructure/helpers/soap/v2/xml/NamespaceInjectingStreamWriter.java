package com.example.fileprocessor.infrastructure.helpers.soap.v2.xml;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.util.Objects;

/**
 * {@link XMLStreamWriter} decorator that overrides the namespace of every
 * start/empty element with a configured namespace URI. The delegate writer
 * must already have the corresponding prefix mapping registered via
 * {@link XMLStreamWriter#setPrefix(String, String)}.
 *
 * <p>This writer will throw {@link XMLStreamException} if any caller attempts
 * to write an element with a non-empty namespace that differs from the
 * configured one, since that would indicate a mismatch with the JAXB models
 * (which must not specify {@code namespace} on their annotations).
 */
public class NamespaceInjectingStreamWriter implements XMLStreamWriter {

    private final XMLStreamWriter delegate;
    private final String namespace;

    public NamespaceInjectingStreamWriter(XMLStreamWriter delegate, String namespace) {
        this.delegate = Objects.requireNonNull(delegate);
        this.namespace = Objects.requireNonNull(namespace);
    }

    private void assertNamespace(String namespaceURI) throws XMLStreamException {
        if (namespaceURI != null && !namespaceURI.isEmpty() && !namespaceURI.equals(this.namespace)) {
            throw new XMLStreamException(
                "NamespaceInjectingStreamWriter: unexpected namespace '" + namespaceURI
                + "'. Expected '" + this.namespace + "'. "
                + "JAXB models must not specify namespace on @XmlElement.");
        }
    }

    @Override
    public void writeStartElement(String localName) throws XMLStreamException {
        delegate.writeStartElement(namespace, localName);
    }

    @Override
    public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
        assertNamespace(namespaceURI);
        delegate.writeStartElement(namespace, localName);
    }

    @Override
    public void writeStartElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        assertNamespace(namespaceURI);
        delegate.writeStartElement(namespace, localName);
    }

    @Override
    public void writeEmptyElement(String localName) throws XMLStreamException {
        delegate.writeEmptyElement(namespace, localName);
    }

    @Override
    public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
        assertNamespace(namespaceURI);
        delegate.writeEmptyElement(namespace, localName);
    }

    @Override
    public void writeEmptyElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        assertNamespace(namespaceURI);
        delegate.writeEmptyElement(namespace, localName);
    }

    @Override
    public void writeEndElement() throws XMLStreamException { delegate.writeEndElement(); }
    @Override
    public void writeEndDocument() throws XMLStreamException { delegate.writeEndDocument(); }
    @Override
    public void close() throws XMLStreamException { delegate.close(); }
    @Override
    public void flush() throws XMLStreamException { delegate.flush(); }
    @Override
    public void writeAttribute(String localName, String value) throws XMLStreamException { delegate.writeAttribute(localName, value); }
    @Override
    public void writeAttribute(String namespaceURI, String localName, String value) throws XMLStreamException { delegate.writeAttribute(namespaceURI, localName, value); }
    @Override
    public void writeAttribute(String prefix, String namespaceURI, String localName, String value) throws XMLStreamException { delegate.writeAttribute(prefix, namespaceURI, localName, value); }
    @Override
    public void writeNamespace(String prefix, String namespaceURI) throws XMLStreamException { delegate.writeNamespace(prefix, namespaceURI); }
    @Override
    public void writeDefaultNamespace(String namespaceURI) throws XMLStreamException { delegate.writeDefaultNamespace(namespaceURI); }
    @Override
    public void writeComment(String data) throws XMLStreamException { delegate.writeComment(data); }
    @Override
    public void writeProcessingInstruction(String target) throws XMLStreamException { delegate.writeProcessingInstruction(target); }
    @Override
    public void writeProcessingInstruction(String target, String data) throws XMLStreamException { delegate.writeProcessingInstruction(target, data); }
    @Override
    public void writeCData(String data) throws XMLStreamException { delegate.writeCData(data); }
    @Override
    public void writeDTD(String dtd) throws XMLStreamException { delegate.writeDTD(dtd); }
    @Override
    public void writeEntityRef(String name) throws XMLStreamException { delegate.writeEntityRef(name); }
    @Override
    public void writeStartDocument() throws XMLStreamException { delegate.writeStartDocument(); }
    @Override
    public void writeStartDocument(String version) throws XMLStreamException { delegate.writeStartDocument(version); }
    @Override
    public void writeStartDocument(String encoding, String version) throws XMLStreamException { delegate.writeStartDocument(encoding, version); }
    @Override
    public void writeCharacters(String text) throws XMLStreamException { delegate.writeCharacters(text); }
    @Override
    public void writeCharacters(char[] text, int start, int len) throws XMLStreamException { delegate.writeCharacters(text, start, len); }
    @Override
    public String getPrefix(String uri) throws XMLStreamException { return delegate.getPrefix(uri); }
    @Override
    public void setPrefix(String prefix, String uri) throws XMLStreamException { delegate.setPrefix(prefix, uri); }
    @Override
    public void setDefaultNamespace(String uri) throws XMLStreamException { delegate.setDefaultNamespace(uri); }
    @Override
    public void setNamespaceContext(NamespaceContext context) throws XMLStreamException { delegate.setNamespaceContext(context); }
    @Override
    public NamespaceContext getNamespaceContext() { return delegate.getNamespaceContext(); }
    @Override
    public Object getProperty(String name) { return delegate.getProperty(name); }
}
