package com.spring.di;

import com.spring.di.exception.IllegalComponentException;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Type;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Nested
class InjectionTest {
    private ContextConfig config = Mockito.mock(ContextConfig.class);
    private Context context = Mockito.mock(Context.class);
    private ContainerTest.Dependency dependency = new ContainerTest.Dependency() {
    };
    private Provider<ContainerTest.Dependency> dependencyProvider = Mockito.mock(Provider.class);
    private Type dependencyProviderType;

    @BeforeEach
    public void setUp() throws NoSuchFieldException {
        when(config.getContext()).thenReturn(context);
        config.bind(ContainerTest.Dependency.class, dependency);
        when(context.get(eq(ComponentRef.of(ContainerTest.Dependency.class)))).thenReturn(Optional.of(dependency));
        dependencyProviderType = InjectionTest.class.getDeclaredField("dependencyProvider").getGenericType();
        when(context.get(eq(ComponentRef.of(dependencyProviderType)))).thenReturn(Optional.of(dependencyProvider));
    }

    private <T, R extends T> T getComponent(Class<T> componentClass, Class<R> componentImplClass) {
        InjectionProvider<R> injectionProvider = new InjectionProvider<>(componentImplClass);
        return injectionProvider.get(context);
    }

    @Nested
    class ConstructorInjection {
        @Nested
        class InjectTest {
            @Test
            public void should_bind_type_to_a_class_with_default_constructor() {
                ContainerTest.TestComponent instance = getComponent(ContainerTest.TestComponent.class, ContainerTest.ComponentWithDefaultConstructor.class);
                assertNotNull(instance);
                assertInstanceOf(ContainerTest.ComponentWithDefaultConstructor.class, instance);
            }

            @Test
            public void should_bind_type_to_a_class_with_injection_constructor() {

                ContainerTest.TestComponent instance = getComponent(ContainerTest.TestComponent.class, ContainerTest.ComponentWithInjectionConstructor.class);
                assertNotNull(instance);
                assertInstanceOf(ContainerTest.ComponentWithInjectionConstructor.class, instance);
                assertSame(dependency, ((ContainerTest.ComponentWithInjectionConstructor) instance).dependency);
            }

            @Test
            public void should_bind_type_to_a_class_with_nested_injection_constructor() {
                when(context.get(ComponentRef.of(ContainerTest.Dependency.class))).thenReturn(Optional.of(new ContainerTest.DependencyWithInjectionConstructor("Hello World!")));
                ContainerTest.TestComponent instance = getComponent(ContainerTest.TestComponent.class, ContainerTest.ComponentWithInjectionConstructor.class);

                assertNotNull(instance);
                assertInstanceOf(ContainerTest.ComponentWithInjectionConstructor.class, instance);
                assertInstanceOf(ContainerTest.DependencyWithInjectionConstructor.class, ((ContainerTest.ComponentWithInjectionConstructor) instance).dependency);
                assertEquals("Hello World!", ((ContainerTest.DependencyWithInjectionConstructor) ((ContainerTest.ComponentWithInjectionConstructor) instance).dependency).value);
            }

            static class ConstructorProviderInjection {
                Provider<ContainerTest.Dependency>  dependencyProvider;
                @Inject
                ConstructorProviderInjection(Provider<ContainerTest.Dependency> dependencyProvider) {
                    this.dependencyProvider = dependencyProvider;
                }
            }
            @Test
            public void should_bind_provider_type_with_constructor_injection() {
                InjectionProvider<ConstructorProviderInjection> provider = new InjectionProvider<>(ConstructorProviderInjection.class);
                ConstructorProviderInjection component = provider.get(context);
                assertSame(dependencyProvider, component.dependencyProvider);
            }

            static class ComponentWithConstructorInjection {
                ContainerTest.Dependency dependency;
                @Inject
                ComponentWithConstructorInjection(ContainerTest.Dependency dependency) {
                    this.dependency = dependency;
                }
            }

            @Test
            public void should_include_constructor_inject_dependencies_info() {
                InjectionProvider<ComponentWithConstructorInjection> injectionProvider = new InjectionProvider<>(ComponentWithConstructorInjection.class);
                assertArrayEquals(new ComponentRef[]{ComponentRef.of(ContainerTest.Dependency.class)}, injectionProvider.getDependencyRefs().toArray());
            }

            static class ComponentWithConstructorProviderInjection {
                Provider<ContainerTest.Dependency> dependency;
                @Inject
                ComponentWithConstructorProviderInjection(Provider<ContainerTest.Dependency> dependency) {
                    this.dependency = dependency;
                }
            }
            @Test
            public void should_include_constructor_inject_dependency_types_info() {
                InjectionProvider<ComponentWithConstructorProviderInjection> injectionProvider = new InjectionProvider<>(ComponentWithConstructorProviderInjection.class);
                assertArrayEquals(new ComponentRef[]{ComponentRef.of(dependencyProviderType)}, injectionProvider.getDependencyRefs().toArray());
            }

