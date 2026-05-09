package com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@XmlRootElement(name = "transmitirDocumento")
@XmlAccessorType(XmlAccessType.FIELD)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TransmitirDocumentoRequest {

    @XmlElement(name = "subTipoDocumental")
    private String subTipoDocumental;

    @XmlElement(name = "nombreArchivo")
    private String nombreArchivo;

    @XmlElement(name = "archivo")
    private String archivo;

    @XmlElement(name = "metaData")
    private MetaDataWrapper metaData;
}
