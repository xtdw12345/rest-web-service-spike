package com.spring.di;

interface ScopeProvider {
    ComponentProvider<?> create(ComponentProvider<?> componentProvider);
}
