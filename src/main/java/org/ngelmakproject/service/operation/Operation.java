package org.ngelmakproject.service.operation;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Represents an operation to be performed on an entity.
 * Supports both long and String identifiers with automatic uniqueness.
 *
 * @param <T> the type of data being operated on
 */
public class Operation<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public enum OperationType {
        CREATE, UPDATE, DELETE, DEFAULT
    }

    @JsonProperty
    private final String id;
    @JsonProperty
    private final OperationType type;
    @JsonProperty
    private final T data;

    /**
     * Creates an operation with a String ID.
     */
    @JsonCreator
    public Operation(
            @JsonProperty("id") String id,
            @JsonProperty("type") OperationType type,
            @JsonProperty("data") T data) {
        this.id = id;
        this.type = type;
        this.data = data;
    }

    /**
     * Creates an operation with an auto-generated unique ID (long-based with
     * counter).
     */
    public Operation(OperationType type, T data) {
        this(Instant.now().toEpochMilli(), type, data);
    }

    /**
     * Creates an operation with a long ID.
     */
    public Operation(long id, OperationType type, T data) {
        this.id = String.valueOf(id);
        this.type = type;
        this.data = data;
    }

    /**
     * Creates an operation with a UUID string ID.
     */
    public static <T> Operation<T> withUuid(OperationType type, T data) {
        return new Operation<>(UUID.randomUUID().toString(), type, data);
    }

    /**
     * Creates a CREATE operation with auto-generated unique ID.
     */
    public static <T> Operation<T> createOperation(T data) {
        return new Operation<>(OperationType.CREATE, data);
    }

    /**
     * Creates a CREATE operation with specific long ID.
     */
    public static <T> Operation<T> createOperation(long id, T data) {
        return new Operation<>(id, OperationType.CREATE, data);
    }

    /**
     * Creates a CREATE operation with specific String ID.
     */
    public static <T> Operation<T> createOperation(String id, T data) {
        return new Operation<>(id, OperationType.CREATE, data);
    }

    /**
     * Creates an UPDATE operation with auto-generated unique ID.
     */
    public static <T> Operation<T> updateOperation(T data) {
        return new Operation<>(OperationType.UPDATE, data);
    }

    /**
     * Creates an UPDATE operation with specific long ID.
     */
    public static <T> Operation<T> updateOperation(long id, T data) {
        return new Operation<>(id, OperationType.UPDATE, data);
    }

    /**
     * Creates an UPDATE operation with specific String ID.
     */
    public static <T> Operation<T> updateOperation(String id, T data) {
        return new Operation<>(id, OperationType.UPDATE, data);
    }

    /**
     * Creates a DELETE operation with auto-generated unique ID.
     */
    public static <T> Operation<T> deleteOperation(T data) {
        return new Operation<>(OperationType.DELETE, data);
    }

    /**
     * Creates a DELETE operation with specific long ID.
     */
    public static <T> Operation<T> deleteOperation(long id, T data) {
        return new Operation<>(id, OperationType.DELETE, data);
    }

    /**
     * Creates a DELETE operation with specific String ID.
     */
    public static <T> Operation<T> deleteOperation(String id, T data) {
        return new Operation<>(id, OperationType.DELETE, data);
    }

    /**
     * Creates a DEFAULT operation with auto-generated unique ID.
     */
    public static <T> Operation<T> defaultOperation(T data) {
        return new Operation<>(OperationType.DEFAULT, data);
    }

    /**
     * Creates a DEFAULT operation with specific long ID.
     */
    public static <T> Operation<T> defaultOperation(long id, T data) {
        return new Operation<>(id, OperationType.DEFAULT, data);
    }

    /**
     * Creates a DEFAULT operation with specific String ID.
     */
    public static <T> Operation<T> defaultOperation(String id, T data) {
        return new Operation<>(id, OperationType.DEFAULT, data);
    }

    // Getters

    /**
     * Returns the operation ID as a String.
     */
    public String id() {
        return id;
    }

    /**
     * Returns the operation ID as a long.
     * Throws NumberFormatException if ID is not numeric.
     */
    public long idAsLong() {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Operation ID '" + id + "' cannot be converted to long");
        }
    }

    /**
     * Returns the operation ID as a String (same as id()).
     */
    public String idAsString() {
        return id;
    }

    public OperationType type() {
        return type;
    }

    public T data() {
        return data;
    }

    /**
     * Serializes the operation to JSON.
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize Operation", e);
        }
    }

    /**
     * Deserializes an operation from JSON.
     */
    public static <T> Operation<T> fromJson(Object json, Class<T> dataClass) {
        try {
            JavaType type = MAPPER.getTypeFactory()
                    .constructParametricType(Operation.class, dataClass);

            return MAPPER.readValue((String) json, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize Operation", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Operation<?> operation = (Operation<?>) o;
        return Objects.equals(id, operation.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Operation{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", data=" + data +
                '}';
    }
}
