# Plan de Modificacion: SoapGatewayAdapter - Nuevo Envelope SOAP V2

> **Estado:** PENDIENTE DE REVISION MANUAL
> **Fecha:** 2026-05-06
> **Branch:** feature/v3.0
> **Principio:** Modificar SOLO la capa del Gateway (`SoapGatewayAdapter` y sus dependencias directas de infraestructura). El dominio y los casos de uso no se tocan.

---

## 1. Objetivo

Agregar un nuevo metodo en `SoapGatewayAdapter` que envie documentos usando un envelope SOAP completamente diferente al actual, con header complejo (`v2:requestHeader`) y body con operacion `v1:transmitirDocumento`, manteniendo intacto el metodo `send()` existente.

---

## 2. Alcance Exacto

### 2.1 Archivos a CREAR

| # | Archivo | Proposito |
|---|---------|-----------|
| 1 | `domain/port/out/SoapGatewayV2.java` | Nuevo puerto de salida (interface) |
| 2 | `infrastructure/helpers/soap/xml/model/TransmitirDocumentoRequest.java` | JAXB model para el nuevo body de request |
| 3 | `infrastructure/helpers/soap/xml/model/TransmitirDocumentoResponse.java` | JAXB model para el nuevo body de response |
| 4 | `infrastructure/helpers/soap/xml/model/SoapV2Header.java` | POJO para construir el header SOAP V2 programaticamente |
| 5 | `infrastructure/helpers/soap/xml/model/MessageProperty.java` | POJO para key/value del messageContext |
| 6 | `infrastructure/helpers/soap/xml/model/MetaDataEntry.java` | POJO para key/value del metaData |
| 7 | `infrastructure/helpers/soap/SoapV2Constants.java` | Constantes XML para el nuevo envelope |
| 8 | `infrastructure/helpers/soap/mapper/SoapV2Mapper.java` | Nuevo mapper (construye envelope V2, parsea response V2) |
| 9 | `infrastructure/drivenadapters/soap/config/SoapV2Properties.java` | `@ConfigurationProperties` para header fields y endpoint V2 |
| 10 | `docs/migrations/005_soap_v2_config.sql` | Script SQL opcional si se requiere insertar config en BD |

### 2.2 Archivos a MODIFICAR

| # | Archivo | Cambio |
|---|---------|--------|
| 1 | `SoapGatewayAdapter.java` | Implementar `SoapGatewayV2`, agregar metodo `sendV2()`, inyectar `SoapV2Mapper` y `SoapV2Properties` |
| 2 | `SoapEnvelopeWrapper.java` | Registrar `TransmitirDocumentoRequest` y `TransmitirDocumentoResponse` en el `JAXBContext` (agregar al `newInstance()`) |
| 3 | `application.yml` | Agregar bloque `app.soap.v2` con las nuevas propiedades |
| 4 | `Application.java` (opcional) | Agregar `@EnableConfigurationProperties(SoapV2Properties.class)` si no se usa auto-deteccion |

### 2.3 Archivos que NO se tocan

- `FileUploadRequest.java` / `FileUploadResult.java` (dominio, se reutilizan tal cual)
- `SoapGateway.java` (puerto original, intacto)
- `SoapMapper.java` (mapper original, intacto)
- `SoapConstants.java` (constantes originales, intacto)
- `SoapDocumentProcessingUseCase.java` (caso de uso, intacto)
- `UploadFileRequest.java` / `UploadFileResponse.java` (modelos JAXB originales, intactos)
- TODOS los tests existentes (intactos)

---

## 3. Analisis Comparativo de Envelopes

### 3.1 Envelope ACTUAL (V1)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
               xmlns:file="http://example.com/fileservice">
  <soap:Header/>
  <soap:Body>
    <file:UploadFileRequest>
      <fileContentBase64>UE9DVElTIFBST0JBQ...</fileContentBase64>
      <filename>test.pdf</filename>
      <contentType>application/pdf</contentType>
      <fileSize>12345</fileSize>
      <timestamp>2026-05-06T12:00:00.000Z</timestamp>
      <parentFolder>.</parentFolder>
      <childFolder>.</childFolder>
    </file:UploadFileRequest>
  </soap:Body>
