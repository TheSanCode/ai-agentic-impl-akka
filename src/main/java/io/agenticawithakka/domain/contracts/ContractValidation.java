package io.agenticawithakka.domain.contracts;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Shared, framework-free validation used by contract constructors. */
public final class ContractValidation {
    public static final Pattern REFERENCE = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    public static final Pattern CAMEL_NAME = Pattern.compile("[a-z][A-Za-z0-9]{0,63}");

    private ContractValidation() {
    }

    public static <T> T required(T value, String field) {
        if (value == null) {
            throw new ContractViolationException(field, "is required");
        }
        return value;
    }

    /** Free text: non-blank, bounded, and no control characters other than tab/CR/LF. */
    public static String text(String value, String field, int maxLength) {
        required(value, field);
        if (value.isBlank()) {
            throw new ContractViolationException(field, "must not be blank");
        }
        if (value.length() > maxLength) {
            throw new ContractViolationException(field, "exceeds " + maxLength + " characters");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                throw new ContractViolationException(field, "contains control characters");
            }
        }
        return value;
    }

    public static String matches(String value, String field, Pattern pattern) {
        required(value, field);
        if (!pattern.matcher(value).matches()) {
            throw new ContractViolationException(field, "has an invalid format");
        }
        return value;
    }

    public static int positive(int value, String field) {
        if (value <= 0) {
            throw new ContractViolationException(field, "must be positive");
        }
        return value;
    }

    public static <T> List<T> list(Collection<T> values, String field, int maxSize) {
        checkElements(values, field, maxSize);
        return List.copyOf(values);
    }

    public static <T> Set<T> set(Collection<T> values, String field, int maxSize) {
        checkElements(values, field, maxSize);
        return Set.copyOf(values);
    }

    public static <K, V> Map<K, V> map(Map<K, V> values, String field, int maxSize) {
        required(values, field);
        if (values.size() > maxSize) {
            throw new ContractViolationException(field, "exceeds " + maxSize + " entries");
        }
        for (var entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new ContractViolationException(field, "must not contain null keys or values");
            }
        }
        return Map.copyOf(new LinkedHashMap<>(values));
    }

    private static void checkElements(Collection<?> values, String field, int maxSize) {
        required(values, field);
        if (values.size() > maxSize) {
            throw new ContractViolationException(field, "exceeds " + maxSize + " entries");
        }
        for (Object value : values) {
            if (value == null) {
                throw new ContractViolationException(field, "must not contain null entries");
            }
        }
    }
}
