package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.HomologationResult;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.entity.CategoryManual;
import com.example.fileprocessor.domain.entity.CountryHomologated;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.CategoryManualEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.CountryHomologatedEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.CategoryManualRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.CountryHomologatedRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@Component
public class HomologationR2dbcAdapter implements HomologationRepository {

    private static final Logger LOGGER = Logger.getLogger(HomologationR2dbcAdapter.class.getName());

    private final CategoryManualRepository categoryRepository;
    private final CountryHomologatedRepository countryRepository;

    private Map<String, CategoryManual> categoryCache = new ConcurrentHashMap<>();
    private Map<String, CountryHomologated> countryCache = new ConcurrentHashMap<>();
    private boolean cacheLoaded = false;

    public HomologationR2dbcAdapter(
            CategoryManualRepository categoryRepository,
            CountryHomologatedRepository countryRepository) {
        this.categoryRepository = categoryRepository;
        this.countryRepository = countryRepository;
    }

    @Override
    public Mono<HomologationResult> resolve(String origin, String country) {
        if (!cacheLoaded) {
            return loadCache().then(resolveFromCache(origin, country));
        }
        return resolveFromCache(origin, country);
    }

    private Mono<HomologationResult> resolveFromCache(String origin, String country) {
        String resolvedOrigin = resolveOrigin(origin);
        String resolvedCountry = resolveCountry(country);

        return Mono.just(new HomologationResult(resolvedOrigin, resolvedCountry));
    }

    String resolveOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return origin;
        }

        String normalized = removeDiacritics(origin.toLowerCase());

        for (Map.Entry<String, CategoryManual> entry : categoryCache.entrySet()) {
            String key = entry.getKey();
            if (key != null && removeDiacritics(key.toLowerCase()).contains(normalized)) {
                CategoryManual category = entry.getValue();
                return category.descripcionManual() != null ? category.descripcionManual() : origin;
            }
        }

        return origin;
    }

    String resolveCountry(String country) {
        if (country == null || country.isBlank()) {
            return country;
        }

        CountryHomologated homologated = countryCache.get(country);
        if (homologated != null) {
            return homologated.countryHomologated() != null ? homologated.countryHomologated() : country;
        }

        return country;
    }

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    static String removeDiacritics(String text) {
        if (text == null) return null;
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        return DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
    }

    private Mono<Void> loadCache() {
        LOGGER.log(Level.INFO, "Loading homologation cache");

        Mono<Void> loadCategories = categoryRepository.findAll()
            .collectMap(
                CategoryManualEntity::getCategoria,
                entity -> new CategoryManual(entity.getCategoria(), entity.getDescripcionManual())
            )
            .doOnNext(map -> {
                categoryCache.clear();
                categoryCache.putAll(map);
                LOGGER.log(Level.INFO, "Category cache loaded with {0} entries", new Object[]{categoryCache.size()});
            })
            .then();

        Mono<Void> loadCountries = countryRepository.findAll()
            .collectMap(
                CountryHomologatedEntity::getCountry,
                entity -> new CountryHomologated(entity.getCountry(), entity.getCountryHomologated())
            )
            .doOnNext(map -> {
                countryCache.clear();
                countryCache.putAll(map);
                LOGGER.log(Level.INFO, "Country cache loaded with {0} entries", new Object[]{countryCache.size()});
            })
            .then();

        return Mono.zip(loadCategories, loadCountries)
            .doOnNext(tuple -> cacheLoaded = true)
            .then();
    }
}