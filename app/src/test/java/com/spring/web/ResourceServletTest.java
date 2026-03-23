package com.spring.web;

import jakarta.servlet.Servlet;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Providers;
import javax.ws.rs.ext.RuntimeDelegate;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResourceServletTest extends ServletTest {
    private Runtime runtime;
    private ResourceRouter router;
    private ResourceContext resourceContext;
    private Providers providers;

    @Override
    protected Servlet getResourceServlet() {
        runtime = mock(Runtime.class);
        router = mock(ResourceRouter.class);
        resourceContext = mock(ResourceContext.class);
        providers = mock(Providers.class);
        when(runtime.getResourceRouter()).thenReturn(router);
        when(runtime.createResourceContext(any(), any())).thenReturn(resourceContext);
        when(runtime.getProviders()).thenReturn(providers);
        return new ResourceServlet(runtime);
    }

    @BeforeEach
    public void before() {
        RuntimeDelegate delegate = mock(RuntimeDelegate.class);
        RuntimeDelegate.setInstance(delegate);
        when(delegate.createHeaderDelegate(NewCookie.class)).thenReturn(new RuntimeDelegate.HeaderDelegate<>() {
            @Override
            public NewCookie fromString(String value) {
                return null;
            }

            @Override
            public String toString(NewCookie value) {
                return value.getName() + "=" + value.getValue();
            }
        });
    }

    @Test
    public void should_use_status_from_response() throws Exception {
        response(HttpStatus.NOT_MODIFIED_304, new MultivaluedHashMap<>(), new GenericEntity("hello", String.class), new Annotation[0], MediaType.TEXT_PLAIN_TYPE);

        HttpResponse httpResponse = get("/");

        assertThat(httpResponse.statusCode()).isEqualTo(HttpStatus.NOT_MODIFIED_304);
    }

    @Test
    public void should_use_headers_from_response() throws Exception {
        NewCookie sessionId= new NewCookie("SESSION_ID", "session");
        NewCookie userId= new NewCookie("USER_ID", "user");
        MultivaluedMap<String, Object>  headers = new MultivaluedHashMap<>();
        headers.addAll("Set-Cookie", sessionId, userId);

        response(HttpStatus.NOT_MODIFIED_304, headers, new GenericEntity("hello", String.class), new Annotation[0], MediaType.TEXT_PLAIN_TYPE);

        HttpResponse httpResponse = get("/");

        assertThat(httpResponse.statusCode()).isEqualTo(HttpStatus.NOT_MODIFIED_304);
        assertThat(httpResponse.headers().allValues("Set-Cookie")).containsExactlyElementsOf(List.of("SESSION_ID=session", "USER_ID=user"));
    }

    @Test
    public void should_write_entity_to_http_response_using_body_writer() throws Exception {
        response(HttpStatus.OK_200, new MultivaluedHashMap<>(), new GenericEntity("hello", String.class), new Annotation[0], MediaType.TEXT_PLAIN_TYPE);

        HttpResponse httpResponse = get("/");

        assertThat(httpResponse.body().toString()).isEqualTo("hello");
    }

    private void response(int status, MultivaluedMap<String, Object> headers, GenericEntity entity, Annotation[] annotations, MediaType mediaType) {
        OutboundResponse outboundResponse = mock(OutboundResponse.class);
        when(outboundResponse.getStatus()).thenReturn(status);
        when(outboundResponse.getHeaders()).thenReturn(headers);
        when(outboundResponse.getGenericEntity()).thenReturn(entity);
        when(outboundResponse.getAnnotations()).thenReturn(annotations);
        when(outboundResponse.getMediaType()).thenReturn(mediaType);

        when(providers.getMessageBodyWriter(eq(String.class), eq(entity.getType()), eq(annotations), eq(mediaType))).thenReturn(new MessageBodyWriter<>() {
            @Override
            public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
                return false;
            }

            @Override
            public void writeTo(String s, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
                PrintWriter writer = new PrintWriter(entityStream);
                writer.print(s);
                writer.flush();
            }
        });

        when(router.dispatch(any(), eq(resourceContext))).thenReturn(outboundResponse);
    }
}
