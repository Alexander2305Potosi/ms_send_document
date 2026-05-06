# Plan de Modificacion: SoapGatewayAdapter - Nuevo Envelope SOAP V2

> **Estado:** PENDIENTE DE REVISION MANUAL (v2 - post-auditoria)
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
| 4 | `infrastructure/helpers/soap/xml/model/MetaDataEntry.java` | POJO compartido para key/value (reutilizado en `metaData.body` y `messageContext.header`) |
| 5 | `infrastructure/helpers/soap/xml/model/MetaDataWrapper.java` | Wrapper JAXB para la lista de `tiposMetaData` dentro del body |
| 6 | `infrastructure/helpers/soap/SoapV2Constants.java` | Unicas constantes aceptables: `SOAP_ENVELOPE_NS` (estandar W3C) y prefixes XML (`soapenv`, `v2`, `v1`). Los namespaces de vendor van en properties. |
| 7 | `infrastructure/helpers/soap/mapper/SoapV2Mapper.java` | Nuevo mapper: construye envelope V2 con StAX (header) + JAXB (body), parsea response V2 |
| 8 | `infrastructure/drivenadapters/soap/config/SoapV2Properties.java` | `@ConfigurationProperties(prefix = "app.soap.v2")` con TODOS los campos incluyendo `headerNamespace`, `bodyNamespace`, `soapAction`, `delayMillis` |

### 2.2 Archivos a MODIFICAR

| # | Archivo | Cambio |
|---|---------|--------|
| 1 | `SoapGatewayAdapter.java` | Implementar `SoapGatewayV2`, agregar metodo `transmitirDocumento()`, inyectar `SoapV2Mapper` y `SoapV2Properties` y `WebClient` para V2. Extraer metodo privado `executeSoapCall()` compartido entre `send()` y `transmitirDocumento()`. Usar `ctx.getOrDefault()` para traceId. |
| 2 | `SoapEnvelopeWrapper.java` | Registrar `TransmitirDocumentoRequest` y `TransmitirDocumentoResponse` en el `JAXBContext` existente |
| 3 | `application.yml` | Agregar bloque `app.soap.v2` con TODAS las nuevas propiedades (incluyendo `header-namespace`, `body-namespace`, `soap-action`, `delay-millis`) |
| 4 | `application-dev.yml` | Agregar bloque `app.soap.v2` con valores de desarrollo |
| 5 | `application-prod.yml` | Agregar bloque `app.soap.v2` con valores de produccion |
| 6 | `Application.java` | Agregar `SoapV2Properties.class` en `@EnableConfigurationProperties` |

### 2.3 Archivos que NO se tocan

- `FileUploadRequest.java` / `FileUploadResult.java` (dominio, se reutilizan tal cual)
- `ExternalServiceResponse.java` (dominio, se reutiliza tal cual)
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
| Namespaces body | `xmlns:file="http://example.com/fileservice"` | `xmlns:v1="..."` (configurable via `SoapV2Properties.bodyNamespace()`) |
| Namespaces header | No aplica | `xmlns:v2="..."` (configurable via `SoapV2Properties.headerNamespace()`) |
| Elemento Body | `UploadFileRequest` | `transmitirDocumento` |
| Campos body | fileContentBase64, filename, contentType, fileSize, timestamp, parentFolder, childFolder | subTipoDocumental, nombreArchivo, archivo, metaData |
| SOAPAction HTTP | `http://example.com/fileservice/UploadFile` | Configurable via `SoapV2Properties.soapAction()` (vacio por defecto) |
| Contenido archivo | Base64 en `fileContentBase64` | Base64 en `archivo` |

---

## 4. Decisiones de Diseno

### 4.1 Origen de los datos del Header V2

El header V2 contiene campos que NO estan en `FileUploadRequest`. Estos datos se originan de:

| Campo del Header | Origen |
|------------------|--------|
| `systemId` | `SoapV2Properties.systemId()` |
| `messageId` | Reactor Context `ctx.getOrDefault(ApiConstants.HEADER_TRACE_ID, "unknown")` |
| `timestamp` | Generado al momento (`Instant.now().toString()`) |
| `messageContext` | `SoapV2Properties.messageContext()` (Map<String, String>, normalizado a `Map.of()` si no configurado) |
| `userId.userName` | `SoapV2Properties.userName()` |
| `userId.userToken` | `SoapV2Properties.userToken()` (solo se escribe si no es blank) |
| `destination.name` | `SoapV2Properties.destinationName()` |
| `destination.namespace` | `SoapV2Properties.destinationNamespace()` |
| `destination.operation` | `SoapV2Properties.destinationOperation()` |
| `classifications` | `SoapV2Properties.classifications()` (List<String>, normalizado a `List.of()` si no configurado) |

**Regla de omision de bloques opcionales:** Cada bloque opcional del header (`messageContext`, `userId.userToken`, `destination`, `classifications`) SOLO se escribe en el XML si su valor NO es null/blank/vacio. Esto evita enviar elementos vacios que podrian ser rechazados por el servicio SOAP.

- `<messageContext>`: se omite si `messageContext` esta vacio
- `<userToken>`: se omite si `userToken` es null o blank
- `<destination>`: se omite si `destinationName` es null o blank
- `<classifications>`: se omite si `classifications` esta vacia

### 4.2 Mapeo de FileUploadRequest al Body V2

| Campo del Body V2 | Origen | Manejo de null |
|--------------------|--------|----------------|
| `subTipoDocumental` | `SoapV2Properties.subTipoDocumental()` | Si es null → se omite el elemento |
| `nombreArchivo` | `request.getFilename()` | Si es null → `"unknown"` |
| `archivo` | `Base64.getEncoder().encodeToString(request.getContent())` | Si `content` es null → `""` |
| `metaData` | `SoapV2Properties.metaData()` (Map<String, String>) | Se omite el bloque `<metaData>` si el map esta vacio |

### 4.3 Estrategia de construccion del envelope V2: StAX (header + esqueleto) + JAXB (body)

**Decision final (hibrida):** StAX para el esqueleto del envelope y el header, JAXB para el body.

