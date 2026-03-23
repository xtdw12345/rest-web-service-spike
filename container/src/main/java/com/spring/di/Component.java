package com.spring.di;

import java.lang.annotation.Annotation;

public record Component(Class<?> componentType, Annotation qualifier) {

}
