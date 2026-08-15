package com.example.fileprocessor.domain.usecase;

import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.IN_PROGRESS;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.NO_SUCURSAL;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PENDING;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PROCESSED;

import com.example.fileprocessor.domain.entity.animal.AnimalDocument;
import com.example.fileprocessor.domain.entity.animal.AnimalDocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.product.StateCount;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.PersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Caso de uso para consultar el estado de sincronización y procesamiento diario.
 * Implementa un caché en memoria del total de documentos de Animal,
 * válido únicamente durante el día actual. Se puede forzar la recarga
 * enviando el parámetro forceRefresh = true.
 */
@RequiredArgsConstructor
public class GetStatusUseCase {

    private final ProductMasterRepository productMasterRepository;
    private final DocumentRepository documentRepository;
    private final PersistenceGateway<AnimalDocument, AnimalDocumentHistoryDTO> animalPersistenceGateway;
    private final AnimalDocumentProvider animalDocumentProvider;

    // Caché diario: total de documentos esperados para Animal
    private final AtomicLong cachedAnimalTotal = new AtomicLong(0);
    private final AtomicReference<LocalDate> cachedDate = new AtomicReference<>(null);

    /**
     * Checks if the synchronization of documents is completed by comparing the count of local documents with the count of master products.
     *
     * Secuencia:
     * 1. Obtiene el inicio del día actual.
     * 2. Cuenta el total de productos en el repositorio maestro y el total de documentos creados hoy en la base local.
     * 3. Compara ambas cantidades: si la cantidad local es mayor o igual a la maestra, el estado es completado.
     * 4. De lo contrario, el estado es en progreso.
     *
     * @param useCase the use case identifier
     * @return a Mono emitting the sync status
     */
    public Mono<String> getSyncStatus(String useCase) {
        var startOfDay = LocalDateTime.now().with(LocalTime.MIN);

        return Mono.zip(
                productMasterRepository.countAllProducts().defaultIfEmpty(0L),
                documentRepository.countDocumentsCreatedToday(startOfDay, useCase).defaultIfEmpty(0L)
        ).map(tuple -> {
            var masterCount = tuple.getT1();
            var localCount = tuple.getT2();
            return (localCount >= masterCount)
                    ? ApiConstants.STATUS_COMPLETED
                    : ApiConstants.STATUS_IN_PROGRESS;
        });
    }

    /**
     * Queries the daily processing status depending on the use case.
     * Para Animal: compara el total en caché (API externa) vs lo guardado en BD.
     * Para Productos: evalúa los estados agrupados directamente desde BD.
     *
     * Secuencia:
     * 1. Establece el inicio del día para evaluar solo el procesamiento actual.
     * 2. Si el caso de uso es "Animal", delega al método específico de estado de Animal.
     * 3. Para otros casos de uso, cuenta los documentos agrupados por estado desde la base de datos local.
     * 4. Recorre los estados, ignorando reglas de negocio o falta de sucursal.
     * 5. Suma las cantidades de documentos procesados, pendientes y con fallos técnicos.
     * 6. Evalúa las sumas: si no aplica ninguno, está completado; si hay pendientes, está en progreso; si hay fallos, retorna error; de lo contrario, completado.
     *
     * @param useCase      Caso de uso ("Animal", "SOAP", etc.)
     * @param forceRefresh Si es true, recarga el total desde la API externa (solo aplica para Animal)
     * @return a Mono emitting the process status
     */
    public Mono<String> getProcessStatus(String useCase, boolean forceRefresh) {
        // 1. Establecemos el inicio del día para solo tener en cuenta el procesamiento de hoy
        var startOfDay = LocalDateTime.now().with(LocalTime.MIN);

        // 2. Si es Animal, usamos la lógica de comparación contra el total en caché
        if (AnimalDocument.USE_CASE_NAME.equals(useCase)) {
            return getAnimalProcessStatus(startOfDay, forceRefresh);
        }

        // 3. Para otros casos de uso (SOAP, S3), evaluamos directamente los estados agrupados
        return documentRepository.countDocumentsGroupedByStateToday(startOfDay, useCase)
                .collectList()
                .map(list -> {
                    long processed = 0;
                    long pending = 0;
                    long technicalFailures = 0;

                    for (var row : list) {
                        var state = row.getState();
                        var count = row.getTotal();

                        if (NO_SUCURSAL.name().equals(state) || ProcessingResultCodes.isBusinessRule(state)) {
                            continue;
                        }

                        if (PENDING.name().equals(state) || IN_PROGRESS.name().equals(state)) {
                            pending += count;
                        } else if (PROCESSED.name().equals(state)) {
                            processed += count;
                        } else {
                            technicalFailures += count;
                        }
                    }

                    var totalApplicable = processed + pending + technicalFailures;
                    if (totalApplicable == 0) {
                        return ApiConstants.STATUS_COMPLETED;
                    }
                    if (pending > 0) {
                        return ApiConstants.STATUS_IN_PROGRESS;
                    }
                    return (technicalFailures > 0) ? ApiConstants.STATUS_ERROR : ApiConstants.STATUS_COMPLETED;
                });
    }

