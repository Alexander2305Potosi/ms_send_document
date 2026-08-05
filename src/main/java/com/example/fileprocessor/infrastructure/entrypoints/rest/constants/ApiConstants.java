package com.example.fileprocessor.infrastructure.entrypoints.rest.constants;

/**
 * API-level constants for REST endpoints and integrations.
 */
public final class ApiConstants {

    private ApiConstants() {}

    // Processor types
    public static final String PROCESSOR_SOAP = "soap";
    public static final String PROCESSOR_S3 = "s3";

    // HTTP headers and query params
    public static final String HEADER_TRACE_ID = "message-id";
    public static final String HEADER_USE_CASE = "use-case";
    public static final String HEADER_DATE_INIT = "date_init";
    public static final String HEADER_DATE_END = "date_end";
    public static final String HEADER_PRODUCT_STATUS = "product_status";
    public static final String TYPE_JOB = "type_job";

    // Respuestas de estado del proceso
    public static final String STATUS_IN_PROGRESS = "0";
    public static final String STATUS_COMPLETED = "exitoso";
    public static final String STATUS_ERROR = "error";

    // Clave para la reanudación en el contexto reactivo
    public static final String LAST_PRODUCT_ID = "last_product_id";

    // Patrones y Formateadores de fecha centralizados
    public static final String DATE_PATTERN_YYYY_MM_DD = "yyyy-MM-dd";
    public static final String DATE_PATTERN_YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";

    public static final java.time.format.DateTimeFormatter DATE_FORMATTER_YYYY_MM_DD =
            java.time.format.DateTimeFormatter.ofPattern(DATE_PATTERN_YYYY_MM_DD);

    public static final java.time.format.DateTimeFormatter DATE_TIME_FORMATTER_YYYY_MM_DD_HH_MM_SS =
            java.time.format.DateTimeFormatter.ofPattern(DATE_PATTERN_YYYY_MM_DD_HH_MM_SS);

    // Horas límite para inicio y fin de día
    public static final java.time.LocalTime START_OF_DAY_TIME = java.time.LocalTime.MIDNIGHT;        // 00:00:00
    public static final java.time.LocalTime END_OF_DAY_TIME   = java.time.LocalTime.of(23, 59, 59);  // 23:59:59

    /**
     * Parsea una cadena de texto a LocalDate usando el formateador DATE_FORMATTER_YYYY_MM_DD.
     * Si el valor es nulo, vacío o tiene un formato inválido, retorna LocalDate.now() como fallback.
     */
    public static java.time.LocalDate parseDateOrToday(String value) {
        if (value == null || value.isBlank()) {
            return java.time.LocalDate.now();
        }
        try {
            return java.time.LocalDate.parse(value.trim(), DATE_FORMATTER_YYYY_MM_DD);
        } catch (java.time.format.DateTimeParseException e) {
            return java.time.LocalDate.now();
        }
    }
}
