package org.kritikal.fabric.metrics;

/**
 * Created by ben on 16/06/15.
 */
public class StringDimension extends BasicDimension<String> {

    public StringDimension(String name) {
        super(name);
    }

    @Override
    public Dimension newInstance() {
        return new StringDimension(name);
    }

    @Override
    public void columnDefinitions(StringBuilder sb, String name) {
        sb.append(",").append(name).append(" text\n");
    }

    @Override
    public void columnNames(StringBuilder sb, String name) {
        sb.append(",").append(name);
    }

    @Override
    public void indexDefinitions(StringBuilder sb, String tableName, String name, String suffix) {
        if (!MetricsConfiguration.USE_CASSANDRA) {
            sb.append("CREATE INDEX i_").append(tableName).append("__").append(suffix).append(" ON ").append(tableName).append(" (").append(name).append(");\n");
        }
    }
}
