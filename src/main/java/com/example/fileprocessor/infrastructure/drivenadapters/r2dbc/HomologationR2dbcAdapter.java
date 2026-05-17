package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.homologation.HomologationCountry;
import com.example.fileprocessor.domain.entity.homologation.HomologationResult;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.entity.homologation.CategoryManual;
import com.example.fileprocessor.domain.entity.homologation.PaisHomologado;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.CategoryManualEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.PaisHomologadoEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.CategoryManualRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.PaisHomologadoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
@RequiredArgsConstructor
public class HomologationR2dbcAdapter implements HomologationRepository {

    private static final Logger log = Logger.getLogger(HomologationR2dbcAdapter.class.getName());

    private final CategoryManualRepository categoryRepository;
    private final PaisHomologadoRepository paisRepository;

    private final List<CategoryManual> categoryCache = new CopyOnWriteArrayList<>();
    private final List<PaisHomologado> paisCache = new CopyOnWriteArrayList<>();
    private boolean cacheLoaded = false;

    @Override
    public Mono<HomologationResult> resolve(DocumentHistoryDTO history) {
        if (!cacheLoaded) {
            return loadCache().then(Mono.defer(() -> resolveFromCache(history)));
        }
        return resolveFromCache(history);
    }

    private Mono<HomologationResult> resolveFromCache(DocumentHistoryDTO history) {
        String documentId = history.getBusinessDocumentId() != null ? history.getBusinessDocumentId() : "";
        String originFolder = normalizeText(history.getOriginFolder());
        String originCountry = normalizeText(history.getOriginCountry());

        // 1. Resolve Category by prefix
        String categoriaDocument = documentId; // default to documentId or empty
        for (CategoryManual category : categoryCache) {
            if (documentId.startsWith(category.prefijo())) {
                categoriaDocument = category.categoriaDocumento();
                break;
            }
        }

        // 2. Resolve Country and Folder by iterating the DB rules
        String homologationFolder = history.getOriginFolder();
        String homologationCountry = history.getOriginCountry();

        for (PaisHomologado p : paisCache) {
            List<String> folderKeywords = parseKeywords(p.originFolder());
            List<String> countryKeywords = parseKeywords(p.originCountry());

            boolean folderMatch = containsAny(originFolder, folderKeywords);
            boolean countryMatch = Boolean.FALSE.equals(p.aplicaFiltroPais()) || containsAny(originCountry, countryKeywords);

            if (folderMatch && countryMatch) {
                homologationFolder = p.homologationFolder();
                homologationCountry = p.homologationCountry();
                break; // Cortocircuito (Evaluación secuencial)
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

    private List<String> parseKeywords(String dbValue) {
        if (dbValue == null || dbValue.isBlank()) return List.of();
        return java.util.Arrays.stream(dbValue.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (keywords.isEmpty()) return false;
        if (keywords.size() == 1 && "*".equals(keywords.get(0))) return true;
        if (text == null || text.isEmpty()) return false;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> loadCache() {
        log.log(Level.INFO, "Loading homologation cache from database");

        Mono<Void> loadCategories = categoryRepository.findAll()
            .map(entity -> new CategoryManual(entity.getPrefijo(), entity.getCategoriaDocumento()))
            .collectList()
            .doOnNext(list -> {
                categoryCache.clear();
                categoryCache.addAll(list);
                log.log(Level.INFO, "Category cache loaded with {0} entries", new Object[]{categoryCache.size()});
            })
            .then();

        Mono<Void> loadPais = paisRepository.findAll()
            .collectSortedList(java.util.Comparator.comparing(PaisHomologadoEntity::getId))
            .map(list -> list.stream()
                .map(entity -> new PaisHomologado(
                    entity.getOriginFolder(),
                    entity.getOriginCountry(),
                    entity.getHomologationFolder(),
                    entity.getHomologationCountry(),
                    entity.getAplicaFiltroPais()
                )).toList()
            )
            .doOnNext(list -> {
                paisCache.clear();
                paisCache.addAll(list);
                log.log(Level.INFO, "Pais cache loaded with {0} entries", new Object[]{paisCache.size()});
            })
            .then();

        return Mono.zip(loadCategories, loadPais)
            .doOnNext(tuple -> {
                cacheLoaded = true;
                log.log(Level.INFO, "Full homologation cache initialized successfully");
            })
            .then();
    }
}