</soap:Envelope>
```

**Caracteristicas:**
- Sin header SOAP (vacio)
- Body usa JAXB con namespace `http://example.com/fileservice`
- SOAPAction header HTTP: `http://example.com/fileservice/UploadFile`
- Contenido del archivo en Base64
- Construido por `SoapMapper.toFullSoapMessage()` via concatenacion de strings

### 3.2 Envelope NUEVO (V2)

```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:v2="http://prueba.com/ents/SOI/MessageFormat/V2.1"
                  xmlns:v1="http://prueba.com/intf/factory/adminDocs/V1.0">
  <soapenv:Header>
    <v2:requestHeader>
      <systemId>123</systemId>
      <messageId>123</messageId>
      <timestamp>2016-12-014T18:00:00</timestamp>
      <messageContext>
        <property>
          <key>key01</key>
          <value>value01</value>
        </property>
      </messageContext>
      <userId>
        <userName>775757775</userName>
        <userToken>bearer</userToken>
      </userId>
      <destination>
        <name>bussinesdocs</name>
        <namespace>http://prueba.com/intf/factory/adminDocs/Enlace/V1.0</namespace>
        <operation>senddocs</operation>
      </destination>
      <classifications>
        <classification>http://prueba.com/clas/AppsUpdated</classification>
      </classifications>
    </v2:requestHeader>
  </soapenv:Header>
  <soapenv:Body>
    <v1:transmitirDocumento>
      <subTipoDocumental>Facturas</subTipoDocumental>
      <nombreArchivo>1234567890ABCDEFGHIJK1234567890ABCDEFG.txt</nombreArchivo>
      <archivo>1234567890ABCDEFGHIJKPDF</archivo>
      <metaData>
        <tiposMetaData>
          <nombre>xIdentificadorFisico</nombre>
          <valor>65464904</valor>
        </tiposMetaData>
        <tiposMetaData>
          <nombre>xFilial</nombre>
          <valor>prueba</valor>
        </tiposMetaData>
      </metaData>
    </v1:transmitirDocumento>
  </soapenv:Body>
</soapenv:Envelope>
```

**Diferencias clave vs V1:**
| Aspecto | V1 | V2 |
|---------|-----|-----|
| Header SOAP | Vacio | Complejo (`v2:requestHeader` con 7 secciones) |
| Namespaces body | `xmlns:file="http://example.com/fileservice"` | `xmlns:v1="http://prueba.com/intf/factory/adminDocs/V1.0"` |
| Namespaces header | No aplica | `xmlns:v2="http://prueba.com/ents/SOI/MessageFormat/V2.1"` |
| Elemento Body | `UploadFileRequest` | `transmitirDocumento` |
| Campos body | fileContentBase64, filename, contentType, fileSize, timestamp, parentFolder, childFolder | subTipoDocumental, nombreArchivo, archivo, metaData |
| SOAPAction HTTP | `http://example.com/fileservice/UploadFile` | Probablemente no requerido o diferente |
| Contenido archivo | Base64 en `fileContentBase64` | En `archivo` (formato por confirmar: Base64 o string directo) |

---

## 4. Decisiones de Diseno

### 4.1 Origen de los datos del Header V2

El header V2 contiene campos que NO estan en `FileUploadRequest`. Estos datos deben originarse de:

| Campo del Header | Origen propuesto |
|------------------|-------------------|
| `systemId` | `SoapV2Properties.systemId()` |
| `messageId` | Reactor Context `ApiConstants.HEADER_TRACE_ID` (mismo traceId actual) |
| `timestamp` | Generado al momento (`Instant.now().toString()`) |
| `messageContext` | `SoapV2Properties.messageContext()` (Map<String, String>) |
| `userId.userName` | `SoapV2Properties.userName()` |
| `userId.userToken` | `SoapV2Properties.userToken()` (opcional) |
| `destination` | `SoapV2Properties.destinationName()`, `.destinationNamespace()`, `.destinationOperation()` |
| `classifications` | `SoapV2Properties.classifications()` (List<String>) |

### 4.2 Mapeo de FileUploadRequest al Body V2

