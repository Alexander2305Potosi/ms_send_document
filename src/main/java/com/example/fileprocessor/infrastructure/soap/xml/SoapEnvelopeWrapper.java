package com.example.fileprocessor.infrastructure.soap.xml;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
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

    private static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String FILE_NS = "http://example.com/fileservice";

    private final JAXBContext jaxbContext;
    private final DocumentBuilderFactory documentBuilderFactory;

    public SoapEnvelopeWrapper() throws JAXBException {
        this.jaxbContext = JAXBContext.newInstance(
            com.example.fileprocessor.infrastructure.soap.xml.model.UploadFileRequest.class,
            com.example.fileprocessor.infrastructure.soap.xml.model.UploadFileResponse.class
        );
        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.documentBuilderFactory.setNamespaceAware(true);
    }

    public String wrapRequest(Object request) {
        try {
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);

            StringWriter writer = new StringWriter();
            marshaller.marshal(request, writer);
            String requestXml = writer.toString();

            String envelope = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                               xmlns:file="http://example.com/fileservice">
                  <soap:Header/>
                  <soap:Body>
                %s
                  </soap:Body>
                </soap:Envelope>
                """.formatted(requestXml);

            return envelope;
        } catch (JAXBException e) {
            log.error("Error marshalling SOAP request: {}", e.getMessage());
            throw new RuntimeException("Failed to create SOAP envelope", e);
        }
    }

    public <T> T unwrapResponse(String soapXml, Class<T> responseClass) {
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document doc = builder.parse(new org.xml.sax.InputSource(new StringReader(soapXml)));

            // Buscar el elemento Body
            Node body = doc.getElementsByTagNameNS(SOAP_NS, "Body").item(0);
            if (body == null) {
                throw new RuntimeException("SOAP Body not found");
            }

            // Extraer el primer elemento hijo del Body (la respuesta)
            Node responseNode = findFirstElementNode(body);
            if (responseNode == null) {
                throw new RuntimeException("Response element not found in SOAP Body");
            }

            // Convertir el nodo a string
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(responseNode), new StreamResult(writer));
            String responseXml = writer.toString();

            // Unmarshal
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            return responseClass.cast(unmarshaller.unmarshal(new StringReader(responseXml)));

        } catch (Exception e) {
            log.error("Error unmarshalling SOAP response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse SOAP response", e);
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
