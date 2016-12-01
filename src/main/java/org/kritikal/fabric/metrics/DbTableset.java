package org.kritikal.fabric.metrics;

import org.kritikal.fabric.CoreFabric;
import org.kritikal.fabric.core.exceptions.FabricError;
import org.kritikal.fabric.db.pgsql.ConnectionInformation;
import com.datastax.driver.core.*;
import org.kritikal.fabric.db.pgsql.PgDbHelper;
import io.vertx.core.logging.Logger;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by ben on 16/06/15.
 */
public class DbTableset<DATA extends DrilldownData, ITEM extends DbBuilder> {
    public DbTableset(Drilldown<DATA, ITEM> drilldown) {
        this.drilldown = drilldown;
    }

    private final Drilldown<DATA, ITEM> drilldown;

    public String createDdl(String keyspace, String tableName, ITEM item) {
        StringBuilder sb = new StringBuilder();

        for (ArrayList<Dimension> axis : drilldown.coordinateCombinations) {

            if (axis.size() == 0)
                createDdl(sb, keyspace, tableName, item);
            else if (axis.size() == 1)
                createDdl(sb, keyspace, tableName, item, axis.get(0));
            else if (axis.size() == 2)
                createDdl(sb, keyspace, tableName, item, axis.get(0), axis.get(1));
            else if (axis.size() == 3)
                createDdl(sb, keyspace, tableName, item, axis.get(0), axis.get(1), axis.get(2));
            else throw new FabricError();
        }
        return sb.toString();
    }

