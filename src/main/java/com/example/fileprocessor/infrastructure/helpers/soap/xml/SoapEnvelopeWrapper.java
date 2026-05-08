package com.example.fileprocessor.infrastructure.helpers.soap.xml;

import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.helpers.soap.constants.SoapConstants;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class SoapEnvelopeWrapper {

    private static final Logger log = Logger.getLogger(SoapEnvelopeWrapper.class.getName());

    private final JAXBContext jaxbContext;
    private final DocumentBuilderFactory documentBuilderFactory;

    /**
     * @param jaxbContext shared JAXB context declared in {@code JaxbConfig}
     */
    public SoapEnvelopeWrapper(JAXBContext jaxbContext) {
        this.jaxbContext = jaxbContext;
        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.documentBuilderFactory.setNamespaceAware(true);
        try {
            this.documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            this.documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            this.documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure secure XML parser", e);
        }
    }

    public JAXBContext getJaxbContext() {
        return jaxbContext;
    }

    public <T> T unwrapResponse(String soapXml, Class<T> responseClass) {
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document doc = builder.parse(new org.xml.sax.InputSource(new StringReader(soapXml)));

            Node fault = doc.getElementsByTagNameNS(SoapConstants.SOAP_ENVELOPE, "Fault").item(0);
            if (fault != null) {
                Node currentNode = fault.getFirstChild();
                while (currentNode != null) {
                    if ("faultstring".equals(currentNode.getLocalName())) {
                        throw ProcessingException.withTraceId(
                            "SOAP Fault received: " + currentNode.getTextContent(),
                            ProcessingResultCodes.INVALID_RESPONSE, null);
                    }
                    currentNode = currentNode.getNextSibling();
                }
                throw ProcessingException.withTraceId(
                    "SOAP Fault received", ProcessingResultCodes.INVALID_RESPONSE, null);
            }

            Node body = doc.getElementsByTagNameNS(SoapConstants.SOAP_ENVELOPE, "Body").item(0);
            if (body == null) {
                throw ProcessingException.withTraceId(SoapConstants.MSG_SOAP_BODY_NOT_FOUND,
                    ProcessingResultCodes.INVALID_RESPONSE, null);
            }

            Node responseNode = findFirstElementNode(body);
            if (responseNode == null) {
                throw ProcessingException.withTraceId(SoapConstants.MSG_RESPONSE_ELEMENT_NOT_FOUND,
                    ProcessingResultCodes.INVALID_RESPONSE, null);
            }

            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            return responseClass.cast(unmarshaller.unmarshal(responseNode));

        } catch (ProcessingException e) {
            throw e;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error unmarshalling SOAP response", e);
            throw ProcessingException.withTraceId(SoapConstants.MSG_PARSE_ERROR,
                ProcessingResultCodes.INVALID_RESPONSE, null, e);
        }
    }

    private Node findFirstElementNode(Node parent) {
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return child;
            }
        }
        return null;
    }
}
