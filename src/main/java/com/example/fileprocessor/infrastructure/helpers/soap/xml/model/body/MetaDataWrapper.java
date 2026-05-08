package com.example.fileprocessor.infrastructure.helpers.soap.xml.model.body;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class MetaDataWrapper {

    @XmlElement(name = "tiposMetaData")
    private List<MetaDataEntry> tiposMetaData;

    public MetaDataWrapper() {}

    public MetaDataWrapper(List<MetaDataEntry> tiposMetaData) {
        this.tiposMetaData = tiposMetaData;
    }

    public List<MetaDataEntry> getTiposMetaData() { return tiposMetaData; }
}
