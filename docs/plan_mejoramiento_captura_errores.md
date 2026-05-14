# Plan de Mejoramiento: Captura y Mapeo de Errores SOAP (Versión Estricta)

## Introducción
Este documento detalla la estrategia para capturar errores SOAP basándose exclusivamente en el modelo de datos `SoapFaultDetail` cuando se detecta una excepción en el servicio.

## 1. Disparador de Mapeo (Trigger)
El mapeo a `SoapFaultDetail` **solo** se ejecutará cuando ocurra alguno de los siguientes eventos:
- El `WebClient` capture una excepción HTTP (ej: 500 Internal Server Error).
- El cuerpo del XML contenga un elemento raíz o hijo llamado `<Fault>`.

## 2. Estrategia de Extracción de Errores

### 2.1 Mapeo Mandatorio a SoapFaultDetail
Una vez detectada la excepción, el sistema procederá de la siguiente manera:
1. **Localización**: Se buscará el nodo `<detail>` dentro del Fault.
2. **Transformación**: Se realizará un unmarshalling directo hacia la clase Java `SoapFaultDetail`.
3. **Extracción**: Se navegará la jerarquía del objeto para obtener:
    - **Código**: `systemException -> genericException -> code`
    - **Descripción**: `systemException -> genericException -> description`

## 3. Código de Referencia

```java
// Este método solo se invoca en el flujo de excepción
private ExternalServiceResponse handleSoapFault(Element faultElement, Unmarshaller unmarshaller) {
    // 1. Intentar mapeo a clase de error mandatoria
    SoapFaultDetail detail = unmarshaller.unmarshal(detailNode, SoapFaultDetail.class).getValue();
    
    // 2. Extraer campos de la clase
    String errorCode = detail.getSystemException().getGenericException().getCode();
    String message = detail.getSystemException().getGenericException().getDescription();
    
    return ExternalServiceResponse.builder()
        .status(FAILURE)
        .message(message)
        .correlationId(errorCode)
        .build();
}
```

---
**Estado**: Definición de Flujo de Excepción Completada
**Fecha**: 2026-05-12
