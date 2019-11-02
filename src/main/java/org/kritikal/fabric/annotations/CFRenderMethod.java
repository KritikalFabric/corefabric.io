package org.kritikal.fabric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CFRenderMethod {
    String regex();
    String[] sites();
    enum TYPE { NOSCRIPT, AMP }
    CFRenderMethod.TYPE type() default CFRenderMethod.TYPE.NOSCRIPT;
    String template();
}
