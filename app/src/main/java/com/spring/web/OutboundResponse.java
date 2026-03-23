package com.spring.web;

import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.Response;
import java.lang.annotation.Annotation;

public abstract class OutboundResponse extends Response {
    abstract GenericEntity getGenericEntity();

    abstract Annotation[] getAnnotations();
}
