package com.spring.di;

import com.spring.di.exception.CyclicDependencyFoundException;
import com.spring.di.exception.DependencyNotFoundException;
import com.spring.di.exception.IllegalComponentException;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.*;

public class ContainerTest {
    ContextConfig config;

    @BeforeEach
    public void setUp() {
        config = new ContextConfig();
    }

    @Nested
    class TypeBinding {
        @Test
        public void should_bind_type_to_a_specific_instance() {
            TestComponent component = new TestComponent() {
            };
            config.bind(TestComponent.class, component);

            Context context = config.getContext();
            TestComponent instance = context.get(ComponentRef.of(TestComponent.class)).get();
            Assertions.assertSame(component, instance);
        }

        @ParameterizedTest(name = "Supporting {0}")
        @MethodSource
        public void should_bind_type_to_an_injectable_component(Class<? extends TestComponent> componentClass) {
            Dependency dependency = new Dependency() {};
            config.bind(Dependency.class, dependency);
            config.bind(TestComponent.class, componentClass);

            Context context = config.getContext();
            Optional<TestComponent> component = context.get(ComponentRef.of(TestComponent.class));
            assertTrue(component.isPresent());
            assertSame(dependency, component.get().getDependency());
        }

        private static Stream<Arguments> should_bind_type_to_an_injectable_component() {
            return Stream.of(
                    Arguments.of(Named.of("Constructor injection", ComponentWithInjectionConstructor.class)),
                    Arguments.of(Named.of("Field injection", ComponentWithInjectionField.class)),
                    Arguments.of(Named.of("Method injection", ComponentWithInjectionMethod.class))
            );
        }

        @Test
        public void should_return_null_is_component_not_defined() {
            Context context = config.getContext();
            Optional<TestComponent> component = context.get(ComponentRef.of(TestComponent.class));
            assertTrue(component.isEmpty());
        }

        @Test
        public void should_retrieve_provided_component() throws Exception {
            TestComponent component = new TestComponent() {};
            config.bind(TestComponent.class, component);
            Context context = config.getContext();
            Provider<TestComponent> instance = context.get(new ComponentRef<Provider<TestComponent>>(){}).get();
            assertSame(component, instance.get());
        }

        private List<TestComponent> componentList;
        @Test
        public void should_not_retrieve_provided_component_if_unsupported_container() throws Exception {
            TestComponent component = new TestComponent() {};
            config.bind(TestComponent.class, component);
            ParameterizedType componentProviderType = (ParameterizedType)TypeBinding.class.getDeclaredField("componentList").getGenericType();
            Context context = config.getContext();
            assertFalse(context.get(ComponentRef.of(componentProviderType)).isPresent());
        }

        @Nested
        class WithQualifier {
            record NamedLiteral(String value) implements jakarta.inject.Named {
                @Override
                public Class<? extends Annotation> annotationType() {
                    return jakarta.inject.Named.class;
                }

                @Override
                public boolean equals(Object o) {
                    if (o instanceof jakarta.inject.Named named) return Objects.equals(value, named.value());
                    return false;
                }

                @Override
                public int hashCode() {
                    return "value".hashCode() * 127 ^ value.hashCode();
                }
            }

            @java.lang.annotation.Documented
            @java.lang.annotation.Retention(RUNTIME)
            @jakarta.inject.Qualifier
            @interface SkyWalker {
            }

            record SkyWalkerLiteral() implements SkyWalker {

                @Override
                public Class<? extends Annotation> annotationType() {
                    return SkyWalker.class;
                }

                @Override
                public boolean equals(Object obj) {
                    return obj instanceof SkyWalker;
                }
            }

            record TestLiteral() implements Test {

                @Override
                public Class<? extends Annotation> annotationType() {
                    return Test.class;
                }
            }

