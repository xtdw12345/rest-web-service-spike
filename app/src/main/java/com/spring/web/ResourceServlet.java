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
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Providers;
import javax.ws.rs.ext.RuntimeDelegate;
import java.io.IOException;
import java.util.List;

public class ResourceServlet extends HttpServlet {

    private final Logger logger = LoggerFactory.getLogger(ResourceServlet.class);

    private Runtime runtime;

    public ResourceServlet(Runtime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            ResourceRouter resourceRouter = runtime.getResourceRouter();
            ResourceContext resourceContext = runtime.createResourceContext(req, res);
            OutboundResponse response = resourceRouter.dispatch(req, resourceContext);
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
            Providers providers = runtime.getProviders();
            MessageBodyWriter writer = providers.getMessageBodyWriter(entity.getRawType(), entity.getType(), response.getAnnotations(), response.getMediaType());
            writer.writeTo(entity.getEntity(), entity.getRawType(), entity.getType(), response.getAnnotations(), response.getMediaType(), headers, res.getOutputStream());
        } catch (Exception e) {
            logger.error("Error in ResourceServlet", e);
            throw new RuntimeException(e);
        }
    }
}
