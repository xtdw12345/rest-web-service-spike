package com.spring.web;

import com.spring.di.Context;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.ext.Providers;

public interface Runtime {
    Providers getProviders();

    ResourceContext createResourceContext(HttpServletRequest req, HttpServletResponse resp);

    Context getApplicationContext();

    ResourceRouter getResourceRouter();
}
