package com.spring.di;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

public class ComponentRef<ComponentType> {

    public static <ComponentType> ComponentRef<ComponentType> of(Class<ComponentType> type) {
        return new ComponentRef<>(type);
    }

    public static <ComponentType> ComponentRef<ComponentType> of(Class<ComponentType> type, Annotation qualifier) {
        return new ComponentRef<>(type, qualifier);
    }

    public static ComponentRef of(Type type) {
        return new ComponentRef(type);
    }

    public static ComponentRef of(Type type, Annotation qualifier) {
        return new ComponentRef(type, qualifier);
    }

    private Type container;
    private Component component;

    ComponentRef(Type type) {
        init(type);
    }

    ComponentRef(Type type, Annotation qualifier) {
        init(type, qualifier);
    }

    public ComponentRef(Class<ComponentType> component, Annotation qualifier) {
        this.component = new Component(component, qualifier);
    }

    ComponentRef(Class<ComponentType> component) {
        init(component);
    }

    protected ComponentRef() {
        Type type = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        init(type);
    }

    private void init(Type type) {
        init(type, null);
    }

    private void init(Type type, Annotation qualifier) {
        if (type instanceof ParameterizedType container) {
            this.container = container.getRawType();
            this.component = new Component((Class<ComponentType>) container.getActualTypeArguments()[0], qualifier);
        } else {
            this.component = new Component((Class<ComponentType>) type, qualifier);
        }

    }

    public Component component() {
        return component;
    }

    public Type getContainer() {
        return container;
    }

    public boolean isContainer() {
        return container != null;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ComponentRef<?> ref = (ComponentRef<?>) o;
        return Objects.equals(container, ref.container) && Objects.equals(component, ref.component);
    }

    @Override
    public int hashCode() {
        return Objects.hash(container, component);
    }

    @Override
    public String toString() {
        return "ComponentRef{" +
                "container=" + container +
                ", component=" + component +
                '}';
    }
}