            @Nested
            class WithQualifier {

                @BeforeEach
                public void setUp() {
                    Mockito.reset(context);
                    when(context.get(eq(ComponentRef.of(ContainerTest.Dependency.class, new ContainerTest.TypeBinding.WithQualifier.NamedLiteral("chosenOne"))))).thenReturn(Optional.of(dependency));
                }

                static class InjectConstructor {
                    ContainerTest.Dependency dependency;
                    @Inject
                    InjectConstructor(@Named("chosenOne") ContainerTest.Dependency dependency) {
                        this.dependency = dependency;
                    }
                }

                @Test
                public void should_inject_dependency_with_qualifier_via_constructor() {
                    InjectionProvider<InjectConstructor> injectionProvider = new InjectionProvider<>(InjectConstructor.class);
                    InjectConstructor component = injectionProvider.get(context);
                    assertSame(dependency, component.dependency);
                }

                @Test
                public void should_include_dependency_with_qualifier() {
                    InjectionProvider<InjectConstructor> injectionProvider = new InjectionProvider<>(InjectConstructor.class);
                    assertArrayEquals(new ComponentRef[]{ComponentRef.of(ContainerTest.Dependency.class, new ContainerTest.TypeBinding.WithQualifier.NamedLiteral("chosenOne"))}, injectionProvider.getDependencyRefs().toArray());
                }

                static class InjectConstructorWithMultiQualifiers {
                    ContainerTest.Dependency dependency;
                    @Inject
                    InjectConstructorWithMultiQualifiers(@Named("chosenOne") @ContainerTest.TypeBinding.WithQualifier.SkyWalker ContainerTest.Dependency dependency) {
                        this.dependency = dependency;
                    }
                }

                @Test
                public void should_throw_exception_if_multi_qualifiers_given() {
                    assertThrows(IllegalComponentException.class, () -> new InjectionProvider<>(InjectConstructorWithMultiQualifiers.class));
                }
            }
        }

        @Nested
        class IllegalConstructor {
            abstract class AbstractComponentWithInjectConstructor {
                ContainerTest.Dependency dependency;

                @Inject
                AbstractComponentWithInjectConstructor(ContainerTest.Dependency dependency) {
                    this.dependency = dependency;
                }
            }

            @Test
            public void should_throw_exception_if_abstract_class_with_inject_constructor() {
                assertThrows(IllegalComponentException.class, () -> new InjectionProvider<>(AbstractComponentWithInjectConstructor.class));
            }

            @Test
            public void should_throw_exception_if_bind_interface_class_as_component() {
                assertThrows(IllegalComponentException.class, () -> new InjectionProvider<>(ContainerTest.TestComponent.class));
            }

            @Test
            public void should_throw_illegal_exception_if_multi_inject_constructors_exist() {
                assertThrows(IllegalComponentException.class, () -> new InjectionProvider<>(ContainerTest.ComponentWithMultiInjectConstructors.class));

            }

            @Test
            public void should_throw_illegal_exception_if_no_inject_constructor_nor_default_constructor_exists() {
                assertThrows(IllegalComponentException.class, () -> new InjectionProvider<>(ContainerTest.ComponentWithNoInjectConstructorNorDefaultConstructor.class));
            }
        }
    }

    @Nested
    class FieldInjection {

        @Nested
        class InjectTest {
            static class ComponentWithFieldInjection implements ContainerTest.TestComponent {
                @Inject
                ContainerTest.Dependency dependency;
            }

            @Test
            public void should_inject_via_field() {
                ContainerTest.TestComponent component = getComponent(ContainerTest.TestComponent.class, ComponentWithFieldInjection.class);
                assertSame(dependency, ((ComponentWithFieldInjection) component).dependency);
            }

            @Test
            public void should_include_field_inject_dependencies_info() {
                InjectionProvider<ComponentWithFieldInjection> injectionProvider = new InjectionProvider<>(ComponentWithFieldInjection.class);
                assertArrayEquals(new ComponentRef[]{ComponentRef.of(ContainerTest.Dependency.class)}, injectionProvider.getDependencyRefs().toArray());
            }

