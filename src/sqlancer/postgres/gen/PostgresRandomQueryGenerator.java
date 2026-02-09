package sqlancer.postgres.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.postgres.PostgresGlobalState;

import sqlancer.postgres.PostgresSchema.PostgresTables;
import sqlancer.postgres.ast.PostgresConstant;
import sqlancer.postgres.ast.PostgresExpression;
import sqlancer.postgres.ast.PostgresOrderByTerm;
import sqlancer.postgres.ast.PostgresSelect;
import sqlancer.postgres.ast.PostgresSelect.ForClause;
import sqlancer.postgres.ast.PostgresSelect.PostgresFromTable;
import sqlancer.postgres.ast.PostgresSelect.SelectType;

public final class PostgresRandomQueryGenerator {

    private PostgresRandomQueryGenerator() {
    }

    public static PostgresSelect createRandomQuery(int nrColumns, PostgresGlobalState globalState) {
        List<PostgresExpression> columns = new ArrayList<>();
        PostgresTables tables = globalState.getSchema().getRandomTableNonEmptyTables();
        PostgresExpressionGenerator gen = new PostgresExpressionGenerator(globalState).setColumns(tables.getColumns());
        for (int i = 0; i < nrColumns; i++) {
            columns.add(gen.generateExpression(0));
        }
        PostgresSelect select = new PostgresSelect();
        select.setSelectType(SelectType.getRandom());
        PostgresExpression distinctOnExpr = null;
        if (select.getSelectOption() == SelectType.DISTINCT && Randomly.getBoolean()) {
            distinctOnExpr = gen.generateExpression(0);
            select.setDistinctOnClause(distinctOnExpr);
        }
        select.setFromList(tables.getTables().stream().map(t -> new PostgresFromTable(t, Randomly.getBoolean()))
                .collect(Collectors.toList()));
        select.setFetchColumns(columns);
        if (Randomly.getBoolean()) {
            select.setWhereClause(gen.generateWhereCondition());
        }
        if (Randomly.getBooleanWithRatherLowProbability()) {
            select.setGroupByExpressions(select.getFetchColumns());
            // PostgreSQL 14: GROUP BY DISTINCT
            if (Randomly.getBoolean()) {
                select.setGroupByDistinct(true);
            }
            if (Randomly.getBoolean()) {
                select.setHavingClause(gen.generateHavingCondition());
            }
        }
        if (Randomly.getBooleanWithRatherLowProbability() || distinctOnExpr != null) {
            List<PostgresExpression> orderBys = new ArrayList<>();
            // PostgreSQL requires DISTINCT ON expressions to appear first in ORDER BY
            if (distinctOnExpr != null) {
                orderBys.add(new PostgresOrderByTerm(distinctOnExpr, Randomly.getBoolean()));
            }
            orderBys.addAll(gen.generateOrderBys());
            select.setOrderByClauses(orderBys);
        }
        if (Randomly.getBoolean()) {
            select.setLimitClause(PostgresConstant.createIntConstant(Randomly.getPositiveOrZeroNonCachedInteger()));
            if (Randomly.getBoolean()) {
                select.setOffsetClause(
                        PostgresConstant.createIntConstant(Randomly.getPositiveOrZeroNonCachedInteger()));
            }
            if (Randomly.getBoolean() && !select.getOrderByClauses().isEmpty()) {
                select.setFetchFirst(true);
                if (Randomly.getBoolean()) {
                    select.setWithTies(true);
                }
            }
        }
        if (Randomly.getBooleanWithRatherLowProbability()) {
            select.setForClause(ForClause.getRandom());
        }
        return select;
    }

    public static PostgresSelect createRandomQueryForMaterializedView(int nrColumns, PostgresGlobalState globalState) {
        List<PostgresExpression> columns = new ArrayList<>();
        PostgresTables tables = globalState.getSchema().getRandomNonTemporaryTables();
        PostgresExpressionGenerator gen = new PostgresExpressionGenerator(globalState).setColumns(tables.getColumns());
        for (int i = 0; i < nrColumns; i++) {
            columns.add(gen.generateExpression(0));
        }
        PostgresSelect select = new PostgresSelect();
        select.setSelectType(SelectType.getRandom());
        PostgresExpression distinctOnExpr = null;
        if (select.getSelectOption() == SelectType.DISTINCT && Randomly.getBoolean()) {
            distinctOnExpr = gen.generateExpression(0);
            select.setDistinctOnClause(distinctOnExpr);
        }
        select.setFromList(tables.getTables().stream().map(t -> new PostgresFromTable(t, Randomly.getBoolean()))
                .collect(Collectors.toList()));
        select.setFetchColumns(columns);
        if (Randomly.getBoolean()) {
            select.setWhereClause(gen.generateWhereCondition());
        }
        if (Randomly.getBooleanWithRatherLowProbability()) {
            select.setGroupByExpressions(select.getFetchColumns());
            if (Randomly.getBoolean()) {
                select.setGroupByDistinct(true);
            }
            if (Randomly.getBoolean()) {
                select.setHavingClause(gen.generateHavingCondition());
            }
        }
        if (Randomly.getBooleanWithRatherLowProbability() || distinctOnExpr != null) {
            List<PostgresExpression> orderBys = new ArrayList<>();
            // PostgreSQL requires DISTINCT ON expressions to appear first in ORDER BY
            if (distinctOnExpr != null) {
                orderBys.add(new PostgresOrderByTerm(distinctOnExpr, Randomly.getBoolean()));
            }
            orderBys.addAll(gen.generateOrderBys());
            select.setOrderByClauses(orderBys);
        }
        if (Randomly.getBoolean()) {
            select.setLimitClause(PostgresConstant.createIntConstant(Randomly.getPositiveOrZeroNonCachedInteger()));
            if (Randomly.getBoolean()) {
                select.setOffsetClause(
                        PostgresConstant.createIntConstant(Randomly.getPositiveOrZeroNonCachedInteger()));
            }
            if (Randomly.getBoolean() && !select.getOrderByClauses().isEmpty()) {
                select.setFetchFirst(true);
                if (Randomly.getBoolean()) {
                    select.setWithTies(true);
                }
            }
        }
        if (Randomly.getBooleanWithRatherLowProbability()) {
            select.setForClause(ForClause.getRandom());
        }
        return select;
    }

}
