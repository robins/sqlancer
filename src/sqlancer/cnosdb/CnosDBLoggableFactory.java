package sqlancer.cnosdb;

import java.io.PrintWriter;
import java.io.StringWriter;

import sqlancer.cnosdb.query.CnosDBOtherQuery;
import sqlancer.cnosdb.query.CnosDBQueryAdapter;
import sqlancer.common.log.Loggable;
import sqlancer.common.log.LoggableFactory;
import sqlancer.common.log.LoggedString;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.Query;

public class CnosDBLoggableFactory extends LoggableFactory {

    @Override
    protected Loggable createLoggable(String input, String suffix) {
        String completeString = input;
        if (!input.endsWith(";")) {
            completeString += ";";
        }
        if (suffix != null && !suffix.isEmpty()) {
            completeString += suffix;
        }
        return new LoggedString(completeString);
    }

    @Override
    public CnosDBQueryAdapter getQueryForStateToReproduce(String queryString) {
        return new CnosDBOtherQuery(queryString, CnosDBExpectedError.expectedErrors());
    }

    @Override
    public CnosDBQueryAdapter commentOutQuery(Query<?> query) {
        String queryString = query.getLogString();
        String newQueryString = "-- " + queryString;
        ExpectedErrors errors = new ExpectedErrors();
        return new CnosDBOtherQuery(newQueryString, errors);
    }

    @Override
    protected Loggable infoToLoggable(String time, String databaseName, String databaseVersion, long seedValue, String oracleName) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Time: ").append(time).append("\n");
        sb.append("-- Database: ").append(databaseName).append("\n");
        sb.append("-- Database version: ").append(databaseVersion).append("\n");
        sb.append("-- seed value: ").append(seedValue).append("\n");
        if (oracleName != null && !oracleName.isEmpty()) {
            sb.append("-- Oracle: ").append(oracleName).append("\n");
        }
        return new LoggedString(sb.toString());
    }

    @Override
    public Loggable convertStacktraceToLoggable(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return new LoggedString("--" + sw.toString().replace("\n", "\n--"));
    }
}