| Campo del Body V2 | Origen desde FileUploadRequest |
|--------------------|-------------------------------|
| `subTipoDocumental` | `SoapV2Properties.subTipoDocumental()` (configuracion, ej: "Facturas") |
| `nombreArchivo` | `request.getFilename()` |
| `archivo` | `Base64.getEncoder().encodeToString(request.getContent())` |
| `metaData` | `SoapV2Properties.metaData()` (Map<String, String>) |

### 4.3 Estrategia de construccion del envelope V2

Se usara **StAX (XMLStreamWriter)** en lugar de concatenacion de strings, debido a la complejidad del header con elementos repetitivos (`property`, `tiposMetaData`, `classification`). Esto produce XML bien formado, con escaping correcto y sin riesgo de malformacion por caracteres especiales.

Alternativa considerada: FreeMarker/Velocity templates. Descartada para no introducir nueva dependencia.

Alternativa considerada: JAXB para todo el envelope. Descartada porque requeriria anotar clases para el header `v2:requestHeader` con namespaces mixtos, lo cual es mas complejo y fragil que StAX para este caso.

**StAX** es parte del JDK (`java.xml`), no requiere dependencias nuevas.

### 4.4 Estrategia para el response V2

Se asume que el response V2 tiene una estructura similar al request (Body con elemento `transmitirDocumentoResponse`). Se crea una clase JAXB `TransmitirDocumentoResponse` y se reutiliza `SoapEnvelopeWrapper.unwrapResponse()` para extraerlo del Body SOAP.

Si el response tiene estructura diferente, se ajustara la clase JAXB durante la implementacion.

### 4.5 Nuevo puerto de salida

Se crea una NUEVA interface `SoapGatewayV2` en `domain/port/out/` con un metodo con nombre descriptivo:

```java
public interface SoapGatewayV2 {
    Mono<FileUploadResult> transmitirDocumento(FileUploadRequest request);
}
```

`SoapGatewayAdapter` implementara AMBAS interfaces (`SoapGateway` + `SoapGatewayV2`), teniendo asi dos metodos independientes:
- `send(FileUploadRequest)` → envelope V1 (existente, sin cambios)
- `transmitirDocumento(FileUploadRequest)` → envelope V2 (nuevo)

---

## 5. Plan de Implementacion Paso a Paso

### Paso 1: Crear `SoapV2Properties.java`

**Ruta:** `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/soap/config/SoapV2Properties.java`

```java
@Validated
@ConfigurationProperties(prefix = "app.soap.v2")
public record SoapV2Properties(
    @NotBlank String endpoint,
    @NotBlank String systemId,
    String userName,
    String userToken,
    String destinationName,
    String destinationNamespace,
    String destinationOperation,
    String subTipoDocumental,
    List<String> classifications,
    Map<String, String> messageContext,
    Map<String, String> metaData,
    @Min(1) int timeoutSeconds,
    @Min(1) int retryAttempts
) {}
```

Campos opcionales (sin `@NotBlank`): `userToken`, `destinationName`, `destinationNamespace`, `destinationOperation`, `classifications`, `messageContext`, `metaData`.

### Paso 2: Crear modelos JAXB para el Body V2

**`TransmitirDocumentoRequest.java`** en `infrastructure/helpers/soap/xml/model/`:

```java
@XmlRootElement(name = "transmitirDocumento", namespace = "http://prueba.com/intf/factory/adminDocs/V1.0")
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
    // constructores, getters
}
```

Se necesita un wrapper `MetaDataWrapper` para la lista de `tiposMetaData`:

```java
@XmlAccessorType(XmlAccessType.FIELD)
public class MetaDataWrapper {
    @XmlElement(name = "tiposMetaData")
    private List<MetaDataEntry> tiposMetaData;
}
```

Y `MetaDataEntry`:

```java
@XmlAccessorType(XmlAccessType.FIELD)
public class MetaDataEntry {
    @XmlElement(name = "nombre")
    private String nombre;
    @XmlElement(name = "valor")
    private String valor;
}
```

**`TransmitirDocumentoResponse.java`** en `infrastructure/helpers/soap/xml/model/`:

