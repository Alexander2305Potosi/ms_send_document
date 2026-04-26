package com.example.fileprocessor.infrastructure.helpers.soap.xml;

import com.example.fileprocessor.domain.usecase.DocumentErrorCodes;
import com.example.fileprocessor.infrastructure.helpers.soap.exception.SoapCommunicationException;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.UploadFileRequest;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.model.UploadFileResponse;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;

@Component
public class SoapEnvelopeWrapper {

    private static final Logger log = LoggerFactory.getLogger(SoapEnvelopeWrapper.class);

    private static final String MSG_SOAP_BODY_NOT_FOUND = "SOAP Body not found";
    private static final String MSG_RESPONSE_ELEMENT_NOT_FOUND = "Response element not found in SOAP Body";
    private static final String MSG_PARSE_ERROR = "Failed to parse SOAP response";

    private final JAXBContext jaxbContext;
    private final DocumentBuilderFactory documentBuilderFactory;

    public SoapEnvelopeWrapper() {
        try {
            this.jaxbContext = JAXBContext.newInstance(UploadFileRequest.class, UploadFileResponse.class);
        } catch (jakarta.xml.bind.JAXBException e) {
            throw new IllegalStateException("Failed to initialize JAXB context", e);
        }
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

    public <T> T unwrapResponse(String soapXml, Class<T> responseClass) {
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document doc = builder.parse(new org.xml.sax.InputSource(new StringReader(soapXml)));

            Node body = doc.getElementsByTagNameNS(SoapNamespaces.SOAP_ENVELOPE, "Body").item(0);
            if (body == null) {
                throw new SoapCommunicationException(MSG_SOAP_BODY_NOT_FOUND, DocumentErrorCodes.INVALID_RESPONSE, null);
            }

            Node responseNode = findFirstElementNode(body);
            if (responseNode == null) {
                throw new SoapCommunicationException(MSG_RESPONSE_ELEMENT_NOT_FOUND, DocumentErrorCodes.INVALID_RESPONSE, null);
            }

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(responseNode), new StreamResult(writer));
            String responseXml = writer.toString();

            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            return responseClass.cast(unmarshaller.unmarshal(new StringReader(responseXml)));

        } catch (SoapCommunicationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error unmarshalling SOAP response: {}", e.getMessage());
            throw new SoapCommunicationException(MSG_PARSE_ERROR, DocumentErrorCodes.INVALID_RESPONSE, null, e);
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