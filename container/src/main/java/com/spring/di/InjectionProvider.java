package com.spring.di;

import com.spring.di.exception.IllegalComponentException;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static com.spring.di.InjectionProvider.Injectable.getQualifier;
import static java.util.Arrays.stream;
import static java.util.stream.Stream.concat;

class InjectionProvider<T> implements ComponentProvider<T> {

    private Injectable<Constructor<T>> injectConstructor;
    private List<Injectable<Method>> injectMethods;
    private List<Injectable<Field>> injectableFields;

    public InjectionProvider(Class<T> component) {
        if (Modifier.isAbstract(component.getModifiers())) {
            throw new IllegalComponentException();
        }

        this.injectConstructor = getInjectConstructor(component);
        this.injectMethods = getInjectMethods(component);
        this.injectableFields = getInjectFields(component);

        checkFields(this.injectableFields);
        checkMethods(this.injectMethods);
    }

    @Override
    public T get(Context context) {
        try {
            Object[] params = toDependencies(context, injectConstructor.element());
            Constructor<T> constructor = injectConstructor.element();
            constructor.setAccessible(true);
            T instance = constructor.newInstance(params);
            for (Injectable<Field> field : injectableFields) {
                field.element().setAccessible(true);
                field.element().set(instance, toDependency(context, field.element()));
            }
            for (Injectable<Method> method : injectMethods) {
                method.element().setAccessible(true);
                method.element().invoke(instance, toDependencies(context, method.element()));
            }
            return instance;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    record Injectable<Element extends AccessibleObject>(Element element, ComponentRef<?>[] required) {

        private static <T extends Executable> Injectable<T> of(T executable) {
            return new Injectable<>(executable, toRequired(executable.getParameters()));
        }

        private static Injectable<Field> of(Field f) {
            return new Injectable<>(f, new ComponentRef[]{toComponentRef(f)});
        }

        private static ComponentRef<?> toComponentRef(Field f) {
            return ComponentRef.of(f.getGenericType(), getQualifier(f));
        }

        private static ComponentRef<?> toComponentRef(Parameter p) {
            return ComponentRef.of(p.getParameterizedType(), getQualifier(p));
        }

        public static Annotation getQualifier(AnnotatedElement element) {
            List<Annotation> annotations = stream(element.getAnnotations()).filter(a -> a.annotationType().isAnnotationPresent(Qualifier.class)).toList();
            if (annotations.size() > 1) {
                throw new IllegalComponentException();
            }
            return annotations.stream().findFirst().orElse(null);
        }

        private static ComponentRef<?>[] toRequired(Parameter[] parameters) {
            return stream(parameters).map(Injectable::toComponentRef).toArray(ComponentRef<?>[]::new);
        }
    }

    @Override
    public List<ComponentRef<?>> getDependencyRefs() {
        return concat(concat(Stream.of(injectConstructor), injectableFields.stream()), injectMethods.stream())
                .flatMap(i -> stream(i.required())).toList();
    }

    private static <T> List<Injectable<Field>> getInjectFields(Class<T> component) {
        return InjectionProvider
                .<Field>traverse(component, (currentClass, injectFields) -> injectable(currentClass.getDeclaredFields()).toList())
                .stream().map(Injectable::of).toList();
    }

    private static <T> List<Injectable<Method>> getInjectMethods(Class<T> component) {
        return InjectionProvider.<Method>traverse(component, (current, injectMethods1) -> injectable(current.getDeclaredMethods())
                .filter(m -> isOverrideByInjectMethod(m, injectMethods1))
                .filter(m -> notOverrideByNoInjectMethod(m, component.getDeclaredMethods()))
                .toList()).stream().map(Injectable::of).toList();
    }

    private static <T> Injectable<Constructor<T>> getInjectConstructor(Class<T> component) {
        List<Constructor<?>> injectConstructors = injectable(component.getDeclaredConstructors()).toList();
        if (injectConstructors.size() > 1) {
            throw new IllegalComponentException();
        }
        return Injectable.of((Constructor<T>) injectConstructors.stream().findFirst().orElseGet(() -> defaultConstructor(component)));
    }

    private static <T> Constructor<T> defaultConstructor(Class<T> componentImplClass) {
        try {
            return componentImplClass.getDeclaredConstructor();
        } catch (Exception e) {
            throw new IllegalComponentException(e);
        }
    }

    private static void checkMethods(List<Injectable<Method>> methods) {
        if (methods.stream().anyMatch(m -> m.element().getTypeParameters().length > 0)) {
            throw new IllegalComponentException();
        }
    }

    private static void checkFields(List<Injectable<Field>> fields) {
        if (fields.stream().map(Injectable::element).anyMatch(f -> Modifier.isFinal(f.getModifiers()))) {
            throw new IllegalComponentException();
        }
    }

    private static <T extends AnnotatedElement> Stream<T> injectable(T[] declaredFields) {
        return stream(declaredFields).filter(f -> f.isAnnotationPresent(Inject.class));
    }

    private static boolean isOverrideByInjectMethod(Method m, List<Method> injectMethods) {
        return injectMethods.stream().noneMatch(c -> isOverride(m, c));
    }

    private static boolean isOverride(Method m, Method c) {
        return c.getName().equals(m.getName()) &&
                Arrays.equals(c.getParameterTypes(), m.getParameterTypes());
    }

    private static boolean notOverrideByNoInjectMethod(Method m, Method[] declaredMethods) {
        return notInjectable(declaredMethods).noneMatch(c -> isOverride(m, c));
    }

    private static Object[] toDependencies(Context context, Executable executable) {
        return stream(executable.getParameters()).map(p -> getDependency(context, p)).toArray();
    }

    private static Stream<Method> notInjectable(Method[] declaredMethods) {
        return stream(declaredMethods).filter(m1 -> !m1.isAnnotationPresent(Inject.class));
    }

    private static Object toDependency(Context context, Field field) {
        return toDependency(context, ComponentRef.of(field.getGenericType(), getQualifier(field)));
    }

    private static Object getDependency(Context context, Parameter p) {
        return toDependency(context, ComponentRef.of(p.getParameterizedType(), getQualifier(p)));
    }

    private static Object toDependency(Context context, ComponentRef<?> ref) {
        return context.get(ref).get();
    }

    private static <T> List<T> traverse(Class<?> component, BiFunction<Class<?>, List<T>, List<T>> toInjections) {
        List<T> injectFields = new ArrayList<>();
        Class<?> currentClass = component;
        while (currentClass != Object.class) {
            injectFields.addAll(toInjections.apply(currentClass, injectFields));
            currentClass = currentClass.getSuperclass();
        }
        return injectFields;
    }
}
