package com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "transmitirDocumento")
@XmlAccessorType(XmlAccessType.FIELD)
public class TransmitirDocumentoRequest {

    @XmlElement(name = "subTipoDocumental")
    private String subTipoDocumental;

    @XmlElement(name = "nombreArchivo")
    private String nombreArchivo;

    @XmlElement(name = "archivo")
    private String archivo;

    @XmlElement(name = "metaData")
    private MetaDataWrapper metaData;

    public TransmitirDocumentoRequest() {}

    public TransmitirDocumentoRequest(String subTipoDocumental, String nombreArchivo,
                                       String archivo, MetaDataWrapper metaData) {
        this.subTipoDocumental = subTipoDocumental;
        this.nombreArchivo = nombreArchivo;
        this.archivo = archivo;
        this.metaData = metaData;
    }

    public String getSubTipoDocumental() { return subTipoDocumental; }
    public String getNombreArchivo() { return nombreArchivo; }
    public String getArchivo() { return archivo; }
    public MetaDataWrapper getMetaData() { return metaData; }
}