| Capa | Tecnologia | Justificacion |
|------|-----------|---------------|
| Envelope esqueleto + Header `v2:requestHeader` | **StAX** (`XMLStreamWriter`) | Header complejo con elementos repetitivos (`property`, `classification`). StAX da control total sin requerir 7+ clases JAXB adicionales. Los valores vienen de `SoapV2Properties` (nada hardcodeado). |
| Body `v1:transmitirDocumento` | **JAXB** (marshalling de `TransmitirDocumentoRequest`) | Type-safe, validado por JAXB, reutiliza el `JAXBContext` existente en `SoapEnvelopeWrapper`. |

**Alternativas descartadas:**

| Alternativa | Razon de descarte |
|-------------|-------------------|
| Full JAXB | Requiere 7+ clases JAXB para el header con namespaces mixtos. Sobrediseno para un header que es esencialmente un dump de configuracion. |
| Full StAX | Verboso para el body, pierde validacion type-safe de JAXB, propenso a errores de tipeo en nombres de elemento. |
| FreeMarker / Velocity | Introduce nueva dependencia. Innecesario para la complejidad real del envelope. |
| String concatenation (como V1) | Inseguro para estructuras complejas con elementos repetitivos, no maneja escaping de caracteres especiales. |

**¿Por que no JAXB para el header?** El header `v2:requestHeader` usa el namespace `http://prueba.com/ents/SOI/MessageFormat/V2.1` pero sus hijos (`systemId`, `messageId`, etc.) NO deben llevar prefijo de namespace (estan en el default namespace del ancestro). Esto es complejo de modelar con anotaciones JAXB y fragile ante cambios de version. Con StAX se escribe exactamente el XML requerido sin ambiguedades.

### 4.4 Estrategia para el response V2

Se asume que el response V2 tiene un Body con un elemento `transmitirDocumentoResponse` que contiene campos analogos a V1: `status`, `message`, `correlationId`, `processedAt`, `externalReference`.

**Clase JAXB `TransmitirDocumentoResponse`** con `@XmlRootElement(name = "transmitirDocumentoResponse", namespace = "...")`.

Se reutiliza el metodo `SoapEnvelopeWrapper.unwrapResponse(xml, TransmitirDocumentoResponse.class)` que:
1. Busca `<soap:Body>` por namespace estandar SOAP (agnostico al namespace del body)
2. Extrae el primer hijo del Body como string XML
3. Unmarshallea con JAXB usando la clase `TransmitirDocumentoResponse`

**ADVERTENCIA:** Esta clase es especulativa hasta obtener el XML real del response (ver seccion 7, pregunta 3).

### 4.5 Nuevo puerto de salida

Se crea una NUEVA interface `SoapGatewayV2` en `domain/port/out/` con:

```java
public interface SoapGatewayV2 {
    Mono<FileUploadResult> transmitirDocumento(FileUploadRequest request);
}
```

`SoapGatewayAdapter` implementara AMBAS interfaces (`SoapGateway` + `SoapGatewayV2`), teniendo asi dos metodos independientes:
- `send(FileUploadRequest)` → envelope V1 (existente, sin cambios)
- `transmitirDocumento(FileUploadRequest)` → envelope V2 (nuevo)

### 4.6 Manejo de WebClient (dos endpoints diferentes)

Se usaran **dos instancias de `WebClient`** (una para V1, otra para V2), cada una con su propio `baseUrl`. Esto es necesario porque V1 y V2 apuntan a hosts/endpoints diferentes.

Reactores Netty maneja connection pools **por host remoto**, no por instancia de `WebClient`. Por tanto, dos WebClients apuntando a dos hosts diferentes naturalmente tendran dos pools separados, lo cual es comportamiento correcto.

Si en el futuro V1 y V2 comparten el mismo host (solo difiere el path), se refactorizara a un unico WebClient con URL dinamica.

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
    @NotBlank String userName,
    @NotBlank String headerNamespace,
    @NotBlank String bodyNamespace,
    @NotBlank String subTipoDocumental,

    String userToken,
    String destinationName,
    String destinationNamespace,
    String destinationOperation,
    String soapAction,

    List<String> classifications,
    Map<String, String> messageContext,
    Map<String, String> metaData,

    @Min(0) int delayMillis,
    @Min(1) int timeoutSeconds,
    @Min(1) int retryAttempts
) {
    public SoapV2Properties {
        // Normalizar colecciones: null → vacio. Elimina toda una clase de bugs NPE.
        if (classifications == null) classifications = List.of();
        if (messageContext == null) messageContext = Map.of();
        if (metaData == null) metaData = Map.of();
        if (delayMillis < 0) delayMillis = 0;
        if (timeoutSeconds <= 0) timeoutSeconds = 30;
        if (retryAttempts <= 0) retryAttempts = 1;
    }
}
```

**Notas sobre el diseno del record:**

1. **Campos `@NotBlank`**: `endpoint`, `systemId`, `userName`, `headerNamespace`, `bodyNamespace`, `subTipoDocumental` son obligatorios. Spring Boot fallara al arrancar si faltan.

2. **Campos opcionales (sin `@NotBlank`)**: `userToken`, `destinationName`, `destinationNamespace`, `destinationOperation`, `soapAction`. Si no se configuran, se omite su bloque correspondiente en el XML.

3. **Compact constructor**: Normaliza `List` y `Map` de null a colecciones vacias. Esto garantiza que el codigo del mapper nunca recibe null, simplificando todas las comprobaciones (solo verifica `isEmpty()`).

4. **`headerNamespace` y `bodyNamespace`**: Son los URIs de namespace que antes se proponian como constantes en `SoapV2Constants`. Al estar en properties, un cambio de version del servicio SOAP (ej: V2.1 → V2.2) solo requiere cambiar el YAML, sin recompilar.

5. **`soapAction`**: Campo opcional para el header HTTP `SOAPAction`. Si es null/blank, no se envia el header. Cubre el caso de que el servicio V2 requiera SOAPAction diferente al V1.

6. **`delayMillis`**: Reemplaza el `Mono.delay(Duration.ofSeconds(20))` hardcodeado. Por defecto 0 (sin delay para V2). Si se necesita, se configura en YAML.

### Paso 2: Crear modelos JAXB para el Body V2

#### 2a. `TransmitirDocumentoRequest.java`

**Ruta:** `src/main/java/com/example/fileprocessor/infrastructure/helpers/soap/xml/model/TransmitirDocumentoRequest.java`

```java
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

    // Getters (requeridos por JAXB)
    public String getSubTipoDocumental() { return subTipoDocumental; }
    public String getNombreArchivo() { return nombreArchivo; }
    public String getArchivo() { return archivo; }
    public MetaDataWrapper getMetaData() { return metaData; }
}
```

**IMPORTANTE:** El `@XmlRootElement` NO especifica `namespace` en la anotacion. El namespace se aplica en tiempo de marshalling via el `XMLStreamWriter` de StAX que ya declaro el binding `xmlns:v1`. Esto evita que el namespace quede compilado en el annotation. Ver Paso 4 para el detalle de como se emite el namespace correcto.

#### 2b. `MetaDataWrapper.java`

**Ruta:** `src/main/java/com/example/fileprocessor/infrastructure/helpers/soap/xml/model/MetaDataWrapper.java`

```java
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
```

#### 2c. `MetaDataEntry.java` (POJO compartido header+body)

**Ruta:** `src/main/java/com/example/fileprocessor/infrastructure/helpers/soap/xml/model/MetaDataEntry.java`

```java
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
```

Esta misma clase se reutiliza para:
- **Body:** `<tiposMetaData>` con `nombre`/`valor`
- **Header:** `<property>` con `key`/`value` (aunque los nombres XML difieren, la estructura Java de transporte es identica: dos strings). En el StAX del header, se itera sobre `List<MetaDataEntry>` y se escriben los elementos `<key>` y `<value>` manualmente.

#### 2d. `TransmitirDocumentoResponse.java`

**Ruta:** `src/main/java/com/example/fileprocessor/infrastructure/helpers/soap/xml/model/TransmitirDocumentoResponse.java`

Estructura analoga al response V1 (`UploadFileResponse`), con campos: `status`, `message`, `correlationId`, `processedAt`, `externalReference`. El namespace se omite del `@XmlRootElement` por la misma razon que el request (se maneja en el unmarshalling).

```java
@XmlRootElement(name = "transmitirDocumentoResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class TransmitirDocumentoResponse {

    @XmlElement(name = "status")
    private String status;

    @XmlElement(name = "message")
    private String message;

    @XmlElement(name = "correlationId")
    private String correlationId;

    @XmlElement(name = "processedAt")
    private String processedAt;

    @XmlElement(name = "externalReference")
    private String externalReference;

    public TransmitirDocumentoResponse() {}

    // Getters
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public String getCorrelationId() { return correlationId; }
    public String getProcessedAt() { return processedAt; }
    public String getExternalReference() { return externalReference; }
}
```

**ADVERTENCIA:** Esta estructura es ESPECULATIVA. Se debe validar contra el XML real del response antes de implementar.

### Paso 3: Crear `SoapV2Constants.java`

**Ruta:** `src/main/java/com/example/fileprocessor/infrastructure/helpers/soap/SoapV2Constants.java`

```java
public final class SoapV2Constants {

