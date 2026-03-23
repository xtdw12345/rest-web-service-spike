package com.spring.web;

import jakarta.servlet.Servlet;
import org.assertj.core.api.Assertions;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.ext.RuntimeDelegate;

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

    @Override
    protected Servlet getResourceServlet() {
        runtime = mock(Runtime.class);
        router = mock(ResourceRouter.class);
        resourceContext = mock(ResourceContext.class);
        when(runtime.getResourceRouter()).thenReturn(router);
        when(runtime.createResourceContext(any(), any())).thenReturn(resourceContext);
        return new ResourceServlet(runtime);
    }

    @Test
    public void should_use_status_from_response() throws Exception {
        OutboundResponse outboundResponse = mock(OutboundResponse.class);
        when(outboundResponse.getStatus()).thenReturn(HttpStatus.NOT_MODIFIED_304);
        when(outboundResponse.getHeaders()).thenReturn(new MultivaluedHashMap<>());
        when(router.dispatch(any(), eq(resourceContext))).thenReturn(outboundResponse);

        HttpResponse httpResponse = get("/");

        assertThat(httpResponse.statusCode()).isEqualTo(HttpStatus.NOT_MODIFIED_304);
    }

    @Test
    public void should_use_headers_from_response() throws Exception {
        RuntimeDelegate delegate = mock(RuntimeDelegate.class);
        RuntimeDelegate.setInstance(delegate);
        when(delegate.createHeaderDelegate(NewCookie.class)).thenReturn(new RuntimeDelegate.HeaderDelegate<NewCookie>() {
            @Override
            public NewCookie fromString(String value) {
                return null;
            }

            @Override
            public String toString(NewCookie value) {
                return value.getName() + "="  + value.getValue();
            }
        });
        NewCookie sessionId= new NewCookie("SESSION_ID", "session");
        NewCookie userId= new NewCookie("USER_ID", "user");

        OutboundResponse outboundResponse = mock(OutboundResponse.class);
        when(outboundResponse.getStatus()).thenReturn(HttpStatus.NOT_MODIFIED_304);
        MultivaluedMap<String, Object>  headers = new MultivaluedHashMap<>();
        headers.addAll("Set-Cookie", sessionId, userId);
        when(outboundResponse.getHeaders()).thenReturn(headers);
        when(router.dispatch(any(), eq(resourceContext))).thenReturn(outboundResponse);

        HttpResponse httpResponse = get("/");

        assertThat(httpResponse.statusCode()).isEqualTo(HttpStatus.NOT_MODIFIED_304);
        assertThat(httpResponse.headers().allValues("Set-Cookie")).containsExactlyElementsOf(List.of("SESSION_ID=session", "USER_ID=user"));
    }
}
