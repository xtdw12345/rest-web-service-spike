package com.spring.di.exception;

import com.spring.di.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class CyclicDependencyFoundException extends RuntimeException {
    private Set<Component> components = new HashSet<>();

    public CyclicDependencyFoundException(Set<Component> components) {
        this.components = components;
    }

    public Set<Class<?>> getComponents() {
        return components.stream().map(Component::componentType).collect(Collectors.toSet());
    }
}
