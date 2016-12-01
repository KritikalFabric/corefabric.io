package org.kritikal.fabric.metrics;

import com.datastax.driver.core.Row;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by ben on 21/02/15.
 */
public interface DbBuilder {

    void columnCqlDefinitions(StringBuilder sb, String prefix);

    void columnSqlDefinitions(StringBuilder sb, String prefix);

    void columnNames(StringBuilder sb, String prefix);

    boolean addToInsertCql(StringBuilder sb);

    boolean addToInsertSql(StringBuilder sb);

    int readFromCassandraResultSet(Row rs, int i);

    int readFromPostgresResultSet(ResultSet rs, int i) throws SQLException;

}