Estructura analoga al response V1, reutilizando campos: status, message, correlationId, processedAt, externalReference.

### Paso 3: Crear `SoapV2Constants.java`

**Ruta:** `src/main/java/com/example/fileprocessor/infrastructure/helpers/soap/SoapV2Constants.java`

```java
public final class SoapV2Constants {
    private SoapV2Constants() {}

    public static final String SOAP_ENVELOPE_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    public static final String V2_HEADER_NS = "http://prueba.com/ents/SOI/MessageFormat/V2.1";
    public static final String V1_BODY_NS = "http://prueba.com/intf/factory/adminDocs/V1.0";

    // Prefijos
    public static final String PREFIX_SOAPENV = "soapenv";
    public static final String PREFIX_V2 = "v2";
    public static final String PREFIX_V1 = "v1";
}
```

### Paso 4: Crear `SoapV2Mapper.java`

**Ruta:** `src/main/java/com/example/fileprocessor/infrastructure/helpers/soap/mapper/SoapV2Mapper.java`

Responsabilidades:
1. **`buildEnvelope(FileUploadRequest, SoapV2Properties, String traceId)`**: Construye el envelope SOAP completo usando `XMLStreamWriter`.

   Logica:
   - Crear `XMLOutputFactory` → `XMLStreamWriter` sobre un `StringWriter`
   - Escribir `soapenv:Envelope` con declaraciones de namespace
   - Escribir `soapenv:Header` → `v2:requestHeader` → todos los campos
     - `systemId`, `messageId` (= traceId), `timestamp`
     - Si `messageContext` no esta vacio, escribir bloque `<messageContext>` con `<property>` por cada entry
     - Escribir `<userId>` con `userName` y opcionalmente `userToken`
     - Si destination configurado, escribir `<destination>` con name, namespace, operation
     - Si classifications no vacia, escribir `<classifications>` con `<classification>` por cada una
   - Escribir `soapenv:Body`
   - Escribir `v1:transmitirDocumento` con sus campos (usando JAXB marshalling para esta parte, o StAX para mantener consistencia)
   - Cerrar todos los elementos

2. **`parseResponse(String xml)`**: Reutiliza `SoapEnvelopeWrapper.unwrapResponse(xml, TransmitirDocumentoResponse.class)` y mapea a `ExternalServiceResponse`.

### Paso 5: Actualizar `SoapEnvelopeWrapper.java`

Agregar las nuevas clases JAXB al contexto:

```java
// Antes:
this.jaxbContext = JAXBContext.newInstance(UploadFileRequest.class, UploadFileResponse.class);

// Despues:
this.jaxbContext = JAXBContext.newInstance(
    UploadFileRequest.class, UploadFileResponse.class,
    TransmitirDocumentoRequest.class, TransmitirDocumentoResponse.class
);
```

La clase `MetaDataWrapper` y `MetaDataEntry` no necesitan registrarse porque son referenciadas desde `TransmitirDocumentoRequest`.

### Paso 6: Crear `SoapGatewayV2.java` (Puerto de Salida)

**Ruta:** `src/main/java/com/example/fileprocessor/domain/port/out/SoapGatewayV2.java`

```java
public interface SoapGatewayV2 {
    Mono<FileUploadResult> transmitirDocumento(FileUploadRequest request);
}
```

### Paso 7: Modificar `SoapGatewayAdapter.java`

Agregar:
- Nuevo campo: `private final SoapV2Mapper soapV2Mapper;`
- Nuevo campo: `private final SoapV2Properties v2Properties;`
- Actualizar constructor para recibir los nuevos campos
- Implementar `SoapGatewayV2`
- Agregar metodo `transmitirDocumento(FileUploadRequest)`:

```java
@Override
public Mono<FileUploadResult> transmitirDocumento(FileUploadRequest request) {
    return Mono.deferContextual(ctx -> {
        String traceId = ctx.get(ApiConstants.HEADER_TRACE_ID);
        int maxRetries = v2Properties.retryAttempts();
        AtomicInteger attemptCount = new AtomicInteger(1);

        String soapEnvelope = soapV2Mapper.buildEnvelope(request, v2Properties, traceId);

        // Misma logica de envio que send(): WebClient post, retry, error handling
        // PERO usando v2Properties.endpoint() y SIN SOAPAction header (o con valor diferente)
        // ...
    });
}
```

