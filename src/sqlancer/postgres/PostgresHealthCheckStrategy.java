package sqlancer.postgres;

import java.util.HashMap;
import java.util.Map;

import sqlancer.MainOptions;
import sqlancer.Randomly;

public class PostgresHealthCheckStrategy {

    private final MainOptions options;
    private final Map<String, Integer> enumCounters = new HashMap<>();

    public PostgresHealthCheckStrategy(MainOptions options) {
        this.options = options;
    }

    /**
     * Deterministically selects an option from the given enum values.
     * Uses a round-robin approach based on the enum class name.
     * 
     * @param <T> The enum type
     * @param values The array of enum values
     * @return The selected enum value
     */
    public <T extends Enum<T>> T selectOption(T[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Values array cannot be null or empty");
        }
        
        String key = values[0].getClass().getName();
        int counter = enumCounters.getOrDefault(key, 0);
        T selected = values[counter % values.length];
        
        // Increment counter for next time
        enumCounters.put(key, counter + 1);
        
        return selected;
    }

    public <T> T selectOption(java.util.List<T> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Values list cannot be null or empty");
        }
        // Use the class name of the first element as the key
        // This assumes the list contains homogenous types, which is typical for these usages
        String key = values.get(0).getClass().getName(); 
        // If it's not an Enum, maybe append specific context, but for now simple class name is enough
        // as we usually pick from Enums. for other objects, it might mix counters if we pick from 
        // different lists of same type strings. But typically we pick Enums.

        int counter = enumCounters.getOrDefault(key, 0);
        T selected = values.get(counter % values.size());
        
        enumCounters.put(key, counter + 1);
        
        return selected;
    }
    
    /**
     * Helper to select from random options if healthcheck is disabled, or deterministically if enabled.
     */
    public <T extends Enum<T>> T select(T[] values, Randomly randomly) {
        if (options.isHealthCheck()) {
            return selectOption(values);
        } else {
            return Randomly.fromOptions(values);
        }
    }
}
