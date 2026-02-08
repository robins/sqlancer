package sqlancer.postgres;

import java.util.List;

import sqlancer.postgres.PostgresSchema.PostgresDataType;

public class PostgresFunctionSignature {

    private final String name;
    private final PostgresDataType returnType;
    private final List<PostgresDataType> argumentTypes;
    private final boolean isVariadic;
    private final boolean returnsSet;
    private final char volatility;

    public PostgresFunctionSignature(String name, PostgresDataType returnType, List<PostgresDataType> argumentTypes,
            boolean isVariadic, boolean returnsSet, char volatility) {
        this.name = name;
        this.returnType = returnType;
        this.argumentTypes = argumentTypes;
        this.isVariadic = isVariadic;
        this.returnsSet = returnsSet;
        this.volatility = volatility;
    }

    public String getName() {
        return name;
    }

    public PostgresDataType getReturnType() {
        return returnType;
    }

    public List<PostgresDataType> getArgumentTypes() {
        return argumentTypes;
    }

    public boolean isVariadic() {
        return isVariadic;
    }

    public char getVolatility() {
        return volatility;
    }
    
    public boolean returnsSet() {
        return returnsSet;
    }
    
    @Override
    public String toString() {
        return name + "(" + argumentTypes + ") -> " + returnType;
    }

}