            static class ComponentWithFieldProviderInjection {
                @Inject
                Provider<ContainerTest.Dependency> dependency;
            }
            @Test
            public void should_include_field_inject_dependency_types_info() {
                InjectionProvider<ComponentWithFieldProviderInjection> injectionProvider = new InjectionProvider<>(ComponentWithFieldProviderInjection.class);
                assertArrayEquals(new ComponentRef[]{ComponentRef.of(dependencyProviderType)}, injectionProvider.getDependencyRefs().toArray());
            }

            static class SubclassWithFieldInjection extends ComponentWithFieldInjection {
            }

            @Test
            public void should_inject_via_superclass() {
                ContainerTest.TestComponent component = getComponent(ContainerTest.TestComponent.class, SubclassWithFieldInjection.class);
                assertSame(dependency, ((SubclassWithFieldInjection) component).dependency);
            }

            static class ProviderInjectByField {
                @Inject
                Provider<ContainerTest.Dependency> dependency;
            }
            @Test
            public void should_inject_provider_via_inject_field() {
                InjectionProvider<ProviderInjectByField> component = new InjectionProvider<>(ProviderInjectByField.class);
                assertSame(dependencyProvider, component.get(context).dependency);
            }
            @Nested
            class WithQualifier {

                @BeforeEach
                public void setUp() {
                    Mockito.reset(context);
                    when(context.get(eq(ComponentRef.of(ContainerTest.Dependency.class, new ContainerTest.TypeBinding.WithQualifier.NamedLiteral("chosenOne"))))).thenReturn(Optional.of(dependency));
                }

                static class InjectField {
                    @Inject
                    @Named("chosenOne") ContainerTest.Dependency dependency;
                }

                @Test
                public void should_inject_dependency_with_qualifier_via_constructor() {
                    InjectionProvider<InjectField> injectionProvider = new InjectionProvider<>(InjectField.class);
                    InjectField component = injectionProvider.get(context);
                    assertSame(dependency, component.dependency);
                }

                @Test
                public void should_include_dependency_with_qualifier_via_method() {
                    InjectionProvider<InjectField> injectionProvider = new InjectionProvider<>(InjectField.class);
                    assertArrayEquals(new ComponentRef[]{ComponentRef.of(ContainerTest.Dependency.class, new ContainerTest.TypeBinding.WithQualifier.NamedLiteral("chosenOne"))}, injectionProvider.getDependencyRefs().toArray());
                }

                static class InjectFieldWithMultiQualifiers {
                    @Inject
                    @Named("chosenOne") @ContainerTest.TypeBinding.WithQualifier.SkyWalker
                    ContainerTest.Dependency dependency;
                }

                @Test
                public void should_throw_exception_if_multi_qualifiers_given() {
                    assertThrows(IllegalComponentException.class, () -> new InjectionProvider<>(InjectFieldWithMultiQualifiers.class));
                }
            }
        }

        @Nested
        class IllegalFields {
            class ComponentWithFinalFieldInjection implements ContainerTest.TestComponent {
                @Inject
                final ContainerTest.Dependency dependency = null;
            }

            @Test
            public void should_throw_exception_if_inject_final_field() {
                assertThrows(IllegalComponentException.class, () -> new InjectionProvider<>(ComponentWithFinalFieldInjection.class));
            }
        }
    }

    @Nested
    class MethodInjection {

        @Nested
        class InjectTest {

            static class ComponentWithMethodInjection implements ContainerTest.TestComponent {
                ContainerTest.Dependency dependency;

                @Inject
                public void install(ContainerTest.Dependency dependency) {
                    this.dependency = dependency;
                }
            }

            static class ComponentWithInjectMethodButNoDependencyDeclared implements ContainerTest.TestComponent {
                boolean called = false;

                @Inject
                public void install() {
                    called = true;
                }
            }

            @Test
            public void should_call_inject_method_if_no_dependency_declared() {
                ContainerTest.TestComponent component = getComponent(ContainerTest.TestComponent.class, ComponentWithInjectMethodButNoDependencyDeclared.class);

                assertTrue(((ComponentWithInjectMethodButNoDependencyDeclared) component).called);
            }

            @Test
            public void should_inject_via_inject_method() {
                ContainerTest.TestComponent component = getComponent(ContainerTest.TestComponent.class, ComponentWithMethodInjection.class);
                assertSame(dependency, ((ComponentWithMethodInjection) component).dependency);
            }

            @Test
            public void should_include_method_dependency_info() {
                InjectionProvider<ComponentWithMethodInjection> injectionProvider = new InjectionProvider<>(ComponentWithMethodInjection.class);
                assertArrayEquals(new ComponentRef[]{ComponentRef.of(ContainerTest.Dependency.class)}, injectionProvider.getDependencyRefs().toArray());
            }

            static class SuperClassWithInjectMethod {
                int superCalled = 0;

