package com.clinecan.backend.llm;

import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/** No polymorphic typing, coercion, duplicate keys, trailing prose, or unbounded nesting. */
public final class StructuredJson {
    public static final JsonMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(40).maxStringLength(1_500_000).maxNumberLength(100).build())
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
            DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    private StructuredJson() {}
    public static <T> T parse(String json, Class<T> type) {
        if (json == null || json.length() > 1_500_000) throw ProviderException.malformed();
        try { return MAPPER.readValue(json, type); }
        catch (RuntimeException e) { throw ProviderException.malformed(); }
    }
}
