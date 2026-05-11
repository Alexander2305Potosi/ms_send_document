package com.example.fileprocessor.infrastructure.helpers.soap.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SoapConstants {
    // Tokens para reemplazo en la plantilla XML
    public static final String T_NS_ENV      = "{{ns_envelope}}";
    public static final String T_NS_BODY     = "{{ns_body}}";
    public static final String T_NS_STD      = "{{ns_standard}}";
    
    public static final String T_TRACE_ID    = "{{traceId}}";
    public static final String T_TIMESTAMP   = "{{timestamp}}";
    
    public static final String T_SYSTEM_ID   = "{{systemId}}";
    public static final String T_USER_NAME   = "{{userName}}";
    public static final String T_USER_TOKEN  = "{{userToken}}";
    
    public static final String T_DEST_NAME   = "{{destName}}";
    public static final String T_DEST_NS     = "{{destNs}}";
    public static final String T_DEST_OP     = "{{destOp}}";
    
    public static final String T_CLASS       = "{{classification}}";
    
    public static final String T_SUBTYPE     = "{{subTipo}}";
    public static final String T_FILENAME    = "{{filename}}";
    public static final String T_CONTENT     = "{{base64Content}}";
    
    // Marcadores especiales
    public static final String T_METADATA    = "{{METADATA_BLOCK}}";
    public static final String T_CONTEXT     = "{{CONTEXT_BLOCK}}";

    // Elementos XML (Solo los necesarios para el Envelope y Response)
    public static final String EL_ENVELOPE = "Envelope";
    public static final String EL_HEADER = "Header";
    public static final String EL_BODY = "Body";
    public static final String EL_TRANSMITIR_DOCUMENTO_RESPONSE = "transmitirDocumentoResponse";

    // Namespaces para JAXB (Utilizados en parseo de respuesta)
    public static final String PREFIX_SOAPENV = "soapenv";
    public static final String NS_SOAPENV = "http://schemas.xmlsoap.org/soap/envelope/";
}