            @Test
            public void should_bind_instance_by_multiple_qualifiers() {
                TestComponent component = new TestComponent() {
                };
                config.bind(TestComponent.class, component, new NamedLiteral("chosenOne"), new SkyWalkerLiteral());

                Context context = config.getContext();
                TestComponent chosenOne = context.get(ComponentRef.of(TestComponent.class, new NamedLiteral("chosenOne"))).get();
                TestComponent skyWalker = context.get(ComponentRef.of(TestComponent.class, new SkyWalkerLiteral())).get();
                Assertions.assertSame(component, chosenOne);
                Assertions.assertSame(component, skyWalker);
            }

            @Test
            public void should_bind_type_by_multiple_qualifiers() {
                config.bind(TestComponent.class, ComponentWithDefaultConstructor.class, new NamedLiteral("chosenOne"), new SkyWalkerLiteral());
                Context context = config.getContext();
                TestComponent chosenOne = context.get(ComponentRef.of(TestComponent.class, new NamedLiteral("chosenOne"))).get();
                TestComponent skyWalker = context.get(ComponentRef.of(TestComponent.class, new SkyWalkerLiteral())).get();
                Assertions.assertNotNull(chosenOne);
                Assertions.assertNotNull(skyWalker);
            }

            @Test
            public void should_throw_exception_if_given_illegal_qualifier_to_instance() {
                TestComponent component = new TestComponent() {
                };
                assertThrows(IllegalComponentException.class, () -> config.bind(TestComponent.class, component, new TestLiteral()));

            }

            @Test
            public void should_throw_exception_if_given_illegal_qualifier_to_component() {
                assertThrows(IllegalComponentException.class, () -> config.bind(TestComponent.class, ComponentWithDefaultConstructor.class, new TestLiteral()));

            }
        }
    }

    @Nested
    class WithScope {
        static class NotSingleton {

        }
        @Test
        public void should_not_be_singleton_scope_by_default() {
            config.bind(NotSingleton.class, NotSingleton.class);
            Context context = config.getContext();
            assertNotSame(context.get(ComponentRef.of(NotSingleton.class)).get(), context.get(ComponentRef.of(NotSingleton.class)).get());
        }

        @Test
        public void should_bind_component_as_singleton() {
            config.bind(NotSingleton.class, NotSingleton.class, new SingletonLiteral());
            Context context = config.getContext();
            assertSame(context.get(ComponentRef.of(NotSingleton.class)).get(), context.get(ComponentRef.of(NotSingleton.class)).get());
        }

        record SingletonLiteral() implements Singleton {

            @Override
            public Class<? extends Annotation> annotationType() {
                return Singleton.class;
            }
        }

        @Singleton
        static class SingletonComponent implements TestComponent{

        }

        @Test
        public void should_retrieve_singleton_scope_from_component() {
            config.bind(SingletonComponent.class, SingletonComponent.class);
            Context context = config.getContext();
            assertSame(context.get(ComponentRef.of(SingletonComponent.class, null)).get(), context.get(ComponentRef.of(SingletonComponent.class, null)).get());
        }

        @Scope
        @Documented
        @Retention(RUNTIME)
        @interface Pooled {}

        @Pooled
        static class PooledComponent implements TestComponent {

        }

        static class PooledProvider<T> implements ComponentProvider<T> {
            private static final int MAX = 2;
            private int current = 0;
            private List<T> instances = new ArrayList<>();
            private ComponentProvider<T> provider;

            public PooledProvider(ComponentProvider<T> provider) {
                this.provider = provider;
            }

            @Override
            public T get(Context context) {
                if (instances.size() < MAX) {
                    instances.add(provider.get(context));
                }
                return instances.get(current++ % MAX);
            }

            @Override
            public List<ComponentRef<?>> getDependencyRefs() {
                return provider.getDependencyRefs();
            }
        }

        @Test
        public void should_support_custom_scope() {
            config.scope(Pooled.class, PooledProvider::new);
            config.bind(PooledComponent.class, PooledComponent.class);
            Context context = config.getContext();
            Set<PooledComponent> instances = IntStream.range(1, 5).mapToObj(i -> context.get(ComponentRef.of(PooledComponent.class, null)).get()).collect(Collectors.toSet());
            assertEquals(PooledProvider.MAX, instances.size());
        }