    // ─── ANIMAL: Lógica de status con caché diario ───

    /**
     * Retrieves the processing status specifically for the Animal use case by comparing expected totals against local database state counts.
     *
     * Secuencia:
     * 1. Obtiene el total esperado de documentos de Animal.
     * 2. Consulta la base de datos local para contar los documentos agrupados por estado en el día actual.
     * 3. Convierte el resultado a una lista y evalúa el estado consolidado mediante una función auxiliar.
     *
     * @param startOfDay the start of the current day
     * @param forceRefresh true to force cache refresh
     * @return a Mono emitting the animal process status
     */
    private Mono<String> getAnimalProcessStatus(LocalDateTime startOfDay, boolean forceRefresh) {
        return getAnimalExpectedTotal(forceRefresh)
                .flatMap(expectedTotal -> animalPersistenceGateway.countDocumentsGroupedByStateToday(startOfDay)
                        .collectList()
                        .map(stateCounts -> evaluateAnimalStatus(stateCounts, expectedTotal))
                );
    }

    /**
     * Returns the expected total of Animal documents, utilizing a daily cache to optimize external API calls.
     *
     * Secuencia:
     * 1. Obtiene la fecha actual y la fecha de la última caché.
     * 2. Verifica si la recarga no es forzada y si la caché corresponde al día actual y tiene un valor válido.
     * 3. Si la caché es válida, retorna el total almacenado directamente.
     * 4. Si no, llama al proveedor para contar los documentos pendientes desde la API externa.
     * 5. Actualiza el valor de la caché y la fecha con el nuevo total obtenido.
     *
     * @param forceRefresh true to bypass the cache and fetch directly
     * @return a Mono emitting the expected total
     */
    private Mono<Long> getAnimalExpectedTotal(boolean forceRefresh) {
        var today = LocalDate.now();
        var lastCachedDate = cachedDate.get();

        // Si el caché es válido (mismo día) y no se fuerza recarga, retornamos directo
        if (!forceRefresh && today.equals(lastCachedDate) && cachedAnimalTotal.get() > 0) {
            return Mono.just(cachedAnimalTotal.get());
        }

        // Delegamos al Provider de Animal (fuente única de verdad para descubrir documentos)
        return animalDocumentProvider.countTotalPendingDocuments()
                .doOnNext(total -> {
                    cachedAnimalTotal.set(total);
                    cachedDate.set(today);
                });
    }

    // ─── Evaluación de estados ───

    /**
     * Evaluates the final status of the Animal processing by comparing database state aggregations against the expected total.
     *
     * Secuencia:
     * 1. Inicializa los contadores para procesados, pendientes, fallos técnicos y total en base de datos.
     * 2. Recorre cada conteo de estado, ignorando aquellos que son reglas de negocio o no tienen sucursal.
     * 3. Acumula el total de documentos válidos encontrados en la base de datos.
     * 4. Suma los documentos según su estado (pendiente/en progreso, procesado, o fallo técnico).
     * 5. Calcula el total aplicable.
     * 6. Determina el estado final: si hay menos documentos en BD que los esperados o hay pendientes, es en progreso.
     * 7. Si no hay aplicables, es completado. Si hay fallos técnicos, es error; de lo contrario, completado.
     *
     * @param stateCounts the aggregated state counts from the database
     * @param expectedTotal the expected total of documents
     * @return the evaluated status as a String
     */
    private String evaluateAnimalStatus(List<StateCount> stateCounts, long expectedTotal) {
        long processed = 0;
        long pending = 0;
        long technicalFailures = 0;
        long totalInDb = 0;

        for (var row : stateCounts) {
            var state = row.getState();
            var count = row.getTotal();

            if (NO_SUCURSAL.name().equals(state) || ProcessingResultCodes.isBusinessRule(state)) {
                continue;
            }

            totalInDb += count;

            if (PENDING.name().equals(state) || IN_PROGRESS.name().equals(state)) {
                pending += count;
            } else if (PROCESSED.name().equals(state)) {
                processed += count;
            } else {
                technicalFailures += count;
            }
        }

        var totalApplicable = processed + pending + technicalFailures;
        String finalStatus;

        if (totalInDb < expectedTotal || pending > 0) {
            finalStatus = ApiConstants.STATUS_IN_PROGRESS;
        } else if (totalApplicable == 0) {
            finalStatus = ApiConstants.STATUS_COMPLETED;
        } else {
            finalStatus = (technicalFailures > 0) ? ApiConstants.STATUS_ERROR : ApiConstants.STATUS_COMPLETED;
        }

        return finalStatus;
    }
}
