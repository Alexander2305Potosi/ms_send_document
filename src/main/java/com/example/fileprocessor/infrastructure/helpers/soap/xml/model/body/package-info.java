/**
 * JAXB model classes for the SOAP V2 request/response body.
 *
 * <p>Elements in this package are serialized <em>unqualified</em> (no namespace prefix
 * on child elements) because the namespace is injected at the parent element level
 * via {@link jakarta.xml.bind.JAXBElement} + {@link javax.xml.namespace.QName}
 * when marshalling in {@code SoapMapper}.
 *
 * <p>The outer {@code <v1:transmitirDocumento>} wrapper carries the vendor namespace
 * declared at runtime in {@code SoapProperties#bodyNamespace()}.
 */
@XmlSchema(elementFormDefault = XmlNsForm.UNQUALIFIED)
package com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body;

import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;