**Detalles del metodo `transmitirDocumento`:**
- Usa `v2Properties.endpoint()` para el baseUrl del WebClient (o un WebClient separado)
- Probablemente SIN header `SOAPAction` (el destino ya va en el header SOAP)
- Misma logica de retry, timeout, y error handling que `send()`
- Mismo mapeo final a `FileUploadResult`

**IMPORTANTE:** El WebClient actual se construye en el constructor con `properties.endpoint()`. Para V2 se necesita un endpoint diferente. Hay dos opciones:

**Opcion A (recomendada):** Crear un segundo `WebClient` en el constructor para el endpoint V2:
```java
this.webClientV2 = webClientBuilder
    .baseUrl(v2Properties.endpoint())
    .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
    .build();
```

**Opcion B:** Construir la URL completa en cada llamada sin usar `baseUrl`.

Se elige **Opcion A** por consistencia con el diseno actual.

### Paso 8: Agregar configuracion en `application.yml`

```yaml
app:
  soap:
    v2:
      endpoint: ${SOAP_V2_ENDPOINT:http://localhost:9000/soap/adminDocs}
      system-id: ${SOAP_V2_SYSTEM_ID:123}
      user-name: ${SOAP_V2_USER_NAME:775757775}
      user-token: ${SOAP_V2_USER_TOKEN:}
      destination-name: ${SOAP_V2_DEST_NAME:bussinesdocs}
      destination-namespace: ${SOAP_V2_DEST_NS:http://prueba.com/intf/factory/adminDocs/Enlace/V1.0}
      destination-operation: ${SOAP_V2_DEST_OP:senddocs}
      sub-tipo-documental: ${SOAP_V2_SUB_TIPO:Facturas}
      classifications:
        - "http://prueba.com/clas/AppsUpdated"
      message-context:
        key01: "value01"
        key02: "value02"
      meta-data:
        xIdentificadorFisico: "65464904"
        xFilial: "prueba"
      timeout-seconds: 30
      retry-attempts: 3
```

Tambien agregar en `application-dev.yml` y `application-prod.yml` con valores apropiados.

### Paso 9: Actualizar `Application.java`

Agregar la nueva clase de propiedades si no se usa auto-deteccion:

```java
@EnableConfigurationProperties({SoapProperties.class, SoapV2Properties.class, ...})
```

---

## 6. Estructura de Archivos Resultante

```
src/main/java/com/example/fileprocessor/
├── domain/
│   └── port/out/
│       ├── SoapGateway.java           # [EXISTENTE, sin cambios]
│       └── SoapGatewayV2.java         # [NUEVO]
├── infrastructure/
│   ├── drivenadapters/soap/
│   │   ├── SoapGatewayAdapter.java    # [MODIFICADO: implementa SoapGatewayV2]
│   │   ├── SoapErrorCodes.java        # [EXISTENTE, sin cambios]
│   │   └── config/
│   │       ├── SoapProperties.java    # [EXISTENTE, sin cambios]
│   │       └── SoapV2Properties.java  # [NUEVO]
│   └── helpers/soap/
│       ├── SoapConstants.java         # [EXISTENTE, sin cambios]
│       ├── SoapV2Constants.java       # [NUEVO]
│       ├── mapper/
│       │   ├── SoapMapper.java        # [EXISTENTE, sin cambios]
│       │   └── SoapV2Mapper.java      # [NUEVO]
│       └── xml/
│           ├── SoapEnvelopeWrapper.java  # [MODIFICADO: agregar clases al JAXBContext]
│           └── model/
│               ├── UploadFileRequest.java   # [EXISTENTE, sin cambios]
│               ├── UploadFileResponse.java  # [EXISTENTE, sin cambios]
│               ├── TransmitirDocumentoRequest.java  # [NUEVO]
│               ├── TransmitirDocumentoResponse.java # [NUEVO]
│               ├── MetaDataWrapper.java     # [NUEVO]
│               └── MetaDataEntry.java       # [NUEVO]
```

---

