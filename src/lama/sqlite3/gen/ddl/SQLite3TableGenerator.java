package lama.sqlite3.gen.ddl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lama.Query;
import lama.QueryAdapter;
import lama.Randomly;
import lama.StateToReproduce;
import lama.sqlite3.SQLite3Errors;
import lama.sqlite3.gen.SQLite3ColumnBuilder;
import lama.sqlite3.gen.SQLite3Common;
import lama.sqlite3.schema.SQLite3Schema;
import lama.sqlite3.schema.SQLite3Schema.Column;
import lama.sqlite3.schema.SQLite3Schema.Table;
import lama.sqlite3.schema.SQLite3Schema.Table.TableKind;

/**
 * See https://www.sqlite.org/lang_createtable.html
 *
 * TODO What's missing:
 * <ul>
 * <li>CREATE TABLE ... AS SELECT Statements</li>
 * </ul>
 */
public class SQLite3TableGenerator {

	private final StringBuilder sb = new StringBuilder();
	private final String tableName;
	private int columnId;
	private boolean containsPrimaryKey;
	private boolean containsAutoIncrement;
	private final List<String> columnNames = new ArrayList<>();
	private final List<Column> columns = new ArrayList<>();
	private final SQLite3Schema existingSchema;
	private boolean tempTable;
	private Randomly r;

	public SQLite3TableGenerator(String tableName, SQLite3Schema existingSchema, Randomly r) {
		this.tableName = tableName;
		this.existingSchema = existingSchema;
		this.r = r;
	}

	public static Query createTableStatement(String tableName, StateToReproduce state, SQLite3Schema existingSchema, Randomly r) {
		SQLite3TableGenerator sqLite3TableGenerator = new SQLite3TableGenerator(tableName, existingSchema, r);
		sqLite3TableGenerator.start();
		List<String> errors = new ArrayList<>();
		errors.add("no such function");
		SQLite3Errors.addTableManipulationErrors(errors);
		errors.add("second argument to likelihood() must be a constant between 0.0 and 1.0");
		errors.add("non-deterministic functions prohibited in generated columns");
		return new QueryAdapter(sqLite3TableGenerator.sb.toString(), errors);
	}

	public void start() {
		sb.append("CREATE ");
		if (Randomly.getBoolean()) {
			tempTable = true;
			if (Randomly.getBoolean()) {
				sb.append("TEMP ");
			} else {
				sb.append("TEMPORARY ");
			}
		}
		sb.append("TABLE ");
		if (Randomly.getBoolean()) {
			sb.append("IF NOT EXISTS ");
		}
		sb.append(tableName + " ");
		sb.append("(");
		boolean allowPrimaryKeyInColumn = Randomly.getBoolean();
		int nrColumns = 3 + Randomly.smallNumber();
		for (int i = 0; i < nrColumns; i++) {
			columns.add(new Column(SQLite3Common.createColumnName(i), null, false, false, null));
		}
		for (int i = 0; i < nrColumns; i++) {
			if (i != 0) {
				sb.append(", ");
			}
			String columnName = SQLite3Common.createColumnName(columnId);
			SQLite3ColumnBuilder columnBuilder = new SQLite3ColumnBuilder()
					.allowPrimaryKey(allowPrimaryKeyInColumn && !containsPrimaryKey);
			sb.append(columnBuilder.createColumn(columnName, r, columns));
			sb.append(" ");
			if (columnBuilder.isContainsAutoIncrement()) {
				this.containsAutoIncrement = true;
			}
			if (columnBuilder.isContainsPrimaryKey()) {
				this.containsPrimaryKey = true;
			}

			columnNames.add(columnName);
			columnId++;
		}
		if (!containsPrimaryKey && Randomly.getBooleanWithSmallProbability()) {
			addColumnConstraints("PRIMARY KEY");
			containsPrimaryKey = true;
		}
		if (Randomly.getBooleanWithSmallProbability()) {
			for (int i = 0; i < Randomly.smallNumber(); i++) {
				addColumnConstraints("UNIQUE");
			}
		}

		if (Randomly.getBoolean()) {
			addForeignKey();
		}

		if (Randomly.getBoolean()) {
			sb.append(SQLite3Common.getCheckConstraint(r, columns));
		}

		sb.append(")");
		if (containsPrimaryKey && !containsAutoIncrement) {
			if (Randomly.getBoolean()) {
				// see https://sqlite.org/withoutrowid.html
				sb.append(" WITHOUT ROWID");
			}
		}
	}

