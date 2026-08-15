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
    private final AnimalDocumentProcessingUseCase animalDocumentProcessingUseCase;

    // Caché diario: total de documentos esperados para Animal
    private final AtomicLong cachedAnimalTotal = new AtomicLong(0);
    private final AtomicReference<LocalDate> cachedDate = new AtomicReference<>(null);

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
     * Consulta el estado del procesamiento diario.
     * Para Animal: compara el total en caché (API externa) vs lo guardado en BD.
     * Para Productos: evalúa los estados agrupados directamente desde BD.
     *
     * @param useCase      Caso de uso ("Animal", "SOAP", etc.)
     * @param forceRefresh Si es true, recarga el total desde la API externa (solo aplica para Animal)
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
     * 1. Obtiene el total esperado de documentos de Animal (del caché o de la API externa).
     * 2. Obtiene los estados agrupados desde la BD local.
     * 3. Compara y determina el estado consolidado.
     */
    private Mono<String> getAnimalProcessStatus(LocalDateTime startOfDay, boolean forceRefresh) {
        return getAnimalExpectedTotal(forceRefresh)
                .flatMap(expectedTotal -> animalPersistenceGateway.countDocumentsGroupedByStateToday(startOfDay)
                        .collectList()
                        .map(stateCounts -> evaluateAnimalStatus(stateCounts, expectedTotal))
                );
    }

    /**
     * Retorna el total esperado de documentos de Animal.
     * Si el caché es del día de hoy y no se fuerza recarga, lo retorna inmediatamente.
     * En caso contrario, delega al AnimalDocumentProcessingUseCase para contar
     * los documentos desde la API externa y actualiza el caché.
     */
    private Mono<Long> getAnimalExpectedTotal(boolean forceRefresh) {
        var today = LocalDate.now();
        var lastCachedDate = cachedDate.get();

        // Si el caché es válido (mismo día) y no se fuerza recarga, retornamos directo
        if (!forceRefresh && today.equals(lastCachedDate) && cachedAnimalTotal.get() > 0) {
            return Mono.just(cachedAnimalTotal.get());
        }

        // Delegamos al UseCase de Animal (fuente única de verdad para descubrir documentos)
        return animalDocumentProcessingUseCase.countTotalPendingDocuments()
                .doOnNext(total -> {
                    cachedAnimalTotal.set(total);
                    cachedDate.set(today);
                });
    }

    // ─── Evaluación de estados ───

    /**
     * Evalúa el estado de Animal comparando contra el total esperado.
     * - Si la BD local tiene menos registros que el total esperado → "0" (en progreso)
     * - Si ya están todos pero hay pendientes de envío → "0" (en progreso)
     * - Si ya están todos y no hay pendientes pero hay fallos → "error"
     * - Si ya están todos y todos están procesados → "exitoso"
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

        // Si aún no se han guardado todos los documentos en BD, sigue en progreso
        if (totalInDb < expectedTotal) {
            return ApiConstants.STATUS_IN_PROGRESS;
        }

        var totalApplicable = processed + pending + technicalFailures;
        if (totalApplicable == 0) {
            return ApiConstants.STATUS_COMPLETED;
        }
        if (pending > 0) {
            return ApiConstants.STATUS_IN_PROGRESS;
        }
        return (technicalFailures > 0) ? ApiConstants.STATUS_ERROR : ApiConstants.STATUS_COMPLETED;
    }
}
