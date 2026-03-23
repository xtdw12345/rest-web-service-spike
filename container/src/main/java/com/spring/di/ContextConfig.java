package com.spring.di;

import com.spring.di.exception.CyclicDependencyFoundException;
import com.spring.di.exception.DependencyNotFoundException;
import com.spring.di.exception.IllegalComponentException;
import jakarta.inject.Provider;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;

import javax.ws.rs.Path;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ContextConfig {

    private final Map<Component, ComponentProvider<?>> components = new HashMap<>();
    private Map<Class<?>, ScopeProvider> scopes = new HashMap<>();

    public ContextConfig() {
        scopes.put(Singleton.class, SingleProvider::new);
    }

    public <ComponentType> void bind(Class<ComponentType> componentType, ComponentType component) {
        components.put(new Component(componentType, null),  context -> component);
    }

    public <ComponentType> void bind(Class<ComponentType> componentClass, ComponentType component, Annotation... qualifiers) {
        for (Annotation qualifier : qualifiers) {
            if (!qualifier.annotationType().isAnnotationPresent(Qualifier.class)) {
                throw new IllegalComponentException();
            }
            components.put(new Component(componentClass, qualifier), context -> component);
        }
    }

    public void bind(Class<?> componentType, Class<?> componentImplClass) {
        bind(componentType, componentImplClass, componentImplClass.getAnnotations());
    }

    public void bind(Class<?> type, Class<?> implementation,  Annotation... annotations) {
        Map<Class<? extends Annotation>, List<Annotation>> typeAnnotationsMap = Arrays.stream(annotations).collect(Collectors.groupingBy(ContextConfig::toType));

        if (typeAnnotationsMap.containsKey(IllegalAnnotation.class)) {
            throw new IllegalComponentException();
        }
        if (typeAnnotationsMap.getOrDefault(Scope.class, List.of()).size() > 1) {
            throw new IllegalComponentException();
        }
        List<Annotation> qualifiers = typeAnnotationsMap.getOrDefault(Qualifier.class, List.of());
        Optional<Annotation> scope = typeAnnotationsMap.getOrDefault(Scope.class, scopeFrom(implementation)).stream().findFirst();
        bind(type, qualifiers, createScopeProvider(scope, new InjectionProvider<>(implementation)));
    }

    private ComponentProvider<?> createScopeProvider(Optional<Annotation> scope, ComponentProvider<?> provider) {
        return scope.<ComponentProvider<?>>map(s -> getComponentProvider(s, provider)).orElse(provider);
    }

    private <ComponentType> void bind(Class<ComponentType> componentClass, List<Annotation> qualifiers, ComponentProvider<?> injectProvider) {
        if(qualifiers.isEmpty()) {
            components.put(new Component(componentClass, null), injectProvider);
        }
        for (Annotation qualifier : qualifiers){
            components.put(new Component(componentClass, qualifier), injectProvider);
        }
    }

    private ComponentProvider<?> getComponentProvider(Annotation s, ComponentProvider<?> finalProvider) {
        if(!scopes.containsKey(s.annotationType())) {
            throw new IllegalComponentException();
        }
        return scopes.get(s.annotationType()).create(finalProvider);
    }

    private static <ComponentType, ComponentImplTpe extends ComponentType> List<Annotation> scopeFrom(Class<ComponentImplTpe> componentImplClass) {
        return Arrays.stream(componentImplClass.getAnnotations()).filter(a -> a.annotationType().isAnnotationPresent(Scope.class)).toList();
    }

    private static Class<? extends Annotation> toType(Annotation a) {
        return Stream.of(Qualifier.class, Scope.class, Path.class).filter(t -> a.annotationType().isAnnotationPresent(t) || a.annotationType() == t).findFirst().orElse(IllegalAnnotation.class);
    }

    @interface IllegalAnnotation {

    }

    public <ScopeType extends Annotation> void scope(Class<ScopeType> scopeType, ScopeProvider scopeProvider) {
        scopes.put(scopeType, scopeProvider);
    }

    public Context getContext() {
        components.keySet().forEach(component -> checkDependencies(component, new Stack<>()));
        return new Context() {
            @Override
            public Optional get(ComponentRef ref) {
                if (ref.component().qualifier() != null) {
                    return Optional.ofNullable(components.get(ref.component())).map(p -> p.get(this));
                }
                if (ref.isContainer()) {
                    return getContainer(ref);
                }
                return getComponent(ref);
            }

            private Optional getComponent(ComponentRef ref) {
                return Optional.ofNullable(components.get(ref.component())).map(p -> p.get(this));
            }

            private Optional getContainer(ComponentRef ref) {
                Type container = ref.getContainer();
                if (container != Provider.class) {
                    return Optional.empty();
                }
                return Optional.ofNullable(components.get(ref.component())).map(p -> (Provider<Object>) () -> p.get(this));
            }
        };
    }

    private void checkDependencies(Component component, Stack<Component> visiting) {
        for (ComponentRef dependency : components.get(component).getDependencyRefs()) {
            if (!components.containsKey(dependency.component())) {
                throw new DependencyNotFoundException(component, dependency.component());
            }
            if (!dependency.isContainer()){
                if (visiting.contains(dependency.component())) {
                    throw new CyclicDependencyFoundException(new HashSet<>(visiting));
                }
                visiting.push(dependency.component());
                checkDependencies(new Component(dependency.component().componentType(), dependency.component().qualifier()), visiting);
                visiting.pop();
            }
        }
    }
}
