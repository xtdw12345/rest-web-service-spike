package com.spring.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Providers;
import javax.ws.rs.ext.RuntimeDelegate;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

public class ResourceServlet extends HttpServlet {

    private final Logger logger = LoggerFactory.getLogger(ResourceServlet.class);

    private Runtime runtime;
    private Providers providers;

    public ResourceServlet(Runtime runtime) {
        this.runtime = runtime;
        providers = runtime.getProviders();
    }

    @Override
    public void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            ResourceRouter resourceRouter = runtime.getResourceRouter();
            ResourceContext resourceContext = runtime.createResourceContext(req, res);

            respond(res, () -> resourceRouter.dispatch(req, resourceContext));
        } catch (Exception e) {
            logger.error("Error in ResourceServlet", e);
            throw new RuntimeException(e);
        }
    }

    private void respond(HttpServletResponse res, Supplier<OutboundResponse> supplier) throws IOException {
        try {
            respond(res, supplier.get());
        } catch (WebApplicationException e) {
            respond(res, () -> (OutboundResponse) e.getResponse());
        } catch (Throwable throwable) {
            respond(res, () -> from(throwable));
        }

    }

    private void respond(HttpServletResponse res, OutboundResponse response) throws IOException {
        res.setStatus(response.getStatus());
        MultivaluedMap<String, Object> headers = response.getHeaders();
        for (String name : headers.keySet()) {
            List<Object> values = headers.get(name);
            for (Object value : values) {
                RuntimeDelegate.HeaderDelegate headerDelegate = RuntimeDelegate.getInstance().createHeaderDelegate(value.getClass());
                res.addHeader(name, headerDelegate.toString(value));
            }
        }
        GenericEntity entity = response.getGenericEntity();
        if (entity != null) {
            MessageBodyWriter writer = providers.getMessageBodyWriter(entity.getRawType(), entity.getType(), response.getAnnotations(), response.getMediaType());
            writer.writeTo(entity.getEntity(), entity.getRawType(), entity.getType(), response.getAnnotations(), response.getMediaType(), headers, res.getOutputStream());
        }
    }

    private OutboundResponse from(Throwable throwable) {
        ExceptionMapper exceptionMapper = providers.getExceptionMapper(throwable.getClass());
        OutboundResponse response = (OutboundResponse) exceptionMapper.toResponse(throwable);
        return response;
    }
}
