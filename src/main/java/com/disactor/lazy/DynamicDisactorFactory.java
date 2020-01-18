package com.disactor.lazy;

import java.lang.reflect.Proxy;

public class DynamicDisactorFactory {

    private final String type;

    public DynamicDisactorFactory(String type) {
        this.type = type;
    }


    public Object getObject() throws Exception {
        //fixme: fix deprecated
        Class proxyClass = Proxy.getProxyClass(this.getClass().getClassLoader(), Class.forName(type));
        Object instance = proxyClass.newInstance();

        return instance;
    }

}
