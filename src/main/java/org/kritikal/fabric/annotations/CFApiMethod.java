package org.kritikal.fabric.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CFApiMethod {
    String url();
    String[] sites();
    enum TYPE {JSON_GET, JSON_POST, GENERIC_GET, GENERIC_POST }
    TYPE type() default TYPE.JSON_GET;
    boolean cors() default false;
}
