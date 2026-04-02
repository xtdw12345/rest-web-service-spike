package com.spring.web;

import jakarta.servlet.Servlet;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
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
        response().status(Response.Status.NOT_MODIFIED).returns();

        HttpResponse httpResponse = get("/");

        assertThat(httpResponse.statusCode()).isEqualTo(HttpStatus.NOT_MODIFIED_304);
    }

    @Test
    public void should_use_headers_from_response() throws Exception {
        response().headers(HttpHeaders.SET_COOKIE, new NewCookie("SESSION_ID", "session"), new NewCookie("USER_ID", "user"))
                .status(Response.Status.NOT_MODIFIED)
                .returns();

        HttpResponse httpResponse = get("/");

        assertThat(httpResponse.statusCode()).isEqualTo(HttpStatus.NOT_MODIFIED_304);
        assertThat(httpResponse.headers().allValues("Set-Cookie")).containsExactlyElementsOf(List.of("SESSION_ID=session", "USER_ID=user"));
    }

    @Test
    public void should_write_entity_to_http_response_using_body_writer() throws Exception {
        response().entity(new GenericEntity("hello", String.class), new Annotation[0], MediaType.TEXT_PLAIN_TYPE)
                .returns();

        HttpResponse httpResponse = get("/");

        assertThat(httpResponse.body().toString()).isEqualTo("hello");
    }

    @Test
    public void should_use_response_in_web_application_exception() throws Exception {
        response().status(Response.Status.FORBIDDEN)
                .entity(new GenericEntity("hello", String.class), new Annotation[0], MediaType.TEXT_PLAIN_TYPE)
                .headers(HttpHeaders.SET_COOKIE)
                .thrown();

        HttpResponse httpResponse = get("/");

        assertThat(httpResponse.body().toString()).isEqualTo("hello");
        assertThat(httpResponse.statusCode()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
        assertThat(httpResponse.headers());
    }

    @Test
    public void should_have_no_message_is_entity_is_null() throws Exception {
        response().entity(null, new Annotation[0], MediaType.TEXT_PLAIN_TYPE).returns();

        HttpResponse httpResponse = get("/");

        assertThat(httpResponse.statusCode()).isEqualTo(HttpStatus.OK_200);
        assertThat(httpResponse.body()).isEqualTo("");
    }

    //TODO: 500 if MessageBodyWriter not found
    //TODO: 500 if header delegate not found
    //TODO: 500 if exception mapper not found
    //TODO: if entity is null, ignore MessageBodyWriter

    //TDOO: exception mapper
    //TODO: providers get exception mapper
    //TODO: runtime delegate
    //TODO: header delegate
    //TODO: providers get MessageBodyWriter
    //TODO: MessageBodyWriter write


    //TODO: message body writer exception

    @Test
    public void should_use_response_from_web_application_exception_from_message_body_writer() throws Exception {
        webApplicationExceptionThrownFrom(this::messageBodyWriterTo);
    }

    @Test
    public void should_use_response_from_web_application_exception_from_providers_get_message_body_writer() throws Exception {
        webApplicationExceptionThrownFrom(this::providersGetExceptionMapper);
    }

    @Test
    public void should_map_exception_thrown_from_message_body_writer() throws Exception {
        otherExceptionsThrowsFrom(this::messageBodyWriterTo);
    }

    @Test
    public void should_use_response_from_web_application_exception_thrown_from_providers_get_exception_mapper() throws Exception {
        otherExceptionsThrowsFrom(this::providersGetExceptionMapper);
    }

    @TestFactory
    public List<DynamicTest> should_respond_from_exception() {
        List<DynamicTest> dynamicTests = new ArrayList<>();

        List<Consumer<RuntimeException>> callers = List.of(this::providersGetExceptionMapper, this::providersGetExceptionMapper);
        List<Consumer<Consumer<RuntimeException>>> thrownFroms = List.of(this::otherExceptionsThrowsFrom, this::webApplicationExceptionThrownFrom);
        for (Consumer<RuntimeException> caller : callers) {
            for (Consumer<Consumer<RuntimeException>> thrownFrom : thrownFroms) {
                dynamicTests.add(DynamicTest.dynamicTest(new Date().toString(), () -> thrownFrom.accept(caller)));
            }
        }

        return dynamicTests;
    }

    private void webApplicationExceptionThrownFrom(Consumer<RuntimeException> caller) {
        throwsExeption(caller, new WebApplicationException(response().status(Response.Status.FORBIDDEN).build()));
    }

    private void otherExceptionsThrowsFrom(Consumer<RuntimeException> consumer) {
        throwsExeption(consumer, new IllegalArgumentException());
    }

    private void throwsExeption(Consumer<RuntimeException> consumer, RuntimeException exception) {
        consumer.accept(exception);

        when(providers.getExceptionMapper(IllegalArgumentException.class)).thenReturn(e -> response().status(Response.Status.FORBIDDEN).build());

        HttpResponse httpResponse = get("/");

        assertThat(httpResponse.statusCode()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    }

    private void messageBodyWriterTo(RuntimeException exception) {
        response().entity(new GenericEntity(2.0, Double.class), new Annotation[0], MediaType.TEXT_PLAIN_TYPE)
                .returns();
        when(providers.getMessageBodyWriter(eq(Double.class), eq(Double.class), eq(new Annotation[0]), eq(MediaType.TEXT_PLAIN_TYPE)))
                .thenReturn(new MessageBodyWriter<>() {
                    @Override
                    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
                        return false;
                    }

                    @Override
                    public void writeTo(Double aDouble, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
                        throw exception;
                    }
                });
    }

    private void providersGetMessageBodyWriter(RuntimeException exception) {
        when(router.dispatch(any(), eq(resourceContext))).thenThrow(RuntimeException.class);
        when(providers.getExceptionMapper(eq(RuntimeException.class))).thenReturn(exception2 -> {
            throw exception;
        });
    }

    private void providersGetExceptionMapper(RuntimeException exception) {
        when(router.dispatch(any(), eq(resourceContext))).thenThrow(RuntimeException.class);
        when(providers.getExceptionMapper(eq(RuntimeException.class))).thenReturn(exception2 -> {
            throw exception;
        });
    }

    @Test
    public void should_map_exception_from_exception_mapper() throws Exception {
        RuntimeException exception1 = new IllegalArgumentException();

        providersGetExceptionMapper(exception1);

        when(providers.getExceptionMapper(eq(IllegalArgumentException.class))).thenReturn(exception -> new OutboundResponseBuilder()
                .status(Response.Status.FORBIDDEN).build());

        HttpResponse httpResponse = get("/");

        assertThat(httpResponse.statusCode()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    }

    //TODO: header delegate to string excetion

    @Test
    public void should_build_response_from_exception() throws Exception {
        when(router.dispatch(any(), eq(resourceContext))).thenThrow(RuntimeException.class);
        when(providers.getExceptionMapper(eq(RuntimeException.class))).thenReturn(exception -> new OutboundResponseBuilder()
                .status(Response.Status.FORBIDDEN).build());

        HttpResponse httpResponse = get("/");
        assertThat(httpResponse.statusCode()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());

    }

    private OutboundResponseBuilder response() {
        return new OutboundResponseBuilder();
    }

    class OutboundResponseBuilder {
        private Response.Status status = Response.Status.OK;
        private MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        private GenericEntity entity = new GenericEntity("hello", String.class);
        private Annotation[] annotations = new Annotation[0];
        private MediaType mediaType = MediaType.TEXT_PLAIN_TYPE;

        public OutboundResponseBuilder status(Response.Status status) {
            this.status = status;
            return this;
        }

        public OutboundResponseBuilder headers(String key, Object... values) {
            this.headers = new MultivaluedHashMap<>();
            headers.addAll(key, values);
            return this;
        }

        public OutboundResponseBuilder entity(GenericEntity entity, Annotation[] annotations, MediaType mediaType) {
            this.entity = entity;
            this.annotations = annotations;
            this.mediaType = mediaType;
            return this;
        }

        public void returns() {
            build(response -> when(router.dispatch(any(), eq(resourceContext))).thenReturn(response));
        }

        public void thrown() {
            build(response -> {
                WebApplicationException webApplicationException = new WebApplicationException(response);
                when(router.dispatch(any(), eq(resourceContext))).thenThrow(webApplicationException);
            });
        }

        public void build(Consumer<OutboundResponse> consumer) {
            OutboundResponse outboundResponse = build();
            consumer.accept(outboundResponse);
        }

        public OutboundResponse build() {
            OutboundResponse outboundResponse = mock(OutboundResponse.class);
            when(outboundResponse.getStatus()).thenReturn(status.getStatusCode());
            when(outboundResponse.getStatusInfo()).thenReturn(status);
            when(outboundResponse.getHeaders()).thenReturn(headers);
            when(outboundResponse.getGenericEntity()).thenReturn(entity);
            when(outboundResponse.getAnnotations()).thenReturn(annotations);
            when(outboundResponse.getMediaType()).thenReturn(mediaType);
            if (entity != null) {
                stubMessageWriter();
            }
            return outboundResponse;
        }

        private void stubMessageWriter() {
            when(providers.getMessageBodyWriter(eq(String.class), eq(entity.getType()), same(annotations), eq(mediaType))).thenReturn(new MessageBodyWriter<>() {
                @Override
                public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations2, MediaType mediaType2) {
                    return false;
                }

                @Override
                public void writeTo(String s, Class<?> type, Type genericType, Annotation[] annotations2, MediaType mediaType2, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
                    PrintWriter writer = new PrintWriter(entityStream);
                    writer.print(s);
                    writer.flush();
                }
            });
        }
    }
}
