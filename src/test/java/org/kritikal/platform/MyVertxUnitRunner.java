package org.kritikal.platform;

import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Ignore
public class MyVertxUnitRunner extends VertxUnitRunner {

    public MyVertxUnitRunner(Class<?> klass) throws InitializationError {
        super(klass);
    }

    @Override
    protected List<FrameworkMethod> computeTestMethods() {
        ArrayList<FrameworkMethod> list = new ArrayList<>(getTestClass().getAnnotatedMethods(Test.class));
        list.sort(new Comparator<FrameworkMethod>() {
            @Override
            public int compare(FrameworkMethod o1, FrameworkMethod o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        return list;
    }

}