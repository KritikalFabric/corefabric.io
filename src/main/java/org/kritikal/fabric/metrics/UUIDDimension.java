package org.kritikal.fabric.metrics;

import java.util.UUID;

/**
 * Created by ben on 16/06/15.
 */
public class UUIDDimension extends BasicDimension<UUID> {
    public UUIDDimension(String name) {
        super(name);
    }

    @Override
    public Dimension newInstance() {
        return new UUIDDimension(name);
    }

    @Override
    public void columnDefinitions(StringBuilder sb, String name) {
        sb.append(",").append(name).append(" uuid\n");
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
