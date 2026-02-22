package sqlancer.postgres.gen;

import java.io.File;

import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresOptions;

public class PostgresTableSpaceGenerator {

    private final ExpectedErrors errors = new ExpectedErrors();
    private final PostgresGlobalState globalState;

    public PostgresTableSpaceGenerator(PostgresGlobalState globalState) {
        this.globalState = globalState;
        errors.addRegexString("ERROR: (?:tablespace )?directory \".*[\\\\/]tablespace\\d+\" does not exist");
        errors.add("already exists");
        errors.add("is not empty");
        errors.add("cannot be created because system does not support tablespaces");
    }

    public static SQLQueryAdapter generate(PostgresGlobalState globalState) {
        // Skip tablespace generation if the option is disabled
        PostgresOptions options = globalState.getDbmsSpecificOptions();
        if (!options.isTestTablespaces()) {
            return null;
        }
        return new PostgresTableSpaceGenerator(globalState).generateTableSpace();
    }

    private SQLQueryAdapter generateTableSpace() {
        StringBuilder sb = new StringBuilder();
        int tableSpaceNum = globalState.getRandomly().getInteger(1, Integer.MAX_VALUE);

        // Get the validated base path from options and append the tablespace number
        PostgresOptions options = globalState.getDbmsSpecificOptions();
        String path = options.getTablespacePath() + tableSpaceNum;

        // Create the directory before attempting to create the tablespace
        // PostgreSQL requires the directory to exist and be empty
        File tablespaceDir = new File(path);
        if (!tablespaceDir.exists()) {
            if (!tablespaceDir.mkdirs()) {
                // If we can't create the directory, skip tablespace generation
                // to avoid infinite retry loops
                return null;
            }
        }

        // CREATE TABLESPACE syntax
        sb.append("CREATE TABLESPACE ");
        sb.append("tablespace");
        sb.append(tableSpaceNum);
        sb.append(" LOCATION '");

        // Convert backslashes to forward slashes for PostgreSQL
        path = path.replace('\\', '/');

        // Escape single quotes in the path
        path = path.replace("'", "''");

        sb.append(path);
        sb.append("'");

        return new SQLQueryAdapter(sb.toString(), errors);
    }
}