    private void createDdl(StringBuilder sb, String keyspace, String tableName, DbBuilder item) {
        if (MetricsConfiguration.USE_CASSANDRA) {
            sb.append("CREATE TABLE IF NOT EXISTS ").append(keyspace).append(".").append(tableName).append(" (dt int, dt_start timestamp, dt_end timestamp");
            item.columnCqlDefinitions(sb, "item");
            sb.append(",PRIMARY KEY (dt,dt_start)");
            sb.append(");");
        }
        else {
            sb.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (dt_start timestamptz, dt_end timestamptz");
            item.columnSqlDefinitions(sb, "item");
            sb.append(",PRIMARY KEY (dt_start)");
            sb.append(");\n");
        }
    }

    private void createDdl(StringBuilder sb, String keyspace, String tableName, DbBuilder item, Dimension dimension1) {
        tableName = tableName + "__" + dimension1.getName();
        if (MetricsConfiguration.USE_CASSANDRA) {
            sb.append("CREATE TABLE IF NOT EXISTS ").append(keyspace).append(".").append(tableName).append(" (dt int, dt_start timestamp, dt_end timestamp");
            item.columnCqlDefinitions(sb, "item");
            dimension1.columnDefinitions(sb, dimension1.getName());
            sb.append(",PRIMARY KEY ((dt");
            dimension1.columnNames(sb, dimension1.getName());
            sb.append("),dt_start)");
            sb.append(");");
            dimension1.indexDefinitions(sb, tableName, dimension1.getName(), "i1");
        }
        else {
            sb.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (dt_start timestamptz, dt_end timestamptz");
            item.columnSqlDefinitions(sb, "item");
            dimension1.columnDefinitions(sb, dimension1.getName());
            sb.append(",PRIMARY KEY (dt_start");
            dimension1.columnNames(sb, dimension1.getName());
            sb.append(")");
            sb.append(");\n");
            dimension1.indexDefinitions(sb, tableName, dimension1.getName(), "i1");
        }
    }

    private void createDdl(StringBuilder sb, String keyspace, String tableName, DbBuilder item, Dimension dimension1, Dimension dimension2) {
        tableName = tableName + "__" + dimension1.getName() + "__" + dimension2.getName();
        if (MetricsConfiguration.USE_CASSANDRA) {
            sb.append("CREATE TABLE IF NOT EXISTS ").append(keyspace).append(".").append(tableName).append(" (dt int, dt_start timestamp, dt_end timestamp");
            item.columnCqlDefinitions(sb, "item");
            dimension1.columnDefinitions(sb, dimension1.getName());
            dimension2.columnDefinitions(sb, dimension2.getName());
            sb.append(",PRIMARY KEY ((dt");
            dimension1.columnNames(sb, dimension1.getName());
            dimension2.columnNames(sb, dimension2.getName());
            sb.append("),dt_start)");
            sb.append(");");
            dimension1.indexDefinitions(sb, tableName, dimension1.getName(), "i1");
            dimension2.indexDefinitions(sb, tableName, dimension2.getName(), "i2");
        }
        else {
            sb.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (dt_start timestamptz, dt_end timestamptz");
            item.columnSqlDefinitions(sb, "item");
            dimension1.columnDefinitions(sb, dimension1.getName());
            dimension2.columnDefinitions(sb, dimension2.getName());
            sb.append(",PRIMARY KEY (dt_start");
            dimension1.columnNames(sb, dimension1.getName());
            dimension2.columnNames(sb, dimension2.getName());
            sb.append(")");
            sb.append(");\n");
            dimension1.indexDefinitions(sb, tableName, dimension1.getName(), "i1");
            dimension2.indexDefinitions(sb, tableName, dimension2.getName(), "i2");
        }
    }

    private void createDdl(StringBuilder sb, String keyspace, String tableName, DbBuilder item, Dimension dimension1, Dimension dimension2, Dimension dimension3) {
        tableName = tableName + "__" + dimension1.getName() + "__" + dimension2.getName() + "__" + dimension3.getName();
        if (MetricsConfiguration.USE_CASSANDRA) {
            sb.append("CREATE TABLE IF NOT EXISTS ").append(keyspace).append(".").append(tableName).append(" (dt int, dt_start timestamp, dt_end timestamp");
            item.columnCqlDefinitions(sb, "item");
            dimension1.columnDefinitions(sb, dimension1.getName());
            dimension2.columnDefinitions(sb, dimension2.getName());
            dimension3.columnDefinitions(sb, dimension3.getName());
            sb.append(",PRIMARY KEY ((dt");
            dimension1.columnNames(sb, dimension1.getName());
            dimension2.columnNames(sb, dimension2.getName());
            dimension3.columnNames(sb, dimension3.getName());
            sb.append("),dt_start)");
            sb.append(");");
            dimension1.indexDefinitions(sb, tableName, dimension1.getName(), "i1");
            dimension2.indexDefinitions(sb, tableName, dimension2.getName(), "i2");
            dimension3.indexDefinitions(sb, tableName, dimension3.getName(), "i3");
        }
        else {
            sb.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (dt_start timestamptz, dt_end timestamptz");
            item.columnSqlDefinitions(sb, "item");
            dimension1.columnDefinitions(sb, dimension1.getName());
            dimension2.columnDefinitions(sb, dimension2.getName());
            dimension3.columnDefinitions(sb, dimension3.getName());
            sb.append(",PRIMARY KEY (dt_start");
            dimension1.columnNames(sb, dimension1.getName());
            dimension2.columnNames(sb, dimension2.getName());
            dimension3.columnNames(sb, dimension3.getName());
            sb.append(")");
            sb.append(");\n");
            dimension1.indexDefinitions(sb, tableName, dimension1.getName(), "i1");
            dimension2.indexDefinitions(sb, tableName, dimension2.getName(), "i2");
            dimension3.indexDefinitions(sb, tableName, dimension3.getName(), "i3");
        }
    }

    public void insert(Logger logger, ConnectionInformation ci, ArrayList<ResultSetFuture> futures, String keyspace, String tableName, Timestamp start, Timestamp end) {
        if (MetricsConfiguration.USE_CASSANDRA) {

            // TODO: drop existing data

            List<com.datastax.driver.core.Statement> statements = new ArrayList<>();

            for (ArrayList<Dimension> axis : drilldown.coordinateCombinations) {
                if (axis.size() == 0) {
                    insertCql(logger, ci, statements, keyspace, tableName, start, end);
                } else if (axis.size() == 1) {
                    insertCql(logger, ci, statements, keyspace, tableName, start, end, axis.get(0));
                } else if (axis.size() == 2) {
                    insertCql(logger, ci, statements, keyspace, tableName, start, end, axis.get(0), axis.get(1));
                } else if (axis.size() == 3) {
                    insertCql(logger, ci, statements, keyspace, tableName, start, end, axis.get(0), axis.get(1), axis.get(2));
                } else throw new FabricError();
            }

            for (com.datastax.driver.core.Statement statement : statements) {
                futures.add(ci.getSession().executeAsync(statement));
            }
        }
        else {
            java.sql.Statement stmt = null;
            try {
                stmt = ci.conWrite.createStatement();

                for (ArrayList<Dimension> axis : drilldown.coordinateCombinations) {
                    if (axis.size() == 0) {
                        StringBuilder sb = new StringBuilder();
                        insertSql(sb, tableName, start, end);
                        String sql = sb.toString();
                        if (!"".equals(sql)) {
                            if (CoreFabric.ServerConfiguration.DEBUG) logger.debug(sql);
                            stmt.execute(sql);
                        }
                    } else if (axis.size() == 1) {
                        StringBuilder sb = new StringBuilder();
                        insertSql(sb, tableName, start, end, axis.get(0));
                        String sql = sb.toString();
                        if (!"".equals(sql)) {
                            if (CoreFabric.ServerConfiguration.DEBUG) logger.debug(sql);
                            stmt.execute(sql);
                        }
                    } else if (axis.size() == 2) {
                        StringBuilder sb = new StringBuilder();
                        insertSql(sb, tableName, start, end, axis.get(0), axis.get(1));
                        String sql = sb.toString();
                        if (!"".equals(sql)) {
                            if (CoreFabric.ServerConfiguration.DEBUG) logger.debug(sql);
                            stmt.execute(sql);
                        }
                    } else if (axis.size() == 3) {
                        StringBuilder sb = new StringBuilder();
                        insertSql(sb, tableName, start, end, axis.get(0), axis.get(1), axis.get(2));
                        String sql = sb.toString();
                        if (!"".equals(sql)) {
                            if (CoreFabric.ServerConfiguration.DEBUG) logger.debug(sql);
                            stmt.execute(sql);
                        }
                    } else throw new FabricError();
                }
                ci.conWrite.commit();
            }
            catch (SQLException e) {
                logger.fatal(e);
                try {
                    ci.conWrite.rollback();
                }
                catch (SQLException e2) {
                    logger.fatal(e2);
                }
            }
            finally {
                try {
                    if (stmt != null) {
                        stmt.close();
                    }
                }
                catch (SQLException e) {
                    logger.fatal(e);
                }
            }
        }
    }

    private void insertCql(Logger logger, ConnectionInformation ci, List<com.datastax.driver.core.Statement> batchStatement, String keyspace, String tableName, Timestamp start, Timestamp end) {
        ITEM item = drilldown.reportItems.get(new CoordinatesPath());
        if (item != null) {
            boolean use = false;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("INSERT INTO ").append(keyspace).append(".").append(tableName).append(" (dt, dt_start, dt_end");
            ((DbBuilder)item).columnNames(stringBuilder, "item");
            stringBuilder.append(") VALUES (").append(toYMD(start)).append(", ").append(PgDbHelper.quote_timestamp(start)).append(", ").append(PgDbHelper.quote_timestamp(end));
            use = ((DbBuilder)item).addToInsertCql(stringBuilder);
            stringBuilder.append(");");
            if (use) {
                String cql = stringBuilder.toString();
                try {
                    com.datastax.driver.core.Statement stmt = new SimpleStatement(cql);
                    stmt.setIdempotent(true);
                    batchStatement.add(stmt);
                }
                catch (Throwable t) {
                    logger.warn(cql, t);
                }
            }
        }
    }

    private void insertCql(Logger logger, ConnectionInformation ci, List<com.datastax.driver.core.Statement> batchStatement, String keyspace, String tableName, Timestamp start, Timestamp end, Dimension dimension1) {
        tableName = tableName + "__" + dimension1.getName();
        String dimensionPath = "/" + dimension1.getName();
        for (Map.Entry<CoordinatesPath, ITEM> me : drilldown.reportItems.entrySet()) {
            CoordinatesPath coordinatesPath = me.getKey();
            if (dimensionPath.equals(coordinatesPath.dimensionPath)) {
                ITEM item = me.getValue();
                if (item != null) {
                    boolean use = false;
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("INSERT INTO ").append(keyspace).append(".").append(tableName).append(" (dt, dt_start, dt_end");
                    ((DbBuilder) item).columnNames(stringBuilder, "item");
                    dimension1.columnNames(stringBuilder, dimension1.getName());
                    stringBuilder.append(") VALUES (").append(toYMD(start)).append(", ").append(PgDbHelper.quote_timestamp(start)).append(", ").append(PgDbHelper.quote_timestamp(end));
                    use = ((DbBuilder) item).addToInsertCql(stringBuilder);
                    if (use) {
                        coordinatesPath.coordinates1.dimensionValue.addToInsertCql(stringBuilder);
                    }
                    stringBuilder.append(");");
                    if (use) {
                        String cql = stringBuilder.toString();
                        try {
                            com.datastax.driver.core.Statement stmt = new SimpleStatement(cql);
                            stmt.setIdempotent(true);
                            batchStatement.add(stmt);
                        } catch (Throwable t) {
                            logger.warn(cql, t);
                        }
                    }
                }
            }
        }
    }

    private void insertCql(Logger logger, ConnectionInformation ci, List<com.datastax.driver.core.Statement> batchStatement, String keyspace, String tableName, Timestamp start, Timestamp end, Dimension dimension1, Dimension dimension2) {
        tableName = tableName + "__" + dimension1.getName() + "__" + dimension2.getName();
        String dimensionPath = "/" + dimension1.getName() + "/" + dimension2.getName();
        for (Map.Entry<CoordinatesPath, ITEM> me : drilldown.reportItems.entrySet()) {
            CoordinatesPath coordinatesPath = me.getKey();
            if (dimensionPath.equals(coordinatesPath.dimensionPath)) {
                ITEM item = me.getValue();
                if (item != null) {
                    boolean use = false;
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("INSERT INTO ").append(keyspace).append(".").append(tableName).append(" (dt, dt_start, dt_end");
                    ((DbBuilder) item).columnNames(stringBuilder, "item");
                    dimension1.columnNames(stringBuilder, dimension1.getName());
                    dimension2.columnNames(stringBuilder, dimension2.getName());
                    stringBuilder.append(") VALUES (").append(toYMD(start)).append(", ").append(PgDbHelper.quote_timestamp(start)).append(", ").append(PgDbHelper.quote_timestamp(end));
                    use = ((DbBuilder) item).addToInsertCql(stringBuilder);
                    if (use) {
                        coordinatesPath.coordinates1.dimensionValue.addToInsertCql(stringBuilder);
                        coordinatesPath.coordinates2.dimensionValue.addToInsertCql(stringBuilder);
                    }
                    stringBuilder.append(");");
                    if (use) {
                        String cql = stringBuilder.toString();
                        try {
                            com.datastax.driver.core.Statement stmt = new SimpleStatement(cql);
                            stmt.setIdempotent(true);
                            batchStatement.add(stmt);
                        } catch (Throwable t) {
                            logger.warn(cql, t);
                        }
                    }
                }
            }
        }
    }

    private void insertCql(Logger logger, ConnectionInformation ci, List<com.datastax.driver.core.Statement> batchStatement, String keyspace, String tableName, Timestamp start, Timestamp end, Dimension dimension1, Dimension dimension2, Dimension dimension3) {
        tableName = tableName + "__" + dimension1.getName() + "__" + dimension2.getName() + "__" + dimension3.getName();
        String dimensionPath = "/" + dimension1.getName() + "/" + dimension2.getName() + "/" + dimension3.getName();
        for (Map.Entry<CoordinatesPath, ITEM> me : drilldown.reportItems.entrySet()) {
            CoordinatesPath coordinatesPath = me.getKey();
            if (dimensionPath.equals(coordinatesPath.dimensionPath)) {
                ITEM item = me.getValue();
                if (item != null) {
                    boolean use = false;
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("INSERT INTO ").append(keyspace).append(".").append(tableName).append(" (dt, dt_start, dt_end");
                    ((DbBuilder) item).columnNames(stringBuilder, "item");
                    dimension1.columnNames(stringBuilder, dimension1.getName());
                    dimension2.columnNames(stringBuilder, dimension2.getName());
                    dimension3.columnNames(stringBuilder, dimension3.getName());
                    stringBuilder.append(") VALUES (").append(toYMD(start)).append(", ").append(PgDbHelper.quote_timestamp(start)).append(", ").append(PgDbHelper.quote_timestamp(end));
                    use = ((DbBuilder) item).addToInsertCql(stringBuilder);
                    if (use) {
                        coordinatesPath.coordinates1.dimensionValue.addToInsertCql(stringBuilder);
                        coordinatesPath.coordinates2.dimensionValue.addToInsertCql(stringBuilder);
                        coordinatesPath.coordinates3.dimensionValue.addToInsertCql(stringBuilder);
                    }
                    stringBuilder.append(");");
                    if (use) {
                        String cql = stringBuilder.toString();
                        try {
                            com.datastax.driver.core.Statement stmt = new SimpleStatement(cql);
                            stmt.setIdempotent(true);
                            batchStatement.add(stmt);
                        } catch (Throwable t) {
                            logger.warn(cql, t);
                        }
                    }
                }
            }
        }
    }

    private void insertSql(StringBuilder sb, String tableName, Timestamp start, Timestamp end) {
        ITEM item = drilldown.reportItems.get(new CoordinatesPath());
        if (item != null) {
            sb.append("DELETE FROM ").append(tableName);
            sb.append(" WHERE ").append(PgDbHelper.quote_timestamp(start)).append(" <= dt_start AND dt_start < ").append(PgDbHelper.quote_timestamp(end));
            sb.append(";\n");

            boolean use = false;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("INSERT INTO ").append(tableName).append(" (dt_start, dt_end");
            ((DbBuilder)item).columnNames(stringBuilder, "item");
            stringBuilder.append(") VALUES (").append(PgDbHelper.quote_timestamp(start)).append(", ").append(PgDbHelper.quote_timestamp(end));
            use = ((DbBuilder)item).addToInsertSql(stringBuilder);
            stringBuilder.append(");\n");

            if (use) {
                sb.append(stringBuilder.toString());
            }
        }
    }

    private void insertSql(StringBuilder sb, String tableName, Timestamp start, Timestamp end, Dimension dimension1) {
        tableName = tableName + "__" + dimension1.getName();
        boolean cleared = false;
        for (Map.Entry<CoordinatesPath, ITEM> me : drilldown.reportItems.entrySet()) {
            CoordinatesPath coordinatesPath = me.getKey();
            if (coordinatesPath.coordinates1 != null && coordinatesPath.coordinates2 == null) {
                if (coordinatesPath.coordinates1.dimension.equals(dimension1)) {
                    ITEM item = me.getValue();
                    if (item != null) {
                        if (!cleared) {
                            cleared = true;
                            sb.append("DELETE FROM ").append(tableName);
                            sb.append(" WHERE ").append(PgDbHelper.quote_timestamp(start)).append(" <= dt_start AND dt_start < ").append(PgDbHelper.quote_timestamp(end));
                            sb.append(";\n");
                        }
                        boolean use = false;
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append("INSERT INTO ").append(tableName).append(" (dt_start, dt_end");
                        ((DbBuilder)item).columnNames(stringBuilder, "item");
                        dimension1.columnNames(stringBuilder, dimension1.getName());
                        stringBuilder.append(") VALUES (").append(PgDbHelper.quote_timestamp(start)).append(", ").append(PgDbHelper.quote_timestamp(end));
                        use = ((DbBuilder)item).addToInsertSql(stringBuilder);
                        if (use) {
                            coordinatesPath.coordinates1.dimensionValue.addToInsertSql(stringBuilder);
                        }
                        stringBuilder.append(");\n");
                        if (use) {
                            sb.append(stringBuilder.toString());
                        }
                    }
                }
            }
        }
    }

    private void insertSql(StringBuilder sb, String tableName, Timestamp start, Timestamp end, Dimension dimension1, Dimension dimension2) {
        tableName = tableName + "__" + dimension1.getName() + "__" + dimension2.getName();
        boolean cleared = false;
        for (Map.Entry<CoordinatesPath, ITEM> me : drilldown.reportItems.entrySet()) {
            CoordinatesPath coordinatesPath = me.getKey();
            if (coordinatesPath.coordinates1 != null && coordinatesPath.coordinates2 != null) {
                if (coordinatesPath.coordinates1.dimension.equals(dimension1) &&
                        coordinatesPath.coordinates2.dimension.equals(dimension2)) {
                    ITEM item = me.getValue();
                    if (item != null) {
                        if (!cleared) {
                            cleared = true;
                            sb.append("DELETE FROM ").append(tableName);
                            sb.append(" WHERE ").append(PgDbHelper.quote_timestamp(start)).append(" <= dt_start AND dt_start < ").append(PgDbHelper.quote_timestamp(end));
                            sb.append(";\n");
                        }
                        boolean use = false;
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append("INSERT INTO ").append(tableName).append(" (dt_start, dt_end");
                        ((DbBuilder)item).columnNames(stringBuilder, "item");
                        dimension1.columnNames(stringBuilder, dimension1.getName());
                        dimension2.columnNames(stringBuilder, dimension2.getName());
                        stringBuilder.append(") VALUES (").append(PgDbHelper.quote_timestamp(start)).append(", ").append(PgDbHelper.quote_timestamp(end));
                        use = ((DbBuilder)item).addToInsertSql(stringBuilder);
                        if (use) {
                            coordinatesPath.coordinates1.dimensionValue.addToInsertSql(stringBuilder);
                            coordinatesPath.coordinates2.dimensionValue.addToInsertSql(stringBuilder);
                        }
                        stringBuilder.append(");\n");
                        if (use) {
                            sb.append(stringBuilder.toString());
                        }
                    }
                }
            }
        }
    }

    private void insertSql(StringBuilder sb, String tableName, Timestamp start, Timestamp end, Dimension dimension1, Dimension dimension2, Dimension dimension3) {
        tableName = tableName + "__" + dimension1.getName() + "__" + dimension2.getName() + "__" + dimension3.getName();
        boolean cleared = false;
        for (Map.Entry<CoordinatesPath, ITEM> me : drilldown.reportItems.entrySet()) {
            CoordinatesPath coordinatesPath = me.getKey();
            if (coordinatesPath.coordinates1 != null && coordinatesPath.coordinates2 != null) {
                if (coordinatesPath.coordinates1.dimension.equals(dimension1) &&
                        coordinatesPath.coordinates2.dimension.equals(dimension2)) {
                    ITEM item = me.getValue();
                    if (item != null) {
                        if (!cleared) {
                            cleared = true;
                            sb.append("DELETE FROM ").append(tableName);
                            sb.append(" WHERE ").append(PgDbHelper.quote_timestamp(start)).append(" <= dt_start AND dt_start < ").append(PgDbHelper.quote_timestamp(end));
                            sb.append(";\n");
                        }
                        boolean use = false;
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append("INSERT INTO ").append(tableName).append(" (dt_start, dt_end");
                        ((DbBuilder)item).columnNames(stringBuilder, "item");
                        dimension1.columnNames(stringBuilder, dimension1.getName());
                        dimension2.columnNames(stringBuilder, dimension2.getName());
                        dimension3.columnNames(stringBuilder, dimension3.getName());
                        stringBuilder.append(") VALUES (").append(PgDbHelper.quote_timestamp(start)).append(", ").append(PgDbHelper.quote_timestamp(end));
                        use = ((DbBuilder)item).addToInsertSql(stringBuilder);
                        if (use) {
                            coordinatesPath.coordinates1.dimensionValue.addToInsertSql(stringBuilder);
                            coordinatesPath.coordinates2.dimensionValue.addToInsertSql(stringBuilder);
                            coordinatesPath.coordinates3.dimensionValue.addToInsertSql(stringBuilder);
                        }
                        stringBuilder.append(");\n");
                        if (use) {
                            sb.append(stringBuilder.toString());
                        }
                    }
                }
            }
        }
    }

    final public static int toYMD(java.util.Date date)
    {
        SimpleDateFormat sdfYMD = new SimpleDateFormat("yyyyMMdd");
        return Integer.parseInt(sdfYMD.format(date));
    }

    public String buildSelectCql(String keyspace, String tableName, java.util.Date start, java.util.Date end, CoordinatesQuery coordinatesQuery) {
        if (coordinatesQuery.coordinatesProvider1 != null)
            tableName = tableName + "__" + coordinatesQuery.coordinatesProvider1.getDimension().getName();
        if (coordinatesQuery.coordinatesProvider2 != null)
            tableName = tableName + "__" + coordinatesQuery.coordinatesProvider2.getDimension().getName();
        if (coordinatesQuery.coordinatesProvider3 != null)
            tableName = tableName + "__" + coordinatesQuery.coordinatesProvider3.getDimension().getName();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(" FROM ").append(keyspace).append(".").append(tableName).append(" WHERE dt = ?");
        stringBuilder.append(" AND dt_start >= ").append(start.getTime()).append(" AND dt_start < ").append(end.getTime());
        if (coordinatesQuery.coordinatesProvider1 != null) {
            stringBuilder.append(" AND ");
            stringBuilder.append(coordinatesQuery.coordinatesProvider1.forSelectWhereClause(coordinatesQuery.coordinatesProvider1.getDimension().getName()));
        }
        if (coordinatesQuery.coordinatesProvider2 != null) {
            stringBuilder.append(" AND ");
            stringBuilder.append(coordinatesQuery.coordinatesProvider2.forSelectWhereClause(coordinatesQuery.coordinatesProvider2.getDimension().getName()));
        }
        if (coordinatesQuery.coordinatesProvider3 != null) {
            stringBuilder.append(" AND ");
            stringBuilder.append(coordinatesQuery.coordinatesProvider3.forSelectWhereClause(coordinatesQuery.coordinatesProvider3.getDimension().getName()));
        }
        return stringBuilder.toString();
    }

    public String buildSelectSql(String tableName, java.util.Date start, java.util.Date end, CoordinatesQuery coordinatesQuery, ITEM forColumnNamesOnly) {
        if (coordinatesQuery.coordinatesProvider1 != null)
            tableName = tableName + "__" + coordinatesQuery.coordinatesProvider1.getDimension().getName();
        if (coordinatesQuery.coordinatesProvider2 != null)
            tableName = tableName + "__" + coordinatesQuery.coordinatesProvider2.getDimension().getName();
        if (coordinatesQuery.coordinatesProvider3 != null)
            tableName = tableName + "__" + coordinatesQuery.coordinatesProvider3.getDimension().getName();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("SELECT dt_start, dt_end");
        forColumnNamesOnly.columnNames(stringBuilder, "item");
        stringBuilder.append(" FROM ").append(tableName).append(" WHERE ").append(PgDbHelper.quote_timestamp(start)).append(" <= dt_start AND dt_start < ").append(PgDbHelper.quote_timestamp(end));
        if (coordinatesQuery.coordinatesProvider1 != null) {
            stringBuilder.append(" AND ");
            stringBuilder.append(coordinatesQuery.coordinatesProvider1.forSelectWhereClause(coordinatesQuery.coordinatesProvider1.getDimension().getName()));
        }
        if (coordinatesQuery.coordinatesProvider2 != null) {
            stringBuilder.append(" AND ");
            stringBuilder.append(coordinatesQuery.coordinatesProvider2.forSelectWhereClause(coordinatesQuery.coordinatesProvider2.getDimension().getName()));
        }
        if (coordinatesQuery.coordinatesProvider3 != null) {
            stringBuilder.append(" AND ");
            stringBuilder.append(coordinatesQuery.coordinatesProvider3.forSelectWhereClause(coordinatesQuery.coordinatesProvider3.getDimension().getName()));
        }
        return stringBuilder.toString();
    }
}