    private SoapV2Constants() {}

    // UNICA constante de namespace aceptable: estandar W3C, no es vendor-specific
    public static final String SOAP_ENVELOPE_NS = "http://schemas.xmlsoap.org/soap/envelope/";

    // Prefijos XML (eleccion local de convencion, no afectan semantica)
    public static final String PREFIX_SOAPENV = "soapenv";
    public static final String PREFIX_V2 = "v2";
    public static final String PREFIX_V1 = "v1";

    // Nombres de elementos del header (parte del contrato WSDL, no son configuracion)
    public static final String HEADER_REQUEST_HEADER = "requestHeader";
    public static final String HEADER_SYSTEM_ID = "systemId";
    public static final String HEADER_MESSAGE_ID = "messageId";
    public static final String HEADER_TIMESTAMP = "timestamp";
    public static final String HEADER_MESSAGE_CONTEXT = "messageContext";
    public static final String HEADER_PROPERTY = "property";
    public static final String HEADER_KEY = "key";
    public static final String HEADER_VALUE = "value";
    public static final String HEADER_USER_ID = "userId";
    public static final String HEADER_USER_NAME = "userName";
    public static final String HEADER_USER_TOKEN = "userToken";
    public static final String HEADER_DESTINATION = "destination";
    public static final String HEADER_DEST_NAME = "name";
    public static final String HEADER_DEST_NAMESPACE = "namespace";
    public static final String HEADER_DEST_OPERATION = "operation";
    public static final String HEADER_CLASSIFICATIONS = "classifications";
    public static final String HEADER_CLASSIFICATION = "classification";
}
```

**¿Por que estos nombres de elemento son CONST_OK y no MUST_CONFIG?** Son parte del contrato WSDL del servicio SOAP V2. Cambian unicamente si el servicio remoto cambia su API (breaking change). En ese escenario, se necesita una nueva version del adapter de todas formas. No son valores que cambien entre ambientes (dev/qa/prod).

**¿Por que `SOAP_ENVELOPE_NS` es la unica constante de namespace?** Es un estandar W3C (SOAP 1.1). No es vendor-specific. Nunca cambiara. Es la misma URI que usa `SoapConstants.SOAP_ENVELOPE`.

### Paso 4: Crear `SoapV2Mapper.java`

**Ruta:** `src/main/java/com/example/fileprocessor/infrastructure/helpers/soap/mapper/SoapV2Mapper.java`

#### 4a. Metodo `buildEnvelope(FileUploadRequest, SoapV2Properties, String traceId)`

Flujo completo de construccion del envelope:

```
1. Crear XMLOutputFactory → XMLStreamWriter sobre StringWriter
2. writeStartDocument("UTF-8", "1.0")
3. writeStartElement(PREFIX_SOAPENV, "Envelope", SOAP_ENVELOPE_NS)
4. writeNamespace(PREFIX_SOAPENV, SOAP_ENVELOPE_NS)
5. writeNamespace(PREFIX_V2, properties.headerNamespace())    // ← de properties, NO hardcodeado
6. writeNamespace(PREFIX_V1, properties.bodyNamespace())      // ← de properties, NO hardcodeado
7. writeStartElement(PREFIX_SOAPENV, "Header", SOAP_ENVELOPE_NS)
8.   writeStartElement(PREFIX_V2, "requestHeader", properties.headerNamespace())
9.     writeTextElement("systemId", properties.systemId())
10.    writeTextElement("messageId", traceId)
11.    writeTextElement("timestamp", Instant.now().toString())
12.
13.    // -- BLOQUE OPCIONAL: messageContext --
14.    if (!properties.messageContext().isEmpty()) {
15.      writeStartElement("messageContext")
16.      for (entry : properties.messageContext()) {
17.        writeStartElement("property")
18.        writeTextElement("key", entry.key)
19.        writeTextElement("value", entry.value)
20.        writeEndElement() // property
21.      }
22.      writeEndElement() // messageContext
23.    }
24.
25.    // -- BLOQUE userId --
26.    writeStartElement("userId")
27.    writeTextElement("userName", properties.userName())
28.    if (properties.userToken() != null && !properties.userToken().isBlank()) {
29.      writeTextElement("userToken", properties.userToken())
30.    }
31.    writeEndElement() // userId
32.
33.    // -- BLOQUE OPCIONAL: destination --
34.    if (properties.destinationName() != null && !properties.destinationName().isBlank()) {
35.      writeStartElement("destination")
36.      writeTextElement("name", properties.destinationName())
37.      writeTextElement("namespace", properties.destinationNamespace())
38.      writeTextElement("operation", properties.destinationOperation())
39.      writeEndElement() // destination
40.    }
41.
42.    // -- BLOQUE OPCIONAL: classifications --
43.    if (!properties.classifications().isEmpty()) {
44.      writeStartElement("classifications")
45.      for (cl : properties.classifications()) {
46.        writeTextElement("classification", cl)
47.      }
48.      writeEndElement() // classifications
49.    }
50.
51.    writeEndElement() // requestHeader
52.  writeEndElement() // Header
53.
54.  // -- BODY --
55.  writeStartElement(PREFIX_SOAPENV, "Body", SOAP_ENVELOPE_NS)
56.
57.  // JAXB marshalling de TransmitirDocumentoRequest
58.  TransmitirDocumentoRequest body = buildBodyRequest(request, properties)
59.  Marshaller marshaller = jaxbContext.createMarshaller()
60.  marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true)
61.  marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, false)
62.  marshaller.marshal(body, xmlStreamWriter)  // escribe directo al StAX writer
63.
64.  writeEndElement() // Body
65.  writeEndElement() // Envelope
66.  writeEndDocument()
```

**Detalle de `buildBodyRequest()`:**

```java
private TransmitirDocumentoRequest buildBodyRequest(FileUploadRequest request, SoapV2Properties props) {
    String base64Content = request.getContent() != null
        ? Base64.getEncoder().encodeToString(request.getContent())
        : "";

    String safeFilename = request.getFilename() != null
        ? request.getFilename()
        : "unknown";

    MetaDataWrapper metaWrapper = null;
    if (!props.metaData().isEmpty()) {
        List<MetaDataEntry> entries = props.metaData().entrySet().stream()
            .map(e -> new MetaDataEntry(e.getKey(), e.getValue()))
            .toList();
        metaWrapper = new MetaDataWrapper(entries);
    }

    return new TransmitirDocumentoRequest(
        props.subTipoDocumental(),
        safeFilename,
        base64Content,
        metaWrapper  // puede ser null → JAXB omite el elemento
    );
}
```

#### 4b. Metodo `parseResponse(String xml)`

```java
public ExternalServiceResponse parseResponse(String xml) {
    TransmitirDocumentoResponse response = envelopeWrapper.unwrapResponse(
        xml, TransmitirDocumentoResponse.class);

    Instant processedAt = response.getProcessedAt() != null
        ? Instant.parse(response.getProcessedAt())
        : Instant.now();

    return ExternalServiceResponse.builder()
        .status(Objects.requireNonNullElse(response.getStatus(), "UNKNOWN"))
        .message(Objects.requireNonNullElse(response.getMessage(), "No message received"))
        .correlationId(Objects.requireNonNullElse(response.getCorrelationId(), "N/A"))
        .processedAt(processedAt)
        .externalReference(response.getExternalReference())
        .build();
}
```

### Paso 5: Actualizar `SoapEnvelopeWrapper.java`

Agregar las nuevas clases JAXB al contexto:

```java
// Constructor, linea 36: ANTES
this.jaxbContext = JAXBContext.newInstance(UploadFileRequest.class, UploadFileResponse.class);

