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
    
    // Marcadores para metadatos fijos
    public static final String T_CAT_HOM     = "{{categoriaHomologada}}";
    public static final String T_PAIS_HOM    = "{{paisHomologado}}";
    public static final String T_CARP_HOM    = "{{carpetaHomologada}}";
    public static final String T_FECHA       = "{{fecha}}";
    public static final String T_COMENTARIO  = "{{comentario}}";
    public static final String T_META_NAME_FECHA = "{{metaNameFecha}}";
    public static final String T_META_NAME_COMENTARIO = "{{metaNameComentario}}";

    // Valores fijos
    public static final String VAL_META_NAME_FECHA = "Bfecha";
    public static final String VAL_META_NAME_COMENTARIO = "Bcomentario";
    public static final String VAL_DEFAULT_COMENTARIO = "Procesamiento automatico";
    // Elementos XML (Solo los necesarios para el Envelope y Response)
    public static final String EL_ENVELOPE = "Envelope";
    public static final String EL_HEADER = "Header";
    public static final String EL_BODY = "Body";
    public static final String EL_TRANSMITIR_DOCUMENTO_RESPONSE = "transmitirDocumentoResponse";
    public static final String EL_STATUS = "status";
    public static final String EL_STATUS_CODE = "statusCode";
    public static final String EL_MESSAGE = "message";
    public static final String EL_CORRELATION_ID = "correlationId";
    public static final String EL_MESSAGE_ID = "messageId";
    public static final String EL_PROCESSED_AT = "processedAt";
    public static final String EL_EXTERNAL_REFERENCE = "externalReference";
    public static final String EL_ID_DOCUMENTO = "idDocumento";
    public static final String STATUS_OK = "OK";

    // Namespaces para JAXB (Utilizados en parseo de respuesta)
    public static final String PREFIX_SOAPENV = "soapenv";
    public static final String NS_SOAPENV = "http://schemas.xmlsoap.org/soap/envelope/";
}
