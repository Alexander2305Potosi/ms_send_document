package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.CategoryManual;
import com.example.fileprocessor.domain.entity.CountryHomologated;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.CategoryManualRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.CountryHomologatedRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HomologationR2dbcAdapterTest {

    @Mock
    private CategoryManualRepository categoryRepository;

    @Mock
    private CountryHomologatedRepository countryRepository;

    private HomologationR2dbcAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new HomologationR2dbcAdapter(categoryRepository, countryRepository);
    }

    private void populateCaches(Map<String, CategoryManual> categories,
                                 Map<String, CountryHomologated> countries) throws Exception {
        Field categoryField = HomologationR2dbcAdapter.class.getDeclaredField("categoryCache");
        categoryField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, CategoryManual> catCache = (Map<String, CategoryManual>) categoryField.get(adapter);
        catCache.clear();
        catCache.putAll(categories);

        Field countryField = HomologationR2dbcAdapter.class.getDeclaredField("countryCache");
        countryField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, CountryHomologated> ctrCache = (Map<String, CountryHomologated>) countryField.get(adapter);
        ctrCache.clear();
        ctrCache.putAll(countries);

        Field loadedField = HomologationR2dbcAdapter.class.getDeclaredField("cacheLoaded");
        loadedField.setAccessible(true);
        loadedField.set(adapter, true);
    }

    // removeDiacritics tests

    @Test
    void removeDiacritics_withAccentedCharacters_removesDiacritics() {
        assertEquals("cafe", HomologationR2dbcAdapter.removeDiacritics("café"));
    }

    @Test
    void removeDiacritics_withTildeN_removesDiacritics() {
        assertEquals("nino", HomologationR2dbcAdapter.removeDiacritics("niño"));
    }

    @Test
    void removeDiacritics_withMultipleDiacritics_removesAll() {
        assertEquals("aeiou", HomologationR2dbcAdapter.removeDiacritics("áéíóú"));
    }

    @Test
    void removeDiacritics_withNull_returnsNull() {
        assertNull(HomologationR2dbcAdapter.removeDiacritics(null));
    }

    @Test
    void removeDiacritics_withNoDiacritics_returnsSame() {
        assertEquals("hello", HomologationR2dbcAdapter.removeDiacritics("hello"));
    }

    @Test
    void removeDiacritics_withMixedText_removesOnlyDiacritics() {
        String result = HomologationR2dbcAdapter.removeDiacritics("Café au Lait");
        assertEquals("Cafe au Lait", result);
    }

    // resolveOrigin tests

    @Test
    void resolveOrigin_withNull_returnsNull() throws Exception {
        populateCaches(Map.of(), Map.of());
        assertNull(adapter.resolveOrigin(null));
    }

    @Test
    void resolveOrigin_withBlank_returnsBlank() throws Exception {
        populateCaches(Map.of(), Map.of());
        assertEquals("  ", adapter.resolveOrigin("  "));
    }

    @Test
    void resolveOrigin_withExactMatch_returnsDescription() throws Exception {
        populateCaches(
            Map.of("electronica", new CategoryManual("electronica", "Electronica")),
            Map.of()
        );
        assertEquals("Electronica", adapter.resolveOrigin("electronica"));
    }

    @Test
    void resolveOrigin_withDiacriticsInInput_findsMatch() throws Exception {
        populateCaches(
            Map.of("electronica", new CategoryManual("electronica", "Electronica")),
            Map.of()
        );
        assertEquals("Electronica", adapter.resolveOrigin("électrônica"));
    }

    @Test
    void resolveOrigin_withDiacriticsInCache_findsMatch() throws Exception {
        populateCaches(
            Map.of("electrónica", new CategoryManual("electrónica", "Electronica")),
            Map.of()
        );
        assertEquals("Electronica", adapter.resolveOrigin("electronica"));
    }

    @Test
    void resolveOrigin_withSubstringMatch_returnsDescription() throws Exception {
        populateCaches(
            Map.of("electronica", new CategoryManual("electronica", "Electronica")),
            Map.of()
        );
        assertEquals("Electronica", adapter.resolveOrigin("elec"));
    }

    @Test
    void resolveOrigin_withNoMatch_returnsOriginal() throws Exception {
        populateCaches(
            Map.of("electronica", new CategoryManual("electronica", "Electronica")),
            Map.of()
        );
        assertEquals("Unknown", adapter.resolveOrigin("Unknown"));
    }

    @Test
    void resolveOrigin_withNullDescription_returnsOriginal() throws Exception {
        populateCaches(
            Map.of("test", new CategoryManual("test", null)),
            Map.of()
        );
        assertEquals("test", adapter.resolveOrigin("test"));
    }

    // resolveCountry tests

    @Test
    void resolveCountry_withNull_returnsNull() throws Exception {
        populateCaches(Map.of(), Map.of());
        assertNull(adapter.resolveCountry(null));
    }

    @Test
    void resolveCountry_withBlank_returnsBlank() throws Exception {
        populateCaches(Map.of(), Map.of());
        assertEquals("", adapter.resolveCountry(""));
    }

    @Test
    void resolveCountry_withExactMatch_returnsHomologated() throws Exception {
        populateCaches(
            Map.of(),
            Map.of("AR", new CountryHomologated("AR", "Argentina"))
        );
        assertEquals("Argentina", adapter.resolveCountry("AR"));
    }

    @Test
    void resolveCountry_withNoMatch_returnsOriginal() throws Exception {
        populateCaches(Map.of(), Map.of());
        assertEquals("XX", adapter.resolveCountry("XX"));
    }

    @Test
    void resolveCountry_withNullHomologated_returnsOriginal() throws Exception {
        populateCaches(
            Map.of(),
            Map.of("AR", new CountryHomologated("AR", null))
        );
        assertEquals("AR", adapter.resolveCountry("AR"));
    }

    // resolve tests

    @Test
    void resolve_withPopulatedCache_returnsHomologatedResult() throws Exception {
        populateCaches(
            Map.of("electronica", new CategoryManual("electronica", "Electronica")),
            Map.of("AR", new CountryHomologated("AR", "Argentina"))
        );

        StepVerifier.create(adapter.resolve("electronica", "AR"))
            .assertNext(result -> {
                assertEquals("Electronica", result.origin());
                assertEquals("Argentina", result.paisHomologado());
            })
            .verifyComplete();
    }

    @Test
    void resolve_withUncachedOrigin_returnsOriginalValue() throws Exception {
        populateCaches(Map.of(), Map.of());

        StepVerifier.create(adapter.resolve("unknown", "XX"))
            .assertNext(result -> {
                assertEquals("unknown", result.origin());
                assertEquals("XX", result.paisHomologado());
            })
            .verifyComplete();
    }
}
