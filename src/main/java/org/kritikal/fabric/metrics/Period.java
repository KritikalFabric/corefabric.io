package org.kritikal.fabric.metrics;

/**
 * Created by ben on 06/06/15.
 */
public class Period {

    protected long start;
    protected long end;

    public long getStart() { return start; }
    public long getEnd() { return end; }

    public void applyPeriod(ApplyPeriod data) {
        data.applyPeriod(this);
    }

    public boolean isSamePeriod(java.sql.Timestamp ts) {
        long time = ts.getTime();
        if (start <= time && time < end) return true;
        return false;
    }

}
