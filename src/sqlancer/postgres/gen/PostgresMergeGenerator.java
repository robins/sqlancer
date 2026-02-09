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
 * Generates PostgreSQL MERGE statements (PostgreSQL 15+ feature).
 *
 * MERGE INTO target_table
 * USING source_table ON join_condition
 * WHEN MATCHED THEN UPDATE SET ...
 * WHEN NOT MATCHED THEN INSERT (...) VALUES (...)
 * RETURNING ... (PostgreSQL 17+)
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

        // Create aliased column lists for expression generation
        List<PostgresColumn> targetColumnsAliased = aliasColumns(targetTable, "target");
        List<PostgresColumn> sourceColumnsAliased = aliasColumns(sourceTable, "source");
        List<PostgresColumn> combinedColumns = new java.util.ArrayList<>();
        combinedColumns.addAll(targetColumnsAliased);
        combinedColumns.addAll(sourceColumnsAliased);

        StringBuilder sb = new StringBuilder();
        sb.append("MERGE INTO ");
        sb.append(targetTable.getName());
        sb.append(" AS target");
        sb.append(" USING ");
        sb.append(sourceTable.getName());
        sb.append(" AS source");
        sb.append(" ON ");

        // Generate join condition - can use both source and target
        PostgresExpression joinCondition = PostgresExpressionGenerator.generateExpression(globalState,
                combinedColumns, PostgresDataType.BOOLEAN);
        sb.append(PostgresVisitor.asString(joinCondition));

        // WHEN MATCHED clause
        if (Randomly.getBoolean()) {
            sb.append(" WHEN MATCHED");
            if (Randomly.getBoolean()) {
                sb.append(" AND ");
                // MATCHED can use both source and target
                PostgresExpression matchedCondition = PostgresExpressionGenerator.generateExpression(globalState,
                        combinedColumns, PostgresDataType.BOOLEAN);
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
                    // UPDATE SET values can use both source and target
                    PostgresExpression value = PostgresExpressionGenerator.generateExpression(globalState,
                            combinedColumns, columns.get(i).getType());
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
                // NOT MATCHED can only use source columns
                PostgresExpression notMatchedCondition = PostgresExpressionGenerator.generateExpression(globalState,
                        sourceColumnsAliased, PostgresDataType.BOOLEAN);
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
                // INSERT values can only use source columns
                PostgresExpressionGenerator gen = new PostgresExpressionGenerator(globalState)
                        .setColumns(sourceColumnsAliased)
                        .allowSetReturningFunctions(false);
                PostgresExpression value = gen.generateExpression(insertColumns.get(i).getType());
                sb.append(PostgresVisitor.asString(value));
            }
            sb.append(")");
        }

        // If neither clause was generated, add at least one
        String result = sb.toString();
        if (!result.contains("WHEN MATCHED") && !result.contains("WHEN NOT MATCHED")) {
            sb.append(" WHEN MATCHED THEN DO NOTHING");
        }

        // PostgreSQL 17: Optional RETURNING clause
        // PostgreSQL 18: OLD/NEW table aliases for pre/post-modification values
        if (Randomly.getBoolean()) {
            sb.append(" RETURNING ");
            // Choose between OLD, NEW, or target alias for returned columns
            String tableAlias = Randomly.fromOptions("target.", "OLD.", "NEW.");
            List<PostgresColumn> returningColumns = targetTable.getRandomNonEmptyColumnSubset();
            sb.append(returningColumns.stream().map(c -> tableAlias + c.getName()).collect(Collectors.joining(", ")));
            // PostgreSQL 17: merge_action() function shows which action was performed
            if (Randomly.getBoolean()) {
                sb.append(", merge_action()");
            }
        }

        // Common MERGE errors (v15+)
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

    private static List<PostgresColumn> aliasColumns(PostgresTable table, String alias) {
        PostgresTable aliasedTable = new PostgresTable(alias, table.getColumns(), table.getIndexes(),
                table.getTableType(), table.getStatistics(), table.isView(), table.isInsertable());
        return table.getColumns().stream().map(c -> {
            PostgresColumn aliasedColumn = new PostgresColumn(c.getName(), c.getType());
            aliasedColumn.setTable(aliasedTable);
            return aliasedColumn;
        }).collect(Collectors.toList());
    }
}