// Constructor, linea 36: DESPUES
this.jaxbContext = JAXBContext.newInstance(
    UploadFileRequest.class, UploadFileResponse.class,
    TransmitirDocumentoRequest.class, TransmitirDocumentoResponse.class
);
```

**Analisis de riesgo:** `JAXBContext.newInstance()` acepta multiples clases y crea un contexto unificado capaz de manejar todas. Como las clases tienen diferentes `@XmlRootElement` names y namespaces, no hay conflicto. Las clases V1 (`UploadFileRequest`, `UploadFileResponse`) y V2 (`TransmitirDocumentoRequest`, `TransmitirDocumentoResponse`) coexisten sin riesgo en el mismo `JAXBContext`. El unmarshalling identifica la clase correcta por el elemento raiz del XML.

**¿Por que no crear un `SoapV2EnvelopeWrapper` independiente?** Arquitectonicamente mas puro (zero-touch en V1), pero pragmaticamente innecesario: el `JAXBContext` compartido es seguro, y crear otra clase casi identica solo para dos lineas diferentes de `JAXBContext.newInstance()` es sobre-ingenieria. Si en el futuro el context V2 requiere configuracion diferente (ej: `ValidationEventHandler`, `Schema`), se extraera.

### Paso 6: Crear `SoapGatewayV2.java` (Puerto de Salida)

**Ruta:** `src/main/java/com/example/fileprocessor/domain/port/out/SoapGatewayV2.java`

```java
package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import reactor.core.publisher.Mono;

public interface SoapGatewayV2 {
    Mono<FileUploadResult> transmitirDocumento(FileUploadRequest request);
}
```

### Paso 7: Modificar `SoapGatewayAdapter.java`

#### 7a. Nuevos campos e inyeccion de dependencias

```java
@Component
public class SoapGatewayAdapter implements SoapGateway, SoapGatewayV2 {

    private static final Logger log = Logger.getLogger(SoapGatewayAdapter.class.getName());

    private final WebClient webClient;        // V1 (existente)
    private final WebClient webClientV2;      // V2 (nuevo)
    private final SoapProperties properties;  // V1 (existente)
    private final SoapV2Properties v2Properties; // V2 (nuevo)
    private final SoapMapper soapMapper;      // V1 (existente)
    private final SoapV2Mapper soapV2Mapper;  // V2 (nuevo)