        record PooledLiteral() implements Pooled {

            @Override
            public Class<? extends Annotation> annotationType() {
                return Pooled.class;
            }
        }

        @Test
        public void should_throw_exception_if_multi_scope_provided() {
            assertThrows(IllegalComponentException.class, () -> config.bind(PooledComponent.class, PooledComponent.class, new SingletonLiteral(), new PooledLiteral()));
        }

        @Singleton @Pooled
        static class MultiScopeAnnotated {

        }

        @Test
        public void should_throw_exception_if_multi_scope_annotated() {
            assertThrows(IllegalComponentException.class, () -> config.bind(MultiScopeAnnotated.class, MultiScopeAnnotated.class));
        }

        @Test
        public void should_throw_exception_if_scope_undefined() {
            assertThrows(IllegalComponentException.class, () -> config.bind(PooledComponent.class, PooledComponent.class, new PooledLiteral()));
        }

        @Nested
        class WithQualifier {
            @Test
            public void should_not_be_singleton_scope_by_default() {
                config.bind(NotSingleton.class, NotSingleton.class, new TypeBinding.WithQualifier.SkyWalkerLiteral());
                Context context = config.getContext();
                assertNotSame(context.get(ComponentRef.of(NotSingleton.class, new TypeBinding.WithQualifier.SkyWalkerLiteral())).get(), context.get(ComponentRef.of(NotSingleton.class, new TypeBinding.WithQualifier.SkyWalkerLiteral())).get());
            }

            @Test
            public void should_bind_component_as_singleton() {
                config.bind(NotSingleton.class, NotSingleton.class, new SingletonLiteral(), new TypeBinding.WithQualifier.SkyWalkerLiteral());
                Context context = config.getContext();
                assertSame(context.get(ComponentRef.of(NotSingleton.class, new TypeBinding.WithQualifier.SkyWalkerLiteral())).get(), context.get(ComponentRef.of(NotSingleton.class, new TypeBinding.WithQualifier.SkyWalkerLiteral())).get());
            }
        }
    }

    @Nested
    class DependencyCheck {

        @ParameterizedTest
        @MethodSource
        public void should_throw_exception_if_dependency_not_found(Class<? extends TestComponent> componentClass) {
            config.bind(TestComponent.class, componentClass);
            DependencyNotFoundException dependencyNotFoundException = assertThrows(DependencyNotFoundException.class, () -> config.getContext());
            assertEquals(Dependency.class, dependencyNotFoundException.getDependency().componentType());
            assertEquals(TestComponent.class, dependencyNotFoundException.getComponent().componentType());
        }

        static class ComponentWithProviderInjectionConstructor {
            Provider<Dependency> dependencyProvider;

            @Inject
            public ComponentWithProviderInjectionConstructor(Provider<Dependency> dependencyProvider) {
                this.dependencyProvider = dependencyProvider;
            }
        }
        static class ComponentWithProviderInjectionField {
            @Inject
            Provider<Dependency> dependencyProvider;
        }
        static class ComponentWithProviderInjectionMethod {
            Provider<Dependency> dependencyProvider;

            @Inject
            public void install(Provider<Dependency> dependencyProvider) {
                this.dependencyProvider = dependencyProvider;
            }
        }
        @Singleton
        static class SingletonMissingDependency {
            @Inject
            Provider<Dependency> dependencyProvider;
        }

        private static Stream<Arguments> should_throw_exception_if_dependency_not_found() {
            return Stream.of(
                    Arguments.of(Named.of("Constructor injection", ComponentWithInjectionConstructor.class)),
                    Arguments.of(Named.of("Field injection", ComponentWithInjectionField.class)),
                    Arguments.of(Named.of("Method injection", ComponentWithInjectionMethod.class))
                    ,Arguments.of(Named.of("Constructor provider injection", ComponentWithProviderInjectionConstructor.class))
                    ,Arguments.of(Named.of("Field provider injection", ComponentWithProviderInjectionField.class))
                    ,Arguments.of(Named.of("Method provider injection", ComponentWithProviderInjectionMethod.class))
                    ,Arguments.of(Named.of("Singleton", SingletonMissingDependency.class))
            );
        }