## 7. Preguntas Pendientes para Revision

1. **Formato de `archivo`:** El ejemplo muestra un string como "1234567890ABCDEFGHIJKPDF". Se asume Base64 del contenido binario, pero se debe confirmar con el equipo dueno del servicio SOAP.

2. **SOAPAction HTTP Header:** El nuevo envelope probablemente no requiera header HTTP `SOAPAction` (el destino esta en el body del header SOAP). Se debe confirmar.

3. **Response V2:** Se asume estructura similar al response V1. Se debe confirmar el XML exacto del response para crear la clase JAXB correcta (`TransmitirDocumentoResponse`).

4. **URL del endpoint V2:** Debe ser configurable por ambiente (dev, prod). Ya se contempla en `SoapV2Properties`.

5. **Delay artificial de 20s:** El metodo `send()` actual tiene un `Mono.delay(Duration.ofSeconds(20))` antes del POST (linea 67 de SoapGatewayAdapter). Se debe confirmar si `transmitirDocumento` tambien necesita este delay o es un workaround temporal del V1.

6. **Pool de conexiones:** Crear un segundo `WebClient` implica un segundo pool de conexiones HTTP. Se debe verificar que no exceda limites de recursos. Alternativa: usar el mismo `WebClient` con URL dinamica (sin `baseUrl`).

7. **Naming final:** Revisar nombres propuestos:
   - `SoapGatewayV2` vs `TransmitirDocumentoGateway`
   - `transmitirDocumento()` vs `sendV2()`
   - `SoapV2Mapper` vs `TransmitirDocumentoMapper`

---

## 8. Riesgos y Mitigaciones

| Riesgo | Probabilidad | Impacto | Mitigacion |
|--------|-------------|---------|------------|
| El servicio SOAP V2 no acepta el formato generado | Media | Alto | Validar con el equipo dueno del servicio antes de implementar; usar el XML de ejemplo como referencia exacta |
| Response V2 tiene estructura diferente a la asumida | Media | Medio | Crear la clase `TransmitirDocumentoResponse` solo cuando se tenga el response real confirmado |
| Doble WebClient aumenta uso de recursos | Baja | Bajo | Medir en ambiente dev; si es problematico, usar un solo WebClient sin baseUrl y pasar URL completa |
| StAX produce XML sin pretty-print | Baja | Bajo | El formato no afecta al servicio SOAP; agregar `javax.xml.transform.Transformer` para pretty-print en logs si se requiere debug |

---

## 9. Criterios de Aceptacion

- [ ] `SoapGatewayAdapter` implementa tanto `SoapGateway` como `SoapGatewayV2`
- [ ] El metodo `send()` existente funciona exactamente igual (sin regresiones)
- [ ] El metodo `transmitirDocumento()` genera EXACTAMENTE el envelope XML definido en el ejemplo
- [ ] Namespaces correctos: `soapenv`, `v2`, `v1` con las URIs especificadas
- [ ] Campos opcionales del header (`userToken`, `destination`, `messageContext`, `classifications`) no aparecen en el XML cuando no estan configurados
- [ ] `metaData` solo aparece en el body si `SoapV2Properties.metaData()` no esta vacio
- [ ] Encoding Base64 del contenido del archivo en `<archivo>`
- [ ] Manejo de errores HTTP, timeout, y conexion igual que V1
- [ ] Retry con backoff igual que V1
- [ ] Tests unitarios para `SoapV2Mapper.buildEnvelope()` con XML de salida validado
- [ ] Tests unitarios para `SoapGatewayAdapter.transmitirDocumento()` (success, errores)
- [ ] Tests existentes de V1 continuan pasando sin modificaciones
- [ ] La configuracion nueva en YAML es auto-documentada con comentarios

---

## 10. Proximos Pasos (post-revision)

1. Revision y feedback de este plan por el equipo
2. Resolver preguntas pendientes (seccion 7) con el equipo dueno del servicio SOAP
3. Obtener XML real de response para disenar `TransmitirDocumentoResponse`
4. Implementar segun el plan aprobado
5. Probar contra mock del servicio SOAP V2 (extender `PortableSoapMock`)
6. PR con los cambios