                @Inject
                public void install() {
                    superCalled++;
                }
            }

            static class SubClassWithInjectMethod extends SuperClassWithInjectMethod {
                @Inject
                public void install() {
                    super.install();
                }
            }

            @Test
            public void should_call_inject_method_only_once_if_overridden() {
                SubClassWithInjectMethod component = getComponent(SubClassWithInjectMethod.class, SubClassWithInjectMethod.class);
                assertEquals(1, component.superCalled);
            }

            static class SubClassWithSuperClassInjectMethod extends SuperClassWithInjectMethod {
            }

            @Test
            public void should_call_inject_via_super_class() {
                SubClassWithSuperClassInjectMethod component = getComponent(SubClassWithSuperClassInjectMethod.class, SubClassWithSuperClassInjectMethod.class);
                assertEquals(1, component.superCalled);
            }

            static class SubClassWithOverrideMethodNoInjectAnnotation extends SuperClassWithInjectMethod {
                public void install() {
                    super.install();
                }
            }

            @Test
            public void should_not_call_inject_method_if_overridden_method_without_inject_annotation() {
                SubClassWithOverrideMethodNoInjectAnnotation component = getComponent(SubClassWithOverrideMethodNoInjectAnnotation.class, SubClassWithOverrideMethodNoInjectAnnotation.class);

                assertEquals(0, component.superCalled);
            }

            static class ProviderInjectionWithMethod {
                Provider<ContainerTest.Dependency> dependency;

                @Inject
                public void install(Provider<ContainerTest.Dependency> dependency) {
                    this.dependency = dependency;
                }
            }
            @Test
            public void should_inject_provider_by_inject_method() {
                InjectionProvider<ProviderInjectionWithMethod> component = new InjectionProvider<>(ProviderInjectionWithMethod.class);
                assertSame(dependencyProvider, component.get(context).dependency);
            }

            static class ComponentWithMethodProviderInjection {
                Provider<ContainerTest.Dependency> dependency;
                @Inject
                public void install(Provider<ContainerTest.Dependency> dependency) {
                    this.dependency = dependency;
                }

            }
            @Test
            public void should_include_method_inject_dependency_types_info() {
                InjectionProvider<ComponentWithMethodProviderInjection> injectionProvider = new InjectionProvider<>(ComponentWithMethodProviderInjection.class);
                assertArrayEquals(new ComponentRef[]{ComponentRef.of(dependencyProviderType)}, injectionProvider.getDependencyRefs().toArray());
            }

            @Nested
            class WithQualifier {

            @BeforeEach
            public void setUp() {
                Mockito.reset(context);
                when(context.get(eq(ComponentRef.of(ContainerTest.Dependency.class, new ContainerTest.TypeBinding.WithQualifier.NamedLiteral("chosenOne"))))).thenReturn(Optional.of(dependency));
            }

                static class InjectMethod {
                    ContainerTest.Dependency dependency;
                    @Inject
                    void install(@Named("chosenOne") ContainerTest.Dependency dependency) {
                        this.dependency = dependency;
                    }
                }

                @Test
                public void should_inject_dependency_with_qualifier_via_method() {
                    InjectionProvider<InjectMethod> injectionProvider = new InjectionProvider<>(InjectMethod.class);
                    InjectMethod component = injectionProvider.get(context);
                    assertSame(dependency, component.dependency);
                }

                @Test
                public void should_include_dependency_with_qualifier_via_method() {
                    InjectionProvider<InjectMethod> injectionProvider = new InjectionProvider<>(InjectMethod.class);
                    assertArrayEquals(new ComponentRef[]{ComponentRef.of(ContainerTest.Dependency.class, new ContainerTest.TypeBinding.WithQualifier.NamedLiteral("chosenOne"))}, injectionProvider.getDependencyRefs().toArray());
                }

                static class InjectMethodWithMultiQualifiers {
                    @Inject
                    void install (@Named("chosenOne") @ContainerTest.TypeBinding.WithQualifier.SkyWalker ContainerTest.Dependency dependency) {

                    }
                }

                @Test
                public void should_throw_exception_if_multi_qualifiers_given() {
                    assertThrows(IllegalComponentException.class, () -> new InjectionProvider<>(InjectMethodWithMultiQualifiers.class));
                }
            }
        }

        @Nested
        class IllegalMethods {
            static class ComponentWithMethodTypeParameter {
                @Inject
                <T> T install() {
                    return null;
                }
            }

            @Test
            public void should_throw_exception_if_inject_method_have_type_parameter() {
                assertThrows(IllegalComponentException.class, () -> new InjectionProvider<>(ComponentWithMethodTypeParameter.class));
            }
        }
    }
}
