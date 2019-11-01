package org.kritikal.fabric.annotations;

public class CFPromiseEnd extends RuntimeException {
    public final Object cfResult;
    public CFPromiseEnd(Object cfResult) { this.cfResult = cfResult; }
}
