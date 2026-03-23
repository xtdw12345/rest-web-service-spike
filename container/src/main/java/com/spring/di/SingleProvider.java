package com.spring.di;

import java.util.List;

class SingleProvider<T> implements ComponentProvider<T> {
    private T singleton;
    private ComponentProvider<T> provider;

    public SingleProvider(ComponentProvider<T> provider) {
        this.provider = provider;
    }

    @Override
    public T get(Context context) {
        if (singleton == null) {
            singleton = provider.get(context);
        }
        return singleton;
    }

    @Override
    public List<ComponentRef<?>> getDependencyRefs() {
        return provider.getDependencyRefs();
    }
}