    public SoapGatewayAdapter(WebClient.Builder webClientBuilder,
                              SoapProperties properties,
                              SoapV2Properties v2Properties,
                              SoapMapper soapMapper,
                              SoapV2Mapper soapV2Mapper) {
        this.properties = properties;
        this.v2Properties = v2Properties;
        this.soapMapper = soapMapper;
        this.soapV2Mapper = soapV2Mapper;

        HttpClient httpClient = HttpClient.create();

        this.webClient = webClientBuilder
            .baseUrl(properties.endpoint())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();

        this.webClientV2 = webClientBuilder
            .baseUrl(v2Properties.endpoint())
            .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
            .build();
    }
```

#### 7b. Refactor: extraer metodo `executeSoapCall()` compartido

En lugar de duplicar las ~50 lineas de logica reactiva (retry, timeout, 6 `onErrorResume`), se extrae un metodo privado que recibe parametros para diferenciar V1 de V2:

```java
/**
 * Executes a SOAP HTTP POST call with retry, timeout, and error handling.
 *
 * @param client        the WebClient instance (pre-configured with baseUrl)
 * @param soapEnvelope  the complete SOAP XML envelope string
 * @param traceId       correlation id for logging
 * @param timeoutSeconds timeout duration for the HTTP call
 * @param maxRetries    number of retry attempts (0 = no retry)
 * @param soapAction    SOAPAction HTTP header value (null or blank = header omitted)
 * @param attemptCount  mutable counter shared across retries
 * @return Mono of the raw SOAP XML response string
 */
private Mono<String> executeSoapCall(WebClient client,
                                     String soapEnvelope,
                                     String traceId,
                                     int timeoutSeconds,
                                     int maxRetries,
                                     @Nullable String soapAction,
                                     AtomicInteger attemptCount) {

    WebClient.RequestBodySpec bodySpec = client.post()
        .contentType(MediaType.TEXT_XML)
        .bodyValue(soapEnvelope);

    if (soapAction != null && !soapAction.isBlank()) {
        bodySpec.header("SOAPAction", soapAction);
    }

    Mono<String> httpCall = Mono.delay(Duration.ofMillis(v2Properties.delayMillis()))
        .then(bodySpec.retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .doOnError(e -> {
                int currentAttempt = attemptCount.incrementAndGet();
                log.log(Level.WARNING, "SOAP attempt {0}/{1} failed for traceId={2}: {3}",
                    new Object[]{currentAttempt, maxRetries + 1, traceId, e.getMessage()});
            }));

    if (maxRetries > 0) {
        return httpCall.retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(500))
            .filter(this::isRetryable)
            .doBeforeRetry(signal -> {
                int currentAttempt = attemptCount.incrementAndGet();
                log.log(Level.INFO, "Retrying SOAP for traceId={0}, attempt {1}/{2}",
                    new Object[]{traceId, currentAttempt, maxRetries + 1});
            }));
    }

    return httpCall;
}
```

#### 7c. Refactorizar `send()` existente para usar `executeSoapCall()`

El metodo `send()` actual se simplifica delegando la parte HTTP a `executeSoapCall()`:

```java
@Override
public Mono<FileUploadResult> send(FileUploadRequest request) {
    return Mono.deferContextual(ctx -> {
        String traceId = ctx.getOrDefault(ApiConstants.HEADER_TRACE_ID, "unknown");
        int maxRetries = properties.retryAttempts();
        AtomicInteger attemptCount = new AtomicInteger(1);

        String soapEnvelope = soapMapper.toFullSoapMessage(request);

        return executeSoapCall(webClient, soapEnvelope, traceId,
                properties.timeoutSeconds(), maxRetries,
                SoapConstants.FILE_SERVICE + SoapConstants.SOAP_ACTION_UPLOAD,
                attemptCount)
            .map(soapMapper::fromSoapXml)
            .doOnNext(response -> log.log(Level.INFO,
                "SOAP response received for traceId={0}: correlationId={1}",
                new Object[]{traceId, response.getCorrelationId()}))
            .map(response -> toFileUploadResult(response, attemptCount.get()))
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.log(Level.SEVERE, "SOAP HTTP error for traceId={0}: {1} {2}",
                    new Object[]{traceId, ex.getStatusCode(), ex.getMessage()});
                return Mono.just(buildErrorResult(traceId, SoapErrorCodes.BAD_GATEWAY,
                    ex.getMessage(), attemptCount.get()));
            })
            .onErrorResume(TimeoutException.class, ex -> {
                log.log(Level.SEVERE, "SOAP timeout for traceId={0}", traceId);
                return Mono.just(buildErrorResult(traceId, SoapErrorCodes.GATEWAY_TIMEOUT,
                    "Timeout after " + properties.timeoutSeconds() + "s", attemptCount.get()));
            })
            .onErrorResume(IOException.class, ex -> {
                log.log(Level.SEVERE, "SOAP IO error for traceId={0}: {1}",
                    new Object[]{traceId, ex.getMessage()});
                return Mono.just(buildErrorResult(traceId, SoapErrorCodes.UNKNOWN_ERROR,
                    ex.getMessage(), attemptCount.get()));
            })
            .onErrorResume(ConnectException.class, ex -> {
                log.log(Level.SEVERE, "SOAP connection error for traceId={0}: {1}",
                    new Object[]{traceId, ex.getMessage()});
                return Mono.just(buildErrorResult(traceId, SoapErrorCodes.SERVICE_UNAVAILABLE,
                    ex.getMessage(), attemptCount.get()));
            })
            .onErrorResume(Throwable.class, ex -> {
                log.log(Level.SEVERE, "SOAP unexpected error for traceId={0}: {1}",
                    new Object[]{traceId, ex.getMessage()});
                return Mono.just(buildErrorResult(traceId, SoapErrorCodes.UNKNOWN_ERROR,
                    ex.getMessage(), attemptCount.get()));
            });
    });
}
```

#### 7d. Nuevo metodo `transmitirDocumento()`

```java
@Override
public Mono<FileUploadResult> transmitirDocumento(FileUploadRequest request) {
    return Mono.deferContextual(ctx -> {
        String traceId = ctx.getOrDefault(ApiConstants.HEADER_TRACE_ID, "unknown");
        int maxRetries = v2Properties.retryAttempts();
        AtomicInteger attemptCount = new AtomicInteger(1);

        String soapEnvelope = soapV2Mapper.buildEnvelope(request, v2Properties, traceId);

        return executeSoapCall(webClientV2, soapEnvelope, traceId,
                v2Properties.timeoutSeconds(), maxRetries,
                v2Properties.soapAction(),
                attemptCount)
            .map(soapV2Mapper::parseResponse)
            .doOnNext(response -> log.log(Level.INFO,
                "SOAP V2 response received for traceId={0}: correlationId={1}",
                new Object[]{traceId, response.getCorrelationId()}))
            .map(response -> toFileUploadResult(response, attemptCount.get()))
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.log(Level.SEVERE, "SOAP V2 HTTP error for traceId={0}: {1} {2}",
                    new Object[]{traceId, ex.getStatusCode(), ex.getMessage()});
                return Mono.just(buildErrorResult(traceId, SoapErrorCodes.BAD_GATEWAY,
                    ex.getMessage(), attemptCount.get()));
            })
            .onErrorResume(TimeoutException.class, ex -> {
                log.log(Level.SEVERE, "SOAP V2 timeout for traceId={0}", traceId);
                return Mono.just(buildErrorResult(traceId, SoapErrorCodes.GATEWAY_TIMEOUT,
                    "Timeout after " + v2Properties.timeoutSeconds() + "s", attemptCount.get()));
            })
            .onErrorResume(IOException.class, ex -> {
                log.log(Level.SEVERE, "SOAP V2 IO error for traceId={0}: {1}",
                    new Object[]{traceId, ex.getMessage()});
                return Mono.just(buildErrorResult(traceId, SoapErrorCodes.UNKNOWN_ERROR,
                    ex.getMessage(), attemptCount.get()));
            })
            .onErrorResume(ConnectException.class, ex -> {
                log.log(Level.SEVERE, "SOAP V2 connection error for traceId={0}: {1}",
                    new Object[]{traceId, ex.getMessage()});
                return Mono.just(buildErrorResult(traceId, SoapErrorCodes.SERVICE_UNAVAILABLE,
                    ex.getMessage(), attemptCount.get()));
            })
            .onErrorResume(Throwable.class, ex -> {
                log.log(Level.SEVERE, "SOAP V2 unexpected error for traceId={0}: {1}",
                    new Object[]{traceId, ex.getMessage()});
                return Mono.just(buildErrorResult(traceId, SoapErrorCodes.UNKNOWN_ERROR,
                    ex.getMessage(), attemptCount.get()));
            });
    });
}
```

### Paso 8: Agregar configuracion en archivos YAML

#### 8a. `application.yml`

```yaml
app:
  soap:
    endpoint: ${SOAP_ENDPOINT:http://localhost:9000/soap/fileservice}
    timeout-seconds: 30
    retry-attempts: 3
    v2:
      # ===== REQUERIDOS (@NotBlank) =====
      endpoint: ${SOAP_V2_ENDPOINT:http://localhost:9000/soap/adminDocs}
      system-id: ${SOAP_V2_SYSTEM_ID:123}
      user-name: ${SOAP_V2_USER_NAME:775757775}
      header-namespace: ${SOAP_V2_HEADER_NS:http://prueba.com/ents/SOI/MessageFormat/V2.1}
      body-namespace: ${SOAP_V2_BODY_NS:http://prueba.com/intf/factory/adminDocs/V1.0}
      sub-tipo-documental: ${SOAP_V2_SUB_TIPO:Facturas}

      # ===== OPCIONALES =====
      user-token: ${SOAP_V2_USER_TOKEN:}
      destination-name: ${SOAP_V2_DEST_NAME:bussinesdocs}
      destination-namespace: ${SOAP_V2_DEST_NS:http://prueba.com/intf/factory/adminDocs/Enlace/V1.0}
      destination-operation: ${SOAP_V2_DEST_OP:senddocs}
      soap-action: ${SOAP_V2_SOAP_ACTION:}

      # ===== COLECCIONES =====
      classifications:
        - "http://prueba.com/clas/AppsUpdated"
      message-context:
        key01: "value01"
        key02: "value02"
      meta-data:
        xIdentificadorFisico: "65464904"
        xFilial: "prueba"

      # ===== TIMEOUTS Y RETRIES =====
      delay-millis: 0          # 0 = sin delay. Para V1 se mantiene el delay actual en el metodo send()
      timeout-seconds: 30
      retry-attempts: 3
```

#### 8b. `application-dev.yml`

```yaml
app:
  soap:
    v2:
      endpoint: ${SOAP_V2_ENDPOINT:http://localhost:9000/soap/adminDocs}
      system-id: "123"
      user-name: "test-user"
      timeout-seconds: 5
      retry-attempts: 1
      delay-millis: 0
```

#### 8c. `application-prod.yml`

```yaml
app:
  soap:
    v2:
      timeout-seconds: 15
      retry-attempts: 2
      delay-millis: 0
```

### Paso 9: Actualizar `Application.java`

Agregar `SoapV2Properties.class` a la anotacion existente:

```java
@EnableConfigurationProperties({
    SoapProperties.class,
    SoapV2Properties.class,
    // ... otras
})
```

### Paso 10: Tests a implementar

#### 10a. Tests para `SoapV2Properties` (binding de Spring Boot)

- `shouldBindRequiredFields()`: verifica que `@NotBlank` fields se llenan desde YAML
- `shouldNormalizeNullCollections()`: verifica que `classifications`, `messageContext`, `metaData` son colecciones vacias (no null) cuando no se configuran en YAML
- `shouldFailOnMissingEndpoint()`: `@NotBlank endpoint` lanza excepcion si falta
- `shouldBindEmptyMaps()`: verifica binding con `message-context:` sin sub-keys produce map vacio

#### 10b. Tests para `SoapV2Mapper.buildEnvelope()` (validacion de XML)

- **`shouldGenerateCorrectEnvelope()`**: genera el envelope con propiedades completas y compara con el XML esperado usando XmlAssert o XmlUnit. Verifica namespaces, prefijos, y estructura exacta.
- **`shouldOmitOptionalBlocks()`**: con todos los campos opcionales vacios/null, verifica que los bloques `<messageContext>`, `<destination>`, `<classifications>`, `<userToken>` NO aparecen en el XML.
- **`shouldHandleNullContent()`**: `FileUploadRequest.content = null` → `<archivo>` vacio, no NPE.
- **`shouldHandleNullFilename()`**: `FileUploadRequest.filename = null` → `<nombreArchivo>unknown</nombreArchivo>`.
- **`shouldHandleEmptyMetaData()`**: `metaData` vacio → bloque `<metaData>` ausente en el body.

#### 10c. Tests para `SoapGatewayAdapter.transmitirDocumento()` (integracion con mock)

- **`sendV2_whenSuccessful_returnsSuccessResult()`**: mock server V2 responde OK → `FileUploadResult.success = true`.
- **`sendV2_whenHttp500_returnsBadGateway()`**: mock server retorna 500 → `SoapErrorCodes.BAD_GATEWAY`.
- **`sendV2_whenTimeout_returnsTimeoutError()`**: timeout → `SoapErrorCodes.GATEWAY_TIMEOUT`.
- **`sendV2_whenRetryable_thenRetries()`**: mock falla 2 veces con 503, la tercera OK → verifica `attemptCount = 3`.

#### 10d. Extension de `PortableSoapMock` para V2

Agregar un segundo contexto/path en `PortableSoapMock` que responda con el formato del response V2 (`<transmitirDocumentoResponse>`).

#### 10e. Validacion de que tests V1 no se rompen

Ejecutar TODOS los tests existentes y verificar que pasan sin modificaciones. El refactor de `executeSoapCall()` no debe alterar el comportamiento externo de `send()`.

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
│   │   ├── SoapGatewayAdapter.java    # [MODIFICADO]
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
│           ├── SoapEnvelopeWrapper.java    # [MODIFICADO: agregar 2 clases al JAXBContext]
│           └── model/
│               ├── UploadFileRequest.java          # [EXISTENTE, sin cambios]
│               ├── UploadFileResponse.java         # [EXISTENTE, sin cambios]
│               ├── TransmitirDocumentoRequest.java # [NUEVO]
│               ├── TransmitirDocumentoResponse.java# [NUEVO]
│               ├── MetaDataWrapper.java            # [NUEVO]
│               └── MetaDataEntry.java              # [NUEVO]

src/test/java/com/example/fileprocessor/
├── infrastructure/drivenadapters/soap/
│   ├── SoapGatewayAdapterTest.java     # [MODIFICADO: agregar tests V2]
│   └── SoapV2PropertiesTest.java       # [NUEVO]
├── infrastructure/soap/mapper/
│   └── SoapV2MapperTest.java           # [NUEVO]
└── mock/
    └── PortableSoapMock.java           # [MODIFICADO: agregar mock para V2]
```

---

## 7. Preguntas Pendientes para Revision con el Equipo Destino

| # | Pregunta | Estado | Accion si no se confirma |
|---|----------|--------|--------------------------|
| 1 | **Formato de `<archivo>`**: El ejemplo muestra string "1234567890ABCDEFGHIJKPDF". ¿Es Base64 del contenido binario o es un identificador de documento? | PENDIENTE | Se asumira Base64 (`request.getContent()`) con la opcion de cambiar via configuracion |
| 2 | **SOAPAction HTTP Header**: El destino esta en el header SOAP (`<destination>`). ¿Se requiere ademas el header HTTP `SOAPAction`? | PENDIENTE | El campo `soapAction` ya existe en `SoapV2Properties`. Si no se configura, no se envia el header. |
| 3 | **Estructura exacta del Response V2**: La clase `TransmitirDocumentoResponse` asume campos analogos a V1. Se necesita el XML real del response. | **BLOQUEANTE** | Sin el response real, toda la ruta de parsing es especulativa. No implementar sin esto. |
| 4 | **URL del endpoint V2 por ambiente**: Dev, QA, Prod. | PENDIENTE | Ya contemplado en `SoapV2Properties` + env vars |
| 5 | **Delay artificial**: El metodo `send()` V1 tiene `Mono.delay(20s)`. ¿V2 tambien lo necesita? | PENDIENTE | `SoapV2Properties.delayMillis()` ya existe (default 0). Se configura si se requiere. El delay de V1 es tech-debt separado. |
| 6 | **`destination` parcialmente configurado**: Si `destinationName` se configura pero `destinationNamespace` no, ¿se omite todo el bloque o se envia parcial? | PENDIENTE | Por defecto: si `destinationName` es blank → se omite todo el bloque `<destination>`. |
| 7 | **Naming final**: ¿`SoapGatewayV2` y `transmitirDocumento()` son los nombres correctos para el equipo? | PENDIENTE | Ajustar segun feedback |

---

## 8. Riesgos y Mitigaciones

| Riesgo | Probabilidad | Impacto | Mitigacion |
|--------|-------------|---------|------------|
| El servicio SOAP V2 no acepta el formato generado | Media | Alto | Usar el XML de ejemplo como spec exacta. Validar cada elemento generado contra el ejemplo. |
| Response V2 tiene estructura diferente a la asumida | Media | Alto | **No implementar `TransmitirDocumentoResponse` ni `parseResponse()` sin el response real.** Es el unico punto bloqueante. |
| StAX + JAXB hibrido produce XML con small divergencias vs el ejemplo | Media | Medio | XmlAssert en tests. Si el servicio es estricto con formato (ej: orden de atributos), migrar a full StAX. |
| El refactor `executeSoapCall()` rompe el metodo `send()` V1 | Baja | Alto | El refactor extrae logica identica. Tests existentes de V1 intactos verifican regresion. |
| `JAXBContext` compartido V1+V2 causa ClassNotFoundException en runtime | Baja | Medio | `JAXBContext.newInstance()` acepta multiples clases. Las clases V2 son nuevas (no dependen de libs externas). |
| Doble `WebClient` aumenta uso de recursos | Baja | Bajo | Los pools de conexion en Reactor Netty son por host remoto, no por WebClient. Si V2 apunta al mismo host que V1, se reutiliza el mismo WebClient. |

---

## 9. Criterios de Aceptacion

### Funcionalidad
- [ ] `SoapGatewayAdapter` implementa tanto `SoapGateway` como `SoapGatewayV2`
- [ ] El metodo `send()` existente funciona exactamente igual (sin regresiones)
- [ ] El metodo `transmitirDocumento()` genera el envelope XML segun el ejemplo de referencia
- [ ] Namespaces `headerNamespace` y `bodyNamespace` se leen de `SoapV2Properties`, NO de constantes Java

### Correccion del envelope
- [ ] Bloques opcionales (`messageContext`, `userToken`, `destination`, `classifications`) no aparecen en el XML cuando no estan configurados
- [ ] `<metaData>` no aparece en el body si `SoapV2Properties.metaData()` esta vacio
- [ ] `<nombreArchivo>` usa `"unknown"` si `request.getFilename()` es null
- [ ] `<archivo>` usa string vacio si `request.getContent()` es null

### Manejo de errores
- [ ] `executeSoapCall()` maneja HTTP errors, timeout, y errores de conexion
- [ ] Errores http 502/503/504/429 + TimeoutException + ConnectException son retryable
- [ ] `ctx.getOrDefault()` para traceId (no lanza excepcion si falta el key)

### Tests
- [ ] Tests para `SoapV2Mapper.buildEnvelope()` con XML de salida validado con XmlAssert
- [ ] Tests de binding de `SoapV2Properties` desde YAML con colecciones normalizadas
- [ ] Tests para `SoapGatewayAdapter.transmitirDocumento()` (success, http error, timeout, retry)
- [ ] Tests existentes de V1 continuan pasando sin modificaciones

### Configuracion
- [ ] `application.yml` tiene el bloque `app.soap.v2` completo con defaults
- [ ] `application-dev.yml` y `application-prod.yml` tienen sus variantes
- [ ] Todos los valores tienen env var para override (`${SOAP_V2_...}`)

---

## 10. Resumen de la Auditoria y Correcciones Aplicadas

Este plan fue auditado por un agente especializado siguiendo el criterio de un ingeniero backend senior (20 anos de experiencia). A continuacion, cada hallazgo y su resolucion:

### HIGH (resueltos en esta version del plan)

| # | Hallazgo | Solucion aplicada |
|---|----------|-------------------|
| H1 | Namespaces vendor-specific hardcodeados en `SoapV2Constants` (`http://prueba.com/ents/SOI/MessageFormat/V2.1`, `http://prueba.com/intf/factory/adminDocs/V1.0`) | Movidos a `SoapV2Properties` como `headerNamespace` y `bodyNamespace` (`@NotBlank`). `SoapV2Constants` ahora solo contiene la URI estandar SOAP 1.1 y los prefijos XML. El `XMLStreamWriter` lee los URIs de `SoapV2Properties`. (Ver Pasos 1 y 3) |
| H2 | `TransmitirDocumentoResponse` y parsing de respuesta V2 son especulativos sin el XML real | Clasificado como **BLOQUEANTE** en la Seccion 7, Pregunta 3. No se debe implementar el response hasta obtener el XML real. La clase JAXB se documenta como especulativa. (Ver Pasos 2d y 7-Q3) |
| H3 | Duplicacion de ~50 lineas de error handling entre `send()` y `transmitirDocumento()` | Se extrae metodo privado `executeSoapCall()` con todos los parametros necesarios (WebClient, envelope, traceId, timeout, maxRetries, soapAction, attemptCount). `send()` y `transmitirDocumento()` lo reutilizan. (Ver Paso 7b) |

### MEDIUM (resueltos en esta version del plan)

| # | Hallazgo | Solucion aplicada |
|---|----------|-------------------|
| M1 | Falta campo `soapAction` en `SoapV2Properties` (asumir que V2 no lo necesita es fragil) | Agregado `String soapAction` como campo opcional. `executeSoapCall()` solo agrega el header HTTP si no es blank. Default vacio en YAML. (Ver Pasos 1 y 7b) |
| M2 | `ctx.get(ApiConstants.HEADER_TRACE_ID)` lanza `NoSuchElementException` si falta el key | Cambiado a `ctx.getOrDefault(ApiConstants.HEADER_TRACE_ID, "unknown")` en ambos metodos (`send()` y `transmitirDocumento()`). (Ver Pasos 7c y 7d) |
| M3 | `SoapV2Properties` no tiene compact constructor: `messageContext`, `metaData`, `classifications` pueden ser null | Agregado compact constructor que normaliza null → `List.of()` / `Map.of()`. Downstream solo verifica `isEmpty()`, nunca null. (Ver Paso 1) |
| M4 | Modificar `SoapEnvelopeWrapper` compartido acopla V1 y V2 | Se documenta el riesgo y la justificacion. `JAXBContext.newInstance()` multi-clase es seguro porque las clases tienen diferentes root elements y namespaces. Si en el futuro V2 requiere configuracion separada, se extraera `SoapV2EnvelopeWrapper`. (Ver Paso 5) |
| M5 | `PortableSoapMock` solo sirve V1; falta mock V2 para tests de integracion | Agregado como item en los tests (Paso 10d). Se necesita un segundo context path o un mock separado. |

### LOW (resueltos en esta version del plan)

| # | Hallazgo | Solucion aplicada |
|---|----------|-------------------|
| L1 | 20-second `Mono.delay` hardcodeado sin resolver para V2 | Agregado `delayMillis` en `SoapV2Properties` (default 0). El delay de V1 queda como tech-debt separado (no se toca en este plan). (Ver Pasos 1 y 7-Q5) |
| L2 | Sin manejo de `filename = null` en `FileUploadRequest` | `buildBodyRequest()` usa `request.getFilename() != null ? filename : "unknown"`. (Ver Paso 4a) |
| L3 | `MessageProperty.java` redundante (misma estructura que `MetaDataEntry`) | Eliminado del plan. Solo existe `MetaDataEntry.java`, reutilizado para ambos contextos. En el header StAX, los elementos se escriben como `<key>`/`<value>` manualmente. (Ver Pasos 2c y 2.1) |
| L4 | `SoapV2Header.java` POJO innecesario (el header se construye directo desde `SoapV2Properties` via StAX) | Eliminado del plan. El StAX escribe directo desde los getters del properties record. (Ver Paso 4a) |

---

## 11. Proximos Pasos (post-revision)

1. Revision y feedback de este plan por el equipo
2. **Resolver Pregunta 3 (BLOQUEANTE):** Obtener XML real del response V2 del equipo dueno del servicio SOAP
3. Resolver Preguntas 1, 2, 4, 5, 6, 7 con el equipo dueno
4. Implementar segun los pasos detallados en la Seccion 5
5. Implementar los tests listados en el Paso 10
6. Probar contra mock del servicio SOAP V2 (extender `PortableSoapMock`)
7. Ejecutar regresion completa de tests V1
8. PR con los cambios
