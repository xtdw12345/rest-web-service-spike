package com.spring.di.exception;

import com.spring.di.Component;

public class DependencyNotFoundException extends RuntimeException {

    private Component component;
    private Component dependency;

    public DependencyNotFoundException(Component component, Component dependency) {
        this.component = component;
        this.dependency = dependency;
    }

    public Component getDependency() {
        return dependency;
    }

    public Component getComponent() {
        return component;
    }
}