	private void addColumnConstraints(String s) {
		sb.append(", " + s + " (");
		for (int i = 0; i < Randomly.smallNumber() + 1; i++) {
			if (i != 0) {
				sb.append(", ");
			}
			sb.append(Randomly.fromList(columnNames));
			if (Randomly.getBoolean()) {
				sb.append(Randomly.fromOptions(" ASC", " DESC"));
			}
		}
		sb.append(")");
	}

	/**
	 * @see https://www.sqlite.org/foreignkeys.html
	 */
	private void addForeignKey() {
		List<String> foreignKeyColumns = new ArrayList<>();
		if (Randomly.getBoolean()) {
			foreignKeyColumns = Arrays.asList(Randomly.fromList(columnNames));
		} else {
			foreignKeyColumns = new ArrayList<>();
			do {
				foreignKeyColumns.add(Randomly.fromList(columnNames));
			} while (Randomly.getBoolean());
		}
		sb.append(", FOREIGN KEY(");
		sb.append(foreignKeyColumns.stream().collect(Collectors.joining(", ")));
		sb.append(")");
		sb.append(" REFERENCES ");
		String referencedTableName;
		List<String> columns = new ArrayList<>();
		if (existingSchema.getDatabaseTables().isEmpty() || Randomly.getBooleanWithSmallProbability()) {
			// the foreign key references our own table
			referencedTableName = tableName;
			for (int i = 0; i < foreignKeyColumns.size(); i++) {
				columns.add(Randomly.fromList(columnNames));
			}
		} else {
			final TableKind type = tempTable ? TableKind.TEMP : TableKind.MAIN;
			List<Table> applicableTables = existingSchema.getTables().getTables().stream()
					.filter(t -> t.getTableType() == type).collect(Collectors.toList());
			if (applicableTables.isEmpty()) {
				referencedTableName = tableName;
				for (int i = 0; i < foreignKeyColumns.size(); i++) {
					columns.add(Randomly.fromList(columnNames));
				}
			} else {
				Table randomTable = Randomly.fromList(applicableTables);
				referencedTableName = randomTable.getName();
				for (int i = 0; i < foreignKeyColumns.size(); i++) {
					columns.add(randomTable.getRandomColumn().getName());
				}
			}
		}
		sb.append(referencedTableName);
		sb.append("(");
		sb.append(columns.stream().collect(Collectors.joining(", ")));
		sb.append(")");
		addActionClause(" ON DELETE ");
		addActionClause(" ON UPDATE ");
		if (Randomly.getBoolean()) {
			// add a deferrable clause
			sb.append(" ");
			String deferrable = Randomly.fromOptions("DEFERRABLE INITIALLY DEFERRED",
					"NOT DEFERRABLE INITIALLY DEFERRED", "NOT DEFERRABLE INITIALLY IMMEDIATE", "NOT DEFERRABLE",
					"DEFERRABLE INITIALLY IMMEDIATE", "DEFERRABLE");
			sb.append(deferrable);
		}
	}

	private void addActionClause(String string) {
		if (Randomly.getBoolean()) {
			// add an ON DELETE or ON ACTION clause
			sb.append(string);
			sb.append(Randomly.fromOptions("NO ACTION", "RESTRICT", "SET NULL", "SET DEFAULT", "CASCADE"));
		}
	}

}