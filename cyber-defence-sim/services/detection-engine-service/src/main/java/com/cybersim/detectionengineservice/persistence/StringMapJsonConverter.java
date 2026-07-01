package com.cybersim.detectionengineservice.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Map;

@Converter
class StringMapJsonConverter implements AttributeConverter<Map<String, String>, String> {
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() { };
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Detection metadata cannot be serialized", exception);
        }
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String databaseValue) {
        try {
            return Map.copyOf(objectMapper.readValue(databaseValue, MAP_TYPE));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Stored detection metadata is invalid", exception);
        }
    }
}
