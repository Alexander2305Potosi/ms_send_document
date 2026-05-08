package com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class MetaDataEntry {

    @XmlElement(name = "nombre")
    private String nombre;

    @XmlElement(name = "valor")
    private String valor;

    public MetaDataEntry() {}

    public MetaDataEntry(String nombre, String valor) {
        this.nombre = nombre;
        this.valor = valor;
    }

    public String getNombre() { return nombre; }
    public String getValor() { return valor; }
}
