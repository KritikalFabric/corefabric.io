package org.kritikal.fabric.annotations;

import org.kritikal.fabric.core.Configuration;

public abstract class CFApiBase {
    public final Configuration cfg;
    public CFApiBase(final Configuration cfg) {
        this.cfg = cfg;
    }
}
