package org.example;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.*;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ASpike {

    Server server;

    @BeforeEach
    public void start() throws Exception {
        server = new Server(8080);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        Application application = new TestApplication();
        context.addServlet(new ServletHolder(new HelloServlet(application, new TestProviders(application))), "/");

        server.setHandler(context);

        server.start();
    }

    @AfterEach
    public void stop() throws Exception {
        server.stop();
    }

    @Test
    public void should() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(new URI("http://localhost:8080/")).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String bodyString = response.body();
        System.out.println(bodyString);
        assertEquals("Hello World", bodyString);
    }

    static class HelloServlet extends HttpServlet {
        private Application application;
        private Providers providers;

        public HelloServlet(Application application, Providers providers) {
            this.application = application;
            this.providers = providers;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            List<Class<?>> rootResources = application.getClasses().stream().filter(x -> x.isAnnotationPresent(Path.class)).toList();
            Object result = dispatch(req, rootResources, providers);
            resp.getWriter().print(result.toString());
            resp.getWriter().flush();
        }
    }

    static Object dispatch(HttpServletRequest req, List<Class<?>> rootResources, Providers providers) {
        try {
            Class rootResource = rootResources.stream().findFirst().orElse(null);
            MessageBodyReader<?> messageBodyReader = providers.getMessageBodyReader(rootResource, null, null, null);
            return messageBodyReader.readFrom(rootResource, null, null, null, null, req.getInputStream());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static class TestProviders implements Providers {
        private Application application;
        private List<MessageBodyReader> readers;
        public TestProviders(Application application) {
            this.application = application;
            readers = (List<MessageBodyReader>) application.getClasses().stream().filter(MessageBodyReader.class::isAssignableFrom).map(type -> {
                try {
                    return type.getConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toList();
        }

        @Override
        public <T> MessageBodyReader<T> getMessageBodyReader(Class<T> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            try {
                Class<MessageBodyReader<T>> messageBodyReaderType = (Class<MessageBodyReader<T>>) application.getClasses().stream().filter(MessageBodyReader.class::isAssignableFrom).findFirst().orElse(null);
                return messageBodyReaderType.getConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }

        @Override
        public <T> MessageBodyWriter<T> getMessageBodyWriter(Class<T> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return null;
        }

        @Override
        public <T extends Throwable> ExceptionMapper<T> getExceptionMapper(Class<T> type) {
            return null;
        }

        @Override
        public <T> ContextResolver<T> getContextResolver(Class<T> contextType, MediaType mediaType) {
            return null;
        }
    }

    static class TestMessageBodyReader implements MessageBodyReader<String> {
        public TestMessageBodyReader() {
        }

        @Override
        public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return true;
        }

        @Override
        public String readFrom(Class<String> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
            try {
                Object o = type.getConstructor().newInstance();
                Method method = Arrays.stream(type.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(GET.class)).findFirst().orElse(null);
                return method.invoke(o).toString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class TestApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(TestResource.class, TestMessageBodyReader.class);
        }
    }

    @Path("/get")
    static class TestResource {
        public TestResource() {

        }
        @GET
        public  String get() {
            return "Hello World";
        }
    }
}
