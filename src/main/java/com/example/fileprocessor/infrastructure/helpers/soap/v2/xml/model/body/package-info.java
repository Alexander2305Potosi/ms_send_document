/**
 * JAXB model classes for the SOAP V2 request/response body.
 *
 * <p>Elements in this package are serialized <em>unqualified</em> (no namespace prefix
 * on child elements) because the namespace is injected at the parent element level
 * via {@link jakarta.xml.bind.JAXBElement} + {@link javax.xml.namespace.QName}
 * when marshalling in {@code SoapV2Mapper}.
 *
 * <p>The outer {@code <v1:transmitirDocumento>} wrapper carries the vendor namespace
 * declared at runtime in {@code SoapV2Properties#bodyNamespace()}.
 */
@XmlSchema(elementFormDefault = XmlNsForm.UNQUALIFIED)
package com.example.fileprocessor.infrastructure.helpers.soap.v2.xml.model.body;

import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;
