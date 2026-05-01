package org.ngelmakproject.service.operation;

import java.time.Instant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public record Operation<T>(
        long id,
        OperationType type,
        T data) {
    public enum OperationType {
        CREATE, UPDATE, DELETE, DEFAULT
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static <T> Operation<T> deleteOperation(T data) {
        long uuid = Instant.now().getEpochSecond();
        return new Operation<>(uuid, OperationType.DELETE, data);
    }

    public static <T> Operation<T> defaultOperation(T data) {
        long uuid = Instant.now().getEpochSecond();
        return new Operation<>(uuid, OperationType.DEFAULT, data);
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize Operation", e);
        }
    }

    public static <T> Operation<T> fromJson(Object object) {
        try {
            // We need a TypeReference to preserve generic type
            return MAPPER.readValue((String) object, new TypeReference<Operation<T>>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize Operation", e);
        }
    }

    // public static <T> Operation<T> fromJson(String json) {
    //     try {
    //         // We need a TypeReference to preserve generic type
    //         return MAPPER.readValue(json, new TypeReference<Operation<T>>() {
    //         });
    //     } catch (Exception e) {
    //         throw new RuntimeException("Failed to deserialize Operation", e);
    //     }
    // }
}