        @ParameterizedTest
        @MethodSource
        public void should_throw_exception_if_cyclic_dependency_exist(Class<? extends TestComponent>  componentClass, Class<? extends Dependency> dependencyClass) {
            config.bind(TestComponent.class, componentClass);
            config.bind(Dependency.class, dependencyClass);
            CyclicDependencyFoundException cyclicDependencyFoundException = assertThrows(CyclicDependencyFoundException.class, () -> config.getContext());
            assertEquals(2, cyclicDependencyFoundException.getComponents().size());
            assertTrue(cyclicDependencyFoundException.getComponents().contains(TestComponent.class));
            assertTrue(cyclicDependencyFoundException.getComponents().contains(Dependency.class));
        }

        private static Stream<Arguments> should_throw_exception_if_cyclic_dependency_exist() {
            List<Arguments>  arguments = new ArrayList<>();
            for (Class<? extends TestComponent> componentClass : Arrays.asList(ComponentInjectDependencyWithConstructor.class, ComponentInjectDependencyWithMethod.class, ComponentInjectDependencyWithField.class)) {
                for (Class<? extends Dependency> dependencyClass : Arrays.asList(DependencyInjectComponentWithConstructor.class, DependencyInjectComponentWithMethod.class, DependencyInjectComponentWithField.class)) {
                    arguments.add(Arguments.of(componentClass, dependencyClass));
                }
            }
            return arguments.stream();
        }

        static class ComponentInjectDependencyWithConstructor implements TestComponent {
            Dependency dependency;
            @Inject
            ComponentInjectDependencyWithConstructor(Dependency dependency) {
                this.dependency = dependency;
            }
        }
        static class DependencyInjectComponentWithConstructor implements Dependency {
            TestComponent component;
            @Inject
            DependencyInjectComponentWithConstructor(TestComponent component) {
                this.component = component;
            }
        }
        static class ComponentInjectDependencyWithMethod  implements TestComponent {
            Dependency dependency;
            @Inject
            void install(Dependency dependency) {
                this.dependency = dependency;
            }
        }
        static class DependencyInjectComponentWithMethod implements Dependency {
            TestComponent component;
            @Inject
            void install(TestComponent component) {
                this.component = component;
            }
        }
        static class ComponentInjectDependencyWithField implements TestComponent {
            @Inject
            Dependency dependency;
        }
        static class DependencyInjectComponentWithField  implements Dependency {
            @Inject
            TestComponent component;
        }

        @Test
        public void should_throw_exception_if_transitive_cyclic_dependency_exist() {
            config.bind(TestComponent.class, ComponentWithInjectionConstructor.class);
            config.bind(Dependency.class, DependencyDependedOnAnotherDependency.class);
            config.bind(AnotherDependency.class, AnotherDependencyDependedOnComponent.class);
            assertThrows(CyclicDependencyFoundException.class, () -> config.getContext());
        }

        static class DependencyDependedProvidedComponentWithConstructor implements Dependency {
            Provider<TestComponent> componentProvider;
            @Inject
            DependencyDependedProvidedComponentWithConstructor(Provider<TestComponent> componentProvider) {
                this.componentProvider = componentProvider;
            }
        }

        @Test
        public void should_not_throw_exception_if_cyclic_dependency_provide_exist() {
            config.bind(TestComponent.class, ComponentWithInjectionConstructor.class);
            config.bind(Dependency.class, DependencyDependedProvidedComponentWithConstructor.class);
            Context context = config.getContext();
            assertTrue(context.get(ComponentRef.of(TestComponent.class)).isPresent());
        }

