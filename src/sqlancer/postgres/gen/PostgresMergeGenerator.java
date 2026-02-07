package sqlancer.postgres.gen;

import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresDataType;
import sqlancer.postgres.PostgresSchema.PostgresTable;
import sqlancer.postgres.PostgresVisitor;
import sqlancer.postgres.ast.PostgresExpression;

/**
 * Generates PostgreSQL MERGE statements (PostgreSQL 15 feature).
 *
 * MERGE INTO target_table
 * USING source_table ON join_condition
 * WHEN MATCHED THEN UPDATE SET ...
 * WHEN NOT MATCHED THEN INSERT (...) VALUES (...)
 */
public final class PostgresMergeGenerator {

    private PostgresMergeGenerator() {
    }

    public static SQLQueryAdapter create(PostgresGlobalState globalState) {
        ExpectedErrors errors = new ExpectedErrors();
        PostgresCommon.addCommonExpressionErrors(errors);
        PostgresCommon.addCommonInsertUpdateErrors(errors);

        // Get two distinct tables for merge
        List<PostgresTable> tables = globalState.getSchema().getDatabaseTables().stream()
                .filter(t -> t.isInsertable() && !t.isView()).collect(Collectors.toList());

        if (tables.size() < 2) {
            // Need at least 2 tables for MERGE
            return new SQLQueryAdapter("SELECT 1", new ExpectedErrors());
        }

        PostgresTable targetTable = Randomly.fromList(tables);
        PostgresTable sourceTable = Randomly.fromList(tables.stream()
                .filter(t -> !t.getName().equals(targetTable.getName()))
                .collect(Collectors.toList()));

        if (sourceTable == null) {
            sourceTable = targetTable; // fallback - use same table as source
        }

        StringBuilder sb = new StringBuilder();
        sb.append("MERGE INTO ");
        sb.append(targetTable.getName());
        sb.append(" AS target");
        sb.append(" USING ");
        sb.append(sourceTable.getName());
        sb.append(" AS source");
        sb.append(" ON ");

        // Generate join condition
        PostgresExpression joinCondition = PostgresExpressionGenerator.generateExpression(globalState,
                targetTable.getColumns(), PostgresDataType.BOOLEAN);
        sb.append(PostgresVisitor.asString(joinCondition));

        // WHEN MATCHED clause
        if (Randomly.getBoolean()) {
            sb.append(" WHEN MATCHED");
            if (Randomly.getBoolean()) {
                sb.append(" AND ");
                PostgresExpression matchedCondition = PostgresExpressionGenerator.generateExpression(globalState,
                        targetTable.getColumns(), PostgresDataType.BOOLEAN);
                sb.append(PostgresVisitor.asString(matchedCondition));
            }
            sb.append(" THEN ");
            if (Randomly.getBoolean()) {
                // UPDATE
                sb.append("UPDATE SET ");
                List<PostgresColumn> columns = targetTable.getRandomNonEmptyColumnSubset();
                for (int i = 0; i < columns.size(); i++) {
                    if (i != 0) {
                        sb.append(", ");
                    }
                    sb.append(columns.get(i).getName());
                    sb.append(" = ");
                    PostgresExpression value = PostgresExpressionGenerator.generateConstant(
                            globalState.getRandomly(), columns.get(i).getType());
                    sb.append(PostgresVisitor.asString(value));
                }
            } else {
                // DELETE
                sb.append("DELETE");
            }
        }

        // WHEN NOT MATCHED clause
        if (Randomly.getBoolean()) {
            sb.append(" WHEN NOT MATCHED");
            if (Randomly.getBoolean()) {
                sb.append(" AND ");
                PostgresExpression notMatchedCondition = PostgresExpressionGenerator.generateExpression(globalState,
                        sourceTable.getColumns(), PostgresDataType.BOOLEAN);
                sb.append(PostgresVisitor.asString(notMatchedCondition));
            }
            sb.append(" THEN INSERT ");
            List<PostgresColumn> insertColumns = targetTable.getRandomNonEmptyColumnSubset();
            sb.append("(");
            sb.append(insertColumns.stream().map(c -> c.getName()).collect(Collectors.joining(", ")));
            sb.append(") VALUES (");
            for (int i = 0; i < insertColumns.size(); i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                PostgresExpression value = PostgresExpressionGenerator.generateConstant(
                        globalState.getRandomly(), insertColumns.get(i).getType());
                sb.append(PostgresVisitor.asString(value));
            }
            sb.append(")");
        }

        // If neither clause was generated, add at least one
        String result = sb.toString();
        if (!result.contains("WHEN MATCHED") && !result.contains("WHEN NOT MATCHED")) {
            sb.append(" WHEN MATCHED THEN DO NOTHING");
        }

        // Common MERGE errors
        errors.add("MERGE command cannot affect row a second time");
        errors.add("cannot affect row a second time");
        errors.add("violates foreign key constraint");
        errors.add("violates not-null constraint");
        errors.add("violates unique constraint");
        errors.add("violates check constraint");
        errors.add("duplicate key value");
        errors.add("out of range");
        errors.add("cannot cast");
        errors.add("division by zero");
        errors.add("invalid input syntax");
        errors.add("could not determine which collation to use");

        return new SQLQueryAdapter(sb.toString(), errors, true);
    }
}
