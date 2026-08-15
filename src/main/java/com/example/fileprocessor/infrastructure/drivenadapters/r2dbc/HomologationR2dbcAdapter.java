package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.homologation.HomologationCountry;
import com.example.fileprocessor.domain.entity.homologation.HomologationResult;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.entity.homologation.CategoryManual;
import com.example.fileprocessor.domain.entity.homologation.PaisHomologado;
import com.example.fileprocessor.domain.entity.product.BaseDocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.PaisHomologadoEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.CategoryManualRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.PaisHomologadoRepository;
import com.example.fileprocessor.infrastructure.helpers.rule.JsonRuleEvaluator;
import com.example.fileprocessor.infrastructure.helpers.rule.JsonRuleEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
@RequiredArgsConstructor
public class HomologationR2dbcAdapter implements HomologationRepository {

    private static final Logger log = Logger.getLogger(HomologationR2dbcAdapter.class.getName());

    private final CategoryManualRepository categoryRepository;
    private final PaisHomologadoRepository paisRepository;
    private final JsonRuleEvaluator ruleEvaluator;

    private final List<CategoryManual> categoryCache = new CopyOnWriteArrayList<>();
    private final List<PaisHomologado> paisCache = new CopyOnWriteArrayList<>();
    private boolean cacheLoaded = false;

    /**
     * Resuelve la homologación de un documento basándose en su historial.
     * <p>
     * Secuencia:
     * 1. Verifica si la caché de homologación ya está cargada.
     * 2. Si no lo está, carga la caché desde la base de datos y luego resuelve utilizando la caché.
     * 3. Si ya está cargada, resuelve directamente desde la caché.
     *
     * @param history el historial base del documento
     * @return un Mono con el resultado de la homologación
     */
    @Override
    public Mono<HomologationResult> resolve(BaseDocumentHistoryDTO history) {
        if (!cacheLoaded) {
            return loadCache().then(Mono.defer(() -> resolveFromCache(history)));
        }
        return resolveFromCache(history);
    }

    /**
     * Resuelve la homologación de un documento utilizando la caché en memoria.
     * <p>
     * Secuencia:
     * 1. Extrae el ID del documento, usando un string vacío si es null.
     * 2. Recorre la caché de categorías buscando un prefijo que coincida con el ID del documento para asignar la categoría.
     * 3. Extrae la carpeta y el país de origen del historial.
     * 4. Evalúa las reglas dinámicas JSON de la caché de países contra el historial para determinar el país y carpeta homologados.
     * 5. Construye y retorna el resultado de la homologación con los datos encontrados.
     *
     * @param history el historial base del documento
     * @return un Mono con el resultado de la homologación
     */
    private Mono<HomologationResult> resolveFromCache(BaseDocumentHistoryDTO history) {
        String documentId = history.getBusinessDocumentId() != null ? history.getBusinessDocumentId() : "";

        // 1. Resolve Category by prefix
        String categoriaDocument = documentId; // default to documentId or empty
        for (CategoryManual category : categoryCache) {
            if (documentId.startsWith(category.prefijo())) {
                categoriaDocument = category.categoriaHomologado();
                break;
            }
        }

        // 2. Resolve Country and Folder by dynamic JSON engine
        String homologationFolder = history.getOriginFolder();
        String homologationCountry = history.getOriginCountry();

        for (PaisHomologado p : paisCache) {
            if (ruleEvaluator.evaluate(p.ruleNode(), history)) {
                homologationFolder = p.homologationFolder();
                homologationCountry = p.homologationCountry();
                break;
            }
        }

        HomologationCountry hc = HomologationCountry.builder()
                .homologationFolder(homologationFolder)
                .homologationCountry(homologationCountry)
                .build();

        return Mono.just(HomologationResult.builder()
                .categoriaDocument(categoriaDocument)
                .homologationCountry(hc)
                .build());
    }

    /**
     * Carga la caché de homologación desde la base de datos a memoria.
     * <p>
     * Secuencia:
     * 1. Registra el inicio de la carga en los logs.
     * 2. Obtiene todas las categorías del repositorio, las mapea al objeto de dominio y actualiza la lista en memoria.
     * 3. Obtiene todos los países homologados, evaluando sus reglas JSON, y actualiza la lista correspondiente en memoria.
     * 4. Ejecuta ambas cargas de manera concurrente (Mono.when).
     * 5. Tras finalizar ambas, marca la caché como cargada y lo registra en el log.
     *
     * @return un Mono vacío al completarse la carga
     */
    private Mono<Void> loadCache() {
        log.log(Level.INFO, "Loading homologation cache from database");

        Mono<Void> loadCategories = categoryRepository.findAll()
                .map(entity -> new CategoryManual(entity.getPrefijo(), entity.getCategoriaHomologado()))
                .collectList()
                .doOnNext(list -> {
                    categoryCache.clear();
                    categoryCache.addAll(list);
                    log.log(Level.INFO, "Category cache loaded with {0} entries",
                            new Object[] { categoryCache.size() });
                })
                .then();

        Mono<Void> loadPais = paisRepository.findAll()
                .collectList()
                .map(list -> list.stream()
                        .map(entity -> new PaisHomologado(
                                entity.getOrden(),
                                entity.getCondicionJsonb() != null ? entity.getCondicionJsonb() : "{}",
                                entity.getHomologationFolder(),
                                entity.getHomologationCountry()))
                        .toList())
                .doOnNext(list -> {
                    paisCache.clear();
                    paisCache.addAll(list);
                    log.log(Level.INFO, "Pais cache loaded with {0} entries", new Object[] { paisCache.size() });
                })
                .then();

        return Mono.when(loadCategories, loadPais)
                .then(Mono.fromRunnable(() -> {
                    cacheLoaded = true;
                    log.log(Level.INFO, "Full homologation cache initialized successfully");
                }));
    }
}