        @Nested
        class WithQualifier {
            @Test
            public void should_throw_exception_if_qualifier_dependency_not_found() {
                Dependency dependency = new Dependency() {
                };
                config.bind(Dependency.class, dependency);
                config.bind(TestComponent.class, InjectConstructor.class, new TypeBinding.WithQualifier.SkyWalkerLiteral());
                DependencyNotFoundException dependencyNotFoundException = assertThrows(DependencyNotFoundException.class, () -> config.getContext());

                assertEquals(new Component(TestComponent.class,  new TypeBinding.WithQualifier.SkyWalkerLiteral()), dependencyNotFoundException.getComponent());
                assertEquals(new Component(Dependency.class,  new TypeBinding.WithQualifier.NamedLiteral("chosenOne")), dependencyNotFoundException.getDependency());
            }
            static class InjectConstructor implements TestComponent {
                private Dependency dependency;
                @Inject
                InjectConstructor(@jakarta.inject.Named("chosenOne") Dependency dependency) {
                    this.dependency = dependency;
                }
            }

            static class InjectDependency implements Dependency {
                private Dependency dependency;
                @Inject
                InjectDependency(@jakarta.inject.Named("chosenOne") Dependency dependency) {
                    this.dependency = dependency;
                }
            }

            static class NotCyclicDependency implements Dependency {
                private Dependency dependency;
                @Inject
                NotCyclicDependency(@TypeBinding.WithQualifier.SkyWalker Dependency dependency) {
                    this.dependency = dependency;
                }
            }

            @Test
            public void should_not_throw_cyclic_exception_if_dependency_tag_with_qualifier() {
                Dependency dependency = new Dependency() {
                };
                config.bind(Dependency.class, dependency, new TypeBinding.WithQualifier.NamedLiteral("chosenOne"));
                config.bind(Dependency.class, InjectDependency.class, new TypeBinding.WithQualifier.SkyWalkerLiteral());
                config.bind(Dependency.class, NotCyclicDependency.class);

                assertDoesNotThrow(() -> config.getContext());
            }
        }
    }

    static interface Dependency {
    }

    static interface AnotherDependency {
    }

    static interface TestComponent {
        default Dependency getDependency(){
            return null;
        };
    }

    static class ComponentWithDefaultConstructor implements TestComponent {
        public ComponentWithDefaultConstructor() {
        }
    }

    static class ComponentWithMultiInjectConstructors implements TestComponent {
        @Inject
        public ComponentWithMultiInjectConstructors(String name, Double value) {
        }

        @Inject
        public ComponentWithMultiInjectConstructors(String name) {
        }
    }

    static class ComponentWithNoInjectConstructorNorDefaultConstructor implements TestComponent {
        public ComponentWithNoInjectConstructorNorDefaultConstructor(String name, Double value) {
        }
    }

    static class AnotherDependencyDependedOnComponent implements AnotherDependency {
        private final TestComponent component;

        @Inject
        public AnotherDependencyDependedOnComponent(TestComponent component) {
            this.component = component;
        }
    }

    static class DependencyWithInjectionConstructor implements Dependency {
        String value;

        @Inject
        DependencyWithInjectionConstructor(String value) {
            this.value = value;
        }
    }

    static class ComponentWithInjectionConstructor implements TestComponent {
        Dependency dependency;

        @Inject
        ComponentWithInjectionConstructor(Dependency dependency) {
            this.dependency = dependency;
        }

        @Override
        public Dependency getDependency() {
            return dependency;
        }
    }

    static class ComponentWithInjectionField implements TestComponent {
        @Inject
        Dependency dependency;

        @Override
        public Dependency getDependency() {
            return dependency;
        }
    }

    static class ComponentWithInjectionMethod implements TestComponent {
        Dependency dependency;
        @Inject
        ComponentWithInjectionMethod(Dependency dependency) {
            this.dependency = dependency;
        }

        @Override
        public Dependency getDependency() {
            return dependency;
        }
    }

    static class DependencyDependedOnAnotherDependency implements Dependency {
        private final AnotherDependency anotherDependency;

        @Inject
        public DependencyDependedOnAnotherDependency(AnotherDependency anotherDependency) {
            this.anotherDependency = anotherDependency;
        }
    }
}

