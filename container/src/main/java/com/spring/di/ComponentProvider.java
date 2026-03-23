package com.spring.di;

import java.util.List;

interface ComponentProvider<T> {
    T get(Context context);

    default List<ComponentRef<?>> getDependencyRefs() {
        return List.of();
    }
}
