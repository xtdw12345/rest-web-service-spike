package com.spring.web;

import jakarta.servlet.Servlet;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.ExceptionMapper;
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
        new OutboundResponseBuilder().withStatus(Response.Status.NOT_MODIFIED).response();

        HttpResponse httpResponse = get("/");

        assertThat(httpResponse.statusCode()).isEqualTo(HttpStatus.NOT_MODIFIED_304);
    }

    @Test
    public void should_use_headers_from_response() throws Exception {
        new OutboundResponseBuilder().withHeaders(HttpHeaders.SET_COOKIE, new NewCookie("SESSION_ID", "session"), new NewCookie("USER_ID", "user")).withStatus(Response.Status.NOT_MODIFIED).response();

        HttpResponse httpResponse = get("/");

        assertThat(httpResponse.statusCode()).isEqualTo(HttpStatus.NOT_MODIFIED_304);
        assertThat(httpResponse.headers().allValues("Set-Cookie")).containsExactlyElementsOf(List.of("SESSION_ID=session", "USER_ID=user"));
    }

    @Test
    public void should_write_entity_to_http_response_using_body_writer() throws Exception {
        new OutboundResponseBuilder().withEntity(new GenericEntity("hello", String.class), new Annotation[0], MediaType.TEXT_PLAIN_TYPE).response();

        HttpResponse httpResponse = get("/");

        assertThat(httpResponse.body().toString()).isEqualTo("hello");
    }

    @Test
    public void should_use_response_in_web_application_exception() throws Exception {
        new OutboundResponseBuilder()
                .withStatus(Response.Status.FORBIDDEN)
                .withEntity(new GenericEntity("hello", String.class), new Annotation[0], MediaType.TEXT_PLAIN_TYPE)
                .withHeaders(HttpHeaders.SET_COOKIE)
                .thrown();

        HttpResponse httpResponse = get("/");

        assertThat(httpResponse.body().toString()).isEqualTo("hello");
        assertThat(httpResponse.statusCode()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
        assertThat(httpResponse.headers());
    }

    @Test
    public void should_build_response_from_exception() throws Exception {
        when(router.dispatch(any(), eq(resourceContext))).thenThrow(RuntimeException.class);
        when(providers.getExceptionMapper(eq(RuntimeException.class))).thenReturn(exception -> new OutboundResponseBuilder()
                .withStatus(Response.Status.FORBIDDEN).build());

        HttpResponse httpResponse = get("/");
        assertThat(httpResponse.statusCode()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());

    }

    class OutboundResponseBuilder {
        private Response.Status status = Response.Status.OK;
        private MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        private GenericEntity entity = new GenericEntity("hello", String.class);
        private Annotation[] annotations = new Annotation[0];
        private MediaType mediaType = MediaType.TEXT_PLAIN_TYPE;

        public OutboundResponseBuilder withStatus(Response.Status status) {
            this.status = status;
            return this;
        }

        public OutboundResponseBuilder withHeaders(String key, Object... values) {
            this.headers = new MultivaluedHashMap<>();
            headers.addAll(key, values);
            return this;
        }

        public OutboundResponseBuilder withEntity(GenericEntity entity,  Annotation[] annotations, MediaType mediaType) {
            this.entity = entity;
            this.annotations = annotations;
            this.mediaType = mediaType;
            return this;
        }

        public void response() {
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
            stubMessageWriter();
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
